package ru.meteoinfo;

import java.util.List;
import java.util.ArrayList;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream; 

import android.content.Intent;
import android.os.Bundle;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.views.MapView;
import org.osmdroid.api.IMapController;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.config.Configuration;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.mapsforge.MapsForgeTileSource;
import org.osmdroid.mapsforge.MapsForgeTileProvider;
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver;

import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.rendertheme.AssetsRenderTheme;
import org.mapsforge.map.rendertheme.XmlRenderTheme;

import android.util.Log;

public class OpenmapsActivity extends Activity {

    private double lat, lon, sta_lat, sta_lon;
    private MapView mapview = null;

    private MapsForgeTileProvider forge = null;
    private MapsForgeTileSource msrc = null;

    final String MAP_FILES_DIR = "/sdcard/osmdroid/";	

    private double getCoord(byte [] b, int offs) {
	int val = b[offs] << 24 | (b[offs+1] & 0xff) << 16 | (b[offs+2] & 0xff) << 8 | (b[offs+3] & 0xff);
	return ((double) val)/1000000.0;      	
    }
	
    private boolean checkFileForRange(File file, double lat, double lon) {
	FileInputStream fin = null;
	try {
	    // See mapforge/docs/Specification-Binary-Map-File.md
	    final int s_idx = 20+4+4+8+8;
	    final int r_sz = s_idx + 16;
	    byte [] hdr = new byte[r_sz];
	    fin = new FileInputStream(file);
	    if(fin.read(hdr) != r_sz) {
		// failed to read header
		return false;
	    }
	    String s = new String(hdr);
	    if(!s.startsWith("mapsforge binary OSM")) {
		// bad file magic
		return false;
	    }	
	    double min_lat = getCoord(hdr, s_idx + 0);
	    double min_lon = getCoord(hdr, s_idx + 4);
	    double max_lat = getCoord(hdr, s_idx + 8);
	    double max_lon = getCoord(hdr, s_idx + 12);
	    if(min_lat < lat && lat < max_lat && min_lon < lon && lon < max_lon) return true;

	    Log.d("meteoinfo.ru", "Lat: min: " + min_lat + " req: " + lat + " max: " + max_lat); 
	    Log.d("meteoinfo.ru", "Lon: min: " + min_lon + " req: " + lon + " max: " + max_lon); 

	} catch(Exception e) {
	    e.printStackTrace();
	    return false;	
	} finally {
	    try {	
		if(fin != null) fin.close();
	    } catch(Exception e) {}	
	}
	// no data for lat/lon
	return false;	
    }
	
    private File[] getMatchingMaps(double lat, double lon) {
	File maps_dir = new File(MAP_FILES_DIR);
	if(!maps_dir.exists()) return null;
	final double _lat = lat, _lon = lon;        
	return maps_dir.listFiles(new FileFilter() {
	    @Override
	    public boolean accept(File file) {
		return file.getName().endsWith(".map") && checkFileForRange(file, _lat, _lon);
	    }	
	});
    }

    @Override public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
	
	setContentView(R.layout.activity_osm);
        mapview = (MapView) findViewById(R.id.osmmap);

        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));

	// Important!
	AndroidGraphicFactory.createInstance(getApplication());	


        Intent intent = getIntent();
        lat = intent.getDoubleExtra("lat", Util.inval_coord);
        lon = intent.getDoubleExtra("lon", Util.inval_coord);
        sta_lat = intent.getDoubleExtra("sta_lat", Util.inval_coord);
        sta_lon = intent.getDoubleExtra("sta_lon", Util.inval_coord);

	if(WeatherActivity.use_offline_maps) {
	    File [] map_files = (lat == Util.inval_coord || lon == Util.inval_coord) ?
			 getMatchingMaps(sta_lat, sta_lon) : getMatchingMaps(lat, lon);
	    XmlRenderTheme theme = null;
	    if(map_files != null && map_files.length > 0) {
		try {
		    theme = new AssetsRenderTheme(ctx, "renderthemes/", "rendertheme-v4.xml");
		    msrc = MapsForgeTileSource.createFromFiles(map_files, theme, "rendertheme-v4");
		//  msrc = MapsForgeTileSource.createFromFiles(map_files);
		    forge = new MapsForgeTileProvider(new SimpleRegisterReceiver(ctx), msrc, null);
		} catch (Exception e) {
                    e.printStackTrace();
		}
	    } else WeatherActivity.logUI(WeatherActivity.COLOUR_DBG, getString(R.string.no_offline_maps));
	}

	if(forge != null) WeatherActivity.logUI(WeatherActivity.COLOUR_INFO, getString(R.string.using_offline_maps));

	if(forge != null) mapview.setTileProvider(forge);
	else mapview.setTileSource(TileSourceFactory.MAPNIK);

        mapview.setBuiltInZoomControls(true);
        mapview.setMultiTouchControls(true);

        IMapController mapController = mapview.getController();
        mapController.setZoom(13.0);

	GeoPoint pt = null, ptc = null;

	if(lat != Util.inval_coord && lon != Util.inval_coord) {
	    pt = new GeoPoint(lat, lon);
            Marker m = new Marker(mapview);
	    m.setPosition(pt);
	    m.setSnippet(getString(R.string.cur_loc));
	    mapview.getOverlayManager().add(m);
	    ptc = new GeoPoint(sta_lat + (lat - sta_lat)/2, 
		sta_lon + (lon - sta_lon)/2);
	}

	GeoPoint pts = new GeoPoint(sta_lat, sta_lon);
	Marker ms = new Marker(mapview);
	ms.setPosition(pts);
	ms.setSnippet(getString(R.string.sta_loc));
	mapview.getOverlayManager().add(ms);

        mapController.setCenter(ptc != null ? ptc : pts);	

	if(pt != null) {
	    Polyline line = new Polyline(mapview);
	    line.setWidth(2.0f);
	    List<GeoPoint> points = new ArrayList<>();
	    points.add(pt);
	    points.add(pts);
	    line.setPoints(points);
	    line.setGeodesic(true);	
	    mapview.getOverlayManager().add(line);
	}

	mapview.setMaxZoomLevel(22.0);


    }

    @Override
    public void onResume(){
        super.onResume();
        if(mapview != null) mapview.onResume(); //needed for compass, my location overlays, v6.0.0 and up
    }

    @Override
    public void onPause(){
        super.onPause();
        //this will refresh the osmdroid configuration on resuming.
        //if you make changes to the configuration, use 
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //Configuration.getInstance().save(this, prefs);
        if(mapview != null) mapview.onPause();  //needed for compass, my location overlays, v6.0.0 and up
    }

    @Override
    public void onDestroy() {
	super.onDestroy();
    }
	

}


