package ru.meteoinfo;

//import android.content.pm.PackageManager;
//import android.os.SystemClock;
//import android.os.AsyncTask;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.RemoteViews;
import android.location.Location;
import android.app.PendingIntent;

import ru.meteoinfo.Util.Station;
import ru.meteoinfo.Util.WeatherData;
import ru.meteoinfo.Util.WeatherInfo;
		
public class WidgetProvider extends AppWidgetProvider {

    public static final String WEATHER_CHANGED_BROADCAST  = "weather_changed";

    private static final String TAG = "ru.meteoinfo:Widget";

    // pending intents for widget clicks	
    private static PendingIntent pint_show_webpage = null;
    private static PendingIntent pint_widget_update = null;

    private static long last_sta_code = -1;

    private static Context ctx;

    private static AppWidgetManager gm;

    // handler for enqueuing update requests to avoid race conditions 	
    private final Handler hdl = new Handler();

/*  private void setWidgetInstalled(boolean installed) {
	SharedPreferences settings = 
	    PreferenceManager.getDefaultSharedPreferences(App.getContext());
	SharedPreferences.Editor editor = settings.edit();
	editor.putBoolean("widget_installed", installed);
	editor.commit();
    } */


    @Override
    public void onEnabled(Context context) {
	super.onEnabled(context);
        Log.d(TAG, "onEnabled");
	App.widget_visible = true;
	ctx = context;
/*
	PackageManager pm = context.getPackageManager();
	try {
	    pm.setComponentEnabledSetting(
		ComponentName.createRelative("ru.meteoinfo", ".WidgetProvider"),
		PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
		PackageManager.DONT_KILL_APP);
	} catch(Exception e) {
	    e.printStackTrace();
	}
*/
 	gm = AppWidgetManager.getInstance(ctx);
	Intent i = new Intent(context, Srv.class);
	i.setAction(Srv.WIDGET_STARTED);
	context.startService(i);
    }
 
    @Override
    public void onUpdate(Context context, AppWidgetManager man, int[] wids) {
	super.onUpdate(context, man, wids);
        Log.d(TAG, "onUpdate");
        for(int i = 0; i < wids.length; i++) 
	    update_widget(context, man, wids[i], App.get_string(R.string.loc_unk_yet));
        Log.d(TAG, "onUpdate complete");
    }

    @Override
    public void onReceive(Context context, Intent intent) {	// just enqueue broadcasts
	if(intent == null) {
	    Log.e(TAG, "bogus intent in onReceive");
	    return;
	}
	String action = intent.getAction();
	if(action == null) {
	    Log.e(TAG, "bogus action in onReceive");
	    return;
	}
	if(action.equals(WEATHER_CHANGED_BROADCAST)) {
	    if(!App.widget_visible) {
		Log.e(TAG, "I'm dead, trying to recover");	
		onEnabled(context);
	    }
	    hdl.post(new Runnable() { 
		public void run() { 
		    weather_update(); 
		}
	    });	
	    Log.d(TAG, "weather change queued");
	} else {
	    super.onReceive(context, intent);
	    return;	
	}
    }

    @Override
    public void onDeleted(Context context, int[] wids) {
        Log.d(TAG, "onDeleted");
    } 

    @Override
    public void onDisabled(Context context) {
        Log.d(TAG, "onDisabled");
	App.widget_visible = false;
	Intent i = new Intent(context, Srv.class);
	i.setAction(Srv.WIDGET_STOPPED);
	context.startService(i);
	pint_show_webpage = null;
	pint_widget_update = null;
    }

    private static String windDir(String degrees) {
        try {
            double deg = Double.parseDouble(degrees);   
            String[] directions = App.getContext().getResources().getStringArray(R.array.short_wind_dirs);
            return directions[(int)Math.round(((deg % 360) / 45))];
        } catch (Exception e) {
            Log.e(TAG, "invalid degrees " + degrees);      
            return null;        
        }
    }

//    private void weather_update(Context context) {
    private void weather_update() {
	WeatherData wd = Srv.getLocalWeather();
	if(wd == null) {
	    Log.d(TAG, "weather_update: localWeather unknown");
	    return;
	}
 	if(wd.for3days == null || wd.for3days.size() == 0) {
	    Log.d(TAG, "weather_update: weather for 3 days unknown");
	    return;
	}
	WeatherInfo wi = wd.for3days.get(0);
	if(wi == null) {
	    Log.d(TAG, "weather_update: first weather info for 3 days unknown");
	    return;
	}
	String temp = wi.get_temperature();
	if(temp == null) {
	    Log.d(TAG, "weather_update: null temperature string");	
	    return;
	}
	if(!temp.startsWith("-")) temp = "+" + temp;
	Log.d(TAG, "weather_update: temp=" + temp);

//	int pt = temp.indexOf(".");
//	if(pt > 0) temp = temp.substring(0, pt);
//	temp += "°C";

	String press = wi.get_pressure();
	if(press != null) press = String.format(App.get_string(R.string.wd_pressure_short), press);
	else {
	    for(int i = 0; i < wd.for3days.size(); i++) {
		wi = wd.for3days.get(i);
		press = wi.get_pressure();
		if(press != null) {
		    press = String.format(App.get_string(R.string.wd_pressure_short), press);
		    break;
		}
	    }	
	}

	Log.d(TAG, "weather_update: pressure=" + press);

        String st1 = null, st2 = null, wind = null;
	st1 = wi.get_wind_dir();
	st2 = wi.get_wind_speed();
	if(st1 != null && st2 != null) {
	    st1 = windDir(st1);
	    wind = String.format(App.get_string(R.string.wd_wind_short), st1, st2);
        }
	Log.d(TAG, "weather_update: wind=" + wind);

	String p = wi.get_precip();
	if(p != null) {
	    st2 = App.get_string(R.string.wd_precip_short);
	    p = String.format(st2, p);	
	}
	Log.d(TAG, "weather_update: precip=" + p);

	String hum = wi.get_humidity();
	if(hum != null) hum = String.format(App.get_string(R.string.wd_humidity_short), hum);
	Log.d(TAG, "weather_update: humidity=" + hum);

	Station st = Srv.getCurrentStation();
	String addr = null;
	if(st != null) addr = st.shortname;

/*
	String s, data;
	for(int i = 0; i < Util.localWeather.for3days.size(); i++) {
	    wi = Util.localWeather.for3days.get(i);
	    s = wi.get_pressure();
	    if(s != null) {
		data = String.format(App.get_string(R.string.wd_pressure), s);
	    } 	  	
	} */

	int [] bound_widgets = gm.getAppWidgetIds(
			    new ComponentName(ctx, "ru.meteoinfo.WidgetProvider"));
	for(int i = 0; i < bound_widgets.length; i++)
	    update_widget(ctx, gm, bound_widgets[i], addr, temp, press, wind, p, hum);
    }

    private void update_widget(Context context, AppWidgetManager man, int wid, 
		String addr, String... data) {

	RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);

	if(addr != null) views.setTextViewText(R.id.w_addr, addr);
	if(data != null) {
	    if(data.length > 0) views.setTextViewText(R.id.w_temp, data[0]);
	    if(data.length > 1) views.setTextViewText(R.id.w_pressure, data[1]);
	    if(data.length > 2) views.setTextViewText(R.id.w_wind, data[2]);
	    if(data.length > 3) views.setTextViewText(R.id.w_precip, data[3]);
	    if(data.length > 4) views.setTextViewText(R.id.w_humidity, data[4]);
	    	
	}

	// Pending intent to display a webpage when the right part of the widget is clicked
	Station st = Srv.getCurrentStation();
	if(st != null && st.code != last_sta_code) {
	    last_sta_code = st.code;
	    String url =  Util.URL_STA_DATA + "?p=" + st.code;	
	    Intent intent = new Intent(context, WebActivity.class);
	    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	    intent.putExtra("action", url);
	    intent.putExtra("show_ui", false);
	    pint_show_webpage = PendingIntent.getActivity(context, 0, 
		intent, PendingIntent.FLAG_UPDATE_CURRENT); 	
	/*  if(pint_show_webpage != null) {
		Log.d(TAG, "setting clicks for " + st.code);
		views.setOnClickPendingIntent(R.id.w_addr, pint_show_webpage);
		views.setOnClickPendingIntent(R.id.w_pressure, pint_show_webpage);
		views.setOnClickPendingIntent(R.id.w_wind, pint_show_webpage);
		views.setOnClickPendingIntent(R.id.w_precip, pint_show_webpage);
		views.setOnClickPendingIntent(R.id.w_humidity, pint_show_webpage);
		views.setOnClickPendingIntent(R.id.w_grid, pint_show_webpage);
	    } else Log.e(TAG, "pint_show_webpage is zero"); */
	}

	// Pending intent to update weather when the left part of the widget is clicked
    	if(pint_widget_update == null) {
//	    Intent intent = new Intent(context, Srv.class);
//	    intent.setAction(Srv.WEATHER_UPDATE);
//	    pint_widget_update = PendingIntent.getService(context, 0, intent, 0); 
	    Intent intent = new Intent(context, WeatherActivity.class);
	    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	    pint_widget_update = PendingIntent.getActivity(context,0,intent,0);	
	    // if(pint_widget_update != null) views.setOnClickPendingIntent(R.id.w_temp, pint_widget_update);
	    // else Log.e(TAG, "pint_widget_update is zero");
	}

	if(pint_widget_update != null) views.setOnClickPendingIntent(R.id.w_temp, pint_widget_update);	    		
	if(pint_show_webpage != null) {
	    views.setOnClickPendingIntent(R.id.w_addr, pint_show_webpage);
	    views.setOnClickPendingIntent(R.id.w_pressure, pint_show_webpage);
	    views.setOnClickPendingIntent(R.id.w_wind, pint_show_webpage);
	    views.setOnClickPendingIntent(R.id.w_precip, pint_show_webpage);
	    views.setOnClickPendingIntent(R.id.w_humidity, pint_show_webpage);
	    views.setOnClickPendingIntent(R.id.w_grid, pint_show_webpage);
	}

        man.updateAppWidget(wid, views);

    }
}


	
