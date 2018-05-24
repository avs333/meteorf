package ru.meteoinfo;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

import ru.meteoinfo.Util.Station;
import ru.meteoinfo.Util.WeatherData;
import ru.meteoinfo.Util.WeatherInfo;
import static ru.meteoinfo.WeatherActivity.*;

public class DataActivity extends AppCompatActivity {

    private static String TAG = "ru.meteoinfo:DataActivity";

    private static void log_msg(String msg) {
        Log.i(TAG, msg);
    }
    private static void log_err(String msg) {
        Log.e(TAG, msg);
    }

    private static String meteodata = null;
    private static TextView etext;
    private static boolean local = false;     
    private static WeatherData wdata = null;	

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if(curStation == null) {
            Toast.makeText(this, getString(R.string.no_station), Toast.LENGTH_LONG);
            finish();
            return;
        }
        setContentView(R.layout.activity_data_select);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        FloatingActionButton fab = findViewById(R.id.fab);

        Intent intent = getIntent();
        local = intent.getBooleanExtra("local", false);

        log_msg("local station=" + local);

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
              /*  Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show(); */
                if(meteodata != null) {
                    Intent sendIntent = new Intent();
                    sendIntent.setAction(Intent.ACTION_SEND);
                    sendIntent.putExtra(Intent.EXTRA_TEXT,
			"<body><html>" +  meteodata + "</body></html>");
                    sendIntent.setType("text/html");
                    startActivity(sendIntent);
                }
            }
        });

        etext = findViewById(R.id.etext);

        Runnable bgr = new Runnable() {
            @Override
            public void run() {
	/*	wdata = null;
		log_msg("getting weather for " + curStation.code);
		if(local) {
		    log_msg("local");
		    wdata = Srv.getLocalWeather();
		} else log_msg("non-local"); 
		if(wdata == null) 
		*/
		log_msg("getting weather for " + curStation.code);
		wdata = Util.getWeather(curStation.code);
		log_msg("getting weather for " + curStation.code + " " + ((wdata == null) ? "failed" : "succeeded"));
		if(wdata != null) meteodata = parseWeatherData(wdata);
		else meteodata = null;
            }
        };
        Runnable fgr = new Runnable() {
            @Override
            public void run() {
                if(meteodata != null && meteodata.length() != 0) 
                    etext.setText(Html.fromHtml(meteodata));
		else etext.setText(Html.fromHtml("<h1><font color=#C00000>" + 
			getString(R.string.no_data_avail) + "</font></h1>"));
            }
        };
        AsyncTaskWithProgress atp = new AsyncTaskWithProgress(this, getString(R.string.receiving_data), bgr, fgr);
        atp.execute();

    }

    private static String windDir(String degrees) {
	try {
	    double deg = Double.parseDouble(degrees);	
	    String[] directions = App.getContext().getResources().getStringArray(R.array.wind_dirs);
            return directions[(int)Math.round(((deg % 360) / 45))];
	} catch (Exception e) {
	    log_err("invalid degrees " + degrees);	
	    return null;	
	}
    }

    private String parseWeatherInfo(WeatherInfo wi) {

	String s = "", st, st1, st2, st3;

	    st = wi.get_date();
	    if(st != null) s += "<br><p><h2><i>" + st + "</i></h2>";

	    st = wi.get_temperature();
	    if(st != null) s += String.format(getString(R.string.wd_temperature), st) + "<br>";

	    st = wi.get_pressure();
	    if(st != null) s += String.format(getString(R.string.wd_pressure), st) + "<br>";

	    st = wi.get_wind_dir();
	    st2 = wi.get_wind_speed();
	    st3 = wi.get_gusts();	

	    if(st != null && st2 != null) {
	        st1 = windDir(st);
		if(st3 != null) s += String.format(getString(R.string.wd_wind_gusts), st1, st, st2, st3);
		else s += String.format(getString(R.string.wd_wind_nogusts), st1, st, st2);
		s += "<br>";
	    }

	    st = wi.get_info();
	    if(st != null) s += st + "<br>";

	    st = wi.get_precip();  		    
	    if(st != null) {
		if(wi.gettype() == Util.WEATHER_REQ_7DAY) st3 = getString(R.string.wd_precip_nd);
		else st3 = getString(R.string.wd_precip1h);		
		s += String.format(st3, st) + "<br>";
	    }	

	    if(wi.gettype() == Util.WEATHER_REQ_7DAY) return s;	// no more data for 7-day forecast
	    else if(wi.gettype() == Util.WEATHER_REQ_3DAY) {
		st = wi.get_humidity();
		if(st != null) s += String.format(getString(R.string.wd_humidity), st) + "<br>";
		return s;				// no more data for 3-day forecast
	    }	
	    st = wi.get_precip3h();  		    
	    if(st != null) s += String.format(getString(R.string.wd_precip3h), st) + "<br>";

	    st = wi.get_precip6h();  		    
	    if(st != null) s += String.format(getString(R.string.wd_precip6h), st) + "<br>";

	    st = wi.get_precip12h();  		    
	    if(st != null) s += String.format(getString(R.string.wd_precip12h), st) + "<br>";

	    st = wi.get_humidity();
	    if(st != null) s += String.format(getString(R.string.wd_humidity), st) + "<br>";

	    st = wi.get_visibility();
	    if(st != null) s += String.format(getString(R.string.wd_visibility), st) + "<br>";

	    st = wi.get_clouds();
	    if(st != null) s += String.format(getString(R.string.wd_clouds), st) + "<br>";

	return s;
	     	
    }

    private static final String separator = "<p>";

    private String parseWeatherData(WeatherData wd) {

        WeatherInfo wi;
        String s = "", st;

/*	if(wd.observ != null && wd.observ.valid) { 
	    s += "<b>" + getString(R.string.observ_data) + "</b>" + separator;	
	    st = parseWeatherInfo(wd.observ);
	    if(st != null) {
		s += st; s += separator;
	    } 		
	    s += separator;
	}
*/

/*
	if(wd.for7days != null) {
	    s += getString(R.string.weekly_data) + "\n";	
	    for(int i = 0; i < wd.for7days.size(); i++) {
		wi = wd.for7days.get(i);
		if(wi == null || !wi.valid) continue;
		st = parseWeatherInfo(wi);
		if(st != null) {
		    s += st; s += separator;
		} 		
	    }
	    s += separator;
	}
*/
	if(wd.for3days != null) {
//	    s += "<b>" + getString(R.string.daily_data) + "</b>" + separator;	
	    for(int i = 0; i < wd.for3days.size(); i++) {
		wi = wd.for3days.get(i);
		if(wi == null || !wi.valid) continue;
		st = parseWeatherInfo(wi);
		if(st != null) {
		    s += st; s += separator;
		} 		
	    }
	}

	return s;
    } 	

}
