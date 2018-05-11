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

    private String[] vals = {"0", "1", "2"};
    private String[] entries_verb = {"Ошибки", "Ошибки и еще кое-что", "Все"};
    private String[] entries_addr = {"Нет", "Google", "OSM"};
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setPreferenceScreen(createPreferenceHierarchy());
    }
    private static String verb = null;
    private static String asrc = null;

    private static final String TAG = "ru.meteoinfo";	
    private static SharedPreferences settings;

    private String[] up_vals = null;
    private String[] up_entries = null;
    private int i2s (String s) {
	int k = settings.getInt(s, 60);
	switch(k) {
	    case 10: return 0;
	    case 60: return 1;
	    case 300: return 2;
	    case 1200: return 3;
	    case 3600: return 4;
	    default: return 0;
	}
    } 
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

        CheckBoxPreference use_offline_maps = new CheckBoxPreference(this);
        use_offline_maps.setTitle(R.string.pref_use_offline_maps);
        use_offline_maps.setKey("use_offline_maps");
        launchPrefCat.addPreference(use_offline_maps);

        CheckBoxPreference use_gps = new CheckBoxPreference(this);
        use_gps.setTitle(R.string.pref_use_gps);
        use_gps.setKey("use_gps");
        launchPrefCat.addPreference(use_gps);

	ListPreference verbose = new ListPreference(this);
	verbose.setTitle(R.string.pref_verbose);
	verbose.setEntryValues(vals);
	verbose.setEntries(entries_verb);
	verbose.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
	    @Override
	    public boolean onPreferenceChange(Preference preference, Object newValue) {
		verb = (String) newValue;
		if(verb != null) {
		    SharedPreferences.Editor editor = settings.edit();
		    editor.putInt("verbose", Integer.parseInt(verb));  
		    editor.commit();
		}
		Log.d(TAG, "preference for verbose changed to " + verb);
		return true;
    	    }
	});
	verbose.setValueIndex(settings.getInt("verbose", 1));
        launchPrefCat.addPreference(verbose);


	ListPreference addr_source = new ListPreference(this);
	addr_source.setTitle(R.string.pref_addr_source);
	addr_source.setEntryValues(vals);
	addr_source.setEntries(entries_addr);
	addr_source.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
	    @Override
	    public boolean onPreferenceChange(Preference preference, Object newValue) {
		asrc = (String) newValue;
		if(asrc != null) {
		    SharedPreferences.Editor editor = settings.edit();
		    editor.putInt("addr_source", Integer.parseInt(asrc));  
		    editor.commit();
		}
		Log.d(TAG, "preference for addr_source changed to " + asrc);
		return true;
    	    }
	});
	addr_source.setValueIndex(settings.getInt("addr_source", 0));
        launchPrefCat.addPreference(addr_source);



	up_vals = getResources().getStringArray(R.array.update_intervals);
	up_entries = getResources().getStringArray(R.array.update_interval_sec);

	final ListPreference loc_updates = new ListPreference(this);
	loc_updates.setTitle(R.string.pref_loc_updates);
	loc_updates.setEntryValues(up_entries);
	loc_updates.setEntries(up_vals);
	loc_updates.setValueIndex(i2s("loc_update_interval"));

	loc_updates.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
	    @Override
	    public boolean onPreferenceChange(Preference preference, Object newValue) {
		String lsrc = (String) newValue;
		if(lsrc != null) {
	//	    Log.d(TAG, "new value is " + newValue + ", index=" + loc_updates.findIndexOfValue(lsrc));  
		    SharedPreferences.Editor editor = settings.edit();
		    editor.putInt("loc_update_interval", Integer.parseInt(lsrc));  
		    editor.commit();
		}
		return true;
    	    }
	});
        launchPrefCat.addPreference(loc_updates);






	ListPreference wth_updates = new ListPreference(this);
	wth_updates.setTitle(R.string.pref_wth_updates);
	wth_updates.setEntryValues(up_entries);
	wth_updates.setEntries(up_vals);
	wth_updates.setValueIndex(i2s("wth_update_interval"));

	wth_updates.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
	    @Override
	    public boolean onPreferenceChange(Preference preference, Object newValue) {
		String wsrc = (String) newValue;
		if(wsrc != null) {
		    SharedPreferences.Editor editor = settings.edit();
		    editor.putInt("wth_update_interval", Integer.parseInt(wsrc));  
		    editor.commit();
		}
		return true;
    	    }
	});
        launchPrefCat.addPreference(wth_updates);

        return root;
    }
}
