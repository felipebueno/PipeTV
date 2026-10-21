package org.schabi.newpipe.views;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.FocusFinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.schabi.newpipe.R;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * PipeTV: the side rail that takes the place of the toolbar, the drawer and the main tabs on TVs,
 * as in the TV apps of the big video sites. Closed, it is a column of icons on the left of the
 * content. Left from the content's edge opens it on the current page's item, with the labels
 * over the dimmed content; Up and Down move along it, OK goes to an item and Right goes back to
 * where the content had the focus.
 * <p>
 * It only animates the scale and alpha of a few views, so opening it costs no layout pass.
 */
public final class TvNavRail extends FrameLayout {

    public interface Listener {
        void onRailItemChosen(@NonNull String key);
    }

    public static final class Item {
        final String key;
        @DrawableRes
        final int icon;
        final String label;

        public Item(@NonNull final String key, @DrawableRes final int icon,
                    @NonNull final String label) {
            this.key = key;
            this.icon = icon;
            this.label = label;
        }

        @Override
        public boolean equals(final Object o) {
            if (!(o instanceof Item)) {
                return false;
            }
            final Item other = (Item) o;
            return key.equals(other.key) && icon == other.icon && label.equals(other.label);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, icon, label);
        }
    }

    private static final long ANIMATION_MS = 160;
    private static final int COLLAPSED_WIDTH_DP = 72;
    // The open rail fits its longest label, within these
    private static final int MIN_EXPANDED_WIDTH_DP = 224;
    private static final int MAX_EXPANDED_WIDTH_DP = 320;
    // Everything in an open row but its label: the margins, the icon and the label's margins
    private static final int ROW_CHROME_WIDTH_DP = 12 + 56 + 12 + 12;
    private static final int FOCUSED_COLOR = 0xFF0F0F0F;
    private static final int SELECTED_COLOR = 0xFFFFFFFF;
    private static final int OPEN_COLOR = 0xCCFFFFFF;
    private static final int CLOSED_COLOR = 0x99FFFFFF;

    private final float density;
    private final int collapsedWidth;
    private int expandedWidth;
    private final View scrim;
    private final View panel;
    private final LinearLayout column;
    private final LinearLayout topItems;
    private final LinearLayout bottomItems;
    private final List<Item> items = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private final Drawable focusedBackground;
    private final Drawable selectedBackground;
    private final Drawable selectedIconBackground;
    private final ViewTreeObserver.OnGlobalFocusChangeListener focusListener =
            this::onGlobalFocusChanged;

    @Nullable
    private Listener listener;
    @Nullable
    private String selectedKey;
    @Nullable
    private WeakReference<View> contentFocus;
    private boolean expanded;

    public TvNavRail(@NonNull final Context context) {
        super(context);
        setId(R.id.pipetv_rail);
        setClipChildren(false);
        density = getResources().getDisplayMetrics().density;
        collapsedWidth = Math.round(COLLAPSED_WIDTH_DP * density);
        expandedWidth = Math.round(MIN_EXPANDED_WIDTH_DP * density);
        focusedBackground = ContextCompat.getDrawable(context, R.drawable.pipetv_rail_focused);
        selectedBackground = ContextCompat.getDrawable(context, R.drawable.pipetv_rail_selected);
        selectedIconBackground = ContextCompat.getDrawable(context,
                R.drawable.pipetv_rail_selected);

        scrim = new View(context);
        scrim.setBackgroundColor(0x99000000);
        scrim.setAlpha(0f);
        scrim.setVisibility(GONE);
        addView(scrim, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        panel = new View(context);
        panel.setBackgroundResource(R.drawable.pipetv_rail_panel);
        panel.setPivotX(0f);
        panel.setAlpha(0f);
        panel.setScaleX(collapsedWidth / (float) expandedWidth);
        addView(panel, new LayoutParams(expandedWidth, LayoutParams.MATCH_PARENT));

        column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        final int verticalPadding = Math.round(32 * density);
        column.setPadding(0, verticalPadding, 0, verticalPadding);
        topItems = new LinearLayout(context);
        topItems.setOrientation(LinearLayout.VERTICAL);
        bottomItems = new LinearLayout(context);
        bottomItems.setOrientation(LinearLayout.VERTICAL);
        column.addView(topItems, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        column.addView(bottomItems, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        addView(column, new LayoutParams(expandedWidth, LayoutParams.MATCH_PARENT));
    }

    /** The width the closed rail takes on the left of the content. */
    public int getCollapsedWidth() {
        return collapsedWidth;
    }

    public void setListener(@Nullable final Listener listener) {
        this.listener = listener;
    }

    /** Shows these items, the top ones from the top down and the bottom ones at the bottom. */
    public void setItems(@NonNull final List<Item> top, @NonNull final List<Item> bottom) {
        final List<Item> all = new ArrayList<>(top);
        all.addAll(bottom);
        if (all.equals(items)) {
            return; // e.g. back from the settings, keeping the focus where it is
        }
        items.clear();
        items.addAll(all);
        rows.clear();
        topItems.removeAllViews();
        bottomItems.removeAllViews();
        for (final Item item : top) {
            addRow(topItems, item);
        }
        for (final Item item : bottom) {
            addRow(bottomItems, item);
        }
        fitExpandedWidth();
        updateRows();
    }

    /** Makes the open rail as wide as its longest label needs, within the limits. */
    private void fitExpandedWidth() {
        float longestLabel = 0;
        for (final Row row : rows) {
            longestLabel = Math.max(longestLabel,
                    row.label.getPaint().measureText(row.label.getText().toString()));
        }
        final int width = Math.round(Math.min(MAX_EXPANDED_WIDTH_DP * density,
                Math.max(MIN_EXPANDED_WIDTH_DP * density,
                        ROW_CHROME_WIDTH_DP * density + longestLabel)));
        if (width == expandedWidth) {
            return;
        }
        expandedWidth = width;
        panel.getLayoutParams().width = width;
        column.getLayoutParams().width = width;
        if (!expanded) {
            panel.setScaleX(collapsedWidth / (float) width);
        }
        requestLayout();
    }

    /** Marks the item of the page on screen, or none for a page that has no item. */
    public void setSelectedKey(@Nullable final String key) {
        selectedKey = key;
        updateRows();
    }

    /** Opens the rail with the focus on the current page's item, as Back from the home does. */
    public void focusSelected() {
        final Row row = selectedOrFirstRow();
        if (row != null) {
            row.view.requestFocus();
        }
    }

    private void addRow(@NonNull final LinearLayout parent, @NonNull final Item item) {
        final View view = LayoutInflater.from(getContext())
                .inflate(R.layout.pipetv_rail_item, parent, false);
        final Row row = new Row(item.key, view, view.findViewById(R.id.pipetvRailIcon),
                view.findViewById(R.id.pipetvRailLabel));
        row.icon.setImageResource(item.icon);
        row.label.setText(item.label);
        row.label.setAlpha(expanded ? 1f : 0f);
        view.setContentDescription(item.label);
        // Its own focus look, the white pill
        view.setTag(R.id.pipetv_no_focus_ring, Boolean.TRUE);
        view.setOnClickListener(v -> {
            if (listener != null) {
                listener.onRailItemChosen(row.key);
            }
        });
        parent.addView(view);
        rows.add(row);
    }

    @Nullable
    private Row selectedOrFirstRow() {
        for (final Row row : rows) {
            if (row.key.equals(selectedKey)) {
                return row;
            }
        }
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void updateRows() {
        final boolean open = hasFocus();
        for (final Row row : rows) {
            final boolean focused = row.view.isFocused();
            final boolean selected = row.key.equals(selectedKey);
            final int color;
            if (focused) {
                color = FOCUSED_COLOR;
            } else if (selected) {
                color = SELECTED_COLOR;
            } else {
                color = open ? OPEN_COLOR : CLOSED_COLOR;
            }
            row.icon.setImageTintList(ColorStateList.valueOf(color));
            row.label.setTextColor(color);
            if (focused) {
                row.view.setBackground(focusedBackground);
            } else {
                row.view.setBackground(selected && open ? selectedBackground : null);
            }
            row.icon.setBackground(selected && !open ? selectedIconBackground : null);
        }
    }

    private void setExpanded(final boolean expand) {
        if (expanded == expand) {
            return;
        }
        expanded = expand;
        panel.animate()
                .scaleX(expand ? 1f : collapsedWidth / (float) expandedWidth)
                .alpha(expand ? 1f : 0f)
                .setDuration(ANIMATION_MS)
                .start();
        if (expand) {
            scrim.setVisibility(VISIBLE);
            scrim.animate().alpha(1f).setDuration(ANIMATION_MS).start();
        } else {
            scrim.animate().alpha(0f).setDuration(ANIMATION_MS)
                    .withEndAction(() -> scrim.setVisibility(GONE)).start();
        }
        for (final Row row : rows) {
            row.label.animate().alpha(expand ? 1f : 0f).setDuration(ANIMATION_MS).start();
        }
    }

    private void onGlobalFocusChanged(@Nullable final View oldFocus,
                                      @Nullable final View newFocus) {
        final boolean inside = isInRail(newFocus);
        final boolean wasInside = isInRail(oldFocus);
        if (!inside && !wasInside) {
            return; // a move within the content
        }
        if (inside && oldFocus != null && !wasInside) {
            contentFocus = new WeakReference<>(oldFocus);
        }
        setExpanded(inside);
        updateRows();
    }

    private boolean isInRail(@Nullable final View view) {
        for (View v = view; v != null; ) {
            if (v == this) {
                return true;
            }
            final ViewParent parent = v.getParent();
            v = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnGlobalFocusChangeListener(focusListener);
    }

    @Override
    protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnGlobalFocusChangeListener(focusListener);
        super.onDetachedFromWindow();
    }

    /**
     * From the content, the rail can only be entered by going left, and then on the current
     * page's item. Otherwise the items, which reach over the content's edge, would catch Up and
     * Down in the content's first column.
     */
    @Override
    public void addFocusables(final ArrayList<View> views, final int direction,
                              final int focusableMode) {
        if (getDescendantFocusability() == FOCUS_BLOCK_DESCENDANTS) {
            return; // e.g. under the open player
        }
        if (hasFocus()) {
            super.addFocusables(views, direction, focusableMode);
        } else if (direction == FOCUS_LEFT) {
            final Row row = selectedOrFirstRow();
            if (row != null) {
                row.view.addFocusables(views, direction, focusableMode);
            }
        }
    }

    /** Never entered by a request for "any focus", such as the window's first one. */
    @Override
    protected boolean onRequestFocusInDescendants(final int direction,
                                                  @Nullable final Rect previouslyFocusedRect) {
        return false;
    }

    @Override
    public View focusSearch(final View focused, final int direction) {
        if (direction == FOCUS_UP || direction == FOCUS_DOWN) {
            // Up and Down stay in the rail
            final View next = FocusFinder.getInstance().findNextFocus(this, focused, direction);
            return next != null ? next : focused;
        }
        if (direction == FOCUS_LEFT) {
            return focused;
        }
        if (direction == FOCUS_RIGHT) {
            final View last = contentFocus == null ? null : contentFocus.get();
            if (last != null && last.isAttachedToWindow() && last.isShown()) {
                return last;
            }
        }
        return super.focusSearch(focused, direction);
    }

    private static final class Row {
        final String key;
        final View view;
        final ImageView icon;
        final TextView label;

        Row(final String key, final View view, final ImageView icon, final TextView label) {
            this.key = key;
            this.view = view;
            this.icon = icon;
            this.label = label;
        }
    }
}
