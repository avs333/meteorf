package ru.meteoinfo;

import android.content.Intent;
import android.os.Bundle;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;


import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.views.MapView;
import org.osmdroid.api.IGeoPoint;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.views.overlay.Marker;

import static ru.meteoinfo.WeatherActivity.*;



public class OpenmapsActivity extends Activity {

    private double lat, lon, sta_lat, sta_lon;
    private MapView map = null;

    @Override public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

	logUI(COLOUR_DBG, "openmaps: init");

        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        setContentView(R.layout.activity_osm);

        map = (MapView) findViewById(R.id.osmmap);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setBuiltInZoomControls(true);
        map.setMultiTouchControls(true);


        Intent intent = getIntent();

        IMapController mapController = map.getController();
        mapController.setZoom(17);

        lat = intent.getDoubleExtra("lat", inval_coord);
        lon = intent.getDoubleExtra("lon", inval_coord);
        sta_lat = intent.getDoubleExtra("sta_lat", inval_coord);
        sta_lon = intent.getDoubleExtra("sta_lon", inval_coord);

	GeoPoint pt;

	if(lat != inval_coord && lon != inval_coord) {
	    pt = new GeoPoint(lat, lon);
            Marker m = new Marker(map);
	    m.setPosition(pt);
	    m.setSnippet(getString(R.string.cur_loc));
	    map.getOverlayManager().add(m);
	}

	pt = new GeoPoint(sta_lat, sta_lon);
	Marker m1 = new Marker(map);
	m1.setPosition(pt);
	m1.setSnippet(getString(R.string.sta_loc));
	map.getOverlayManager().add(m1);

        mapController.setCenter(pt);	

	if(lat != inval_coord && lon != inval_coord) {
	    Marker marker = new Marker(map);
	    marker.setPosition(pt);
	} 


    }

    public void onResume(){
        super.onResume();
        //this will refresh the osmdroid configuration on resuming.
        //if you make changes to the configuration, use 
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        map.onResume(); //needed for compass, my location overlays, v6.0.0 and up
    }

    public void onPause(){
        super.onPause();
        //this will refresh the osmdroid configuration on resuming.
        //if you make changes to the configuration, use 
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //Configuration.getInstance().save(this, prefs);
        map.onPause();  //needed for compass, my location overlays, v6.0.0 and up
    }
}



/*
public class OpenmapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    final double inval_coord = -1000.0;
    private double lat, lon, sta_lat, sta_lon;
    private long start_bytes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
	start_bytes = TrafficStats.getTotalRxBytes();
        Intent intent = getIntent();
        lat = intent.getDoubleExtra("lat", inval_coord);
        lon = intent.getDoubleExtra("lon", inval_coord);
        sta_lat = intent.getDoubleExtra("sta_lat", inval_coord);
        sta_lon = intent.getDoubleExtra("sta_lon", inval_coord);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        //mMap.setMinZoomPreference(15.0f);
        //mMap.setMaxZoomPreference(21.0f);

        // Add a marker in Sydney and move the camera
	LatLng sta_pos;
	if(lat != inval_coord && lon != inval_coord) {
	    LatLng cur_pos = new LatLng(lat, lon);
            Marker m = mMap.addMarker(new MarkerOptions().position(cur_pos).title(getString(R.string.cur_loc)));
	    m.setVisible(true);	
	    m.showInfoWindow();
	    long rx = TrafficStats.getTotalRxBytes();
	    rx -= start_bytes;
	    if(rx > 0) logUI(COLOUR_DBG, String.format("%d bytes", rx, " bytes"));
	}

	sta_pos = new LatLng(sta_lat, sta_lon);
        Marker m1 = mMap.addMarker(new MarkerOptions().position(sta_pos).title(getString(R.string.sta_loc)));
        m1.setVisible(true);	
        m1.showInfoWindow();

        CameraUpdate update1 = CameraUpdateFactory.zoomTo(10.0f);
        mMap.moveCamera(update1);

        CameraUpdate update = CameraUpdateFactory.newLatLng(sta_pos);
        mMap.moveCamera(update);

    }
}

*/



