package org.schabi.newpipe.update;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.format.Formatter;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.R;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

/**
 * PipeTV: an update's dialog: what's new in it, then its download's progress, then Android's
 * installer takes over.
 */
public final class PipeTvUpdateDialog {
    private static final String TAG = "PipeTvUpdateDialog";

    private PipeTvUpdateDialog() {
    }

    /** Offers the newer build found last, if there is one. */
    public static void showLatest(@NonNull final Activity activity) {
        PipeTvUpdates.latestNewer(activity).ifPresent(release -> show(activity, release));
    }

    public static void show(@NonNull final Activity activity,
                            @NonNull final PipeTvRelease release) {
        final View view = LayoutInflater.from(activity)
                .inflate(R.layout.pipetv_update_dialog, null);
        final TextView notes = view.findViewById(R.id.pipetvUpdateNotes);
        final ProgressBar progress = view.findViewById(R.id.pipetvUpdateProgress);
        final TextView status = view.findViewById(R.id.pipetvUpdateStatus);
        notes.setText(describe(activity, release));

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.pipetv_update_title, release.version))
                .setView(view)
                .setPositiveButton(R.string.pipetv_update_install, null)
                .setNegativeButton(R.string.pipetv_update_later, null)
                .create();
        final CompositeDisposable download = new CompositeDisposable();
        dialog.setOnDismissListener(d -> download.dispose());
        dialog.setOnShowListener(d -> {
            final Button update = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            final Button later = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);
            update.requestFocus();
            // Set here, so the button doesn't close the dialog
            update.setOnClickListener(v -> {
                update.setVisibility(View.GONE);
                later.setText(android.R.string.cancel);
                later.requestFocus();
                progress.setIndeterminate(true);
                progress.setVisibility(View.VISIBLE);
                status.setText(R.string.pipetv_update_starting);
                status.setVisibility(View.VISIBLE);
                download.add(PipeTvUpdateInstaller.download(activity, release)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(got -> {
                            progress.setIndeterminate(false);
                            progress.setProgress((int) (1000 * got.done
                                    / Math.max(1, got.total)));
                            status.setText(activity.getString(R.string.pipetv_update_downloading,
                                    Formatter.formatShortFileSize(activity, got.done),
                                    Formatter.formatShortFileSize(activity, got.total)));
                        }, error -> {
                            Log.w(TAG, "Could not download the update", error);
                            progress.setVisibility(View.GONE);
                            status.setText(activity.getString(R.string.pipetv_update_failed,
                                    error.getMessage() != null ? error.getMessage()
                                            : error.getClass().getSimpleName()));
                            update.setText(R.string.pipetv_update_retry);
                            update.setVisibility(View.VISIBLE);
                            update.requestFocus();
                            later.setText(R.string.pipetv_update_later);
                        }, () -> {
                            dialog.dismiss();
                            try {
                                PipeTvUpdateInstaller.install(activity, release);
                            } catch (final ActivityNotFoundException e) {
                                Toast.makeText(activity, R.string.pipetv_update_no_installer,
                                        Toast.LENGTH_LONG).show();
                            }
                        }));
            });
        });
        dialog.show();
    }

    @NonNull
    private static CharSequence describe(@NonNull final Context context,
                                         @NonNull final PipeTvRelease release) {
        final SpannableStringBuilder text = new SpannableStringBuilder(context.getString(
                R.string.pipetv_update_builds, release.build, BuildConfig.PIPETV_BUILD));
        if (!release.notes.isEmpty()) {
            text.append("\n\n");
            appendBold(text, context.getString(R.string.pipetv_update_whats_new,
                    release.version));
            text.append("\n\n").append(formatNotes(release.notes));
        }
        return text;
    }

    /**
     * The notes' Markdown as the dialog shows it: headings in bold, bullets, and the lines
     * CHANGELOG.md wraps joined back.
     */
    @NonNull
    static CharSequence formatNotes(@NonNull final String markdown) {
        final SpannableStringBuilder text = new SpannableStringBuilder();
        for (final String line : markdown.replace("`", "").split("\n")) {
            if (line.startsWith("#")) {
                if (text.length() > 0) {
                    text.append("\n\n");
                }
                appendBold(text, line.replaceFirst("^#+\\s*", ""));
            } else if (line.startsWith("- ")) {
                text.append("\n• ").append(line.substring(2).trim());
            } else if (!line.trim().isEmpty()) {
                text.append(' ').append(line.trim());
            }
        }
        return text;
    }

    private static void appendBold(@NonNull final SpannableStringBuilder text,
                                   @NonNull final String bold) {
        final int start = text.length();
        text.append(bold);
        text.setSpan(new StyleSpan(Typeface.BOLD), start, text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}
