package com.xlythe.textmanager.text;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

import com.xlythe.textmanager.text.Mock;
import com.xlythe.textmanager.text.Receive;
import com.xlythe.textmanager.text.Text;
import com.xlythe.textmanager.text.exception.MmsException;
import com.xlythe.textmanager.text.pdu.NotificationInd;
import com.xlythe.textmanager.text.util.ContentType;
import com.xlythe.textmanager.text.pdu.GenericPdu;
import com.xlythe.textmanager.text.pdu.PduBody;
import com.xlythe.textmanager.text.pdu.PduParser;
import com.xlythe.textmanager.text.pdu.PduPart;
import com.xlythe.textmanager.text.pdu.PduPersister;
import com.xlythe.textmanager.text.pdu.RetrieveConf;
import com.xlythe.textmanager.text.util.EncodedStringValue;

import java.util.Objects;

import static android.provider.Telephony.Sms.Intents.SMS_RECEIVED_ACTION;
import static android.provider.Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION;
import static android.provider.Telephony.Sms.Intents.SMS_DELIVER_ACTION;
import static android.provider.Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION;
import static android.provider.Telephony.Sms.Intents.getMessagesFromIntent;

public abstract class TextReceiver extends BroadcastReceiver {
    private final String TAG = getClass().getSimpleName();

    public static final String ACTION_TEXT_RECEIVED = "com.xlythe.textmanager.text.ACTION_TEXT_RECEIVED";
    public static final String EXTRA_TEXT = "text";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_TEXT_RECEIVED.equals(intent.getAction())) {
            onMessageReceived(context, (Text) intent.getParcelableExtra(EXTRA_TEXT));
        } else if (WAP_PUSH_DELIVER_ACTION.equals(intent.getAction()) && ContentType.MMS_MESSAGE.equals(intent.getType())) {
            Log.v(TAG, "Received PUSH Intent: " + intent);

            // Hold a wake lock for 5 seconds, enough to give any
            // services we start time to take their own wake locks.
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MMS PushReceiver");
            wl.acquire(5000);
            new ReceivePushTask(context).execute(intent);
        } else if (SMS_DELIVER_ACTION.equals(intent.getAction())) {
            SmsMessage[] messages = getMessagesFromIntent(intent);
            Text text = Receive.storeMessage(context, messages, 0);
            onMessageReceived(context, text);
        //} else if (android.os.Build.VERSION.SDK_INT < 19 && SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            //onMessageReceived(context, text);
        }
    }

    private class ReceivePushTask extends AsyncTask<Intent, Void, Void> {
        Context mContext;
        public ReceivePushTask(Context context) {
            mContext = context;
        }

        @Override
        protected Void doInBackground(Intent... intents) {
            PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
            final PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MMS StoreMedia");
            wl.acquire();
            Intent intent = intents[0];

            byte[] pushData = intent.getByteArrayExtra("data");
            String pd = new String(pushData);
            Log.d("pushData", pd + "");

            final PduParser parser = new PduParser(pushData, true);
            final GenericPdu pdu = parser.parse();
            NotificationInd notif = (NotificationInd) pdu;

            if (pdu == null) {
                Log.e(TAG, "Invalid PUSH data");
                return null;
            }

            byte[] location = notif.getContentLocation();
            String loc = new String (location);
            Log.d(TAG, "Content location: " + loc);
            Receive.getPdu(loc, mContext, new Receive.DataCallback() {
                @Override
                public void onSuccess(byte[] result) {
                    Log.e(TAG, "Download Success");
                    RetrieveConf retrieveConf = (RetrieveConf) new PduParser(result, true).parse();
                    PduPersister persister = PduPersister.getPduPersister(mContext);
                    Uri msgUri;
                    try {
                        msgUri = persister.persist(retrieveConf, Mock.Telephony.Mms.Inbox.CONTENT_URI, true, true, null);

                        // Use local time instead of PDU time
                        ContentValues values = new ContentValues(1);
                        values.put(Telephony.Mms.DATE, System.currentTimeMillis() / 1000L);
                        mContext.getContentResolver().update(msgUri, values, null, null);

                        Cursor c = mContext.getContentResolver().query(msgUri, null, null, null, null);
                        if (c == null) return;
                        c.moveToFirst();
                        Text text = new Text(mContext, c);
                        c.close();
                        onMessageReceived(mContext, text);
                    } catch (MmsException e) {
                        Log.e(TAG, "unable to persist message");
                        onFail();
                    }
                }

                @Override
                public void onFail() {
                    PduPersister p = PduPersister.getPduPersister(mContext);
                    try {
                        p.persist(pdu, Mock.Telephony.Mms.Inbox.CONTENT_URI, true, true, null);
                    } catch (MmsException e) {
                        Log.e(TAG, "persisting pdu failed");
                        e.printStackTrace();
                    }
                }
            });
            wl.release();
            return null;
        }
    }

    public abstract void onMessageReceived(Context context, Text text);
}
