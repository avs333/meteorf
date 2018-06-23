package ru.meteoinfo;

//import android.content.pm.PackageManager;
//import android.os.SystemClock;
//import android.os.AsyncTask;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
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
    public static final String COLOURS_CHANGED_BROADCAST  = "colours_changed";
    public static final String ACTION_LEFT_BROADCAST  = "action_left";
    public static final String ACTION_RIGHT_BROADCAST  = "action_right";
    public static final String ACTION_RESTART = "action_restart";

    private static final String TAG = "ru.meteoinfo:Widget";

    // pending intents for widget clicks	
    private static PendingIntent pint_show_webpage = null;
    private static PendingIntent pint_start_activity = null;
    private static PendingIntent pint_left = null;
    private static PendingIntent pint_right = null;

    private static long last_sta_code = -1;

    private static Context ctx = null;

    private static AppWidgetManager gm = null;
    private static RemoteViews views = null;

    // handler for enqueuing update requests to avoid race conditions 	
    private final Handler hdl = new Handler();
    private static boolean wd_show_sta = false;

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
	pint_left = null;
	pint_right = null;
	pint_start_activity = null;
	pint_show_webpage = null;
	gm = AppWidgetManager.getInstance(context);
	views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
	set_pints(context);
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
	colours_update(context);
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
	    // why do I get APPWIDGET_ENABLED in onReceive()? 
	    // it should fire up onEnabled() instead...
	    if(!action.equals(AppWidgetManager.ACTION_APPWIDGET_ENABLED)) {
		Log.e(TAG, action + ": I'm dead, trying to recover");	
		startup(context);
		colours_update(context);
	    }
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
//		Log.d(TAG, "weather change queued for " + action);
		break;
	    case ACTION_RESTART:
		restart(cc);
		break;	
	    case COLOURS_CHANGED_BROADCAST:
		hdl.post(new Runnable() { 
		    public void run() { 
			colours_update(cc); 
		    }
		});	
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


    private static SharedPreferences settings = null;

    private void colours_update(Context context) {	

	if(settings == null) settings = PreferenceManager.getDefaultSharedPreferences(context);
	int [] bound_widgets = gm.getAppWidgetIds(new ComponentName(context, "ru.meteoinfo.WidgetProvider"));
	String fg_colour = settings.getString("wd_font_colour", SettingsActivity.DFL_WDT_FONT_COLOUR);
	String bg_colour = settings.getString("wd_back_colour", SettingsActivity.DFL_WDT_BACK_COLOUR);
	wd_show_sta = settings.getBoolean("wd_show_sta", false);

	if(gm == null) gm = AppWidgetManager.getInstance(context);
	if(views == null) new RemoteViews(context.getPackageName(), R.layout.widget_layout);

	int fg, bg;
	try {
	    fg = (int) Long.parseLong(fg_colour, 16);
	} catch(Exception e) { 
	    fg = 0xffffffff;
	    Log.e(TAG, "fg: NumberFormatException in colours_update()" + fg);
	}

	try {
	    bg = (int) Long.parseLong(bg_colour, 16);
	} catch(Exception e) { 
	    bg = 0;
	    Log.e(TAG, "bg: NumberFormatException in colours_update()" + bg);
	}

	Log.d(TAG, String.format("colours_update: fg=%08x bg=%08x " + wd_show_sta, fg, bg));
	Station st = Srv.getCurrentStation();
	String addr = null;
	String va[] = null;
	if(st != null) {
	    if(wd_show_sta) addr = String.format(App.get_string(R.string.wd_sta_short), st.code);
	    else addr = st.shortname;
	}

	Log.d(TAG, "show_sta=" + wd_show_sta + ", addr=" + addr);
	views.setTextColor(R.id.w_temp, fg);
	views.setTextColor(R.id.w_addr, fg);
	views.setTextColor(R.id.w_pressure, fg);
	views.setTextColor(R.id.w_wind, fg);
	views.setTextColor(R.id.w_date, fg);
	views.setTextColor(R.id.w_humidity, fg);
	views.setTextColor(R.id.w_precip, fg);
	views.setInt(R.id.w_grid, "setBackgroundColor", bg);
	if(addr != null) views.setTextViewText(R.id.w_addr, addr);

	for(int i = 0; i < bound_widgets.length; i++) gm.updateAppWidget(bound_widgets[i], views);
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

    static private int widx = 0;
    private final int max_widx = 12;

    private void weather_update(String action, Context context) {

	if(gm == null) gm = AppWidgetManager.getInstance(context);
	if(views == null) new RemoteViews(context.getPackageName(), R.layout.widget_layout);

	WeatherData wd = Srv.getLocalWeather();

	if(wd == null) {
	    Log.e(TAG, "weather_update: localWeather unknown");
	    return;
	}
 	if(wd.for3days == null) {
	    Log.e(TAG, "weather_update: invalid weather for 3 days");
	    return;
	}

	Station st = Srv.getCurrentStation();
	String addr = null;
	if(st != null) {
	    if(wd_show_sta) addr = String.format(App.get_string(R.string.wd_sta_short), st.code);
	    else addr = st.shortname;
	} else Log.d(TAG, "weather_update: no current station yet");

	if(st != null && st.code != last_sta_code) {
	    Log.d(TAG, "pint to display webpage");	
	    last_sta_code = st.code;
	    String url =  Util.URL_STA_DATA + "?p=" + st.code;	
	    Intent intent = new Intent(context, WebActivity.class);
	    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	    intent.putExtra("action", url);
	    intent.putExtra("show_ui", false);
	    if(st.name_p != null) intent.putExtra("title", st.name_p);
	    pint_show_webpage = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT); 	
	    views.setOnClickPendingIntent(R.id.w_addr, pint_show_webpage);
	}

	long now = System.currentTimeMillis();

	WeatherInfo wi = null;

	while((wi = wd.for3days.get(0)) != null) {
	    if(now <= wi.get_utc()) break;
	    Log.d(TAG, "weather_update: removing stale data for " + wi.get_date());
	    wd.for3days.remove(0);
	    if(wd.for3days.size() == 0) break;
	    if(widx > 0) widx--;
	}

	int max = wd.for3days.size() - 1;
	if(max < 0) {
	    Log.e(TAG, "weather_update: no weather to display");
	    return;
	}
	if(max > max_widx) max = max_widx;

	Log.d(TAG, "weather_update: " + action + " addr=" + addr + ", widx=" + widx + ", max=" + max);

	switch(action) {
	    case WEATHER_CHANGED_BROADCAST:
		widx = 0;
		wi = wd.for3days.get(widx);
		break; 
	    case ACTION_LEFT_BROADCAST:	
		widx--;
		if(widx < -1 || (widx < 0 && wd.observ == null)) widx = max;
		wi = (widx < 0) ? wd.observ : wd.for3days.get(widx);
		break;
	    case ACTION_RIGHT_BROADCAST:	
		widx++;
		if(widx > max) widx = (wd.observ != null) ? -1 : 0;
		wi = (widx < 0) ? wd.observ : wd.for3days.get(widx);
		break;
	    default:
		wi = null;
		break;
	}

	if(wi == null) {
	    Log.e(TAG, "null WeatherInfo at index " + widx);
	    return;
	}

	String date = wi.get_date();

	if(date != null) {
	    if(date.length() < 5) date = null;
	    else {
		date = date.substring(0,5);
		if(widx >= 0) date = "[" + date + "]";
		// context.getString(widx < 0 ? R.string.wd_observ : R.string.wd_forecast);
	    }	
	}
//	Log.d(TAG, "weather_update: date=" + date);

	String temp = null;
	double val = wi.get_temperature();

	if(val == Util.inval_temp) {
	    Log.e(TAG, "weather_update: invalid temperature");	
	    return;
	}

	temp = String.format(java.util.Locale.US, "+%.1f", val);
//	Log.d(TAG, "weather_update: temp=" + temp);

	String press = null;
	val = wi.get_pressure();

	if(val != -1) {	 
	    try {
		press = String.format(App.get_string(R.string.wd_pressure_short), (int) Math.round(val));
	    } catch(Exception e) {
		Log.e(TAG, "error parsing pressure " + val);
		press = null;
	    }
	}
	if(press != null) press += " " + App.get_string(R.string.wd_pressure_units);

//	Log.d(TAG, "weather_update: pressure=" + press);

	String wind = null;

	double wind_dir = wi.get_wind_dir();
	double wind_speed = wi.get_wind_speed();

	if(wind_dir != -1 && wind_speed != -1) 
	    wind = String.format(App.get_string(R.string.wd_wind_short), windDir(wind_dir), wind_speed);

//	Log.d(TAG, "weather_update: wind=" + wind);

	String precip = null;
	val = wi.get_precip();

	if(precip == null && widx == -1) {
	    val = wi.get_precip3h();
	    if(val == -1) val = wi.get_precip6h(); 
	    if(val == -1) val = wi.get_precip12h();
	}

	if(val != -1) precip = String.format(App.get_string(R.string.wd_precip_short), val);

//	Log.d(TAG, "weather_update: precip=" + precip);

	final String filler = "_______________";

	String hum = null;
	val = wi.get_humidity();
	if(val != -1) hum = String.format(App.get_string(R.string.wd_humidity_short), val);

//	Log.d(TAG, "weather_update: humidity=" + hum);

	/* pressure is often missing in observ data; move humidity string to its position then */
	if(press == null && hum != null) {
	    press = hum;
	    hum = filler;	
	}

	if(wind == null) wind = filler;
	if(precip == null) precip = filler;
	if(hum == null) hum = filler;

	if(addr != null) views.setTextViewText(R.id.w_addr, addr);
	if(date != null) views.setTextViewText(R.id.w_date, date);
	if(temp != null) views.setTextViewText(R.id.w_temp, temp);
	if(press != null) views.setTextViewText(R.id.w_pressure, press);
	if(wind != null) views.setTextViewText(R.id.w_wind, wind);
	if(precip != null) views.setTextViewText(R.id.w_precip, precip);
	if(hum != null) views.setTextViewText(R.id.w_humidity, hum);


	int [] bound_widgets = gm.getAppWidgetIds(
			    new ComponentName(context, "ru.meteoinfo.WidgetProvider"));

	for(int i = 0; i < bound_widgets.length; i++) gm.updateAppWidget(bound_widgets[i], views);
    }

    private void restart(Context context) {
	Log.d(TAG, "restarting");
	startup(context);
	colours_update(context);
    }


    private void set_pints(Context context) {
	// Pending intent to display a webpage when the right part of the widget is clicked
	Station st = Srv.getCurrentStation();
	views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
	Intent intent;

	if(st != null && st.code != last_sta_code) {
	    Log.d(TAG, "pint to display webpage");	
	    last_sta_code = st.code;
	    String url =  Util.URL_STA_DATA + "?p=" + st.code;	
	    intent = new Intent(context, WebActivity.class);
	    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	    intent.putExtra("action", url);
	    intent.putExtra("show_ui", false);
	    pint_show_webpage = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT); 	
	    views.setOnClickPendingIntent(R.id.w_addr, pint_show_webpage);
	}

	// Pending intent to start main activity when the left part of the widget is clicked
	Log.d(TAG, "pint to start activity");	
	intent = new Intent(context, WeatherActivity.class);
	intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	pint_start_activity = PendingIntent.getActivity(context,0,intent,0);	
	views.setOnClickPendingIntent(R.id.w_temp, pint_start_activity);
	views.setOnClickPendingIntent(R.id.w_date, pint_start_activity);

	Log.d(TAG, "pint to scroll left");	
	intent = new Intent(context, WidgetProvider.class);
	intent.setAction(ACTION_LEFT_BROADCAST);	
	pint_left = PendingIntent.getBroadcast(context,0,intent,0);	
	views.setOnClickPendingIntent(R.id.w_pressure, pint_left);
	views.setOnClickPendingIntent(R.id.w_humidity, pint_left);

	Log.d(TAG, "pint to scroll right");	
	intent = new Intent(context, WidgetProvider.class);
	intent.setAction(ACTION_RIGHT_BROADCAST);	
	pint_right = PendingIntent.getBroadcast(context,0,intent,0);	
	views.setOnClickPendingIntent(R.id.w_wind, pint_right);
	views.setOnClickPendingIntent(R.id.w_precip, pint_right);
    }

}


	
