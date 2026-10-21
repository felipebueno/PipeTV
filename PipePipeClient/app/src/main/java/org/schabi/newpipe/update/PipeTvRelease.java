package org.schabi.newpipe.update;

import android.os.Build;
import android.os.Process;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

/**
 * PipeTV: a build of PipeTV published on its GitHub releases, as the release's release.json
 * describes it (see .github/workflows/build.yml), with the APK for this device.
 */
public final class PipeTvRelease {
    static final String REPOSITORY = "felipebueno/PipeTV";

    /** The version, like 0.7.0. */
    @NonNull
    public final String version;
    /** The build number, the commit count, which grows with each build. */
    public final int build;
    /** What's new in the version, in Markdown: its section of CHANGELOG.md. */
    @NonNull
    public final String notes;
    @NonNull
    final String apkFile;
    final long apkSize;
    @NonNull
    final String apkSha256;
    /** The release.json it came from, to keep it until the next check. */
    @NonNull
    final String json;

    private PipeTvRelease(@NonNull final String version, final int build,
                          @NonNull final String notes, @NonNull final String apkFile,
                          final long apkSize, @NonNull final String apkSha256,
                          @NonNull final String json) {
        this.version = version;
        this.build = build;
        this.notes = notes;
        this.apkFile = apkFile;
        this.apkSize = apkSize;
        this.apkSha256 = apkSha256;
        this.json = json;
    }

    /** @return the release, or null when it has no APK for this device */
    @Nullable
    static PipeTvRelease parse(@NonNull final String json) throws JsonParserException {
        final JsonObject root = JsonParser.object().from(json);
        final JsonObject apk = root.getObject("apks").getObject(deviceAbi());
        if (apk.isEmpty() || root.getInt("build", 0) <= 0) {
            return null;
        }
        return new PipeTvRelease(root.getString("version", ""), root.getInt("build"),
                root.getString("notes", ""), apk.getString("file", ""), apk.getLong("size", 0),
                apk.getString("sha256", ""), json);
    }

    /**
     * The ABI of the APK this device runs, whose kind the update keeps: a 64-bit device may
     * well run the 32-bit APK.
     */
    @NonNull
    static String deviceAbi() {
        final String[] abis = Process.is64Bit()
                ? Build.SUPPORTED_64_BIT_ABIS : Build.SUPPORTED_32_BIT_ABIS;
        return abis.length > 0 ? abis[0] : Build.SUPPORTED_ABIS[0];
    }

    /** The APK's address, in its own release, so a newer release can't change it under us. */
    @NonNull
    String apkUrl() {
        return "https://github.com/" + REPOSITORY + "/releases/download/build-" + build + "/"
                + apkFile;
    }
}
