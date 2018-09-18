package ru.meteoinfo;

//import android.content.pm.PackageManager;
//import android.os.SystemClock;
//import android.os.AsyncTask;


import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
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
		
public abstract class WidgetProvider extends AppWidgetProvider {

    public static final String WEATHER_CHANGED_BROADCAST  = "weather_changed";
    public static final String SETTINGS_CHANGED_BROADCAST  = "colours_changed";
    public static final String ACTION_LEFT_BROADCAST  = "action_left";
    public static final String ACTION_RIGHT_BROADCAST  = "action_right";
    public static final String ACTION_RESTART = "action_restart";
    private static final String TAG = "ru.meteoinfo:Widget";

    // handler for enqueuing update requests to avoid race conditions 	
    private final Handler hdl = new Handler();

    // you can't create abstact class instances, so "this" should be real
    protected final Class<?> clz = this.getClass();	
    protected final String classname = clz.getName();

    protected abstract void weather_update(Context c, String a, RemoteViews rv);
    protected abstract int getLayout();

    @Override
    public void onEnabled(Context context) {
	super.onEnabled(context);
        Log.d(TAG, "onEnabled");
	startup(context);
    }

    private void startup(Context context) {
	App.widget_visible = true;
	if(context == null) return;
	Intent i = new Intent(context, Srv.class);
	if(i == null) return;
	i.setAction(Srv.WIDGET_STARTED);
	context.startService(i);
    }

    private AppWidgetManager get_man(Context context) {
	AppWidgetManager man = AppWidgetManager.getInstance(context);
	if(man == null) {
	    Log.e(TAG, "no AppWidgetManager");
	    return null;	
	}
	int [] bound_widgets = man.getAppWidgetIds(new ComponentName(context, classname));
	if(bound_widgets == null || bound_widgets.length == 0) {
	    Log.d(TAG, "no bound widgets of class " + classname);	
	    return null;
	}
	return man;
    }

    private void update(Context context, AppWidgetManager man, RemoteViews rv) {
	if(context == null) {
	    Log.e(TAG, "null context in update()");
	    return;
	}
	if(man == null) {
	    Log.e(TAG, "null AppWidget Manager in update()");
	    return;
	}
	if(rv == null) {
	    Log.e(TAG, "null RemoteViews in update()");
	    return;
	}
	man.updateAppWidget(new ComponentName(context, classname), rv);
    }

 
    @Override
    public void onUpdate(Context context, AppWidgetManager man, int[] wids) {
	super.onUpdate(context, man, wids);
        Log.d(TAG, "onUpdate");
	final RemoteViews rv = new RemoteViews(context.getPackageName(), getLayout());
	set_click_handlers(context, rv);
	weather_update(context, WEATHER_CHANGED_BROADCAST, rv);
	settings_update(context, rv);
	update(context, man, rv);
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

	final Context ctx = context;
	final RemoteViews rv = new RemoteViews(context.getPackageName(), getLayout());
	final AppWidgetManager man = get_man(context);

	if(man == null) return;

	if(!App.widget_visible || action.equals(ACTION_RESTART)) {
	    // why do I get APPWIDGET_ENABLED in onReceive()? 
	    // it should fire up onEnabled() instead...
	    if(!action.equals(AppWidgetManager.ACTION_APPWIDGET_ENABLED)) {
		Log.e(TAG, action + ": restarting, visible=" + App.widget_visible);	
		restart(ctx, man, rv);
	    }
	}

	switch(action) {
	    case WEATHER_CHANGED_BROADCAST:
	    case ACTION_LEFT_BROADCAST:	
	    case ACTION_RIGHT_BROADCAST:	
		// Rect r = intent.getSourceBounds();
		// if(r != null) Log.d(TAG, String.format("rect (%d %d %d %d)", r.top, r.left, r.bottom, r.right));
		hdl.post(new Runnable() { 
		    public void run() { 
			set_click_handlers(ctx, rv);
			weather_update(ctx, action, rv); 
			update(ctx, man, rv);
		    }
		});	
		break;
	    case ACTION_RESTART:
		restart(ctx, man, rv);
		break;	
	    case SETTINGS_CHANGED_BROADCAST:
		hdl.post(new Runnable() { 
		    public void run() { 
			settings_update(ctx, rv); 
			update(ctx, man, rv);
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

	
    private void restart(Context context, AppWidgetManager man, RemoteViews rv) {
	Log.d(TAG, "restarting");
	startup(context);
	set_click_handlers(context, rv);
	settings_update(context, rv);
	update(context, man, rv);
    }

    protected void set_click_handlers(Context context, RemoteViews rv) {

	Station st = Srv.getCurrentStation();
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
	    rv.setOnClickPendingIntent(R.id.w_info, pint);
	}

	intent = new Intent(context, ru.meteoinfo.WeatherActivity.class);
	intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	pint = PendingIntent.getActivity(context,0,intent,0);	
	rv.setOnClickPendingIntent(R.id.w_temp, pint);

	intent = new Intent(context, clz);
	intent.setAction(ACTION_LEFT_BROADCAST);	
	pint = PendingIntent.getBroadcast(context,0,intent,0);	
	rv.setOnClickPendingIntent(R.id.w_left, pint);

	intent = new Intent(context, clz);
	intent.setAction(ACTION_RIGHT_BROADCAST);	
	pint = PendingIntent.getBroadcast(context,0,intent,0);	
	rv.setOnClickPendingIntent(R.id.w_right, pint);
    }

    protected void settings_update(Context context, RemoteViews rv) {	

	int fg, bg;
	try {
	    fg = (int) Long.parseLong(Srv.fg_colour, 16);
	} catch(Exception e) { 
	    fg = 0xffffffff;
	    Log.e(TAG, "fg: NumberFormatException in settings_update()" + fg);
	}

	try {
	    bg = (int) Long.parseLong(Srv.bg_colour, 16);
	} catch(Exception e) { 
	    bg = 0;
	    Log.e(TAG, "bg: NumberFormatException in settings_update()" + bg);
	}

	Log.d(TAG, String.format("settings_update: fg=%08x bg=%08x sta=" + Srv.wd_show_sta, fg, bg));
	Station st = Srv.getCurrentStation();
	String addr = null;
	String va[] = null;
	if(st != null) {
	    if(Srv.wd_show_sta) addr = String.format(App.get_string(R.string.wd_sta_short), st.code);
	    else addr = st.shortname;
	}

	Log.d(TAG, "show_sta=" + Srv.wd_show_sta + ", addr=" + addr);
	rv.setTextColor(R.id.w_temp, fg);
	rv.setTextColor(R.id.w_addr, fg);
	rv.setTextColor(R.id.w_date, fg);
	rv.setTextColor(R.id.w_time, fg);
	rv.setInt(R.id.w_grid, "setBackgroundColor", bg);

	if(addr != null) rv.setTextViewText(R.id.w_addr, addr);

	Log.d(TAG, "settings_update: updating widgets for " + classname);
    }

}


	
