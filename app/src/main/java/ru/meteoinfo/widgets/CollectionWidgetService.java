package ru.meteoinfo.widgets;

//import me.sunphiz.adapterviewflipperwidget.R;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.util.ArrayList;

import ru.meteoinfo.App;
import ru.meteoinfo.Util;
import ru.meteoinfo.Srv;
import ru.meteoinfo.Util.WeatherData;
import ru.meteoinfo.Util.WeatherInfo;
import ru.meteoinfo.R;

public class CollectionWidgetService extends RemoteViewsService {

    private static final String TAG = "ru.meteoinfo:CWService";

   @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
	Log.d(TAG, "onGetViewFactory()");
	return new ViewFactory(this.getApplicationContext(), intent);
    }

    private class ViewFactory implements RemoteViewsService.RemoteViewsFactory {
		
        private Context context;
        private static final int max_widx = 12;
	private int count = 0;
	private ArrayList <WeatherInfo> wi_list = null;
	
	public ViewFactory(Context ctx, Intent intent) {
	    context = ctx;
	}

	@Override
	public void onDataSetChanged() {

	    Log.i(TAG, "onDataSetChanged()");
	    WeatherData wd = Srv.getLocalWeather();

	    if(wd == null) {
		Log.e(TAG, "localWeather unknown or invalid weather for 3 days");
		count = 0;
		return;
	    }	

	    wi_list = new ArrayList<WeatherInfo>();
	    long now = System.currentTimeMillis();
	    WeatherInfo wi;

	    if(wd.observ != null) {
		wi_list.add(wd.observ);
		count = 1;
	    } else count = 0;	

	    while(wd.for3days != null) {
		wi = wd.for3days.get(0);
		if(wi == null || now <= wi.get_utc()) break;
		Log.d(TAG, "weather_update: removing stale data for " + wi.get_date());
		wd.for3days.remove(0);
	    }

	    int k = (wd.for3days != null) ? wd.for3days.size() : 0;

	    if(k > 0) {
		if(k > max_widx) k = max_widx;
		for(int i = 0; i < k; i++) wi_list.add(wd.for3days.get(i));
		count += k;
	    }			
	    Log.i(TAG, "onDataSetChanged: count=" + count);	
	}

	@Override
	public int getCount() {
	    Log.i(TAG, "getCount() return " + count);
	    return count;
	}

	@Override
	public RemoteViews getViewAt(int position) {
	    if(count == 0 || position >= count || wi_list == null) return null;
	    RemoteViews views = new RemoteViews(getPackageName(), R.layout.collection_widget_item);
	    WeatherInfo wi = wi_list.get(position);

	    String icon_nm = wi.get_icon_name();
	    if(icon_nm == null) icon_nm = "ru.meteoinfo:drawable/no_data";	
	    int id = context.getResources().getIdentifier(icon_nm, null, null);
	    views.setImageViewResource(R.id.w_info, id);
	
	    String temp = null;
	    double val = wi.get_temperature();
	    if(val == Util.inval_temp) temp = "????";
	    else temp = String.format(java.util.Locale.US, "+%.1f°C", val);
	    views.setTextViewText(R.id.w_temp, temp);

	    String date = wi.get_date();
	    views.setTextViewText(R.id.w_date, date);

	    String time = wi.get_time();
	    views.setTextViewText(R.id.w_time, time);

	    int fg;
	    try {
		fg = (int) Long.parseLong(Srv.fg_colour, 16);
	    } catch(Exception e) { 
		fg = 0xffffffff;
	    }
	    views.setTextColor(R.id.w_temp, fg);
	    views.setTextColor(R.id.w_date, fg);
	    views.setTextColor(R.id.w_time, fg);

	    Intent fill_in = new Intent();
/*
	    Bundle extras = new Bundle();	
	    extras.putInt("ru.meteoinfo.EXTRA_ITEM", position);
	    fill_in = new Intent();
	    views.setOnClickFillInIntent(R.id.collection_widget_item, fill_in);
Replaced by:
*/
//	    fill_in  = new Intent(context,  ru.meteoinfo.WebActivity.class);
//	    views.setOnClickFillInIntent(R.id.w_info, fill_in);
	    fill_in  = new Intent(context,  ru.meteoinfo.WeatherActivity.class);
//	    views.setOnClickFillInIntent(R.id.w_temp, fill_in);
	    views.setOnClickFillInIntent(R.id.collection_widget_item, fill_in);
  		
	    return views;
	}

	@Override
	public int getViewTypeCount() {
	    return 1;
	}

	@Override
	public long getItemId(int position) {
	    return position;
	}

	@Override
	public boolean hasStableIds() {
	    return true;
	}

	@Override
	public void onCreate() {
	}

	@Override
	public void onDestroy() {
	    if(wi_list != null)	wi_list.clear();
	}

	@Override
	public RemoteViews getLoadingView() {
	    return null;
	}


    }

}
