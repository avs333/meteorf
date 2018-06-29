package ru.meteoinfo;

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
import android.widget.GridLayout;
import android.widget.RemoteViews;

import java.util.Date;
import java.util.List;

// import android.graphics.Rect;
		
public abstract class WidgetProvider extends AppWidgetProvider {

    public static final String WEATHER_CHANGED_BROADCAST  = "weather_changed";
    public static final String SETTINGS_CHANGED_BROADCAST  = "colours_changed";
    public static final String ACTION_LEFT_BROADCAST  = "action_left";
    public static final String ACTION_RIGHT_BROADCAST  = "action_right";
    public static final String ACTION_RESTART = "action_restart";

    public static final String TAG = "ru.meteoinfo:Widget";

    protected static AppWidgetManager gm = null;

    // handler for enqueuing update requests to avoid race conditions 	
    private final Handler hdl = new Handler();

    protected static boolean large_widget;

    public abstract void set_size();
    public abstract String get_class_name();
    protected abstract void set_click_handlers(Context context);
    protected abstract void weather_update(String action, Context context);
    protected abstract void settings_update(Context context);

    @Override
    public void onEnabled(Context context) {
	super.onEnabled(context);
	set_size();
        Log.d(TAG, "onEnabled, large=" + large_widget);
	startup(context);
    }

    private void startup(Context context) {
	Log.d(TAG, "startup entry");
	App.widget_visible = true;
	gm = AppWidgetManager.getInstance(context);
	Intent i = new Intent(context, Srv.class);
	i.setAction(Srv.WIDGET_STARTED);
	context.startService(i);
	Log.d(TAG, "startup complete");
    }
 
    @Override
    public void onUpdate(Context context, AppWidgetManager man, int[] wids) {
	super.onUpdate(context, man, wids);
        Log.d(TAG, "onUpdate");
	set_click_handlers(context);
	weather_update(WEATHER_CHANGED_BROADCAST, context);
	settings_update(context);
        Log.d(TAG, "onUpdate complete");
    }

    @Override
    public void onReceive(Context context, Intent intent) {	// just enqueue broadcasts
	if(intent == null) {
	    Log.e(TAG, "null intent in onReceive");
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

}


	
