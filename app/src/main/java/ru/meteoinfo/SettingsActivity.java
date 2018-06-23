package ru.meteoinfo;

import android.os.Bundle;
import android.content.SharedPreferences;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceChangeListener;
import android.util.Log;
import android.widget.EditText;

public class SettingsActivity extends PreferenceActivity
    implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "ru.meteoinfo:Prefs";	
    private static SharedPreferences settings;

    public static final boolean DFL_USE_GPS = false;
    public static final int DFL_VERBOSE = 1; 	
    public static final int DFL_ADDR_SRC = 0; 	
    public static final int DFL_LOC_UPDATE_INTERVAL = 60; 	
    public static final int DFL_WTH_UPDATE_INTERVAL = 1200; 	
    public static final String DFL_WDT_FONT_COLOUR = "FFFFFFFF";
    public static final String DFL_WDT_BACK_COLOUR = "00000000";
    private int prefs_changed = 0;
    public static final int PCHG_SRV_MASK = 1;
    public static final int PCHG_WID_MASK = 2;

    @Override	
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
	
        PreferenceScreen root = createPreferenceHierarchy();
	setPreferenceScreen(root);
	root.getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }
    @Override
    public void onDestroy() {
	getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
	Log.d(TAG, "returned result=" + prefs_changed);
	super.onDestroy();
    }	

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
		    editor.apply();
		}
		return true;
    	    }
	});
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        Preference pref = findPreference(key);
 	if (pref instanceof ListPreference) {
	     ListPreference listPref = (ListPreference) pref;
             String s = ""+ listPref.getEntry();
	     Log.d(TAG, "listpref change: key=" + key + " val=" + s);	
	     prefs_changed |= PCHG_SRV_MASK;
        } else if (pref instanceof EditTextPreference) {
	     EditTextPreference editPref = (EditTextPreference) pref;
             Log.d(TAG, "editpref change: key=" + key + " val=" + editPref.getText());
	     if(key.equals("wd_font_colour") || key.equals("wd_back_colour")) prefs_changed |= PCHG_WID_MASK;
        } else {
	     Log.d(TAG, "otherpref change: key=" + key);	
	     if(key.equals("wd_show_sta")) prefs_changed |= PCHG_WID_MASK;	
	     else prefs_changed |= PCHG_SRV_MASK;
	}  
	setResult(prefs_changed);
    }	

    private PreferenceScreen createPreferenceHierarchy() {

        settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

        PreferenceScreen root = getPreferenceManager().createPreferenceScreen(this);
        root.setTitle(getString(R.string.app_name) + " " + getString(R.string.title_activity_settings));

        PreferenceCategory basePrefCat = new PreferenceCategory(this);
        basePrefCat.setTitle(R.string.pref_base_settings);
        root.addPreference(basePrefCat);

//        CheckBoxPreference use_russian = new CheckBoxPreference(this);
//        use_russian.setTitle(R.string.pref_use_russian);
//        use_russian.setKey("use_russian");
//        basePrefCat.addPreference(use_russian);

//        CheckBoxPreference use_offline_maps = new CheckBoxPreference(this);
//        use_offline_maps.setTitle(R.string.pref_use_offline_maps);
//        use_offline_maps.setKey("use_offline_maps");
//        basePrefCat.addPreference(use_offline_maps);

        CheckBoxPreference use_gps = new CheckBoxPreference(this);
        use_gps.setTitle(R.string.pref_use_gps);
        use_gps.setKey("use_gps");
        basePrefCat.addPreference(use_gps);

	final ListPreference verbose = new ListPreference(this);
	setupListPreference(verbose, R.string.pref_verbose, "verbose", 
	    R.array.verb_names, R.array.vals123, DFL_VERBOSE);
        basePrefCat.addPreference(verbose);

	final ListPreference addr_source = new ListPreference(this);
	setupListPreference(addr_source, R.string.pref_addr_source, "addr_source", 
	    R.array.addr_names, R.array.vals123, DFL_ADDR_SRC);
        basePrefCat.addPreference(addr_source);


	final ListPreference loc_updates = new ListPreference(this);
	setupListPreference(loc_updates, R.string.pref_loc_updates, "loc_update_interval", 
	    R.array.loc_update_names, R.array.loc_update_vals, DFL_LOC_UPDATE_INTERVAL);
        basePrefCat.addPreference(loc_updates);

	final ListPreference wth_updates = new ListPreference(this);
	setupListPreference(wth_updates, R.string.pref_wth_updates, "wth_update_interval", 
	    R.array.wth_update_names, R.array.wth_update_vals, DFL_WTH_UPDATE_INTERVAL);
        basePrefCat.addPreference(wth_updates);

        PreferenceCategory widgetPrefCat = new PreferenceCategory(this);
        widgetPrefCat.setTitle(R.string.pref_widget_settings);
        root.addPreference(widgetPrefCat);

	final EditTextPreference wd_font_colour = new EditTextPreference(this);
	wd_font_colour.setTitle(R.string.wd_font_colour);
	wd_font_colour.setKey("wd_font_colour");
	EditText et = wd_font_colour.getEditText();
	if(wd_font_colour.getText() == null) {
	    wd_font_colour.setText(DFL_WDT_FONT_COLOUR);
	    et.setText(DFL_WDT_FONT_COLOUR);
	}
        widgetPrefCat.addPreference(wd_font_colour);

	final EditTextPreference wd_back_colour = new EditTextPreference(this);
	wd_back_colour.setTitle(R.string.wd_back_colour);
	wd_back_colour.setKey("wd_back_colour");
	EditText et1 = wd_back_colour.getEditText();
	if(wd_back_colour.getText() == null) {
	    wd_back_colour.setText(DFL_WDT_BACK_COLOUR);
	    et1.setText(DFL_WDT_BACK_COLOUR);
	}
        widgetPrefCat.addPreference(wd_back_colour);

        CheckBoxPreference wd_show_sta = new CheckBoxPreference(this);
        wd_show_sta.setTitle(R.string.wd_show_sta);
        wd_show_sta.setKey("wd_show_sta");
        widgetPrefCat.addPreference(wd_show_sta);

        return root;
    }
}
