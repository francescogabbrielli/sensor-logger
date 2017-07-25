package it.francescogabbrielli.apps.sensorlogger;


import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.util.Log;
import android.view.MenuItem;

import java.util.List;

/**
 * A {@link PreferenceActivity} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 * <p>
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 */
public class SettingsActivity extends AppCompatPreferenceActivity {

    public static class AppCompatPreferenceFragment extends PreferenceFragment {

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setHasOptionsMenu(true);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), SettingsActivity.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */
    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            Log.d(SettingsActivity.class.getSimpleName(), "Preference "+preference.getKey()+" -> "+value+" ["+value.getClass()+"]");
            String stringValue = value.toString();
            Context ctx = preference.getContext();
            if (preference instanceof ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);

                if (index < 0) {
                    preference.setSummary(null);
                } else {
                    CharSequence val = listPreference.getEntries()[index];
                    int descriptionId = ctx.getResources().getIdentifier(
                            preference.getKey() + "_description", "string", ctx.getPackageName());
                    // Set the summary to reflect the new value.
                    preference.setSummary(descriptionId != 0
                            ? ctx.getString(descriptionId, val)
                            : val);
                }

            } else if(preference instanceof EditTextPreference &&
                    (((EditTextPreference) preference).getEditText().getInputType() & InputType.TYPE_TEXT_VARIATION_PASSWORD)!=0) {

                preference.setSummary(stringValue.replaceAll(".","*"));

            } else {
                // For all other preferences, set the summary to the value's
                // simple string representation.
                preference.setSummary(stringValue);
            }
            return true;
        }
    };

    /**
     * Helper method to determine if the device has an extra-large screen. For
     * example, 10" tablets are extra-large.
     */
    private static boolean isXLargeTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    /**
     * Binds a preference's summary to its value. More specifically, when the
     * preference's value is changed, its summary (line of text below the
     * preference title) is updated to reflect the value. The summary is also
     * immediately updated upon calling this method. The exact display format is
     * dependent on the type of preference.
     *
     * @see #sBindPreferenceSummaryToValueListener
     */
    private static void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(), ""));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupActionBar();
    }

    /**
     * Set up the {@link android.app.ActionBar}, if the API is available.
     */
    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // Show the Up button in the action bar.
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onIsMultiPane() {
        return isXLargeTablet(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.pref_headers, target);
    }

    /**
     * This method stops fragment injection in malicious applications.
     * Make sure to deny any unknown fragments here.
     */
    protected boolean isValidFragment(String fragmentName) {
        return PreferenceFragment.class.getName().equals(fragmentName)
                || LoggingPreferenceFragment.class.getName().equals(fragmentName)
                || SensorsPreferenceFragment.class.getName().equals(fragmentName)
                || CapturePreferenceFragment.class.getName().equals(fragmentName)
                || FTPPreferenceFragment.class.getName().equals(fragmentName);
    }

    /**
     * This fragment shows general preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class LoggingPreferenceFragment extends AppCompatPreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_logging);
            bindPreferenceSummaryToValue(findPreference(Util.PREF_LOGGING_RATE));
            bindPreferenceSummaryToValue(findPreference(Util.PREF_LOGGING_UPDATE));
            bindPreferenceSummaryToValue(findPreference(Util.PREF_LOGGING_LENGTH));
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class FTPPreferenceFragment extends AppCompatPreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_ftp);
            bindPreferenceSummaryToValue(findPreference(Util.PREF_FTP_ADDRESS));
            bindPreferenceSummaryToValue(findPreference(Util.PREF_FTP_USER));
            bindPreferenceSummaryToValue(findPreference(Util.PREF_FTP_PW));
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class CapturePreferenceFragment extends AppCompatPreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_capture);
       }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class SensorsPreferenceFragment extends AppCompatPreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromSensors();
        }

        private void addPreferencesFromSensors() {
            PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(getActivity());
            screen.setOrderingAsAdded(true);

            List<Sensor> sensors = Util.getSensors((SensorManager) getActivity().getSystemService(SENSOR_SERVICE));
            for (Sensor s : sensors) {
                CheckBoxPreference p = new CheckBoxPreference(getActivity());
                p.setTitle(Util.getSensorName(s));
                p.setSummary(s.getName() + "/" + s.getVendor());
                p.setDefaultValue(false);
                p.setKey("pref_sensor_"+s.getType());
                screen.addPreference(p);
            }

            setPreferenceScreen(screen);
        }

    }

}
