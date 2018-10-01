package ru.meteoinfo;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.RemoteViews;

import ru.meteoinfo.Util;
import ru.meteoinfo.Srv;
import ru.meteoinfo.Util.Station;
import ru.meteoinfo.Util.WeatherData;
import ru.meteoinfo.Util.WeatherInfo;
	
public class WidgetLarge2 extends WidgetProvider {

    private static final String TAG = "ru.meteoinfo:WLarge2";

    @Override
    protected int getLayout() {
	return R.layout.widget_layout_large2;
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
    protected void weather_update(Context context, String action, RemoteViews rv) {

	WeatherData wd = Srv.getLocalWeather();

	if(wd == null) {
	    Log.e(TAG, "weather_update: localWeather unknown");
	    return;
	}

	Station st = Srv.getCurrentStation();
	String addr = null;

	if(st != null) addr = st.shortname;

	long now = System.currentTimeMillis();

	WeatherInfo wi = null;

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
	rv.setImageViewResource(R.id.w_info, id);	

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

	temp = String.format(java.util.Locale.US, "%+.1f", val);
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

	final String filler = " ";

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


	if(addr != null) rv.setTextViewText(R.id.w_addr, addr);
	if(temp != null) rv.setTextViewText(R.id.w_temp, temp);
	if(date != null) rv.setTextViewText(R.id.w_date, date);
	if(time != null) rv.setTextViewText(R.id.w_time, time);
	if(press != null) rv.setTextViewText(R.id.w_pressure, press);
	if(wind != null) rv.setTextViewText(R.id.w_wind, wind);
	if(precip != null) rv.setTextViewText(R.id.w_precip, precip);
	if(hum != null) rv.setTextViewText(R.id.w_humidity, hum);

	Log.d(TAG, "weather_update: updating weather for " + classname);
    }

}

