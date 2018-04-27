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

// Should be a setting to select one of:
// private static final int locPriority = LocationRequest.PRIORITY_HIGH_ACCURACY;	
    private static final int locPriority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY;	

    private static final String TAG = "meteoinfo:WidgetProvider";
    private static PendingIntent pint_click = null;
    private static PendingIntent pint_loc = null;
    private static long cur_station_code = -1;	
    private static boolean loc_update_in_progress = false; 

    @Override
    public void onReceive(Context context, Intent intent) {
	Log.d(TAG, "onReceive entry");
//	if(intent.getAction() == Intent.ACTION_BOOT_COMPLETED) Log.d(TAG, "received BOOT_COMPLETED");
	if(intent.getAction().equals(LOCATION_CHANGED_BROADCAST)) {
	    Log.d(TAG, "location changed");
	    if(!loc_update_in_progress) {
		loc_update_in_progress = true;	
	        LocationResult result = LocationResult.extractResult(intent);
		if(result == null) Log.e(TAG, "null LocationResult");
		else onLocationUpdate(context, result);
	    } else Log.d(TAG, "location update in progress");
	}
	super.onReceive(context, intent);
	Log.d(TAG, "onReceive exit");
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

	    Intent intent = new Intent(context, WidgetProvider.class);
	    intent.setAction(LOCATION_CHANGED_BROADCAST);

	    pint_loc = PendingIntent.getBroadcast(context, 0, intent, 0);	
	    LocationRequest loc_req = new LocationRequest();
	    loc_req.setPriority(locPriority);
	    loc_req.setInterval(LOC_UPDATE_INTERVAL);
	    loc_req.setFastestInterval(LOC_FASTEST_UPDATE_INTERVAL);

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
        Log.d(TAG, "done Enabled");
    }


    void onLocationUpdate(Context context, LocationResult result) {

	final Location location = result.getLastLocation();
	if(location == null) {
	    Log.d(TAG, "location is null");
	    loc_update_in_progress = false;
	    return;
	}		
	final Context ctx = context;
	final AppWidgetManager gm = AppWidgetManager.getInstance(context);

        new AsyncTask<Void, Void, String>() {
	    @Override
	    protected String doInBackground(Void... params) {
		Log.i(TAG, "background task");
		double lat = location.getLatitude(), lon = location.getLongitude();
		if(Util.fullStationList == null) {
		    Log.d(TAG, "null station list, updating");
		    boolean result = Util.getStations(false);
		    Log.i(TAG, "getStations returned " + result);	
		}
		Station st = Util.getNearestStation(lat, lon);
		if(st != null) {
		    cur_station_code = st.code;
		    pint_click = null;
		    Log.d(TAG, "cur_station_code=" + cur_station_code);	
		}
		String s = Util.getAddress(lat, lon);
		Log.d(TAG, "getAddress returned " + (s != null));	
		if(s != null && s.matches("^\\d+?.*")) s = "Дом " + s;
		return s;
	    }
	    @Override
	    protected void onPostExecute(String addr) {
		Log.i(TAG, "foreground task");
		try {
		    if(addr != null) {	
			int [] bound_widgets = gm.getAppWidgetIds(
			    new ComponentName(ctx, "ru.meteoinfo.WidgetProvider"));
			for(int i = 0; i < bound_widgets.length; i++)
			    updateAppWidget(ctx, gm, bound_widgets[i], addr);
		    } else Log.e(TAG, "Null address, won't touch the widget");
		} catch (Exception e) {
		    Log.e(TAG, "exception in foreground task");
		}
		Log.i(TAG, "done with foreground task");
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



