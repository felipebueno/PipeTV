package org.schabi.newpipe.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.DeviceUtils;

/**
 * PipeTV: settings PipeTV changed after it was installed, applied once to existing installs.
 * A new default only reaches new installs, since the settings store their values on the first
 * run or as soon as their screen is shown. Its own version, apart from PipePipe's migrations,
 * keeps upstream's numbering free.
 */
final class PipeTvSettingsMigrations {
    private static final String VERSION_KEY = "pipetv_settings_version";
    private static final int VERSION = 1;

    private PipeTvSettingsMigrations() {
    }

    static void run(@NonNull final Context context, final boolean isFirstRun) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final int version = prefs.getInt(VERSION_KEY, 0);
        if (version >= VERSION) {
            return;
        }
        final SharedPreferences.Editor editor = prefs.edit().putInt(VERSION_KEY, VERSION);
        // A first run already gets the current defaults, from the resources
        if (!isFirstRun && DeviceUtils.isTv(context)) {
            if (version < 1) {
                // 0.8.2: no shorts and no paid videos on TVs (values-television/bools.xml)
                editor.putBoolean(context.getString(R.string.filter_shorts_key), true)
                        .putBoolean(context.getString(R.string.filter_paid_contents_key), true);
            }
        }
        editor.apply();
    }
}
