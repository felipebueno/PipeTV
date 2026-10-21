package org.schabi.newpipe.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.update.PipeTvUpdates;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.ThemeHelper;

import java.util.Objects;

public abstract class BasePreferenceFragment extends PreferenceFragmentCompat {
    protected final String TAG = getClass().getSimpleName() + "@" + Integer.toHexString(hashCode());
    protected static final boolean DEBUG = MainActivity.DEBUG;

    SharedPreferences defaultPreferences;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        defaultPreferences = PreferenceManager.getDefaultSharedPreferences(requireActivity());
        super.onCreate(savedInstanceState);
    }

    protected void addPreferencesFromResourceRegistry() {
        addPreferencesFromResource(
                SettingsResourceRegistry.getInstance().getPreferencesResId(this.getClass()));
    }

    @Override
    public void onViewCreated(@NonNull final View rootView,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);
        setDivider(null);
        hideWhatPipeTvDoesNotUse();
        ThemeHelper.setTitleToAppCompatActivity(getActivity(), getPreferenceScreen().getTitle());
        if (DeviceUtils.isTv(requireContext())) {
            focusListOnTv();
        }
    }

    /** PipeTV: the item that was opened, focused again on the way back to this screen. */
    private int openedPosition = 0;

    @Override
    public boolean onPreferenceTreeClick(@NonNull final Preference preference) {
        final View focused = getListView().getFocusedChild();
        if (focused != null) {
            openedPosition = Math.max(0, getListView().getChildAdapterPosition(focused));
        }
        return super.onPreferenceTreeClick(preference);
    }

    /**
     * PipeTV: on TVs a screen starts on its first setting, or on the one it opened when coming
     * back, as in the TV's own settings, instead of on the toolbar's back arrow.
     */
    private void focusListOnTv() {
        final RecyclerView list = getListView();
        list.scrollToPosition(openedPosition);
        list.post(() -> {
            final RecyclerView.ViewHolder holder =
                    list.findViewHolderForAdapterPosition(openedPosition);
            if (holder == null || !holder.itemView.requestFocus()) {
                list.requestFocus();
            }
        });
    }

    /**
     * PipeTV: settings with nothing to act on in PipeTV's TV interface: touch gestures, screen
     * rotation, the details page and its tablet layout, the popup player (Android TV doesn't let
     * apps draw over others), the tab bar the side rail replaced, phone-only features, and a
     * fullscreen start that is always on.
     */
    private static final int[] TV_UNUSED_KEYS = {
            R.string.start_main_player_fullscreen_key,
            R.string.tablet_mode_key,
            R.string.show_dislike_key,
            R.string.use_experimental_new_ui_key,
            R.string.disable_android_auto_key,
            R.string.limit_mobile_data_usage_key,
            R.string.default_popup_resolution_key,
            R.string.pin_video_to_top_key,
            R.string.rotate_fullscreen_to_video_orientation_key,
            R.string.rotation_controls_fullscreen_key,
            R.string.volume_gesture_control_key,
            R.string.brightness_gesture_control_key,
            R.string.fullscreen_gesture_control_key,
            R.string.minimize_gesture_control_key,
            R.string.playback_speed_gesture_control_key,
            R.string.swipe_seek_gesture_control_key,
            R.string.pinch_to_zoom_gesture_control_key,
            R.string.lock_screen_button_key,
            R.string.speeding_playback_key,
            R.string.pull_to_refresh_key,
            R.string.local_playlist_swipe_action_key,
            R.string.player_notification_screen_key,
            R.string.grid_columns_key,
            R.string.video_tabs_key,
            R.string.main_tabs_position_key,
    };

    private void hideWhatPipeTvDoesNotUse() {
        // The updates screen drives PipeTV's updater, which only release builds have
        if (!PipeTvUpdates.isSupported()) {
            hidePreference(R.string.update_pref_screen_key);
        }
        if (!DeviceUtils.isTv(requireContext())) {
            return;
        }
        for (final int key : TV_UNUSED_KEYS) {
            hidePreference(key);
        }
        // What is left of the gestures on a TV is the remote's seek step
        if (this instanceof GestureSettingsFragment) {
            getPreferenceScreen().setTitle(R.string.pipetv_remote_control);
        }
        final PreferenceScreen screen = getPreferenceScreen();
        for (int i = 0; i < screen.getPreferenceCount(); i++) {
            final Preference preference = screen.getPreference(i);
            if (GestureSettingsFragment.class.getName().equals(preference.getFragment())) {
                preference.setTitle(R.string.pipetv_remote_control);
            }
        }
    }

    private void hidePreference(@StringRes final int key) {
        final Preference preference = findPreference(getString(key));
        if (preference != null) {
            preference.setVisible(false);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ThemeHelper.setTitleToAppCompatActivity(getActivity(), getPreferenceScreen().getTitle());
    }

    @NonNull
    public final Preference requirePreference(@StringRes final int resId) {
        final Preference preference = findPreference(getString(resId));
        Objects.requireNonNull(preference);
        return preference;
    }
}
