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

import ru.meteoinfo.Util.Station;
import ru.meteoinfo.Util.WeatherData;
import ru.meteoinfo.Util.WeatherInfo;

public class WidgetProvider extends AppWidgetProvider {

    public static final String WEATHER_CHANGED_BROADCAST  = "weather_changed";
    public static final String LOCATION_CHANGED_BROADCAST  = "location_changed";

    private static final String TAG = "ru.meteoinfo:WidgetProvider";

    // pending intents for widget clicks	
    private static PendingIntent pint_show_webpage = null;
    private static PendingIntent pint_widget_update = null;

    private static long last_sta_code = -1;

    private static Context ctx;

    private static AppWidgetManager gm;

    // handler for enqueuing update requests to avoid race conditions 	
    private final Handler hdl = new Handler();

    @Override
    public void onEnabled(Context context) {
        Log.d(TAG, "onEnabled");
	App.widget_visible = true;
	ctx = context;
 	gm = AppWidgetManager.getInstance(ctx);
	Intent i = new Intent(context, Srv.class);
	i.setAction(Srv.WIDGET_STARTED);
	context.startService(i);
        Log.d(TAG, "onEnabled complete");
    }
 
    @Override
    public void onReceive(Context context, Intent intent) {	// just enqueue broadcasts
	if(intent == null) {
	    Log.e(TAG, "bogus intent in onReceive");
	    return;
	}
	String action = intent.getAction();
	if(action == null) {
	    Log.e(TAG, "bogus action in onReceive");
	    return;
	}
	if(action.equals(LOCATION_CHANGED_BROADCAST)) {
	    hdl.post(new Runnable() { 
		public void run() { 
		    location_update(); 
		}
	    });	
	    Log.d(TAG, "location change queued");
	} else if(action.equals(WEATHER_CHANGED_BROADCAST)) {
	    hdl.post(new Runnable() { 
		public void run() { 
		    weather_update(); 
		}
	    });	
	    Log.d(TAG, "weather change queued");
	} else {
	    super.onReceive(context, intent);
	    return;	
	}
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager man, int[] wids) {
        Log.d(TAG, "onUpdate");
        for(int i = 0; i < wids.length; i++) 
	    update_widget(context, man, wids[i], null, null, App.get_string(R.string.loc_unk_yet));
        Log.d(TAG, "onUpdate complete");
    }

    @Override
    public void onDeleted(Context context, int[] wids) {
        Log.d(TAG, "onDeleted");
    } 

    @Override
    public void onDisabled(Context context) {
        Log.d(TAG, "onDisabled");
	Intent i = new Intent(context, Srv.class);
	i.setAction(Srv.WIDGET_STOPPED);
	context.startService(i);
	App.widget_visible = false;
	pint_show_webpage = null;
	pint_widget_update = null;
    }

//    private void weather_update(Context context) {
    private void weather_update() {
	if(Util.localWeather == null) {
	    Log.d(TAG, "weather_update: localWeather unknown");
	    return;
	}
 	if(Util.localWeather.for3days == null || Util.localWeather.for3days.size() == 0) {
	    Log.d(TAG, "weather_update: weather for 3 days unknown");
	    return;
	}
	WeatherInfo wi = Util.localWeather.for3days.get(0);
	if(wi == null) {
	    Log.d(TAG, "weather_update: first weather info for 3 days unknown");
	    return;
	}
	String temp = wi.get_temperature();
	if(temp == null) {
	    Log.d(TAG, "weather_update: null temperature string");	
	    return;
	}
	if(!temp.startsWith("-")) temp = "+" + temp;
	Log.d(TAG, "weather_update: temp=" + temp);
	int pt = temp.indexOf(".");
//	if(pt > 0) temp = temp.substring(0, pt);
//	temp += "°C";

/*
	String s, data;
	for(int i = 0; i < Util.localWeather.for3days.size(); i++) {
	    wi = Util.localWeather.for3days.get(i);
	    s = wi.get_pressure();
	    if(s != null) {
		data = String.format(App.get_string(R.string.wd_pressure), s);
	    } 	  	
	} */

	int [] bound_widgets = gm.getAppWidgetIds(
			    new ComponentName(ctx, "ru.meteoinfo.WidgetProvider"));
	for(int i = 0; i < bound_widgets.length; i++)
	    update_widget(ctx, gm, bound_widgets[i], temp, null, null);
    }

//    private void location_update(Context context) {
    private void location_update() {
//	final Context ctx = context;
        new AsyncTask<Void, Void, String>() {
	    @Override
	    protected String doInBackground(Void... params) {
		Log.i(TAG, "background task");
		if(Util.currentLocation == null) {
		    Log.e(TAG, "background task complete: no current location yet");	
		    return null;
	        }
		Station st = Util.getNearestStation(Util.currentLocation.getLatitude(), 
			Util.currentLocation.getLongitude());
		if(st == null) {	
		    Log.e(TAG, "background task complete: no current station yet");	
		    return null;
		}
		try {
		    String addr = Util.getAddressFromOSM(Util.currentLocation.getLatitude(), 
		    	Util.currentLocation.getLongitude());
		    if(addr != null) {
			if(addr.matches("^\\d+?.*")) addr = "Дом " + addr;
		    } else Log.d(TAG, "background task: zero address");
		    Log.d(TAG, "background task complete");
		    return addr;
		} catch (Exception e) {
		    Log.d(TAG, "background task: no connection");
		    return null;
		}
	    }
	    @Override
	    protected void onPostExecute(String addr) {
		try {
		    if(addr != null) {	
			int [] bound_widgets = gm.getAppWidgetIds(
			    new ComponentName(ctx, "ru.meteoinfo.WidgetProvider"));
			for(int i = 0; i < bound_widgets.length; i++)
			    update_widget(ctx, gm, bound_widgets[i], null, null, addr);
			Log.d(TAG, "location change: widget updated: " + addr);
		    } else Log.d(TAG, "location change: widget left untouched");
		} catch (Exception e) {
		    Log.e(TAG, "exception in foreground task");
		}
	    }
	}.execute();
    }	

    static void update_widget(Context context, AppWidgetManager man, int wid, 
		String temp, String data, String addr) {

        Log.d(TAG, "update_widget id=" + wid + ", temp=" + temp + ", addr=" + addr);

	RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);

	if(temp != null) views.setTextViewText(R.id.w_temp, temp);
	if(addr != null) views.setTextViewText(R.id.w_addr, addr);
//	if(data != null) views.setTextViewText(R.id.w_data, data);

	// Pending intent to display a webpage when the right part of the widget is clicked
	if(Util.currentStation != null && Util.currentStation.code != last_sta_code) {
	    last_sta_code = Util.currentStation.code;
	    String url =  Util.URL_STA_DATA + "?p=" + Util.currentStation.code;	
	    Intent intent = new Intent(context, WebActivity.class);
	    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	    intent.putExtra("action", url);
	    intent.putExtra("show_ui", false);
	    pint_show_webpage = PendingIntent.getActivity(context, 0, intent, 0); 	
	}
	if(pint_show_webpage != null) views.setOnClickPendingIntent(R.id.w_addr, pint_show_webpage);	    		

	// Pending intent to update weather when the left part of the widget is clicked
    	if(pint_widget_update == null) {
	    Intent intent = new Intent(context, WidgetProvider.class);
	    intent.setAction(WEATHER_CHANGED_BROADCAST);
	    pint_widget_update = PendingIntent.getBroadcast(context, 0, intent, 0); 
	}
	if(pint_widget_update != null) views.setOnClickPendingIntent(R.id.w_temp, pint_widget_update);	    		

        man.updateAppWidget(wid, views);

    }
}



