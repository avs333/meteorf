package ru.meteoinfo;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import javax.net.ssl.HttpsURLConnection;
import java.net.URL;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.os.Looper;
import android.util.Log;
import android.widget.RemoteViews;
import android.location.Location;
import android.app.PendingIntent;
import android.net.Uri;
import android.util.JsonReader;
import android.util.JsonToken;
import android.os.AsyncTask;

import com.google.android.gms.location.*;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import ru.meteoinfo.Util.Station;

public class WidgetProvider extends AppWidgetProvider {

    public static final String LOCATION_CHANGED_BROADCAST = "location_changed";

    private static final long LOC_UPDATE_INTERVAL = 20 * 1000;		/* 20 sec should be enough */
    private static final long LOC_FASTEST_UPDATE_INTERVAL = 2 * 1000;
    private static final float LOC_MIN_DISPLACEMENT = 0.0f; // <- in metres. 0.0 debug only!! 500.0f; will be ok for release

// Should be a setting to select one of:
// private static final int locPriority = LocationRequest.PRIORITY_HIGH_ACCURACY;	
    private static final int locPriority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY;	

    private static final String TAG = "meteoinfo:WidgetProvider";
    private static PendingIntent pint_click = null;
    private static PendingIntent pint_loc = null;
    private static long cur_station_code = -1;	
    private static String last_loc_addr = null;
    private static boolean loc_update_in_progress = false; 
    private final Object obj = new Object();

    @Override
    public void onReceive(Context context, Intent intent) {
	if(intent.getAction().equals(LOCATION_CHANGED_BROADCAST)) {
	    Log.d(TAG, "location change");
	    synchronized(obj) {	
		if(loc_update_in_progress) {
		    Log.d(TAG, "location change: update in progress, exiting");
		    return;
		}	
		loc_update_in_progress = true;
	    }
	    Location location = null;	
	    LocationResult result = LocationResult.extractResult(intent);
	    if(result != null) location = result.getLastLocation();	    	 	
	    if(location == null) {
		Log.e(TAG, "location change: null location, exiting");
		loc_update_in_progress = false;
	    } else onLocationUpdate(context, location);
	    return;	
	}
	super.onReceive(context, intent);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.d(TAG, "onUpdate");
        // For each widget that needs an update, get the text that we should display:
        //   - Create a RemoteViews object for it
        //   - Set the text in the RemoteViews object
        //   - Tell the AppWidgetManager to show that views object for the widget.
	final int N = appWidgetIds.length;
        for (int i=0; i<N; i++) {
            int appWidgetId = appWidgetIds[i];
            updateAppWidget(context, appWidgetManager, appWidgetId, App.get_string(R.string.loc_unk_yet));
        }
        Log.d(TAG, "onUpdate complete");
    }
    
    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        Log.d(TAG, "onDeleted");
        // When the user deletes the widget, delete the preference associated with it.
/*        final int N = appWidgetIds.length;
        for (int i=0; i<N; i++) {
            WidgetConfigure.deleteTitlePref(context, appWidgetIds[i]);
        } */
    }

    @Override
    public void onEnabled(Context context) {
        Log.d(TAG, "onEnabled");
	try {

/* 	    PackageManager pm = context.getPackageManager();
	    pm.setComponentEnabledSetting(
                new ComponentName(context, "ru.meteoinfo.WidgetBroadcastReceiver"),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP); */

	    // previous installation may have failed!
	    cur_station_code = -1;	
	    last_loc_addr = null;

	    Intent intent = new Intent(context, WidgetProvider.class);
	    intent.setAction(LOCATION_CHANGED_BROADCAST);

	    pint_loc = PendingIntent.getBroadcast(context, 0, intent, 0);	
	    LocationRequest loc_req = new LocationRequest();
	    loc_req.setPriority(locPriority);
	    loc_req.setInterval(LOC_UPDATE_INTERVAL);
	    loc_req.setFastestInterval(LOC_FASTEST_UPDATE_INTERVAL);
	    loc_req.setSmallestDisplacement(LOC_MIN_DISPLACEMENT);

	    LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
	    builder.addLocationRequest(loc_req);
	    LocationSettingsRequest loc_set_request = builder.build();
	    SettingsClient sett_client = LocationServices.getSettingsClient(context);
	    sett_client.checkLocationSettings(loc_set_request);

	    LocationServices.getFusedLocationProviderClient(context).requestLocationUpdates(loc_req, pint_loc);

	} catch (Exception e) {
	    Log.e(TAG, "exception in onEnabled()");
	    e.printStackTrace();	
	}
        Log.d(TAG, "onEnabled complete");
    }

    void onLocationUpdate(Context context, Location loc) {

	final Location location = loc;
	final Context ctx = context;
	final AppWidgetManager gm = AppWidgetManager.getInstance(context);
        new AsyncTask<Void, Void, String>() {
	    @Override
	    protected String doInBackground(Void... params) {
		Log.i(TAG, "background task");
		double lat = location.getLatitude(), lon = location.getLongitude();
		if(Util.fullStationList == null) {
		    Log.d(TAG, "null station list, updating");
		    Util.getStations(false);
		}
		Station st = Util.getNearestStation(lat, lon);
		if(st == null) {
		    Log.e(TAG, "background task complete: failed to update station list");	
		    return null;
	        }
		String addr = Util.getAddress(lat, lon);
		if(addr != null) {
		    if(last_loc_addr != null && addr.equals(last_loc_addr)) {
			Log.d(TAG, "background task complete: address unchanged");
			return null;
		    }
		    last_loc_addr = addr;
		    if(addr.matches("^\\d+?.*")) addr = "Дом " + addr;
		    cur_station_code = st.code;	
		    pint_click = null;  // must update pint_click with new station code
		    Log.d(TAG, "cur_station_code=" + cur_station_code);	
		}
		Log.d(TAG, "background task complete");
		return addr;
	    }
	    @Override
	    protected void onPostExecute(String addr) {
		try {
		    if(addr != null) {	
			int [] bound_widgets = gm.getAppWidgetIds(
			    new ComponentName(ctx, "ru.meteoinfo.WidgetProvider"));
			for(int i = 0; i < bound_widgets.length; i++)
			    updateAppWidget(ctx, gm, bound_widgets[i], addr);
			Log.d(TAG, "location change: widget updated");
		    } else Log.d(TAG, "location change: widget left untouched");
		} catch (Exception e) {
		    Log.e(TAG, "exception in foreground task");
		}
		loc_update_in_progress = false;
	    }
	}.execute();
    }	

    @Override
    public void onDisabled(Context context) {
        Log.d(TAG, "onDisabled");
	try {
/*	    PackageManager pm = context.getPackageManager();
	    pm.setComponentEnabledSetting(
		new ComponentName(context, "ru.meteoinfo.WidgetBroadcastReceiver"),
		PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP); */
	    if(pint_loc != null) LocationServices.getFusedLocationProviderClient(context).removeLocationUpdates(pint_loc);
	} catch (Exception e) {
	    Log.e(TAG, "exception in onDisabled()");
	    e.printStackTrace();	
	}
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager,
            int appWidgetId, String addr) {

        Log.d(TAG, "updateAppWidget appWidgetId=" + appWidgetId);
	RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
	views.setTextViewText(R.id.w_addr, addr);

	// Pending intent to display a webpage when the widget is clicked
	if(pint_click == null && cur_station_code != -1) {
	    String url =  Util.URL_SRV_DATA + "?p=" + cur_station_code;	
	    Intent intent = new Intent(context, WebActivity.class);
	    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	    intent.putExtra("action", url);
	    intent.putExtra("show_ui", false);
	    pint_click = PendingIntent.getActivity(context, 0, intent, 0); 	
	}
	// "If the pendingIntent is null, we clear the onClickListener" -> (C) Android Oreo
	views.setOnClickPendingIntent(R.id.w_addr, pint_click);	    		
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
}



