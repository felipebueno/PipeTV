/*
 * Copyright 2019 Alexander Rvachev <rvacheva@nxt.ru>
 * FocusOverlayView.java is part of NewPipe
 *
 * License: GPL-3.0+
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.schabi.newpipe.views;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.view.WindowCallbackWrapper;
import androidx.core.content.ContextCompat;

import org.schabi.newpipe.R;

import java.lang.ref.WeakReference;

public final class FocusOverlayView extends Drawable implements
        ViewTreeObserver.OnGlobalFocusChangeListener,
        ViewTreeObserver.OnDrawListener,
        ViewTreeObserver.OnGlobalLayoutListener,
        ViewTreeObserver.OnScrollChangedListener, ViewTreeObserver.OnTouchModeChangeListener {

    private boolean isInTouchMode;

    private final Rect focusRect = new Rect();

    private final Paint rectPaint = new Paint();

    private final Handler animator = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(final Message msg) {
            updateRect();
        }
    };

    private WeakReference<View> focused;

    // PipeTV: a ring that can be seen from the couch on light and dark screens alike,
    // drawn just outside the focused view so it doesn't cover its content.
    private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF ringRect = new RectF();
    private final float ringGap;
    private final float ringRadius;

    public FocusOverlayView(final Context context) {
        final float density = context.getResources().getDisplayMetrics().density;
        final float ringWidth = 2 * density;
        ringGap = 3 * density;
        ringRadius = 12 * density;
        rectPaint.setAntiAlias(true);
        rectPaint.setStyle(Paint.Style.STROKE);
        rectPaint.setStrokeWidth(ringWidth);
        rectPaint.setColor(ContextCompat.getColor(context, R.color.tv_focus_ring));
        haloPaint.setStyle(Paint.Style.STROKE);
        haloPaint.setStrokeWidth(ringWidth + 2 * density);
        haloPaint.setColor(ContextCompat.getColor(context, R.color.tv_focus_halo));
    }

    @Override
    public void onGlobalFocusChanged(final View oldFocus, final View newFocus) {
        if (newFocus != null) {
            focused = new WeakReference<>(newFocus);
        } else {
            focused = null;
        }

        updateRect();

        animator.sendEmptyMessageDelayed(0, 1000);
    }

    private void updateRect() {
        final View focusedView = focused == null ? null : this.focused.get();

        final int l = focusRect.left;
        final int r = focusRect.right;
        final int t = focusRect.top;
        final int b = focusRect.bottom;

        if (focusedView == null || !isShown(focusedView)
                || !focusedView.getGlobalVisibleRect(focusRect)) {
            // PipeTV: no ring left behind where a hidden view had the focus, e.g. over the
            // video once the player controls fade out
            focusRect.setEmpty();
        }

        if (shouldClearFocusRect(focusedView, focusRect)) {
            focusRect.setEmpty();
        }

        if (l != focusRect.left || r != focusRect.right
                || t != focusRect.top || b != focusRect.bottom) {
            invalidateSelf();
        }
    }

    private boolean isShown(@NonNull final View view) {
        if (view.getWidth() == 0 || view.getHeight() == 0 || !view.isShown()) {
            return false;
        }
        // PipeTV: nor is a view faded out, by itself or with a parent
        for (ViewParent v = view.getParent(); v instanceof View; v = v.getParent()) {
            if (((View) v).getAlpha() == 0f) {
                return false;
            }
        }
        return view.getAlpha() != 0f;
    }

    @Override
    public void onDraw() {
        updateRect();
    }

    @Override
    public void onScrollChanged() {
        updateRect();

        animator.removeMessages(0);
        animator.sendEmptyMessageDelayed(0, 1000);
    }

    @Override
    public void onGlobalLayout() {
        updateRect();

        animator.sendEmptyMessageDelayed(0, 1000);
    }

    @Override
    public void onTouchModeChanged(final boolean inTouchMode) {
        this.isInTouchMode = inTouchMode;

        if (inTouchMode) {
            updateRect();
        } else {
            invalidateSelf();
        }
    }

    public void setCurrentFocus(final View newFocus) {
        if (newFocus == null) {
            return;
        }

        this.isInTouchMode = newFocus.isInTouchMode();

        onGlobalFocusChanged(null, newFocus);
    }

    @Override
    public void draw(@NonNull final Canvas canvas) {
        if (!isInTouchMode && focusRect.width() != 0) {
            ringRect.set(focusRect);
            ringRect.inset(-ringGap, -ringGap);
            canvas.drawRoundRect(ringRect, ringRadius, ringRadius, haloPaint);
            canvas.drawRoundRect(ringRect, ringRadius, ringRadius, rectPaint);
        }
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    @Override
    public void setAlpha(final int alpha) {
    }

    @Override
    public void setColorFilter(final ColorFilter colorFilter) {
    }

    /*
     * When any view in the player looses it's focus (after setVisibility(GONE)) the focus gets
     * added to the whole fragment which has a width and height equal to the window frame.
     * The easiest way to avoid the unneeded frame is to skip highlighting of rect that is
     * equal to the overlayView bounds
     * */
    private boolean shouldClearFocusRect(@Nullable final View focusedView, final Rect focusedRect) {
        return focusedView == null || focusedRect.equals(getBounds())
                // PipeTV: a window can turn the ring off, e.g. while a cover hides its content
                || focusedView.getRootView().getTag(R.id.pipetv_no_focus_ring) != null;
    }

    public static void setupFocusObserver(final Dialog dialog) {
        final Rect displayRect = new Rect();

        final Window window = dialog.getWindow();
        assert window != null;

        final View decor = window.getDecorView();
        decor.getWindowVisibleDisplayFrame(displayRect);

        final FocusOverlayView overlay = new FocusOverlayView(dialog.getContext());
        overlay.setBounds(0, 0, displayRect.width(), displayRect.height());

        setupOverlay(window, overlay);
    }

    public static void setupFocusObserver(final Activity activity) {
        final Rect displayRect = new Rect();

        final Window window = activity.getWindow();
        final View decor = window.getDecorView();
        decor.getWindowVisibleDisplayFrame(displayRect);

        final FocusOverlayView overlay = new FocusOverlayView(activity);
        overlay.setBounds(0, 0, displayRect.width(), displayRect.height());

        setupOverlay(window, overlay);
    }

    private static void setupOverlay(final Window window, final FocusOverlayView overlay) {
        final ViewGroup decor = (ViewGroup) window.getDecorView();
        decor.getOverlay().add(overlay);

        fixFocusHierarchy(decor);

        final ViewTreeObserver observer = decor.getViewTreeObserver();
        observer.addOnScrollChangedListener(overlay);
        observer.addOnGlobalFocusChangeListener(overlay);
        observer.addOnGlobalLayoutListener(overlay);
        observer.addOnTouchModeChangeListener(overlay);
        observer.addOnDrawListener(overlay);

        overlay.setCurrentFocus(decor.getFocusedChild());

        // Some key presses don't actually move focus, but still result in movement on screen.
        // For example, MovementMethod of TextView may cause requestRectangleOnScreen() due to
        // some "focusable" spans, which in turn causes CoordinatorLayout to "scroll" it's children.
        // Unfortunately many such forms of "scrolling" do not count as scrolling for purpose
        // of dispatching ViewTreeObserver callbacks, so we have to intercept them by directly
        // receiving keys from Window.
        window.setCallback(new WindowCallbackWrapper(window.getCallback()) {
            @Override
            public boolean dispatchKeyEvent(final KeyEvent event) {
                final boolean res = super.dispatchKeyEvent(event);
                overlay.onKey(event);
                return res;
            }
        });
    }

    private void onKey(final KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return;
        }

        updateRect();

        animator.sendEmptyMessageDelayed(0, 100);
    }

    private static void fixFocusHierarchy(final View decor) {
        // During Android 8 development some dumb ass decided, that action bar has to be
        // a keyboard focus cluster. Unfortunately, keyboard clusters do not work for primary
        // auditory of key navigation — Android TV users (Android TV remotes do not have
        // keyboard META key for moving between clusters). We have to fix this unfortunate accident
        // While we are at it, let's deal with touchscreenBlocksFocus too.

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        if (!(decor instanceof ViewGroup)) {
            return;
        }

        clearFocusObstacles((ViewGroup) decor);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private static void clearFocusObstacles(final ViewGroup viewGroup) {
        viewGroup.setTouchscreenBlocksFocus(false);

        if (viewGroup.isKeyboardNavigationCluster()) {
            viewGroup.setKeyboardNavigationCluster(false);

            return; // clusters aren't supposed to nest
        }

        final int childCount = viewGroup.getChildCount();

        for (int i = 0; i < childCount; ++i) {
            final View view = viewGroup.getChildAt(i);

            if (view instanceof ViewGroup) {
                clearFocusObstacles((ViewGroup) view);
            }
        }
    }
}
