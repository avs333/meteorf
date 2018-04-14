package ru.meteoinfo;

import android.content.Intent;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Marker;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    final double inval_coord = -1000.0;
    private double lat, lon, sta_lat, sta_lon;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
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
