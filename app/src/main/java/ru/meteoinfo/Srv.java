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

    // called periodically & by widget when temperature field is clicked
    public static final String WEATHER_UPDATE = "weather_update";

    // called by widget from its onEnabled/onDisabled
    public static final String WIDGET_STARTED = "widget_started";
    public static final String WIDGET_STOPPED = "widget_stopped";

    // called by main activity from its onCreate/onDestroy
    public static final String ACTIVITY_STARTED = "activity_started";
    public static final String ACTIVITY_STOPPED = "activity_stopped";

    // called after preferences update 
    public static final String UPDATE_REQUIRED = "update_required";

    private static final int init_attempts = 4;
    private static final long init_attempts_delay = 30 * 1000;

    private static boolean widget_installed = false;    // true if at least one widget instance is installed
	
    private static long loc_update_interval = 60 * 1000;		/* 20 sec should be enough */
    private static long wth_update_interval = 120 * 1000;
    private static int loc_priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY; // PRIORITY_HIGH_ACCURACY;

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
    public static Station getCurrentStation() { return currentStation; };	

    private static WeatherData localWeather = null;
    public static WeatherData getLocalWeather() { return localWeather; }

    private static void log(int colour, String mesg) {
	if(App.activity_visible) WeatherActivity.logUI(colour, mesg);
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

    public static final int RES_ERR = 0;
    public static final int RES_LIST = 1;
    public static final int RES_LOC = 2;
    public static int cur_res_level = RES_ERR;
	
    private void send_init_result(int res) {
	if(App.activity_visible) {
	    Log.d(TAG, "Activity: stage " + res + " passed");	
	    Message msg = new Message();
	    Bundle b = new Bundle();
	    b.putInt("init_complete", res);
	    msg.setData(b);
	    WeatherActivity.ui_update.sendMessage(msg);		
	}
    }

    @Override
    public void onCreate() {
	super.onCreate();
	ComponentName wc = new ComponentName(this, "ru.meteoinfo.WidgetProvider");
	AppWidgetManager man = AppWidgetManager.getInstance(this);
	int ids[] = man.getAppWidgetIds(wc);
	widget_installed = (ids != null && ids.length > 0);
	log(COLOUR_INFO, "service created, widget_installed=" + widget_installed);
    }

    // startup -> getStations

    private static final Object lock_startup = new Object();
    private static final Object wt_obj = new Object();

    private void startup() {

	synchronized(lock_startup) {
	    if(!Util.stationListKnown()) {
		new Thread(new Runnable() {
		    @Override
		    public void run() {
			log(COLOUR_DBG, "obtaining station list");
			int i;
			synchronized(wt_obj) {
			    for(i = 0; i < init_attempts; i++) {
				if(Util.getStations()) break;
				try {
				    log(COLOUR_DBG, "retrying: attempt " + i);
				    wt_obj.wait(init_attempts_delay);
				} catch(Exception e) { Log.e(TAG, "exception in wait()"); }	
			    }
			}
			if(i == init_attempts) {
			    log(COLOUR_ERR, R.string.conn_bad);
			    send_init_result(RES_ERR);
			    return;
			}
			log(COLOUR_DBG, "station list obtained");
			restart_updates(false);
		    }
		}).start();
	    } else {
		log(COLOUR_INFO, "station list is known already");
		restart_updates(false);
	    }
	}
    }

    // fullStationList must be known here
    // Set up location updates (and weather updates if widget is installed)

    private static final Object lock_restart = new Object();
    private static final Object lock_loc_update = new Object();
		
    private void restart_updates(boolean forced) {

	synchronized(lock_restart) {

	    Log.d(TAG, "restarting updates");

	    if(cur_res_level < RES_LIST) {
		send_init_result(RES_LIST);
		cur_res_level = RES_LIST;
	    }

	    // Start location updates

	    read_prefs();
	
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
			    currentStation = Util.getNearestStation(currentLocation.getLatitude(), 
				currentLocation.getLongitude());
			}
			if(cur_res_level < RES_LOC) {
			    send_init_result(RES_LOC);
			    cur_res_level = RES_LOC;
			}
			Log.d(TAG, "getLastLocation.onComplete: station=" + currentStation.code);
			updateLocalWeather();
		    }
		});
	    }

	    if(pint_location == null || forced) {
		Intent intent = new Intent(this, Srv.class);
		intent.setAction(LOCATION_UPDATE);
		pint_location = PendingIntent.getService(this, 0, intent, 0);	

		LocationRequest loc_req = new LocationRequest();
		loc_req.setPriority(loc_priority);
		loc_req.setInterval(loc_update_interval);
//		loc_req.setFastestInterval(LOC_FASTEST_UPDATE_INTERVAL);
		loc_req.setFastestInterval(loc_update_interval/4);	// Samsung Galaxy S9
		loc_req.setSmallestDisplacement(LOC_MIN_DISPLACEMENT);
		loc_client.requestLocationUpdates(loc_req, pint_location);
		Log.d(TAG, "location updates started");
	    }

	    if(widget_installed && (pint_weather == null || forced)) {
		Intent intent  = new Intent(this, Srv.class);
		intent.setAction(WEATHER_UPDATE);
		pint_weather = PendingIntent.getService(this, 0, intent, 0);	
		AlarmManager amgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		amgr.setInexactRepeating(AlarmManager.ELAPSED_REALTIME,
		    SystemClock.elapsedRealtime(), wth_update_interval, pint_weather);
		Log.d(TAG, "weather updates started");
	    }
	}
    }

    @Override
    public void onDestroy() {
	Log.d(TAG, "destroying service");
	if(pint_location != null) 
	    LocationServices.getFusedLocationProviderClient(this).removeLocationUpdates(pint_location);
	if(pint_weather != null) {
	    AlarmManager amgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
	    amgr.cancel(pint_weather);
	}
    }

    long last_loc_update_time = 0, last_wth_update_time = 0, cur_time;

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

		log(COLOUR_DBG, "location update received");

		Location loc = null;
		LocationResult result = LocationResult.extractResult(intent);
		if(result != null) loc = result.getLastLocation();
		if(loc == null) {
		    Log.e(TAG, "null location");
		    break;
		}
		synchronized(lock_loc_update) {
		    currentLocation = loc;
		    currentStation = Util.getNearestStation(loc.getLatitude(), loc.getLongitude());
		}
		if(App.activity_visible && cur_res_level != RES_LOC) {
                    send_init_result(RES_LOC);
                    cur_res_level = RES_LOC;
		}
		if(localWeather == null) updateLocalWeather();
		break;

	    case WEATHER_UPDATE:
		Log.d(TAG, "weather update received");
		updateLocalWeather();
		break;

	    case WIDGET_STARTED:
		Log.d(TAG, "widget started");
		widget_installed = true;
		startup();
		break;		

	    case WIDGET_STOPPED:
		// stop weather updates
		if(pint_weather != null) {
		    AlarmManager amgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		    amgr.cancel(pint_weather);
		    pint_weather = null;
		    Log.d(TAG, "weather updates stopped");	
		}
		break;

	    case ACTIVITY_STARTED: 	
		cur_res_level = RES_ERR;
		startup();
		break;	

	    case ACTIVITY_STOPPED: 	
		Log.d(TAG, "activity stopped");
//		if(!widget_installed && !App.activity_visible) stopSelf();	// Won't: widget may've crashed
		break;

	    case UPDATE_REQUIRED:	// settings changed
		Log.d(TAG, "restarting updates with new settings");
		restart_updates(true);
		break;

	    default:
		Log.e(TAG, "unknown action");
		break;
	}
	return START_STICKY;
    }

    Object w_update_lock = new Object();	

    private void updateLocalWeather() {
	if(currentStation == null) {
	    Log.e(TAG, "updateLocalWeather: local station unknown yet");
	    return;	
	}
	final Intent intent = new Intent(this, WidgetProvider.class);
	intent.setAction(WidgetProvider.WEATHER_CHANGED_BROADCAST);
	new Thread(new Runnable() {
	    @Override
	    public void run() {
		synchronized(w_update_lock) {
		    cur_time = System.currentTimeMillis();
		    long tdiff = cur_time - last_wth_update_time;	
		    if(tdiff < wth_update_interval/4 && localWeather != null) {
			Log.d(TAG, "too fast weather update request after " + tdiff + "ms, ignored");
			return;
		    }
	    	    last_wth_update_time = cur_time;
		    Log.d(TAG, "updating local weather for " + currentStation.code);
		    localWeather = Util.getWeather(currentStation.code);
		    if(widget_installed && localWeather != null) sendBroadcast(intent);
		    else if(localWeather == null) Log.e(TAG, "getWeather returned null!");
		}
	    }
	}).start();
    }

    private static SharedPreferences settings = null;

    public static void read_prefs() {
	if(settings == null) settings = PreferenceManager.getDefaultSharedPreferences(App.getContext());
	boolean use_gps = settings.getBoolean("use_gps", false);
	loc_priority = use_gps ? LocationRequest.PRIORITY_HIGH_ACCURACY : LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY;
	loc_update_interval = settings.getInt("loc_update_interval", 60) * 1000;
	wth_update_interval = settings.getInt("wth_update_interval", 120) * 1000;
    }		

}






