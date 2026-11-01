package ru.peregruz;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.audiofx.DynamicsProcessing;

/** Settings and the distortion chain built on the system DynamicsProcessing effect. */
public final class Fx {
    private Fx() {}

    public static final String PREFS = "fx";
    public static final String K_ON = "on", K_TYPE = "type", K_DRIVE = "drive", K_TONE = "tone",
            K_LOW = "low", K_OUT = "out", K_GLOBAL = "global", K_ERROR = "error", K_PAIRED = "paired";

    public static final int OVERDRIVE = 0, DISTORTION = 1, FUZZ = 2, RADIO = 3;
    public static final String[] TYPE_NAMES = {"Овердрайв", "Дисторшн", "Фузз", "Радио"};

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static int type(SharedPreferences p) { return p.getInt(K_TYPE, DISTORTION); }
    public static int drive(SharedPreferences p) { return p.getInt(K_DRIVE, 60); }
    public static int tone(SharedPreferences p) { return p.getInt(K_TONE, 60); }
    public static int low(SharedPreferences p) { return p.getInt(K_LOW, 10); }
    /** Output slider 0..100, 70 = 0 dB. */
    public static int out(SharedPreferences p) { return p.getInt(K_OUT, 70); }

    public static float toneHz(int tone) {
        return (float) (1000.0 * Math.pow(16.0, tone / 100.0)); // 1 kHz .. 16 kHz
    }

    public static float lowHz(int low) {
        return (float) (20.0 * Math.pow(35.0, low / 100.0)); // 20 Hz .. 700 Hz
    }

    public static float outDb(int out) {
        return (out - 70) * 0.4f; // -28 dB .. +12 dB
    }

    /** Last problem seen while configuring an effect (shown on screen), or null. */
    public static volatile String lastIssue = null;

    public static DynamicsProcessing create(int session, SharedPreferences p) {
        DynamicsProcessing dp;
        try {
            DynamicsProcessing.Config cfg = new DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2,
                    true, 3,
                    false, 0,
                    true, 3,
                    true)
                    .build();
            dp = new DynamicsProcessing(1000, session, cfg);
        } catch (RuntimeException first) {
            // Some phones only accept the default layout.
            try {
                dp = new DynamicsProcessing(session);
            } catch (RuntimeException second) {
                throw new RuntimeException(short_(first) + " / " + short_(second));
            }
        }
        apply(dp, p);
        dp.setEnabled(true);
        return dp;
    }

    static String short_(Throwable t) {
        String m = t.getMessage();
        return t.getClass().getSimpleName() + (m == null ? "" : ": " + (m.length() > 90 ? m.substring(0, 90) : m));
    }

    private static int bands(DynamicsProcessing.Eq eq, int fallback) {
        try { return Math.max(1, eq.getBandCount()); } catch (Throwable t) { return fallback; }
    }

    /** Applies the settings; each part is tried on its own so one refused value does not kill the rest. */
    public static void apply(DynamicsProcessing dp, SharedPreferences p) {
        int type = type(p);
        float d = drive(p) / 100f;
        float lowHz = lowHz(low(p));
        float toneHz = toneHz(tone(p));
        float outDb = outDb(out(p));
        if (type == RADIO) {
            lowHz = Math.max(lowHz, 450f);
            toneHz = Math.min(toneHz, 3200f);
        }
        StringBuilder issues = new StringBuilder();

        // Pre-EQ: cut the lows before the clipper, push the mids into it.
        try {
            int n = bands(dp.getPreEqByChannelIndex(0), 3);
            DynamicsProcessing.Eq pre = new DynamicsProcessing.Eq(true, true, n);
            for (int i = 0; i < n; i++) {
                float f = n == 1 ? 20000f : (float) (lowHz * Math.pow(20000.0 / lowHz, i / (double) (n - 1)));
                float g;
                if (i == 0) g = lowHz > 25f ? -24f : 0f;
                else if (f >= 1000f && f <= 5000f) g = 3f * d;
                else g = 0f;
                pre.setBand(i, new DynamicsProcessing.EqBand(true, f, g));
            }
            dp.setPreEqAllChannelsTo(pre);
        } catch (Throwable t) { issues.append("EQ1 ").append(short_(t)).append("; "); }

        // Input gain drives the signal into the limiter, which acts as the clipper.
        float inGain = d * 30f;
        try { dp.setInputGainAllChannelsTo(inGain); }
        catch (Throwable t) { issues.append("gain ").append(short_(t)).append("; "); }

        float attack, release, ratio, threshold;
        switch (type) {
            case OVERDRIVE: attack = 1f;   release = 25f; ratio = 6f;  threshold = -6f - d * 14f; break;
            case FUZZ:      attack = 0.1f; release = 1f;  ratio = 30f; threshold = -14f - d * 16f; break;
            case RADIO:     attack = 0.1f; release = 2f;  ratio = 20f; threshold = -10f - d * 16f; break;
            default:        attack = 0.1f; release = 2f;  ratio = 20f; threshold = -10f - d * 18f; break;
        }
        float post = -threshold - 4f + outDb;
        try {
            dp.setLimiterAllChannelsTo(new DynamicsProcessing.Limiter(
                    true, true, 0, attack, release, ratio, threshold, post));
        } catch (Throwable t) {
            issues.append("lim ").append(short_(t)).append("; ");
            // Retry with gentler numbers.
            try {
                dp.setLimiterAllChannelsTo(new DynamicsProcessing.Limiter(
                        true, true, 0, 1f, 10f, 10f, threshold, Math.min(post, 20f)));
            } catch (Throwable t2) { issues.append("lim2 ").append(short_(t2)).append("; "); }
        }

        // Post-EQ: the tone control, a high cut above toneHz.
        try {
            int n = bands(dp.getPostEqByChannelIndex(0), 3);
            float hiCut = type == RADIO ? -36f : -18f;
            DynamicsProcessing.Eq postEq = new DynamicsProcessing.Eq(true, true, n);
            for (int i = 0; i < n; i++) {
                float f, g;
                if (n == 1) { f = 20000f; g = 0f; }
                else if (i == 0) { f = toneHz; g = 0f; }
                else {
                    f = (float) (toneHz * Math.pow(20000.0 / toneHz, i / (double) (n - 1)));
                    if (f <= toneHz) f = toneHz + i;
                    g = hiCut * i / (n - 1);
                }
                postEq.setBand(i, new DynamicsProcessing.EqBand(true, f, g));
            }
            dp.setPostEqAllChannelsTo(postEq);
        } catch (Throwable t) { issues.append("EQ2 ").append(short_(t)).append("; "); }

        lastIssue = issues.length() == 0 ? null : issues.toString();
    }
}
