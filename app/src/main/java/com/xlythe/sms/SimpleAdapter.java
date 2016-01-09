package com.xlythe.sms;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.net.Uri;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.xlythe.sms.util.ColorUtils;
import com.xlythe.sms.util.DateFormatter;
import com.xlythe.textmanager.text.Contact;
import com.xlythe.textmanager.text.Thread;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by Niko on 12/22/15.
 */
public class SimpleAdapter extends SelectableAdapter<RecyclerView.ViewHolder>{
    private final Context mContext;
    private Thread.ThreadCursor mCursor;
    private final int CARD_STATE_ACTIVE_COLOR = Color.rgb(229, 244, 243);
    private final int CARD_STATE_COLOR = Color.WHITE;
    private SimpleViewHolder.ClickListener mClickListener;

    public SimpleAdapter(Context context, Thread.ThreadCursor cursor) {
        mClickListener = (SimpleViewHolder.ClickListener) context;
        mContext = context;
        mCursor = cursor;
    }

    public void add(Thread s, int position) {
        position = position == -1 ? getItemCount()  : position;
        // TODO: FIX
        //mData.add(position, s);
        notifyItemInserted(position);
    }

    public void removeItem(int position) {
        // TODO: FIX
        //mData.remove(position);
        notifyItemRemoved(position);
    }

    public void removeItems(List<Integer> positions) {
        // TODO: FIX
        // Reverse-sort the list
        if (positions!=null) {
            Collections.sort(positions, new Comparator<Integer>() {
                @Override
                public int compare(Integer lhs, Integer rhs) {
                    return rhs - lhs;
                }
            });

            // Split the list in ranges
            while (!positions.isEmpty()) {
                if (positions.size() == 1) {
                    removeItem(positions.get(0));
                    positions.remove(0);
                } else {
                    int count = 1;
                    while (positions.size() > count && positions.get(count).equals(positions.get(count - 1) - 1)) {
                        ++count;
                    }

                    if (count == 1) {
                        removeItem(positions.get(0));
                    } else {
                        removeRange(positions.get(count - 1), count);
                    }

                    for (int i = 0; i < count; ++i) {
                        positions.remove(0);
                    }
                }
            }
        }
        clearSelection();
        invalidate();
    }

    void invalidate(){
        for (int i=0; i < mCursor.getCount(); i++) {
            notifyItemChanged(i);
        }
    }

    private void removeRange(int positionStart, int itemCount) {
        for (int i = 0; i < itemCount; ++i) {
            // TODO: FIX
            //mData.remove(positionStart);
        }
        notifyItemRangeRemoved(positionStart, itemCount);
    }

    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final View view = LayoutInflater.from(mContext).inflate(R.layout.list_item, parent, false);
        return new SimpleViewHolder(view, mClickListener);
    }

    @Override
    public void onBindViewHolder(final RecyclerView.ViewHolder holder, final int position) {
        mCursor.moveToPosition(position);
        Thread data = mCursor.getThread();
        String body = "";
        String time = "";
        String address = "";
        Uri uri = null;
        int unread = 0;
        int color = mContext.getColor(R.color.colorPrimary);

        if (data.getLatestMessage()!=null) {
            body = data.getLatestMessage().getBody();
            time = DateFormatter.getFormattedDate(data.getLatestMessage());
            address = data.getLatestMessage().getSender().getDisplayName()+"";
            uri = data.getLatestMessage().getSender().getPhotoUri();
            unread = data.getUnreadCount();
            color = ColorUtils.getColor(Long.parseLong(data.getId()));
        }
        SimpleViewHolder simpleHolder = (SimpleViewHolder) holder;
        simpleHolder.message.setText(body);
        simpleHolder.date.setText(time);
        simpleHolder.profile.setBackground(mContext.getDrawable(R.drawable.selector));

        if (unread > 0) {
            simpleHolder.title.setText(address);
            simpleHolder.unread.setVisibility(View.VISIBLE);
            simpleHolder.unread.setText(unread + " new");
            simpleHolder.unread.setTextColor(color);
            simpleHolder.unread.getBackground().setColorFilter(color, PorterDuff.Mode.SRC_IN);
            simpleHolder.unread.getBackground().setAlpha(25);
            simpleHolder.title.setTextColor(color);
            simpleHolder.title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            simpleHolder.message.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            simpleHolder.date.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        } else {
            simpleHolder.title.setText(address);
            simpleHolder.unread.setVisibility(View.GONE);
            simpleHolder.title.setTextColor(mContext.getColor(R.color.headerText));
            simpleHolder.title.setTypeface(Typeface.create("sans-serif-regular", Typeface.NORMAL));
            simpleHolder.message.setTypeface(Typeface.create("sans-serif-regular", Typeface.NORMAL));
            simpleHolder.date.setTypeface(Typeface.create("sans-serif-regular", Typeface.NORMAL));
        }

        boolean isSelected = isSelected(position);

        if (selectMode()) {
            simpleHolder.profile.setImageResource(android.R.color.transparent);
        } else {
            if (!address.equals("")) {
                ProfileDrawable border = new ProfileDrawable(mContext,
                        address.charAt(0),
                        color,
                        uri);
                simpleHolder.profile.setImageDrawable(border);
            }
        }

        simpleHolder.profile.setActivated(isSelected);
        if (isSelected) {
            simpleHolder.card.setCardBackgroundColor(CARD_STATE_ACTIVE_COLOR);
        } else {
            simpleHolder.card.setCardBackgroundColor(CARD_STATE_COLOR);
        }
    }

    @Override
    public int getItemCount() {
        return mCursor.getCount();
    }

    public static class SimpleViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener,
            View.OnLongClickListener {
        public final TextView title;
        public final TextView unread;
        public final TextView message;
        public final TextView date;
        public final CardView card;
        public final de.hdodenhof.circleimageview.CircleImageView profile;
        private ClickListener mListener;

        public SimpleViewHolder(View view, ClickListener listener) {
            super(view);
            title = (TextView) view.findViewById(R.id.sender);
            unread = (TextView) view.findViewById(R.id.unread);
            message = (TextView) view.findViewById(R.id.message);
            date = (TextView) view.findViewById(R.id.date);
            card = (CardView) view.findViewById(R.id.card);
            profile = (de.hdodenhof.circleimageview.CircleImageView) view.findViewById(R.id.profile_image);

            mListener = listener;

            profile.setOnClickListener(this);
            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this);
        }
        @Override
        public void onClick(View v) {
            if (mListener != null) {
                if (v instanceof de.hdodenhof.circleimageview.CircleImageView) {
                    mListener.onProfileClicked(getAdapterPosition());
                } else {
                    mListener.onItemClicked(getAdapterPosition());
                }
            }
        }

        @Override
        public boolean onLongClick(View v) {
            if (mListener != null) {
                return mListener.onItemLongClicked(getAdapterPosition());
            }

            return false;
        }

        public interface ClickListener {
            void onProfileClicked(int position);
            void onItemClicked(int position);
            boolean onItemLongClicked(int position);
        }
    }
}
