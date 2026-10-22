package org.schabi.newpipe.fragments.detail;

import android.graphics.Outline;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.util.PicassoHelper;

/**
 * PipeTV: what plays next when a video ends on a TV with autoplay on, as in the TV apps of the
 * big video sites. A card in the corner counts down and then plays it; OK on "Play now" plays it
 * at once, and "Cancel" or Back stay on the ended video.
 */
final class TvUpNext {

    interface Listener {
        /** The countdown ended or the user chose to play now. The card is already hidden. */
        void onUpNextPlay(@NonNull StreamInfoItem item);

        /** The user cancelled. The card is already hidden. */
        void onUpNextCancelled();
    }

    private static final int COUNTDOWN_SECONDS = 10;

    private final View root;
    private final ImageView thumbnail;
    private final TextView title;
    private final TextView channel;
    private final TextView countdown;
    private final View play;
    private final View cancelButton;
    private final Listener listener;
    private final Runnable tick = this::tick;
    @Nullable
    private StreamInfoItem item;
    private int secondsLeft;

    TvUpNext(@NonNull final ViewGroup parent, @NonNull final Listener listener) {
        this.listener = listener;
        root = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.pipetv_up_next, parent, false);
        thumbnail = root.findViewById(R.id.pipetvUpNextThumbnail);
        title = root.findViewById(R.id.pipetvUpNextTitle);
        channel = root.findViewById(R.id.pipetvUpNextChannel);
        countdown = root.findViewById(R.id.pipetvUpNextCountdown);
        play = root.findViewById(R.id.pipetvUpNextPlay);
        play.setOnClickListener(v -> play());
        cancelButton = root.findViewById(R.id.pipetvUpNextCancel);
        cancelButton.setOnClickListener(v -> cancel());

        final float radius = 6 * parent.getResources().getDisplayMetrics().density;
        thumbnail.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(final View view, final Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        thumbnail.setClipToOutline(true);
        parent.addView(root);
    }

    boolean isShown() {
        return root.getVisibility() == View.VISIBLE;
    }

    /** Shows the video that plays next, focuses "Play now" and starts the countdown. */
    void show(@NonNull final StreamInfoItem next) {
        item = next;
        title.setText(next.getName());
        channel.setText(next.getUploaderName());
        PicassoHelper.loadThumbnail(next.getThumbnailUrl()).into(thumbnail);
        secondsLeft = COUNTDOWN_SECONDS;
        showCountdown();
        root.setVisibility(View.VISIBLE);
        play.requestFocus();
        root.removeCallbacks(tick);
        root.postDelayed(tick, 1000);
    }

    /** Hides the card and stops the countdown, without telling the listener. */
    void hide() {
        root.removeCallbacks(tick);
        root.setVisibility(View.GONE);
    }

    /**
     * Handles the keys while the card is shown: Back cancels, Left and Right move between its
     * two buttons, and Up and Down stay on the card, where the hidden player controls would
     * otherwise take the focus.
     */
    boolean onKeyDown(final int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                cancel();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                play.requestFocus();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                cancelButton.requestFocus();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return true;
            default:
                return false;
        }
    }

    private void tick() {
        secondsLeft--;
        if (secondsLeft <= 0) {
            play();
            return;
        }
        showCountdown();
        root.postDelayed(tick, 1000);
    }

    private void showCountdown() {
        countdown.setText(root.getContext().getString(R.string.pipetv_up_next_in, secondsLeft));
    }

    private void play() {
        final StreamInfoItem next = item;
        hide();
        if (next != null) {
            listener.onUpNextPlay(next);
        }
    }

    private void cancel() {
        hide();
        listener.onUpNextCancelled();
    }
}
