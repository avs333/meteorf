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

import java.util.List;
import java.util.Date;

import ru.meteoinfo.Util.Station;
import ru.meteoinfo.Util.WeatherData;
import ru.meteoinfo.Util.WeatherInfo;
		
public class WidgetProvider extends AppWidgetProvider {

    public static final String WEATHER_CHANGED_BROADCAST  = "weather_changed";
    public static final String ACTION_LEFT_BROADCAST  = "action_left";
    public static final String ACTION_RIGHT_BROADCAST  = "action_right";

    private static final String TAG = "ru.meteoinfo:Widget";

    // pending intents for widget clicks	
    private static PendingIntent pint_show_webpage = null;
    private static PendingIntent pint_start_activity = null;
    private static PendingIntent pint_left = null;
    private static PendingIntent pint_right = null;

    private static long last_sta_code = -1;

    private static Context ctx = null;

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
	startup(context);
    }
    private void startup(Context context) {
	Log.d(TAG, "startup entry");
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
 	gm = AppWidgetManager.getInstance(context);
	Intent i = new Intent(context, Srv.class);
	i.setAction(Srv.WIDGET_STARTED);
	context.startService(i);
	Log.d(TAG, "startup complete");
    }	 
 
    @Override
    public void onUpdate(Context context, AppWidgetManager man, int[] wids) {
	super.onUpdate(context, man, wids);
        Log.d(TAG, "onUpdate");
	weather_update(WEATHER_CHANGED_BROADCAST, context);
        Log.d(TAG, "onUpdate complete");
    }

    @Override
    public void onReceive(Context context, Intent intent) {	// just enqueue broadcasts
	if(intent == null) {
	    Log.e(TAG, "null intent in onReceive");
	    return;
	}
	final String action = intent.getAction();
	if(action == null) {
	    Log.e(TAG, "null action in onReceive");
	    return;
	}
	if(!App.widget_visible) {
	    Log.e(TAG, "I'm dead, trying to recover");	
	    startup(context);
	}
	final Context cc = context;
	switch(action) {
	    case WEATHER_CHANGED_BROADCAST:
	    case ACTION_LEFT_BROADCAST:	
	    case ACTION_RIGHT_BROADCAST:	
		hdl.post(new Runnable() { 
		    public void run() { 
			weather_update(action, cc); 
		    }
		});	
		Log.d(TAG, "weather change queued for " + action);
		break;
	    default:		
		super.onReceive(context, intent);
		//  Log.e(TAG, "spurious action in onRecieve" + action);
		break;
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
	pint_start_activity = null;
    }

    private static String windDir(double deg) {
        try {
            String[] directions = App.getContext().getResources().getStringArray(R.array.short_wind_dirs);
            return directions[(int)Math.round(((deg % 360) / 45))];
        } catch (Exception e) {
            Log.e(TAG, "invalid degrees " + deg);      
            return null;        
        }
    }

/*
    private int findWeatherInfo(WeatherData wd) {
	try {
	    long now = System.currentTimeMillis();
	    List<WeatherInfo> wl = wd.for3days;
	    for(int i = 0; i < wl.size(); i++) {
		WeatherInfo wi = wl.get(i);
		if(wi == null) {
		    Log.e(TAG, "null WeatherInfo at index " + i);
		    return -1;
		}
		long time = wi.get_utc();
		if(now <= time) return i;
	    }
	} catch(Exception e) {
	    Log.e(TAG, "exception in findWeatherInfo");
	    e.printStackTrace();
	}		
	return -1;
    } 
*/

//    private void weather_update(Context context) {

    static private int widx = 0;
    private final int max_widx = 6;

    private void weather_update(String action, Context context) {

	WeatherData wd = Srv.getLocalWeather();
	if(wd == null) {
	    Log.d(TAG, "weather_update: localWeather unknown");
	    return;
	}
 	if(wd.for3days == null || wd.for3days.size() <= max_widx ||
	    (wd.observ == null &&
		((widx == 0 && action.equals(ACTION_LEFT_BROADCAST)) ||
		 (widx == max_widx && action.equals(ACTION_LEFT_BROADCAST))))) {
	    Log.d(TAG, "weather_update: invalid weather request");
	    return;
	}

	Station st = Srv.getCurrentStation();
	String addr = null;
	if(st != null) addr = st.shortname;

//	int k = findWeatherInfo(wd);
//	if(k < 0) return;
//	WeatherInfo wi = wd.for3days.get(k);

	WeatherInfo wi;

	switch(action) {
	    case WEATHER_CHANGED_BROADCAST:
		widx = 0;
		wi = wd.for3days.get(widx);
		Log.d(TAG, "" + action + ": " + widx);
		break; 
	    case ACTION_LEFT_BROADCAST:	
		widx--;
		if(widx < -1) widx = max_widx;
		Log.d(TAG, "" + action + ": " + widx);
		wi = (widx < 0) ? wd.observ : wd.for3days.get(widx);
		break;
	    case ACTION_RIGHT_BROADCAST:	
		widx++;
		if(widx > max_widx) widx = -1;
		Log.d(TAG, "" + action + ": " + widx);
		wi = (widx < 0) ? wd.observ : wd.for3days.get(widx);
		break;
	    default:
		wi = null;
		break;
	}

	if(wi == null) {
	    Log.d(TAG, "null WeatherInfo at index " + widx);
	    return;
	}

//	Log.d(TAG, "found WeatherInfo at index " + k);

	String date = wi.get_date();

	if(date != null) {
	    if(date.length() < 5) date = null;
	    else date = date.substring(0,5) + " " +
		context.getString(widx < 0 ? R.string.wd_observ : R.string.wd_forecast);
	}
	Log.d(TAG, "weather_update: date=" + date);

	String temp = null;
	double val = wi.get_temperature();

	if(val == Util.inval_temp) {
	    Log.d(TAG, "weather_update: fake temperature");	
	    return;
	}

	temp = String.format("+%.1f", val);
	Log.d(TAG, "weather_update: temp=" + temp);

//	int pt = temp.indexOf(".");
//	if(pt > 0) temp = temp.substring(0, pt);
//	temp += "°C";

	String press = null;
	val = wi.get_pressure();

	if(val != -1) {	 
	    try {
		press = String.format(App.get_string(R.string.wd_pressure_short), (int) Math.round(val));
	    } catch(Exception e) {
		Log.e(TAG, "error parsing pressure " + val);
	    }
	} /* else {
	    for(int i = k; i < wd.for3days.size(); i++) {
		wi = wd.for3days.get(i);
		val = wi.get_pressure();
		if(val != -1) {
		   try {
			press = String.format(App.get_string(R.string.wd_pressure_short), (int) Math.round(val));
		    } catch(Exception e) {
			Log.e(TAG, "error parsing pressure " + val);
		    }
		}
	    }	
	} */

	if(press != null) press += " " + App.get_string(R.string.wd_pressure_units);

	Log.d(TAG, "weather_update: pressure=" + press);

	String wind = null;

	double wind_dir = wi.get_wind_dir();
	double wind_speed = wi.get_wind_speed();

	if(wind_dir != -1 && wind_speed != -1) 
	    wind = String.format(App.get_string(R.string.wd_wind_short), windDir(wind_dir), wind_speed);
	Log.d(TAG, "weather_update: wind=" + wind);

	String precip = null;
	val = wi.get_precip();

	if(val != -1) precip = String.format(App.get_string(R.string.wd_precip_short), val);
	Log.d(TAG, "weather_update: precip=" + precip);

	String hum = null;
	val = wi.get_humidity();
	if(val != -1) hum = String.format(App.get_string(R.string.wd_humidity_short), val);
	Log.d(TAG, "weather_update: humidity=" + hum);

	if(press == null && hum != null) {
	    press = hum;
	    hum = null;	
	}

/*	int [] bound_widgets = gm.getAppWidgetIds(
			    new ComponentName(ctx, "ru.meteoinfo.WidgetProvider"));
	for(int i = 0; i < bound_widgets.length; i++)
	    update_widget(ctx, gm, bound_widgets[i], addr, temp, press, wind, precip, hum); */

	int [] bound_widgets = gm.getAppWidgetIds(
			    new ComponentName(context, "ru.meteoinfo.WidgetProvider"));

	for(int i = 0; i < bound_widgets.length; i++)
	    update_widget(context, gm, bound_widgets[i], addr, date, temp, press, wind, precip, hum);

    }

    private void update_widget(Context context, AppWidgetManager man, int wid, 
		String addr, String date, String... data) {

	RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);

	if(addr != null) views.setTextViewText(R.id.w_addr, addr);
	if(date != null) views.setTextViewText(R.id.w_date, date);
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
	    pint_show_webpage = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT); 	
	/*  if(pint_show_webpage != null) {
		Log.d(TAG, "setting clicks for " + st.code);
		views.setOnClickPendingIntent(R.id.w_addr, pint_show_webpage);
		views.setOnClickPendingIntent(R.id.w_pressure, pint_show_webpage);
		views.setOnClickPendingIntent(R.id.w_wind, pint_show_webpage);
		views.setOnClickPendingIntent(R.id.w_precip, pint_show_webpage);
		views.setOnClickPendingIntent(R.id.w_humidity, pint_show_webpage);
		views.setOnClickPendingIntent(R.id.w_grid, pint_show_webpage);
	    } else Log.e(TAG, "pint_show_webpage is zero"); */
	    if(pint_show_webpage != null) views.setOnClickPendingIntent(R.id.w_addr, pint_show_webpage);

	}

	// Pending intent to update weather when the left part of the widget is clicked
    	if(pint_start_activity == null) {
//	    Intent intent = new Intent(context, Srv.class);
//	    intent.setAction(Srv.WEATHER_UPDATE);
//	    pint_widget_update = PendingIntent.getService(context, 0, intent, 0); 
	    Intent intent = new Intent(context, WeatherActivity.class);
	    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	    pint_start_activity = PendingIntent.getActivity(context,0,intent,0);	
	    // if(pint_widget_update != null) views.setOnClickPendingIntent(R.id.w_temp, pint_widget_update);
	    // else Log.e(TAG, "pint_widget_update is zero");
	    if(pint_start_activity != null) views.setOnClickPendingIntent(R.id.w_temp, pint_start_activity);	    		
	}

	if(pint_left == null) {
	    Intent intent = new Intent(context, WidgetProvider.class);
	    intent.setAction(ACTION_LEFT_BROADCAST);	
	    pint_left = PendingIntent.getBroadcast(context,0,intent,0);	
	    views.setOnClickPendingIntent(R.id.w_pressure, pint_left);
	    views.setOnClickPendingIntent(R.id.w_humidity, pint_left);
	}
	if(pint_right == null) {
	    Intent intent = new Intent(context, WidgetProvider.class);
	    intent.setAction(ACTION_RIGHT_BROADCAST);	
	    pint_right = PendingIntent.getBroadcast(context,0,intent,0);	
	    views.setOnClickPendingIntent(R.id.w_wind, pint_right);
	    views.setOnClickPendingIntent(R.id.w_precip, pint_right);
	}

        man.updateAppWidget(wid, views);

    }
}


	
