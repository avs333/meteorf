package ru.meteoinfo;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.FileInputStream; 
import java.io.File;
import java.net.HttpURLConnection;
import javax.net.ssl.HttpsURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.Date;
import java.util.HashSet;
import java.util.TimeZone; 
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.Uri;
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


public class Util {

    static {
	System.loadLibrary("bz2_jni");
    } 	

    public static native int unBzip2(byte [] b, String outfile);

    private static String TAG = "ru.meteoinfo:Util";   

    public static final double inval_coord = -1000.0;

// https://wiki.openstreetmap.org/wiki/Nominatim -- takes lat/lon coordinates, returns some human-readable
// address in the proximity. NB: user-agent +must+ be specified (no permission otherwise), see getAddress() in Utils.
    public static final String URL_OSM_GEOCODING = "https://nominatim.openstreetmap.org/reverse?format=json&accept-language=ru,en"; // &lat=...&lon=...	

    public static final String URL_STA_LIST = "https://meteoinfo.ru/hmc-output/mobile/st_list.php";  // + query
    public static final String URL_STA_DATA = "https://meteoinfo.ru/hmc-output/mobile/st_fc.php";    //?p=station
    public static final String URL_WEATHER_DATA = "https://meteoinfo.ru/hmc-output/mobile/st_obsfc.php";    //?p=query&st=station
    public static final String GOOGLE_LL = "http://maps.googleapis.com/maps/api/geocode/json?latlng="; // lat,lon&language=

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

/*  No class declarations in java, here's what we define:
    class Station;
    class WeatherInfo;
    class WeatherData; */

    // Main exported globals. 
    // Should make them private, and return from get*'s synchronised with their updates

    public static Location currentLocation = null;
    public static Station currentStation = null;
    public static ArrayList<Station> fullStationList = null;
    public static WeatherData localWeather = null;

    public static class Station {
        String name = null;
        String country = null;
        String name_p = null;
        long code = -1;
	long wmo = -1;	
        double latitude  = inval_coord;
        double longitude = inval_coord;
        @Override
        public String toString() {	// override for ListAdapter
            return name_p;
        }
        public String getInfo() {
            String info = App.get_string(R.string.station) + ": " + code;
            if(wmo != -1) info += "\n" + "WMO id: " + wmo;
            if(name != null) info += "\n" + App.get_string(R.string.name) + ": " + name;
            if(country != null) info += "\n" + App.get_string(R.string.country) + ": " + country;
            if(name_p != null) info += "\n[" + name_p + "]";
            if(latitude != inval_coord) info += "\n" + App.get_string(R.string.latitude) + " " + latitude;
            if(longitude != inval_coord) info += "\n" + App.get_string(R.string.longitude) + " " + longitude;
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
        HttpsURLConnection urlConnection = null;
        InputStream in = null;
	final int bufsz = 8*1024, len;
	byte [] b = new byte[bufsz];
        try {
            url = new URL(url_str);
            urlConnection = (HttpsURLConnection) url.openConnection();
            urlConnection.setReadTimeout(SERVER_TIMEOUT);
            in = new BufferedInputStream(urlConnection.getInputStream());
	    len = in.read(b, 0, bufsz);
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
            } catch (Exception e) {}
        }
	if(len < 1) {
	//  log(COLOUR_ERR, R.string.empty_string);	
	    return null;
	}	
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
		editor.commit();

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

    synchronized public static boolean getStations() {

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
	    	    log(COLOUR_DBG, R.string.skip_kiev);
		    continue;
	        }
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
        ArrayList<Station> stations = new ArrayList<>();
	int i;
	String pat = pattern.toLowerCase();
	for(i = 0; i < fullStationList.size(); i++) {
	   Station st = fullStationList.get(i);		    	
	   String s1 = st.name.toLowerCase();
	   if(s1.startsWith(pat)) {
		stations.add(st);
	   } 		
	}
	return stations;
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
	//  Log.d("meteoinfo.ru", url.toString() + " response=" + response);	
	    if (response != HttpsURLConnection.HTTP_OK) return null;
            in = new BufferedInputStream(urlConnection.getInputStream());
            reader = new JsonReader(new InputStreamReader(in, "UTF-8"));
            reader.beginObject();
            while(reader.hasNext()) {
                String name = reader.nextName();
                if(name.equals("display_name")) {
                    addr = reader.nextString();
                    break;
                } else reader.skipValue();
            }
        } catch (Exception e) {
            Log.e("meteoinfo.ru", "exception in getAdderss()");
            e.printStackTrace();
        } finally {
            try {
                if(reader != null) reader.close();
                if(in != null) in.close();
                if(urlConnection != null) urlConnection.disconnect();
            } catch (Exception e) {}
        }
        return addr;
    }

    public static String getAddressFromGoogle(double lat, double lon) {
        InputStream in = null;
        HttpURLConnection urlConnection = null;
        JsonReader reader = null;
        String addr = null;
        try {
	    String url_str = GOOGLE_LL + lat + "," + lon + "&sensor=false&language=ru"; // + (use_russian ? "ru" : "en");
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


    public static String windDegreesToString(double degrees) {
	String[] directions = App.getContext().getResources().getStringArray(R.array.wind_dirs);
	return directions[(int)Math.round(((degrees % 360) / 45))];
    }  	

    public static class WeatherInfo {
	// won't bother converting floats to strings and back
	private String params[] = null;
	private int type = -1;
        public boolean valid = false;
	// "yyyy-MM-dd HH:mm UTC"

	public int gettype() { return type; }
	public String get_date() { return params[0]; } 
//	public String get_time() { return params[1]; } 
	public String get_pressure() { return params[2]; }
	public String get_temperature() { return params[3]; }
	public String get_wind_dir() { return params[4]; }
	public String get_wind_speed() { return params[5]; }
	public String get_info() { 
	    String ret = null;	
	    if(params[6] == null || params[6].isEmpty()) return null;
	    try {
		int i = Integer.parseInt(params[6]);
		switch(type) {
		    case WEATHER_REQ_OBSERV:
			ret = new String(weatherCodesObserv[i]);
			break;	
		    case WEATHER_REQ_7DAY:
			ret = new String(weatherCodes7day[i]);
			break;
		    case WEATHER_REQ_3DAY:
			// ???
			break; 
		}	
	    } catch(Exception e) {
		Log.e(TAG, "error parsing weather code " + params[6] + " for type " + type);
	    }	 	
	    return ret; 
	}
	public String get_precip() { return params[7]; }
	public String get_precip3h() { return (type == WEATHER_REQ_OBSERV) ? params[8] : null; }
	public String get_precip6h() { return (type == WEATHER_REQ_OBSERV) ? params[9] : null; }
	public String get_precip12h() { return (type == WEATHER_REQ_OBSERV) ? params[10] : null; }
	public String get_humidity() { 
	    switch(type) {
		case WEATHER_REQ_OBSERV: 
		    return params[11];
		case WEATHER_REQ_3DAY:
		    return params[8];
		default:
		    return null;
	    }
	}
	public String get_visibility() { return (type == WEATHER_REQ_OBSERV) ? params[12] : null; }
	public String get_clouds() { return (type == WEATHER_REQ_OBSERV) ? params[13] : null; }
	public String get_gusts() { return (type == WEATHER_REQ_OBSERV) ? params[14] : null; }

	public WeatherInfo(int type, String s) {
	    int i, params_sz;
	    if(s == null) return;  	

	    //Log.i(TAG, "string=<" + s + ">"); 	
	   	
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

	    if(p.length < params_sz) {
		Log.e(TAG, "invalid weatherinfo string: " + p.length + "<" + params_sz);
		Log.e(TAG, "string was <" + s + ">");
		return;
	    }

	    this.type = type;	
	    params = new String[params_sz];
	    for(i = 0; i < params_sz; i++) {
		if(p[i] == null || p[i].isEmpty()) params[i] = null;
		else params[i] = new String(p[i]);
	    }	

	    // rewrite a bit to speedup future access
	    try {
//		Locale loc = use_russian ? new Locale("ru", "RU") : new Locale("en", "US");
		Locale loc = new Locale("ru", "RU");
	        if(type == WEATHER_REQ_7DAY) {
		    SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd");
		    Date date = in.parse(params[0]);
		    SimpleDateFormat out = new SimpleDateFormat("EEEE, d MMM", loc);
		    if(params[1].equals("night")) params[1] = new String(App.get_string(R.string.night));
		    else if(params[1].equals("day")) params[1] = new String(App.get_string(R.string.day));
		    params[0] = new String(out.format(date) + ", " + params[1]);
		} else {
		    SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd HH:mm");
		    in.setTimeZone(TimeZone.getTimeZone("UTC"));
		    Date date = in.parse(params[0] + " " + params[1]);
		    SimpleDateFormat out = new SimpleDateFormat("HH:mm EEEE, d MMM", loc);
		    out.setTimeZone(TimeZone.getDefault());
		    params[0] = new String(out.format(date));    
		}
		params[1] = null;
	    } catch(Exception e) {
		Log.e(TAG, "error parsing date");
	    }

/*	    try {
		double d = Double.parseDouble(get_wind_dir());
		if(d >= 0.0 && d <= 360.0) params[4] = new String(windDegreesToString(d));
	    } catch(Exception e) {
		Log.e(TAG, "error parsing wind direction");
		params[4] = null;
	    }	 
	    if(type == WEATHER_REQ_OBSERV && params[12] != null) { // visibility -> km
		try {
		    double d = Double.parseDouble(params[12]);
		    d /= 1000.0;
		    params[12] = String.format("%.1g", d);				    	
		} catch(Exception e) { }
	    } 	*/      	 	

	    valid = true; 	
	}
    }

    // All weather data for a particular station

    public static class WeatherData {
	long sta_code = -1;
	WeatherInfo observ = null;	// of type WEATHER_REQ_OBSERV
        ArrayList<WeatherInfo> for7days = null;	// of type WEATHER_REQ_7DAY 
        ArrayList<WeatherInfo> for3days = null;	// of type WEATHER_REQ_7DAY
    }

    synchronized public static WeatherData getWeather(long station_code) {

	log(COLOUR_DBG, App.get_string(R.string.query_weather_data) + " " + station_code);

	String so = getShortStringFromURL(URL_WEATHER_DATA + WEATHER_QUERY_OBSERV + "&st=" + station_code);
	String s7 = getShortStringFromURL(URL_WEATHER_DATA + WEATHER_QUERY_7DAY + "&st=" + station_code);
	String s3 = getShortStringFromURL(URL_WEATHER_DATA + WEATHER_QUERY_3DAY + "&st=" + station_code);

	if(so == null && s7 == null && s3 == null) {
	    log(COLOUR_ERR, App.get_string(R.string.no_weather_data) + " " + station_code);
	    return null;
	}
	WeatherData ret = new WeatherData();
	ret.sta_code = station_code;

	if(so != null && !so.isEmpty()) {
	    WeatherInfo wi = new WeatherInfo(WEATHER_REQ_OBSERV, so);
	    if(wi != null && wi.valid) {
		ret.observ = wi;
		log(COLOUR_DBG, R.string.observed_data_okay);
	    } else log(COLOUR_ERR, R.string.observed_data_bad);	
	} else log(COLOUR_ERR, R.string.observed_data_bad);
	if(s7 != null && !s7.isEmpty()) {
	    String[] ws = s7.split(" \n");
	    ret.for7days = new ArrayList<>();	    		     
	    for(int i = 0; i < ws.length; i++) {		
		if(ws[i] != null) {
		    WeatherInfo wi = new WeatherInfo(WEATHER_REQ_7DAY, ws[i]);
		    if(wi != null && wi.valid) ret.for7days.add(wi);
		}	
	    }	
	    if(ret.for7days.size() > 0) log(COLOUR_DBG, R.string.weekly_data_okay);
	    else log(COLOUR_ERR, R.string.weekly_data_bad);	
	} else log(COLOUR_ERR, R.string.weekly_data_bad);
	if(s3 != null && !s3.isEmpty()) {
	    String[] ws = s3.split(" \n");
	    ret.for3days = new ArrayList<>();	    		     
	    for(int i = 0; i < ws.length; i++) {		
		if(ws[i] != null) {
		    WeatherInfo wi = new WeatherInfo(WEATHER_REQ_3DAY, ws[i]);
		    if(wi != null && wi.valid) ret.for3days.add(wi);
		}	
	    }	
	    if(ret.for3days.size() > 0) log(COLOUR_DBG, R.string.hourly_data_okay);
	    else log(COLOUR_ERR, R.string.hourly_data_bad);	
	} else log(COLOUR_ERR, R.string.hourly_data_bad);

	return ret;

    }

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
	"Ливневой дождь слабый",	//80
	"Ливневой дождь умеренный или сильный",	//81
	"Ливневой дождь очень сильный",	//82
	"Ливневой дождь со снегом слабый",	//83
	"Ливневой дождь со снегом умеренный или сильный",	//84
	"Ливневой снег слабый",	//85
	"Ливневой снег умеренный или сильный",	//86
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
}


