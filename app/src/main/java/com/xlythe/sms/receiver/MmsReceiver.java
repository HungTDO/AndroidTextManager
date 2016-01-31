package com.xlythe.sms.receiver;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.provider.MediaStore;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;

import com.xlythe.sms.MainActivity;
import com.xlythe.sms.R;
import com.xlythe.textmanager.text.Attachment;
import com.xlythe.textmanager.text.ImageAttachment;
import com.xlythe.textmanager.text.Text;

import java.io.IOException;

public class MmsReceiver extends com.xlythe.textmanager.text.TextReceiver {

    @Override
    public void onMessageReceived(Context context, Text text) {
        Intent dismissIntent = new Intent(context, MainActivity.class);
        PendingIntent piDismiss = PendingIntent.getService(context, 0, dismissIntent, 0);
        Bitmap bitmap = null;
        try {
            if (text.getAttachments().get(0).getType() == Attachment.Type.IMAGE) {
                bitmap = MediaStore.Images.Media.getBitmap(context.getContentResolver(), text.getAttachments().get(0).getUri());
            }
        } catch (IOException ioe){
            // TODO: throw
        }
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context)
                        .setLargeIcon(bitmap)
                        .setSmallIcon(R.drawable.user_icon)
                        .setContentTitle("")
                        .setContentText("")
                        .setAutoCancel(true)
                        .setLights(Color.WHITE, 500, 1500)
                        .setDefaults(Notification.DEFAULT_SOUND)
                        .setPriority(Notification.PRIORITY_HIGH)
                        .setCategory(Notification.CATEGORY_MESSAGE)
                        .addAction(R.mipmap.ic_launcher, "Reply", piDismiss);

        NotificationCompat.BigPictureStyle notiStyle = new NotificationCompat.BigPictureStyle();
        notiStyle.setBigContentTitle("");
        notiStyle.setSummaryText("");
        notiStyle.bigPicture(bitmap);
        builder.setStyle(notiStyle);

        Intent resultIntent = new Intent(context, MainActivity.class);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        stackBuilder.addParentStack(MainActivity.class);
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(
                        0,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );
        builder.setContentIntent(resultPendingIntent);

        NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(12345, builder.build());
    }
}