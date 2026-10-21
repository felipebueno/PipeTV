package org.schabi.newpipe.update;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import org.schabi.newpipe.DownloaderImpl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * PipeTV: downloads a build of PipeTV, checks it against its release's checksum, and hands it to
 * Android's installer, which asks to confirm and ends on "Open", back into PipeTV.
 */
public final class PipeTvUpdateInstaller {
    private static final long PROGRESS_EVERY_MS = 200;

    /** How far a download got, in bytes. */
    public static final class Progress {
        public final long done;
        public final long total;

        Progress(final long done, final long total) {
            this.done = done;
            this.total = total;
        }
    }

    private PipeTvUpdateInstaller() {
    }

    /** Downloads the build's APK, telling how far it got; completes once it's checked. */
    @NonNull
    public static Observable<Progress> download(@NonNull final Context context,
                                                @NonNull final PipeTvRelease release) {
        final Context app = context.getApplicationContext();
        return Observable.<Progress>create(emitter -> {
            final File dir = PipeTvUpdates.downloadDir(app);
            final File apk = apkFile(app, release);
            final File part = new File(dir, apk.getName() + ".part");
            final Call call = DownloaderImpl.getInstance().getClient()
                    .newCall(new Request.Builder().url(release.apkUrl()).build());
            emitter.setCancellable(call::cancel);
            try {
                // Downloaded already, e.g. before the installer was cancelled
                if (apk.length() == release.apkSize && matches(apk, release)) {
                    emitter.onNext(new Progress(release.apkSize, release.apkSize));
                    emitter.onComplete();
                    return;
                }
                if (!dir.isDirectory() && !dir.mkdirs()) {
                    throw new IOException("Could not create " + dir);
                }
                PipeTvUpdates.deleteDownloadsExcept(app, null);
                final MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
                try (Response response = call.execute()) {
                    final ResponseBody body = response.body();
                    if (!response.isSuccessful() || body == null) {
                        throw new IOException("HTTP " + response.code());
                    }
                    final long total = body.contentLength() > 0
                            ? body.contentLength() : release.apkSize;
                    try (InputStream in = body.byteStream();
                         OutputStream out = new FileOutputStream(part)) {
                        final byte[] buffer = new byte[64 * 1024];
                        long done = 0;
                        long reportedAt = 0;
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                            sha256.update(buffer, 0, read);
                            done += read;
                            final long now = SystemClock.uptimeMillis();
                            if (now - reportedAt >= PROGRESS_EVERY_MS) {
                                reportedAt = now;
                                emitter.onNext(new Progress(done, total));
                            }
                        }
                        emitter.onNext(new Progress(done, total));
                    }
                }
                if (!hex(sha256.digest()).equalsIgnoreCase(release.apkSha256)) {
                    throw new IOException("The download doesn't match its checksum");
                }
                if (!part.renameTo(apk)) {
                    throw new IOException("Could not keep the download");
                }
                emitter.onComplete();
            } catch (final Exception e) {
                //noinspection ResultOfMethodCallIgnored
                part.delete();
                emitter.tryOnError(e);
            }
        }).subscribeOn(Schedulers.io());
    }

    /** Opens Android's installer on the downloaded build. */
    public static void install(@NonNull final Activity activity,
                               @NonNull final PipeTvRelease release)
            throws ActivityNotFoundException {
        // The provider the app already has shares any of its files
        final Uri apk = FileProvider.getUriForFile(activity,
                activity.getPackageName() + ".provider", apkFile(activity, release));
        // Unlike a package installer session, Android's installer ends on "Open", so PipeTV
        // comes back after it updates on any Android version. In a task of its own, which
        // outlives PipeTV's: replacing PipeTV removes PipeTV's task.
        @SuppressWarnings("deprecation") final Intent intent =
                new Intent(Intent.ACTION_INSTALL_PACKAGE)
                        .setData(apk)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
    }

    /** Where a build's APK is downloaded to, by its build number. */
    @NonNull
    static File apkFile(@NonNull final Context context, @NonNull final PipeTvRelease release) {
        return new File(PipeTvUpdates.downloadDir(context), "build-" + release.build + ".apk");
    }

    private static boolean matches(@NonNull final File file, @NonNull final PipeTvRelease release)
            throws Exception {
        final MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            final byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                sha256.update(buffer, 0, read);
            }
        }
        return hex(sha256.digest()).equalsIgnoreCase(release.apkSha256);
    }

    @NonNull
    private static String hex(@NonNull final byte[] bytes) {
        final StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (final byte b : bytes) {
            builder.append(Character.forDigit((b >> 4) & 0xF, 16))
                    .append(Character.forDigit(b & 0xF, 16));
        }
        return builder.toString();
    }
}
