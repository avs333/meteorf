package ru.meteoinfo;

import android.content.Intent;
import android.os.Bundle;
import android.app.Activity;
import java.util.ArrayList;

import com.yandex.mapkit.Animation;
import com.yandex.mapkit.MapKitFactory;
import com.yandex.mapkit.geometry.Point;
import com.yandex.mapkit.map.CameraPosition;
import com.yandex.mapkit.mapview.MapView;

import com.yandex.mapkit.map.MapObjectCollection;
import android.graphics.Color;
import com.yandex.mapkit.map.CircleMapObject;
import com.yandex.mapkit.map.PolylineMapObject;
import com.yandex.mapkit.geometry.Circle;
import com.yandex.mapkit.geometry.Polyline;



public class YMapsActivity extends Activity {

    private final String MAPKIT_API_KEY = "89f3b1d1-b707-433c-a0ee-99804c4cba8a";
//  private final Point TARGET_LOCATION = new Point(59.945933, 30.320045);
    private MapView mapView;
    private MapObjectCollection mapObjects;

    private double lat, lon, sta_lat, sta_lon;
    final double inval_coord = -1000.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

	Intent intent = getIntent();
	lat = intent.getDoubleExtra("lat", inval_coord);
	lon = intent.getDoubleExtra("lon", inval_coord);
	sta_lat = intent.getDoubleExtra("sta_lat", inval_coord);
	sta_lon = intent.getDoubleExtra("sta_lon", inval_coord);

        /**
        * Set the api key before calling initialize on MapKitFactory.
        * It is recommended to set api key in the Application.onCreate method,
        * but here we do it in each activity to make examples isolated.
        */
	MapKitFactory.setApiKey(MAPKIT_API_KEY);
	MapKitFactory.initialize(this);

	setContentView(R.layout.activity_ymaps);
	super.onCreate(savedInstanceState);

	mapView = (MapView)findViewById(R.id.mapview);

	Point pt = new Point(sta_lat, sta_lon);
	mapView.getMap().move(
		new CameraPosition(pt, 14.0f, 0.0f, 0.0f),
		new Animation(Animation.Type.SMOOTH, 5),
		null);

	mapObjects = mapView.getMap().getMapObjects().addCollection();
	
//	CircleMapObject circle;
//	circ= 
 //     circle.setZIndex(100.0f);

	mapObjects.addCircle(new Circle(pt, 20), Color.GREEN, 1, Color.RED);

	if(lat != inval_coord && lon != inval_coord) {

	    Point pt1 = new Point(lat, lon);
	    mapObjects.addCircle(new Circle(pt1, 20), Color.RED, 1, Color.GREEN);

	    ArrayList<Point> polylinePoints = new ArrayList<>();
	    polylinePoints.add(pt);	
	    polylinePoints.add(pt1);	
	    PolylineMapObject polyline = mapObjects.addPolyline(new Polyline(polylinePoints));
	    polyline.setStrokeColor(Color.BLACK);		
	    polyline.setStrokeWidth(1);		
	}

    }

    @Override
    protected void onStop() {
        // Activity onStop call must be passed to both MapView and MapKit instance.
	mapView.onStop();
	MapKitFactory.getInstance().onStop();
	super.onStop();
    }

    @Override
    protected void onStart() {
	// Activity onStart call must be passed to both MapView and MapKit instance.
	super.onStart();
	MapKitFactory.getInstance().onStart();
	mapView.onStart();
    }

}

