package com.xlythe.textmanager;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Parcelable;

/**
 * Represents a person who can send or receive messages.
 */
public interface User {
    String getDisplayName();
    Uri getPhotoUri();
}
