package org.schabi.newpipe.fragments.detail;

import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.views.TvCardAdapter;

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
    private static final int CARD_WIDTH_DP = 176;

    private final View root;
    private final RecyclerView list;
    private final Listener listener;
    private final TvCardAdapter adapter;
    private final Runnable closeWhenIdle = () -> close(false);

    TvRelatedRow(@NonNull final ViewGroup parent, @NonNull final Listener listener) {
        this.listener = listener;
        root = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.pipetv_related_row, parent, false);
        list = root.findViewById(R.id.pipetvRelatedList);
        list.setLayoutManager(new LinearLayoutManager(parent.getContext(),
                LinearLayoutManager.HORIZONTAL, false));
        // The cards' stable ids would otherwise animate every change of the row
        list.setItemAnimator(null);
        adapter = new TvCardAdapter(parent.getContext(), CARD_WIDTH_DP, item -> {
            hide();
            listener.onRelatedVideoChosen(item);
        });
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
        adapter.setItems(streams);
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
}
