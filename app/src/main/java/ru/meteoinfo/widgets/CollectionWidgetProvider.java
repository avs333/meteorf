package ru.meteoinfo.widgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.RemoteViews;

import java.util.Date;
import java.util.List;

import ru.meteoinfo.App;
import ru.meteoinfo.Util;
import ru.meteoinfo.Srv;
import ru.meteoinfo.Util.Station;
import ru.meteoinfo.Util.WeatherData;
import ru.meteoinfo.Util.WeatherInfo;
import ru.meteoinfo.R;

public class CollectionWidgetProvider extends AppWidgetProvider {

    public static final String WEATHER_CHANGED_BROADCAST  = "weather_changed";
    public static final String SETTINGS_CHANGED_BROADCAST  = "colours_changed";
    public static final String ACTION_LEFT_BROADCAST  = "action_left";
    public static final String ACTION_RIGHT_BROADCAST  = "action_right";
    public static final String ACTION_RESTART = "action_restart";

    private static final String TAG = "ru.meteoinfo:CWProvider";
    private static final Object restart_lock = new Object();
    private static String last_sta_name = "no such station"; //null;

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
/*	
	for(int id : wids) {
	    RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.collection_widget);
	    // Specify the service to provide data for the collection widget.
	    // Note that we need to
	    // embed the appWidgetId via the data otherwise it will be ignored.
	    final Intent intent = new Intent(context, CollectionWidgetService.class);
	    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
	    intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
	    rv.setRemoteAdapter(R.id.page_flipper, intent);
	    man.updateAppWidget(id, rv);
	} */

	RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.collection_widget);
	final Intent intent = new Intent(context, CollectionWidgetService.class);
	intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
	rv.setRemoteAdapter(R.id.page_flipper, intent);
	settings_changed(wids, rv);
	man.updateAppWidget(wids, rv);

	// set_click_handlers(context, rv, id);
	// weather_update(WEATHER_CHANGED_BROADCAST, context);
	// settings_update(context);
	super.onUpdate(context, man, wids);
    }

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
		Log.e(TAG, action + ": I'm dead, trying to recover");	
		startup(context);
		settings_changed(wids, rv);
	    }
	}
	
	Log.d(TAG, "processing " + action);
	switch(action) {
	    case SETTINGS_CHANGED_BROADCAST:
		settings_changed(wids, rv);
		man.partiallyUpdateAppWidget(wids, rv);
		break;
	    case WEATHER_CHANGED_BROADCAST:
	    case ACTION_RESTART: 
		try {
		Station st = Srv.getCurrentStation();
		if(st != null && !st.shortname.equals(last_sta_name)) {
		    Log.d(TAG, "station changed, updating widget and PendingIntentTemplate");
		    last_sta_name = st.shortname;
		    rv.setTextViewText(R.id.w_addr, last_sta_name);
		//  rv.setScrollPosition(R.id.page_flipper, 0);		/* listview only? */
		
		    Intent sintent;
		    PendingIntent ptt;	
/*
		    String url =  Util.URL_STA_DATA + "?p=" + st.code;	
		    sintent = new Intent(context, ru.meteoinfo.WebActivity.class);
		    sintent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		    sintent.putExtra("action", url);
		    sintent.putExtra("show_ui", false);
		    sintent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));	
		    if(st.name_p != null) sintent.putExtra("title", st.name_p);
		    ptt =  PendingIntent.getActivity(context, 0, sintent, PendingIntent.FLAG_UPDATE_CURRENT);
		    rv.setPendingIntentTemplate(R.id.page_flipper, ptt);
*/
		    sintent = new Intent(context, ru.meteoinfo.WeatherActivity.class);
		    sintent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		    sintent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));	
		    sintent.putExtra("open_drawer", true);
		    ptt = PendingIntent.getActivity(context, 0, sintent, 0);
		    rv.setPendingIntentTemplate(R.id.page_flipper, ptt);

		    man.partiallyUpdateAppWidget(wids, rv);
		} else {
		    Log.d(TAG, "station is " + st);
		    if(st != null) Log.d(TAG, "shortname is " + st.shortname);
		    break;	
		}
		} catch (Exception e) {
		    Log.d(TAG, "Exception while processing");	
		    e.printStackTrace();	
		}
		// will call RemoteViewsService.RemoteViewsFactory.onDataSetChanged();
		man.notifyAppWidgetViewDataChanged(wids, R.id.page_flipper);
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
   
}
