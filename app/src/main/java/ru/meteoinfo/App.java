package ru.meteoinfo;

import android.app.Application;
import android.content.Context;

public class App extends Application {

    private static Context context;
    public static boolean activity_visible = false;

    @Override
    public void onCreate() {
        super.onCreate();
        context = this;
    }

    public static Context getContext() {
        return context;
    }

    public static String get_string(int res_id) {
	return context.getResources().getString(res_id);
    }
}
