package ru.meteoinfo;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;

import java.lang.ref.WeakReference;
import static ru.meteoinfo.WeatherActivity.*;


public class AsyncTaskWithProgress extends AsyncTask<Void,Void,Void> {
    private ProgressDialog pr;
    private Runnable bgr, fgr;
    private WeakReference<Context> contextRef;
    String title;

    AsyncTaskWithProgress(Context context, String msg, Runnable bgr_run, Runnable fgr_run) {
        bgr = bgr_run;
        fgr = fgr_run;
        contextRef = new WeakReference<>(context);
        title = msg;
    }
    @Override
    protected void onPreExecute() {
        Context context = contextRef.get();
        pr = ProgressDialog.show(context, title, context.getString(R.string.op_in_progress), true, true, new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialogInterface) {
                if(pr != null) pr.dismiss();
                cancel(true); // onCancelled() will be called rather than onPostExecute()
            }
        });
	pr.setCancelable(true);
    }
    protected Void doInBackground(Void... args) {
        if(bgr != null) bgr.run();
        return null;
    }
    protected void onPostExecute(Void result) {
        try {
            boolean showing = pr.isShowing();
            if (showing) pr.dismiss();
        } catch(Exception e) {
	    logUI(COLOUR_ERR, mainAct.getString(R.string.err_exception));
            e.printStackTrace();
        }
        pr = null;
        if(fgr != null) fgr.run();
    }
    protected void onCancelled(Void result) {
        logUI(COLOUR_DBG, "AsyncTask: onCancelled");
    }
}

