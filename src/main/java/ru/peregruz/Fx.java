package ru.peregruz;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.audiofx.DynamicsProcessing;

/** Settings and the distortion chain built on the system DynamicsProcessing effect. */
public final class Fx {
    private Fx() {}

    public static final String PREFS = "fx";
    public static final String K_ON = "on", K_TYPE = "type", K_DRIVE = "drive", K_TONE = "tone",
            K_LOW = "low", K_OUT = "out", K_GLOBAL = "global", K_ERROR = "error";

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

    public static DynamicsProcessing create(int session, SharedPreferences p) {
        DynamicsProcessing.Config cfg = new DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_TIME_RESOLUTION,
                2,
                true, 3,
                false, 0,
                true, 3,
                true)
                .setPreferredFrameDuration(10f)
                .build();
        DynamicsProcessing dp = new DynamicsProcessing(1000, session, cfg);
        apply(dp, p);
        dp.setEnabled(true);
        return dp;
    }

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

        // Pre-EQ: cut the lows before the clipper (tighter sound), push the mids into it.
        DynamicsProcessing.Eq pre = new DynamicsProcessing.Eq(true, true, 3);
        pre.setBand(0, new DynamicsProcessing.EqBand(true, lowHz, lowHz > 25f ? -24f : 0f));
        pre.setBand(1, new DynamicsProcessing.EqBand(true, 2500f, 3f * d));
        pre.setBand(2, new DynamicsProcessing.EqBand(true, 20000f, 0f));
        dp.setPreEqAllChannelsTo(pre);

        // Input gain drives the signal into the limiter, which acts as the clipper.
        float inGain = d * 30f;
        dp.setInputGainAllChannelsTo(inGain);

        float attack, release, ratio, threshold;
        switch (type) {
            case OVERDRIVE: attack = 1f;    release = 25f; ratio = 6f;   threshold = -6f - d * 14f; break;
            case FUZZ:      attack = 0.01f; release = 0.5f; ratio = 100f; threshold = -14f - d * 16f; break;
            case RADIO:     attack = 0.05f; release = 2f;  ratio = 40f;  threshold = -10f - d * 16f; break;
            default:        attack = 0.05f; release = 2f;  ratio = 40f;  threshold = -10f - d * 18f; break;
        }
        // Bring the clipped level back to roughly -4 dBFS, then apply the output control.
        float post = -threshold - 4f + outDb;
        DynamicsProcessing.Limiter lim = new DynamicsProcessing.Limiter(
                true, true, 0, attack, release, ratio, threshold, post);
        dp.setLimiterAllChannelsTo(lim);

        // Post-EQ: the tone control, a high cut above toneHz.
        float b1 = toneHz < 10000f ? toneHz * 1.8f : (toneHz + 20000f) / 2f;
        float hiCut = type == RADIO ? -36f : -18f;
        DynamicsProcessing.Eq postEq = new DynamicsProcessing.Eq(true, true, 3);
        postEq.setBand(0, new DynamicsProcessing.EqBand(true, toneHz, 0f));
        postEq.setBand(1, new DynamicsProcessing.EqBand(true, b1, hiCut / 2f));
        postEq.setBand(2, new DynamicsProcessing.EqBand(true, 20000f, hiCut));
        dp.setPostEqAllChannelsTo(postEq);
    }
}
