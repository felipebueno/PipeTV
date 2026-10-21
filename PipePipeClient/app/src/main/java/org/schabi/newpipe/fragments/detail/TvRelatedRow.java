package org.schabi.newpipe.fragments.detail;

import android.content.Context;
import android.graphics.Outline;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.PicassoHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * PipeTV: the related videos row of the fullscreen player on TVs, as in the TV apps of the big
 * video sites. Down opens it over the video, Left and Right move along it, OK plays the focused
 * video, and Up or Back close it. It also closes by itself when left alone for a while.
 */
final class TvRelatedRow {

    interface Listener {
        /** A video of the row was chosen. The row is already hidden. */
        void onRelatedVideoChosen(@NonNull StreamInfoItem item);

        /**
         * The row was closed by the user or for being idle.
         *
         * @param toControls whether the user went up, towards the player controls
         */
        void onRelatedRowClosed(boolean toControls);
    }

    private static final long CLOSE_WHEN_IDLE_MS = 15_000;

    private final View root;
    private final RecyclerView list;
    private final Listener listener;
    private final List<StreamInfoItem> items = new ArrayList<>();
    private final RecyclerView.Adapter<CardHolder> adapter = new CardAdapter();
    private final Runnable closeWhenIdle = () -> close(false);

    TvRelatedRow(@NonNull final ViewGroup parent, @NonNull final Listener listener) {
        this.listener = listener;
        root = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.pipetv_related_row, parent, false);
        list = root.findViewById(R.id.pipetvRelatedList);
        list.setLayoutManager(new LinearLayoutManager(parent.getContext(),
                LinearLayoutManager.HORIZONTAL, false));
        list.setAdapter(adapter);
        parent.addView(root);
    }

    boolean isShown() {
        return root.getVisibility() == View.VISIBLE;
    }

    /**
     * Shows the videos among the given related items and focuses the first one.
     *
     * @return false, with nothing shown, when there is no video among the items
     */
    boolean show(@NonNull final List<InfoItem> relatedItems) {
        final List<StreamInfoItem> streams = new ArrayList<>();
        for (final InfoItem item : relatedItems) {
            if (item instanceof StreamInfoItem) {
                streams.add((StreamInfoItem) item);
            }
        }
        if (streams.isEmpty()) {
            return false;
        }
        items.clear();
        items.addAll(streams);
        adapter.notifyDataSetChanged();
        list.scrollToPosition(0);
        root.setVisibility(View.VISIBLE);
        // Runs after the layout the visibility change triggers, once the cards exist
        list.post(() -> {
            final RecyclerView.ViewHolder first = list.findViewHolderForAdapterPosition(0);
            if (first != null) {
                first.itemView.requestFocus();
            }
        });
        scheduleClose();
        return true;
    }

    /** Hides the row without telling the listener, e.g. when the player leaves fullscreen. */
    void hide() {
        root.removeCallbacks(closeWhenIdle);
        root.setVisibility(View.GONE);
    }

    /**
     * Handles the keys the focused card didn't, while the row is shown. Left and Right are left
     * to the focus search, which moves along the row.
     */
    boolean onKeyDown(final int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                close(false);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                close(true);
                return true;
            default:
                scheduleClose();
                return false;
        }
    }

    private void close(final boolean toControls) {
        hide();
        listener.onRelatedRowClosed(toControls);
    }

    private void scheduleClose() {
        root.removeCallbacks(closeWhenIdle);
        root.postDelayed(closeWhenIdle, CLOSE_WHEN_IDLE_MS);
    }

    private final class CardAdapter extends RecyclerView.Adapter<CardHolder> {
        @NonNull
        @Override
        public CardHolder onCreateViewHolder(@NonNull final ViewGroup parent, final int viewType) {
            return new CardHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.pipetv_related_card, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull final CardHolder holder, final int position) {
            holder.bind(items.get(position));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private final class CardHolder extends RecyclerView.ViewHolder {
        private final ImageView thumbnail;
        private final TextView duration;
        private final TextView title;
        private final TextView channel;

        CardHolder(@NonNull final View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.pipetvRelatedThumbnail);
            duration = itemView.findViewById(R.id.pipetvRelatedDuration);
            title = itemView.findViewById(R.id.pipetvRelatedTitle);
            channel = itemView.findViewById(R.id.pipetvRelatedChannel);

            final float radius = 6 * itemView.getResources().getDisplayMetrics().density;
            thumbnail.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(final View view, final Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                }
            });
            thumbnail.setClipToOutline(true);

            itemView.setOnClickListener(v -> {
                final int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    hide();
                    listener.onRelatedVideoChosen(items.get(position));
                }
            });
        }

        void bind(@NonNull final StreamInfoItem item) {
            final Context context = itemView.getContext();
            title.setText(item.getName());
            channel.setText(item.getUploaderName());
            if (item.getStreamType() == StreamType.LIVE_STREAM
                    || item.getStreamType() == StreamType.AUDIO_LIVE_STREAM) {
                duration.setVisibility(View.VISIBLE);
                duration.setText(R.string.duration_live);
                duration.setBackgroundColor(ContextCompat.getColor(context,
                        R.color.live_duration_background_color));
            } else if (item.getDuration() > 0) {
                duration.setVisibility(View.VISIBLE);
                duration.setText(Localization.getDurationString(item.getDuration()));
                duration.setBackgroundColor(ContextCompat.getColor(context,
                        R.color.duration_background_color));
            } else {
                duration.setVisibility(View.GONE);
            }
            PicassoHelper.loadThumbnail(item.getThumbnailUrl()).into(thumbnail);
        }
    }
}
