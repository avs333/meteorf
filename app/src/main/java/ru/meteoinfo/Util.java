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
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.Date;
import java.util.HashSet;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
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

import static ru.meteoinfo.WeatherActivity.*;

public class Util {

    static {
	System.loadLibrary("bz2_jni");
    } 	

    public static native int unBzip2(byte [] b, String outfile);

    private static String TAG = "ru.meteoinfo:Util";   

    public static final double inval_coord = -1000.0;

    public static final String URL_STA_LIST = "https://meteoinfo.ru/hmc-output/mobile/st_list.php";  // + query
    public static final String URL_SRV_DATA = "https://meteoinfo.ru/hmc-output/mobile/st_fc.php";    //?p=station

    public static final String STA_LIST_QUERY_PLAIN =	"?p=10"; // plain text list    
    public static final String STA_LIST_QUERY_MD5 = 	"?p=20"; // md5 sum of list
    public static final String STA_LIST_QUERY_SHA256 =	"?p=30"; // sha256 hash of    
    public static final String STA_LIST_QUERY_ZLIB =	"?p=40"; // libz compressed list    
    public static final String STA_LIST_QUERY_BZIP2 =	"?p=50"; // bzip2 compressed list

    public static final int GOOGLE_TIMEOUT = 60000;	// let's take a minute for RKN	
    public static final int OMS_TIMEOUT = 20000;	// on very slow connections
    public static final int SERVER_TIMEOUT = 20000;	// on very slow connections

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

    public static ArrayList<Station> fullStationList = null;

    public static byte[] appendTo(byte[] dest, byte[] src, int add_len) {
        int i, old_len = (dest == null) ? 0 : dest.length;
        byte[] new_b = new byte[old_len + add_len];
        for(i = 0; i < old_len; i++) new_b[i] = dest[i];
        for(i = 0; i < add_len; i++) new_b[i+old_len] = src[i];
	return new_b;
    }

    private static void log(boolean show_ui, int colour, String mesg) {
	if(show_ui) logUI(colour, mesg);
	else {
	    if(colour == COLOUR_ERR) Log.e(TAG, mesg);	
	    else Log.d(TAG, mesg);
	}
    }

    private static void log(boolean show_ui, int colour, int res_id) {
	log(show_ui, colour, App.get_string(res_id));
    }

    private static File getStationsFile(boolean show_ui) {

        InputStream in = null;
        HttpsURLConnection urlConnection = null;
	String sta_file = App.getContext().getFilesDir().toString() + "/station.list";
	final int bufsz = 8*1024;
	byte [] b = new byte[bufsz], result = null;
	int len;

        try {

	    // query md5 of the station list currently stored on the server
	
	    log(show_ui, COLOUR_DBG, R.string.query_md5);
            URL url = new URL(URL_STA_LIST + STA_LIST_QUERY_MD5);
    	
            urlConnection = (HttpsURLConnection) url.openConnection();
	    urlConnection.setReadTimeout(SERVER_TIMEOUT);

	    in = new BufferedInputStream(urlConnection.getInputStream());
	    len = in.read(b, 0, bufsz);
	    if(len <= 0) {	
		log(show_ui, COLOUR_ERR, R.string.conn_bad);
		return null;
	    }	
	    urlConnection.disconnect();
	    in.close();
	    urlConnection = null;
	    in = null;	

	    // check md5 against last_md5 saved in preferences before

	    String md5 = new String(b);	
	    md5 = md5.substring(0, len);
	    if(md5.startsWith("Rez "))	md5 = md5.substring(5);
   	    SharedPreferences settings =  PreferenceManager.getDefaultSharedPreferences(App.getContext());
	    String last_md5 = settings.getString("last_md5", null); 

	    if(last_md5 == null || !md5.equals(last_md5) || !(new File(sta_file)).exists()) {	

		// md5 mismatch or no station file yet, obtain its bz2 file
		log(show_ui, COLOUR_INFO, R.string.retr_sta_list);

		url = new URL(URL_STA_LIST + STA_LIST_QUERY_BZIP2);
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
		    log(show_ui, COLOUR_ERR, R.string.bzip2_rec_failure);		    
		    return null; 	
		}	

		float kbs = (end_time > start_time) ? ((float) result.length)/((float) (end_time - start_time)) : 0;
		String s = String.format(App.get_string(R.string.bzip2_rec_ok), kbs);
		log(show_ui, COLOUR_INFO, s);

		// unbzip the station file

		int unzipped_size = unBzip2(result, sta_file);

		if(unzipped_size <= 0) {
		    log(show_ui, COLOUR_ERR, R.string.bzip2_dec_failure);		    
		    return null;	
		}

		s = String.format(App.get_string(R.string.bzip2_dec_ok), result.length, unzipped_size);
		log(show_ui, COLOUR_DBG, s);

		// save md5 in preferences

		last_md5 = md5;
		SharedPreferences.Editor editor = settings.edit();
		editor.putString("last_md5", last_md5);	
		editor.commit();

	    } else {
		// No need to do anything. sta_file exists and its md5 matches with the server's
		log(show_ui, COLOUR_INFO, R.string.sta_unchanged);
	    }	

	    return new File(sta_file);

	} catch (java.net.ConnectException c) {
	    log(show_ui, COLOUR_ERR, R.string.no_server_conn);
	    return null;	
	} catch(java.net.SocketTimeoutException je) {
	    log(show_ui, COLOUR_ERR, URL_STA_LIST + ": " + App.get_string(R.string.read_timeout));
	    return null;		
        } catch (Exception e) {
	    log(show_ui, COLOUR_ERR, R.string.err_exception);	
            e.printStackTrace();
            return null;
        } finally {
            try {
                if(in != null) in.close();
                if(urlConnection != null) urlConnection.disconnect();
            } catch(Exception e) {
	    	log(show_ui, COLOUR_ERR, R.string.err_exception);	
		e.printStackTrace();
            }
        }
    } 

    synchronized public static boolean getStations(boolean show_ui) {

	boolean ok = false;
	FileInputStream inf = null;
	final int bufsz = 8*1024;
	byte [] b = new byte[bufsz], result = null;
	int len, i;

        try {

	    File stations = getStationsFile(show_ui);
	    if(stations == null) return false;	   

	    // First, read it to array

	    inf = new FileInputStream(stations);
	    result = null;	
            while ((len = inf.read(b,0,bufsz)) > 0) result = appendTo(result, b, len);	
	    inf.close();
	    inf = null; 	

	    if(result == null) {
		log(show_ui, COLOUR_ERR, R.string.err_read_sta);
	    	return false;
	    }  	

	    // Convert file contents from byte array to string

	    String s = new String(result);	

	    // Split the string into lines

	    String[] std, stans;
 	    stans = s.split("\n");
	    if(stans.length < 1) {
		log(show_ui, COLOUR_ERR, R.string.empty_sta_list);
		return false;
	    }

	    // Parse and add each station to fullStationList

	    log(show_ui, COLOUR_DBG, R.string.parse_list);

	    fullStationList = new ArrayList<>();	
	
	    for(i = 0; i < stans.length; i++) {
		if(stans[i].startsWith("16169")) {  // known dead station next to Kievsky vokzal
	    	    log(show_ui, COLOUR_DBG, R.string.skip_kiev);
		    continue;
	        }
		std = stans[i].split(";");
		if(std.length < 5) {
		    log(show_ui, COLOUR_DBG, App.get_string(R.string.inv_sta_data) + i);
		    continue;
		}
		Station sta = new Station();	
		try {
		    sta.code = Long.parseLong(std[0]);	
		    if(!std[1].equals("")) sta.wmo = Long.parseLong(std[1]);	
		    sta.latitude = Double.parseDouble(std[2]);	
		    sta.longitude = Double.parseDouble(std[3]);
		} catch(java.lang.NumberFormatException nfe) {
		    log(show_ui, COLOUR_DBG, App.get_string(R.string.inv_sta_data) + i);
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
	    log(show_ui, COLOUR_INFO, s); 		

	    ok = true;	

        } catch (Exception e) {
	    log(show_ui, COLOUR_ERR, R.string.err_exception);	
            e.printStackTrace();
            return false;
        } finally {
            try {
                if(inf != null) inf.close();
            } catch(Exception e) {
	    	log(show_ui, COLOUR_ERR, R.string.err_exception);	
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

// https://wiki.openstreetmap.org/wiki/Nominatim -- takes lat/lon coordinates, returns some human-readable
// address in the proximity. NB: user-agent +must+ be specified (no permission otherwise), see getAddress() in Utils.

    public static String OSM_GEOCODING_URL = "https://nominatim.openstreetmap.org/reverse?format=json&accept-language=ru,en"; // &lat=...&lon=...	
    public static String getAddress(double lat, double lon) {
        HttpsURLConnection urlConnection = null;
        InputStream in = null;
        JsonReader reader = null;
        String addr = null;
        try {
            URL url = new URL(OSM_GEOCODING_URL +  // lat + "," + lon); 
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
}

