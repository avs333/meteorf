package ru.meteoinfo;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.FileInputStream; 
import java.io.File;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import javax.net.ssl.HttpsURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.Date;
import java.util.Calendar;
import java.util.HashSet;
import java.util.TimeZone; 
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Environment;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.method.ScrollingMovementMethod; 
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import com.luckycatlabs.sunrisesunset.*;


class Util {

    static {
	System.loadLibrary("bz2_jni");
    } 	

    public static native int unBzip2(byte [] b, String outfile);
    public static native int gettid();

    private static String TAG = "ru.meteoinfo:Util";   

    public static final double inval_coord = -1000.0;

// Our APIs:
    public static final String URL_STA_LIST = "https://meteoinfo.ru/hmc-output/mobile/st_list.php";  // + query
    public static final String URL_STA_DATA = "https://meteoinfo.ru/hmc-output/mobile/st_fc.php";    //?p=station
    public static final String URL_WEATHER_DATA = "https://meteoinfo.ru/hmc-output/mobile/st_obsfc.php";    //?p=query&st=station

// OSM APIs:
// https://wiki.openstreetmap.org/wiki/Nominatim -- takes lat/lon coordinates, returns some human-readable
// address in the proximity. NB: user-agent +must+ be specified (no permission otherwise), see getAddress() in Utils.
    public static final String URL_OSM_GEOCODING = "https://nominatim.openstreetmap.org/reverse?format=json&accept-language=ru,en"; // &lat=...&lon=...	

// GOOGLE APIs:
    public static final String GOOGLE_NM = "http://maps.googleapis.com/maps/api/geocode/json?latlng="; // lat,lon&language=

// GEONAMES APIs:
    public static final String URL_GEONAMES_NM = "http://api.geonames.org/findNearbyPlaceName?username=meteoinfo_ru&lang=local"; // &lat=...&lng...
    public static final String URL_GEONAMES_TZ = "http://api.geonames.org/timezone?username=meteoinfo_ru"; // &lat=...&lng...

    public static final String STA_LIST_QUERY_PLAIN =	"?p=10"; // plain text list    
    public static final String STA_LIST_QUERY_MD5 = 	"?p=20"; // md5 sum of list
    public static final String STA_LIST_QUERY_SHA256 =	"?p=30"; // sha256 hash of    
    public static final String STA_LIST_QUERY_ZLIB =	"?p=40"; // libz compressed list    
    public static final String STA_LIST_QUERY_BZIP2 =	"?p=50"; // bzip2 compressed list
    
    public static final int WEATHER_REQ_OBSERV = 10;	
    public static final int WEATHER_REQ_7DAY = 20;	
    public static final int WEATHER_REQ_3DAY = 30;	

    public static final String WEATHER_QUERY_OBSERV = "?p=" + Integer.toString(WEATHER_REQ_OBSERV); 
    public static final String WEATHER_QUERY_7DAY = "?p=" + Integer.toString(WEATHER_REQ_7DAY); 
    public static final String WEATHER_QUERY_3DAY = "?p=" + Integer.toString(WEATHER_REQ_3DAY); 

    public static final int GOOGLE_TIMEOUT = 60000;	// let's take a minute for RKN	
    public static final int OMS_TIMEOUT = 20000;	// on very slow connections
    public static final int SERVER_TIMEOUT = 20000;	// on very slow connections
    public static final int SERVER_CONN_TIMEOUT = 2000;	// for geonames


/*  No class declarations in java, here's what we define:
    class Station;
    class WeatherInfo;
    class WeatherData; */

    private static ArrayList<Station> fullStationList = null;

    public static class Station {
        public String name = null;
        public String country = null;
        public String name_p = null;
	public String shortname = null;
        public long code = -1;
	public long wmo = -1;	
        public double latitude  = inval_coord;
        public double longitude = inval_coord;
        @Override
        public String toString() {	// override for ListAdapter
            return name_p;
        }
/*      public String getInfo() {
            String info = App.get_string(R.string.station) + ": " + code;
            if(wmo != -1) info += "\n" + "WMO id: " + wmo;
            if(name != null) info += "\n" + App.get_string(R.string.name) + ": " + name;
            if(country != null) info += "\n" + App.get_string(R.string.country) + ": " + country;
            if(name_p != null) info += "\n[" + name_p + "]";
            if(latitude != inval_coord) info += "\n" + App.get_string(R.string.latitude) + " " + latitude;
            if(longitude != inval_coord) info += "\n" + App.get_string(R.string.longitude) + " " + longitude;
            return info;
        } */
        public String getInfo() {
	    if(name == null || country == null || latitude == inval_coord 
		|| longitude == inval_coord) return null;
            String info = App.get_string(R.string.station) + ": " + name; 
	    info += "\n" + String.format(App.get_string(R.string.sta_ll_fmt), latitude, longitude, country);
            return info;
        }
    }

    // Won't import WeatherActvity; colours and logUI are only needed from it. 	

    private static final int COLOUR_ERR = WeatherActivity.COLOUR_ERR;      
    private static final int COLOUR_INFO = WeatherActivity.COLOUR_INFO; 
    private static final int COLOUR_GOOD = WeatherActivity.COLOUR_GOOD;       
    private static final int COLOUR_DBG = WeatherActivity.COLOUR_DBG;      

    private static void log(int colour, String mesg) {
	if(App.activity_visible) WeatherActivity.logUI(colour, mesg);
	if(colour == COLOUR_ERR) Log.e(TAG, mesg);	
	else Log.d(TAG, mesg);
    }

    private static void log(int colour, int res_id) {
	log(colour, App.get_string(res_id));
    }
	
    // Add src to the end of dest expanding it as required

    public static byte[] appendTo(byte[] dest, byte[] src, int add_len) {
        int i, old_len = (dest == null) ? 0 : dest.length;
        byte[] new_b = new byte[old_len + add_len];
        for(i = 0; i < old_len; i++) new_b[i] = dest[i];
        for(i = 0; i < add_len; i++) new_b[i+old_len] = src[i];
	return new_b;
    }

    public static boolean urlAccessible(String url, int timeout_ms) { 
	try {
	    HttpsURLConnection.setFollowRedirects(false);
	    HttpsURLConnection con = (HttpsURLConnection) new URL(url).openConnection();
	    con.setRequestMethod("HEAD");
	    con.setConnectTimeout(timeout_ms);
	    return (con.getResponseCode() == HttpURLConnection.HTTP_OK);
	} catch (Exception e) {
	    return false;
	}
    }

    // Get string at most 8 kB bytes long from the server

    private static String getShortStringFromURL(String url_str) {
        URL url;
        HttpsURLConnection sconnection = null;
        HttpURLConnection connection = null;
        InputStream in = null;
	final int bufsz = 8*1024, len;
	byte [] b = new byte[bufsz];
	boolean use_https = url_str.startsWith("https");
	if(url_str == null) return null;
	long start = System.currentTimeMillis(); 
        try {
            url = new URL(url_str);
	    if(use_https) {
		sconnection = (HttpsURLConnection) url.openConnection();
		sconnection.setReadTimeout(SERVER_TIMEOUT);
		in = new BufferedInputStream(sconnection.getInputStream());
	    } else {
		connection = (HttpURLConnection) url.openConnection();
		connection.setReadTimeout(SERVER_TIMEOUT);
		connection.setConnectTimeout(SERVER_CONN_TIMEOUT);
		in = new BufferedInputStream(connection.getInputStream());
	    }
	    len = in.read(b, 0, bufsz);
	} catch (java.net.ConnectException c) {
	    log(COLOUR_ERR, url_str + ": " + App.get_string(R.string.no_server_conn));
	    return null;	
	} catch(java.net.SocketTimeoutException je) {
	    log(COLOUR_ERR, url_str + ": " + App.get_string(R.string.read_timeout));
	    return null;		
        } catch (Exception e) {
	    log(COLOUR_ERR, url_str + ": " + App.get_string(R.string.err_exception));
            e.printStackTrace();
	    return null;	
        } finally {
            try {
                if(in != null) in.close();
                if(sconnection != null) sconnection.disconnect();
                if(connection != null) connection.disconnect();
            } catch (Exception e) {
	        e.printStackTrace();
		log(COLOUR_ERR, "getShortStringFromURL: exception in final block");
		return null;	
	    }
        }
	if(len < 1) {
	    Log.e(TAG, App.get_string(R.string.empty_string));	
	    return null;
	}	
	long end = System.currentTimeMillis();
//	log(COLOUR_DBG, "url: " + url_str + " " + App.get_string(R.string.received_in) + " " + (end - start) + "ms");
	Log.d(TAG, "url: " + url_str + " " + App.get_string(R.string.received_in) + " " + (end - start) + "ms");
	String ret = new String(b);
	ret = ret.substring(0, len);
	return ret;
    }

    private static File getStationsFile() {

        InputStream in = null;
        HttpsURLConnection urlConnection = null;
	String sta_file = App.getContext().getFilesDir().toString() + "/station.list";
	final int bufsz = 8*1024;
	byte [] b = new byte[bufsz], result = null;
	int len;

        try {

	    // query md5 of the station list currently stored on the server
	    String md5 = getShortStringFromURL(URL_STA_LIST + STA_LIST_QUERY_MD5); 		

	    // if(md5.startsWith("Rez "))	md5 = md5.substring(5);

   	    SharedPreferences settings =  PreferenceManager.getDefaultSharedPreferences(App.getContext());
	    String last_md5 = settings.getString("last_md5", null); 

	    if(last_md5 == null || !md5.equals(last_md5) || !(new File(sta_file)).exists()) {	

		// md5 mismatch or no station file yet, obtain its bz2 file
		log(COLOUR_INFO, R.string.retr_sta_list);

		URL url = new URL(URL_STA_LIST + STA_LIST_QUERY_BZIP2);
            	urlConnection = (HttpsURLConnection) url.openConnection();
	    	urlConnection.setReadTimeout(SERVER_TIMEOUT);
	        in = new BufferedInputStream(urlConnection.getInputStream());

		long start_time = System.currentTimeMillis();

		result = null;

		while ((len = in.read(b,0,bufsz)) > 0) result = appendTo(result, b, len);	
		
		long end_time = System.currentTimeMillis();

		urlConnection.disconnect();
		in.close();
		urlConnection = null;
		in = null;

		if(result == null) {
		    log(COLOUR_ERR, R.string.bzip2_rec_failure);		    
		    return null; 	
		}	

		float kbs = (end_time > start_time) ? ((float) result.length)/((float) (end_time - start_time)) : 0;
		String s = String.format(App.get_string(R.string.bzip2_rec_ok), kbs);
		log(COLOUR_INFO, s);

		// unbzip the station file

		int unzipped_size = unBzip2(result, sta_file);

		if(unzipped_size <= 0) {
		    log(COLOUR_ERR, R.string.bzip2_dec_failure);		    
		    return null;	
		}

		s = String.format(App.get_string(R.string.bzip2_dec_ok), result.length, unzipped_size);
		log(COLOUR_DBG, s);

		// save md5 in preferences

		last_md5 = md5;
		SharedPreferences.Editor editor = settings.edit();
		editor.putString("last_md5", last_md5);	
		editor.apply();

	    } else {
		// No need to do anything. sta_file exists and its md5 matches with the server's
		log(COLOUR_INFO, R.string.sta_unchanged);
	    }	

	    return new File(sta_file);

	} catch (java.net.ConnectException c) {
	    log(COLOUR_ERR, R.string.no_server_conn);
	    return null;	
	} catch(java.net.SocketTimeoutException je) {
	    log(COLOUR_ERR, URL_STA_LIST + ": " + App.get_string(R.string.read_timeout));
	    return null;		
        } catch (Exception e) {
	    log(COLOUR_ERR, R.string.err_exception);	
            e.printStackTrace();
            return null;
        } finally {
            try {
                if(in != null) in.close();
                if(urlConnection != null) urlConnection.disconnect();
            } catch(Exception e) {
	    	log(COLOUR_ERR, R.string.err_exception);	
		e.printStackTrace();
            }
        }
    } 

//    synchronized 
    public static boolean getStations() {

//	if(fullStationList != null && fullStationList.size() > 0) return true;
	if(stationListKnown()) return true;

	boolean ok = false;
	FileInputStream inf = null;
	final int bufsz = 8*1024;
	byte [] b = new byte[bufsz], result = null;
	int len, i;

        try {

	    File stations = getStationsFile();
	    if(stations == null) return false;	   

	    // First, read it to array

	    inf = new FileInputStream(stations);
	    result = null;	
            while ((len = inf.read(b,0,bufsz)) > 0) result = appendTo(result, b, len);	
	    inf.close();
	    inf = null; 	

	    if(result == null) {
		log(COLOUR_ERR, R.string.err_read_sta);
	    	return false;
	    }  	

	    // Convert file contents from byte array to string

	    String s = new String(result);

	    // Split the string into lines

	    String[] std, stans;

 	    stans = s.split("\n");

	    if(stans.length < 1) {
		log(COLOUR_ERR, R.string.empty_sta_list);
		return false;
	    }
		
	    // Parse and add each station to fullStationList

	    log(COLOUR_DBG, R.string.parse_list);

	    fullStationList = new ArrayList<>();	
	
	    for(i = 0; i < stans.length; i++) {
		if(stans[i] == null) {
	    	    log(COLOUR_DBG, R.string.empty_elem_skiped);
		    continue;
		}
		if(stans[i].startsWith("16169")) {  // known dead station next to Kievsky vokzal
		    continue;
	        }

		stans[i] = stans[i].trim();

		std = stans[i].split(";");
		if(std.length < 5) {
		    log(COLOUR_DBG, App.get_string(R.string.inv_sta_data) + i);
		    continue;
		}
		Station sta = new Station();	
		try {
		    sta.code = Long.parseLong(std[0]);	
		    if(!std[1].equals("")) sta.wmo = Long.parseLong(std[1]);	
		    sta.latitude = Double.parseDouble(std[2]);	
		    sta.longitude = Double.parseDouble(std[3]);
		} catch(java.lang.NumberFormatException nfe) {
		    log(COLOUR_DBG, App.get_string(R.string.inv_sta_data) + i);
		    continue;
		}		

		sta.name = std[4];
		sta.name_p = std[4];
		if(std.length > 5 && !std[5].equals(" ") && !std[5].equals("")) {
		    sta.country = std[5];
		    sta.name_p += ", " + std[5];
		}
		if(std.length > 6 && std[6] != null && !std[6].equals(" ") && !std[6].equals("")) {
		    sta.name_p += ", " + std[6];
		}		
		switch(std.length) {
		    case 7: sta.shortname = std[6]; break;
		    case 6: sta.shortname = std[4] + ", " + std[5]; break;
		    default: sta.shortname = std[4]; break;
		}
		if(sta.shortname == null) sta.shortname = std[4];
		fullStationList.add(sta);
	    }
	    
	    s = String.format(App.get_string(R.string.good_read_sta), fullStationList.size());
	    log(COLOUR_INFO, s); 		

	    ok = true;	

        } catch (Exception e) {
	    log(COLOUR_ERR, R.string.err_exception);	
            e.printStackTrace();
            return false;
        } finally {
            try {
                if(inf != null) inf.close();
            } catch(Exception e) {
	    	log(COLOUR_ERR, R.string.err_exception);	
		e.printStackTrace();
                ok = false;
            }
        }
        return ok;
    }

    public static boolean stationListKnown() {
	return fullStationList != null && fullStationList.size() != 0;
    }

    public static Station getNearestStation(double latitude, double longitude) {
	if(fullStationList == null) return null;
	double last_diff = Long.MAX_VALUE; 
	int i, result = -1;      
	for(i = 0; i < fullStationList.size(); i++) {
	    Station st = fullStationList.get(i);
	    double diff = (st.longitude - longitude) * (st.longitude - longitude)
			+ (st.latitude - latitude) * (st.latitude - latitude);
	    if(diff < last_diff) {
		result = i;
		last_diff = diff;		
	    }	
	}
	return (result >= 0) ? fullStationList.get(result) : null;
    }	

    public static ArrayList<Station> getMatchingStationsList(String pattern) {
	if(fullStationList == null || pattern == null) return null;	
        ArrayList<Station> stations = new ArrayList<>();
	int i;
	String pat = pattern.toLowerCase(java.util.Locale.US);
	for(i = 0; i < fullStationList.size(); i++) {
	   Station st = fullStationList.get(i);		    	
	   String s1 = st.name.toLowerCase(java.util.Locale.US);
	   if(s1.startsWith(pat)) {
		stations.add(st);
	   } 		
	}
	return stations;
    }

    public static Station getFavStation(String fav) {
        try {
	    if(fullStationList == null || fav == null) return null;	
            long k = Long.parseLong(fav);
            for(int i = 0; i < Util.fullStationList.size(); i++) {
                Station st = Util.fullStationList.get(i);
                if(k == st.code) return st;
            }
        } catch (Exception e) {
            log(COLOUR_ERR, App.get_string(R.string.err_exception) + " " + fav);
            e.printStackTrace();
            return null;
        }
        return null;
    }

    public static String getAddressFromOSM(double lat, double lon) {
        HttpsURLConnection urlConnection = null;
        InputStream in = null;
        JsonReader reader = null;
        String addr = null;
        try {
            URL url = new URL(URL_OSM_GEOCODING +  // lat + "," + lon); 
			"&lat=" + lat + "&lon=" + lon);
            urlConnection = (HttpsURLConnection) url.openConnection();
            urlConnection.setReadTimeout(30*1000);
	    urlConnection.setRequestMethod("GET");
            urlConnection.setConnectTimeout(OMS_TIMEOUT);
            urlConnection.setDoInput(true);
            urlConnection.setDoOutput(false);
	    // Required for their server, see https://operations.osmfoundation.org/policies/nominatim/ !!
	    // If unspecified, the server will respond with HTTP/400 "permission denied" 	
	    urlConnection.setRequestProperty("User-Agent", "android/ru.meteoinfo");	
	    urlConnection.connect();
	    int response = urlConnection.getResponseCode();
	//  Log.d(TAG, url.toString() + " response=" + response);	
	    if (response != HttpsURLConnection.HTTP_OK) return null;
            in = new BufferedInputStream(urlConnection.getInputStream());
            reader = new JsonReader(new InputStreamReader(in, "UTF-8"));
            reader.beginObject();
            while(reader.hasNext()) {
                String name = reader.nextName();
                if(name.equals("display_name")) {
                    addr = reader.nextString();
		    if(addr.matches("^\\d+?.*")) addr = "Дом " + addr;	
                    break;
                } else reader.skipValue();
            }
        } catch (Exception e) {
            Log.e(TAG, "exception in getAdderss()");
//            e.printStackTrace();
	    return null;	
        } finally {
            try {
                if(reader != null) reader.close();
                if(in != null) in.close();
                if(urlConnection != null) urlConnection.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "exception on exit from getAdderss()");
	    }
        }
        return addr;
    }

    public static String getAddressFromGoogle(double lat, double lon) {
        InputStream in = null;
        HttpURLConnection urlConnection = null;
        JsonReader reader = null;
        String addr = null;
        try {
	    String url_str = GOOGLE_NM + lat + "," + lon + "&sensor=false&language=ru"; // + (use_russian ? "ru" : "en");
//	    url_str += "&key=" + "AIzaSyAReLe7a8eqNswzVxIaVlj0n-EEYl0PN38";	

            URL url = new URL(url_str);

            urlConnection = (HttpURLConnection) url.openConnection();
	    urlConnection.setReadTimeout(Util.GOOGLE_TIMEOUT);	

            in = new BufferedInputStream(urlConnection.getInputStream());
            reader = new JsonReader(new InputStreamReader(in, "UTF-8"));
            reader.beginObject();
            String name = reader.nextName();
            if(name.equals("results")) {
                reader.beginArray();
                if(reader.peek() == JsonToken.BEGIN_OBJECT) {
                    reader.beginObject();
                    while (reader.hasNext()) {
                        name = reader.nextName();
                        switch (name) {
                            case "formatted_address":
                                addr = reader.nextString();
				if(addr == null) log(COLOUR_DBG, R.string.err_google);
                                break;
                            default:
                                reader.skipValue();
                                break;
                        }
                        if(addr != null) break;
                    }
                }
            } else if(name.equals("error_message")) {
		String err_msg = reader.nextString();
		log(COLOUR_DBG, err_msg);
	    } else log(COLOUR_DBG, R.string.err_google);

            if(addr != null) log(COLOUR_DBG, App.get_string(R.string.found_sta_info) + " " + addr);
	    reader.close();
            reader = null;
	} catch(java.net.SocketTimeoutException je) {
	    log(COLOUR_ERR, "http://maps.googleapis.com: " + App.get_string(R.string.read_timeout));
	    return null;		
        } catch(Exception e) {
	    log(COLOUR_ERR, R.string.err_exception);	
	    e.printStackTrace();
            return null;
        } finally {
            try {
                if(reader != null) reader.close();
                if(in != null) in.close();
                if(urlConnection != null) urlConnection.disconnect();
            } catch(Exception e) {
       		log(COLOUR_ERR, R.string.err_exception);	
		e.printStackTrace();
                addr = null;
            }
        }
        return addr;
    }

/*
    public static String windDegreesToString(double degrees) {
	String[] directions = App.getContext().getResources().getStringArray(R.array.wind_dirs);
	return directions[(int)Math.round(((degrees % 360) / 45))];
    }  	
*/
    public static final double inval_temp = -273.15;

    public static class WeatherInfo {

	private int type = -1;
	private long utc = 0;

        public boolean valid = false;

	// debug only: interpolated weather
	public boolean interpol_data = false;

	private String date = null;
	private String time = null;

	private double pressure = -1, temperature = inval_temp, 
		wind_dir = -1, wind_speed = -1, precip = -1, 
		precip3h = -1, precip6h = -1, precip12h = -1, 
		humidity = -1, visibility = -1, gusts = -1;
	private int info = -1, clouds = -1;

	public int get_type() { return type; }

	// for WEATHER_REQ_7DAY and WEATHER_REQ_3DAY only
	private boolean night = false;
	public boolean is_night() { return night; }	

	// "yyyy-MM-dd HH:mm UTC"
	public String get_date() { return date; } 
	public String get_time() { return time; } 
	public long get_utc() { return utc; } 
	public double get_pressure() { return pressure; }
	public double get_temperature() { return temperature; }
	public double get_wind_dir() { return wind_dir; }
	public double get_wind_speed() { return wind_speed; }
	public int get_info() { return info; }
	public double get_precip() { return precip; }
	public double get_precip3h() { return precip3h; }
	public double get_precip6h() { return precip6h; }
	public double get_precip12h() { return precip12h; }
	public double get_humidity() { return humidity; }
	public double get_visibility() { return visibility; }
	public int    get_clouds() { return clouds; }
	public double get_gusts() { return gusts; }

	public String get_info_string() {
	    if(info == -1) return null;	
	    String ret = null;
	    try {
	 	switch(type) {
		    case WEATHER_REQ_OBSERV: ret = new String(weatherCodesObserv[info]); break;
		    case WEATHER_REQ_7DAY: ret = new String(weatherCodes7day[info]); break;
		    case WEATHER_REQ_3DAY: ret = new String(weatherCodes3day[info]); break;
		}
	    } catch (Exception e) { Log.e(TAG, "exception in get_info_string()"); }
	    return ret; 
	} 

	public String get_icon_name() {
	    String resname = null;
	    int ret = -1;		
	    try {
		switch(type) {
		    case WEATHER_REQ_7DAY:	// always OK.
			if(info > 0 && info <= 85) ret = forecast7day_code2pic[info];
			break;
		    case WEATHER_REQ_3DAY:
		        if(info > 0 && info <= 17) ret = info;
			else Log.e(TAG, "code " + info + " is out of range!");
			break;
		    case WEATHER_REQ_OBSERV:
			if(info > 0 && info <= 99) ret = observ_code2pic[info];
			if(ret != -1) break;
			if(clouds == -1) break;
			switch(clouds) {
			    case 0: case 1: case 2: case 3: 
				ret = 0; break;
			    case 4: case 5: case 6: case 7: 
				ret = 6; break;
			    case 8: case 9: case 10:
				ret = 16; break;
			    default:
 				Log.e(TAG, "get_icon_name(): invalid clouds=" + clouds + " for observables"); 
				break;
			}
			break;
		}
		if(ret == -1) {
		    Log.e(TAG, "get_icon_name(): invalid info=" + info + " for type=" + type); 
		    return null;
		}
		resname = "ru.meteoinfo:drawable/wi" + ret + (night ? "n" : "d") + "_s";
	    } catch (Exception e) { 
		Log.e(TAG, "exception in get_icon_name(): info=" + info + ", type=" + type); 
		return null;
	    }
	    return resname;		
	}


	public WeatherInfo(int type, String s, long utc_sunrise, long utc_sunset) {
	    int i, params_sz;
	    if(s == null) return;  	

	    //Log.d(TAG, "string=<" + s + ">"); 	
	   	
	    switch(type) {	
		case WEATHER_REQ_OBSERV:
		    params_sz = 15;
		    break;	
		case WEATHER_REQ_7DAY:
		    params_sz = 8;
		    break;	
		case WEATHER_REQ_3DAY:
		    params_sz = 9;
		    break;	
		default:
		    Log.e(TAG, "invalid weatherinfo type");
		    return;	
	    }

	    String p[] = s.split(";", params_sz + 1);	

	    if(p == null) {
		Log.e(TAG, "java sucks in String.split");
		return;
	    }

	    if(p.length < params_sz) {
		Log.e(TAG, "invalid weatherinfo string: " + p.length + "<" + params_sz);
		Log.e(TAG, "string was <" + s + ">, of type " + type);
		return;
	    }

	    this.type = type;	

	    for(i = 2; i < params_sz; i++) {
		try {
		  if(p[i] == null || p[i].isEmpty()) {
		      p[i] = null; 
		      continue;
		  }
		  switch(i) {
		    case 2: pressure = Double.parseDouble(p[i]); break;
		    case 3: temperature = Double.parseDouble(p[i]); break; 
		    case 4: wind_dir = Double.parseDouble(p[i]); break;
		    case 5: wind_speed = Double.parseDouble(p[i]); break;
		    case 6: info = Integer.parseInt(p[i]); break;
		    case 7: precip  = Double.parseDouble(p[i]); break;
		    case 8: if(type == WEATHER_REQ_OBSERV) precip3h = Double.parseDouble(p[i]);
			    else if(type == WEATHER_REQ_3DAY) humidity = Double.parseDouble(p[i]); 
			    break;
		    case 9: if((type == WEATHER_REQ_OBSERV)) precip6h = Double.parseDouble(p[i]); break;
		    case 10: if((type == WEATHER_REQ_OBSERV)) precip12h = Double.parseDouble(p[i]); break;
		    case 11: if((type == WEATHER_REQ_OBSERV)) humidity = Double.parseDouble(p[i]); break;
		    case 12: if((type == WEATHER_REQ_OBSERV)) visibility = Double.parseDouble(p[i]); break;
		    case 13: if((type == WEATHER_REQ_OBSERV)) clouds = Integer.parseInt(p[i]); break;
		    case 14: if((type == WEATHER_REQ_OBSERV)) gusts = Double.parseDouble(p[i]); break;
		  }
		} catch(Exception e) {
		    Log.d(TAG, "error parsing p[" + i + "]");
		}
	    }

	    if(p[0] == null || p[1] == null) {
		log(COLOUR_ERR, "missing date in weatherinfo");
		return;
	    }

	    if(!Srv.use_interp && pressure < 0 && type != WEATHER_REQ_OBSERV) {
		// log(COLOUR_ERR, "no pressure for " + p[0] + " " + p[1]);	
		return;
	    }

	    try {
//		Locale loc = use_russian ? new Locale("ru", "RU") : new Locale("en", "US");
		Locale loc = new Locale("ru", "RU");
		Locale len = new Locale("en", "US");
	        if(type == WEATHER_REQ_7DAY) {
		    SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", len);
		    in.setTimeZone(TimeZone.getTimeZone("UTC"));
		    Date _date = in.parse(p[0]);
		    SimpleDateFormat out = new SimpleDateFormat("EEEE, d MMMM", loc);
		    utc = _date.getTime(); 		    
		    if(p[1].equals("night")) {
			time = new String(App.get_string(R.string.night));
			night = true;
		    } else if(p[1].equals("day")) {
			time = new String(App.get_string(R.string.day));
			night = false;
		    }	
		    date = new String(out.format(_date));
		} else {
		    SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd HH:mm", len);
		    in.setTimeZone(TimeZone.getTimeZone("UTC"));
		    Date _date = in.parse(p[0] + " " + p[1]);
		    utc = _date.getTime();
		//  SimpleDateFormat out_date = new SimpleDateFormat("EEEE, d MMMM", loc);
		    SimpleDateFormat out_date = new SimpleDateFormat("d MMMM", loc);
		    SimpleDateFormat out_time = new SimpleDateFormat("HH:mm", loc);
		    out_date.setTimeZone(TimeZone.getDefault());	// convert UTC -> device timezone
		    out_time.setTimeZone(TimeZone.getDefault());	// convert UTC -> device timezone
		    date = new String(out_date.format(_date)); 
		    time = new String(out_time.format(_date)); 

		    if(utc_sunrise != 0 && utc_sunset != 0) {
			final long day = 24 * 60 * 60 * 1000;
			long cs = utc_sunset - utc_sunrise;
			long c_utc = utc - utc_sunrise;
			night = true;

			while(c_utc < cs - day) c_utc += day;			

			while(c_utc >= 0) {
			    if(c_utc < cs) {
				night = false;
				break;
			    } 
			    if(c_utc <= day) break;
			    c_utc -= day;  	
			}
		//	Log.d(TAG, "sunrise/utc/sunset=" + utc_sunrise + "/" + c_utc + "/" +utc_sunset);
		//	Log.d(TAG, "night=" + night + " for UTC=" + p[1] + " local=" + time);
		    }
		}
	    } catch(Exception e) {
		e.printStackTrace();
		Log.e(TAG, "error parsing date");
		return;
	    }

	    valid = true; 	
	}

	// interpolate WeatherInfo data: wl = future forecasts, w = last observed data
	public static boolean interpol(ArrayList<WeatherInfo> wl, WeatherInfo w) {
	    if(wl == null) {
		log(COLOUR_ERR, "Internal error: interpolation for null WeatherInfo list");
		return false;
	    }
	    if(wl.get(0).type != WEATHER_REQ_3DAY || 
		  (w != null && (w.type != WEATHER_REQ_OBSERV && w.type != WEATHER_REQ_3DAY))) {
		log(COLOUR_ERR, "Internal error: interpolation for wrong WeatherInfo type");
		return false;
	    }
	    if(w != null) wl.add(0, w);		// if we've got observables, let it be 1st entry

	    int i, k, i_first; 	

	    if(wl.get(0).get_pressure() != -1) i_first = 0;
	    else i_first = -1;		    

	    for(i = 1; i < wl.size(); i++) {
		WeatherInfo wn = wl.get(i);
		if(!wn.valid) continue;
		if(wn.get_pressure() != -1) {		// must be valid
		    if(i - i_first == 1) {		// no need to interpolate
			i_first = i;
			continue;
		    }
		    if(i_first == -1) {			// no previous weather data, set to current
			for(k = 0; k < i; k++) {
			    WeatherInfo wi = wl.get(k);
			    if(!wi.valid) continue;
			    wi.type = WEATHER_REQ_3DAY; // just for clarity
			    wi.pressure = wn.get_pressure();
			    wi.wind_dir = wn.get_wind_dir();
			    wi.wind_speed = wn.get_wind_speed();
			    wi.precip = wn.get_precip();
			    wi.humidity = wn.get_humidity();
			    wi.interpol_data = true;
			    wl.set(k, wi);
			}
			i_first = i;
		        continue;
		    }
		    for(k = i_first + 1; k < i; k++) {
			WeatherInfo wi = wl.get(k);
			if(!wi.valid) continue;
			if(wi.get_pressure() != -1) {
			    log(COLOUR_ERR, "Internal error: interpolation for known WeatherInfo");
			    return false;
			}
			WeatherInfo w0 = wl.get(i_first);
			if(w0.get_pressure() == -1) {
			    log(COLOUR_ERR, "Internal error: interpolation for unknown WeatherInfo");
			    return false;
			}	
			wi.type = WEATHER_REQ_3DAY; // just for clarity

			if(w0.utc == 0 || wi.utc < w0.utc || wn.utc < wi.utc) {
			    log(COLOUR_ERR, "Invalid utc dates: w0=" + w0.utc + " wi=" + wi.utc + " wn=" + wn.utc);
			    continue;
			}
			double alpha = ((double) (wi.utc - w0.utc))/((double) (wn.utc - w0.utc));
			wi.pressure = mean_param(w0.get_pressure(), wn.get_pressure(), alpha); // i - i_first > 1 here
			wi.wind_dir = mean_wind(w0.get_wind_dir(), wn.get_wind_dir(), alpha);  // this case is special
			wi.wind_speed = mean_param(w0.get_wind_speed(), wn.get_wind_speed(), alpha);
			wi.precip = mean_param(w0.get_precip(), wn.get_precip(), alpha);
			wi.humidity = mean_param(w0.get_humidity(), wn.get_humidity(), alpha);
			wi.interpol_data = true;
			wl.set(k, wi);
		    }
		    i_first = i;
		}
	    }
	    if(w != null) wl.remove(0);
	    return true;
	}

        private static double mean_param(double f0, double f1, double alpha) {
	    if(f0 == -1) return (f1 == -1) ? -1 : f1;
	    if(f1 == -1) return (f0 == -1) ? -1 : f0;
	    return f0 + alpha * (f1 - f0);
	}

	private static double mean_wind(double f0, double f1, double alpha) {
	    try {
		double fr, diff;
		final double max_diff = 90;	// largest sector f0 and f1 must be in to get a credible result
		boolean min_changed = false;
		if(f0 == -1) return (f1 == -1) ? -1 : f1;
		if(f1 == -1) return (f0 == -1) ? -1 : f0;
		if(f0 == f1) return f0;

		if(f1 < f0) {
		    fr = f0;
		    f0 = f1;
		    f1 = fr;
		    min_changed = true;
		}

		diff = f1 - f0;

		if(diff < max_diff) {
		    if(!min_changed) fr = f0 + alpha * diff;
		    else fr = f1 - alpha * diff; 
		} else if(360 - f1 + f0 < max_diff) {
		    diff = 360 - diff;
		    if(!min_changed) {
			fr = f0 - alpha * diff;
			if(fr < 0) fr = 360 - fr;
		    } else {	
			fr = f1 + alpha * diff;
			if(fr > 360) fr -= 360;
 		    }
		} else { 
		    log(COLOUR_DBG, String.format(App.get_string(R.string.too_windy),f0, f1));	
		    fr = f0;
		}
		return fr;
	    } catch(Exception e) {
		log(COLOUR_ERR, "exception in WeatherInfo::mean_wind()");
		e.printStackTrace();
		return f0;
	    }
        }
    }  // class WeatherInfo


    // All weather data for a particular station

    public static class WeatherData {
	public long sta_code = -1;
	public WeatherInfo observ = null;		// of type WEATHER_REQ_OBSERV
        public ArrayList<WeatherInfo> for7days = null;	// of type WEATHER_REQ_7DAY 
        public ArrayList<WeatherInfo> for3days = null;	// of type WEATHER_REQ_7DAY
    }

    // Parse xml code in <str> seeking for <tags> and returning array
    // of tag values at the same positions as in <tags> (all must be present). 
    // No namespaces, etc. _Very_ rudimentary.

    private static ArrayList<String> parse_xml_string(String str, ArrayList<String> tags) {	
	if(str == null || tags == null || tags.size() == 0) return null;
	try {
	    ArrayList<String> ret = new ArrayList<String>(tags);
	    XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
	    XmlPullParser xpp = factory.newPullParser();
	    xpp.setInput(new StringReader(str));
	    int evt = xpp.getEventType();
	    String tag_name = null;
	    while(evt != XmlPullParser.END_DOCUMENT) {
		switch(evt) {
		    case XmlPullParser.START_TAG: tag_name = xpp.getName();
		    case XmlPullParser.TEXT:
			if(tag_name == null) break;			
			int idx = tags.indexOf(tag_name);
			if(idx < 0) break;
			String s  = xpp.getText();	// fucking crap
		        if(idx >= 0 && s != null && s.charAt(0) != 0xa) {
	//		    Log.d(TAG,"idx=" + idx + ", set=" + s);
			    ret.set(idx, new String(s));
			}
			break;
		}
		evt = xpp.next();
	    }
	    if(ret.size() != tags.size()) {
		Log.e(TAG, "parse_xml_string: input/output size mismatch");
		return null;
	    } 
	    for(String s : ret) {
		if(s == null) {
		    Log.e(TAG, "parse_xml_string: tag values missing");
		    return null;
		}
	    }		
	    return ret;
	} catch (Exception e) {
	    e.printStackTrace();	
	    return null;
	}
    }

    private static final String[] tags_tz = { "rawOffset", "timezoneId", "sunrise", "sunset" } ;
    private static final ArrayList<String> xml_tags_tz = new ArrayList<String>(Arrays.asList(tags_tz));	
//  private final String[] tags_name = { "name" };
//  private final ArrayList<String> xml_tags_name = new ArrayList<String>(Arrays.asList(tags_name));

//  synchronized 
    public static WeatherData getWeather(Station station) {

	if(station == null) {
	    log(COLOUR_ERR, "getWeather called for null station");
	    return null;	
	}
//	log(COLOUR_DBG, App.get_string(R.string.query_weather_data) + " " + station.code);
	Log.d(TAG, App.get_string(R.string.query_weather_data) + " " + station.code);

	WeatherData ret = new WeatherData();
	ret.sta_code = station.code;

	long utc_sunrise = 0;
	long utc_sunset = 0;	

	Locale loc = new Locale("en", "US");
	Date date;
	
	try {	
	    String s_off, s_tz, s_sr, s_ss;
	    SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd HH:mm", loc);	
	    if(Srv.use_geonames) { 
		int retries = 3;
		String timezone_xml = null;
		while(retries > 0) {		
		     timezone_xml = getShortStringFromURL(URL_GEONAMES_TZ + "&lat=" + station.latitude + "&lng=" + station.longitude);
		     if(timezone_xml != null) break;
		     retries--;		
		     Log.d(TAG, "retrying...");	
		}
		if(timezone_xml != null) {
		    ArrayList<String> al = parse_xml_string(timezone_xml, xml_tags_tz);
		    if(al != null) {
			s_off = al.get(0);
			s_tz = al.get(1);	// timezone
			s_sr = al.get(2);	// sunrise
			s_ss = al.get(3);	// sunset
			Log.d(TAG, "geonames: utc_offs=" + s_off + " tz=" + s_tz + ", sunrise=" + s_sr + ", sunset=" + s_ss);
			TimeZone tz = TimeZone.getTimeZone(s_tz);
			in.setTimeZone(tz);
			date = in.parse(s_sr);
			utc_sunrise = date.getTime();
			date = in.parse(s_ss);
			utc_sunset = date.getTime();
			Log.d(TAG, "geonames: utc_sunrise=" + utc_sunrise + ", utc_sunset=" + utc_sunset);
		    }
	   	} else log(COLOUR_ERR, "failed to obtain timezone info from api.geonames.org");
	    } else { 
		com.luckycatlabs.sunrisesunset.dto.Location location = 
			new com.luckycatlabs.sunrisesunset.dto.Location(station.latitude, station.longitude);
		SunriseSunsetCalculator calculator = new SunriseSunsetCalculator(location, TimeZone.getDefault());
		Calendar cal = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", loc);
		sdf.setTimeZone(TimeZone.getDefault());
		String dstr = sdf.format(cal.getTime()) + " "; 	 
		s_sr = dstr + calculator.getOfficialSunriseForDate(Calendar.getInstance()); 	
		s_ss = dstr + calculator.getOfficialSunsetForDate(Calendar.getInstance()); 	
		Log.d(TAG, "SunriseSunsetCalculator: sunrise=" + s_sr + ", sunset=" + s_ss);
		in.setTimeZone(TimeZone.getDefault());
		date = in.parse(s_sr);
		utc_sunrise = date.getTime();
		date = in.parse(s_ss);
		utc_sunset = date.getTime();
		Log.d(TAG, "SunriseSunsetCalculator: utc_sunrise=" + utc_sunrise + ", utc_sunset=" + utc_sunset);
	    } 
	} catch (Exception e) {
	    log(COLOUR_ERR, "exception: failed to obtain timezone info");
	    e.printStackTrace();
	    utc_sunrise = 0;
	    utc_sunset  = 0;
	}

	String so = getShortStringFromURL(URL_WEATHER_DATA + WEATHER_QUERY_OBSERV + "&st=" + station.code);
	if(so != null && !so.isEmpty()) {
	    WeatherInfo wi = new WeatherInfo(WEATHER_REQ_OBSERV, so, utc_sunrise, utc_sunset);
	    if(wi != null && wi.valid) {
		ret.observ = wi;
		log(COLOUR_DBG, R.string.observed_data_okay);
	    } else log(COLOUR_ERR, R.string.observed_data_bad);	
	} else log(COLOUR_ERR, R.string.observed_data_bad);

	String s7 = getShortStringFromURL(URL_WEATHER_DATA + WEATHER_QUERY_7DAY + "&st=" + station.code);
	if(s7 != null && !s7.isEmpty()) {
	    String[] ws = s7.split(" \n");
	    ret.for7days = new ArrayList<>();	    		     
	    for(int i = 0; i < ws.length; i++) {		
		if(ws[i] != null) {
		    WeatherInfo wi = new WeatherInfo(WEATHER_REQ_7DAY, ws[i], 0, 0);
		    if(wi != null && wi.valid) ret.for7days.add(wi);
		}	
	    }	
	    if(ret.for7days.size() > 0) log(COLOUR_DBG, R.string.weekly_data_okay);
	    else log(COLOUR_ERR, R.string.weekly_data_bad);	
	} else log(COLOUR_ERR, R.string.weekly_data_bad);

	String s3 = getShortStringFromURL(URL_WEATHER_DATA + WEATHER_QUERY_3DAY + "&st=" + station.code);
	if(s3 != null && !s3.isEmpty()) {
	    String[] ws = s3.split(" \n");
	    ret.for3days = new ArrayList<>();	    		     
	    for(int i = 0; i < ws.length; i++) {		
		if(ws[i] != null) {
		    WeatherInfo wi = new WeatherInfo(WEATHER_REQ_3DAY, ws[i], utc_sunrise, utc_sunset);
		    if(wi != null && wi.valid) ret.for3days.add(wi);
		}	
	    }	
	    if(ret.for3days.size() > 0) {
		log(COLOUR_DBG, R.string.hourly_data_okay);
		if(Srv.use_interp) {
		    if(WeatherInfo.interpol(ret.for3days, null)) log(COLOUR_GOOD, R.string.data_obtained_processed);
		    else log(COLOUR_ERR, R.string.data_process_error);
		}  
	    } else log(COLOUR_ERR, R.string.hourly_data_bad);	
	} else log(COLOUR_ERR, R.string.hourly_data_bad);

	if((so == null || so.isEmpty()) && 
	   (s7 == null || s7.isEmpty()) && 
	   (s3 == null || s3.isEmpty())) {
	    log(COLOUR_ERR, App.get_string(R.string.no_weather_data) + " " + station.code);
	    return null;
	}

	return ret;
    }

    // Tables for conversion of weather codes to strings and picture indices

    private static final String[] weatherCodesObserv = {
	"Изменение количества облаков неизвестно",	//0
	"Количество облаков уменьшилось",	//1
	"Количество облаков не изменилось",	//2
	"Количество облаков увеличилось",	//3
	"Ухудшение видимости из-за дыма или вулканического пепла",	//4
	"Мгла",	//5
	"Пыль, взвешенная в воздухе в обширном пространстве, но не поднятая ветром",	//6
	"Пыль, песок или брызги, поднятые ветром",	//7
	"Пыльные/песчаные сильные вихри, но пыльной/песчаной бури нет",	//8
	"Пыльная/песчаная буря",	//9
	"Дымка (видимость больше 1 км)",	//10
	"Поземные туманы клочками, полосами(высота меньше 2 м от поверхности земли)",	//11
	"Поземные туманы сплошным слоем (высота меньше 2 м от поверхности земли)",	//12
	"Зарница",	//13
	"Осадки (не достигают поверхности земли)",	//14
	"Осадки (достигают поверхности земли вдали от станции (> 5 км))",	//15
	"Осадки (достигают поверхности земли)",	//16
	"Гроза, но без осадков",	//17
	"Шквал",	//18
	"Смерч",	//19
	"Морось (незамерзающая) Снежные зерна",	//20
	"Дождь (незамерзающий)",	//21
	"Снег",	//22
	"Дождь со снегом (ледяной дождь)",	//23
	"Морось, дождь (замерзающие, образующие гололед)",	//24
	"Ливневый дождь",	//25
	"Ливневый дождь со снегом",	//26
	"Град",	//27
	"Туман (видимость менее 1 км)",	//28
	"Гроза",	//29
	"Пыльная (песчаная) буря, слабая или умеренная, ослабела",	//30
	"Пыльная (песчаная) буря, слабая или умеренная",	//31
	"Пыльная (песчаная) буря, слабая или умеренная началась (усилилась)",	//32
	"Пыльная или песчаная буря (сильная) ослабела",	//33
	"Пыльная или песчаная буря (сильная)",	//34
	"Пыльная или песчаная буря (сильная) началась (усилилась)",	//35
	"Поземок (слабый или умеренный)",	//36
	"Поземок сильный",	//37
	"Метель низовая (слабая или умеренная)",	//38
	"Метель низовая сильная",	//39
	"В окрестности станции тумана нет",	//40
	"В окрестности станции местами туман",	//41
	"Туман ослабел (небо видно)",	//42
	"Туман ослабел (небо не видно)",	//43
	"Туман без изменений (небо видно)",	//44
	"Туман без изменений (небо не видно)",	//45
	"Туман начался (усилился). Небо видно",	//46
	"Туман начался (усилился). Небо не видно",	//47
	"Туман (с отложением изморози). Небо видно",	//48
	"Туман (с отложением изморози). Небо не видно",	//49
	"Морось (незамерзающая) слабая с перерывами",	//50
	"Морось (незамерзающая) слабая непрерывная",	//51
	"Морось (незамерзающая) умеренная с перерывами",	//52
	"Морось (незамерзающая) умеренная непрерывная",	//53
	"Морось (незамерзающая) сильная с перерывами",	//54
	"Морось (незамерзающая) сильная непрерывная",	//55
	"Морось замерзающая, образующая гололёд, слабая",	//56
	"Морось замерзающая, образующая гололёд, умеренная или сильная",	//57
	"Морось с дождем слабая",	//58
	"Морось с дождем сильная или умеренная",	//59
	"Дождь (незамерзающий) слабый с перерывами",	//60
	"Дождь (незамерзающий) слабый непрерывный",	//61
	"Дождь (незамерзающий) умеренный с перерывами",	//62
	"Дождь (незамерзающий) умеренный непрерывный",	//63
	"Дождь (незамерзающий) сильный с перерывами",	//64
	"Дождь (незамерзающий) сильный непрерывный",	//65
	"Дождь замерзающий, образующий гололёд, слабый",	//66
	"Дождь замерзающий, образующий гололёд, умеренный или сильный",	//67
	"Дождь со снегом слабый",	//68
	"Дождь со снегом (умеренный или сильный)",	//69
	"Снег слабый с перерывами",	//70
	"Снег слабый непрерывный",	//71
	"Снег умеренный с перерывами",	//72
	"Снег умеренный непрерывный",	//73
	"Снег сильный с перерывами",	//74
	"Снег сильный непрерывный",	//75
	"Снег (возможно с туманом). Ледяные иглы",	//76
	"Снег (возможно с туманом). Снежные зерна",	//77
	"Снег (возможно с туманом). Отдельные снежные кристаллы в виде звездочек",	//78
	"Ледяной дождь",	//79
	"Ливневый дождь слабый",	//80
	"Ливневый дождь умеренный или сильный",	//81
	"Ливневый дождь очень сильный",	//82
	"Ливневый дождь со снегом слабый",	//83
	"Ливневый дождь со снегом умеренный или сильный",	//84
	"Ливневый снег слабый",	//85
	"Ливневый снег умеренный или сильный",	//86
	"Снежная крупа (возможно с дождем) слабая",	//87
	"Снежная крупа (возможно с дождем) умеренная или сильная",	//88
	"Град (возможно с дождем) слабый",	//89
	"Град (возможно с дождем) умеренный или сильный",	//90
	"Гроза, дождь слабый",	//91
	"Гроза, дождь умеренный или сильный",	//92
	"Гроза, дождь со снегом слабые",	//93
	"Гроза, снег или град, умеренные или сильные",	//94
	"Гроза слабая или умеренная (возможен дождь или снег)",	//95
	"Гроза слабая или умеренная (возможен град)",	//96
	"Гроза сильная (возможен дождь или снег)",	//97
	"Гроза сильная (возможен град)",	//98
	"Пыльная или песчаная буря (с осадками или без них)",	//99
    };
    
    private static final String[] weatherCodes7day = {
	null,
        "Небольшая облачность, без осадков",    //1
        "Переменная облачность, без осадков",   //2
        "Облачно с прояснениями, без осадков",  //3
        "Облачно, без осадков", //4
        "Переменная облачность, небольшой кратковременный снег",        //5
        "Переменная облачность, временами снег",        //6
        "Переменная облачность, снег зарядами", //7
        "Переменная облачность, сильный снег зарядами", //8
        "Облачно с прояснениями, небольшой кратковременный снег",       //9
        "Облачно с прояснениями, временами небольшой снег",     //10
        "Облачно с прояснениями, небольшой снег",       //11
        "Облачно с прояснениями, кратковременный снег", //12
        "Облачно с прояснениями, временами снег",       //13
        "Облачно с прояснениями, снег", //14
        "Облачно с прояснениями, снегопад",     //15
        "Облачно с прояснениями, сильный снег зарядами",        //16
        "Облачно с прояснениями, временами сильный снег",       //17
        "Облачно с прояснениями, сильный снег", //18
        "Облачно с прояснениями, временами снег, метель",       //19
        "Облачно с прояснениями, снег, метель", //20
        "Облачно с прояснениями, снегопад, метель",     //21
        "Облачно с прояснениями, сильный снег зарядами, метель",        //22
        "Облачно с прояснениями, временами сильный снег, метель",       //23
        "Облачно с прояснениями, сильный снег, метель", //24
        "Облачно, небольшой кратковременный снег",      //25
        "Облачно, временами небольшой снег",    //26
        "Облачно, небольшой снег",      //27
        "Облачно, кратковременный снег",        //28
        "Облачно, временами снег",      //29
        "Облачно, снег",        //30
        "Облачно, снегопад",    //31
        "Облачно, сильный снег зарядами",       //32
        "Облачно, временами сильный снег",      //33
        "Облачно, сильный снегопад",    //34
        "Облачно, временами снег, метель",      //35
        "Облачно, снег, метель",        //36
        "Облачно, снегопад, метель",    //37
        "Облачно, сильный снег зарядами, метель",       //38
        "Облачно, временами сильный снег, метель",      //39
        "Облачно, сильный снегопад, метель",    //40
        "Переменная облачность, небольшие кратковременные осадки",      //41
        "Переменная облачность, кратковременные осадки",        //42
        "Переменная облачность, кратковременные осадки",        //43
        "Переменная облачность, сильные кратковременные осадки",        //44
        "Облачно с прояснениями, небольшие кратковременные осадки",     //45
        "Облачно с прояснениями, временами небольшие осадки",   //46
        "Облачно с прояснениями, небольшие осадки",     //47
        "Облачно с прояснениями, кратковременные осадки",       //48
        "Облачно с прояснениями, временами осадки",     //49
        "Облачно с прояснениями, осадки",       //50
        "Облачно с прояснениями, осадки",       //51
        "Облачно с прояснениями, сильные кратковременные осадки",       //52
        "Облачно с прояснениями, временами сильные осадки",     //53
        "Облачно с прояснениями, сильные осадки",       //54
        "Облачно, временами небольшие осадки",  //55
        "Облачно, небольшие осадки",    //56
        "Облачно, временами осадки",    //57
        "Облачно, осадки",      //58
        "Облачно, осадки",      //59
        "Облачно, временами сильные осадки",    //60
        "Облачно, сильные осадки",      //61
        "Переменная облачность, небольшой кратковременный дождь",       //62
        "Переменная облачность, кратковременный дождь", //63
        "Переменная облачность, кратковременный дождь", //64
        "Переменная облачность, ливневый дождь",        //65
        "Облачно с прояснениями, небольшой кратковременный дождь",      //66
        "Облачно с прояснениями, временами небольшой дождь",    //67
        "Облачно с прояснениями, небольшой дождь",      //68
        "Облачно с прояснениями, кратковременный дождь",        //69
        "Облачно с прояснениями, временами дождь",      //70
        "Облачно с прояснениями, дождь",        //71
        "Облачно с прояснениями, дождь",        //72
        "Облачно с прояснениями, ливневый дождь",       //73
        "Облачно с прояснениями, временами сильный дождь",      //74
        "Облачно с прояснениями, сильный дождь",        //75
        "Облачно, небольшой кратковременный дождь",     //76
        "Облачно, временами небольшой дождь",   //77
        "Облачно, небольшой дождь",     //78
        "Облачно, кратковременный дождь",       //79
        "Облачно, временами дождь",     //80
        "Облачно, дождь",       //81
        "Облачно, дождь",       //82
        "Облачно, ливневый дождь",      //83
        "Облачно, временами сильный дождь",     //84
        "Облачно, сильный дождь",       //85
    };		

    private static final String[] weatherCodes3day = {
	null,			// 0
	"Облачно, дождь",	// 1
	"Облачно, снег",	// 2
	"Облачно, дождь, возможен град",	// 3
	"Облачно, осадки",			// 4
	"Облачно, без осадков",			// 5
	"Переменная облачность, без осадков",	// 6
	"Малооблачно, без осадков",		// 7
	"Облачно, дождь с грозой",		// 8
	"Переменная облачность, дождь",		// 9
	"Переменная облачность, небольшой дождь",	// 10
	"Облачно, небольшой дождь",		// 11
	"Переменная облачность, небольшой снег",	// 12
	"Облачно, небольшой снег",		// 13
	"Переменная облачность, небольшие осадки",	// 14
	"Облачно, небольшие осадки",		// 15
	"Облачно, без существенных осадков",	// 16
	"Метель",				// 17
    };
	
    // 3-day weather code -> array of 7-day weathero codes
    // An extra step required to select a unique value from each array

    public static final int convTbl3to7[][] = {
	null,
        {33, 34, 70, 71, 72, 73, 74, 75, 80, 81, 82, 83, 84, 85, },     //1
        {13, 14, 15, 16, 17, 18, 29, 30, 31, 32, },     //2
        null,     //3
        {49, 50, 51, 52, 53, 54, 58, 59, 60, 61, },     //4
        {3, 4, },       //5
        {2, },  //6
        {1, },  //7
        null,     //8
        {65, }, //9
        {62, 63, 64, }, //10
        {66, 67, 68, 69, 76, 77, 78, 79, },     //11
        {5, 6, 7, 8, }, //12
        {9, 10, 11, 12, 25, 26, 27, 28, },      //13
        {41, 42, 43, 44, },     //14
        {45, 46, 47, 48, 55, 56, 57, }, //15
        null,     //16
        {19, 20, 21, 22, 23, 24, 35, 36, 37, 38, 39, 40, },     //17
    };

    // weather code -> picture number for 7day forecasts
    // (for 3day forecasts, pictures numbers are the same as codes)  

    public static final int forecast7day_code2pic[] = { 
	-1, 7, 6, 6, 5, 12, 12, 55, 56, 12, 12, 12, 12, 55, 55, 
	56, 56, 56, 56, 57, 58, 59, 59, 59, 59, 
	13, 13, 13, 13, 13, 2, 50, 50, 50, 50, 51, 52, 52, 53, 53, 53, 
	14, 14, 14, 14, 15, 15, 15, 15, 14, 14, 14, 14, 14, 14, 15, 15, 15, 
	4, 4, 4, 4, 10, 10, 10, 54, 10, 10, 10, 10, 10, 9, 9, 
	54, 54, 54, 11, 11, 11, 11, 11, 1, 1, 20, 20, 20 
    };

    public static final int observ_code2pic[] = { 
	-1, -1, -1, -1, 21, 21, 21, 21, -1, -1, 21, -1, -1, -1, -1, -1, 4, 23, 
	-1, -1, 13, 1, 2, 24, 4, 20, 24, -1, 21, 23, -1, -1, -1, -1, -1, -1, 
	17, 17, 17, 17, -1, 21, 21, 21, 21, 21, 21, 21, 21, 21, 11, 11, 11, 11, 
	11, 11, 11, 11, 11, 11, 10, 11, 9, 1, 9, 20, 11, 1, 24, 24, 13, 2, 2, 2, 
	56, 50, 2, 2, 2, 1, 1, 1, 1, 24, 24, 2, 2, 25, 25, -1, -1, 23, 23, 23, 23, 23, 23, 23, 23, -1	
    };	

}


class AsyncTaskWithProgress extends AsyncTask<Void,Void,Void> {
    private ProgressDialog pr = null;
    private Runnable bgr, fgr;
    private WeakReference<Context> contextRef;
    String title;

    AsyncTaskWithProgress(Context context, String msg, Runnable bgr_run, Runnable fgr_run) {
        bgr = bgr_run;
        fgr = fgr_run;
        contextRef = new WeakReference<>(context);
        title = msg;
    }
    @Override
    protected void onPreExecute() {
        Context context = contextRef.get();
        pr = ProgressDialog.show(context, title, context.getString(R.string.op_in_progress), true, true, new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialogInterface) {
                if(pr != null) pr.dismiss();
                cancel(true); // onCancelled() will be called rather than onPostExecute()
            }
        });
	pr.setCancelable(true);
    }
    protected Void doInBackground(Void... args) {
        if(bgr != null) bgr.run();
        return null;
    }
    protected void onPostExecute(Void result) {
        try {
            if(pr != null && pr.isShowing()) pr.dismiss();
        } catch(Exception e) {
            e.printStackTrace();
        }
        pr = null;
        if(fgr != null) fgr.run();
    }
}



