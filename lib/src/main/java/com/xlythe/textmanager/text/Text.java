package com.xlythe.textmanager.text;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.Telephony;
import android.text.TextUtils;
import android.util.Log;

import com.xlythe.textmanager.Message;
import com.xlythe.textmanager.MessageCallback;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Either an sms or an mms
 */
public class Text implements Message, Comparable{
    private static final String TYPE_SMS = "sms";
    private static final String TYPE_MMS = "mms";
    private static final long MILLI_TO_SEC = 1000;

    static final String[] MMS_PROJECTION = new String[] {
            BaseColumns._ID,
            Telephony.Mms.Part.CONTENT_TYPE,
            Telephony.Mms.Part.TEXT,
            Telephony.Mms.Part._DATA
    };

    private long mId;
    private long mThreadId;
    private long mDate;
    private String mAddress;
    private String mBody;
    private boolean mIncoming;
    private Uri mAttachment;

    private Text() { }

    protected Text(Context context, Cursor cursor, String myNumber) {
        String type = getMessageType(cursor);
        if (TYPE_SMS.equals(type)){
            parseSmsMessage(cursor);
        }
        else if (TYPE_MMS.equals(type)){
            parseMmsMessage(context, cursor, myNumber);
        }
        else {
            Log.w("TelephonyProvider", "Unknown Message Type");
        }
    }

    private String getMessageType(Cursor cursor) {
        int typeIndex = cursor.getColumnIndex(Telephony.MmsSms.TYPE_DISCRIMINATOR_COLUMN);
        if (typeIndex < 0) {
            // Type column not in projection, use another discriminator
            String cType = cursor.getString(cursor.getColumnIndex(Telephony.Mms.CONTENT_TYPE));
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
        mThreadId = data.getLong(data.getColumnIndexOrThrow(Telephony.Sms.Conversations.THREAD_ID));
        mDate = data.getLong(data.getColumnIndexOrThrow(Telephony.Sms.Conversations.DATE));

        mAddress = data.getString(data.getColumnIndexOrThrow(Telephony.Sms.ADDRESS));
        mBody = data.getString(data.getColumnIndexOrThrow(Telephony.Sms.BODY));
        mIncoming = isIncomingMessage(data, true);
    }

    private void parseMmsMessage(Context context, Cursor data, String myNumber) {
        mId = data.getLong(data.getColumnIndexOrThrow(BaseColumns._ID));
        mThreadId = data.getLong(data.getColumnIndexOrThrow(Telephony.Sms.Conversations.THREAD_ID));
        mDate = data.getLong(data.getColumnIndexOrThrow(Telephony.Sms.Conversations.DATE))*MILLI_TO_SEC;
        mIncoming = isIncomingMessage(data, false);

        long _id = data.getLong(data.getColumnIndexOrThrow(BaseColumns._ID));

        // Query the address information for this message
        Uri addressUri = Uri.withAppendedPath(Telephony.Mms.CONTENT_URI, _id + "/addr");// TODO Telephony.Mms.CONTENT_URL is api 19+
        Cursor addr = context.getContentResolver().query(
                addressUri,
                null,
                null,
                null,
                null
        );
        HashSet<String> recipients = new HashSet<>();
        while (addr.moveToNext()) {
            String address = addr.getString(addr.getColumnIndex(Telephony.Mms.Addr.ADDRESS));
            // Don't add our own number to the displayed list
            if (myNumber == null || !address.contains(myNumber)) {
                recipients.add(address);
            }
        }
        mAddress = TextUtils.join(",", recipients);
        addr.close();

        // Query all the MMS parts associated with this message
        Uri messageUri = Uri.withAppendedPath(Telephony.Mms.CONTENT_URI, _id + "/part");
        Cursor inner = context.getContentResolver().query(
                messageUri,
                MMS_PROJECTION,
                Telephony.Mms.Part.MSG_ID + " = ?",
                new String[] {String.valueOf((data.getLong(data.getColumnIndex(Telephony.Mms._ID))))},
                null
        );

        while (inner.moveToNext()) {
            String contentType = inner.getString(inner.getColumnIndex(Telephony.Mms.Part.CONTENT_TYPE));
            if (contentType == null) {
                continue;
            }
            else if (contentType.matches("image/.*")) {
                // Find any part that is an image attachment
                long partId = inner.getLong(inner.getColumnIndex(BaseColumns._ID));
                mAttachment = Uri.withAppendedPath(Telephony.Mms.CONTENT_URI, "part/" + partId);
            }
            else if (contentType.matches("text/.*")) {
                // Find any part that is text data
                mBody = inner.getString(inner.getColumnIndex(Telephony.Mms.Part.TEXT));
            }
        }
        inner.close();
    }

    private static boolean isIncomingMessage(Cursor cursor, boolean isSMS) {
        int boxId;
        if (isSMS) {
            boxId = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE));
            return (boxId == Telephony.TextBasedSmsColumns.MESSAGE_TYPE_INBOX ||
                    boxId == Telephony.TextBasedSmsColumns.MESSAGE_TYPE_ALL);
        }
        else {
            boxId = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Mms.MESSAGE_BOX));
            return (boxId == Telephony.Mms.MESSAGE_BOX_INBOX ||
                    boxId == Telephony.Mms.MESSAGE_BOX_ALL);
        }
    }

    public long getId() {
        return mId;
    }

    public String getAddress() {
        return mAddress;
    }

    public String getBody() {
        return mBody;
    }

    public long getDate() {
        return mDate;
    }

    public long getThreadId() {
        return mThreadId;
    }

    public Uri getAttachment() {
        return mAttachment;
    }

    public boolean getIncoming() {
        return mIncoming;
    }

    @Override
    public int compareTo(Object o) {
        float myDate = getDate();
        float theirDate = ((Text) o).getDate();
        if(myDate > theirDate) {
            return 1;
        }
        if(theirDate > myDate) {
            return -1;
        }
        return 0;
    }

    public static class Builder {
        private String mMessage;
        private String mRecipient;

        public Builder() {
        }

        public Builder drawable(Drawable drawable) {
            // TODO support mms
            return this;
        }

        public Builder recipient(String recipient) {
            mRecipient = recipient;
            return this;
        }

        public Builder message(String message) {
            mMessage = message;
            return this;
        }

        public Text build() {
            Text text = new Text();
            text.mBody = mMessage;
            text.mAddress = mRecipient;
            return text;
        }
    }
}