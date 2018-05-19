package ru.meteoinfo;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.location.Location;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.os.IBinder;
import android.os.SystemClock;
import android.os.Handler;
import android.os.Bundle;
import android.os.Message;
import android.preference.PreferenceManager;

import com.google.android.gms.location.*;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import ru.meteoinfo.Util.Station;
import ru.meteoinfo.Util.WeatherData;
import ru.meteoinfo.Util.WeatherInfo;

public class Srv extends Service {

    private static   Handler hdl = WeatherActivity.ui_update;

    public static final String LOCATION_UPDATE = "location_update";
    // called periodically
    public static final String WEATHER_UPDATE = "weather_update";
    // called by widget from its onEnabled/onDisabled
    public static final String WIDGET_STARTED = "widget_started";
    public static final String WIDGET_STOPPED = "widget_stopped";
    // called by activity from its onCreate/onDestroy
    public static final String ACTIVITY_STARTED = "activity_started";
    public static final String ACTIVITY_STOPPED = "activity_stopped";

    public static final String UPDATE_REQUIRED = "update_required";

    private static long loc_update_interval = 20 * 1000;		/* 20 sec should be enough */
    private static long wth_update_interval = 60 * 1000;
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

    private void send_init_result(boolean res) {
	if(App.activity_visible) {
	    Log.e(TAG, "Activity visible, sending " + res);	
	    Message msg = new Message();
	    Bundle b = new Bundle();
	    b.putBoolean("init_complete", res);
	    msg.setData(b);
	    WeatherActivity.ui_update.sendMessage(msg);		
	} else Log.e(TAG, "activity not visible");
	if(App.widget_visible) {
	    	
	}
    }		

    @Override
    public void onCreate() {
	super.onCreate();

	log(COLOUR_INFO, "creating service");

	if(!Util.stationListKnown()) {
	    new Thread(new Runnable() {
		@Override
		public void run() {
		    log(COLOUR_DBG, "updating station list");
		    if(!Util.getStations()) {  // stationList still unknown
			log(COLOUR_ERR, "init: failed");
			send_init_result(false);
		    } else {
			log(COLOUR_DBG, "station list obtained");
			restart_updates();
		    }	
	        }
	    }).start();
	} else {
	    log(COLOUR_INFO, "station list is known already");
	    restart_updates(); 
	}
    }

    public void restart_updates() {
	Log.d(TAG, "restarting updates");
	send_init_result(true);
	// Start location updates
	read_prefs();
	
	pint_location = null;
	pint_weather = null;
	
	log(COLOUR_DBG, "starting updates, priority=" + loc_priority + ", updates: location=" 
		+ loc_update_interval + ", weather=" + wth_update_interval);

	Intent intent  = new Intent(this, Srv.class);
	intent.setAction(LOCATION_UPDATE);
	pint_location = PendingIntent.getService(this, 0, intent, 0);	

	loc_client = LocationServices.getFusedLocationProviderClient(this);
	loc_client.getLastLocation().addOnCompleteListener(new OnCompleteListener<Location>() {
	    @Override
	    public void onComplete(Task<Location> task) {
		if(!task.isSuccessful() || task.getResult() == null) return;
		currentLocation = task.getResult();
		currentStation = Util.getNearestStation(currentLocation.getLatitude(), 
		    currentLocation.getLongitude());
		updateLocalWeather(null);
            }
	});

	LocationRequest loc_req = new LocationRequest();
	loc_req.setPriority(loc_priority);
	loc_req.setInterval(loc_update_interval);
//	loc_req.setFastestInterval(LOC_FASTEST_UPDATE_INTERVAL);
	loc_req.setFastestInterval(loc_update_interval/4);	// Samsung Galaxy S9
	loc_req.setSmallestDisplacement(LOC_MIN_DISPLACEMENT);
	loc_client.requestLocationUpdates(loc_req, pint_location);

	Log.d(TAG, "location updates started");

	// Start weather updates

	intent  = new Intent(this, Srv.class);
	intent.setAction(WEATHER_UPDATE);
	pint_weather = PendingIntent.getService(this, 0, intent, 0);	

	AlarmManager amgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
	amgr.setInexactRepeating(AlarmManager.ELAPSED_REALTIME,
	    SystemClock.elapsedRealtime(), // + WEATHER_UPDATE_INTERVAL,
	    wth_update_interval, pint_weather);
	
	Log.d(TAG, "weather updates started");
    }	

    @Override
    public void onDestroy() {
	Log.d(TAG, "destroying service");
	if(pint_location != null) 
	    LocationServices.getFusedLocationProviderClient(this).removeLocationUpdates(pint_location);
	AlarmManager amgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
	amgr.cancel(pint_weather);
    }

//  static long last_update_time = 0, cur_time;	

//  If it were IntentService:

//  @Override
//  void onHandleIntent (Intent intent) {

    @Override
    public int onStartCommand(Intent intent, int flags, int id) {
	if(intent == null) return START_STICKY;
	String action = intent.getAction();
	if(action == null) return START_STICKY;
	Intent ii = null;
	switch(action) {

	    case LOCATION_UPDATE:	

		log(COLOUR_DBG, "location update received");

/*		if(last_update_time == 0) last_update_time = System.currentTimeMillis();
		cur_time = System.currentTimeMillis();
		if(cur_time - last_update_time < loc_update_interval) {
		    return START_STICKY;
		} 		     
		last_update_time = cur_time; */

		Location loc = null;
		LocationResult result = LocationResult.extractResult(intent);
		if(result != null) loc = result.getLastLocation();
		if(loc == null) {
		    Log.e(TAG, "null location");
		    break;
		}
		currentLocation = loc;
		Station st = Util.getNearestStation(loc.getLatitude(), loc.getLongitude());
		if(st == null) {
		    Log.d(TAG, "station list unknown yet");
		    break;
		}
		if(currentStation == null || currentStation.code != st.code) {
		    currentStation = st;
		    Log.d(TAG, "station changed to " + st.code);
		    if(App.widget_visible) {
			ii = new Intent(this, WidgetProvider.class);
			ii.setAction(WidgetProvider.WEATHER_CHANGED_BROADCAST);
		    }
		    updateLocalWeather(ii);  // station changed: update weather right now
	        }
		if(App.widget_visible) {
		    ii = new Intent(this, WidgetProvider.class);
		    ii.setAction(WidgetProvider.LOCATION_CHANGED_BROADCAST);
		    sendBroadcast(ii);	
		}
		break;

	    case WEATHER_UPDATE:
		Log.d(TAG, "weather update received");
		if(App.widget_visible) {
		    ii = new Intent(this, WidgetProvider.class);
		    ii.setAction(WidgetProvider.WEATHER_CHANGED_BROADCAST);
		}
		updateLocalWeather(ii);
		break;

	    case WIDGET_STARTED:
		Log.d(TAG, "widget started");
		if(localWeather != null) {
		    ii = new Intent(this, WidgetProvider.class);
		    ii.setAction(WidgetProvider.WEATHER_CHANGED_BROADCAST);
		    sendBroadcast(ii);
		} 
		if(currentStation != null) {
		    ii.setAction(WidgetProvider.LOCATION_CHANGED_BROADCAST);
		    sendBroadcast(ii);
		} else {
		    final Intent uu = new Intent(this, WidgetProvider.class);
		    uu.setAction(WidgetProvider.WEATHER_CHANGED_BROADCAST);
		    new Thread(new Runnable() {
			@Override
			public void run() {
			  try {	
			    this.wait(2000);
			    if(currentStation != null) {
		//		ii = new ru.meteoinfo.App.getContext().Intent(this, WidgetProvider.class);
		//		ii.setAction(WidgetProvider.WEATHER_CHANGED_BROADCAST);
				sendBroadcast(uu);
			    }
			  } catch(Exception e) { }	
			}
		    }).start();	
		}
		break;		

	    case UPDATE_REQUIRED:
		Log.d(TAG, "restarting updates with new settings");
		restart_updates();
		break;
		
	    case WIDGET_STOPPED:
		break;

	    case ACTIVITY_STARTED: 	
		restart_updates();
		break;	

	    case ACTIVITY_STOPPED: 	
		Log.d(TAG, "activity stopped");
		if(!App.widget_visible && !App.activity_visible) stopSelf();	// no widget, no activity, exiting.
		break;

	    default:
		Log.e(TAG, "unknown action");
		break;
	}
	return START_STICKY;
    }

    private void updateLocalWeather(Intent intent) {
	final Intent ii = intent;
	if(currentStation == null) {
	    Log.e(TAG, "updateLocalWeather: local station unknown yet");
	    return;	
	}
	new Thread(new Runnable() {
	    @Override
	    public void run() {
		Log.d(TAG, "updating local weather for " + currentStation.code);
		localWeather = Util.getWeather(currentStation.code);
		if(ii != null && localWeather != null) sendBroadcast(ii);
	    }
	}).start();
    }

    private static SharedPreferences settings = null;

    public static void read_prefs() {
	if(settings == null) settings = PreferenceManager.getDefaultSharedPreferences(App.getContext());
	boolean use_gps = settings.getBoolean("use_gps", false);
	loc_priority = use_gps ? LocationRequest.PRIORITY_HIGH_ACCURACY : LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY;
	loc_update_interval = settings.getInt("loc_update_interval", 20) * 1000;
	wth_update_interval = settings.getInt("wth_update_interval", 60) * 1000;
    }		


}






