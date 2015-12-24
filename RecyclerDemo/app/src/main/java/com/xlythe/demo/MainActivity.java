package com.xlythe.demo;

import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.TypedValue;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity  implements SimpleAdapter.SimpleViewHolder.ClickListener  {

    RecyclerView mRecyclerView;
    SimpleAdapter mAdapter;
    private float px;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, getResources().getDisplayMetrics());

        //String[] array = {"Josh Cheston","Alex Goldstein",
        //        "Natalie","Tim Nerozzi","Alex Bourdakos","Cyrus Basseri","Mark Steffl"};

        ArrayList<Thread> list = new ArrayList<>();
        list.add(new Thread("Will Harmon", "How\'s it going, did you get the ...", "10 min", null, 6, getColor(R.color.icon)));
        Thread oriana = new Thread("Oriana", "picture", "2:17pm", null, 0, getColor(R.color.icon));
        oriana.mDrawable = BitmapFactory.decodeResource(getResources(), R.drawable.oriana);
        list.add(oriana);
        list.add(new Thread("Mom", "Random message to show this off ...", "6:22pm", null, 0, getColor(R.color.purple)));
        list.add(new Thread("(216) 283-3928", "Hopefully Will likes this new design ...", "1:05pm", null, 1, getColor(R.color.pink)));
        list.add(new Thread("Josh Cheston", "Make nick stop", "10 min", null, 0, getColor(R.color.icon)));
        list.add(new Thread("Alex Goldstein", "hi", "10 min", null, 0, getColor(R.color.icon)));
        list.add(new Thread("Natalie", "The language!", "10 min", null, 0, getColor(R.color.icon)));
        list.add(new Thread("Tim Nerozzi", "My only big gripe is that Chewbacca ...", "10 min", null, 0, getColor(R.color.icon)));
        list.add(new Thread("Alex Bourdakos", "I agree", "10 min", null, 0, getColor(R.color.icon)));
        list.add(new Thread("Cyrus Basseri", "Unless you just want it to be on your ...", "10 min", null, 0, getColor(R.color.icon)));
        list.add(new Thread("Mark Steffl", "Noice", "10 min", null, 0, getColor(R.color.icon)));

        //Your RecyclerView
        mRecyclerView = (RecyclerView) findViewById(R.id.list);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerView.addItemDecoration(new DividerItemDecorationRes(this, R.drawable.divider));
        //mRecyclerView.addItemDecoration(new DividerItemDecoration(this, LinearLayoutManager.VERTICAL, 14));

//        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
//            @Override
//            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
//                super.onScrolled(recyclerView, dx, dy);
//                //drawVertical(mRecyclerView);
//            }
//        });

        //Your RecyclerView.Adapter
        mAdapter = new SimpleAdapter(this, list, mRecyclerView);

        //This is the code to provide a sectioned list
        List<ManagerAdapter.Section> sections = new ArrayList<>();

        //Sections
        sections.add(new ManagerAdapter.Section(0,"Today"));
        sections.add(new ManagerAdapter.Section(2,"Yesterday"));
        sections.add(new ManagerAdapter.Section(7,"November"));
        sections.add(new ManagerAdapter.Section(list.size(), ""));

        //Add your adapter to the sectionAdapter
        ManagerAdapter.Section[] dummy = new ManagerAdapter.Section[sections.size()];
        ManagerAdapter mSectionedAdapter = new ManagerAdapter(this,R.layout.section,R.id.section_text, mAdapter);
        mSectionedAdapter.setSections(sections.toArray(dummy));

        //Apply this adapter to the RecyclerView
        mRecyclerView.setAdapter(mSectionedAdapter);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Compose not yet added", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
    }

    @Override
    public void onItemClicked(int position) {

    }

    @Override
    public boolean onItemLongClicked(int position) {
        return false;
    }

    private void toggleSelection(int position) {
        mAdapter.toggleSelection(position);
        int count = mAdapter.getSelectedItemCount();

        if (count == 0) {
            actionMode.finish();
        } else {
            actionMode.setTitle(String.valueOf(count));
            actionMode.invalidate();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_search) {
            Snackbar.make(findViewById(R.id.list), "Search not yet added", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

//    public void drawVertical(RecyclerView parent) {
//
//        final int childCount = parent.getChildCount();
//
//        for (int i = 0; i < childCount; i++) {
//            final View child = parent.getChildAt(i);
//            if (child instanceof TextView) {
//                final View childTop = parent.getChildAt(i-1);
//                final View childBottom = parent.getChildAt(i+1);
//                if (childTop instanceof LinearLayout) {
//                    final View card = ((LinearLayout) childTop).getChildAt(0);
//                    final LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) card.getLayoutParams();
//                    params.setMargins(0, 0, 0, (int)px);
//                    card.setLayoutParams(params);
//                }
//                if (childBottom instanceof LinearLayout) {
//                    final View card = ((LinearLayout) childBottom).getChildAt(0);
//                    final LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) card.getLayoutParams();
//                    params.setMargins(0, (int)px, 0, 0);
//                    card.setLayoutParams(params);
//                }
//            }
//            else {
//                final View childTop = parent.getChildAt(i-1);
//                final View childBottom = parent.getChildAt(i+1);
//                if (childTop instanceof LinearLayout) {
//                    final View card = ((LinearLayout) child).getChildAt(0);
//                    final LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) card.getLayoutParams();
//                    int bottom = params.bottomMargin;
//                    params.setMargins(0, 0, 0, bottom);
//                    card.setLayoutParams(params);
//                }
//                if (childBottom instanceof LinearLayout) {
//                    final View card = ((LinearLayout) child).getChildAt(0);
//                    final LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) card.getLayoutParams();
//                    int top = params.topMargin;
//                    params.setMargins(0, top, 0, 0);
//                    card.setLayoutParams(params);
//                }
//            }
//        }
//    }
}
