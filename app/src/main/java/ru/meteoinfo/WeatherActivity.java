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
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.method.ScrollingMovementMethod; 
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView; 
import android.widget.Toast;

import com.google.android.gms.common.GoogleApiAvailability;
//import com.google.android.gms.location.*;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
//import static com.google.android.gms.location.LocationServices.getFusedLocationProviderClient;

import ru.meteoinfo.Util.Station;


public class WeatherActivity extends AppCompatActivity 
	implements NavigationView.OnNavigationItemSelectedListener {


    private static String TAG = "ru.meteoinfo:WeatherActivity";   
 
//    public static final String URL_STA_LIST = "https://meteoinfo.ru/tmp/1/mobile/mobile_stan.php";
//    public static final String URL_SRV_DATA = "https://meteoinfo.ru/tmp/1/mobile/mobile_data.php"; //?p=station


    public static final String GOOGLE_LL = "http://maps.googleapis.com/maps/api/geocode/json?latlng="; // lat,lon&language=


    private final int ST_LIST_PREPARE_DLG = 1;
    private final int ST_LIST_PROCESS_DLG = 2;
    private final int ST_LAUNCH_DLG = 3;
    private final int SEL_ST_COORD_DLG = 4;
    private final int SEL_FAV = 5;

    public static final int COLOUR_ERR = 0xc00000;	
    public static final int COLOUR_INFO = 0xc0;	
    public static final int COLOUR_GOOD = 0xc000;	
    public static final int COLOUR_DBG = 0x808080;	

    private static final int PREF_ACT_REQ = 22;

    // 0 -> only COLOUR_ERR
    // 1 -> all excluding COLOUR_DBG
    // 2 -> all
    // all means all, not only the four defined above.
    private static int verbose = 1;

    // 0 -> none
    // 1 -> google
    // 2 -> OSM (always used by widget)
    private static int addr_source = 0;      	    
 
    private final long LOC_UPDATE_INTERVAL = 20 * 1000;
    private final long LOC_FASTEST_UPDATE_INTERVAL = 2000; /* 2 sec */

//    private final int locPriority = LocationRequest.PRIORITY_HIGH_ACCURACY;	
    private final int locPriority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY;	

    public static String formattedLocString = null;

    public static AppCompatActivity mainAct;    // this activity

    private static ArrayList<Station> matchingStationList = null;

    public static Station curStation = null;
    public static Location curLocation = null;

    // either coordinates of curLocation, or explicitly entered ones
    public static double requestedLon = Util.inval_coord;
    public static double requestedLat = Util.inval_coord;

    public static boolean serverAvail = false;

    public static boolean use_russian = true;
    public static boolean use_offline_maps = true;

    private static Prefs prefs = null;
    private static Set<String> favs;


    private String get_formatted_address(double lat, double lon) {
	if(addr_source == 0) return null;
	else if(addr_source == 2) return Util.getAddress(lat, lon);
        InputStream in = null;
        HttpURLConnection urlConnection = null;
        JsonReader reader = null;
        String addr = null;
        try {
	    String url_str = GOOGLE_LL + lat + "," + lon + "&sensor=false&language=" + (use_russian ? "ru" : "en");
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
				if(addr == null) logUI(COLOUR_DBG, R.string.err_google);
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
		logUI(COLOUR_DBG, err_msg);
	    } else logUI(COLOUR_DBG, R.string.err_google);

            if(addr != null) logUI(COLOUR_DBG, getString(R.string.found_sta_info) + " " + addr);
	    reader.close();
            reader = null;
	} catch(java.net.SocketTimeoutException je) {
	    logUI(COLOUR_ERR, "http://maps.googleapis.com: " + getString(R.string.read_timeout));
	    return null;		
        } catch(Exception e) {
	    logUI(COLOUR_ERR, R.string.err_exception);	
	    e.printStackTrace();
            return null;
        } finally {
            try {
                if(reader != null) reader.close();
                if(in != null) in.close();
                if(urlConnection != null) urlConnection.disconnect();
            } catch(Exception e) {
       		logUI(COLOUR_ERR, R.string.err_exception);	
		e.printStackTrace();
                addr = null;
            }
        }
        return addr;
    }


    protected void startLocationUpdates() {

	logUI(COLOUR_DBG, "startLocationUpdates");

	LocationRequest loc_request = new LocationRequest();
	loc_request.setPriority(locPriority);
	loc_request.setInterval(LOC_UPDATE_INTERVAL);
	loc_request.setFastestInterval(LOC_FASTEST_UPDATE_INTERVAL);
 
	LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
	builder.addLocationRequest(loc_request);
	LocationSettingsRequest loc_set_request = builder.build();

	// Check whether location settings are satisfied
	// https://developers.google.com/android/reference/com/google/android/gms/location/SettingsClient
	SettingsClient sett_client = LocationServices.getSettingsClient(this);
	sett_client.checkLocationSettings(loc_set_request);

        LocationCallback loc_callback = new LocationCallback() {
	    @Override
	    public void onLocationResult(LocationResult locationResult) {
		onLocationChanged(locationResult.getLastLocation());
	    }   
	};
	LocationServices.getFusedLocationProviderClient(this).requestLocationUpdates(
	    loc_request, loc_callback, Looper.myLooper());
    }

    public void onLocationChanged(Location location) {
	if(location == null) {
	    logUI(COLOUR_ERR, R.string.null_coords);	
	    return;		
	}
	curLocation = new Location(location);
//	String s = String.format(getString(R.string.cur_coord), location.getLatitude(), location.getLongitude());
//	logUI(COLOUR_DBG, s);
    }

    public void getLastLocation() {
	logUI(COLOUR_DBG, "getLastLocation()");
	FusedLocationProviderClient locationClient = LocationServices.getFusedLocationProviderClient(this);
	locationClient.getLastLocation()
	    .addOnSuccessListener(new OnSuccessListener<Location>() {
		@Override
		public void onSuccess(Location location) {
                    if(location != null) {
			String s = String.format(getString(R.string.cur_coord), location.getLatitude(), location.getLongitude());
			logUI(COLOUR_DBG, s);
			onLocationChanged(location);
		    }	
		}
            })
            .addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(Exception e) {
               	    logUI(COLOUR_ERR, R.string.inv_last_coords);	
		    e.printStackTrace();
                }
            });
    }

    boolean maps_avail = false;

    protected  void onActivityResult(int req, int res, Intent data) {
        logUI(COLOUR_DBG, "onActivityResult: req=" + req + ", res=" + res);
	if(req == PREF_ACT_REQ) prefs.load();
        if(res == 0) maps_avail = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        GoogleApiAvailability ga = GoogleApiAvailability.getInstance();
        int result = ga.isGooglePlayServicesAvailable(this);
        logUI(COLOUR_DBG, "google availability=" + (result == 0 ? "ok" : "no"));
        if(result != 0) {
            Dialog dlg = ga.getErrorDialog(this,result,555);
            dlg.show();
        } else {
            maps_avail = true;
        }
	getLastLocation();
	startLocationUpdates(); 
	App.activity_visible = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
	App.activity_visible = false;
    }

//    public static NavigationView navigationView;

    public static Menu navMenu;
    private static int menu_size;
    private static TextView tview;
    private static int fav_menu_idx = -1;

    static Handler ui_update = new Handler() {
	@Override
	public void handleMessage(Message msg) {
	    super.handleMessage(msg);
	    Bundle b = msg.getData();
	    if(b == null) return;
	    String s = b.getString("mesg");
	    int col = b.getInt("colour");	
	    if(s == null) return;
	    String ss = String.format("<font color=#%06X>%s</font><br>", col, s);		
	    tview.append(Html.fromHtml(ss));
	}
    };


    public static void logUI(int colour, int res_id) {
	String str = App.get_string(res_id);
	logUI(colour, str);
    }	

    public static void logUI(int colour, String str) {
	if(str == null) return;
	if(verbose == 0 && colour != COLOUR_ERR) return;
	else if(verbose == 1 && colour == COLOUR_DBG) return;
	Message msg = new Message();
	Bundle b = new Bundle();
	b.putString("mesg", str);
	b.putInt("colour", colour);
	msg.setData(b);
	ui_update.sendMessage(msg);
    }	

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mainAct = this;

        ConnectivityManager cman = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cman == null || cman.getActiveNetworkInfo() == null) {
            String msg = getString(R.string.no_internet);
            Log.e(TAG + getClass().getSimpleName(), msg);
	    Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
	    finish();
	    return;
        }
 	prefs = new Prefs();

        Locale loc = use_russian ? new Locale("ru", "RU") : new Locale("en", "US");
        Locale.setDefault(loc);
        Resources res = this.getResources();
        Configuration config = new Configuration(res.getConfiguration());
        if (Build.VERSION.SDK_INT >= 17) config.setLocale(loc);
        else config.locale = loc;
        res.updateConfiguration(config, res.getDisplayMetrics());

        setContentView(R.layout.activity_weather);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

	tview = findViewById(R.id.text_view_id);
	tview.setMovementMethod(new ScrollingMovementMethod());

        final DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);

        drawer.addDrawerListener(toggle);
        toggle.syncState();

//	drawer.openDrawer(android.view.Gravity.LEFT, true);
//	drawer.openDrawer(GravityCompat.START, true);

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

	navMenu = navigationView.getMenu();
	menu_size = navMenu.size();

	
	for(int i = 0; i < menu_size; i++) {
	    MenuItem mi = navMenu.getItem(i);
	    if(Util.fullStationList == null) mi.setEnabled(false);
	    if(mi.getTitle().equals(getString(R.string.select_fav))) {
		fav_menu_idx = i;
	    }		
	}
	if(Util.fullStationList != null) {
	    logUI(COLOUR_GOOD, R.string.init_already);	
	    if(favs != null && fav_menu_idx != -1) navMenu.getItem(fav_menu_idx).setEnabled(true);
	    else navMenu.getItem(fav_menu_idx).setEnabled(false);
	} else connect_to_server();
    }

    private void connect_to_server() {
	Runnable rr = new Runnable() {
	    @Override
	    public void run() {
		logUI(COLOUR_INFO, R.string.checking_server_conn);
		serverAvail = Util.getStations(true);
		if(serverAvail) {
		    logUI(COLOUR_GOOD, R.string.conn_ok);	
		    ui_update.post(new Runnable() {	
			public void run() {
			    for(int i = 0; i < menu_size; i++) navMenu.getItem(i).setEnabled(true);
			    if(favs != null && fav_menu_idx != -1) navMenu.getItem(fav_menu_idx).setEnabled(true);
			    else navMenu.getItem(fav_menu_idx).setEnabled(false);
			}
		    });
                }
	    }		
	};
	Thread thd = new Thread(rr);
	thd.start();
    }	

    public static boolean urlAccessible(String url, int timeout_ms) { 
	try {
	    HttpURLConnection.setFollowRedirects(false);
	    HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
	    con.setRequestMethod("HEAD");
	    con.setConnectTimeout(timeout_ms);
	    return (con.getResponseCode() == HttpURLConnection.HTTP_OK);
	} catch (Exception e) {
	    return false;
	}
    }

    @Override
    public void onBackPressed() {
	DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
	if(drawer.isDrawerOpen(GravityCompat.START)) {
	    drawer.closeDrawer(GravityCompat.START);
	} else {
	    super.onBackPressed();
	}
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {

        int id = item.getItemId();

        try {
	  switch(id) {

	    case R.id.cur_loc:
		Runnable fgr = new Runnable() {
		    @Override
		    public void run() {
			if(curLocation == null) {
			   Toast.makeText(getApplicationContext(), getString(R.string.failed_to_find_loc), Toast.LENGTH_LONG).show();
			   return;
		        }
			curStation = Util.getNearestStation(requestedLat, requestedLon);
			if(curStation == null) {
			    Toast.makeText(getApplicationContext(), getString(R.string.no_station), Toast.LENGTH_LONG).show();
			    return;
			} 	
			logUI(COLOUR_DBG, getString(R.string.sta_loc) + ": " + curStation.name_p + " #" + curStation.code);
			removeDialog(ST_LAUNCH_DLG);
			showDialog(ST_LAUNCH_DLG);
		    } 	
		};
		Runnable bgr = new Runnable() {
		    @Override
		    public void run() {
			if(curLocation == null) return;
			requestedLat = curLocation.getLatitude();
			requestedLon = curLocation.getLongitude();
			formattedLocString = get_formatted_address(requestedLat, requestedLon);
		    }
		};
		AsyncTaskWithProgress atp = new AsyncTaskWithProgress(mainAct, getString(R.string.receiving_data), bgr, fgr);
                atp.execute();
		break;

	    case R.id.sel_loc:
	        formattedLocString = null;
                removeDialog(ST_LIST_PREPARE_DLG);
                showDialog(ST_LIST_PREPARE_DLG);
		break;
	    case R.id.sel_coord:
                formattedLocString = null;
                removeDialog(SEL_ST_COORD_DLG);
                showDialog(SEL_ST_COORD_DLG);
		break;
	    case R.id.sel_fav:
                formattedLocString = null;
                removeDialog(SEL_FAV);
                showDialog(SEL_FAV);
		break;
	  }
        } catch (Exception e) {
	    logUI(COLOUR_ERR, R.string.err_exception);
	    e.printStackTrace();	
        }

/*
	// won't do that!!!
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START); */

        return true;

    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
	    prefs.save();	
            Intent i = new Intent(this, SettingsActivity.class);
            startActivityForResult(i, PREF_ACT_REQ);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    EditText edit_pattern;              // for pattern in ST_LIST_PREPARE_DLG
    EditText lat_input, lon_input;      // for SEL_ST_COORD

    public void onPrepareDialog(int id, Dialog dlg) {
        switch(id) {
            case ST_LIST_PREPARE_DLG:
                AlertDialog dialog = (AlertDialog) dlg;
                edit_pattern = dialog.findViewById(R.id.EditLocPatt);
                edit_pattern.setText("");
                dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                    @Override
                    public void onShow(DialogInterface dialog) {    // Show soft keyboard at once
                            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                            if(imm != null) imm.showSoftInput(edit_pattern, InputMethodManager.SHOW_IMPLICIT);
                    }
                });
                break;
            case SEL_ST_COORD_DLG:
                AlertDialog dialog1 = (AlertDialog) dlg;
                lat_input = dialog1.findViewById(R.id.EditLat);
                lon_input = dialog1.findViewById(R.id.EditLong);
                lat_input.setText("");
                lon_input.setText("");
                lat_input.requestFocus();
                dialog1.setOnShowListener(new DialogInterface.OnShowListener() {
                    @Override
                    public void onShow(DialogInterface dialog) {    // Show soft keyboard at once
                            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                            if(imm != null) imm.showSoftInput(lat_input, InputMethodManager.SHOW_IMPLICIT);
                    }
                });
                break;
        }
    }

    public Dialog onCreateDialog(int id) {
        try {
            switch (id) {
                case ST_LIST_PREPARE_DLG:
                    return prepareStationList();
                case ST_LIST_PROCESS_DLG:
                    return processStationList();
                case ST_LAUNCH_DLG:
                    return do_launch();
                case SEL_ST_COORD_DLG:
                    return selectStationCoordinates();
		case SEL_FAV:
		    return selFavList();
            }
        } catch (Exception e) {
	    logUI(COLOUR_ERR, R.string.err_exception);
            e.printStackTrace();
        }
        return null;
    }

    public Dialog prepareStationList() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        builder.setView(inflater.inflate(R.layout.loc_sel_layout, null))
                .setPositiveButton(R.string.okey, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        final String patt = edit_pattern.getText().toString();
                        if(patt.length() < 2) {
                            Toast.makeText(getApplicationContext(), getString(R.string.short_patt), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Runnable bgr = new Runnable() {
                            @Override
                            public void run() {
                                matchingStationList = Util.getMatchingStationsList(patt);
                            }
                        };
                        Runnable fgr = new Runnable() {
                            @Override
                            public void run() {
                                if(matchingStationList == null || matchingStationList.size() == 0)
                                    Toast.makeText(getApplicationContext(),
                                            getString(R.string.patt_no_match) + " " + patt, Toast.LENGTH_SHORT).show();
                                else {
                                    removeDialog(ST_LIST_PROCESS_DLG);
                                    showDialog(ST_LIST_PROCESS_DLG);
                                }
                            }
                        };
                        AsyncTaskWithProgress atp = new AsyncTaskWithProgress(mainAct, getString(R.string.receiving_stl), bgr, fgr);
                        atp.execute();
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        removeDialog(ST_LIST_PREPARE_DLG);
                        matchingStationList = null;
                    }
                });

        return builder.create();
    }



    public Dialog processStationList() {
        if(matchingStationList == null || matchingStationList.size() == 0) {
	    logUI(COLOUR_DBG, getString(R.string.err_in) + " processStationList");	
            return null;
        }
        final Dialog dialog = new Dialog(this);
    	dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.list_layout);

        Window wnd = dialog.getWindow();
        if(wnd != null ) wnd.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);

        ListView st_list = dialog.findViewById(R.id.StationList);
        final ArrayAdapter<Station> aas = new ArrayAdapter<>(this,android.R.layout.simple_list_item_1, matchingStationList);
        st_list.setAdapter(aas);
        st_list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                curStation = matchingStationList.get(i);
		if(curStation == null) {
		    logUI(COLOUR_ERR, R.string.no_cur_sta);
		    return;	
		}
	        aas.clear();
		final Runnable fgr = new Runnable() {
 	            @Override
		    public void run() {
			if(curStation == null) {
			    Toast.makeText(getApplicationContext(), getString(R.string.no_station), Toast.LENGTH_LONG).show();
			    return;	
			}
			requestedLon = Util.inval_coord;
			requestedLat = Util.inval_coord;
	                removeDialog(ST_LIST_PROCESS_DLG);
	                removeDialog(ST_LAUNCH_DLG);
	                showDialog(ST_LAUNCH_DLG);
		    }
		};
		if(addr_source > 0) {
		    final Runnable bgr = new Runnable() {
       		        @Override
			public void run() {
			    if(curStation != null) formattedLocString = get_formatted_address(curStation.latitude, curStation.longitude);
		        }
		    };
		    AsyncTaskWithProgress atp = new AsyncTaskWithProgress(mainAct, getString(R.string.try_google), bgr, fgr);
		    atp.execute();
		} else fgr.run();
            }
        });

        return dialog;
    }

    private Station getFavStation(String fav) {
	try {
	    long k = Long.parseLong(fav);
	    for(int i = 0; i < Util.fullStationList.size(); i++) {
		Station st = Util.fullStationList.get(i);
		if(k == st.code) return st;
	    }
        } catch (Exception e) {
	    logUI(COLOUR_ERR, getString(R.string.err_exception) + " " + fav);	
	    e.printStackTrace();
	    return null;
	}
        return null;
    }

    public Dialog selFavList() {
        if(favs == null || favs.size() == 0) {
	    logUI(COLOUR_DBG, getString(R.string.err_in) + " selFavList");	
            return null;
        }
        final Dialog dialog = new Dialog(this);
	int i;
    	dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.list_layout);
        Window wnd = dialog.getWindow();
        if(wnd != null ) wnd.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);

	final ArrayList<String> fstr0 = new ArrayList<>(favs);
	ArrayList<String> fstr1 = new ArrayList<>();
	for(i = 0; i < fstr0.size(); i++) fstr1.add(getFavStation(fstr0.get(i)).name_p);
        final ListView st_list = dialog.findViewById(R.id.StationList);
        final ArrayAdapter<String> ff = new ArrayAdapter<>(this,android.R.layout.simple_list_item_1, fstr1);

	final Context ffc = this;
        st_list.setAdapter(ff);
        st_list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                curStation = getFavStation(fstr0.get(i));
		if(curStation == null) {
		    logUI(COLOUR_ERR, R.string.no_cur_sta);
		    return;	
		}
                ff.clear();
		final Runnable fgr = new Runnable() {
 	            @Override
		    public void run() {
    			if(curStation == null) {
			    Toast.makeText(getApplicationContext(), getString(R.string.no_station), Toast.LENGTH_LONG).show();
			    return;	
			}
       			requestedLat = Util.inval_coord;
			requestedLon = Util.inval_coord;
		     	removeDialog(SEL_FAV);
	                removeDialog(ST_LAUNCH_DLG);
        		showDialog(ST_LAUNCH_DLG);
		    }
		};
		if(addr_source > 0) {
		    final Runnable bgr = new Runnable() {
       		        @Override
			public void run() {
			    if(curStation != null) formattedLocString = get_formatted_address(curStation.latitude, curStation.longitude);
		        }
		    };
		    AsyncTaskWithProgress atp = new AsyncTaskWithProgress(mainAct, getString(R.string.try_google), bgr, fgr);
		    atp.execute();
		} else fgr.run();
            }
        });
        st_list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                long cd = getFavStation(fstr0.get(i)).code;
		String code = Long.toString(cd);
		logUI(COLOUR_DBG, getString(R.string.fav_removal) + " " + i);
		favs.remove(code);
		prefs.save();
                removeDialog(SEL_FAV);
		if(favs.size() == 0) {
		    if(fav_menu_idx != -1)
		    navMenu.getItem(fav_menu_idx).setEnabled(false);  	
		    return true;
		}
                showDialog(SEL_FAV);
		return true;
            }
        });

        return dialog;
    }

    public Dialog do_launch() {

	if(curStation == null) {
	    logUI(COLOUR_ERR, "do_launch -> null");
	    return null;
	}
        final Dialog dialog = new Dialog(this);



	dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.launch_layout);
	logUI(COLOUR_DBG, "do_launch()");
        Window wnd = dialog.getWindow();
        if(wnd != null) wnd.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);

        EditText launch_msg = dialog.findViewById(R.id.launch_msg);
        Button act_btn = dialog.findViewById(R.id.act_btn);
        Button add_btn = dialog.findViewById(R.id.add_to_fav);
        Button view_google = dialog.findViewById(R.id.view_google);
        Button view_osm = dialog.findViewById(R.id.view_osm);


        launch_msg.setKeyListener(null);
        String s = curStation.getInfo();
        if(formattedLocString != null) s += "\n" + formattedLocString;
	
        launch_msg.setText(s);

        act_btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                Intent ii = new Intent(getApplicationContext(),WebActivity.class);
//		logUI(COLOUR_DBG, R.string.launch_webpage);
                String url;
		if(curStation == null) {
	    	    logUI(COLOUR_DBG, getString(R.string.err_in) + " do_launch");	
		    return;
		}
                url = Util.URL_SRV_DATA + "?p=" + curStation.code;
                ii.putExtra("action", url);
                startActivity(ii);
            }
        });

        final Button ab = add_btn;

	if(favs != null && favs.contains(Long.toString(curStation.code))) add_btn.setEnabled(false);
	else add_btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
		long code = curStation.code;
		String s = Long.toString(code);
		if(favs == null) favs = new HashSet<String>();
 		logUI(COLOUR_DBG, getString(R.string.add_new_fav) + ": " + s);
 		favs.add(s);
		String[] f = favs.toArray(new String[0]);
		for(int i = 0; i < f.length; i++) logUI(COLOUR_DBG, "fav[" + i + "]=" + f[i]);
		prefs.save();
		if(fav_menu_idx != -1) navMenu.getItem(fav_menu_idx).setEnabled(true);  	
		ab.setEnabled(false);
        	Toast.makeText(getApplicationContext(),getString(R.string.added), Toast.LENGTH_SHORT).show();
            }
        });

	final double lat = requestedLat, lon = requestedLon, 
		sta_lat = curStation.latitude, sta_lon = curStation.longitude;

        if(maps_avail) {
            view_google.setEnabled(true);
            view_google.setOnClickListener(new View.OnClickListener() {
                public void onClick(View arg0) {
                    Intent i = new Intent(getApplicationContext(), MapsActivity.class);
                    logUI(COLOUR_DBG, R.string.launch_gmaps);
                    i.putExtra("lat", lat);
                    i.putExtra("lon", lon);
                    i.putExtra("sta_lat", sta_lat);
                    i.putExtra("sta_lon", sta_lon);
                    startActivity(i);
                }
            });
        } else view_google.setEnabled(false);

	if(true) {
            view_osm.setOnClickListener(new View.OnClickListener() {
                public void onClick(View arg0) {
                    Intent i = new Intent(getApplicationContext(), OpenmapsActivity.class);
                    logUI(COLOUR_DBG, R.string.launch_osmaps);
                    i.putExtra("lat", lat);
                    i.putExtra("lon", lon);
                    i.putExtra("sta_lat", sta_lat);
                    i.putExtra("sta_lon", sta_lon);
                    startActivity(i);
                }
            });
	}

        return dialog;
    }

    public Dialog selectStationCoordinates() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        builder.setView(inflater.inflate(R.layout.coord_sel_layout, null))
                .setPositiveButton(R.string.okey, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        final double lat = Double.parseDouble(lat_input.getText().toString());
                        final double lon = Double.parseDouble(lon_input.getText().toString());
                        if(lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) {
                            Toast.makeText(getApplicationContext(), getString(R.string.inv_coords), Toast.LENGTH_SHORT).show();
                            return;
                        }
			curStation = Util.getNearestStation(lat,lon);
			if(curStation == null) {
			    logUI(COLOUR_ERR, R.string.no_cur_sta);
			    return;	
			}
			requestedLat = lat;
			requestedLon = lon; 	
                        final Runnable fgr = new Runnable() {
                            @Override
                            public void run() {
                                if(curStation == null) {
                                    Toast.makeText(getApplicationContext(), getString(R.string.failed_to_find_loc), Toast.LENGTH_LONG).show();
				    return;	
				}
                                removeDialog(ST_LAUNCH_DLG);
                                showDialog(ST_LAUNCH_DLG);
                            }
                        };
			if(addr_source > 0) {
			    final Runnable bgr = new Runnable() {
                        	@Override
				public void run() {
				    formattedLocString = get_formatted_address(curStation.latitude, curStation.longitude);
                            	}
			    };
			    AsyncTaskWithProgress atp = new AsyncTaskWithProgress(mainAct, getString(R.string.try_google), bgr, fgr);
			    atp.execute();
			} else fgr.run();
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        removeDialog(SEL_ST_COORD_DLG);
                    }
                });

        return builder.create();
    }


    public static class Prefs {
        private SharedPreferences settings;

        Prefs() {
            settings = PreferenceManager.getDefaultSharedPreferences(App.getContext());
            //settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
	    load(); 
	    favs = settings.getStringSet("favs", null);
        }
	public void load() {
            use_russian = settings.getBoolean("use_russian", true);
            use_offline_maps = settings.getBoolean("use_offline_maps", true);
	    verbose = settings.getInt("verbose", 1);
	    addr_source = settings.getInt("addr_source", 0);
	}
        public void save() {
            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean("use_russian", use_russian);
            editor.putBoolean("use_offline_maps", use_offline_maps);
	    editor.putStringSet("favs", favs);
	    editor.putInt("verbose", verbose);	
	    editor.putInt("addr_source", addr_source);	
	    editor.commit();
        }
    }
}
