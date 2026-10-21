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

import android.animation.ValueAnimator;
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
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.view.WindowCallbackWrapper;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.ThemeHelper;

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

    // PipeTV: a soft white ring with a faint glow, drawn just outside the focused view so it
    // doesn't cover its content. It glides from the last focus to the new one, and fades in
    // where the focus appears; both only redraw this overlay, never the views.
    private static final long GLIDE_MS = 160;
    private static final long FADE_IN_MS = 120;
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int glowAlpha;
    private final RectF ringRect = new RectF();
    private final RectF shownRect = new RectF();
    private final RectF glideFrom = new RectF();
    private final ValueAnimator glide = ValueAnimator.ofFloat(0f, 1f);
    private final ValueAnimator fadeIn = ValueAnimator.ofFloat(0f, 1f);
    @Nullable
    private WeakReference<View> ringOn;
    private final float ringGap;
    private final float ringRadius;
    private final float glowWidth;
    private static final float LIFT_SCALE = 1.04f;
    private static final long LIFT_MS = 160;
    private final float liftZ;
    private final DecelerateInterpolator liftInterpolator = new DecelerateInterpolator(2f);
    @Nullable
    private WeakReference<View> lifted;

    public FocusOverlayView(final Context context) {
        final float density = context.getResources().getDisplayMetrics().density;
        final float ringWidth = 2 * density;
        ringGap = 3 * density;
        ringRadius = 12 * density;
        glowWidth = 2 * density;
        liftZ = 4 * density;
        rectPaint.setAntiAlias(true);
        rectPaint.setStyle(Paint.Style.STROKE);
        rectPaint.setStrokeWidth(ringWidth);
        // White on the dark themes TVs use; dark if someone picks the light theme there
        final boolean light = ThemeHelper.isLightThemeSelected(context);
        rectPaint.setColor(light ? 0xDD000000
                : ContextCompat.getColor(context, R.color.tv_focus_ring));
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(glowWidth);
        glowPaint.setColor(light ? 0x33000000
                : ContextCompat.getColor(context, R.color.tv_focus_halo));
        glowAlpha = glowPaint.getAlpha();

        glide.setDuration(GLIDE_MS);
        glide.setInterpolator(new DecelerateInterpolator(2f));
        glide.addUpdateListener(animation -> {
            final float t = (float) animation.getAnimatedValue();
            shownRect.set(glideFrom.left + (focusRect.left - glideFrom.left) * t,
                    glideFrom.top + (focusRect.top - glideFrom.top) * t,
                    glideFrom.right + (focusRect.right - glideFrom.right) * t,
                    glideFrom.bottom + (focusRect.bottom - glideFrom.bottom) * t);
            invalidateSelf();
        });
        fadeIn.setDuration(FADE_IN_MS);
        fadeIn.addUpdateListener(animation -> invalidateSelf());
    }

    @Override
    public void onGlobalFocusChanged(final View oldFocus, final View newFocus) {
        if (newFocus != null) {
            focused = new WeakReference<>(newFocus);
        } else {
            focused = null;
        }
        lift(newFocus);

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

        final View ringView = ringOn == null ? null : ringOn.get();
        if (focusRect.isEmpty()) {
            glide.cancel();
            fadeIn.cancel();
            shownRect.setEmpty();
            ringOn = null;
        } else if (shownRect.isEmpty()) {
            // Fades in where the focus appears
            shownRect.set(focusRect);
            ringOn = new WeakReference<>(focusedView);
            fadeIn.start();
        } else if (focusedView != ringView) {
            // Glides from the last focus to the new one, even from the middle of a glide
            glideFrom.set(shownRect);
            ringOn = new WeakReference<>(focusedView);
            glide.start();
        } else if (!glide.isRunning()) {
            // The same view moved, e.g. scrolled: the ring follows it at once
            shownRect.set(focusRect);
        }

        if (l != focusRect.left || r != focusRect.right
                || t != focusRect.top || b != focusRect.bottom) {
            invalidateSelf();
        }
    }

    /**
     * PipeTV: the focused card grows a little and rises above its neighbors, as on the TV apps,
     * and settles back when the focus leaves. A card is an item of a list, at most half as wide
     * as it, so full-width rows such as the settings' stay as they are. Only scale and Z move:
     * no layout pass.
     */
    private void lift(@Nullable final View newFocus) {
        final View previous = lifted == null ? null : lifted.get();
        if (previous == newFocus) {
            return;
        }
        if (previous != null) {
            previous.animate().scaleX(1f).scaleY(1f).translationZ(0f)
                    .setDuration(LIFT_MS).setInterpolator(liftInterpolator).start();
        }
        lifted = null;
        if (newFocus == null || !(newFocus.getParent() instanceof RecyclerView)) {
            return;
        }
        final RecyclerView list = (RecyclerView) newFocus.getParent();
        if (newFocus.getWidth() == 0 || newFocus.getWidth() * 2 > list.getWidth()) {
            return;
        }
        // The grown card may reach past its list's edges
        list.setClipChildren(false);
        list.setClipToPadding(false);
        if (list.getParent() instanceof ViewGroup) {
            ((ViewGroup) list.getParent()).setClipChildren(false);
        }
        newFocus.animate().scaleX(LIFT_SCALE).scaleY(LIFT_SCALE).translationZ(liftZ)
                .setDuration(LIFT_MS).setInterpolator(liftInterpolator).start();
        lifted = new WeakReference<>(newFocus);
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
        if (isInTouchMode || shownRect.isEmpty()) {
            return;
        }
        final float alpha = fadeIn.isRunning() ? (float) fadeIn.getAnimatedValue() : 1f;
        rectPaint.setAlpha(Math.round(255 * alpha));
        ringRect.set(shownRect);
        ringRect.inset(-ringGap, -ringGap);
        canvas.drawRoundRect(ringRect, ringRadius, ringRadius, rectPaint);
        // The glow: two faint strokes around the ring, the outer one fainter
        glowPaint.setAlpha(Math.round(glowAlpha * alpha));
        ringRect.inset(-glowWidth, -glowWidth);
        canvas.drawRoundRect(ringRect, ringRadius + glowWidth, ringRadius + glowWidth, glowPaint);
        glowPaint.setAlpha(Math.round(glowAlpha * alpha / 3));
        ringRect.inset(-glowWidth, -glowWidth);
        canvas.drawRoundRect(ringRect, ringRadius + 2 * glowWidth, ringRadius + 2 * glowWidth,
                glowPaint);
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
                // PipeTV: a window can turn the ring off, e.g. while a cover hides its content,
                // and so can a view with a focus look of its own
                || focusedView.getRootView().getTag(R.id.pipetv_no_focus_ring) != null
                || focusedView.getTag(R.id.pipetv_no_focus_ring) != null;
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
