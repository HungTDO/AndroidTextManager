package com.xlythe.sms;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.design.widget.AppBarLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;

import com.commonsware.cwac.camera.CameraHost;
import com.commonsware.cwac.camera.CameraHostProvider;
import com.commonsware.cwac.camera.PictureTransaction;
import com.commonsware.cwac.camera.SimpleCameraHost;
import com.xlythe.sms.adapter.MessageAdapter;
import com.xlythe.sms.fragment.CameraFragment;
import com.xlythe.sms.fragment.StickerFragment;
import com.xlythe.sms.fragment.GalleryFragment;
import com.xlythe.sms.fragment.MicFragment;
import com.xlythe.sms.receiver.Notifications;
import com.xlythe.sms.util.ColorUtils;
import com.xlythe.sms.view.ExtendedEditText;
import com.xlythe.sms.view.ICameraView;
import com.xlythe.sms.view.LegacyCameraView;
import com.xlythe.textmanager.MessageObserver;
import com.xlythe.textmanager.text.Attachment;
import com.xlythe.textmanager.text.Contact;
import com.xlythe.textmanager.text.Text;
import com.xlythe.textmanager.text.TextManager;
import com.xlythe.textmanager.text.Thread;

import java.util.Set;

public class MessageActivity extends AppCompatActivity
        implements MessageAdapter.FailedViewHolder.ClickListener, LegacyCameraView.HostProvider /* legacy support */ {
    private static final String TAG = TextManager.class.getSimpleName();
    private static final boolean DEBUG = true;
    public static final String EXTRA_THREAD = "thread";

    // Keyboard hack
    private int mScreenSize;
    private int mKeyboardSize;
    private boolean mAdjustNothing;
    private boolean mKeyboardOpen;

    private AppBarLayout mAppbar;
    private View mAttachView;
    private ExtendedEditText mEditText;
    private ImageView mSendButton;
    private Thread mThread;
    private TextManager mManager;
    private RecyclerView mRecyclerView;
    private MessageAdapter mAdapter;
    private final MessageObserver mMessageObserver = new MessageObserver() {
        @Override
        public void notifyDataChanged() {
            mAdapter.swapCursor(mManager.getMessageCursor(mThread));
            mManager.markAsRead(mThread);
        }
    };

    private ActionModeCallback mActionModeCallback = new ActionModeCallback();
    private ActionMode mActionMode;

    private ImageView mGalleryAttachments;
    private ImageView mCameraAttachments;
    private ImageView mStickerAttachments;
    private ImageView mMicAttachments;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_message);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        Notifications.clearNotifications(getApplicationContext());

        final Window rootWindow = getWindow();
        final View root = rootWindow.getDecorView().findViewById(android.R.id.content);

        // Seems redundant to set as ADJUST_NOTHING in manifest and then immediately to ADJUST_RESIZE
        // but it seems that the input gets reset to a default on keyboard dismissal if not set otherwise.
        rootWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        root.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            public void onGlobalLayout() {
                Rect r = new Rect();
                View view = rootWindow.getDecorView();
                view.getWindowVisibleDisplayFrame(r);
                if (mScreenSize != 0 && mScreenSize != r.bottom) {
                    mKeyboardSize = mScreenSize - r.bottom;
                    mAttachView.getLayoutParams().height = mKeyboardSize;
                    rootWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
                    if (mKeyboardOpen) {
                        mAttachView.setVisibility(View.VISIBLE);
                    }
                    mAdjustNothing = true;
                } else {
                    mScreenSize = r.bottom;
                }
            }
        });

        mAppbar = (AppBarLayout) findViewById(R.id.appbar);

        mAttachView = findViewById(R.id.fragment_container);
        mEditText = (ExtendedEditText) findViewById(R.id.edit_text);
        mSendButton = (ImageView) findViewById(R.id.send);

        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mManager.send(new Text.Builder()
                                .message(mEditText.getText().toString())
                                .addRecipients(mThread.getLatestMessage().getMembersExceptMe(getApplicationContext()))
                                .build()
                );
                mEditText.setText(null);
                setSendable(false);
            }
        });

        mEditText.setOnDismissKeyboardListener(new ExtendedEditText.OnDismissKeyboardListener() {
            @Override
            public void onDismissed() {
                mEditText.clearFocus();
                onAttachmentHidden();
            }
        });

        mEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                setSendable(s.length() > 0);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        mEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                mKeyboardOpen = hasFocus;
                if(hasFocus) {
                    clearAttachmentSelection();
                    if (!mAdjustNothing) {
                        onAttachmentHidden();
                    } else {
                        mAttachView.setVisibility(View.VISIBLE);
                    }
                }
            }
        });

        mManager = TextManager.getInstance(getBaseContext());
        mThread = getIntent().getParcelableExtra(EXTRA_THREAD);
        final Drawable upArrow = getResources().getDrawable(R.drawable.abc_ic_ab_back_mtrl_am_alpha);
        upArrow.setColorFilter(getResources().getColor(R.color.icon_color), PorterDuff.Mode.SRC_ATOP);
        getSupportActionBar().setHomeAsUpIndicator(upArrow);

        String name = "";
        for (Contact member : mThread.getLatestMessage().getMembersExceptMe(this)) {
            if (!name.isEmpty()){
                name += ", ";
            }
            name += member.getDisplayName();
        }
        getSupportActionBar().setTitle(Html.fromHtml("<font color='#212121'>" + name + " </font>"));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (android.os.Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(ColorUtils.getDarkColor(mThread.getIdAsLong()));
        }

        mRecyclerView = (RecyclerView) findViewById(R.id.list);
        mRecyclerView.setHasFixedSize(false);

        final LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        // maybe add transcript mode, and show a notification of new messages
        mRecyclerView.setLayoutManager(layoutManager);

        mAdapter = new MessageAdapter(this, mManager.getMessageCursor(mThread));
        mAdapter.setOnClickListener(this);
        mRecyclerView.setAdapter(mAdapter);

        mManager.registerObserver(mMessageObserver);

        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState > 0) {
                    InputMethodManager mgr = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    mgr.hideSoftInputFromWindow(mEditText.getWindowToken(), 0);
                    mEditText.clearFocus();
                    onAttachmentHidden();
                }
            }
        });

        mGalleryAttachments = (ImageView) findViewById(R.id.gallery);
        mCameraAttachments = (ImageView) findViewById(R.id.camera);
        mStickerAttachments = (ImageView) findViewById(R.id.sticker);
        mMicAttachments = (ImageView) findViewById(R.id.mic);

        // TODO: Unhide when support is ready
        mStickerAttachments.setVisibility(View.INVISIBLE);
        mMicAttachments.setVisibility(View.INVISIBLE);
    }

    public void setSendable(boolean sendable){
        mSendButton.setEnabled(sendable);
        if (sendable) {
            mSendButton.setColorFilter(ColorUtils.getColor(Long.parseLong(mThread.getId())), PorterDuff.Mode.SRC_ATOP);
        } else {
            mSendButton.clearColorFilter();
        }
    }

    public void onAttachmentClicked(View view){
        InputMethodManager mgr = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        mgr.hideSoftInputFromWindow(mEditText.getWindowToken(), 0);
        mEditText.clearFocus();

        mAttachView.setVisibility(View.VISIBLE);

        clearAttachmentSelection();
        int color = ColorUtils.getColor(mThread.getIdAsLong());
        ((ImageView) view).setColorFilter(color, PorterDuff.Mode.SRC_ATOP);

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        Fragment fragment;
        Bundle args = new Bundle();
        args.putInt(GalleryFragment.ARG_COLOR, color);
        args.putParcelable(GalleryFragment.ARG_MESSAGE, mThread.getLatestMessage());
        switch (view.getId()) {
            case R.id.gallery:
                fragment = new GalleryFragment();
                fragment.setArguments(args);
                transaction.replace(R.id.fragment_container, fragment).commit();
                break;
            case R.id.camera:
                fragment = new CameraFragment();
                fragment.setArguments(args);
                transaction.replace(R.id.fragment_container, fragment).commit();
                break;
            case R.id.sticker:
                transaction.replace(R.id.fragment_container, new StickerFragment()).commit();
                break;
            case R.id.mic:
                transaction.replace(R.id.fragment_container, new MicFragment()).commit();
                break;
        }
    }

    public void onAttachmentHidden(){
        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, new Fragment()).commit();
        mAttachView.setVisibility(View.GONE);
        clearAttachmentSelection();
    }

    private void clearAttachmentSelection() {
        mGalleryAttachments.clearColorFilter();
        mCameraAttachments.clearColorFilter();
        mStickerAttachments.clearColorFilter();
        mMicAttachments.clearColorFilter();
    }

    @Override
    protected void onDestroy() {
        mAdapter.destroy();
        mManager.unregisterObserver(mMessageObserver);
        super.onDestroy();
    }

    @Override
    public void onItemClicked(Text text) {
        if (mActionMode != null) {
            toggleSelection(text);
        } else if (text.getAttachment() != null
                && (text.getAttachment().getType() == Attachment.Type.IMAGE
                || text.getAttachment().getType() == Attachment.Type.VIDEO)) {
            Intent i = new Intent(getBaseContext(), MediaActivity.class);
            i.putExtra(MediaActivity.EXTRA_TEXT, text);
            startActivity(i);
        }
    }

    @Override
    public boolean onItemLongClicked(Text text) {
        if (mActionMode == null) {
            mActionMode = startSupportActionMode(mActionModeCallback);
        }
        toggleSelection(text);
        return true;
    }

    private void toggleSelection(Text text) {
        mAdapter.toggleSelection(text);
        int count = mAdapter.getSelectedItemCount();

        if (count == 0) {
            mActionMode.finish();
        } else {
            mActionMode.setTitle(String.valueOf(count)+" selected");
            mActionMode.invalidate();
        }
    }

    private class ActionModeCallback implements ActionMode.Callback {

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate (R.menu.menu_message, menu);
            menu.findItem(R.id.menu_remove).setVisible(TextManager.getInstance(getBaseContext()).isDefaultSmsPackage());
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            mRecyclerView.setNestedScrollingEnabled(false);
            mAppbar.setExpanded(true);
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            Set<Text> texts = mAdapter.getSelectedItems();
            switch (item.getItemId()) {
                case R.id.menu_remove:
                    TextManager.getInstance(getBaseContext()).delete(texts);
                    mAdapter.clearSelection();
                    mode.finish();
                    return true;
                case R.id.menu_copy:
                    String copy = "";
                    for (Text text : texts) {
                        if (!copy.isEmpty()) {
                            copy += "\n";
                        }
                        copy += text.getBody();
                    }
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("simple text", copy);
                    clipboard.setPrimaryClip(clip);
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mode.finish();
            mAdapter.clearSelection();
            mActionMode = null;
            mRecyclerView.setNestedScrollingEnabled(true);
        }
    }

    @Override
    public void onBackPressed() {
        if (mAttachView.getVisibility() == View.VISIBLE) {
            onAttachmentHidden();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mManager.markAsRead(mThread);
    }

    public void log(String message){
        if (DEBUG) {
            Log.d(TAG, message);
        }
    }

    @Override
    public LegacyCameraView.Host getCameraHost() {
        return new LegacyCameraView.Host(this);
    }
}
