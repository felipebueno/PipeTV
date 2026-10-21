package org.schabi.newpipe.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;

import org.schabi.newpipe.util.NewPipeTextViewHelper;
import org.schabi.newpipe.util.external_communication.ShareUtils;

/**
 * An {@link AppCompatEditText} which uses {@link ShareUtils#shareText(Context, String, String)}
 * when sharing selected text by using the {@code Share} command of the floating actions.
 * <p>
 * This allows NewPipe to show Android share sheet instead of EMUI share sheet when sharing text
 * from {@link AppCompatEditText} on EMUI devices.
 * </p>
 */
public class NewPipeEditText extends AppCompatEditText {

    /** PipeTV: asked to take the focus down, out of the box; returns whether it did. */
    public interface OnDownListener {
        boolean onDown();
    }

    @Nullable
    private OnDownListener onDownListener;
    private boolean keyboardShown;

    public NewPipeEditText(@NonNull final Context context) {
        super(context);
    }

    public NewPipeEditText(@NonNull final Context context, @Nullable final AttributeSet attrs) {
        super(context, attrs);
    }

    public NewPipeEditText(@NonNull final Context context,
                           @Nullable final AttributeSet attrs,
                           final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * PipeTV: on TVs the on-screen keyboard gets every key before the app, and even once closed
     * it can keep Down from leaving the box for the list below. So Down is handled here, before
     * the keyboard, whenever the keyboard isn't open; while it is, it needs the D-pad itself.
     */
    public void setOnDownListener(@Nullable final OnDownListener listener) {
        onDownListener = listener;
    }

    /** PipeTV: tells the box whether the on-screen keyboard is open. */
    public void setKeyboardShown(final boolean shown) {
        keyboardShown = shown;
    }

    @Override
    public boolean onKeyPreIme(final int keyCode, final KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            keyboardShown = false; // Back closes an open keyboard before anything else
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && !keyboardShown
                && onDownListener != null && event.getAction() == KeyEvent.ACTION_DOWN
                && onDownListener.onDown()) {
            return true;
        }
        return super.onKeyPreIme(keyCode, event);
    }

    @Override
    public boolean onTextContextMenuItem(final int id) {
        if (id == android.R.id.shareText) {
            NewPipeTextViewHelper.shareSelectedTextWithShareUtils(this);
            return true;
        }
        return super.onTextContextMenuItem(id);
    }
}
