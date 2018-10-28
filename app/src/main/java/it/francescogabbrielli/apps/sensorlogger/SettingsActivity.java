package it.francescogabbrielli.apps.sensorlogger;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.support.annotation.Nullable;
import android.app.LoaderManager;
import android.content.AsyncTaskLoader;
import android.content.Loader;
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

    private final static String TAG = SettingsActivity.class.getSimpleName();

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
            Log.d(TAG, "Preference "+preference.getKey()+" -> "+value+" ["+value.getClass()+"]");
            String stringValue = value.toString();
            Context ctx = preference.getContext();
            int descriptionId = ctx.getResources().getIdentifier(
                    preference.getKey() + "_description", "string", ctx.getPackageName());
            if (preference instanceof ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);
                if (index < 0) {
                    preference.setSummary(null);
                } else {
                    listPreference.setValueIndex(index);
                    CharSequence val = listPreference.getEntries()[index];
                    preference.setSummary(descriptionId != 0
                            ? ctx.getString(descriptionId, val)
                            : val
                    );
                }
            } else if(preference instanceof EditTextPreference &&
                    (((EditTextPreference) preference).getEditText().getInputType() & InputType.TYPE_TEXT_VARIATION_PASSWORD)!=0) {

                preference.setSummary(stringValue.replaceAll(".","*"));

            } else {
                // For all other preferences, set the summary to the value's
                // simple string representation (plus optional description).
                preference.setSummary(descriptionId!=0 ? ctx.getString(descriptionId, stringValue) : stringValue);
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


        String def = PreferenceManager.getDefaultSharedPreferences(preference.getContext()).getString(preference.getKey(), "");
//        Log.v(TAG, "Loading default setting " + preference.getKey() + "=" + def);

        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference, def != null ? def : "");

        // set defaults from "default.properties" file
//        if (def != null)
//            PreferenceManager.getDefaultSharedPreferences(preference.getContext())
//                    .edit().putString(preference.getKey(), def).apply();

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
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.pref_headers, target);
    }

    /**
     * This method stops fragment injection in malicious applications.
     * Make sure to deny any unknown fragments here.
     */
    protected boolean isValidFragment(String fragmentName) {
        return PreferenceFragment.class.getName().equals(fragmentName)
                || FilePreferenceFragment.class.getName().equals(fragmentName)
                || LoggingPreferenceFragment.class.getName().equals(fragmentName)
                || SensorsPreferenceFragment.class.getName().equals(fragmentName)
                || CapturePreferenceFragment.class.getName().equals(fragmentName)
                || FTPPreferenceFragment.class.getName().equals(fragmentName)
                || StreamingPreferenceFragment.class.getName().equals(fragmentName);
    }

    public static class LoggingPreferenceFragment extends AppCompatPreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_logging);
            bindPreferenceSummaryToValue(findPreference(Util.PREF_LOGGING_RATE));
        }
    }

    public static class FilePreferenceFragment extends AppCompatPreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_file);
            bindPreferenceSummaryToValue(findPreference(Util.PREF_FILE));
        }
    }

    public static class FTPPreferenceFragment extends AppCompatPreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_ftp);
            bindPreferenceSummaryToValue(findPreference(Util.PREF_FTP));
            bindPreferenceSummaryToValue(findPreference(Util.PREF_FTP_ADDRESS));
            bindPreferenceSummaryToValue(findPreference(Util.PREF_FTP_USER));
            bindPreferenceSummaryToValue(findPreference(Util.PREF_FTP_PW));
            bindPreferenceSummaryToValue(findPreference(Util.PREF_FTP_SKIP));
        }
    }

    public static class StreamingPreferenceFragment extends AppCompatPreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_streaming);
            bindPreferenceSummaryToValue(findPreference(Util.PREF_STREAMING));
            bindPreferenceSummaryToValue(findPreference(Util.PREF_STREAMING_PORT));
        }
    }

    public static class CapturePreferenceFragment extends AppCompatPreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_capture);
            bindPreferenceSummaryToValue(findPreference(Util.PREF_CAPTURE_IMGFORMAT));
       }
    }

    public static class SensorsPreferenceFragment extends AppCompatPreferenceFragment
                implements LoaderManager.LoaderCallbacks<Cursor> {

        private Context context;
        private PreferenceScreen screen;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            screen = getPreferenceManager().createPreferenceScreen(getActivity());
            screen.setOrderingAsAdded(true);
            setPreferenceScreen(screen);
        }

        @Override
        public void onAttach(Context context) {
            super.onAttach(context);
            this.context = context;
            getLoaderManager().initLoader(0, null, this);
        }

        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            return new SensorsLoader(context);
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            data.moveToFirst();
            while(!data.isAfterLast()) {
                CheckBoxPreference p = new CheckBoxPreference(context);
                p.setTitle(data.getString(0));
                p.setSummary(data.getString(1));
                p.setDefaultValue(false);
                p.setKey(data.getString(2));
                screen.addPreference(p);
                data.move(1);
            }
        }
    }

    private static class SensorsLoader extends AsyncTaskLoader<Cursor> {

        SensorsLoader(Context context) { super(context); }

        @Override
        protected void onStartLoading() {
            super.onStartLoading();
            forceLoad();
        }

        @Override
        public Cursor loadInBackground() {
            Log.i(TAG, "Loading sensors");
            MatrixCursor ret = new MatrixCursor(new String[]{"title", "summary", "key"});
            SensorManager sensorManager = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
            List<Sensor> sensors = Util.getSensors(sensorManager);
            for (Sensor s : sensors)
                ret.addRow(new Object[] {
                        Util.getSensorName(s),
                        s.getName() + "/" + s.getVendor(),
                        "pref_sensor_" + s.getType() });
            return ret;
        }
    }

}
