package ru.peregruz;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;

public class MainActivity extends Activity {

    private static final int BG = 0xFF121512, ENC = 0xFF2F4B3F, SILK = 0xFFEFE6C8, SILK_DIM = 0xFFB9B39A,
            ACCENT = 0xFFD9FF3F, INK = 0xFF1D211C, LED_ON = 0xFFFF3B2F, LED_OFF = 0xFF4A2422;

    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView statusView, powerLabel;
    private View led;
    private Button power;
    private final TextView[] typeButtons = new TextView[4];
    private SeekBar drive, tone, low, out;
    private TextView vDrive, vTone, vLow, vOut;
    private Switch global;
    private TextView accessView;

    private final Runnable refresh = new Runnable() {
        @Override public void run() { renderStatus(); handler.postDelayed(this, 1000); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Fx.prefs(this);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFitsSystemWindows(true);
        LinearLayout root = vbox();
        root.setPadding(dp(16), dp(20), dp(16), dp(32));
        scroll.addView(root);

        TextView title = text("ПЕРЕГРУЗ", 30, SILK, true);
        title.setLetterSpacing(0.06f);
        root.addView(title);
        TextView sub = text("Дисторшн для музыки на телефоне", 15, SILK_DIM, false);
        root.addView(sub, lp(0, dp(4)));

        // ---- the pedal ----
        LinearLayout pedal = vbox();
        GradientDrawable enc = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF3C5D4F, ENC});
        enc.setCornerRadius(dp(22));
        pedal.setBackground(enc);
        pedal.setPadding(dp(16), dp(16), dp(16), dp(20));
        root.addView(pedal, lp(dp(16), 0));

        LinearLayout top = hbox();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = text("ПЕРЕГРУЗ", 18, SILK, true);
        top.addView(brand, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        led = new View(this);
        top.addView(led, new LinearLayout.LayoutParams(dp(14), dp(14)));
        pedal.addView(top);

        // type selector
        LinearLayout seg = hbox();
        GradientDrawable segBg = round(0x40000000, 12);
        seg.setBackground(segBg);
        seg.setPadding(dp(4), dp(4), dp(4), dp(4));
        for (int i = 0; i < 4; i++) {
            final int t = i;
            TextView b = text(Fx.TYPE_NAMES[i], 13, SILK_DIM, true);
            b.setGravity(Gravity.CENTER);
            b.setPadding(0, dp(10), 0, dp(10));
            b.setOnClickListener(v -> { prefs.edit().putInt(Fx.K_TYPE, t).apply(); render(); });
            typeButtons[i] = b;
            seg.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        pedal.addView(seg, lp(dp(16), 0));

        vDrive = new TextView(this); vTone = new TextView(this); vLow = new TextView(this); vOut = new TextView(this);
        drive = knob(pedal, "ДРАЙВ", vDrive, Fx.K_DRIVE, Fx.drive(prefs));
        tone = knob(pedal, "ТОН", vTone, Fx.K_TONE, Fx.tone(prefs));
        low = knob(pedal, "СРЕЗ НИЗА", vLow, Fx.K_LOW, Fx.low(prefs));
        out = knob(pedal, "ГРОМКОСТЬ", vOut, Fx.K_OUT, Fx.out(prefs));

        // presets
        TextView pl = text("ПРЕСЕТЫ", 11, SILK_DIM, true);
        pl.setLetterSpacing(0.12f);
        pedal.addView(pl, lp(dp(16), dp(6)));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout prow = hbox();
        hs.addView(prow);
        preset(prow, "Тёплый ламповый", Fx.OVERDRIVE, 35, 55, 8, 70);
        preset(prow, "Рок", Fx.DISTORTION, 62, 66, 22, 70);
        preset(prow, "Взорванный 808", Fx.DISTORTION, 90, 50, 0, 66);
        preset(prow, "Фузз-стена", Fx.FUZZ, 90, 58, 12, 64);
        preset(prow, "Старое радио", Fx.RADIO, 70, 40, 60, 72);
        pedal.addView(hs);

        // footswitch
        power = new Button(this);
        power.setAllCaps(false);
        power.setTextSize(17);
        power.setTypeface(Typeface.DEFAULT_BOLD);
        power.setOnClickListener(v -> toggle());
        pedal.addView(power, lp(dp(20), 0));
        powerLabel = text("", 12, SILK_DIM, false);
        powerLabel.setGravity(Gravity.CENTER);
        pedal.addView(powerLabel, lp(dp(6), 0));

        // ---- status ----
        TextView sl = text("К ЧЕМУ ПОДКЛЮЧЕНО", 11, SILK_DIM, true);
        sl.setLetterSpacing(0.12f);
        root.addView(sl, lp(dp(20), dp(6)));
        statusView = text("", 15, SILK, false);
        statusView.setTextIsSelectable(true);
        root.addView(statusView);

        global = new Switch(this);
        global.setText("Весь звук телефона (эксперимент)");
        global.setTextColor(SILK);
        global.setTextSize(15);
        global.setChecked(prefs.getBoolean(Fx.K_GLOBAL, false));
        global.setOnCheckedChangeListener((b, c) -> prefs.edit().putBoolean(Fx.K_GLOBAL, c).apply());
        root.addView(global, lp(dp(16), 0));
        TextView gh = text("Если музыка звучит без эффекта, включите этот переключатель. На некоторых телефонах система его не пускает, тогда здесь появится «не удалось».", 13, SILK_DIM, false);
        root.addView(gh, lp(dp(4), 0));

        // ---- access to all players ----
        TextView al = text("ВСЕ ПЛЕЕРЫ: SPOTIFY, YOUTUBE И ДРУГИЕ", 11, SILK_DIM, true);
        al.setLetterSpacing(0.12f);
        root.addView(al, lp(dp(24), dp(6)));
        root.addView(text("Многие приложения (в том числе Spotify) не сообщают системе о своём звуке. Чтобы педаль находила их сама, нужно один раз выдать доступ через приложение Shizuku.", 14, SILK, false));
        Button access = new Button(this);
        access.setAllCaps(false);
        access.setText("Дать доступ ко всем плеерам");
        access.setTextColor(INK);
        access.setTypeface(Typeface.DEFAULT_BOLD);
        access.setBackground(round(ACCENT, 12));
        access.setOnClickListener(v -> giveAccess());
        root.addView(access, lp(dp(12), 0));
        accessView = text("", 14, SILK_DIM, false);
        accessView.setTextIsSelectable(true);
        root.addView(accessView, lp(dp(8), 0));

        // ---- how to ----
        TextView hl = text("КАК ЗАСТАВИТЬ РАБОТАТЬ", 11, SILK_DIM, true);
        hl.setLetterSpacing(0.12f);
        root.addView(hl, lp(dp(24), dp(6)));
        root.addView(text(
                "1. Нажмите «Включить педаль».\n" +
                "2. Один раз выдайте доступ ко всем плеерам: установите Shizuku из Google Play, запустите его по подсказке внутри (через «Отладку по Wi-Fi»), затем нажмите «Дать доступ ко всем плеерам». На Xiaomi в «Для разработчиков» должен быть включён пункт «Отладка по USB (настройки безопасности)».\n" +
                "3. Включите музыку в любом приложении. Через пару секунд оно появится в списке «К чему подключено».\n" +
                "4. Чтобы HyperOS не выключала педаль: нажмите кнопку ниже, затем «Контроль активности» → «Нет ограничений» и включите «Автозапуск».\n" +
                "5. Начинайте с небольшой громкости: дисторшн делает звук громче и резче.",
                14, SILK, false));
        Button bat = new Button(this);
        bat.setAllCaps(false);
        bat.setText("Открыть настройки приложения");
        bat.setTextColor(INK);
        bat.setBackground(round(SILK, 10));
        bat.setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
            startActivity(i);
        });
        root.addView(bat, lp(dp(12), 0));

        setContentView(scroll);
        render();
    }

    @Override protected void onResume() { super.onResume(); render(); handler.post(refresh); }
    @Override protected void onPause() { super.onPause(); handler.removeCallbacks(refresh); }

    private void toggle() {
        boolean on = !FxService.running;
        if (on && Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        prefs.edit().putBoolean(Fx.K_ON, on).remove(Fx.K_ERROR).apply();
        Intent svc = new Intent(this, FxService.class);
        try {
            if (on) startForegroundService(svc); else stopService(svc);
        } catch (Throwable t) {
            prefs.edit().putString(Fx.K_ERROR, App.trace(t)).putBoolean(Fx.K_ON, false).apply();
        }
        handler.postDelayed(this::render, 300);
        handler.postDelayed(this::render, 1500);
    }

    private void giveAccess() {
        if (Discovery.hasPermission(this)) {
            accessView.setText("Доступ уже есть. Включите педаль и музыку: плеер появится в списке за пару секунд.");
            return;
        }
        if (!ShizukuGrant.running()) {
            accessView.setText("Shizuku не запущен. Установите его из Google Play, откройте и запустите через «Отладку по Wi-Fi» (там есть пошаговая подсказка). Потом вернитесь сюда и нажмите кнопку ещё раз.");
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api")));
            } catch (Exception ignored) {}
            return;
        }
        if (!ShizukuGrant.allowed()) {
            accessView.setText("Разрешите «Перегрузу» доступ в окне Shizuku.");
            try {
                ShizukuGrant.ask((ok, msg) -> runOnUiThread(() -> { if (ok) giveAccess(); else accessView.setText(msg); }));
            } catch (Throwable t) {
                accessView.setText("Shizuku не ответил: " + t);
            }
            return;
        }
        accessView.setText("Выдаю доступ…");
        new Thread(() -> ShizukuGrant.grant(this, (ok, msg) -> runOnUiThread(() -> accessView.setText(ok
                ? "Готово! Теперь включите музыку: плеер появится в списке «К чему подключено» за пару секунд."
                : msg + "\n\nНа Xiaomi включите в «Для разработчиков» пункт «Отладка по USB (настройки безопасности)» и нажмите кнопку снова.")))).start();
    }

    private void render() {
        int type = Fx.type(prefs);
        for (int i = 0; i < 4; i++) {
            boolean sel = i == type;
            typeButtons[i].setTextColor(sel ? ENC : SILK_DIM);
            typeButtons[i].setBackground(sel ? round(SILK, 9) : null);
        }
        vDrive.setText(Fx.drive(prefs) + "%");
        float th = Fx.toneHz(Fx.tone(prefs));
        vTone.setText(th >= 1000 ? String.format("%.1f кГц", th / 1000f) : Math.round(th) + " Гц");
        float lh = Fx.lowHz(Fx.low(prefs));
        vLow.setText(lh < 26 ? "выкл" : Math.round(lh) + " Гц");
        float od = Fx.outDb(Fx.out(prefs));
        vOut.setText((od > 0 ? "+" : "") + String.format("%.0f дБ", od));
        drive.setProgress(Fx.drive(prefs));
        tone.setProgress(Fx.tone(prefs));
        low.setProgress(Fx.low(prefs));
        out.setProgress(Fx.out(prefs));
        renderStatus();
    }

    private void renderStatus() {
        boolean on = FxService.running;
        GradientDrawable l = new GradientDrawable();
        l.setShape(GradientDrawable.OVAL);
        l.setColor(on ? LED_ON : LED_OFF);
        led.setBackground(l);
        power.setText(on ? "Выключить педаль" : "Включить педаль");
        power.setTextColor(on ? SILK : INK);
        power.setBackground(round(on ? 0xFF1D2D25 : ACCENT, 14));
        powerLabel.setText(on ? "Эффект работает в фоне, можно свернуть приложение" : "Эффект выключен");
        List<String> s = FxService.status;
        if (!on) statusView.setText("Педаль выключена.");
        else if (s.isEmpty()) statusView.setText("Пока ни одного приложения. Запустите музыку заново (шаг 2 ниже).");
        else statusView.setText("• " + String.join("\n• ", s));
        String err = prefs.getString(Fx.K_ERROR, null);
        if (err != null && !on) {
            statusView.setText("Педаль не запустилась. Сделайте скриншот этого текста и пришлите его:\n\n" + err);
        }
    }

    private SeekBar knob(LinearLayout parent, String name, TextView value, String key, int initial) {
        LinearLayout head = hbox();
        TextView n = text(name, 11, SILK, true);
        n.setLetterSpacing(0.14f);
        head.addView(n, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        value.setTextColor(SILK_DIM);
        value.setTextSize(12);
        value.setTypeface(Typeface.MONOSPACE);
        head.addView(value);
        parent.addView(head, lp(dp(16), 0));
        SeekBar sb = new SeekBar(this);
        sb.setMax(100);
        sb.setProgress(initial);
        sb.setProgressTintList(android.content.res.ColorStateList.valueOf(SILK));
        sb.setThumbTintList(android.content.res.ColorStateList.valueOf(SILK));
        sb.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0x66000000));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (user) { prefs.edit().putInt(key, p).apply(); render(); }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        parent.addView(sb, lp(dp(4), 0));
        return sb;
    }

    private void preset(LinearLayout row, String name, int type, int d, int t, int l, int o) {
        TextView b = text(name, 13, SILK, false);
        b.setPadding(dp(12), dp(7), dp(12), dp(7));
        GradientDrawable bg = round(0x38000000, 999);
        bg.setStroke(dp(1), 0x40EFE6C8);
        b.setBackground(bg);
        b.setOnClickListener(v -> {
            prefs.edit().putInt(Fx.K_TYPE, type).putInt(Fx.K_DRIVE, d).putInt(Fx.K_TONE, t)
                    .putInt(Fx.K_LOW, l).putInt(Fx.K_OUT, o).apply();
            render();
        });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.rightMargin = dp(6);
        row.addView(b, p);
    }

    // ---- small helpers ----
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private LinearLayout vbox() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout hbox() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private LinearLayout.LayoutParams lp(int top, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = top; p.bottomMargin = bottom; return p;
    }
    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLineSpacing(0, 1.15f);
        return t;
    }
    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }
}
