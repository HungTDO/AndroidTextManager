package com.xlythe.textmanager.text;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import com.xlythe.textmanager.User;
import com.xlythe.textmanager.text.util.Utils;

/**
 * Represents a phone number.
 */
public final class Contact implements User, Parcelable {

    private String mNumber;
    private String mPhotoUri;
    private String mDisplayName;
    private String mPhotoThumbUri;

    protected Contact(Cursor c) {
//        mId = c.getString(c.getColumnIndex("_id"));
//        mType = c.getString(c.getColumnIndex("type"));
//        mTimesContacted = c.getString(c.getColumnIndex("times_contacted"));
        mNumber = c.getString(c.getColumnIndex("number"));
        mPhotoUri = c.getString(c.getColumnIndex("photo_uri"));
//        mSendToVoicemail = c.getString(c.getColumnIndex("send_to_voicemail"));
//        mLookup = c.getString(c.getColumnIndex("lookup"));
        mDisplayName = c.getString(c.getColumnIndex("display_name"));
//        mLastTimeContacted = c.getString(c.getColumnIndex("last_time_contacted"));
//        mHasPhoneNumber = c.getString(c.getColumnIndex("has_phone_number"));
//        mInVisibleGroup = c.getString(c.getColumnIndex("in_visible_group"));
//        mPhotoFileId = c.getString(c.getColumnIndex("photo_file_id"));
//        mLabel = c.getString(c.getColumnIndex("label"));
//        mStarred = c.getString(c.getColumnIndex("starred"));
//        mNormalizedNumber = c.getString(c.getColumnIndex("normalized_number"));
        mPhotoThumbUri = c.getString(c.getColumnIndex("photo_thumb_uri"));
//        mPhotoId = c.getString(c.getColumnIndex("photo_id"));
//        mInDefaultDirectory = c.getString(c.getColumnIndex("in_default_directory"));
//        mCustomRingtone = c.getString(c.getColumnIndex("custom_ringtone"));
    }

    protected Contact(String address) {
        mNumber = address;
    }

    private Contact(Parcel in) {
        mNumber = in.readString();
        mPhotoUri = in.readString();
        mDisplayName = in.readString();
        mPhotoThumbUri = in.readString();
    }

    public String getNumber() {
        return mNumber;
    }

    public String getDisplayName() {
        return hasName() ? mDisplayName : getNumber();
    }

    public boolean hasName() {
        return mDisplayName != null;
    }

    public Bitmap getPhoto() {
        return null;
    }

    public Uri getPhotoThumbUri(){
        return mPhotoThumbUri!=null ? Uri.parse(mPhotoThumbUri) : null;
    }

    public Uri getPhotoUri(){
        return mPhotoUri!=null ? Uri.parse(mPhotoUri) : null;
    }

    public Drawable getPhotoThumbDrawable(){
        return null;
    }

    public Drawable getPhotoDrawable(){
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (o != null && o instanceof Contact) {
            Contact a = (Contact) o;
            return Utils.equals(mNumber, a.mNumber)
                    && Utils.equals(mPhotoUri, a.mPhotoUri)
                    && Utils.equals(mDisplayName, a.mDisplayName)
                    && Utils.equals(mPhotoThumbUri, a.mPhotoThumbUri);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Utils.hashCode(mNumber)
                + Utils.hashCode(mPhotoUri)
                + Utils.hashCode(mDisplayName)
                + Utils.hashCode(mPhotoThumbUri);
    }

    @Override
    public String toString() {
        return String.format("Contact{number=%s, photo_uri=%s, display_name=%s, photo_thumb_uri=%s}",
                mNumber, mPhotoUri, mDisplayName, mPhotoThumbUri);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(mNumber);
        out.writeString(mPhotoUri);
        out.writeString(mDisplayName);
        out.writeString(mPhotoThumbUri);
    }

    public static final Parcelable.Creator<Contact> CREATOR = new Parcelable.Creator<Contact>() {
        public Contact createFromParcel(Parcel in) {
            return new Contact(in);
        }

        public Contact[] newArray(int size) {
            return new Contact[size];
        }
    };
}
