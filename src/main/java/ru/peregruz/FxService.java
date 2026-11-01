package ru.peregruz;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.DynamicsProcessing;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps the effect alive in the background. Music apps announce their audio session with
 * AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION; we attach the distortion to each one.
 * Optionally also tries the global output mix (session 0), which some phones still allow.
 */
public class FxService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String ACTION_STOP = "ru.peregruz.STOP";
    private static final String CHANNEL = "fx";

    /** Read by the screen to show what is happening. */
    public static volatile boolean running = false;
    public static volatile List<String> status = new ArrayList<>();

    private final Map<Integer, DynamicsProcessing> effects = new LinkedHashMap<>();
    private final Map<Integer, String> labels = new LinkedHashMap<>();
    private final Map<Integer, String> errors = new LinkedHashMap<>();
    private SharedPreferences prefs;
    private final Set<Integer> fromScan = new HashSet<>();
    private volatile String scanNote = null;
    private HandlerThread scanThread;
    private HandlerThread crackleThread;
    private CrackleSynth synth;
    private Handler crackleHandler;
    private final java.util.Random rnd = new java.util.Random();
    private final Runnable crackleTask = new Runnable() {
        @Override public void run() {
            long next = 25 + rnd.nextInt(40);
            try { next = crackleOnce(); } catch (Throwable ignored) {}
            if (crackleHandler != null) crackleHandler.postDelayed(this, next);
        }
    };
    private Handler scanHandler;
    private final Runnable scanTask = new Runnable() {
        @Override public void run() {
            scanOnce();
            if (scanHandler != null) scanHandler.postDelayed(this, 2500);
        }
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int session = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR_BAD_VALUE);
            if (session <= 0) return;
            String pkg = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME);
            if (AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION.equals(intent.getAction())) {
                attach(session, appName(pkg));
            } else {
                detach(session);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = Fx.prefs(this);
        try {
            start();
        } catch (Throwable t) {
            prefs.edit().putString(Fx.K_ERROR, App.trace(t)).putBoolean(Fx.K_ON, false).commit();
            running = false;
            stopSelf();
        }
    }

    private void start() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Перегруз", NotificationManager.IMPORTANCE_LOW));
        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(1, n);
        }

        IntentFilter f = new IntentFilter();
        f.addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
        f.addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, f, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(receiver, f);
        }
        prefs.registerOnSharedPreferenceChangeListener(this);
        running = true;
        if (prefs.getBoolean(Fx.K_GLOBAL, false)) attach(0, "Весь звук");
        scanThread = new HandlerThread("scan");
        scanThread.start();
        scanHandler = new Handler(scanThread.getLooper());
        scanHandler.post(scanTask);
        crackleThread = new HandlerThread("crackle", android.os.Process.THREAD_PRIORITY_AUDIO);
        crackleThread.start();
        crackleHandler = new Handler(crackleThread.getLooper());
        crackleHandler.post(crackleTask);
        synth = new CrackleSynth();
        synth.start();
        publish();
    }

    /** "Broken" type only: random short dropouts. Also keeps the crackle layer in sync. */
    private long crackleOnce() {
        syncSynth();
        if (Fx.type(prefs) != Fx.BROKEN) return 100;
        float c = Math.max(Fx.crackle(prefs) / 100f, 0.2f);
        List<DynamicsProcessing> list;
        synchronized (this) { list = new ArrayList<>(effects.values()); }
        if (list.isEmpty()) return 200;
        if (rnd.nextFloat() > c * 0.3f) return 30 + rnd.nextInt(60);
        float[] lim = Fx.limiter(prefs);
        for (DynamicsProcessing dp : list) { try { Fx.setLimiter(dp, lim, -60f); } catch (Throwable ignored) {} }
        sleep(8 + rnd.nextInt((int) (10 + 50 * c)));
        for (DynamicsProcessing dp : list) { try { Fx.setLimiter(dp, lim, 0f); } catch (Throwable ignored) {} }
        return 40 + rnd.nextInt((int) (40 + 160 * (1f - c)));
    }

    private void syncSynth() {
        CrackleSynth s = synth;
        if (s == null) return;
        s.amount = Fx.crackle(prefs) / 100f;
        boolean hasTargets;
        synchronized (this) { hasTargets = !effects.isEmpty(); }
        boolean scanning = prefs.getBoolean(Fx.K_PAIRED, false);
        s.active = running && hasTargets && (!scanning || Discovery.playing);
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    private void scanOnce() {
        if (!prefs.getBoolean(Fx.K_PAIRED, false)) {
            scanNote = "Автопоиск плееров выключен: подключите «Отладку по Wi-Fi» (раздел «Все плееры» ниже).";
            publish();
            return;
        }
        try {
            Map<Integer, Integer> found = Discovery.scan(this);
            synchronized (this) {
                for (Map.Entry<Integer, Integer> e : found.entrySet()) {
                    if (!effects.containsKey(e.getKey()) && !errors.containsKey(e.getKey())) {
                        attach(e.getKey(), Discovery.appName(this, e.getValue()));
                        fromScan.add(e.getKey());
                    }
                }
                for (Integer old : new ArrayList<>(fromScan)) {
                    if (!found.containsKey(old)) { detach(old); fromScan.remove(old); }
                }
            }
            scanNote = "Автопоиск плееров включён" + (found.isEmpty() ? ": сейчас ничего не играет." : ".");
        } catch (io.github.muntashirakon.adb.AdbPairingRequiredException e) {
            scanNote = "Автопоиск: нужно заново сделать сопряжение (раздел «Все плееры» ниже).";
        } catch (Throwable t) {
            scanNote = "Автопоиск ждёт «Отладку по Wi-Fi»: включите её в «Для разработчиков» (" + t.getMessage() + ")";
        }
        publish();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            prefs.edit().putBoolean(Fx.K_ON, false).apply();
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (scanHandler != null) { scanHandler.removeCallbacksAndMessages(null); scanHandler = null; }
        if (scanThread != null) scanThread.quitSafely();
        if (crackleHandler != null) { crackleHandler.removeCallbacksAndMessages(null); crackleHandler = null; }
        if (crackleThread != null) crackleThread.quitSafely();
        if (synth != null) { synth.stop(); synth = null; }
        try { unregisterReceiver(receiver); } catch (Exception ignored) {}
        try { prefs.unregisterOnSharedPreferenceChangeListener(this); } catch (Exception ignored) {}
        for (DynamicsProcessing dp : effects.values()) {
            try { dp.setEnabled(false); dp.release(); } catch (Exception ignored) {}
        }
        effects.clear();
        labels.clear();
        errors.clear();
        publish();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public synchronized void onSharedPreferenceChanged(SharedPreferences p, String key) {
        if (Fx.K_GLOBAL.equals(key)) {
            if (p.getBoolean(Fx.K_GLOBAL, false)) attach(0, "Весь звук");
            else detach(0);
            return;
        }
        for (Map.Entry<Integer, DynamicsProcessing> e : effects.entrySet()) {
            try { Fx.apply(e.getValue(), p); } catch (Exception ex) { errors.put(e.getKey(), ex.getClass().getSimpleName()); }
        }
        publish();
    }

    private synchronized void attach(int session, String label) {
        detach(session);
        labels.put(session, label);
        try {
            DynamicsProcessing dp = Fx.create(session, prefs);
            effects.put(session, dp);
            errors.remove(session);
        } catch (Throwable t) {
            errors.put(session, Fx.short_(t));
        }
        publish();
    }

    private synchronized void detach(int session) {
        DynamicsProcessing dp = effects.remove(session);
        if (dp != null) {
            try { dp.setEnabled(false); dp.release(); } catch (Exception ignored) {}
        }
        labels.remove(session);
        errors.remove(session);
        publish();
    }

    private synchronized void publish() {
        List<String> s = new ArrayList<>();
        for (Map.Entry<Integer, String> e : labels.entrySet()) {
            String err = errors.get(e.getKey());
            s.add(e.getValue() + (err == null ? " — работает" : " — не удалось (" + err + ")"));
        }
        if (scanNote != null && running) s.add(scanNote);
        String issue = Fx.lastIssue;
        if (issue != null && running && !effects.isEmpty()) s.add("Часть настроек система не приняла: " + issue);
        status = s;
    }

    private String appName(String pkg) {
        if (pkg == null) return "Приложение";
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            return String.valueOf(pm.getApplicationLabel(ai));
        } catch (Exception e) {
            return pkg;
        }
    }

    private Notification buildNotification() {
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, FxService.class).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Перегруз включён")
                .setContentText("Дисторшн работает в фоне")
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "Выключить", stop).build())
                .build();
    }
}
