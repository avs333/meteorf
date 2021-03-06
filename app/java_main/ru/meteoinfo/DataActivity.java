package ru.meteoinfo;

import android.content.Intent;
import android.net.Uri;
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
            Toast.makeText(this, App.get_string(R.string.no_station), Toast.LENGTH_LONG).show();
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

		try {
/*                  Intent sendIntent = new Intent();
                    sendIntent.setAction(Intent.ACTION_SEND); */
		    Intent sendIntent = new Intent(Intent.ACTION_SEND, Uri.parse("mailto:"));
		    sendIntent.setType("text/html");
			//	Uri.fromParts("mailto", "mygmail.com", null));	
		    sendIntent.putExtra(Intent.EXTRA_SUBJECT, "Meteoinfo.ru data");
                    sendIntent.putExtra(Intent.EXTRA_TEXT,
			Html.fromHtml(new StringBuilder()
			.append("<body><html>" +  meteodata + "</body></html>")
			.toString())
			);
//			Html.fromHtml("<body><html>" +  meteodata + "</body></html>").toString());
//			Html.fromHtml(meteodata).toString());
//			Html.fromHtml(new StringBuilder().append(meteodata).toString()));
                    startActivity(sendIntent);
		} catch (Exception e) { e.printStackTrace(); }


                }
            }
        });

        etext = findViewById(R.id.etext);
	etext.setTextIsSelectable(true);

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
		wdata = Util.getWeather(curStation);
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
		else etext.setText(Html.fromHtml("<p><br><br>&emsp;<h1><font color=#C00000>" + 
			App.get_string(R.string.no_data_avail) + "</font></h1>"));
            }
        };
        AsyncTaskWithProgress atp = new AsyncTaskWithProgress(this, App.get_string(R.string.receiving_data), bgr, fgr);
        atp.execute();

    }

    private static String windDir(double degrees) {
	try {
	    String[] directions = App.getContext().getResources().getStringArray(R.array.wind_dirs);
            return directions[(int)Math.round(((degrees % 360) / 45))];
	} catch (Exception e) {
	    log_err("invalid degrees " + degrees);	
	    return null;	
	}
    }

    public static String parseWeatherInfo(WeatherInfo wi, boolean show_temp) {

	String s = "", st;
	double val, val2, val3;

	    if(wi.get_type() == Util.WEATHER_REQ_7DAY) st = wi.get_date() + ", " + wi.get_time();	
	    else st = wi.get_time() + " &nbsp;&nbsp; " + wi.get_date();	
	    if(show_temp && wi.interpol_data) st += " (***)";
	    s += "<p><h3><i>" + st + "</i></h3>";

	    st = wi.get_info_string();
	    if(st != null) s += st + "<br>";

	    if(show_temp) {	
		val = wi.get_temperature();	
		if(val != Util.inval_temp) s += String.format(App.get_string(R.string.wd_temperature), val) + "<br>";
	    }	

	    val = wi.get_pressure();
	    if(val != -1) s += String.format(App.get_string(R.string.wd_pressure), val) + "<br>";

/*	    
            val = wi.get_wind_dir();
            val2 = wi.get_wind_speed();
            val3 = wi.get_gusts();

            if(val != -1 && val2 != -1) {
                st = windDir(val);
                if(val3 != -1) s += String.format(App.get_string(R.string.wd_wind_gusts), st, val, val2, val3);
                else s += String.format(App.get_string(R.string.wd_wind_nogusts), st, val, val2);
                s += "<br>";
            }

	    } */

            val = wi.get_wind_dir();
            val2 = wi.get_wind_speed();
            val3 = wi.get_gusts();

            if(val != -1 && val2 != -1) {
                st = windDir(val);
                if(val3 != -1) s += String.format(App.get_string(R.string.wd_wind_gusts1), st, val2, val3);
                else s += String.format(App.get_string(R.string.wd_wind_nogusts1), st, val2);
                s += "<br>";
            }



	    val = wi.get_precip();  		    
	    if(val != -1) {
		if(wi.get_type() == Util.WEATHER_REQ_7DAY) st = App.get_string(R.string.wd_precip_nd);
		else st = App.get_string(R.string.wd_precip1h);		
		s += String.format(st, val) + "<br>";
	    }	

	    if(wi.get_type() == Util.WEATHER_REQ_7DAY) return s;	// no more data for 7-day forecast
	    else if(wi.get_type() == Util.WEATHER_REQ_3DAY) {
		val = wi.get_humidity();
		if(val != -1) s += String.format(App.get_string(R.string.wd_humidity), val) + "<br>";
		return s;				// no more data for 3-day forecast
	    }	
	    val = wi.get_precip3h();  		    
	    if(val != -1) s += String.format(App.get_string(R.string.wd_precip3h), val) + "<br>";

	    val = wi.get_precip6h();  		    
	    if(val != -1) s += String.format(App.get_string(R.string.wd_precip6h), val) + "<br>";

	    val = wi.get_precip12h();  		    
	    if(val != -1) s += String.format(App.get_string(R.string.wd_precip12h), val) + "<br>";

	    val = wi.get_humidity();
	    if(val != -1) s += String.format(App.get_string(R.string.wd_humidity), val) + "<br>";

	    val = wi.get_visibility();
	    if(val != -1) {
		if(val >= 1000) {
		    int vis = (int) val/1000;
		    s += String.format(App.get_string(R.string.wd_visibility_km), vis) + "<br>";
		} else {
		    s += String.format(App.get_string(R.string.wd_visibility_m), (int) val) + "<br>";
		}
	    }
 
	    int clouds = wi.get_clouds();
	    if(clouds != -1) s += String.format(App.get_string(R.string.wd_clouds), clouds) + "<br>";

	return s;
	     	
    }

    public static String parseWeatherData(WeatherData wd) {

        WeatherInfo wi;
        String s = "", st;

	if(wd.observ != null && wd.observ.valid) { 
	    s += "<br><h2><i><font color=#0000C0>" + App.get_string(R.string.observ_data) + "</font></i></h2>";	
	    st = parseWeatherInfo(wd.observ, true);
	    if(st != null) {
		s += st; s += "<p>";
	    } 		
	}

	if(wd.for3days != null) {
	    s += "<br><h2><i><font color=#0000C0>" + App.get_string(R.string.daily_data) + "</font></i></h2>";	
	    for(int i = 0; i < wd.for3days.size(); i++) {
		wi = wd.for3days.get(i);
		if(wi == null || !wi.valid) continue;
		st = parseWeatherInfo(wi, true);
		if(st != null) {
		    s += st; s += "<p>";
		} 		
	    }
	}

	if(wd.for7days != null) {
	    s += "<br><h2><i><font color=#0000C0>" + App.get_string(R.string.weekly_data) + "</font></i></h2>";	
	    for(int i = 0; i < wd.for7days.size(); i++) {
		wi = wd.for7days.get(i);
		if(wi == null || !wi.valid) continue;
		st = parseWeatherInfo(wi, true);
		if(st != null) {
		    s += st; s += "<p>";
		} 		
	    }
	}

	return s;
    } 	

}
