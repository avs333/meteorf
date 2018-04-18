package ru.meteoinfo;

import android.os.Bundle;
import android.content.SharedPreferences;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceChangeListener;
import android.util.Log;

public class SettingsActivity extends PreferenceActivity {

    private String[] verbs = {"0", "1", "2"};
    private String[] entries = {"Err", "Err+Info", "All"};
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setPreferenceScreen(createPreferenceHierarchy());
    }
    private static String verb = null;
    private static final String TAG = "ru.meteoinfo";	
    private static SharedPreferences settings;

    private PreferenceScreen createPreferenceHierarchy() {

        settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

        PreferenceScreen root = getPreferenceManager().createPreferenceScreen(this);
        root.setTitle(getString(R.string.app_name) + " " + getString(R.string.title_activity_settings));

        PreferenceCategory launchPrefCat = new PreferenceCategory(this);
        launchPrefCat.setTitle(R.string.pref_base_settings);
        root.addPreference(launchPrefCat);

        CheckBoxPreference use_russian = new CheckBoxPreference(this);
        use_russian.setTitle(R.string.pref_use_russian);
        use_russian.setKey("use_russian");
        launchPrefCat.addPreference(use_russian);

        CheckBoxPreference use_google_info = new CheckBoxPreference(this);
        use_google_info.setTitle(R.string.pref_use_google_info);
        use_google_info.setKey("use_google_info");
        launchPrefCat.addPreference(use_google_info);

        CheckBoxPreference use_offline_maps = new CheckBoxPreference(this);
        use_offline_maps.setTitle(R.string.pref_use_offline_maps);
        use_offline_maps.setKey("use_offline_maps");
        launchPrefCat.addPreference(use_offline_maps);

	ListPreference verbose = new ListPreference(this);
	verbose.setTitle(R.string.pref_verbose);
	verbose.setEntryValues(verbs);
	verbose.setEntries(entries);
	verbose.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
	    @Override
	    public boolean onPreferenceChange(Preference preference, Object newValue) {
		verb = (String) newValue;
		if(verb != null) {
		    SharedPreferences.Editor editor = settings.edit();
		    editor.putInt("verbose", Integer.parseInt(verb));  
		    editor.commit();
		}
		Log.d(TAG, "preference changed to " + verb);
		return true;
    	    }
	});
	verbose.setValueIndex(settings.getInt("verbose", 1));
        launchPrefCat.addPreference(verbose);

        return root;
    }
}
