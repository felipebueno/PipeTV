package org.schabi.newpipe.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.ViewPager;

import org.schabi.newpipe.util.DeviceUtils;

/**
 * PipeTV: a ViewPager that leaves Left and Right to the focus search on TVs. There the side rail
 * switches the pages, and Left at a page's edge has to reach the rail instead of turning the
 * page.
 */
public class FocusAwareViewPager extends ViewPager {
    private final boolean tv;

    public FocusAwareViewPager(@NonNull final Context context) {
        this(context, null);
    }

    public FocusAwareViewPager(@NonNull final Context context,
                               @Nullable final AttributeSet attrs) {
        super(context, attrs);
        tv = DeviceUtils.isTv(context);
    }

    @Override
    public boolean executeKeyEvent(@NonNull final KeyEvent event) {
        if (tv && (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT
                || event.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            return false;
        }
        return super.executeKeyEvent(event);
    }
}
