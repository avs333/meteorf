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

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setPreferenceScreen(createPreferenceHierarchy());
    }
    private static final String TAG = "ru.meteoinfo";	
    private static SharedPreferences settings;

    private void setupListPreference(ListPreference pref, int title_id,
		String pref_name, int names_id, int values_id, int defl) {
	String names[] = getResources().getStringArray(names_id);
	String values[] = getResources().getStringArray(values_id);
	if(names == null || values == null || names.length == 0 || values.length == 0) {
	    Log.e(TAG, "internal error: no names/values arrays found in resources");
	    return;	
	}
	pref.setTitle(title_id);
	pref.setEntries(names);
	pref.setEntryValues(values);
	int i, k = settings.getInt(pref_name, defl);
	String s = Integer.toString(k);
	for(i = 0; i < values.length; i++) if(s.equals(values[i])) break;
	if(i == values.length) i = 0;
	pref.setValueIndex(i);
	final String pname = pref_name;
	pref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
	    @Override
	    public boolean onPreferenceChange(Preference preference, Object newValue) {
		String newval = (String) newValue;
		if(newval != null) {
		    SharedPreferences.Editor editor = settings.edit();
		    editor.putInt(pname, Integer.parseInt(newval));  
		    editor.commit();
		}
		return true;
    	    }
	});
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
	setupListPreference(verbose, R.string.pref_verbose, "verbose", 
	    R.array.verb_names, R.array.vals123, 1);
        launchPrefCat.addPreference(verbose);

	ListPreference addr_source = new ListPreference(this);
	setupListPreference(addr_source, R.string.pref_addr_source, "addr_source", 
	    R.array.addr_names, R.array.vals123, 0);
        launchPrefCat.addPreference(addr_source);


	final ListPreference loc_updates = new ListPreference(this);
	setupListPreference(loc_updates, R.string.pref_loc_updates, "loc_update_interval", 
	    R.array.loc_update_names, R.array.loc_update_vals, 60);
        launchPrefCat.addPreference(loc_updates);

	final ListPreference wth_updates = new ListPreference(this);
	setupListPreference(wth_updates, R.string.pref_wth_updates, "wth_update_interval", 
	    R.array.wth_update_names, R.array.wth_update_vals, 1200);
        launchPrefCat.addPreference(wth_updates);

        return root;
    }
}
