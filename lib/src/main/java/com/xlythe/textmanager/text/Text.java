package com.xlythe.textmanager.text;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.xlythe.textmanager.Message;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * Either an sms or an mms
 */

public class Text implements Message, Parcelable {
    private static final String[] MMS_PROJECTION = new String[]{
            BaseColumns._ID,
            Mock.Telephony.Mms.Part.CONTENT_TYPE,
            Mock.Telephony.Mms.Part.TEXT,
            Mock.Telephony.Mms.Part._DATA
    };
    private static final String TYPE_SMS = "sms";
    private static final String TYPE_MMS = "mms";
    private static final long SEC_TO_MILLI = 1000;

    private long mId;
    private long mThreadId;
    private long mDate;
    private long mMmsId;
    private String mAddress;
    private String mBody;
    private String mDeviceNumber;
    private boolean mIncoming;
    private boolean mIsMms = false;
    private Attachment mAttachment;
    private Contact mSender;
    private Contact mRecipient;
    private ArrayList<Attachment> mAttachments = new ArrayList<>();

    private Text() {}

    // This was public for cursors, let me know if I should make it protected again
    public Text(Context context, Cursor cursor) {
        TelephonyManager manager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        mDeviceNumber = manager.getLine1Number();
        String type = getMessageType(cursor);
        if (TYPE_SMS.equals(type)){
            mIsMms = false;
            parseSmsMessage(cursor);
        } else if (TYPE_MMS.equals(type)){
            mIsMms = true;
            parseMmsMessage(cursor, context);
        } else {
            Log.w("TelephonyProvider", "Unknown Message Type");
        }
        buildContact(context);
    }

    private String getMessageType(Cursor cursor) {
        int typeIndex = cursor.getColumnIndex(Mock.Telephony.MmsSms.TYPE_DISCRIMINATOR_COLUMN);
        if (typeIndex < 0) {
            // Type column not in projection, use another discriminator
            String cType = cursor.getString(cursor.getColumnIndex(Mock.Telephony.Mms.CONTENT_TYPE));
            // If content type is present, this is an MMS message
            if (cType != null) {
                return TYPE_MMS;
            } else {
                return TYPE_SMS;
            }
        }
        else {
            return cursor.getString(typeIndex);
        }
    }

    private void parseSmsMessage(Cursor data) {
        mId = data.getLong(data.getColumnIndexOrThrow(BaseColumns._ID));
        mThreadId = data.getLong(data.getColumnIndexOrThrow(Mock.Telephony.Sms.Conversations.THREAD_ID));
        mDate = data.getLong(data.getColumnIndexOrThrow(Mock.Telephony.Sms.Conversations.DATE));
        mAddress = data.getString(data.getColumnIndexOrThrow(Mock.Telephony.Sms.ADDRESS));
        mBody = data.getString(data.getColumnIndexOrThrow(Mock.Telephony.Sms.BODY));
        mIncoming = isIncomingMessage(data, true);
    }

    private String cleanNumber(String number){
        String clean = "";
        for (int i = 0; i < number.length(); i++) {
            if (Character.isDigit(number.charAt(i))) {
                clean += number.charAt(i);
            }
        }
        if (clean.length() > 10){
            clean = clean.substring(clean.length()-10, clean.length());
        }
        return clean;
    }

    private void parseMmsMessage(Cursor data, Context context) {
        mId = data.getLong(data.getColumnIndexOrThrow(BaseColumns._ID));
        mThreadId = data.getLong(data.getColumnIndexOrThrow(Mock.Telephony.Sms.Conversations.THREAD_ID));
        mDate = data.getLong(data.getColumnIndexOrThrow(Mock.Telephony.Sms.Conversations.DATE)) * SEC_TO_MILLI;
        mIncoming = isIncomingMessage(data, false);
        mMmsId = data.getLong(data.getColumnIndex(Mock.Telephony.Mms._ID));
        Uri addressUri = Uri.withAppendedPath(Telephony.Mms.CONTENT_URI, mId + "/addr");

        // Query the address information for this message
        Cursor addr = context.getContentResolver().query(
                addressUri,
                null,
                null,
                null,
                null
        );
        HashSet<String> recipients = new HashSet<>();
        while (addr.moveToNext()) {
            String address;
            if (android.os.Build.VERSION.SDK_INT >= 19) {
                address = addr.getString(addr.getColumnIndex(Telephony.Mms.Addr.ADDRESS));
            } else {
                address = addr.getString(addr.getColumnIndex("address"));
            }

            // Don't add our own number to the displayed list
            mDeviceNumber = cleanNumber(mDeviceNumber);
            
            if (mDeviceNumber == null || !address.contains(mDeviceNumber)) {
                recipients.add(address);
            }
        }
        mAddress = TextUtils.join(", ", recipients);
        addr.close();

        if (mIsMms && mAttachment == null) {
            // Query all the MMS parts associated with this message
            Uri messageUri = Uri.withAppendedPath(Mock.Telephony.Mms.CONTENT_URI, mId + "/part");
            Cursor inner = context.getContentResolver().query(
                    messageUri,
                    MMS_PROJECTION,
                    Mock.Telephony.Mms.Part.MSG_ID + " = ?",
                    new String[]{String.valueOf(mMmsId)},
                    null
            );

            while (inner.moveToNext()) {
                String contentType = inner.getString(inner.getColumnIndex(Mock.Telephony.Mms.Part.CONTENT_TYPE));
                if (contentType == null) {
                    continue;
                }

                if (contentType.matches("image/.*")) {
                    // Find any part that is an image attachment
                    long partId = inner.getLong(inner.getColumnIndex(BaseColumns._ID));
                    mAttachment = new ImageAttachment(Uri.withAppendedPath(Mock.Telephony.Mms.CONTENT_URI, "part/" + partId));
                } else if (contentType.matches("text/.*")) {
                    // Find any part that is text data
                    mBody = inner.getString(inner.getColumnIndex(Mock.Telephony.Mms.Part.TEXT));
                }
            }
            inner.close();
        }
    }

    private static boolean isIncomingMessage(Cursor cursor, boolean isSMS) {
        int boxId;
        if (isSMS) {
            boxId = cursor.getInt(cursor.getColumnIndexOrThrow(Mock.Telephony.Sms.TYPE));
            return (boxId == Mock.Telephony.TextBasedSmsColumns.MESSAGE_TYPE_INBOX ||
                    boxId == Mock.Telephony.TextBasedSmsColumns.MESSAGE_TYPE_ALL);
        }
        else {
            boxId = cursor.getInt(cursor.getColumnIndexOrThrow(Mock.Telephony.Mms.MESSAGE_BOX));
            return (boxId == Mock.Telephony.Mms.MESSAGE_BOX_INBOX ||
                    boxId == Mock.Telephony.Mms.MESSAGE_BOX_ALL);
        }
    }

    private void buildContact(Context context) {
        ContentResolver contentResolver = context.getContentResolver();
        Uri uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(mAddress));

        Cursor c = contentResolver.query(uri, null, null, null, null);
        try {
            if (c != null && c.moveToFirst()) {
                mSender = new Contact(c);
            } else {
                mSender = new Contact(mAddress);
            }
        } finally {
            if (c != null) c.close();
        }
    }

    public boolean isMms() {
        return mIsMms;
    }

    public boolean isIncoming(){
        return mIncoming;
    }

    @Override
    public String getId() {
        return Long.toString(mId);
    }

    @Override
    public String getBody() {
        return mBody;
    }

    @Override
    public long getTimestamp() {
        return mDate;
    }

    @Override
    public String getThreadId() {
        return Long.toString(mThreadId);
    }

    @Override
    public Attachment getAttachment() {
        return mAttachment;
    }

    public List<Attachment> getAttachments() {
        return mAttachments;
    }

    @Override
    public Contact getSender() {
        return mSender;
    }

    @Override
    public Contact getRecipient() {
        if (mRecipient == null) {
            return new Contact(mAddress);
        } else {
            return mRecipient;
        }
    }

    @Override
    public Status getStatus() {
        //TODO: getStatus()
        return null;
    }

    private Text(Parcel in) {
        mId = in.readLong();
        mThreadId = in.readLong();
        mDate = in.readLong();
        mMmsId = in.readLong();
        mAddress = in.readString();
        mBody = in.readString();
        mDeviceNumber = in.readString();
        mIncoming = in.readByte() != 0;
        mIsMms = in.readByte() != 0;
        mSender = in.readParcelable(Contact.class.getClassLoader());
        mRecipient = in.readParcelable(Contact.class.getClassLoader());
        // TODO: Attachment may not be functioning properly, needs further testing
        mAttachment = in.readParcelable(Attachment.class.getClassLoader());
//        int size = in.readInt();
//        for(int i=0; i<size; i++){
//            mAttachments.add((Attachment)in.readParcelable(ImageAttachment.class.getClassLoader()));
//        }
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeLong(mId);
        out.writeLong(mThreadId);
        out.writeLong(mDate);
        out.writeLong(mMmsId);
        out.writeString(mAddress);
        out.writeString(mBody);
        out.writeString(mDeviceNumber);
        out.writeByte((byte) (mIncoming ? 1 : 0));
        out.writeByte((byte) (mIsMms ? 1 : 0));
        out.writeParcelable(mSender, flags);
        out.writeParcelable(mRecipient, flags);
        out.writeParcelable(mAttachment, flags);
//        out.writeInt(mAttachments.size());
//        for(int i=0; i<mAttachments.size(); i++){
//            out.writeParcelable(mAttachments.get(0), flags);
//        }
    }

    public static final Parcelable.Creator<Text> CREATOR = new Parcelable.Creator<Text>() {
        public Text createFromParcel(Parcel in) {
            return new Text(in);
        }

        public Text[] newArray(int size) {
            return new Text[size];
        }
    };

    public static class Builder {
        private String mMessage;
        private Contact mRecipient;
        private String mAddress;
        private ArrayList<Attachment> mAttachments = new ArrayList<>();

        public Builder() {
        }

        public Builder recipient(String address) {
            mAddress = address;
            return this;
        }

        public Builder recipient(Contact recipient) {
            mRecipient = recipient;
            return this;
        }

        public Builder attach(Attachment attachment) {
            mAttachments.add(attachment);
            return this;
        }

        public Builder message(String message) {
            mMessage = message;
            return this;
        }

        public Text build() {
            Text text = new Text();
            text.mBody = mMessage;
            text.mAddress = mAddress;
            text.mRecipient = mRecipient;
            if(mAttachments.size()>0){
                text.mIsMms = true;
                text.mAttachments.addAll(mAttachments);
            }
            text.mDate = System.currentTimeMillis();
            return text;
        }
    }
}