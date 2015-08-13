package com.xlythe.sms;

import android.app.ActionBar;
import android.app.Activity;
import android.database.ContentObserver;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;

import com.xlythe.textmanager.MessageObserver;
import com.xlythe.textmanager.text.Text;
import com.xlythe.textmanager.text.TextManager;

import java.util.List;


public class ThreadActivity extends FragmentActivity {
    public static String EXTRA_THREAD_ID = "threadId";
    public static String EXTRA_ADDRESS = "address";
    public static String EXTRA_NUMBER = "number";
    private static final int NUM_PAGES = 5;

    ImageButton mCamera;
    ImageButton mPhoto;
    ImageButton mFace;
    ImageButton mMic;
    ImageButton mLocation;

    private ActionBar mActionBar;
    private AttachView mAttachView;
    private FrameLayout mMessages;
    private ImageButton mButton;
    private ViewPager mPager;
    private PagerAdapter mPagerAdapter;
    private LinearLayout mTabBar;

    private TextAdapter mTextAdapter;
    private ListView mListView;
    private ImageButton mSend;
    private EditText mMessage;
    private TextManager mManager;
    private String mAddress;
    private String mNumber;
    private long mThreadId;

    private List<Text> mTexts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().requestFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
        setContentView(R.layout.activity_thread);

        mCamera = (ImageButton) findViewById(R.id.camera);
        mPhoto = (ImageButton) findViewById(R.id.photo);
        mFace = (ImageButton) findViewById(R.id.face);
        mMic = (ImageButton) findViewById(R.id.mic);
        mLocation = (ImageButton) findViewById(R.id.location);

        mManager = TextManager.getInstance(getBaseContext());
        mListView = (ListView) findViewById(R.id.messages);
        mSend = (ImageButton) findViewById(R.id.send);
        mMessage = (EditText) findViewById(R.id.message);
        mTabBar = (LinearLayout) findViewById(R.id.tab_bar);

        mTabBar.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                mTabBar.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                mTabBar.setTranslationY(mTabBar.getHeight());
            }
        });

        // Get threadId that was clicked.
         mThreadId = getIntent().getLongExtra(EXTRA_THREAD_ID, -1);

        // Get address.
        mAddress = getIntent().getStringExtra(EXTRA_ADDRESS);
        mNumber = getIntent().getStringExtra(EXTRA_NUMBER);

        // Color bars to match thread color.
        Window window = getWindow();
        window.setStatusBarColor(ColorUtils.getDarkColor(mThreadId));
        getActionBar().setBackgroundDrawable(new ColorDrawable(ColorUtils.getColor(mThreadId)));
        getActionBar().setTitle(mAddress);

        // Set tab bar color
        mTabBar.setBackground(new ColorDrawable(ColorUtils.getColor(mThreadId)));

        mTexts = mManager.getMessages(mThreadId);

        // Populate Adapter with list of texts.
        mTextAdapter = new TextAdapter(getBaseContext(), R.layout.list_item_texts, mTexts);
        mListView.setAdapter(mTextAdapter);

        //register observer
        mManager.registerObserver(new MessageObserver() {
            @Override
            public void notifyDataChanged() {
                Log.d("activity","change");
                mTexts.clear();
                mTexts.addAll(mManager.getMessages(mThreadId));
                mTextAdapter.notifyDataSetChanged();
            }
        });

        // Delete a message on long press.
        mListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> av, View v, int position, long id) {
                Text text = (Text) v.getTag();
                //mManager.delete(text);
                return true;
            }
        });

        // Send message.
        mSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TextManager manager = TextManager.getInstance(getBaseContext());
                manager.send(new Text.Builder()
                                .message(mMessage.getText().toString())
                                .recipient(mNumber)
                                .build()
                );
                mMessage.setText("");
            }
        });

        mPager = (ViewPager) findViewById(R.id.pager);
        mPagerAdapter = new ScreenSlidePagerAdapter(getSupportFragmentManager());
        mPager.setAdapter(mPagerAdapter);
        mPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int i, float v, int i1) {
            }

            @Override
            public void onPageSelected(int i) {
                switch (i) {
                    case 0:
                        mCamera.setAlpha(1f);
                        mPhoto.setAlpha(0.5f);
                        mFace.setAlpha(0.5f);
                        mMic.setAlpha(0.5f);
                        mLocation.setAlpha(0.5f);
                        break;
                    case 1:
                        mCamera.setAlpha(0.5f);
                        mPhoto.setAlpha(1f);
                        mFace.setAlpha(0.5f);
                        mMic.setAlpha(0.5f);
                        mLocation.setAlpha(0.5f);
                        break;
                    case 2:
                        mCamera.setAlpha(0.5f);
                        mPhoto.setAlpha(0.5f);
                        mFace.setAlpha(1f);
                        mMic.setAlpha(0.5f);
                        mLocation.setAlpha(0.5f);
                        break;
                    case 3:
                        mCamera.setAlpha(0.5f);
                        mPhoto.setAlpha(0.5f);
                        mFace.setAlpha(0.5f);
                        mMic.setAlpha(1f);
                        mLocation.setAlpha(0.5f);
                        break;
                    case 4:
                        mCamera.setAlpha(0.5f);
                        mPhoto.setAlpha(0.5f);
                        mFace.setAlpha(0.5f);
                        mMic.setAlpha(0.5f);
                        mLocation.setAlpha(1f);
                        break;
                }
            }

            @Override
            public void onPageScrollStateChanged(int i) {

            }
        });


        mAttachView = (AttachView) findViewById(R.id.attach_view);
        mMessages = (FrameLayout) findViewById(R.id.messages_xxx);
        mAttachView.setUpperView(mMessages);
        mActionBar = getActionBar();
        mAttachView.setActionBar(mActionBar);
        mAttachView.setTabBar(mTabBar);

        mButton = (ImageButton) findViewById(R.id.attach);
        mButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mAttachView.partial();
            }
        });
    }

    public void setActiveScrollView(ScrollView sv) {
        mAttachView.setScrollView(sv);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_conversation, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void jumpToCamera(View v){
        mPager.setCurrentItem(0);
    }
    public void jumpToPhoto(View v){
        mPager.setCurrentItem(1);
    }
    public void jumpToFace(View v){
        mPager.setCurrentItem(2);
    }
    public void jumpToMic(View v){
        mPager.setCurrentItem(3);
    }
    public void jumpToLocation(View v){
        mPager.setCurrentItem(4);
    }

    /**
     * A simple pager adapter that represents 5 ScreenSlidePageFragment objects, in
     * sequence.
     */
    private class ScreenSlidePagerAdapter extends FragmentStatePagerAdapter {
        public ScreenSlidePagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            final Fragment result;
            switch (position) {
                case 0:
                    //result = new ScreenSlidePageFragment();
                    result = new CameraFragment();
                    break;
                case 1:
                    result = new ScreenSlidePageFragment();
                    break;
                case 2:
                    result = new FaceFragment();
                    break;
                case 3:
                    result = new MicFragment();
                    break;
                case 4:
                    result = new LocationFragment();
                    break;
                default:
                    result = null;
                    break;
            }

            return result;
        }

        @Override
        public int getCount() {
            return NUM_PAGES;
        }
    }
}
