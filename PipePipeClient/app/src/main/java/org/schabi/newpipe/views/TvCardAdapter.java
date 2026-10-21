package org.schabi.newpipe.views;

import android.content.Context;
import android.graphics.Outline;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.PicassoHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PipeTV: the video cards of the TV rows, the home's and the player's related videos: a
 * thumbnail with the duration or a live badge, the title and the channel.
 */
public final class TvCardAdapter extends RecyclerView.Adapter<TvCardAdapter.CardHolder> {

    public interface Listener {
        void onCardChosen(@NonNull StreamInfoItem item);

        default void onCardFocused(@NonNull final StreamInfoItem item) {
        }
    }

    private final List<StreamInfoItem> items = new ArrayList<>();
    /** Each video's id, so its card follows it when a row changes, and the focus with it. */
    private final Map<String, Long> ids = new HashMap<>();
    private final Listener listener;
    private final int cardWidth;

    /** @param cardWidthDp the cards' width; their thumbnails keep a 16:9 shape */
    public TvCardAdapter(@NonNull final Context context, final int cardWidthDp,
                         @NonNull final Listener listener) {
        this.listener = listener;
        cardWidth = Math.round(cardWidthDp * context.getResources().getDisplayMetrics().density);
        setHasStableIds(true);
    }

    /** Shows the given videos, each once: two cards can't have the same id. */
    public void setItems(@NonNull final List<StreamInfoItem> newItems) {
        items.clear();
        items.addAll(unique(newItems));
        notifyDataSetChanged();
    }

    /** The position of the video with the given URL, or -1 when the cards don't show it. */
    public int indexOf(@Nullable final String url) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getUrl().equals(url)) {
                return i;
            }
        }
        return -1;
    }

    /** Whether the cards already show these videos, in this order. */
    public boolean hasItems(@NonNull final List<StreamInfoItem> others) {
        final List<StreamInfoItem> unique = unique(others);
        if (unique.size() != items.size()) {
            return false;
        }
        for (int i = 0; i < items.size(); i++) {
            if (!items.get(i).getUrl().equals(unique.get(i).getUrl())) {
                return false;
            }
        }
        return true;
    }

    @NonNull
    private static List<StreamInfoItem> unique(@NonNull final List<StreamInfoItem> list) {
        final Set<String> urls = new HashSet<>();
        final List<StreamInfoItem> unique = new ArrayList<>();
        for (final StreamInfoItem item : list) {
            if (urls.add(item.getUrl())) {
                unique.add(item);
            }
        }
        return unique;
    }

    @Override
    public long getItemId(final int position) {
        final String url = items.get(position).getUrl();
        Long id = ids.get(url);
        if (id == null) {
            id = (long) ids.size();
            ids.put(url, id);
        }
        return id;
    }

    @NonNull
    @Override
    public CardHolder onCreateViewHolder(@NonNull final ViewGroup parent, final int viewType) {
        final View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.pipetv_related_card, parent, false);
        view.getLayoutParams().width = cardWidth;
        ((View) view.findViewById(R.id.pipetvRelatedThumbnail).getParent())
                .getLayoutParams().height = cardWidth * 9 / 16;
        return new CardHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final CardHolder holder, final int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    final class CardHolder extends RecyclerView.ViewHolder {
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
                    listener.onCardChosen(items.get(position));
                }
            });
            itemView.setOnFocusChangeListener((v, hasFocus) -> {
                final int position = getBindingAdapterPosition();
                if (hasFocus && position != RecyclerView.NO_POSITION) {
                    listener.onCardFocused(items.get(position));
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
