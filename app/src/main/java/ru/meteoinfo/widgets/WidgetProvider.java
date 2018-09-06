package ru.meteoinfo.widgets;

//import android.content.pm.PackageManager;
//import android.os.SystemClock;
//import android.os.AsyncTask;


import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.widget.RemoteViews;
import android.app.PendingIntent;

import java.util.Date;
import java.util.List;

import ru.meteoinfo.App;
import ru.meteoinfo.Util;
import ru.meteoinfo.Srv;
import ru.meteoinfo.Util.Station;
import ru.meteoinfo.Util.WeatherData;
import ru.meteoinfo.Util.WeatherInfo;
import ru.meteoinfo.R;

		
public abstract class WidgetProvider extends AppWidgetProvider {

    public static final String WEATHER_CHANGED_BROADCAST  = "weather_changed";
    public static final String SETTINGS_CHANGED_BROADCAST  = "colours_changed";
    public static final String ACTION_LEFT_BROADCAST  = "action_left";
    public static final String ACTION_RIGHT_BROADCAST  = "action_right";
    public static final String ACTION_RESTART = "action_restart";
    private static final String TAG = "ru.meteoinfo:Widget";

    protected static AppWidgetManager gm = null;

    // handler for enqueuing update requests to avoid race conditions 	
    private final Handler hdl = new Handler();

    // you can't create abstact class instances, so "this" should be real
    protected final Class<?> clz = this.getClass();	
    protected final String classname = clz.getName();

    protected abstract void weather_update(String action, Context context);
    protected abstract void settings_update(Context context);
    protected abstract RemoteViews get_views(Context context);
   
    int layout_id = 7890;

    @Override
    public void onEnabled(Context context) {
	super.onEnabled(context);
        Log.d(TAG, "onEnabled");
	this.layout_id = 123456;
	Log.d(TAG, "layout_id=" + layout_id + ", classname=" + classname);
	startup(context);
    }

    private void startup(Context context) {
	App.widget_visible = true;
	if(context == null) return;
	gm = AppWidgetManager.getInstance(context);
	Intent i = new Intent(context, Srv.class);
	if(i == null) return;
	i.setAction(Srv.WIDGET_STARTED);
	context.startService(i);
    }
 
    @Override
    public void onUpdate(Context context, AppWidgetManager man, int[] wids) {
	super.onUpdate(context, man, wids);
        Log.d(TAG, "onUpdate");
	Log.d(TAG, "layout_id=" + layout_id + ", classname=" + classname);
	set_click_handlers(context);
	weather_update(WEATHER_CHANGED_BROADCAST, context);
	settings_update(context);
        Log.d(TAG, "onUpdate complete");
    }

    @Override
    public void onReceive(Context context, Intent intent) {	// just enqueue broadcasts
	if(intent == null || context == null) {
	    Log.e(TAG, "null intent or context in onReceive");
	    return;
	}
	final String action = intent.getAction();
	if(action == null) {
	    Log.e(TAG, "null action in onReceive");
	    return;
	}
	if(!App.widget_visible) {
	    // why do I get APPWIDGET_ENABLED in onReceive()? 
	    // it should fire up onEnabled() instead...
	    if(!action.equals(AppWidgetManager.ACTION_APPWIDGET_ENABLED)) {
		Log.e(TAG, action + ": I'm dead, trying to recover");	
		startup(context);
		set_click_handlers(context);
		settings_update(context);
	    }
	}

	final Context cc = context;

	switch(action) {
	    case WEATHER_CHANGED_BROADCAST:
	    case ACTION_LEFT_BROADCAST:	
	    case ACTION_RIGHT_BROADCAST:	
		// Rect r = intent.getSourceBounds();
		// if(r != null) Log.d(TAG, String.format("rect (%d %d %d %d)", r.top, r.left, r.bottom, r.right));
		hdl.post(new Runnable() { 
		    public void run() { 
			weather_update(action, cc); 
		    }
		});	
//		Log.d(TAG, "weather change queued for " + action);
		break;
	    case ACTION_RESTART:
		restart(cc);
		break;	
	    case SETTINGS_CHANGED_BROADCAST:
		hdl.post(new Runnable() { 
		    public void run() { 
			settings_update(cc); 
		    }
		});	
		break;
	    default:		
		super.onReceive(context, intent);
		//  Log.e(TAG, "spurious action in onRecieve" + action);
		break;
	}
    }

    @Override
    public void onDeleted(Context context, int[] wids) {
        Log.d(TAG, "onDeleted");
    } 


    @Override
    public void onDisabled(Context context) {
        Log.d(TAG, "onDisabled");
	App.widget_visible = false;
	Intent i = new Intent(context, Srv.class);
	i.setAction(Srv.WIDGET_STOPPED);
	context.startService(i);
    }

	
    private void restart(Context context) {
	Log.d(TAG, "restarting");
	startup(context);
	set_click_handlers(context);
	settings_update(context);
    }

    protected void set_click_handlers(Context context) {

	Station st = Srv.getCurrentStation();
	RemoteViews views = get_views(context);
	Intent intent;
	PendingIntent pint;

	if(st != null) {
	    String url =  Util.URL_STA_DATA + "?p=" + st.code;	
	    intent = new Intent(context, ru.meteoinfo.WebActivity.class);
	    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	    intent.putExtra("action", url);
	    intent.putExtra("show_ui", false);
	    if(st.name_p != null) intent.putExtra("title", st.name_p);
	    pint = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT); 	
	    views.setOnClickPendingIntent(R.id.w_info, pint);
	}

	intent = new Intent(context, ru.meteoinfo.WeatherActivity.class);
	intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	pint = PendingIntent.getActivity(context,0,intent,0);	
	views.setOnClickPendingIntent(R.id.w_temp, pint);

	intent = new Intent(context, clz);
	intent.setAction(ACTION_LEFT_BROADCAST);	
	pint = PendingIntent.getBroadcast(context,0,intent,0);	
	views.setOnClickPendingIntent(R.id.w_left, pint);

	intent = new Intent(context, clz);
	intent.setAction(ACTION_RIGHT_BROADCAST);	
	pint = PendingIntent.getBroadcast(context,0,intent,0);	
	views.setOnClickPendingIntent(R.id.w_right, pint);
    }

}


	
