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
import static ru.meteoinfo.Util.*;

public class WidgetProvider extends AppWidgetProvider {

    public static final String LOCATION_CHANGED_BROADCAST = "location_changed";
    public static final long LOC_UPDATE_INTERVAL = 20 * 1000;
    public static final long LOC_FASTEST_UPDATE_INTERVAL = 2000; /* 2 sec */

// Lazy todo is a setting to select one of:
// public static final int locPriority = LocationRequest.PRIORITY_HIGH_ACCURACY;	
    public static final int locPriority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY;	

    private static final String TAG = "meteoinfo:WidgetProvider";
    private static PendingIntent pint = null;

// https://wiki.openstreetmap.org/wiki/Nominatim -- takes lat/lon coordinates, returns some human-readable
// address in the proximity. NB: user-agent +must+ be specified (no permission otherwise), see getAddress() in Utils.
    public static String OSM_GEOCODING_URL = "https://nominatim.openstreetmap.org/reverse?format=json&accept-language=ru,en"; // &lat=...&lon=...	

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

    static long cur_station_code = -1;	

    @Override
    public void onEnabled(Context context) {
        Log.d(TAG, "onEnabled");
	try {

/*
	    Just in case:	
	    PackageManager pm = context.getPackageManager();
	    pm.setComponentEnabledSetting(
                new ComponentName(context, "ru.meteoinfo.WidgetBroadcastReceiver"),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP); */

	    Log.d(TAG, "startLocationUpdates");
/*
	    Just in case:
	    Intent intent = new Intent(LOCATION_CHANGED_BROADCAST);
	    pint = PendingIntent.getBroadcast(context, 0, intent, 0);	
	    LocationRequest loc_req = new LocationRequest();
	    loc_req.setPriority(locPriority);
	    loc_req.setInterval(LOC_UPDATE_INTERVAL);
	    loc_req.setFastestInterval(LOC_FASTEST_UPDATE_INTERVAL);

	    LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
	    builder.addLocationRequest(loc_req);
	    LocationSettingsRequest loc_set_request = builder.build();
	    SettingsClient sett_client = LocationServices.getSettingsClient(context);
	    sett_client.checkLocationSettings(loc_set_request);

	    LocationServices.getFusedLocationProviderClient(context).requestLocationUpdates(loc_req, pint);
*/
	    LocationRequest loc_request = new LocationRequest();
	    loc_request.setPriority(locPriority);
	    loc_request.setInterval(LOC_UPDATE_INTERVAL);
	    loc_request.setFastestInterval(LOC_FASTEST_UPDATE_INTERVAL);
 	    LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
	    builder.addLocationRequest(loc_request);
	    LocationSettingsRequest loc_set_request = builder.build();
	    SettingsClient sett_client = LocationServices.getSettingsClient(context);
	    sett_client.checkLocationSettings(loc_set_request);

	    final Context ctx = context;
	    final AppWidgetManager gm = AppWidgetManager.getInstance(context);

	    LocationCallback loc_callback = new LocationCallback() {
		@Override
		public void onLocationResult(LocationResult locationResult) {
		    Log.d(TAG, "location changed, updating widgets");
		    try {
			final Location location = locationResult.getLastLocation();
			if(location == null) {
			    Log.e(TAG, "null location supplied");
			    return;
			}
		        new AsyncTask<Void, Void, String>() {
			    @Override
			    protected String doInBackground(Void... params) {
				// Log.i(TAG, "background task");
				double lat = location.getLatitude(), lon = location.getLongitude();
				if(fullStationList == null) {
				    Log.i(TAG, "null station list, calling server");	
				    boolean bb = getStations(false);
				    Log.i(TAG, "getStations() returned " + bb);
				} else Log.i(TAG, "fullStationList known already");
				if(fullStationList != null) {
				    Station st = getNearestStation(lat, lon);
				    if(st != null) {
					cur_station_code = st.code;
					pint = null;
				    }
				//    Log.d(TAG, "cur_station_code=" + cur_station_code);	
				}
				String s = getAddress(lat, lon);
				Log.d(TAG, "getAddress returned " + (s != null));	
				return s;
			    }
			    @Override
			    protected void onPostExecute(String addr) {
				Log.i(TAG, "foreground task");
				if(addr == null) {
				    Log.e(TAG, "Null addres on input, exiting");	
				    return;
				}
				try {
				    int [] bound_widgets = gm.getAppWidgetIds(
					new ComponentName(ctx, "ru.meteoinfo.WidgetProvider"));
				    for(int i = 0; i < bound_widgets.length; i++)
					updateAppWidget(ctx, gm, bound_widgets[i], addr);
				} catch (Exception e) {
				    Log.e(TAG, "exception in foreground task");
				}
				Log.i(TAG, "done with foreground task");
			    }
			}.execute();
		    } catch (Exception e) {
			Log.e(TAG, "exception in onLocationResult()");
			e.printStackTrace();	
		    } 			
		}   
	    };
	    LocationServices.getFusedLocationProviderClient(context).requestLocationUpdates(
		loc_request, loc_callback, Looper.myLooper());

	} catch (Exception e) {
	    Log.e(TAG, "exception in onEnabled()");
	    e.printStackTrace();	
	}
        Log.d(TAG, "done Enabled");
    }

    @Override
    public void onDisabled(Context context) {
        Log.d(TAG, "onDisabled");
	try {
/*	    PackageManager pm = context.getPackageManager();
	    pm.setComponentEnabledSetting(
		new ComponentName(context, "ru.meteoinfo.WidgetBroadcastReceiver"),
		PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP); */
	    LocationServices.getFusedLocationProviderClient(context).removeLocationUpdates(pint);
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

	// Listen, and display a webpage when the widget is clicked
	if(pint == null && cur_station_code != -1) {
	    String url =  URL_SRV_DATA + "?p=" + cur_station_code;	
	    Intent intent = new Intent(context, WebActivity.class);
	    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	    intent.putExtra("action", url);
	    intent.putExtra("show_ui", false);
	    pint = PendingIntent.getActivity(context, 0, intent, 0); 	
	}
	// "If the pendingIntent is null, we clear the onClickListener" -> (C) Android Oreo
	views.setOnClickPendingIntent(R.id.w_addr, pint);	    		
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
}



