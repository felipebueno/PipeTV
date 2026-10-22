package org.schabi.newpipe.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

/**
 * PipeTV: a row of cards on TVs that keeps the focus at its ends. Right on its last card stays
 * there, where it used to jump to a card of another row and leave this one scrolled to its end.
 * Left on its first card leaves the row only if the row allows it, as the home's rows do for the
 * side rail.
 */
public final class TvRowList extends RecyclerView {
    private boolean leftLeaves = true;

    public TvRowList(@NonNull final Context context) {
        super(context);
    }

    public TvRowList(@NonNull final Context context, @Nullable final AttributeSet attrs) {
        super(context, attrs);
    }

    public TvRowList(@NonNull final Context context, @Nullable final AttributeSet attrs,
                     final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /** Whether Left on the first card may take the focus out of the row. */
    public void setLeftLeaves(final boolean leaves) {
        leftLeaves = leaves;
    }

    @Override
    public View focusSearch(final View focused, final int direction) {
        final View next = super.focusSearch(focused, direction);
        final boolean keepsFocus = direction == FOCUS_RIGHT
                || (direction == FOCUS_LEFT && !leftLeaves);
        if (keepsFocus && !isInside(next)) {
            return focused;
        }
        return next;
    }

    private boolean isInside(@Nullable final View view) {
        for (View v = view; v != null; ) {
            if (v == this) {
                return true;
            }
            final ViewParent parent = v.getParent();
            v = parent instanceof View ? (View) parent : null;
        }
        return false;
    }
}
