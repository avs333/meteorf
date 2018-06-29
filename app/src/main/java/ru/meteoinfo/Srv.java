package ru.meteoinfo;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.location.Location;
import android.os.IBinder;
import android.os.SystemClock;
import android.os.Handler;
import android.os.Bundle;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
//import java.util.concurrent.locks.ReentrantLock;

import com.google.android.gms.location.*;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import ru.meteoinfo.Util.Station;
import ru.meteoinfo.Util.WeatherData;
import ru.meteoinfo.Util.WeatherInfo;

public class Srv extends Service {

    private static   Handler hdl = WeatherActivity.ui_update;

    // called by GoogleLocation
    public static final String LOCATION_UPDATE = "location_update";

    // called periodically & maybe by widget when temperature field is clicked
    public static final String WEATHER_UPDATE = "weather_update";

    // called by widget from its onEnabled/onDisabled
    public static final String WIDGET_STARTED = "widget_started";
    public static final String WIDGET_STOPPED = "widget_stopped";

    // called by main activity from its onCreate/onDestroy
    public static final String ACTIVITY_STARTED = "activity_started";
    public static final String ACTIVITY_STOPPED = "activity_stopped";

    // called after preferences update 
    public static final String UPDATE_REQUIRED = "update_required";
    public static boolean use_geonames;
    public static boolean use_gps;
    public static boolean use_interp;
    public static boolean wd_show_sta;
    public static String bg_colour;
    public static String fg_colour;

    public static final String WIDGET_RESTART_REQUIRED = "widget_restart_required";

    private static final int init_attempts = 4;
    private static final long init_attempts_delay = 30 * 1000;

    private static boolean widget_installed = false;    // true if at least one widget instance is installed
    private static boolean widget_updated = false;
	
    private static long loc_update_interval = 60 * 1000;
    private static long wth_update_interval = 1200 * 1000;
    private static final long init_loc_update_interval = 1000;
    private static final long init_wth_update_interval = 3000;
    private static int loc_priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY; // PRIORITY_HIGH_ACCURACY;

    // false if update intervals must be increased after first loc/wth update
    private boolean loc_fix = false;
    private boolean wth_fix = false;

    private static final long LOC_FASTEST_UPDATE_INTERVAL = 2 * 1000;
    private static final float LOC_MIN_DISPLACEMENT = 0.0f; // <- in metres. 0.0 debug only!! 500.0f; will be ok for release
	
    private static PendingIntent pint_location = null;
    private static PendingIntent pint_weather = null;
    private static final String TAG = "ru.meteoinfo:Srv";

    private FusedLocationProviderClient loc_client = null;

    private static final int COLOUR_ERR = WeatherActivity.COLOUR_ERR;
    private static final int COLOUR_INFO = WeatherActivity.COLOUR_INFO;
    private static final int COLOUR_GOOD = WeatherActivity.COLOUR_GOOD;
    private static final int COLOUR_DBG = WeatherActivity.COLOUR_DBG;
	
    private static Location currentLocation = null;	
    private static Station currentStation = null;
    private static Station lastStation = null;
    public static Station getCurrentStation() { return currentStation; };	

    private static WeatherData localWeather = null;
    public static WeatherData getLocalWeather() { return localWeather; }

    private static void log(int colour, String mesg) {
	// if(App.activity_visible) 
	WeatherActivity.logUI(colour, mesg);
	if(colour == COLOUR_ERR) Log.e(TAG, mesg);
	else Log.d(TAG, mesg);
    }

    private static void log(int colour, int res_id) {
	log(colour, App.get_string(res_id));
    }

    public static Location getCurrentLocation() {
	return currentLocation;
    }

    @Override
    public IBinder onBind (Intent intent) {
	return null;
    }

    /*  Three init stages:
	0: uninitialised
	1: station list known
	2: current location known */

    public static final int RES_ERR = 0;
    public static final int RES_LIST = 1;
    public static final int RES_LOC = 2;
    public static int cur_res_level;
	
    private void send_init_result(int res) {
	if(App.activity_visible) {
	    log(COLOUR_DBG, "Activity: stage " + res + " passed");	
	    Message msg = new Message();
	    Bundle b = new Bundle();
	    b.putInt("init_complete", res);
	    msg.setData(b);
	    WeatherActivity.ui_update.sendMessage(msg);		
	}
    }

    static Context context = App.getContext();
    private void notify_widgets(String action) {
	Intent intent1 = new Intent(context, WidgetSmall.class);
	intent1.setAction(action);
	Intent intent2 = new Intent(context, WidgetLarge.class);
	intent2.setAction(action);
	sendBroadcast(intent1);
	sendBroadcast(intent2);
    }

    private static final Object wt_obj = new Object();

    @Override
    public void onCreate() {
	super.onCreate();

	ComponentName wc = new ComponentName(this, "ru.meteoinfo.WidgetSmall");
	AppWidgetManager man = AppWidgetManager.getInstance(this);
	int ids[] = man.getAppWidgetIds(wc);
	widget_installed = (ids != null && ids.length > 0);
	if(!widget_installed) {
	    wc = new ComponentName(this, "ru.meteoinfo.WidgetLarge");
	    ids = man.getAppWidgetIds(wc);
	    widget_installed = (ids != null && ids.length > 0);
	}
	read_prefs();

	log(COLOUR_INFO, App.get_string(R.string.srv_created) + widget_installed);
	cur_res_level = RES_ERR;
	new Thread(new Runnable() {
	    @Override
	    public void run() {
		int i;
		synchronized(wt_obj) {
		    for(i = 0; i < init_attempts; i++) {
			if(Util.getStations()) break;
			try {
			    log(COLOUR_DBG, "attempt " + i + " failed, retrying after " + init_attempts_delay + "ms");
			    wt_obj.wait(init_attempts_delay);
			} catch(Exception e) { Log.e(TAG, "exception in wait()"); }	
		    }
		}
		if(i == init_attempts) {
		    log(COLOUR_ERR, R.string.conn_bad);
		    send_init_result(RES_ERR);
		    return;
		}
		log(COLOUR_GOOD, R.string.srv_sta_list_recv);
		restart_updates();
	    }
	}).start();
	App.service_started = true;
    }

    // fullStationList must be known here
    // Set up location updates (and weather updates if widget is installed)

    private static final Object lock_restart = new Object();
    private static final Object lock_loc_update = new Object();

		
    private void restart_updates() {

	synchronized(lock_restart) {

	    if(!Util.stationListKnown()) {
		send_init_result(RES_ERR);
		Log.d(TAG, "internal error: station list unknown on entry to restart_updates()");
		return;
	    }
	    log(COLOUR_DBG, "restarting updates");

	    if(cur_res_level < RES_LIST) {
		send_init_result(RES_LIST);
		cur_res_level = RES_LIST;	// Stage 1
	    }
	    // Start location updates
	
	    log(COLOUR_DBG, "starting updates, priority=" + loc_priority + ", updates: location=" 
		+ loc_update_interval + ", weather=" + wth_update_interval);

	    if(loc_client == null || App.activity_visible) {
		loc_client = LocationServices.getFusedLocationProviderClient(this);
		log(COLOUR_DBG, "trying getLastLocation()");
		loc_client.getLastLocation().addOnCompleteListener(new OnCompleteListener<Location>() {
		    @Override
		    public void onComplete(Task<Location> task) {
			if(!task.isSuccessful() || task.getResult() == null) {
			    log(COLOUR_ERR, "getLastLocation() failed");
			    return;
			}
			synchronized(lock_loc_update) {
			    currentLocation = task.getResult();
			    if(currentLocation == null) return;
			    currentStation = Util.getNearestStation(currentLocation.getLatitude(), 
				currentLocation.getLongitude());
			    if(currentStation != lastStation) {
				lastStation = currentStation;
				log(COLOUR_INFO, App.get_string(R.string.sta_changed) + " " + currentStation.code);
			    }
			}
			if(cur_res_level < RES_LOC) {	// Stage 2
			    send_init_result(RES_LOC);
			    cur_res_level = RES_LOC;
			}
			log(COLOUR_DBG, "getLastLocation.onComplete: station=" + currentStation.code);
			updateLocalWeather(true);
		    }
		});
	    }
	    loc_fix = false;
	    wth_fix = false;
	    start_location_updates(true);
	    if(widget_installed) start_weather_updates(true);
	}
    }


    synchronized void start_location_updates(boolean init) {
	long interval = init ? init_loc_update_interval : loc_update_interval;
	Intent intent = new Intent(this, Srv.class);
	intent.setAction(LOCATION_UPDATE);
	pint_location = PendingIntent.getService(this, 0, intent, 0);	
	LocationRequest loc_req = new LocationRequest();
	loc_req.setPriority(loc_priority);
	loc_req.setInterval(interval);
//	loc_req.setFastestInterval(LOC_FASTEST_UPDATE_INTERVAL);
	loc_req.setFastestInterval(interval/4);	// Samsung Galaxy S9
	loc_req.setSmallestDisplacement(LOC_MIN_DISPLACEMENT);
	if(loc_client == null) loc_client = LocationServices.getFusedLocationProviderClient(this);
	loc_client.requestLocationUpdates(loc_req, pint_location);
	log(COLOUR_DBG, "location updates restarted at " + interval/1000 + " sec intervals");
    }

    synchronized void start_weather_updates(boolean init) {
	long interval = init ? init_wth_update_interval : wth_update_interval;
	Intent intent  = new Intent(this, Srv.class);
	intent.setAction(WEATHER_UPDATE);
	pint_weather = PendingIntent.getService(this, 0, intent, 0);	
	AlarmManager amgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
	amgr.setInexactRepeating(AlarmManager.ELAPSED_REALTIME,
	    SystemClock.elapsedRealtime(), interval, pint_weather);
	log(COLOUR_DBG, "weather updates restarted at " + interval/1000 + " sec intervals");
    }

    @Override
    public void onDestroy() {
	log(COLOUR_DBG, "destroying service");
	App.service_started = false;
	if(pint_location != null) 
	    LocationServices.getFusedLocationProviderClient(this).removeLocationUpdates(pint_location);
	if(pint_weather != null) {
	    AlarmManager amgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
	    amgr.cancel(pint_weather);
	}
    }

    long last_loc_update_time = 0, last_wth_update_time = 0, cur_wtime, cur_ltime;

//  If it were IntentService:

//  @Override
//  void onHandleIntent (Intent intent) {

    @Override
    public int onStartCommand(Intent intent, int flags, int id) {

	if(intent == null) return START_STICKY;
	String action = intent.getAction();

	if(action == null) return START_STICKY;

	switch(action) {

	    case LOCATION_UPDATE:	

		cur_ltime = System.currentTimeMillis();

		Location loc = null;
		LocationResult result = LocationResult.extractResult(intent);

		if(result != null) loc = result.getLastLocation();
		if(loc == null) {
		    log(COLOUR_DBG, "null location received");
		    break;
		}


		boolean station_changed = false;
		
		synchronized(lock_loc_update) {
		    long tdiff = cur_ltime - last_loc_update_time;	
		    long delta = loc_fix ? loc_update_interval/4 : init_loc_update_interval/4;
		    if(tdiff < delta && currentLocation != null) {
			log(COLOUR_DBG, "too fast location update request after " + tdiff + "ms, ignored");
			break;
		    }
		    currentLocation = loc;
		    currentStation = Util.getNearestStation(loc.getLatitude(), loc.getLongitude());
		    last_loc_update_time = cur_ltime;
		    if(currentStation != lastStation) {
			lastStation = currentStation;
		        station_changed = true;
			log(COLOUR_INFO, App.get_string(R.string.sta_changed) + " " + currentStation.code);
		    }
		}

		if(!loc_fix) {
		    start_location_updates(false);
		    loc_fix = true;
		}

		if(App.activity_visible && cur_res_level != RES_LOC) {	// Stage 2
                    send_init_result(RES_LOC);
                    cur_res_level = RES_LOC;
		}
		if(localWeather == null || !widget_updated || station_changed) updateLocalWeather(true);

	//	log(COLOUR_INFO, R.string.srv_loc_update);
		log(COLOUR_DBG, R.string.srv_loc_update);

		break;

	    case WEATHER_UPDATE:
		updateLocalWeather(false);
	//	log(COLOUR_INFO, R.string.srv_weather_update);
		log(COLOUR_DBG, R.string.srv_weather_update);
		if(!wth_fix) {
		    start_weather_updates(false);
		    wth_fix = true;
		}
		break;

	    case WIDGET_STARTED:
		log(COLOUR_DBG, "widget started");
		widget_installed = true;
		widget_updated = false;
		wth_fix = false;
		start_weather_updates(true);
		break;		

	    case WIDGET_STOPPED:
		// stop weather updates
		if(pint_weather != null) {
		    AlarmManager amgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		    amgr.cancel(pint_weather);
		    pint_weather = null;
		    log(COLOUR_DBG, "weather updates stopped");	
		}
		break;

	    case ACTIVITY_STARTED: 	
		send_init_result(cur_res_level);
		break;

	    case ACTIVITY_STOPPED: 	
		Log.d(TAG, "activity stopped");
//		if(!widget_installed && !App.activity_visible) stopSelf();	// won't, let it be
		break;

	    case UPDATE_REQUIRED:	// settings changed
		log(COLOUR_DBG, "restarting updates with new settings");
		if(intent == null) return START_STICKY;
		int res = intent.getIntExtra("res", 0);
		read_prefs();
		if((res & SettingsActivity.PCHG_SRV_MASK) != 0) restart_updates();
		if((res & SettingsActivity.PCHG_WID_MASK) != 0) notify_widgets(WidgetProvider.SETTINGS_CHANGED_BROADCAST);
		break;

	    case WIDGET_RESTART_REQUIRED:
		notify_widgets(WidgetProvider.ACTION_RESTART);
		break;

	    default:
		Log.e(TAG, "unknown action");
		break;
	}
	return START_STICKY;
    }

    Object w_update_lock = new Object();	

    private void updateLocalWeather(boolean forced) {
	if(currentStation == null) {
	    log(COLOUR_ERR, "updateLocalWeather: local station unknown yet");
	    return;	
	}
	final boolean force_update = forced;
	new Thread(new Runnable() {
	    @Override
	    public void run() {
		synchronized(w_update_lock) {
		    cur_wtime = System.currentTimeMillis();
		    long tdiff = cur_wtime - last_wth_update_time;	
		    long delta = wth_fix ? wth_update_interval/4 : init_wth_update_interval/4;
		    if(tdiff < delta && localWeather != null && !force_update) {
			log(COLOUR_DBG, "too fast weather update request after " + tdiff + "ms, ignored");
			return;
		    }
	    	    last_wth_update_time = cur_wtime;
		    log(COLOUR_DBG, "updating local weather for station " + currentStation.code);
		    localWeather = Util.getWeather(currentStation);
		    if(widget_installed && localWeather != null) {
			notify_widgets(WidgetProvider.WEATHER_CHANGED_BROADCAST);
			widget_updated = true;
		    } else if(localWeather == null) Log.e(TAG, "getWeather returned null!");
		}
	    }
	}).start();
    }

    private static SharedPreferences settings = null;

    public static void read_prefs() {
	if(settings == null) settings = PreferenceManager.getDefaultSharedPreferences(App.getContext());
	use_gps = settings.getBoolean("use_gps", SettingsActivity.DFL_USE_GPS);
	use_geonames = settings.getBoolean("use_geonames", SettingsActivity.DFL_USE_GEONAMES);
        use_interp = settings.getBoolean("use_interp", SettingsActivity.DFL_USE_INTERP);

        fg_colour = settings.getString("wd_font_colour", SettingsActivity.DFL_WDT_FONT_COLOUR);
        bg_colour = settings.getString("wd_back_colour", SettingsActivity.DFL_WDT_BACK_COLOUR);
        wd_show_sta = settings.getBoolean("wd_show_sta", false);

	loc_priority = use_gps ? LocationRequest.PRIORITY_HIGH_ACCURACY : LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY;
	loc_update_interval = settings.getInt("loc_update_interval", SettingsActivity.DFL_LOC_UPDATE_INTERVAL) * 1000;
	wth_update_interval = settings.getInt("wth_update_interval", SettingsActivity.DFL_WTH_UPDATE_INTERVAL) * 1000;
    }

}


