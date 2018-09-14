package ru.meteoinfo;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.RemoteViews;

import ru.meteoinfo.App;
import ru.meteoinfo.Util;
import ru.meteoinfo.Srv;
import ru.meteoinfo.Util.Station;
import ru.meteoinfo.Util.WeatherData;
import ru.meteoinfo.Util.WeatherInfo;
	
public class WidgetLarge2 extends WidgetProvider {

    private static final String TAG = "ru.meteoinfo:WLarge2";
    RemoteViews views = null;

    @Override
    protected RemoteViews get_views(Context context) {
	if(views != null) return views;
	return new RemoteViews(context.getPackageName(), R.layout.widget_layout_large2);
    }

    @Override
    protected void settings_update(Context context) {	

	if(gm == null) gm = AppWidgetManager.getInstance(context);
	int [] bound_widgets = gm.getAppWidgetIds(new ComponentName(context, classname));
	if(bound_widgets == null || bound_widgets.length == 0) {
	    Log.d(TAG, "no bound widgets of class " + classname);	
	    return;
	}

	views = get_views(context);

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
	views.setTextColor(R.id.w_temp, fg);
	views.setTextColor(R.id.w_addr, fg);
	views.setTextColor(R.id.w_date, fg);
	views.setTextColor(R.id.w_time, fg);
	views.setInt(R.id.w_grid, "setBackgroundColor", bg);

	if(addr != null) views.setTextViewText(R.id.w_addr, addr);

	Log.d(TAG, "settings_update: updating " + bound_widgets.length + " " + classname);
	for(int i = 0; i < bound_widgets.length; i++) gm.updateAppWidget(bound_widgets[i], views);
    }

    private static String windDir(double deg) {
        try {
            String[] directions = App.getContext().getResources().getStringArray(R.array.short_wind_dirs);
            return directions[(int)Math.round(((deg % 360) / 45))];
        } catch (Exception e) {
            Log.e(TAG, "invalid degrees " + deg);      
            return null;        
        }
    }

    protected static int widx = 0;
    protected final int max_widx = 12;
    private int max;

    @Override
    protected void weather_update(String action, Context context) {

	if(context == null) { 
	    Log.e(TAG, "weather_update: no context");
	    return;
	}

	if(gm == null) gm = AppWidgetManager.getInstance(context);
	if(gm == null) return;
	int [] bound_widgets = gm.getAppWidgetIds(new ComponentName(context, classname));
	if(bound_widgets == null || bound_widgets.length == 0) {
	    Log.d(TAG, "no bound widgets of class " + classname);	
	    return;
	}
	views = get_views(context);
	if(views == null) { 
	    Log.e(TAG, "weather_update: no views");
	    return;
	}
	WeatherData wd = Srv.getLocalWeather();

	if(wd == null) {
	    Log.e(TAG, "weather_update: localWeather unknown");
	    return;
	}

	Station st = Srv.getCurrentStation();
	String addr = null;

/*	if(st != null) {
	    if(Srv.wd_show_sta) addr = String.format(App.get_string(R.string.wd_sta_short), st.code);
	    else addr = st.shortname;
	} else Log.d(TAG, "weather_update: no current station yet");
*/
	if(st != null) addr = st.shortname;

	set_click_handlers(context);

	long now = System.currentTimeMillis();

	WeatherInfo wi = null;

/*
	while((wi = wd.for3days.get(0)) != null) {
	    wi = wd.for3days.get(0);
	    if(wi == null) break;	
	    if(!wi.valid) {
	        wd.for3days.remove(0);
	        if(wd.for3days.size() == 0) break;
	        if(widx > 0) widx--;
		continue;
	    }
	    if(now <= wi.get_utc()) break;
	    Log.d(TAG, "weather_update: removing stale data for " + wi.get_date());
	    wd.for3days.remove(0);
	    if(wd.for3days.size() == 0) break;
	    if(widx > 0) widx--;
	}

	int max = wd.for3days.size() - 1;
	if(max < 0) {
	    Log.e(TAG, "weather_update: no weather to display");
	    return;
	}
	if(max > max_widx) max = max_widx;

	switch(action) {
	    case WEATHER_CHANGED_BROADCAST:
		widx = 0;
		wi = wd.for3days.get(widx);
		break; 
	    case ACTION_LEFT_BROADCAST:	
		widx--;
		if(widx < -1 || (widx < 0 && wd.observ == null)) widx = max;
		wi = (widx < 0) ? wd.observ : wd.for3days.get(widx);
		break;
	    case ACTION_RIGHT_BROADCAST:	
		widx++;
		if(widx > max) widx = (wd.observ != null) ? -1 : 0;
		wi = (widx < 0) ? wd.observ : wd.for3days.get(widx);
		break;
	    default:
		wi = null;
		break;
	}

	if(wi == null) {
	    Log.e(TAG, "null WeatherInfo at index " + widx);
	    return;
	}

*/
	if(wd.for3days == null || wd.for3days.size() == 0) {
	    wi = wd.observ;
	    Log.d(TAG, "no forecasts");
	    if(!action.equals(WEATHER_CHANGED_BROADCAST)) {
		Log.d(TAG, "action " + action + " ignored");
		return;
	    }	
	} else {
	    while((wi = wd.for3days.get(0)) != null) {
		if(!wi.valid) {
		    wd.for3days.remove(0);
		    if(wd.for3days.size() == 0) break;
		    if(widx > 0) widx--;
		    continue;
		}
		if(now <= wi.get_utc()) break;
		Log.d(TAG, "weather_update: removing stale data for " + wi.get_date());
		wd.for3days.remove(0);
		if(wd.for3days.size() == 0) break;
		if(widx > 0) widx--;
	    }

	    max = wd.for3days.size() - 1;
	    if(max > max_widx) max = max_widx;

	    if(max < 0) {
		wi = wd.observ;
		widx = -1;
	    } else switch(action) {
		case WEATHER_CHANGED_BROADCAST:
		    if(wd.observ != null) {
			widx = -1;
			wi = wd.observ;
		    } else {	
			widx = 0;
			wi = wd.for3days.get(widx);
		    }
		    break; 
		case ACTION_LEFT_BROADCAST:	
		    widx--;
		    if(widx < -1 || (widx < 0 && wd.observ == null)) widx = max;
		    wi = (widx < 0) ? wd.observ : wd.for3days.get(widx);
		    break;
		case ACTION_RIGHT_BROADCAST:	
		    widx++;
		    if(widx > max) widx = (wd.observ != null) ? -1 : 0;
		    wi = (widx < 0) ? wd.observ : wd.for3days.get(widx);
		    break;
		default:
		    wi = null;
		    break;
	    }
	}

	if(wi == null) {
	    Log.e(TAG, "no weather to display");
	    return;
	}


	Log.d(TAG, "weather_update: " + action + " addr=" + addr + ", widx=" + widx + ", max=" + max);

	String icon_nm = wi.get_icon_name();
	if(icon_nm == null) {
	    Log.e(TAG, "icon not found");
	    icon_nm = "ru.meteoinfo:drawable/no_data";	
	}
	int id = context.getResources().getIdentifier(icon_nm, null, null);	
	Log.d(TAG, "icon_name=" + icon_nm + ", id=" + id);
	views.setImageViewResource(R.id.w_info, id);	

	String time = wi.get_time();
	if(time != null && widx >= 0) time = "[" + time + "]";
	String date = wi.get_date();

//	Log.d(TAG, "weather_update: date=" + date);

	String temp = null;
	double val = wi.get_temperature();

	if(val == Util.inval_temp) {
	    Log.e(TAG, "weather_update: invalid temperature");	
	    return;
	}

	temp = String.format(java.util.Locale.US, "+%.1f", val);
//	Log.d(TAG, "weather_update: temp=" + temp);

	String press = null;
	val = wi.get_pressure();

	if(val != -1) {	 
	    try {
		press = String.format(App.get_string(R.string.wd_pressure_short_hg), (int) Math.round(val));
	    } catch(Exception e) {
		Log.e(TAG, "error parsing pressure " + val);
		press = null;
	    }
	}

//	Log.d(TAG, "weather_update: pressure=" + press);

	String wind = null;

	double wind_dir = wi.get_wind_dir();
	double wind_speed = wi.get_wind_speed();

	if(wind_dir != -1 && wind_speed != -1) 
	    wind = String.format(App.get_string(R.string.wd_wind_short), windDir(wind_dir), wind_speed);

//	Log.d(TAG, "weather_update: wind=" + wind);

	String precip = null;
	val = wi.get_precip();

	if(precip == null && widx == -1) {
	    val = wi.get_precip3h();
	    if(val == -1) val = wi.get_precip6h(); 
	    if(val == -1) val = wi.get_precip12h();
	}

	if(val != -1) precip = String.format(App.get_string(R.string.wd_precip_short), val);

//	Log.d(TAG, "weather_update: precip=" + precip);

	final String filler = "_______________";

	String hum = null;
	val = wi.get_humidity();
	if(val != -1) hum = String.format(App.get_string(R.string.wd_humidity_short), val);

//	Log.d(TAG, "weather_update: humidity=" + hum);

	/* pressure is often missing in observ data; move humidity string to its position then */
	if(press == null && hum != null) {
	    press = hum;
	    hum = filler;	
	}

	if(wind == null) wind = filler;
	if(precip == null) precip = filler;
	if(hum == null) hum = filler;


	if(addr != null) views.setTextViewText(R.id.w_addr, addr);
	if(temp != null) views.setTextViewText(R.id.w_temp, temp);
	if(date != null) views.setTextViewText(R.id.w_date, date);
	if(time != null) views.setTextViewText(R.id.w_time, time);
	if(press != null) views.setTextViewText(R.id.w_pressure, press);
	if(wind != null) views.setTextViewText(R.id.w_wind, wind);
	if(precip != null) views.setTextViewText(R.id.w_precip, precip);
	if(hum != null) views.setTextViewText(R.id.w_humidity, hum);


	Log.d(TAG, "weather_update: updating " + bound_widgets.length + " " + classname);
	for(int i = 0; i < bound_widgets.length; i++) gm.updateAppWidget(bound_widgets[i], views);
    }

}

