package org.schabi.newpipe.fragments;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.rxjava3.RxRoom;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.feed.model.FeedGroupEntity;
import org.schabi.newpipe.database.history.model.StreamHistoryEntity;
import org.schabi.newpipe.database.stream.StreamStatisticsEntry;
import org.schabi.newpipe.database.stream.StreamWithState;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.local.feed.FeedDatabaseManager;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.KioskTranslator;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.ServiceHelper;
import org.schabi.newpipe.views.TvCardAdapter;
import org.schabi.newpipe.views.TvRowsScrollView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * PipeTV: the home of TVs, rows of videos as in the TV apps of the big video sites: the recently
 * watched ones, more like the last of them, the service's live videos and what's new from the
 * subscriptions. Each row loads on its own and stays hidden while it has nothing.
 */
public class TvHomeFragment extends Fragment {
    private static final String TAG = "TvHomeFragment";
    private static final int CARD_WIDTH_DP = 216;
    private static final int ROW_SIZE = 30;

    private final CompositeDisposable disposables = new CompositeDisposable();
    @Nullable
    private LinearLayout column;
    @Nullable
    private Row recent;
    @Nullable
    private Row moreLike;
    @Nullable
    private Disposable moreLikeLoad;
    /** The video the more like row is about. */
    @Nullable
    private String moreLikeSeedUrl;
    /** The video of the last card focused, the one the player returns the focus to. */
    @Nullable
    private String focusedUrl;
    @Nullable
    private Row focusedRow;

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        final Context context = requireContext();
        final TvRowsScrollView scroll = new TvRowsScrollView(context);
        scroll.setClipChildren(false);
        // Back on the last card focused when the home gets the focus as a whole: the player
        // opened by a link has no card to return to
        scroll.setFocusRestorer(() -> focusedRow != null && focusedRow.focus(focusedUrl));
        column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setClipChildren(false);
        column.setPadding(0, 0, 0,
                Math.round(48 * getResources().getDisplayMetrics().density));
        scroll.addView(column, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // The recently watched first: they load at once from the database and take the focus,
        // and the rows loaded from the network then arrive below it, not above
        recent = addRow();
        moreLike = addRow();
        final Row live = addRow();
        final Row news = addRow();
        watchRecent();
        loadLive(live);
        loadNews(news);
    }

    @Override
    public void onDestroyView() {
        disposables.clear();
        column = null;
        recent = null;
        moreLike = null;
        moreLikeLoad = null;
        moreLikeSeedUrl = null;
        focusedUrl = null;
        focusedRow = null;
        super.onDestroyView();
    }

    /**
     * The recently watched videos, and more like the last of them. They follow the history: a
     * video joins it as it starts, under the player, so the rows are up to date when it closes.
     */
    private void watchRecent() {
        final HistoryRecordManager history = new HistoryRecordManager(requireContext());
        // The history table changes once per video watched, while the statistics also read the
        // progress, saved on every seek: so the whole history is only read again then
        disposables.add(RxRoom.createFlowable(NewPipeDatabase.getInstance(requireContext()),
                        StreamHistoryEntity.STREAM_HISTORY_TABLE)
                .subscribeOn(Schedulers.io())
                .unsubscribeOn(Schedulers.io())
                .switchMapMaybe(changed -> history.getStreamStatistics().firstElement())
                .map(TvHomeFragment::recentItems)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::showRecent,
                        error -> Log.w(TAG, "Could not load the history", error)));
    }

    @NonNull
    private static List<StreamInfoItem> recentItems(
            @NonNull final List<StreamStatisticsEntry> entries) {
        final List<StreamStatisticsEntry> sorted = new ArrayList<>(entries);
        Collections.sort(sorted, Comparator.comparing(
                StreamStatisticsEntry::getLatestAccessDate).reversed());
        final List<StreamInfoItem> items = new ArrayList<>();
        for (final StreamStatisticsEntry entry : sorted) {
            if (items.size() == ROW_SIZE) {
                break;
            }
            items.add(entry.toStreamInfoItem());
        }
        return items;
    }

    private void showRecent(@NonNull final List<StreamInfoItem> items) {
        if (recent == null
                || !recent.show(getString(R.string.pipetv_recently_watched), items)) {
            return;
        }
        if (items.isEmpty()) {
            // A cleared history takes what came from it along
            loadMoreLike(null);
        } else if (!items.get(0).getUrl().equals(moreLikeSeedUrl)
                && moreLike.adapter.indexOf(focusedUrl) < 0) {
            // More like the last video watched, unless the player returns the focus to that
            // row: it stays as it was under the focus
            loadMoreLike(items.get(0));
        }
    }

    /** The videos YouTube relates to the given one, in the more like row; none hides it. */
    private void loadMoreLike(@Nullable final StreamInfoItem seed) {
        if (moreLikeLoad != null) {
            disposables.remove(moreLikeLoad);
            moreLikeLoad = null;
        }
        moreLikeSeedUrl = seed == null ? null : seed.getUrl();
        if (seed == null) {
            moreLike.show("", Collections.emptyList());
            return;
        }
        moreLikeLoad = ExtractorHelper.getStreamInfo(seed.getServiceId(), seed.getUrl(), false)
                .subscribeOn(Schedulers.io())
                .map(info -> streams(info.getRelatedItems()))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(items -> moreLike.show(
                        getString(R.string.pipetv_more_like, seed.getName()), items),
                        error -> Log.w(TAG, "Could not load related videos", error));
        disposables.add(moreLikeLoad);
    }

    /** The service's main kiosk, live videos on YouTube. */
    private void loadLive(@NonNull final Row row) {
        final Context context = requireContext();
        final int serviceId = ServiceHelper.getSelectedServiceId(context);
        final String kioskId;
        final String url;
        try {
            final StreamingService service = NewPipe.getService(serviceId);
            kioskId = service.getKioskList().getDefaultKioskId();
            url = service.getKioskList().getListLinkHandlerFactoryByType(kioskId)
                    .fromId(kioskId).getUrl();
        } catch (final Exception e) {
            Log.w(TAG, "No kiosk to show", e);
            return;
        }
        disposables.add(ExtractorHelper.getKioskInfo(serviceId, url, false)
                .subscribeOn(Schedulers.io())
                .map(info -> streams(info.getRelatedItems()))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(items -> row.show(
                        KioskTranslator.getTranslatedKioskName(kioskId, context), items),
                        error -> Log.w(TAG, "Could not load the kiosk", error)));
    }

    /** What's new from the subscriptions, once the feed has been fetched. */
    private void loadNews(@NonNull final Row row) {
        disposables.add(new FeedDatabaseManager(requireContext())
                .getStreams(FeedGroupEntity.GROUP_ALL_ID, false)
                .subscribeOn(Schedulers.io())
                .map(entries -> {
                    final List<StreamInfoItem> items = new ArrayList<>();
                    for (final StreamWithState entry : entries) {
                        if (items.size() == ROW_SIZE) {
                            break;
                        }
                        items.add(entry.getStream().toStreamInfoItem());
                    }
                    return items;
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(items -> row.show(getString(R.string.fragment_feed_title), items),
                        error -> Log.w(TAG, "Could not load the feed", error)));
    }

    @NonNull
    private static List<StreamInfoItem> streams(@NonNull final List<? extends InfoItem> items) {
        final List<StreamInfoItem> streams = new ArrayList<>();
        for (final InfoItem item : items) {
            if (item instanceof StreamInfoItem && streams.size() < ROW_SIZE) {
                streams.add((StreamInfoItem) item);
            }
        }
        return streams;
    }

    @NonNull
    private Row addRow() {
        final View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.pipetv_home_row, column, false);
        column.addView(view);
        return new Row(view);
    }

    private void play(@NonNull final StreamInfoItem item) {
        NavigationHelper.openVideoDetailFragment(requireContext(),
                requireActivity().getSupportFragmentManager(), item.getServiceId(),
                item.getUrl(), item.getName(), null, false);
    }

    private final class Row {
        private final View view;
        private final TextView title;
        private final RecyclerView list;
        private final TvCardAdapter adapter;

        Row(@NonNull final View view) {
            this.view = view;
            title = view.findViewById(R.id.pipetvRowTitle);
            list = view.findViewById(R.id.pipetvRowList);
            list.setLayoutManager(new LinearLayoutManager(view.getContext(),
                    LinearLayoutManager.HORIZONTAL, false));
            list.setHasFixedSize(true);
            // The cards' stable ids would otherwise animate every change of the row
            list.setItemAnimator(null);
            adapter = new TvCardAdapter(view.getContext(), CARD_WIDTH_DP,
                    new TvCardAdapter.Listener() {
                        @Override
                        public void onCardChosen(@NonNull final StreamInfoItem item) {
                            play(item);
                        }

                        @Override
                        public void onCardFocused(@NonNull final StreamInfoItem item) {
                            focusedUrl = item.getUrl();
                            focusedRow = Row.this;
                        }
                    });
            list.setAdapter(adapter);
        }

        /** Focuses the card of the given video, when the row shows it on screen. */
        boolean focus(@Nullable final String url) {
            final int position = adapter.indexOf(url);
            final RecyclerView.ViewHolder holder = position < 0
                    || view.getVisibility() != View.VISIBLE
                    ? null : list.findViewHolderForAdapterPosition(position);
            return holder != null && holder.itemView.requestFocus();
        }

        /**
         * Shows the row with the given videos, or hides it without any.
         *
         * @return whether the row changed
         */
        boolean show(@NonNull final String rowTitle, @NonNull final List<StreamInfoItem> items) {
            final boolean shown = view.getVisibility() == View.VISIBLE;
            if (items.isEmpty()) {
                view.setVisibility(View.GONE);
                adapter.setItems(items);
                return shown;
            }
            if (shown && adapter.hasItems(items)) {
                return false;
            }
            title.setText(rowTitle);
            adapter.setItems(items);
            view.setVisibility(View.VISIBLE);
            if (shown) {
                // The focus's video stays in view, and its card goes along with it, so the
                // player returns the focus there; otherwise the row starts over, where the
                // video just watched now is
                list.scrollToPosition(Math.max(0, adapter.indexOf(focusedUrl)));
                return true;
            }
            // The first row to arrive takes the focus, unless something already has it
            list.post(() -> {
                if (getActivity() != null && getActivity().getCurrentFocus() == null
                        && list.getChildCount() > 0) {
                    list.getChildAt(0).requestFocus();
                }
            });
            return true;
        }
    }
}
