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

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.feed.model.FeedGroupEntity;
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

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        final Context context = requireContext();
        final TvRowsScrollView scroll = new TvRowsScrollView(context);
        scroll.setClipChildren(false);
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
        final Row recent = addRow();
        final Row moreLike = addRow();
        final Row live = addRow();
        final Row news = addRow();
        loadRecent(recent, moreLike);
        loadLive(live);
        loadNews(news);
    }

    @Override
    public void onDestroyView() {
        disposables.clear();
        column = null;
        super.onDestroyView();
    }

    /** The recently watched videos, and more like the last of them. */
    private void loadRecent(@NonNull final Row recent, @NonNull final Row moreLike) {
        disposables.add(new HistoryRecordManager(requireContext()).getStreamStatistics()
                .firstElement()
                .map(entries -> {
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
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(items -> {
                    recent.show(getString(R.string.pipetv_recently_watched), items);
                    if (!items.isEmpty()) {
                        loadMoreLike(moreLike, items.get(0));
                    }
                }, error -> Log.w(TAG, "Could not load the history", error)));
    }

    /** The videos YouTube relates to the given one, for a row of more like it. */
    private void loadMoreLike(@NonNull final Row row, @NonNull final StreamInfoItem seed) {
        disposables.add(ExtractorHelper.getStreamInfo(seed.getServiceId(), seed.getUrl(), false)
                .subscribeOn(Schedulers.io())
                .map(info -> streams(info.getRelatedItems()))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(items -> row.show(getString(R.string.pipetv_more_like, seed.getName()),
                        items), error -> Log.w(TAG, "Could not load related videos", error)));
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
            adapter = new TvCardAdapter(view.getContext(), CARD_WIDTH_DP,
                    TvHomeFragment.this::play);
            list.setAdapter(adapter);
        }

        void show(@NonNull final String rowTitle, @NonNull final List<StreamInfoItem> items) {
            if (items.isEmpty()) {
                return;
            }
            title.setText(rowTitle);
            adapter.setItems(items);
            view.setVisibility(View.VISIBLE);
            // The first row to arrive takes the focus, unless something already has it
            list.post(() -> {
                if (getActivity() != null && getActivity().getCurrentFocus() == null
                        && list.getChildCount() > 0) {
                    list.getChildAt(0).requestFocus();
                }
            });
        }
    }
}
