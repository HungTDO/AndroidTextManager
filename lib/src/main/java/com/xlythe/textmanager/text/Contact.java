package com.xlythe.textmanager.text;

import android.content.Context;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;

import com.xlythe.textmanager.User;
import com.xlythe.textmanager.text.util.Utils;

import java.util.LinkedList;
import java.util.List;

/**
 * Represents a phone number.
 */
public final class Contact implements User, Parcelable {
    private final long mId;
    private final String mNumber;
    private final String mDisplayName;
    private final String mPhotoUri;
    private final String mPhotoThumbUri;

    protected Contact(Cursor c) {
        mId = c.getLong(c.getColumnIndexOrThrow(ContactsContract.Contacts._ID));
        // Number may not exist, so check first
        int column = c.getColumnIndex(ContactsContract.PhoneLookup.NUMBER);
        mNumber = column != -1 ? c.getString(column) : null;
        mDisplayName = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME));
        mPhotoUri = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts.PHOTO_URI));
        mPhotoThumbUri = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI));
    }

    protected Contact(String address) {
        mId = -1;
        mNumber = address;
        mDisplayName = null;
        mPhotoUri = null;
        mPhotoThumbUri = null;
    }

    private Contact(Parcel in) {
        mId = in.readLong();
        mNumber = in.readString();
        mPhotoUri = in.readString();
        mDisplayName = in.readString();
        mPhotoThumbUri = in.readString();
    }

    public String getId() {
        return Long.toString(mId);
    }

    public long getIdAsLong() {
        return mId;
    }

    public String getNumber() {
        return mNumber;
    }

    public String getNumber(Context context) {
        return getNumber();
    }

    public List<String> getEmails(Context context) {
        List<String> emailAddresses = new LinkedList<>();

        Uri uri = Email.CONTENT_URI;
        String[] projection = new String[] {
                Email.DATA,
                Email.IS_PRIMARY
        };
        String clause = Email.CONTACT_ID + " = ?";
        String[] args = new String[] { getId() };
        Cursor cursor = context.getContentResolver().query(uri, projection, clause, args, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    do {
                        boolean isPrimary = cursor.getInt(cursor.getColumnIndex(Email.IS_PRIMARY)) != 0;
                        String address = cursor.getString(cursor.getColumnIndex(Email.DATA));

                        if (isPrimary) {
                            emailAddresses.add(0, address);
                        } else {
                            emailAddresses.add(address);
                        }
                    } while (cursor.moveToNext());
                }
            } finally {
                cursor.close();
            }
        }
        return emailAddresses;
    }

    public List<String> getNumbers(Context context) {
        List<String> phoneNumbers = new LinkedList<>();

        Uri uri = Phone.CONTENT_URI;
        String[] projection = new String[] {
                Phone.NUMBER,
                Phone.IS_PRIMARY
        };
        String clause = Phone.CONTACT_ID + " = ?";
        String[] args = new String[] { getId() };
        Cursor cursor = context.getContentResolver().query(uri, projection, clause, args, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    do {
                        boolean isPrimary = cursor.getInt(cursor.getColumnIndex(Phone.IS_PRIMARY)) != 0;
                        String number = cursor.getString(cursor.getColumnIndex(Phone.NUMBER));

                        if (isPrimary) {
                            phoneNumbers.add(0, number);
                        } else {
                            phoneNumbers.add(number);
                        }
                    } while (cursor.moveToNext());
                }
            } finally {
                cursor.close();
            }
        }
        return phoneNumbers;
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
        return mPhotoThumbUri != null ? Uri.parse(mPhotoThumbUri) : null;
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
        out.writeLong(mId);
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

    public static class ContactCursor extends CursorWrapper {
        public ContactCursor(Cursor cursor) {
            super(cursor);
        }

        public Contact getContact() {
            return new Contact(this);
        }
    }

    public enum Sort {
        Alphabetical(ContactsContract.Contacts.DISPLAY_NAME + " ASC"),
        FrequentlyContacted(ContactsContract.Contacts.TIMES_CONTACTED + " ASC");
        private final String key;

        Sort(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }
    }
}
