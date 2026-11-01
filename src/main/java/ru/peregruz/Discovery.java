package ru.peregruz;

import android.content.Context;
import android.content.pm.PackageManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds audio sessions of playing apps by reading "dumpsys audio" through the built-in ADB connection.
 */
public final class Discovery {
    private Discovery() {}

    private static final Pattern UID = Pattern.compile("u/pid:\\s*(\\d+)");
    private static final Pattern SESSION = Pattern.compile("session(?:Id)?:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern STATE = Pattern.compile("state:\\s*(\\w+)");
    private static final Pattern USAGE = Pattern.compile("usage[=:]\\s*(\\w+)", Pattern.CASE_INSENSITIVE);

    /** Result: session id -> uid. Throws with a readable message when nothing can be read. */
    public static Map<Integer, Integer> scan(Context c) throws Exception {
        String dump = Adb.run(c, "dumpsys audio");
        Map<Integer, Integer> out = new LinkedHashMap<>();
        int lines = 0, playerLines = 0, sessionLines = 0;
        String sample = null;
        int myUid = android.os.Process.myUid();
        for (String line : dump.split("\n")) {
            lines++;
            if (!line.contains("u/pid:") || !line.contains("AudioPlaybackConfiguration")) continue;
            playerLines++;
            if (sample == null) sample = line.trim();
            Matcher mu = UID.matcher(line), ms = SESSION.matcher(line), mst = STATE.matcher(line);
            if (!mu.find() || !ms.find()) continue;
            sessionLines++;
            int uid = Integer.parseInt(mu.group(1));
            int session = Integer.parseInt(ms.group(1));
            String state = mst.find() ? mst.group(1).toLowerCase() : "started";
            if (uid == myUid || session <= 0 || uid < 10000) continue;
            if (state.contains("release")) continue;
            Matcher mus = USAGE.matcher(line);
            if (mus.find()) {
                String u = mus.group(1).toUpperCase();
                if (u.contains("NOTIFICATION") || u.contains("ALARM") || u.contains("VOICE") || u.contains("RING")
                        || u.contains("SONIFICATION") || u.contains("ACCESSIBILITY") || u.contains("NAVIGATION")) continue;
            }
            out.put(session, uid);
        }
        if (lines < 5) throw new Exception("система не отдала данные о звуке");
        if (playerLines > 0 && sessionLines == 0 && sample != null) {
            throw new Exception("не нашёл номера сессий: " + (sample.length() > 160 ? sample.substring(0, 160) : sample));
        }
        return out;
    }

    public static String appName(Context c, int uid) {
        PackageManager pm = c.getPackageManager();
        String[] pkgs = pm.getPackagesForUid(uid);
        if (pkgs == null || pkgs.length == 0) return "Приложение " + uid;
        try {
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkgs[0], 0)));
        } catch (Exception e) {
            return pkgs[0];
        }
    }
}
