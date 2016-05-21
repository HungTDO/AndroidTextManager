package com.xlythe.sms.service;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.service.chooser.ChooserTarget;
import android.service.chooser.ChooserTargetService;

import com.xlythe.sms.MessageActivity;
import com.xlythe.sms.drawable.ProfileDrawable;
import com.xlythe.textmanager.text.Contact;
import com.xlythe.textmanager.text.TextManager;
import com.xlythe.textmanager.text.Thread;
import com.xlythe.textmanager.text.util.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@TargetApi(23)
public class FetchChooserTargetService extends ChooserTargetService {
    private static final int SIZE = 3;

    private TextManager mManager;

    @Override
    public void onCreate() {
        super.onCreate();
        mManager = TextManager.getInstance(this);
    }

    @Override
    public List<ChooserTarget> onGetChooserTargets(ComponentName targetActivityName, IntentFilter matchedFilter) {
        final List<ChooserTarget> targets = new ArrayList<>();

        if (!mManager.isDefaultSmsPackage()) {
            // If we can't send texts, then don't have targets.
            return targets;
        }

        final ComponentName componentName = new ComponentName(this, MessageActivity.class);

        List<Thread> recentThreads = getRecentThreads();
        for (Thread thread : recentThreads) {
            final String title = getTitle(thread);
            final Icon icon = getIcon(thread);
            final float score = 1.0f - ((float) recentThreads.indexOf(thread) / recentThreads.size());
            final Bundle extras = new Bundle();
            extras.putString(MessageActivity.EXTRA_THREAD_ID, thread.getId());
            extras.putString(Intent.EXTRA_PHONE_NUMBER, getRecipients(thread));

            targets.add(new ChooserTarget(title, icon, score, componentName, extras));
        }

        return targets;
    }

    private String getTitle(Thread thread) {
        String title = "";
        for (Contact member : mManager.getMembersExceptMe(thread.getLatestMessage()).get()) {
            if (!title.isEmpty()){
                title += ", ";
            }
            title += member.getDisplayName();
        }
        return title;
    }

    private Icon getIcon(Thread thread) {
        ProfileDrawable drawable = new ProfileDrawable(this, mManager.getMembersExceptMe(thread.getLatestMessage()).get());

        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.draw(canvas);

        return Icon.createWithBitmap(bitmap);
    }

    private List<Thread> getRecentThreads() {
        List<Thread> recentThreads = new ArrayList<>(SIZE);
        Thread.ThreadCursor cursor = mManager.getThreadCursor();
        try {
            while (cursor.moveToNext() && recentThreads.size() < SIZE) {
                if (mManager.getMembersExceptMe(cursor.getThread().getLatestMessage()).get().size() == 0) {
                    // Ignore corrupted texts
                    continue;
                }
                recentThreads.add(cursor.getThread());
            }
        } finally {
            cursor.close();
        }
        return recentThreads;
    }

    private String getRecipients(Thread thread) {
        Set<Contact> contacts = mManager.getMembersExceptMe(thread.getLatestMessage()).get();
        return Utils.join(';', contacts, new Utils.Rule<Contact>() {
            @Override
            public String toString(Contact contact) {
                return contact.getNumber(getBaseContext()).get();
            }
        });
    }
}
