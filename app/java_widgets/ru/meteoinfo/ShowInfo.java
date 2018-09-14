package ru.meteoinfo;

import android.support.v7.app.AppCompatActivity;
import android.content.Intent;
import android.content.Context;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;

import java.util.ArrayList;

import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import ru.meteoinfo.App;
import ru.meteoinfo.Util;
import ru.meteoinfo.Srv;
import ru.meteoinfo.DataActivity;
import ru.meteoinfo.Util.Station;
import ru.meteoinfo.Util.WeatherData;
import ru.meteoinfo.Util.WeatherInfo;
import ru.meteoinfo.CollectionWidgetService.*;


public class ShowInfo extends AppCompatActivity {

    private int position;
    private static String TAG = "ru.meteoinfo:ShowInfo";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.show_info_layout);
        try {
            if(CollectionWidgetService.view_factory == null) {
                Log.e(TAG, "no view factory, exiting");
                return;
            }
            Intent intent = getIntent();
            position = intent.getIntExtra(CollectionWidgetService.CUR_POS, 0);
            if(position < 0 || position >= CollectionWidgetService.view_factory.count) {
                Log.e(TAG, "position=" + position + " out of range (count=" + CollectionWidgetService.view_factory.count + ")");
                return;
            }
            Log.d(TAG, "position=" + position);

            TextView tview = findViewById(R.id.etext);
            Button act_btn = findViewById(R.id.act_btn);
            Button weekdata_btn = findViewById(R.id.weekdata_btn);

            tview.setKeyListener(null);
            act_btn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View arg0) {
                    Intent intent = new Intent(getApplicationContext(), ru.meteoinfo.WeatherActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.putExtra("open_drawer", true);
                    startActivity(intent);
                }
            });

            final Station st = Srv.getCurrentStation();
            if(st == null) weekdata_btn.setEnabled(false);

            weekdata_btn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View arg0) {
                    String url = Util.URL_STA_DATA + "?p=" + st.code;
                    Intent intent = new Intent(getApplicationContext(), ru.meteoinfo.WebActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.putExtra("action", url);
                    intent.putExtra("show_ui", false);
                    if (st.name_p != null) intent.putExtra("title", st.name_p);
                    startActivity(intent);
                }
            });

            String s = null;

            if(CollectionWidgetService.view_factory.wi_list != null) {
                WeatherInfo wi = CollectionWidgetService.view_factory.wi_list.get(position);
                if(wi != null) s = DataActivity.parseWeatherInfo(wi, false);  // false = skip temperature
            }

            if(s != null && s.length() != 0) tview.setText(Html.fromHtml(s));
            else tview.setText(Html.fromHtml("<p><br><br>&emsp;<h1><font color=#C00000>" +
                    getString(R.string.no_data_avail) + "</font></h1>"));
        } catch (Exception e) {
            Log.e(TAG, App.get_string(R.string.err_exception));
            e.printStackTrace();
        }
    }

}
