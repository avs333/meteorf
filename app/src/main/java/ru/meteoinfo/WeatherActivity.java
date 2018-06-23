package ru.meteoinfo;

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
import ru.meteoinfo.Util.Station;

//import android.appwidget.AppWidgetManager;
//import android.appwidget.AppWidgetProviderInfo;

public class WeatherActivity extends AppCompatActivity 
	implements NavigationView.OnNavigationItemSelectedListener {


    private static String TAG = "ru.meteoinfo:WeatherActivity";   

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
    private static int verbose;

    // 0 -> none
    // 1 -> google
    // 2 -> OSM (always used by widget)
    private static int addr_source;
 
    private final long LOC_UPDATE_INTERVAL = 20 * 1000;
    private final long LOC_FASTEST_UPDATE_INTERVAL = 2000; /* 2 sec */


    public static String formattedLocString = null;

    public static AppCompatActivity mainAct;    // this activity

    private static ArrayList<Station> matchingStationList = null;
    public static Station curStation = null;

    // either coordinates of curLocation, or explicitly entered ones
    public static double requestedLon = Util.inval_coord;
    public static double requestedLat = Util.inval_coord;

    public static boolean serverAvail = false;

//  public static boolean use_russian = true;
//  public static boolean use_offline_maps = true;

    private static Prefs prefs = null;
    private static Set<String> favs;

    private static boolean cur_sta_local = false;

    private String get_formatted_address(double lat, double lon) {
	if(addr_source == 0) return null;
	else if(addr_source == 1) return Util.getAddressFromGoogle(lat, lon);
	return Util.getAddressFromOSM(lat, lon);
    }

    boolean maps_avail = false;

    protected void onActivityResult(int req, int res, Intent data) {
	Log.d(TAG, "req=" + req + " , res=" + res);
	if(req == PREF_ACT_REQ) {
	    logUI(COLOUR_DBG, "settings result=" + res);	
 	    Intent i;
	    if((res & SettingsActivity.PCHG_SRV_MASK) != 0) {	
		Log.d(TAG, "perferences changed for server");
		i = new Intent(this, Srv.class);
		i.setAction(Srv.UPDATE_REQUIRED);
		startService(i);
		if(prefs != null) prefs.load();	
		else prefs = new Prefs();	
	    }
	    if((res & SettingsActivity.PCHG_WID_MASK) != 0) {
		Log.d(TAG, "perferences changed for widget");
		i = new Intent(this, WidgetProvider.class);
		i.setAction(WidgetProvider.COLOURS_CHANGED_BROADCAST);
		sendBroadcast(i);
	    }	
	} else if(res == 0) maps_avail = true;
    }

    @Override
    protected void onResume() {
        super.onResume();

        GoogleApiAvailability ga = GoogleApiAvailability.getInstance();
        int result = ga.isGooglePlayServicesAvailable(this);
//        logUI(COLOUR_DBG, "google availability=" + (result == 0 ? "ok" : "no"));
        if(result != 0) {
            Dialog dlg = ga.getErrorDialog(this,result,555);
            dlg.show();
        } else {
            maps_avail = true;
        }
	App.activity_visible = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
	App.activity_visible = false;
    }

    @Override
    protected void onStop() {
        super.onStop();
	App.activity_visible = false;
    }

//  private static NavigationView navigationView;
    private static Menu navMenu;
    private static int menu_size;
    private static TextView tview;
    private static int fav_menu_idx = -1;
    private static int cur_loc_menu_idx = -1;
    public static final Handler ui_update = new Handler() {
	@Override
	public void handleMessage(Message msg) {
	    super.handleMessage(msg);
	    Bundle bundle = msg.getData();
	    if(bundle == null) return;
	    String s = bundle.getString("mesg");
	    if(s != null) {	
		int col = bundle.getInt("colour");	
		String ss = String.format("<font color=#%06X>%s</font><br>", col, s);		
	 	tview.append(Html.fromHtml(ss));
		return;
	    }

	    int result = bundle.getInt("init_complete", Srv.RES_ERR);
	    if(result == Srv.RES_ERR) {
		// MUST restart service!
		return;
	    }
	    for(int i = 0; i < menu_size; i++) navMenu.getItem(i).setEnabled(true);
	    if(result == Srv.RES_LIST) {
		navMenu.getItem(cur_loc_menu_idx).setEnabled(false);	
	//	logUI(COLOUR_INFO, R.string.no_cur_sta);
	    } else logUI(COLOUR_GOOD, R.string.conn_ok);	

	    boolean fp = (favs == null) ? false : favs.size() > 0;
	    if(fav_menu_idx != -1) navMenu.getItem(fav_menu_idx).setEnabled(fp);
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

 	prefs = new Prefs();	// calls prefs.load();

	App.activity_visible = true;

        Locale loc = /* use_russian ? new Locale("ru", "RU") : */ new Locale("en", "US");
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

	if(App.service_started) logUI(COLOUR_INFO, R.string.srv_running);
	
	for(int i = 0; i < menu_size; i++) {
	    MenuItem mi = navMenu.getItem(i);
	    if(mi.getTitle().equals(getString(R.string.select_fav))) {
		fav_menu_idx = i;
		boolean fp = (favs == null) ? false : favs.size() > 0;
		if(App.service_started) mi.setEnabled(Srv.cur_res_level != Srv.RES_ERR && fp ? true : false);
		else mi.setEnabled(fp ? false : true);
	    } else if(mi.getTitle().equals(getString(R.string.current_location))) {
		cur_loc_menu_idx = i;
		if(App.service_started) mi.setEnabled(Srv.cur_res_level == Srv.RES_LOC ? true : false);
		else mi.setEnabled(false);
	    } else {
		if(App.service_started) mi.setEnabled(Srv.cur_res_level != Srv.RES_ERR ? true : false);
		else mi.setEnabled(false);
	    }			
	}

	Intent intie = new Intent(this, Srv.class);
	intie.setAction(Srv.ACTIVITY_STARTED);
	if(startService(intie) == null) Log.d(TAG, "startService() failed");
	else Log.d(TAG, "startService() succeeded");
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
			if(Srv.getCurrentLocation() == null) {
			   Toast.makeText(getApplicationContext(), getString(R.string.failed_to_find_loc), Toast.LENGTH_LONG).show();
			   return;
		        }
			curStation = Util.getNearestStation(requestedLat, requestedLon);
			if(curStation == null) {
			    Toast.makeText(getApplicationContext(), getString(R.string.no_station), Toast.LENGTH_LONG).show();
			    return;
			} 	
			cur_sta_local = true;
			logUI(COLOUR_DBG, getString(R.string.sta_loc) + ": " + curStation.name_p + " #" + curStation.code);
			removeDialog(ST_LAUNCH_DLG);
			showDialog(ST_LAUNCH_DLG);
		    } 	
		};
		Runnable bgr = new Runnable() {
		    @Override
		    public void run() {
			if(Srv.getCurrentLocation() == null) return;
			requestedLat = Srv.getCurrentLocation().getLatitude();
			requestedLon = Srv.getCurrentLocation().getLongitude();
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
	Intent i;
        int id = item.getItemId();
        //noinspection SimplifiableIfStatement
        switch(id) {
	    case R.id.action_settings:
		prefs.save();	
		i = new Intent(this, SettingsActivity.class);
		startActivityForResult(i, PREF_ACT_REQ);
		return true;
	    case R.id.action_about:
		String url = "file:///android_asset/about.html"; 
		i = new Intent(this, WebActivity.class);
		i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		i.putExtra("action", url);
		i.putExtra("show_ui", false);
		i.putExtra("title", getString(R.string.action_about));
		Log.d(TAG, "starting web activity for " + url);
		startActivity(i);
		return true;	
	    case R.id.action_restart:
		i = new Intent(this, WidgetProvider.class);
		i.setAction(WidgetProvider.ACTION_RESTART);
		sendBroadcast(i);
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
			cur_sta_local = false;
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
	for(i = 0; i < fstr0.size(); i++) fstr1.add(Util.getFavStation(fstr0.get(i)).name_p);
        final ListView st_list = dialog.findViewById(R.id.StationList);
        final ArrayAdapter<String> ff = new ArrayAdapter<>(this,android.R.layout.simple_list_item_1, fstr1);

	final Context ffc = this;
        st_list.setAdapter(ff);
        st_list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                curStation = Util.getFavStation(fstr0.get(i));
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
			cur_sta_local = false;
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
                long cd = Util.getFavStation(fstr0.get(i)).code;
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
        Button data_btn = dialog.findViewById(R.id.data_btn);
        Button add_btn = dialog.findViewById(R.id.add_to_fav);
        Button view_google = dialog.findViewById(R.id.view_google);
        Button view_osm = dialog.findViewById(R.id.view_osm);

        launch_msg.setKeyListener(null);
        String s = curStation.getInfo();
        if(formattedLocString != null) s += "\n" + formattedLocString;
	
        launch_msg.setText(s);

        act_btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                Intent ii = new Intent(getApplicationContext(), WebActivity.class);
//		logUI(COLOUR_DBG, R.string.launch_webpage);
                String url;
		if(curStation == null) {
	    	    logUI(COLOUR_DBG, getString(R.string.err_in) + " do_launch");	
		    return;
		}
                url = Util.URL_STA_DATA + "?p=" + curStation.code;
                ii.putExtra("action", url);
		if(curStation.name_p != null) ii.putExtra("title", curStation.name_p);
                startActivity(ii);
            }
        });

        data_btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                Intent ii = new Intent(getApplicationContext(), DataActivity.class);
                String url;
		if(curStation == null) {
	    	    logUI(COLOUR_DBG, getString(R.string.err_in) + " do_launch");	
		    return;
		}
                ii.putExtra("local", cur_sta_local);
		Log.i(TAG, "STARTING DataActivity");
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
				cur_sta_local = false;
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
//          use_russian = settings.getBoolean("use_russian", true);
//          use_offline_maps = settings.getBoolean("use_offline_maps", true);
	    verbose = settings.getInt("verbose", SettingsActivity.DFL_VERBOSE);
	    addr_source = settings.getInt("addr_source", SettingsActivity.DFL_ADDR_SRC);
	}
        public void save() {
            SharedPreferences.Editor editor = settings.edit();
//          editor.putBoolean("use_russian", use_russian);
//          editor.putBoolean("use_offline_maps", use_offline_maps);
	    editor.putStringSet("favs", favs);
	    editor.putInt("verbose", verbose);	
	    editor.putInt("addr_source", addr_source);	
	    editor.apply();
        }
    }
}
