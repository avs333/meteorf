package ru.meteoinfo;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.RemoteViews;

import android.view.View;
import android.graphics.Rect;

import java.util.Date;
import java.util.List;

import ru.meteoinfo.App;
import ru.meteoinfo.Util;
import ru.meteoinfo.Srv;
import ru.meteoinfo.Util.Station;
import ru.meteoinfo.Util.WeatherData;
import ru.meteoinfo.Util.WeatherInfo;

public class CollectionWidgetProvider extends AppWidgetProvider {

    public static final String WEATHER_CHANGED_BROADCAST  = "weather_changed";
    public static final String SETTINGS_CHANGED_BROADCAST  = "colours_changed";
    public static final String ACTION_LEFT_BROADCAST  = "action_left";
    public static final String ACTION_RIGHT_BROADCAST  = "action_right";
    public static final String ACTION_RESTART = "action_restart";
    public static final String ACTION_SHOW_INFO = "show_info";	
    public static final String ACTION_RESIZE = "action_resize";	

    private static final String TAG = "ru.meteoinfo:CWProvider";
    private static final Object restart_lock = new Object();
    private String last_sta_name = "no such station"; //null;

    @Override	
    public void onEnabled(Context context) {
	super.onEnabled(context);
	startup(context);
    }	
    private void startup(Context context) {   
	Log.d(TAG, "startup entry");
	App.widget_visible = true;
	if(context == null) return;
	Intent i = new Intent(context, ru.meteoinfo.Srv.class);
	if(i == null) return;
	i.setAction(Srv.WIDGET_STARTED);
	context.startService(i);
	Log.d(TAG, "startup complete");
    }
 
    @Override
    public void onUpdate(Context context, AppWidgetManager man,	int[] wids) {
        Log.d(TAG, "onUpdate");
	RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.collection_widget);
	final Intent intent = new Intent(context, CollectionWidgetService.class);
	intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
	rv.setRemoteAdapter(R.id.page_flipper, intent);
	settings_changed(wids, rv);
	final Intent ii = new Intent(context, CollectionWidgetProvider.class);
	ii.setAction(ACTION_RESIZE);
	PendingIntent p = PendingIntent.getBroadcast(context, 0, ii, PendingIntent.FLAG_UPDATE_CURRENT);
	rv.setOnClickPendingIntent(R.id.w_addr, p); 
	man.updateAppWidget(wids, rv);
	super.onUpdate(context, man, wids);
    }

    static int pb = 0;
    final int pmax = 40;

    @Override
    public void onReceive(Context context, Intent intent) {
	if(intent == null || context == null) {
	    Log.e(TAG, "null intent or context in onReceive");
	    return;
	}
	final String action = intent.getAction();
	if(action == null) {
	    Log.e(TAG, "null action in onReceive");
	    return;
	}

	final AppWidgetManager man = AppWidgetManager.getInstance(context);
	final ComponentName cname = new ComponentName(context, CollectionWidgetProvider.class);
	int [] wids = man.getAppWidgetIds(cname);
	if(wids == null || wids.length == 0) {
	    Log.d(TAG, "no bound widgets of class CollectionWidgetProvider");	
	    return;
	}
	RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.collection_widget);

	if((!App.widget_visible && !action.equals(AppWidgetManager.ACTION_APPWIDGET_ENABLED))
		|| action.equals(ACTION_RESTART)) {
	    synchronized(restart_lock) {
		if(!App.widget_visible) Log.e(TAG, action + ": I'm dead, trying to recover");
		else Log.d(TAG, action + ": restarting");	
		startup(context);
		settings_changed(wids, rv);
	    }
	}
	
	Log.d(TAG, "processing " + action);
	switch(action) {

	    case SETTINGS_CHANGED_BROADCAST:
		settings_changed(wids, rv);
		man.partiallyUpdateAppWidget(wids, rv);
		man.notifyAppWidgetViewDataChanged(wids, R.id.page_flipper);
		break;

	    case WEATHER_CHANGED_BROADCAST:
	    case ACTION_RESTART: 
		try {
		    Station st = Srv.getCurrentStation();
		    if(st != null && !st.shortname.equals(last_sta_name)) {
			Log.d(TAG, "station changed, updating widget and PendingIntentTemplate shortname " + st.shortname + ", name=" + st + ", name_p=" + st.name_p);
			last_sta_name = st.shortname;
			rv.setTextViewText(R.id.w_addr, last_sta_name);
			//  rv.setScrollPosition(R.id.page_flipper, 0);
			Intent sintent = new Intent(context, ShowInfo.class);
			sintent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			sintent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));	
			PendingIntent p = PendingIntent.getActivity(context, 0, sintent, 0);
			rv.setPendingIntentTemplate(R.id.page_flipper, p);
			man.partiallyUpdateAppWidget(wids, rv);
		    }
		} catch (Exception e) {
		    Log.d(TAG, "Exception while processing");	
		    e.printStackTrace();	
		}
		// will call RemoteViewsService.RemoteViewsFactory.onDataSetChanged();
		man.notifyAppWidgetViewDataChanged(wids, R.id.page_flipper);
		break;

	    case ACTION_RESIZE:
		pb += 4;
		if(pb > pmax) pb = 0;
		rv.setViewPadding(R.id.page_flipper,0,0,0,pb);
		man.partiallyUpdateAppWidget(wids, rv);
		Log.d(TAG, "resizing");
		break;
	
	/*
	    case ACTION_LEFT_BROADCAST:
		rv = new RemoteViews(context.getPackageName(), R.layout.collection_widget);
		rv.showPrevious(R.id.page_flipper);
		man.partiallyUpdateAppWidget(wids, rv);
		break;

	    case ACTION_RIGHT_BROADCAST:
		rv = new RemoteViews(context.getPackageName(), R.layout.collection_widget);
		rv.showNext(R.id.page_flipper);
		man.partiallyUpdateAppWidget(wids, rv);
		break;	*/
	}
	super.onReceive(context, intent);
    }

    @Override
    public void onDeleted(Context context, int[] wids) {
        Log.d(TAG, "onDeleted");
    } 


    @Override
    public void onDisabled(Context context) {
        Log.d(TAG, "onDisabled");
	App.widget_visible = false;
	Intent i = new Intent(context, ru.meteoinfo.Srv.class);
	i.setAction(Srv.WIDGET_STOPPED);
	context.startService(i);
    }

    private void settings_changed(int [] wids, RemoteViews rv) {
	int bg, fg;
	try {
	    bg = (int) Long.parseLong(Srv.bg_colour, 16);
	} catch(Exception e) { 
	    bg = 0;
	}
	try {
	    fg = (int) Long.parseLong(Srv.fg_colour, 16);
	} catch(Exception e) { 
	    fg = 0xffffffff;
	}
	rv.setTextColor(R.id.w_addr, fg);
	rv.setInt(R.id.w_grid, "setBackgroundColor", bg);
    }

    private static int dp2cells(int size) {
	// return (int)(Math.ceil(size + 30d)/70d);
	int n = 2;
	while(70 * n - 30 < size) n++;
	return n - 1;
    }
/*
float density = context.getResources().getDisplayMetrics().density;
float px = someDpValue * density;
float dp = somePxValue / density;
*/

    @Override	
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager man, int wid, Bundle opt) {

	// portrait; max/min swapped in landscape
	int min_wd = opt.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
	int max_ht = opt.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT);

	int rows = dp2cells(max_ht); 
	
	RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.collection_widget);

	Log.d(TAG, "height changed to " + max_ht + " (" + rows + " " + (rows == 1 ? " row) " : "rows) "));

/*
	View nv = rv.apply(context, null);
	View v = nv.findViewById(R.id.w_addr);

	if(v != null) {
	    Rect rr = v.getClipBounds();
	    int h = v.getHeight();	
	    int w = v.getWidth();
	    int b = v.getBottom();
	    int t = v.getTop();	
	    Log.d(TAG, "view: " +  h + "x" + w + " " + t + ":" + b + " " + v + " clpb=" + rr);
	}

	RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.collection_widget);
	//    updateWidget(context, appWidgetManager, appWidgetId, newOptions);
	man.updateAppWidget(wid, views); 
*/
    }


	
   
}
