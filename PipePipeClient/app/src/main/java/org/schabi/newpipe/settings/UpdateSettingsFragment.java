package org.schabi.newpipe.settings;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.Preference;

import org.schabi.newpipe.R;
import org.schabi.newpipe.update.PipeTvUpdateDialog;
import org.schabi.newpipe.update.PipeTvUpdates;

import io.reactivex.rxjava3.disposables.CompositeDisposable;

/** PipeTV: the updates screen drives PipeTV's own updater, not PipePipe's (NewVersionWorker). */
public class UpdateSettingsFragment extends BasePreferenceFragment {
    private static final String TAG = "UpdateSettingsFragment";
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final Preference.OnPreferenceChangeListener updatePreferenceChange
            = (preference, checkForUpdates) -> {
        defaultPreferences.edit()
                .putBoolean(getString(R.string.update_app_key), (boolean) checkForUpdates).apply();

        if ((boolean) checkForUpdates) {
            PipeTvUpdates.checkWhenDue(requireContext());
        }
        return true;
    };

    private final Preference.OnPreferenceClickListener manualUpdateClick
            = preference -> {
        Toast.makeText(getContext(), R.string.checking_updates_toast, Toast.LENGTH_SHORT).show();
        disposables.add(PipeTvUpdates.check(requireContext()).subscribe(newer -> {
            if (newer.isPresent()) {
                PipeTvUpdateDialog.show(requireActivity(), newer.get());
            } else {
                Toast.makeText(getContext(), R.string.pipetv_update_up_to_date,
                        Toast.LENGTH_SHORT).show();
            }
        }, error -> {
            Log.w(TAG, "Could not check for updates", error);
            Toast.makeText(getContext(), R.string.pipetv_update_check_failed,
                    Toast.LENGTH_SHORT).show();
        }));
        return true;
    };

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResourceRegistry();

        final Preference automatic = findPreference(getString(R.string.update_app_key));
        automatic.setTitle(R.string.pipetv_update_auto_title);
        automatic.setSummary(R.string.pipetv_update_auto_summary);
        automatic.setOnPreferenceChangeListener(updatePreferenceChange);
        findPreference(getString(R.string.manual_update_key))
                .setOnPreferenceClickListener(manualUpdateClick);
        // Every build of PipeTV is a release, with no pre-releases between them
        findPreference(getString(R.string.show_prerelease_key)).setVisible(false);
    }

    @Override
    public void onDestroy() {
        disposables.clear();
        super.onDestroy();
    }
}
