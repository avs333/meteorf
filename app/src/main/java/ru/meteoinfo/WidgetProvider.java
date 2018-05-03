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

    private static final String TAG = "meteoinfo:WidgetProvider";
    private static PendingIntent pint_click = null;
    private static String last_loc_addr = null;
    private static long last_sta_code = -1;

    private final Handler hdl = new Handler();
    private static Context ctx;
    private static AppWidgetManager gm;

    private static int call_count = 0;

    private final Runnable r_loc = new Runnable() {
	public void run() { 
	   location_update(); 
	}
    }; 	    	
    private final Runnable r_wth = new Runnable() {
	public void run() { 
	   weather_update(); 
	}
    }; 	    	

    @Override
    public void onEnabled(Context context) {
        Log.d(TAG, "onEnabled");
	ctx = context;
 	gm = AppWidgetManager.getInstance(ctx);
	Intent i = new Intent(context, Srv.class);
	i.setAction(Srv.WIDGET_STARTED);
	context.startService(i);
        Log.d(TAG, "onEnabled complete");
    }
 
    @Override
    public void onReceive(Context context, Intent intent) {	// just enqueue broadcasts
	if(intent.getAction().equals(LOCATION_CHANGED_BROADCAST)) {
	    hdl.post(r_loc);	
	    Log.d(TAG, "location change queued");
	} else if(intent.getAction().equals(WEATHER_CHANGED_BROADCAST)) {
	    hdl.post(r_wth);	
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
    }

    void weather_update() {
	if(Util.localWeather == null) return;
	WeatherInfo wi = Util.localWeather.for3days.get(0);
	String temp = null, data = null, s = wi.get_temperature();
	if(!s.startsWith("-")) temp = "+" + s;
	else temp = s;
	int pt = temp.indexOf(".");
	if(pt > 0) temp = temp.substring(0, pt);
//	temp += "°C";
	s += call_count;
	call_count++;

/*
	for(int i = 0; i < 2; i++) {
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

    void location_update() {
        new AsyncTask<Void, Void, String>() {
	    @Override
	    protected String doInBackground(Void... params) {
		Log.i(TAG, "background task");
		if(Util.currentLocation == null || Util.currentStation == null) {
		    Log.e(TAG, "background task complete: no current location yet");	
		    return null;
	        }
		String addr = Util.getAddressFromOSM(Util.currentLocation.getLatitude(), 
			Util.currentLocation.getLongitude());
		if(addr != null) {
		    if(last_loc_addr != null && addr.equals(last_loc_addr)) {
			Log.d(TAG, "background task complete: address unchanged");
			return null;
		    }
		    last_loc_addr = addr;
		    if(addr.matches("^\\d+?.*")) addr = "Дом " + addr;
		    if(last_sta_code != Util.currentStation.code) {	
			last_sta_code = Util.currentStation.code;
			pint_click = null;  // must update pint_click with new station code
		    }	
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
			    update_widget(ctx, gm, bound_widgets[i], null, null, addr);
			Log.d(TAG, "location change: widget updated");
		    } else Log.d(TAG, "location change: widget left untouched");
		} catch (Exception e) {
		    Log.e(TAG, "exception in foreground task");
		}
	    }
	}.execute();
    }	

    static void update_widget(Context context, AppWidgetManager man, int wid, 
		String temp, String data, String addr) {

        Log.d(TAG, "update_widget id=" + wid);
	RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);

	if(temp != null) views.setTextViewText(R.id.w_temp, temp);
	if(addr != null) views.setTextViewText(R.id.w_addr, addr);
//	if(data != null) views.setTextViewText(R.id.w_data, data);

	// Pending intent to display a webpage when the widget is clicked
	if(pint_click == null && Util.currentStation != null) {
	    String url =  Util.URL_STA_DATA + "?p=" + Util.currentStation.code;	
	    Intent intent = new Intent(context, WebActivity.class);
	    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	    intent.putExtra("action", url);
	    intent.putExtra("show_ui", false);
	    pint_click = PendingIntent.getActivity(context, 0, intent, 0); 	
	}
	views.setOnClickPendingIntent(R.id.w_addr, pint_click);	    		
        man.updateAppWidget(wid, views);
    }
}



