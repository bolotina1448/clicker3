package ru.peregruz;

import android.app.Application;

import java.io.PrintWriter;
import java.io.StringWriter;

/** Saves any crash so the next launch can show it on screen. */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                Fx.prefs(this).edit().putString(Fx.K_ERROR, trace(e)).putBoolean(Fx.K_ON, false).commit();
            } catch (Throwable ignored) {}
            if (prev != null) prev.uncaughtException(t, e);
        });
    }

    public static String trace(Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String s = sw.toString();
        return s.length() > 1500 ? s.substring(0, 1500) : s;
    }
}
