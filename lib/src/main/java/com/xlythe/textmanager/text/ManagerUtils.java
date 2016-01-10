package com.xlythe.textmanager.text;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.provider.MediaStore;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import com.xlythe.textmanager.text.pdu.PduBody;
import com.xlythe.textmanager.text.pdu.PduComposer;
import com.xlythe.textmanager.text.pdu.PduPart;
import com.xlythe.textmanager.text.pdu.SendReq;
import com.xlythe.textmanager.text.smil.SmilHelper;
import com.xlythe.textmanager.text.smil.SmilXmlSerializer;
import com.xlythe.textmanager.text.util.ApnDefaults;
import com.xlythe.textmanager.text.util.CharacterSets;
import com.xlythe.textmanager.text.util.ContentType;
import com.xlythe.textmanager.text.util.EncodedStringValue;
import com.xlythe.textmanager.text.util.HttpUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Created by Niko on 1/1/16.
 */
public class ManagerUtils {
    public static void send(Context context, final Text text){
        String SMS_SENT = "SMS_SENT";
        String SMS_DELIVERED = "SMS_DELIVERED";

        PendingIntent sentPendingIntent = PendingIntent.getBroadcast(context, 0, new Intent(SMS_SENT), 0);
        PendingIntent deliveredPendingIntent = PendingIntent.getBroadcast(context, 0, new Intent(SMS_DELIVERED), 0);

        // For when the SMS has been sent
        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                ContentValues values = new ContentValues();
                Uri uri = Mock.Telephony.Sms.Sent.CONTENT_URI;
                Uri.withAppendedPath(uri, Uri.encode(text.getId()));
                switch (getResultCode()) {
                    case Activity.RESULT_OK:
                        values.put(Mock.Telephony.Sms.Sent.STATUS, Mock.Telephony.Sms.Sent.STATUS_COMPLETE);
                        Toast.makeText(context, "SMS sent successfully", Toast.LENGTH_SHORT).show();
                        break;
                    case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                        values.put(Mock.Telephony.Sms.Sent.STATUS, Mock.Telephony.Sms.Sent.STATUS_FAILED);
                        Toast.makeText(context, "Generic failure cause", Toast.LENGTH_SHORT).show();
                        break;
                    case SmsManager.RESULT_ERROR_NO_SERVICE:
                        values.put(Mock.Telephony.Sms.Sent.STATUS, Mock.Telephony.Sms.Sent.STATUS_FAILED);
                        Toast.makeText(context, "Service is currently unavailable", Toast.LENGTH_SHORT).show();
                        break;
                    case SmsManager.RESULT_ERROR_NULL_PDU:
                        values.put(Mock.Telephony.Sms.Sent.STATUS, Mock.Telephony.Sms.Sent.STATUS_FAILED);
                        Toast.makeText(context, "No pdu provided", Toast.LENGTH_SHORT).show();
                        break;
                    case SmsManager.RESULT_ERROR_RADIO_OFF:
                        values.put(Mock.Telephony.Sms.Sent.STATUS, Mock.Telephony.Sms.Sent.STATUS_FAILED);
                        Toast.makeText(context, "Radio was explicitly turned off", Toast.LENGTH_SHORT).show();
                        break;
                }
                context.getContentResolver().insert(uri, values);
            }
        }, new IntentFilter(SMS_SENT));

        // For when the SMS has been delivered
        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                switch (getResultCode()) {
                    case Activity.RESULT_OK:
                        Toast.makeText(context, "SMS delivered", Toast.LENGTH_SHORT).show();
                        break;
                    case Activity.RESULT_CANCELED:
                        Toast.makeText(context, "SMS not delivered", Toast.LENGTH_SHORT).show();
                        break;
                }
            }
        }, new IntentFilter(SMS_DELIVERED));

        String address = text.getRecipient().getNumber();

        if (!text.isMms()) {
            SmsManager sms = SmsManager.getDefault();
            sms.sendTextMessage(address, null, text.getBody(), sentPendingIntent, deliveredPendingIntent);
        } else {
            List<Attachment> attachment = text.getAttachments();
            sendMediaMessage(context, address, " ", text.getBody(), attachment, sentPendingIntent, deliveredPendingIntent);
        }

        ContentValues values = new ContentValues();
        Uri uri = Mock.Telephony.Sms.Sent.CONTENT_URI;
        values.put(Mock.Telephony.Sms.ADDRESS, address);
        values.put(Mock.Telephony.Sms.BODY, text.getBody());
        values.put(Mock.Telephony.Sms.Sent.STATUS, Mock.Telephony.Sms.Sent.STATUS_PENDING);
        context.getContentResolver().insert(uri, values);
    }

    // TODO: Add backwards compatibility
    @SuppressLint("NewApi")
    public static void sendMediaMessage(final Context context,
                                        final String address,
                                        final String subject,
                                        final String body,
                                        final List<Attachment> attachments,
                                        PendingIntent sentPendingIntent,
                                        PendingIntent deliveredPendingIntent) {
        final ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkRequest.Builder builder = new NetworkRequest.Builder();

        builder.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        builder.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);

        final NetworkRequest networkRequest = builder.build();
        new java.lang.Thread(new Runnable() {
            public void run() {
                connectivityManager.requestNetwork(networkRequest, new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(Network network) {
                        super.onAvailable(network);
                        ConnectivityManager.setProcessDefaultNetwork(network);
                        ArrayList<MMSPart> data = new ArrayList<>();

                        int i = 0;
                        MMSPart part;
                        for(Attachment a: attachments){
                            Attachment.Type type = a.getType();
                            switch(type) {
                                case IMAGE:
                                    byte[] imageBytes = bitmapToByteArray(((ImageAttachment) a).getBitmap());
                                    part = new MMSPart();
                                    part.MimeType = "image/jpeg";
                                    part.Name = "image" + i;
                                    part.Data = imageBytes;
                                    data.add(part);
                                    break;
                                case VIDEO:
                                    try {
                                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                        FileInputStream fis = new FileInputStream(new File(a.getUri().getPath()));

                                        byte[] buf = new byte[1024];
                                        int n;
                                        while (-1 != (n = fis.read(buf)))
                                            baos.write(buf, 0, n);

                                        byte[] videoBytes = baos.toByteArray();

                                        part = new MMSPart();
                                        part.MimeType = "video/mpeg";
                                        part.Name = "video" + i;
                                        part.Data = videoBytes;
                                        data.add(part);
                                    } catch (FileNotFoundException fnfe){
                                        Log.d("Send video","File not found");
                                        fnfe.printStackTrace();
                                    } catch (IOException ioe){

                                    }
                                    break;
                                case VOICE:
                                    //TODO: Voice support
                                    break;
                            }
                            i++;
                        }

                        if (!body.isEmpty()) {
                            // add text to the end of the part and send
                            part = new MMSPart();
                            part.Name = "text";
                            part.MimeType = "text/plain";
                            part.Data = body.getBytes();
                            data.add(part);
                        }

                        byte[] pdu = getBytes(context, address.split(" "), data.toArray(new MMSPart[data.size()]), subject);

                        try {
                            ApnDefaults.ApnParameters apnParameters = ApnDefaults.getApnParameters(context);
                            HttpUtils.httpConnection(
                                    context, 4444L,
                                    apnParameters.getMmscUrl(), pdu, HttpUtils.HTTP_POST_METHOD,
                                    apnParameters.isProxySet(),
                                    apnParameters.getProxyAddress(),
                                    apnParameters.getProxyPort());
                        } catch (IOException ioe) {
                            Log.d("in", "failed");
                        }
                        connectivityManager.unregisterNetworkCallback(this);
                    }
                });
            }
        }).start();
    }

    public static byte[] getBytes(Context context, String[] recipients, MMSPart[] parts, String subject) {
        final SendReq sendRequest = new SendReq();
        // create send request addresses
        for (int i = 0; i < recipients.length; i++) {
            final EncodedStringValue[] phoneNumbers = EncodedStringValue.extract(recipients[i]);
            Log.d("send", recipients[i] + "");
            if (phoneNumbers != null && phoneNumbers.length > 0) {
                sendRequest.addTo(phoneNumbers[0]);
            }
        }
        if (subject != null) {
            sendRequest.setSubject(new EncodedStringValue(subject));
        }
        sendRequest.setDate(Calendar.getInstance().getTimeInMillis() / 1000L);
        TelephonyManager manager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        sendRequest.setFrom(new EncodedStringValue(manager.getLine1Number()));
        final PduBody pduBody = new PduBody();
        // assign parts to the pdu body which contains sending data
        long size = 0;
        if (parts != null) {
            for (int i = 0; i < parts.length; i++) {
                MMSPart part = parts[i];
                if (part != null) {
                    PduPart partPdu = new PduPart();
                    partPdu.setName(part.Name.getBytes());
                    partPdu.setContentType(part.MimeType.getBytes());
                    if (part.MimeType.startsWith("text")) {
                        partPdu.setCharset(CharacterSets.UTF_8);
                    }
                    partPdu.setData(part.Data);
                    pduBody.addPart(partPdu);
                    size += (part.Name.getBytes().length + part.MimeType.getBytes().length + part.Data.length);
                }
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SmilXmlSerializer.serialize(SmilHelper.createSmilDocument(pduBody), out);
        PduPart smilPart = new PduPart();
        smilPart.setContentId("smil".getBytes());
        smilPart.setContentLocation("smil.xml".getBytes());
        smilPart.setContentType(ContentType.APP_SMIL.getBytes());
        smilPart.setData(out.toByteArray());
        pduBody.addPart(0, smilPart);
        sendRequest.setBody(pduBody);
        Log.d("send", "setting message size to " + size + " bytes");
        sendRequest.setMessageSize(size);
        // create byte array which will actually be sent
        final PduComposer composer = new PduComposer(context, sendRequest);
        final byte[] bytesToSend;
        bytesToSend = composer.make();
        return bytesToSend;
    }

    public static byte[] bitmapToByteArray(Bitmap image) {
        if (image == null) {
            Log.v("Message", "image is null, returning byte array of size 0");
            return new byte[0];
        }

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        image.compress(Bitmap.CompressFormat.JPEG, 90, stream);
        return stream.toByteArray();
    }

    public static class MMSPart {
        public String Name = "";
        public String MimeType = "";
        public byte[] Data;
        public Uri Path;
    }
}
