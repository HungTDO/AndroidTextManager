package com.xlythe.sms;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ListView;

import com.xlythe.textmanager.text.Text;
import com.xlythe.textmanager.text.TextManager;


public class ManagerActivity extends Activity {
    private ThreadAdapter mThreadAdapter;
    private ImageButton mCompose;
    private ListView mListView;
    private TextManager mManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manager);

        mManager = TextManager.getInstance(getBaseContext());
        mCompose = (ImageButton) findViewById(R.id.compose);
        mListView = (ListView) findViewById(R.id.listView);

        // Start a new Message.
        mCompose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                Intent i = new Intent(getBaseContext(), ComposeActivity.class);
//                startActivity(i);
                Bitmap bmp1 = BitmapFactory.decodeResource(getBaseContext().getResources(), R.drawable.photo);
                Bitmap bmp2 = BitmapFactory.decodeResource(getBaseContext().getResources(), R.drawable.mic);
                Bitmap bmp3 = BitmapFactory.decodeResource(getBaseContext().getResources(), R.drawable.face);
                mManager.send(new Text.Builder()
                                .message("HIII!!!!")
                                .recipient("2163138473")
                                .attach(bmp1)
                                .attach(bmp2)
                                .attach(bmp3)
                                .build()
                );
            }
        });

        // Start Thread Activity.
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> av, View v, int position, long id) {
                Intent i = new Intent(getBaseContext(), ThreadActivity.class);
                i.putExtra(ThreadActivity.EXTRA_THREAD_ID, ((com.xlythe.textmanager.text.Thread) av.getItemAtPosition(position)).getThreadId());
                i.putExtra(ThreadActivity.EXTRA_ADDRESS, mManager.getSender((com.xlythe.textmanager.text.Thread) av.getItemAtPosition(position)).getDisplayName());
                i.putExtra(ThreadActivity.EXTRA_NUMBER, ((com.xlythe.textmanager.text.Thread) av.getItemAtPosition(position)).getAddress());
                startActivity(i);
            }
        });

        // Populate Adapter with list of threads.
        mThreadAdapter = new ThreadAdapter(getBaseContext(), R.layout.list_item_threads, mManager.getThreads());
        mListView.setAdapter(mThreadAdapter);
    }
}