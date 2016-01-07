package com.xlythe.textmanager.text;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.util.Log;

import com.xlythe.textmanager.MessageCallback;
import com.xlythe.textmanager.MessageManager;
import com.xlythe.textmanager.MessageObserver;
import com.xlythe.textmanager.text.exception.MmsException;
import com.xlythe.textmanager.text.pdu.PduParser;
import com.xlythe.textmanager.text.pdu.PduPersister;
import com.xlythe.textmanager.text.pdu.RetrieveConf;
import com.xlythe.textmanager.text.util.SimpleLruCache;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Manages sms and mms messages
 */
public class TextManager implements MessageManager<Text, Thread, Contact> {
    private static final int CACHE_SIZE = 50;
    public static final String[] PROJECTION = new String[] {
            // Determine if message is SMS or MMS
            Mock.Telephony.MmsSms.TYPE_DISCRIMINATOR_COLUMN,
            // Base item ID
            BaseColumns._ID,
            // Conversation (thread) ID
            Mock.Telephony.Sms.Conversations.THREAD_ID,
            // Date values
            Mock.Telephony.Sms.DATE,
            Mock.Telephony.Sms.DATE_SENT,
            // For SMS only
            Mock.Telephony.Sms.ADDRESS,
            Mock.Telephony.Sms.BODY,
            Mock.Telephony.Sms.TYPE,
            // For MMS only
            Mock.Telephony.Mms.SUBJECT,
            Mock.Telephony.Mms.MESSAGE_BOX
    };

    private static TextManager sTextManager;

    private Context mContext;
    private final Set<MessageObserver> mObservers = new HashSet<>();
    private final SimpleLruCache<String, Contact> mContactCache = new SimpleLruCache<>(CACHE_SIZE);

    public static TextManager getInstance(Context context) {
        if (sTextManager == null) {
            sTextManager = new TextManager(context);
        }
        return sTextManager;
    }

    private TextManager(Context context) {
        mContext = context;
        context.getContentResolver().registerContentObserver(Mock.Telephony.MmsSms.CONTENT_CONVERSATIONS_URI, true, new TextObserver(new Handler()));
    }

    public void downloadAttachment(Text text){
        if (text.isMms()) {
            PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
            PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MMS StoreMedia");
            wl.acquire();
            final Uri uri = Uri.withAppendedPath(Mock.Telephony.Mms.CONTENT_URI, text.getId());
            Receive.getPdu(uri, mContext, new Receive.DataCallback() {
                @Override
                public void onSuccess(byte[] result) {
                    RetrieveConf retrieveConf = (RetrieveConf) new PduParser(result, true).parse();

                    PduPersister persister = PduPersister.getPduPersister(mContext);
                    Uri msgUri;
                    try {
                        msgUri = persister.persist(retrieveConf, uri, true, true, null);

                        // Use local time instead of PDU time
                        ContentValues values = new ContentValues(1);
                        values.put(Mock.Telephony.Mms.DATE, System.currentTimeMillis() / 1000L);
                        mContext.getContentResolver().update(msgUri, values, null, null);
                    } catch (MmsException e) {
                        Log.e("MMS", "unable to persist message");
                        onFail();
                    }
                }

                @Override
                public void onFail() {
                    // this maybe useful
                }
            });
            wl.release();
        }
    }
    @Override
    public void getMessages(final Thread thread, final MessageCallback<List<Text>> callback) {
        // Create a handler so we call back on the same thread we were called on
        final Handler handler = new Handler();

        // Then start a background thread
        new java.lang.Thread() {
            @Override
            public void run() {
                // getMessages is a long running operation
                final List<Text> threads = getMessages(thread);

                // Return the list in the callback
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onSuccess(threads);
                    }
                });
            }
        }.start();
    }

    public void registerObserver(MessageObserver observer) {
        mObservers.add(observer);
    }

    public void unregisterObserver(MessageObserver observer) {
        mObservers.remove(observer);
    }

    public List<Text> search(String text) {
        return new LinkedList<>(); // TODO
    }

    public void search(final String text, final MessageCallback<List<Text>> callback) {
        // Create a handler so we call back on the same thread we were called on
        final Handler handler = new Handler();

        // Then start a background thread
        new java.lang.Thread() {
            @Override
            public void run() {
                // search is a long running operation
                final List<Text> threads = search(text);

                // Return the list in the callback
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onSuccess(threads);
                    }
                });
            }
        }.start();
    }

    private class TextObserver extends ContentObserver {
        TextObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            this.onChange(selfChange, null);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            for (MessageObserver observer : mObservers) {
                observer.notifyDataChanged();
            }
        }
    }

    @Override
    public void send(final Text text) {
        ManagerUtils.send(mContext, text);
    }

    @Override
    public List<Text> getMessages(Thread thread) {
        List<Text> messages = new ArrayList<>();
        Text.TextCursor c = getMessageCursor(thread);
        if (c.moveToFirst()) {
            do {
                messages.add(c.getText());
            } while (c.moveToNext());
        }
        c.close();
        return messages;
    }

    @Override
    public Text.TextCursor getMessageCursor(Thread thread) {
        ContentResolver contentResolver = mContext.getContentResolver();
        final String[] projection = PROJECTION;
        final Uri uri = Uri.parse(Mock.Telephony.MmsSms.CONTENT_CONVERSATIONS_URI +"/"+ thread.getId());
        final String order = "normalized_date ASC";
        return new Text.TextCursor(mContext, contentResolver.query(uri, projection, null, null, order));
    }

    @Override
    public void getMessage(final String id, final MessageCallback<Text> callback) {
        // Create a handler so we call back on the same thread we were called on
        final Handler handler = new Handler();

        // Then start a background thread
        new java.lang.Thread() {
            @Override
            public void run() {
                // getMessage is a long running operation
                final Text text = getMessage(id);

                // Return the list in the callback
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onSuccess(text);
                    }
                });
            }
        }.start();
    }

    @Override
    public Text getMessage(String messageId) {
        return null; // TODO
    }

    @Override
    public void getThreads(final MessageCallback<List<Thread>> callback) {
        // Create a handler so we call back on the same thread we were called on
        final Handler handler = new Handler();

        // Then start a background thread
        new java.lang.Thread() {
            @Override
            public void run() {
                // getThreads is a long running operation
                final List<Thread> threads = getThreads();

                // Return the list in the callback
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onSuccess(threads);
                    }
                });
            }
        }.start();
    }

    @Override
    public List<Thread> getThreads() {
        List<Thread> threads = new ArrayList<>();
        Cursor c = getThreadCursor();
        if (c.moveToFirst()) {
            do {
                threads.add(new Thread(mContext, c));
            } while (c.moveToNext());
        }
        c.close();
        return threads;
    }

    @Override
    public Cursor getThreadCursor() {
        ContentResolver contentResolver = mContext.getContentResolver();
        final Uri uri = Mock.Telephony.MmsSms.CONTENT_CONVERSATIONS_URI;
        final String order = "normalized_date DESC";
        return contentResolver.query(uri, null, null, null, order);
    }

    @Override
    public void getThread(final String id, final MessageCallback<Thread> callback) {
        // Create a handler so we call back on the same thread we were called on
        final Handler handler = new Handler();

        // Then start a background thread
        new java.lang.Thread() {
            @Override
            public void run() {
                // getThread is a long running operation
                final Thread thread = getThread(id);

                // Return the list in the callback
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onSuccess(thread);
                    }
                });
            }
        }.start();
    }

    @Override
    public Thread getThread(String threadId) {
        return null; // TODO
    }

    @Override
    public void delete(Text message) {
        String clause = String.format("%s = %s",
                Mock.Telephony.Sms._ID, message.getId());
        mContext.getContentResolver().delete(Mock.Telephony.MmsSms.CONTENT_CONVERSATIONS_URI, clause, null);
    }

    @Override
    public void delete(Thread thread) {
        String clause = String.format("%s = %s",
                Mock.Telephony.Sms.THREAD_ID, thread.getId());
        mContext.getContentResolver().delete(Mock.Telephony.MmsSms.CONTENT_CONVERSATIONS_URI, clause, null);
    }

    @Override
    public void markAsRead(Text message) {
        ContentValues values = new ContentValues();
        values.put(Mock.Telephony.Sms.READ, true);
        String clause = String.format("%s = %s",
                Mock.Telephony.Sms._ID, message.getId());
        mContext.getContentResolver().update(Mock.Telephony.MmsSms.CONTENT_CONVERSATIONS_URI, values, clause, null);
    }

    @Override
    public void markAsRead(Thread thread) {
        ContentValues values = new ContentValues();
        values.put(Mock.Telephony.Sms.READ, true);
        String clause = String.format("%s = %s AND %s = %s",
                Mock.Telephony.Sms.THREAD_ID, thread.getId(),
                Mock.Telephony.Sms.READ, 0);
        mContext.getContentResolver().update(Mock.Telephony.MmsSms.CONTENT_CONVERSATIONS_URI, values, clause, null);
    }

    public Contact lookupContact(String phoneNumber) {
        Contact contact = mContactCache.get(phoneNumber);
        if (contact == null) {
            ContentResolver contentResolver = mContext.getContentResolver();
            Uri uri = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(phoneNumber));

            Cursor c = contentResolver.query(uri, null, null, null, null);
            try {
                if (c != null && c.moveToFirst()) {
                    contact = new Contact(c);
                } else {
                    contact = new Contact(phoneNumber);
                }
            } finally {
                if (c != null) c.close();
            }
            mContactCache.add(phoneNumber, contact);
        }
        return contact;

    }
}