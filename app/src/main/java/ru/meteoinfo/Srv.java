package ru.meteoinfo;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
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

import com.google.android.gms.location.*;
import com.google.android.gms.tasks.OnSuccessListener;

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

    private static final long LOC_UPDATE_INTERVAL = 20 * 1000;		/* 20 sec should be enough */
    private static final long LOC_FASTEST_UPDATE_INTERVAL = 2 * 1000;
    private static final float LOC_MIN_DISPLACEMENT = 0.0f; // <- in metres. 0.0 debug only!! 500.0f; will be ok for release
    private static final int loc_priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY; // PRIORITY_HIGH_ACCURACY;
    private static final long WEATHER_UPDATE_INTERVAL = 2 * 60 * 1000;
	
    private static PendingIntent pint_location = null;
    private static PendingIntent pint_weather = null;
    private static final String TAG = "ru.meteoinfo:Srv";

    private static boolean must_update_widget = false;
    private FusedLocationProviderClient loc_client = null;

    private static final int COLOUR_ERR = WeatherActivity.COLOUR_ERR;
    private static final int COLOUR_INFO = WeatherActivity.COLOUR_INFO;
    private static final int COLOUR_GOOD = WeatherActivity.COLOUR_GOOD;
    private static final int COLOUR_DBG = WeatherActivity.COLOUR_DBG;

    private static void log(int colour, String mesg) {
	if(App.activity_visible) WeatherActivity.logUI(colour, mesg);
	if(colour == COLOUR_ERR) Log.e(TAG, mesg);
	else Log.d(TAG, mesg);
    }

    private static void log(int colour, int res_id) {
	log(colour, App.get_string(res_id));
    }

    @Override
    public IBinder onBind (Intent intent) {
	return null;
    }

    @Override
    public void onCreate() {
	super.onCreate();

	log(COLOUR_INFO, "creating service");
	
	if(Util.fullStationList == null || Util.localWeather == null) {
	    new Thread(new Runnable() {
		@Override
		public void run() {
		    int result = 0;
		    log(COLOUR_INFO, "init: updating station list");
		    if(Util.fullStationList == null) result = (Util.getStations()) ? 1 : 0; 
		    else Log.d(TAG,"otlichno"); 
		    if(Util.currentStation != null) {
		        Log.d(TAG, "init: updating local weather for " + Util.currentStation.code);
			Util.localWeather = Util.getWeather(Util.currentStation.code);
		    	Log.d(TAG, "init: weather updated");
		    }	
		    if(App.activity_visible) {
			log(COLOUR_DBG, "init: report result to activity");
			Message msg = new Message();
			Bundle b = new Bundle();
			b.putInt("init_complete", result);
			msg.setData(b);
			WeatherActivity.ui_update.sendMessage(msg);
		    } else log(COLOUR_DBG, "init: activity not started");
	        }
	    }).start();
	}

	// Start location updates

	Intent intent  = new Intent(this, Srv.class);
	intent.setAction(LOCATION_UPDATE);
	pint_location = PendingIntent.getService(this, 0, intent, 0);	

	loc_client = LocationServices.getFusedLocationProviderClient(this);
	LocationRequest loc_req = new LocationRequest();
	loc_req.setPriority(loc_priority);
	loc_req.setInterval(LOC_UPDATE_INTERVAL);
	loc_req.setFastestInterval(LOC_FASTEST_UPDATE_INTERVAL);
	loc_req.setSmallestDisplacement(LOC_MIN_DISPLACEMENT);
	loc_client.requestLocationUpdates(loc_req, pint_location);

	Log.d(TAG, "init: location updates started");

	// Start weather updates

	intent  = new Intent(this, Srv.class);
	intent.setAction(WEATHER_UPDATE);
	pint_weather = PendingIntent.getService(this, 0, intent, 0);	

	AlarmManager amgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
	amgr.setInexactRepeating(AlarmManager.ELAPSED_REALTIME,
	    SystemClock.elapsedRealtime(), // + WEATHER_UPDATE_INTERVAL,
	    WEATHER_UPDATE_INTERVAL, pint_weather);
	
	Log.d(TAG, "init: weather updates started");
    }

    @Override
    public void onDestroy() {
	Log.d(TAG, "destroying service");
	if(pint_location != null) 
	    LocationServices.getFusedLocationProviderClient(this).removeLocationUpdates(pint_location);
	AlarmManager amgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
	amgr.cancel(pint_weather);
    }

//  If it were IntentService:

//  @Override
//  void onHandleIntent (Intent intent) {

    @Override
    public int onStartCommand(Intent intent, int flags, int id) {
	Intent ii = null;
	switch(intent.getAction()) {

	    case LOCATION_UPDATE:	
		log(COLOUR_DBG, "location update received");

		Location loc = null;
		LocationResult result = LocationResult.extractResult(intent);
		if(result != null) loc = result.getLastLocation();
		if(loc == null) {
		    Log.e(TAG, "null location");
		    break;
		}
		Util.currentLocation = loc;
		Station st = Util.getNearestStation(loc.getLatitude(), loc.getLongitude());
		if(st == null) {
		    Log.d(TAG, "station list unknown yet");
		    break;
		}
		if(Util.currentStation == null || Util.currentStation.code != st.code) {
		    Util.currentStation = st;
		    Log.d(TAG, "station changed");
		    if(must_update_widget) {
			ii = new Intent(this, WidgetProvider.class);
			ii.setAction(WidgetProvider.WEATHER_CHANGED_BROADCAST);
		    }
		    updateLocalWeather(ii);  // station changed: update weather right now
	        }
		if(must_update_widget) {
		    ii = new Intent(this, WidgetProvider.class);
		    ii.setAction(WidgetProvider.LOCATION_CHANGED_BROADCAST);
		    sendBroadcast(ii);	
		}
		break;

	    case WEATHER_UPDATE:
		Log.d(TAG, "weather update received");
		if(must_update_widget) {
		    ii = new Intent(this, WidgetProvider.class);
		    ii.setAction(WidgetProvider.WEATHER_CHANGED_BROADCAST);
		}
		updateLocalWeather(ii);
		break;

	    case WIDGET_STARTED:
		Log.d(TAG, "widget started");
		must_update_widget = true;
		if(Util.localWeather != null) {
		    ii = new Intent(this, WidgetProvider.class);
		    ii.setAction(WidgetProvider.WEATHER_CHANGED_BROADCAST);
		} if(Util.currentStation != null) {
		    sendBroadcast(ii);
		    ii.setAction(WidgetProvider.LOCATION_CHANGED_BROADCAST);
		    sendBroadcast(ii);
		}
		break;		

	    case WIDGET_STOPPED:
		Log.d(TAG, "widget stopped");
		must_update_widget = false;
		break;	

	    case ACTIVITY_STARTED: 	
		Log.d(TAG, "activity started");
		break;	

	    case ACTIVITY_STOPPED: 	
		Log.d(TAG, "activity stopped");
		if(!must_update_widget) stopSelf();
		break;

	    default:
		Log.d(TAG, "unknown action");
		break;
	}
	return START_STICKY;
    }

    private void updateLocalWeather(Intent intent) {
	final Intent ii = intent;
	if(Util.currentStation == null) {
	    Log.e(TAG, "updateLocalWeather: local station unknown yet");
	}
	new Thread(new Runnable() {
	    @Override
	    public void run() {
		Log.d(TAG, "updating local weather for " + Util.currentStation.code);
		Util.localWeather = Util.getWeather(Util.currentStation.code);
		if(ii != null && Util.localWeather != null) sendBroadcast(ii);
	    }
	}).start();
    }

}





