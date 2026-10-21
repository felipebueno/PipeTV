package org.schabi.newpipe.update;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.DownloaderImpl;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.util.DeviceUtils;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * PipeTV: finds newer builds of PipeTV on its GitHub releases. Release builds look for them
 * every hour, unless the settings say not to; the TV home offers them, and phones get a
 * notification. Development builds, with other app ids, can't install release builds over them.
 */
public final class PipeTvUpdates {
    private static final String TAG = "PipeTvUpdates";
    private static final String RELEASE_URL = "https://github.com/" + PipeTvRelease.REPOSITORY
            + "/releases/latest/download/release.json";
    private static final long CHECK_EVERY_MS = TimeUnit.HOURS.toMillis(1);
    private static final long RETRY_AFTER_MS = TimeUnit.MINUTES.toMillis(30);
    private static final String PREF_RELEASE = "pipetv_update_release";
    private static final String PREF_CHECKED_AT = "pipetv_update_checked_at";
    private static final String PREF_NOTIFIED_BUILD = "pipetv_update_notified_build";
    private static final int NOTIFICATION_ID = 2000;
    /** Opens the update dialog, from the notification phones get. */
    public static final String EXTRA_SHOW_UPDATE = "pipetv_show_update";

    /** The newer build there is, if any. */
    private static final BehaviorSubject<Optional<PipeTvRelease>> NEWER =
            BehaviorSubject.createDefault(Optional.empty());
    private static boolean restored;
    @Nullable
    private static Disposable checking;

    private PipeTvUpdates() {
    }

    /** Whether this build can update itself: only release builds take release builds. */
    public static boolean isSupported() {
        return "prod".equals(BuildConfig.PIPETV_CHANNEL);
    }

    /** This build's number, without the "+" of uncommitted changes. */
    public static int installedBuild() {
        try {
            return Integer.parseInt(BuildConfig.PIPETV_BUILD.replace("+", ""));
        } catch (final NumberFormatException e) {
            return 0;
        }
    }

    /** The newer build there is, if any, now and as it changes. */
    @NonNull
    public static Observable<Optional<PipeTvRelease>> newer() {
        return NEWER.distinctUntilChanged((a, b) -> a.isPresent() == b.isPresent()
                && (!a.isPresent() || a.get().build == b.get().build));
    }

    /** The newer build found last, if any. */
    @NonNull
    public static Optional<PipeTvRelease> latestNewer(@NonNull final Context context) {
        restore(context.getApplicationContext());
        return NEWER.getValue();
    }

    /** Once per run: the newer build found before, until the next check. */
    private static void restore(@NonNull final Context app) {
        if (restored) {
            return;
        }
        restored = true;
        final Optional<PipeTvRelease> newer = publish(app,
                PreferenceManager.getDefaultSharedPreferences(app).getString(PREF_RELEASE, null));
        // Only the download of the build still on offer is of use, e.g. if its install was
        // cancelled; an installed one isn't
        deleteDownloadsExcept(app, newer.map(release ->
                PipeTvUpdateInstaller.apkFile(app, release)).orElse(null));
    }

    private static boolean isEnabled(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.update_app_key), true);
    }

    /**
     * Brings back the newer build found before, and looks for one again when it's time. For
     * the app's start and return to the screen.
     */
    public static void checkWhenDue(@NonNull final Context context) {
        if (!isSupported()) {
            return;
        }
        final Context app = context.getApplicationContext();
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(app);
        restore(app);
        if (!isEnabled(app) || (checking != null && !checking.isDisposed())
                || System.currentTimeMillis() - prefs.getLong(PREF_CHECKED_AT, 0)
                < CHECK_EVERY_MS) {
            return;
        }
        checking = check(app).subscribe(release -> {
            if (release.isPresent() && !DeviceUtils.isTv(app)) {
                notifyOnce(app, release.get());
            }
        }, error -> Log.w(TAG, "Could not check for updates", error));
    }

    /** Looks for a newer build now. */
    @NonNull
    public static Single<Optional<PipeTvRelease>> check(@NonNull final Context context) {
        final Context app = context.getApplicationContext();
        return Single.fromCallable(PipeTvUpdates::fetchReleaseJson)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError(error -> PreferenceManager.getDefaultSharedPreferences(app).edit()
                        // Offline or the like: the next check comes sooner
                        .putLong(PREF_CHECKED_AT, System.currentTimeMillis() - CHECK_EVERY_MS
                                + RETRY_AFTER_MS)
                        .apply())
                .map(json -> {
                    PreferenceManager.getDefaultSharedPreferences(app).edit()
                            .putString(PREF_RELEASE, json)
                            .putLong(PREF_CHECKED_AT, System.currentTimeMillis())
                            .apply();
                    return publish(app, json);
                });
    }

    @NonNull
    private static String fetchReleaseJson() throws IOException {
        final Request request = new Request.Builder().url(RELEASE_URL).build();
        try (Response response = DownloaderImpl.getInstance().getClient().newCall(request)
                .execute()) {
            final ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                throw new IOException("HTTP " + response.code() + " for " + RELEASE_URL);
            }
            return body.string();
        }
    }

    @NonNull
    private static Optional<PipeTvRelease> publish(@NonNull final Context context,
                                                   @Nullable final String json) {
        Optional<PipeTvRelease> newer = Optional.empty();
        if (json != null) {
            try {
                final PipeTvRelease release = PipeTvRelease.parse(json);
                if (release != null && release.build > installedBuild()) {
                    newer = Optional.of(release);
                }
            } catch (final Exception e) {
                Log.w(TAG, "Could not read the release", e);
            }
        }
        NEWER.onNext(newer);
        return newer;
    }

    /** Phones are told once per build, with a notification that opens the update. */
    @SuppressLint("MissingPermission")
    private static void notifyOnce(@NonNull final Context context,
                                   @NonNull final PipeTvRelease release) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (prefs.getInt(PREF_NOTIFIED_BUILD, 0) >= release.build) {
            return;
        }
        prefs.edit().putInt(PREF_NOTIFIED_BUILD, release.build).apply();
        final Intent intent = new Intent(context, MainActivity.class)
                .putExtra(EXTRA_SHOW_UPDATE, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        final PendingIntent pendingIntent = PendingIntent.getActivity(context, NOTIFICATION_ID,
                intent, PendingIntent.FLAG_UPDATE_CURRENT
                        | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_IMMUTABLE : 0));
        final NotificationCompat.Builder notification = new NotificationCompat.Builder(context,
                context.getString(R.string.app_update_notification_channel_id))
                .setSmallIcon(R.drawable.ic_newpipe_update)
                .setContentTitle(context.getString(R.string.pipetv_update_notification_title,
                        release.version))
                .setContentText(context.getString(R.string.pipetv_update_notification_text))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification.build());
        } catch (final SecurityException e) {
            // Notifications not allowed: the settings still offer the update
            Log.i(TAG, "Could not notify of the update", e);
        }
    }

    /** Where updates are downloaded to before Android installs them. */
    @NonNull
    static File downloadDir(@NonNull final Context context) {
        return new File(context.getCacheDir(), "updates");
    }

    static void deleteDownloadsExcept(@NonNull final Context context, @Nullable final File keep) {
        final File[] files = downloadDir(context).listFiles();
        if (files != null) {
            for (final File file : files) {
                if (!file.equals(keep) && !file.delete()) {
                    Log.w(TAG, "Could not delete " + file);
                }
            }
        }
    }
}
