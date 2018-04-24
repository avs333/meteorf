package ru.meteoinfo;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.graphics.Bitmap;
import android.app.ProgressDialog;
import java.lang.System;
import android.content.DialogInterface;
import android.app.AlertDialog;
import android.net.TrafficStats;

import static ru.meteoinfo.WeatherActivity.*;

public class WebActivity extends AppCompatActivity {

    static private final String TAG = "meteoinfo.ru"; 
    private WebView webview;
    private static long time, bytes;
    private static boolean show_ui = true;

    Handler hdl = new Handler() {
	@Override
        public void handleMessage(Message msg) {
	    super.handleMessage(msg);
	    Bundle bb = msg.getData();
	    if(bb == null) return;
	    String err = bb.getString("error");
	    if(err != null) showMsg(err);
        }
    };


    public void showMsg(String msg) {
        new AlertDialog.Builder(this).setMessage(msg).setCancelable(false).setPositiveButton("OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                    }
                }
        ).show();
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web);

      try {

	time = 0;
        Intent intent = getIntent();
        final String url = intent.getStringExtra("action");
	show_ui = intent.getBooleanExtra("show_ui", true);

        webview = (WebView) findViewById(R.id.webview);

        final ProgressDialog locDlg = ProgressDialog.show(this, getString(R.string.info),
                                        getString(R.string.op_in_progress));
	locDlg.setCancelable(true);


	webview.getSettings().setJavaScriptEnabled(true);
	webview.getSettings().setDomStorageEnabled(true);
	webview.getSettings().setBuiltInZoomControls(true);

	webview.setWebViewClient(new WebViewClient() {
	    @Override 
	    public boolean shouldOverrideUrlLoading(WebView view, String u) {
	        view.loadUrl(u);
	        return false;
	    }
	    @Override 
	    public void onPageStarted(WebView view, String u, Bitmap favicon) {
		super.onPageStarted(view, u, favicon);
		time = System.currentTimeMillis();	
		bytes = TrafficStats.getTotalRxBytes();
		if(show_ui) logUI(COLOUR_DBG, "web: onPageStarted");
	    }	
	    @Override 
	    public void onPageFinished(WebView view, String u) {
		super.onPageFinished(view, u);
		String s = "web: onPageFinished";
		long rx = TrafficStats.getTotalRxBytes();
		if(time != 0) s += ": " + (System.currentTimeMillis() - time)  + " ms";
		if(rx - bytes > 0) s += " bytes=" + (rx - bytes);
		else s += " (cached)";
		if(show_ui) logUI(COLOUR_DBG, s);
	    }	
	    @Override 
	    public void onLoadResource(WebView view, String u) {
	        super.onLoadResource(view, u);
	        if(webview.getProgress() == 100) {
		    locDlg.dismiss();
	        }    
	    }
	    @Override
	    public void onReceivedError(WebView view, int errorCode, String description, String failedUrl) {
		if(show_ui) logUI(COLOUR_ERR, "web: (" + failedUrl + ")" + description);		    
		try {
		    view.stopLoading();
		    view.clearView();
		    if(view.canGoBack()) view.goBack();
		    locDlg.dismiss();
		    Message msg = new Message();
                    Bundle data = new Bundle();
		    data.putString("error", description);
                    msg.setData(data);
                    hdl.sendMessage(msg);
		} catch (Exception e) {
		    e.printStackTrace();
		}
		super.onReceivedError(view, errorCode, description, failedUrl);		
	    }
	}); 

	webview.loadUrl(url);

	} catch (Exception e) {
	    if(show_ui) logUI(COLOUR_ERR, getString(R.string.conn_slow));
	    e.printStackTrace();		
	}
    }
    @Override
    public void onBackPressed() {
        if (webview.canGoBack()) {
            webview.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
