package com.purpleclicker;

import net.minecraftforge.common.config.Configuration;

import java.awt.Color;
import java.io.File;

/** All user settings, saved to config/purpleclicker.cfg */
public final class Settings {

    public static final int MAX_CPS = 50;

    private static Configuration cfg;

    public static boolean enabled = true;      // master switch
    public static boolean leftEnabled = true;
    public static int leftMinCps = 9;
    public static int leftMaxCps = 12;
    public static boolean rightEnabled = false;
    public static int rightCps = 10;
    public static boolean breakBlocks = true;  // pause left clicker while aiming at a block
    public static boolean showHud = true;
    public static int accentHue = 275;         // 275 = purple
    public static int accentSat = 70;          // 0..100

    private Settings() {}

    public static void load(File file) {
        cfg = new Configuration(file);
        cfg.load();
        String c = Configuration.CATEGORY_GENERAL;
        enabled      = cfg.get(c, "enabled", enabled).getBoolean();
        leftEnabled  = cfg.get(c, "leftEnabled", leftEnabled).getBoolean();
        leftMinCps   = cfg.get(c, "leftMinCps", leftMinCps).getInt();
        leftMaxCps   = cfg.get(c, "leftMaxCps", leftMaxCps).getInt();
        rightEnabled = cfg.get(c, "rightEnabled", rightEnabled).getBoolean();
        rightCps     = cfg.get(c, "rightCps", rightCps).getInt();
        breakBlocks  = cfg.get(c, "breakBlocks", breakBlocks).getBoolean();
        showHud      = cfg.get(c, "showHud", showHud).getBoolean();
        accentHue    = cfg.get(c, "accentHue", accentHue).getInt();
        accentSat    = cfg.get(c, "accentSat", accentSat).getInt();
        clampAll();
        if (cfg.hasChanged()) cfg.save();
    }

    public static void save() {
        if (cfg == null) return;
        clampAll();
        String c = Configuration.CATEGORY_GENERAL;
        cfg.get(c, "enabled", true).set(enabled);
        cfg.get(c, "leftEnabled", true).set(leftEnabled);
        cfg.get(c, "leftMinCps", 9).set(leftMinCps);
        cfg.get(c, "leftMaxCps", 12).set(leftMaxCps);
        cfg.get(c, "rightEnabled", false).set(rightEnabled);
        cfg.get(c, "rightCps", 10).set(rightCps);
        cfg.get(c, "breakBlocks", true).set(breakBlocks);
        cfg.get(c, "showHud", true).set(showHud);
        cfg.get(c, "accentHue", 275).set(accentHue);
        cfg.get(c, "accentSat", 70).set(accentSat);
        cfg.save();
    }

    private static void clampAll() {
        leftMinCps = clamp(leftMinCps, 1, MAX_CPS);
        leftMaxCps = clamp(leftMaxCps, 1, MAX_CPS);
        if (leftMaxCps < leftMinCps) leftMaxCps = leftMinCps;
        rightCps = clamp(rightCps, 1, MAX_CPS);
        accentHue = clamp(accentHue, 0, 360);
        accentSat = clamp(accentSat, 0, 100);
    }

    public static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /** Main accent color (ARGB). */
    public static int accent() {
        return 0xFF000000 | (Color.HSBtoRGB(accentHue / 360f, accentSat / 100f, 0.95f) & 0xFFFFFF);
    }

    /** Darker accent for gradients/borders (ARGB). */
    public static int accentDark() {
        return 0xFF000000 | (Color.HSBtoRGB(accentHue / 360f, accentSat / 100f, 0.55f) & 0xFFFFFF);
    }
}
