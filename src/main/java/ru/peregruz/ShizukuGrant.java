package ru.peregruz;

import android.content.Context;
import android.content.pm.PackageManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

import rikka.shizuku.Shizuku;

/** Grants android.permission.DUMP to this app with one shell command run through Shizuku. */
public final class ShizukuGrant {
    private ShizukuGrant() {}

    public interface Callback { void done(boolean ok, String message); }

    public static boolean running() {
        try { return Shizuku.pingBinder(); } catch (Throwable t) { return false; }
    }

    public static boolean allowed() {
        try { return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED; } catch (Throwable t) { return false; }
    }

    public static void ask(Callback after) {
        Shizuku.addRequestPermissionResultListener(new Shizuku.OnRequestPermissionResultListener() {
            @Override public void onRequestPermissionResult(int requestCode, int grantResult) {
                Shizuku.removeRequestPermissionResultListener(this);
                if (grantResult == PackageManager.PERMISSION_GRANTED) after.done(true, "");
                else after.done(false, "Вы не разрешили доступ в Shizuku.");
            }
        });
        Shizuku.requestPermission(7);
    }

    /** Runs on a background thread; reports on the same thread. */
    public static void grant(Context c, Callback cb) {
        try {
            Method m = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
            m.setAccessible(true);
            Process p = (Process) m.invoke(null,
                    new String[]{"pm", "grant", c.getPackageName(), Discovery.DUMP}, null, null);
            StringBuilder out = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
                String l;
                while ((l = r.readLine()) != null) out.append(l).append('\n');
            }
            int code = p.waitFor();
            if (code == 0 && Discovery.hasPermission(c)) cb.done(true, "Доступ выдан.");
            else cb.done(false, "Команда не сработала (" + code + "): " + out.toString().trim());
        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            cb.done(false, "Ошибка: " + cause);
        }
    }
}
