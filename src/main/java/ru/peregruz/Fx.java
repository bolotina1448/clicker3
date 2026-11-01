package ru.peregruz;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.audiofx.DynamicsProcessing;

/** Settings and the distortion chain built on the system DynamicsProcessing effect. */
public final class Fx {
    private Fx() {}

    public static final String PREFS = "fx";
    public static final String K_ON = "on", K_TYPE = "type", K_DRIVE = "drive", K_TONE = "tone",
            K_LOW = "low", K_OUT = "out", K_CRACKLE = "crackle",
            K_GLOBAL = "global", K_ERROR = "error", K_PAIRED = "paired";

    public static final int OVERDRIVE = 0, DISTORTION = 1, FUZZ = 2, RADIO = 3, BROKEN = 4, CRACKLING = 5;
    public static final String[] TYPE_NAMES = {"Овердрайв", "Дисторшн", "Фузз", "Радио", "Сломанный", "Треск"};

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static int type(SharedPreferences p) { return p.getInt(K_TYPE, DISTORTION); }
    public static int drive(SharedPreferences p) { return p.getInt(K_DRIVE, 60); }
    public static int tone(SharedPreferences p) { return p.getInt(K_TONE, 60); }
    public static int low(SharedPreferences p) { return p.getInt(K_LOW, 10); }
    /** Output slider 0..100, 70 = 0 dB. */
    public static int out(SharedPreferences p) { return p.getInt(K_OUT, 70); }
    /** Random dropouts and clicks, 0..100. */
    public static int crackle(SharedPreferences p) { return p.getInt(K_CRACKLE, 0); }

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
                    true, 4,
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
        return dp;
    }

    static String short_(Throwable t) {
        String m = t.getMessage();
        return t.getClass().getSimpleName() + (m == null ? "" : ": " + (m.length() > 90 ? m.substring(0, 90) : m));
    }

    private static int eqBands(DynamicsProcessing.Eq eq, int fallback) {
        try { return Math.max(1, eq.getBandCount()); } catch (Throwable t) { return fallback; }
    }

    private static int mbcBands(DynamicsProcessing dp) {
        try { return Math.max(1, dp.getMbcByChannelIndex(0).getBandCount()); } catch (Throwable t) { return 0; }
    }

    // ---- limiter / gain numbers, shared with the crackle glitches ----

    static float inputGain(SharedPreferences p) {
        float d = drive(p) / 100f;
        int t = type(p);
        if (t == BROKEN) return 12f + d * 36f;
        if (t == CRACKLING) return 6f + d * 30f;
        return d * 30f;
    }

    static float[] limiter(SharedPreferences p) {
        float d = drive(p) / 100f;
        float attack, release, ratio, threshold;
        switch (type(p)) {
            case OVERDRIVE: attack = 1f;    release = 25f;  ratio = 6f;  threshold = -6f - d * 14f; break;
            case FUZZ:      attack = 0.1f;  release = 1f;   ratio = 30f; threshold = -14f - d * 16f; break;
            case RADIO:     attack = 0.1f;  release = 2f;   ratio = 20f; threshold = -10f - d * 16f; break;
            case BROKEN:    attack = 0.01f; release = 0.2f; ratio = 50f; threshold = -24f - d * 12f; break;
            case CRACKLING: attack = 0.01f; release = 0.1f; ratio = 50f; threshold = -6f - d * 18f; break;
            default:        attack = 0.1f;  release = 2f;   ratio = 20f; threshold = -10f - d * 18f; break;
        }
        float post = -threshold - 4f + outDb(out(p));
        return new float[]{attack, release, ratio, threshold, post};
    }

    static void setGain(DynamicsProcessing dp, float db) {
        dp.setInputGainAllChannelsTo(db);
    }

    static void setLimiter(DynamicsProcessing dp, float[] l, float extraPostDb) {
        try {
            dp.setLimiterAllChannelsTo(new DynamicsProcessing.Limiter(
                    true, true, 0, l[0], l[1], l[2], l[3], l[4] + extraPostDb));
        } catch (RuntimeException e) {
            // Gentler numbers for phones that refuse very fast settings.
            dp.setLimiterAllChannelsTo(new DynamicsProcessing.Limiter(
                    true, true, 0, Math.max(l[0], 1f), Math.max(l[1], 10f), Math.min(l[2], 10f), l[3],
                    Math.min(l[4], 20f) + extraPostDb));
        }
    }

    /** Applies the settings; each part is tried on its own so one refused value does not kill the rest. */
    public static void apply(DynamicsProcessing dp, SharedPreferences p) {
        int type = type(p);
        float d = drive(p) / 100f;
        float c = crackle(p) / 100f;
        float lowHz = lowHz(low(p));
        float toneHz = toneHz(tone(p));
        if (type == RADIO) {
            lowHz = Math.max(lowHz, 450f);
            toneHz = Math.min(toneHz, 3200f);
        }
        StringBuilder issues = new StringBuilder();

        if (type == CRACKLING) { lowHz = 150f; toneHz = 16000f; }

        // Pre-EQ: cut the lows before the clipper, push the mids into it.
        // CRACKLING: no colour, only the bass pushed harder into the clipper, like a speaker cone hitting its limit.
        try {
            int n = eqBands(dp.getPreEqByChannelIndex(0), 3);
            DynamicsProcessing.Eq pre = new DynamicsProcessing.Eq(true, true, n);
            for (int i = 0; i < n; i++) {
                float f = n == 1 ? 20000f : (float) (lowHz * Math.pow(20000.0 / lowHz, i / (double) (n - 1)));
                float g;
                if (type == CRACKLING) g = i == 0 ? 6f * d : 0f;
                else if (i == 0) g = lowHz > 25f ? -24f : 0f;
                else if (f >= 800f && f <= 5000f) g = (type == BROKEN ? 8f : type == CRACKLING ? 4f : 3f) * d;
                else g = 0f;
                pre.setBand(i, new DynamicsProcessing.EqBand(true, f, g));
            }
            dp.setPreEqAllChannelsTo(pre);
        } catch (Throwable t) { issues.append("EQ1 ").append(short_(t)).append("; "); }

        // Multiband stage: fast gates chop the wave near zero. That is the "broken speaker" crackle.
        int nb = mbcBands(dp);
        if (nb > 0) {
            try {
                boolean on = type != CRACKLING && (type == BROKEN || c > 0f);
                float gate, expander;
                if (type == BROKEN) { gate = -42f + Math.max(c, 0.4f) * 34f; expander = 6f + c * 44f; }
                else if (type == CRACKLING) { gate = -62f + c * 24f; expander = 1.5f + c * 2.5f; }  // mild: crackly, never silent
                else { gate = -70f + c * 30f; expander = 1f + c * 3f; }
                DynamicsProcessing.Mbc mbc = new DynamicsProcessing.Mbc(true, on, nb);
                for (int i = 0; i < nb; i++) {
                    float f = nb == 1 ? 20000f : (float) (120.0 * Math.pow(20000.0 / 120.0, (i + 1) / (double) nb));
                    if (i == nb - 1) f = 20000f;
                    mbc.setBand(i, new DynamicsProcessing.MbcBand(on, f,
                            0.05f, 1f,
                            type == BROKEN ? 20f : 2f,
                            type == BROKEN ? -30f : -10f,
                            0f,
                            gate, expander,
                            0f, type == BROKEN ? 10f : 0f));
                }
                dp.setMbcAllChannelsTo(mbc);
            } catch (Throwable t) { issues.append("MBC ").append(short_(t)).append("; "); }
        }

        try { setGain(dp, inputGain(p)); }
        catch (Throwable t) { issues.append("gain ").append(short_(t)).append("; "); }

        try { setLimiter(dp, limiter(p), 0f); }
        catch (Throwable t) { issues.append("lim ").append(short_(t)).append("; "); }

        // Post-EQ: the tone control, a high cut above toneHz. "Broken" also gets a tinny, torn-cone peak.
        try {
            int n = eqBands(dp.getPostEqByChannelIndex(0), 3);
            float hiCut = type == RADIO ? -36f : type == BROKEN ? -24f : type == CRACKLING ? 0f : -18f;
            DynamicsProcessing.Eq postEq = new DynamicsProcessing.Eq(true, true, n);
            for (int i = 0; i < n; i++) {
                float f, g;
                if (n == 1) { f = 20000f; g = 0f; }
                else if (i == 0) { f = toneHz; g = type == BROKEN ? 3f : 0f; }
                else {
                    f = (float) (toneHz * Math.pow(20000.0 / toneHz, i / (double) (n - 1)));
                    if (f <= toneHz) f = toneHz + i;
                    g = hiCut * i / (n - 1);
                }
                postEq.setBand(i, new DynamicsProcessing.EqBand(true, f, g));
            }
            dp.setPostEqAllChannelsTo(postEq);
        } catch (Throwable t) { issues.append("EQ2 ").append(short_(t)).append("; "); }

        // Drive at zero = music stays clean (only the crackle layer is heard).
        try { dp.setEnabled(drive(p) > 0); } catch (Throwable t) { issues.append("on ").append(short_(t)).append("; "); }

        lastIssue = issues.length() == 0 ? null : issues.toString();
    }
}
