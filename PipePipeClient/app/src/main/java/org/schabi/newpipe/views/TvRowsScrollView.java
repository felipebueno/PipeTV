package org.schabi.newpipe.views;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewParent;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * PipeTV: the scroll of the TV home's rows. As in the TV apps, it brings the focused row up to
 * the top of the screen, title included, instead of scrolling just enough to show the card.
 */
public final class TvRowsScrollView extends ScrollView {
    private final int alignMargin;

    public TvRowsScrollView(@NonNull final Context context) {
        super(context);
        alignMargin = Math.round(8 * getResources().getDisplayMetrics().density);
        setVerticalScrollBarEnabled(false);
        // The cards take the focus, never the scroll itself (which would wear the focus ring
        // around the whole home while the rows load)
        setFocusable(false);
    }

    @Override
    protected int computeScrollDeltaToGetChildRectOnScreen(final Rect rect) {
        final View row = focusedRow();
        if (row == null) {
            return super.computeScrollDeltaToGetChildRectOnScreen(rect);
        }
        final int maxScroll = Math.max(0, getChildAt(0).getHeight() - getHeight());
        final int target = Math.min(maxScroll, Math.max(0, row.getTop() - alignMargin));
        return target - getScrollY();
    }

    /** The row, a child of the rows' column, holding the focused view. */
    @Nullable
    private View focusedRow() {
        if (getChildCount() == 0) {
            return null;
        }
        final View column = getChildAt(0);
        View view = findFocus();
        while (view != null && view.getParent() != column) {
            final ViewParent parent = view.getParent();
            view = parent instanceof View ? (View) parent : null;
        }
        return view;
    }
}
