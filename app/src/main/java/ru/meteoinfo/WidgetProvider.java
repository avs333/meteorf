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
import static ru.meteoinfo.WeatherActivity.*;

public class WidgetProvider extends AppWidgetProvider {

    public static final String LOCATION_CHANGED_BROADCAST = "location_changed";
    public static final long LOC_UPDATE_INTERVAL = 20 * 1000;
    public static final long LOC_FASTEST_UPDATE_INTERVAL = 2000; /* 2 sec */
//    public static final int locPriority = LocationRequest.PRIORITY_HIGH_ACCURACY;	
    public static final int locPriority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY;	
    private static final String TAG = "meteoinfo:WidgetProvider";
    private static PendingIntent pint = null;
//    private GoogleApiClient cli = null;    
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
            updateAppWidget(context, appWidgetManager, appWidgetId, "Location unknown yet");
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

/*	    PackageManager pm = context.getPackageManager();
	    pm.setComponentEnabledSetting(
                new ComponentName(context, "ru.meteoinfo.WidgetBroadcastReceiver"),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP); */

	    Log.d(TAG, "startLocationUpdates");
/*
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
				double lat = location.getLatitude(), lon = location.getLongitude();
				if(fullStationList == null) {
				    Log.i(TAG, "null station list, calling server");	
				    if(!WeatherActivity.getStations(false)) Log.e(TAG, "getStations() returned error");
				}	
				if(fullStationList != null) {
				    Station st = getNearestStation(lat, lon);
				    if(st != null) {
					cur_station_code = st.code;
					pint = null;
				    }
				    Log.d(TAG, "cur_station_code=" + cur_station_code);	
				}
				return WeatherActivity.getAddress(lat, lon);
			    }
			    @Override
			    protected void onPostExecute(String addr) {
				int [] bound_widgets = gm.getAppWidgetIds(
					new ComponentName(ctx, "ru.meteoinfo.WidgetProvider"));
			        for(int i = 0; i < bound_widgets.length; i++)
				    updateAppWidget(ctx, gm, bound_widgets[i], addr);
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

/*
    String getAddress(Location location) {
	if(location == null) return null;
	HttpsURLConnection urlConnection = null;
	InputStream in = null;
	JsonReader reader = null;
	String addr = null;
	try {
	    URL url = new URL(OSM_GEOCODING_URL + "&lat=" + location.getLatitude() + "&lon=" + location.getLongitude());	
	    urlConnection = (HttpsURLConnection) url.openConnection();
            urlConnection.setReadTimeout(30*1000);
            in = new BufferedInputStream(urlConnection.getInputStream());
            reader = new JsonReader(new InputStreamReader(in, "UTF-8"));
            reader.beginArray();
	    while(reader.hasNext()) {
		String name = reader.nextName();
		if(name.equals("display_name")) {
		    addr = reader.nextString();
		    break;		
		}
	    }
	} catch (Exception e) {
	    Log.e(TAG, "exception in getAdderss()");
	    e.printStackTrace();		
	} finally {
	    try {
		if(reader != null) reader.close();
		if(in != null) in.close();
		if(urlConnection != null) urlConnection.disconnect();
	    } catch (Exception e) {}		
	}
	return addr;
    }	
*/

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager,
            int appWidgetId, String addr) {

        Log.d(TAG, "updateAppWidget appWidgetId=" + appWidgetId);
	RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
	views.setTextViewText(R.id.w_addr, addr);

	if(pint == null && cur_station_code != -1) {
	    String url =  URL_SRV_DATA + "?p=" + curStation.code;	
	    Log.d(TAG, "onClick -> " + url);	
	    Intent intent = new Intent(context, WebActivity.class);
	    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	    intent.putExtra("action", url);
	    pint = PendingIntent.getActivity(context, 0, intent, 0); 	
	}
	// "If the pendingIntent is null, we clear the onClickListener"
	views.setOnClickPendingIntent(R.id.w_addr, pint);	    		


/*	String s;
	if(loc == null) s = "Latitude: <unknown>"; else s = "Latitude: " + loc.getLatitude();
        views.setTextViewText(R.id.w_latitude, s);
	if(loc == null) s = "Longitude: <unknown>"; else s = "Longitude: " + loc.getLongitude();
        views.setTextViewText(R.id.w_longitude, s); */
	

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
}



