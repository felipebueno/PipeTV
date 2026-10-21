package org.schabi.newpipe.views;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.R;

/**
 * PipeTV: the scroll of the TV home's rows. As in the TV apps, it brings the focused row up to
 * the top of the screen, title included, instead of scrolling just enough to show the card.
 */
public final class TvRowsScrollView extends ScrollView {
    public interface FocusRestorer {
        /** Focuses where the focus was the last time, if it can; false otherwise. */
        boolean restoreFocus();
    }

    private final int alignMargin;
    @Nullable
    private FocusRestorer focusRestorer;

    public TvRowsScrollView(@NonNull final Context context) {
        super(context);
        alignMargin = Math.round(8 * getResources().getDisplayMetrics().density);
        setVerticalScrollBarEnabled(false);
        // The cards take the focus, never the scroll itself (which would wear the focus ring
        // around the whole home while the rows load)
        setFocusable(false);
    }

    /**
     * Gives the focus back to where it was when the rows get it as a whole, e.g. as the player
     * closes without a card to return to. Without it, the first card would take it, and none
     * when that card is scrolled out of the screen.
     */
    public void setFocusRestorer(@Nullable final FocusRestorer restorer) {
        focusRestorer = restorer;
    }

    @Override
    protected boolean onRequestFocusInDescendants(final int direction,
                                                  final Rect previouslyFocusedRect) {
        return (focusRestorer != null && focusRestorer.restoreFocus())
                || super.onRequestFocusInDescendants(direction, previouslyFocusedRect);
    }

    /** Marks a view above the rows, e.g. the update banner, which the first row keeps in view. */
    public static void markHeader(@NonNull final View view) {
        view.setTag(R.id.pipetv_rows_header, Boolean.TRUE);
    }

    @Override
    protected int computeScrollDeltaToGetChildRectOnScreen(final Rect rect) {
        final View row = focusedRow();
        if (row == null) {
            return super.computeScrollDeltaToGetChildRectOnScreen(rect);
        }
        final int maxScroll = Math.max(0, getChildAt(0).getHeight() - getHeight());
        final int top = isFirstRow(row) ? 0 : row.getTop() - alignMargin;
        final int target = Math.min(maxScroll, Math.max(0, top));
        return target - getScrollY();
    }

    /** Whether only headers, or nothing, come before the row. */
    private boolean isFirstRow(@NonNull final View row) {
        final ViewGroup column = (ViewGroup) getChildAt(0);
        for (int i = 0; i < column.getChildCount(); i++) {
            final View child = column.getChildAt(i);
            if (child == row) {
                return true;
            }
            if (child.getVisibility() == VISIBLE && child.getTag(R.id.pipetv_rows_header) == null) {
                return false;
            }
        }
        return false;
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
