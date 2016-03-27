package com.xlythe.textmanager.text;

import android.content.Context;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.BaseColumns;
import android.provider.Telephony;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.xlythe.textmanager.Message;
import com.xlythe.textmanager.User;
import com.xlythe.textmanager.text.util.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Either an sms or an mms
 */
public final class Text implements Message, Parcelable, Comparable<Text> {
    private static final String[] MMS_PROJECTION = new String[]{
            BaseColumns._ID,
            Mock.Telephony.Mms.Part.CONTENT_TYPE,
            Mock.Telephony.Mms.Part.TEXT,
            Mock.Telephony.Mms.Part._DATA
    };
    private static final String TYPE_SMS = "sms";
    private static final String TYPE_MMS = "mms";
    private static final long SEC_TO_MILLI = 1000;
    private static final long TYPE_SENDER = 137;

    private long mId;
    private long mThreadId;
    private long mDate;
    private long mMmsId;
    private int mStatus;
    private String mBody;
    private String mDeviceNumber;
    private boolean mIncoming;
    private boolean mIsMms = false;
    private String mSenderAddress;
    private Contact mSender;
    private HashSet<String> mMemberAddresses = new HashSet<>();
    private HashSet<Contact> mMembers = new HashSet<>();
    private Attachment mAttachment;

    private Text() {}

    protected Text(Context context, Cursor cursor) {
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
        for (String address : mMemberAddresses) {
            Contact addr = TextManager.getInstance(context).lookupContact(address);
            if (!equal(addr.getNumber(), TextManager.getInstance(context).getSelf().getNumber())) {
                mMembers.add(addr);
            }
        }
        if (isIncoming()) {
            mSender = TextManager.getInstance(context).lookupContact(mSenderAddress);
        } else {
            mSender = TextManager.getInstance(context).getSelf();
        }
    }

    public boolean equal(String number1, String number2) {
        return number1.length() >= 10
                && number2.length() >= 10
                &&(normalizeNumber(number1).contains(number2)
                || normalizeNumber(number2).contains(number1));
    }

    public String normalizeNumber(String number) {
        String clean = "";
        for (char c: number.toCharArray()) {
            if (Character.isDigit(c)){
                clean += c;
            }
        }
        return clean;
    }

    private Text(Parcel in) {
        mId = in.readLong();
        mThreadId = in.readLong();
        mDate = in.readLong();
        mMmsId = in.readLong();
        mStatus = in.readInt();
        mSenderAddress = in.readString();
        mBody = in.readString();
        mDeviceNumber = in.readString();
        mIncoming = in.readByte() != 0;
        mIsMms = in.readByte() != 0;
        mSender = in.readParcelable(Contact.class.getClassLoader());

        int membersSize = in.readInt();
        for (int i = 0; i < membersSize; i++) {
            mMembers.add((Contact) in.readParcelable(Contact.class.getClassLoader()));
        }

        mAttachment = in.readParcelable(Attachment.class.getClassLoader());
    }

    private String getMessageType(Cursor cursor) {
        int typeIndex = cursor.getColumnIndex(Mock.Telephony.MmsSms.TYPE_DISCRIMINATOR_COLUMN);
        if (typeIndex < 0) {
            // Type column not in projection, use another discriminator
            String cType = null;
            int cTypeIndex = cursor.getColumnIndex(Mock.Telephony.Mms.CONTENT_TYPE);
            if (cTypeIndex >= 0) {
                cType = cursor.getString(cursor.getColumnIndex(Mock.Telephony.Mms.CONTENT_TYPE));
            }
            // If content type is present, this is an MMS message
            if (cType != null) {
                return TYPE_MMS;
            } else {
                return TYPE_SMS;
            }
        } else {
            return cursor.getString(typeIndex);
        }
    }

    private void parseSmsMessage(Cursor data) {
        mIncoming = isIncomingMessage(data, true);
        mId = data.getLong(data.getColumnIndexOrThrow(BaseColumns._ID));
        mThreadId = data.getLong(data.getColumnIndexOrThrow(Mock.Telephony.Sms.Conversations.THREAD_ID));
        mDate = data.getLong(data.getColumnIndexOrThrow(Mock.Telephony.Sms.Conversations.DATE));
        mMemberAddresses.add(data.getString(data.getColumnIndexOrThrow(Mock.Telephony.Sms.ADDRESS)));
        mSenderAddress = data.getString(data.getColumnIndexOrThrow(Mock.Telephony.Sms.ADDRESS));
        mBody = data.getString(data.getColumnIndexOrThrow(Mock.Telephony.Sms.BODY));
        mStatus = data.getInt(data.getColumnIndexOrThrow(Mock.Telephony.Sms.STATUS));
    }

    private void parseMmsMessage(Cursor data, Context context) {
        mIncoming = isIncomingMessage(data, false);
        mId = data.getLong(data.getColumnIndexOrThrow(BaseColumns._ID));
        mThreadId = data.getLong(data.getColumnIndexOrThrow(Mock.Telephony.Sms.Conversations.THREAD_ID));
        mDate = data.getLong(data.getColumnIndexOrThrow(Mock.Telephony.Sms.Conversations.DATE)) * SEC_TO_MILLI;
        mMmsId = data.getLong(data.getColumnIndex(Mock.Telephony.Mms._ID));
        mStatus = data.getInt(data.getColumnIndexOrThrow(Mock.Telephony.Mms.STATUS));
        Uri addressUri = Uri.withAppendedPath(Mock.Telephony.Mms.CONTENT_URI, mId + "/addr");

        // Query the address information for this message
        Cursor addr = context.getContentResolver().query(addressUri, null, null, null, null);

        while (addr.moveToNext()) {
            if (addr.getLong(addr.getColumnIndex(Mock.Telephony.Mms.Addr.TYPE)) == TYPE_SENDER){
                mSenderAddress = addr.getString(addr.getColumnIndex(Mock.Telephony.Mms.Addr.ADDRESS));
            }
            mMemberAddresses.add(addr.getString(addr.getColumnIndex(Mock.Telephony.Mms.Addr.ADDRESS)));
        }
        addr.close();

        if (mIsMms) {
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
                } else if (contentType.matches("video/.*")) {
                    long partId = inner.getLong(inner.getColumnIndex(BaseColumns._ID));
                    mAttachment = new VideoAttachment(Uri.withAppendedPath(Mock.Telephony.Mms.CONTENT_URI, "part/" + partId));
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

    public long getIdAsLong() {
        return mId;
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

    public long getThreadIdAsLong() {
        return mThreadId;
    }

    @Override
    public Attachment getAttachment() {
        return mAttachment;
    }

    @Override
    public Contact getSender() {
        return mSender;
    }

    @Override
    public Set<Contact> getMembers() {
        return mMembers;
    }

    public Set<Contact> getMembersExceptMe(Context context) {
        Set<Contact> members = new HashSet<>(mMembers);
        members.remove(TextManager.getInstance(context).getSelf());
        return members;
    }

    @Override
    public Status getStatus() {
        if (mStatus == Mock.Telephony.Sms.STATUS_COMPLETE)
                return Status.COMPLETE;
        if (mStatus == Mock.Telephony.Sms.STATUS_PENDING)
                return Status.PENDING;
        if (mStatus == Mock.Telephony.Sms.STATUS_FAILED)
                return Status.FAILED;
        return Status.NONE;
    }

    public byte[] toBytes() {
        Parcel parcel = Parcel.obtain();
        writeToParcel(parcel, describeContents());
        try {
            return parcel.marshall();
        } finally {
            parcel.recycle();
        }
    }

    public static Text fromBytes(byte[] bytes) {
        Parcel parcel = Parcel.obtain();
        parcel.unmarshall(bytes, 0, bytes.length);
        parcel.setDataPosition(0);
        try {
            return CREATOR.createFromParcel(parcel);
        } finally {
            parcel.recycle();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o != null && o instanceof Text) {
            Text a = (Text) o;
            return Utils.equals(mId, a.mId)
                    && Utils.equals(mThreadId, a.mThreadId)
                    && Utils.equals(mDate, a.mDate)
                    && Utils.equals(mMmsId, a.mMmsId)
                    && Utils.equals(mSenderAddress, a.mSenderAddress)
                    && Utils.equals(mBody, a.mBody)
                    && Utils.equals(mDeviceNumber, a.mDeviceNumber)
                    && Utils.equals(mIncoming, a.mIncoming)
                    && Utils.equals(mIsMms, a.mIsMms)
                    && Utils.equals(mSender, a.mSender)
                    && Utils.equals(mMembers, a.mMembers)
                    && Utils.equals(mAttachment, a.mAttachment);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Utils.hashCode(mId)
                + Utils.hashCode(mThreadId)
                + Utils.hashCode(mDate)
                + Utils.hashCode(mMmsId)
                + Utils.hashCode(mSenderAddress)
                + Utils.hashCode(mBody)
                + Utils.hashCode(mDeviceNumber)
                + Utils.hashCode(mIncoming)
                + Utils.hashCode(mIsMms)
                + Utils.hashCode(mSender)
                + Utils.hashCode(mMembers)
                + Utils.hashCode(mAttachment);
    }

    @Override
    public String toString() {
        return String.format("Text{id=%s, thread_id=%s, date=%s, mms_id=%s, address=%s, body=%s," +
                        "device_number=%s, incoming=%s, is_mms=%s, sender=%s, recipient=%s," +
                        "attachment=%s}",
                mId, mThreadId, mDate, mMmsId, mSenderAddress, mBody, mDeviceNumber, mIncoming, mIsMms,
                mSender, mMembers, mAttachment);
    }

    @Override
    public int compareTo(Text text) {
        if (text.getTimestamp() > getTimestamp()) {
            return 1;
        }
        if(text.getTimestamp() < getTimestamp()) {
            return -1;
        }
        return 0;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeLong(mId);
        out.writeLong(mThreadId);
        out.writeLong(mDate);
        out.writeLong(mMmsId);
        out.writeInt(mStatus);
        // TODO: can probably remove the string addresses
        // Should probably not even be a global variable
        out.writeString(mSenderAddress);
        out.writeString(mBody);
        out.writeString(mDeviceNumber);
        out.writeByte((byte) (mIncoming ? 1 : 0));
        out.writeByte((byte) (mIsMms ? 1 : 0));
        out.writeParcelable(mSender, flags);

        out.writeInt(mMembers.size());
        for (Contact member : mMembers){
            out.writeParcelable(member, flags);
        }

        out.writeParcelable(mAttachment, flags);
    }

    public static final Parcelable.Creator<Text> CREATOR = new Parcelable.Creator<Text>() {
        public Text createFromParcel(Parcel in) {
            return new Text(in);
        }

        public Text[] newArray(int size) {
            return new Text[size];
        }
    };

    public static class TextCursor extends CursorWrapper {
        private final Context mContext;

        public TextCursor(Context context, android.database.Cursor cursor) {
            super(cursor);
            mContext = context.getApplicationContext();
        }

        public Text getText() {
            return new Text(mContext, this);
        }
    }

    public static class Builder {
        private String mMessage;
        private Contact mSender;
        private HashSet<Contact> mRecipients = new HashSet<>();
        private Attachment mAttachment;

        public Builder setSender(Context context) {
            mSender = TextManager.getInstance(context).getSelf();
            return this;
        }

        public Builder addRecipient(Context context, String address) {
            mRecipients.add(TextManager.getInstance(context).lookupContact(address));
            return this;
        }

        public Builder addRecipient(Contact address) {
            mRecipients.add(address);
            return this;
        }

        public Builder addRecipients(Context context, String... addresses) {
            TextManager textManager = TextManager.getInstance(context);
            for (String address : addresses) {
                mRecipients.add(textManager.lookupContact(address));
            }
            return this;
        }

        public Builder addRecipients(Set<Contact> addresses) {
            mRecipients.addAll(addresses);
            return this;
        }

        public Builder addRecipients(Contact... addresses) {
            Collections.addAll(mRecipients, addresses);
            return this;
        }

        public Builder attach(Attachment attachment) {
            mAttachment = attachment;
            return this;
        }

        public Builder message(String message) {
            mMessage = message;
            return this;
        }

        public Text build() {
            Text text = new Text();
            text.mBody = mMessage;
            text.mSender = mSender;
            text.mMembers.addAll(mRecipients);
            if (mRecipients.size() > 1) {
                text.mIsMms = true;
            }
            text.mAttachment = mAttachment;
            if (mAttachment != null) {
                text.mIsMms = true;
            }
            text.mDate = System.currentTimeMillis();
            return text;
        }
    }

    /**
     * Visible for testing
     */
    static class DebugBuilder extends Builder {
        private Contact mSender;
        private long mThreadId;

        public DebugBuilder setSender(String contact) {
            mSender = new Contact(contact);
            return this;
        }

        public DebugBuilder setThreadId(long threadId) {
            mThreadId = threadId;
            return this;
        }

        public Text build() {
            Text text = super.build();
            if (mSender != null) {
                text.mSender = mSender;
            }
            text.mThreadId = mThreadId;
            return text;
        }
    }
}