package com.purpleclicker;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Dark window with a category sidebar on the left. Drag it by the top strip. */
public class ClickerGui extends GuiScreen {

    private static final int W = 380;
    private static final int H = 250;
    private static final int SIDE = 112;
    private static final int RADIUS = 7;
    private static final int DRAG_STRIP = 42;

    private static final int BG_MAIN  = 0xF5141219;
    private static final int BG_SIDE  = 0xF50D0C11;
    private static final int CARD     = 0xFF1C1A25;
    private static final int TEXT     = 0xFFEDEAF4;
    private static final int TEXT_DIM = 0xFF8C869C;
    private static final int TRACK    = 0xFF2C2937;
    private static final int OFF      = 0xFF3A3646;
    private static final int LINE     = 0x10FFFFFF;

    private static final String[] TABS  = {"Left Click", "Right Click", "Appearance"};
    private static final String[] DESCS = {
            "Clicks while you hold left mouse",
            "Clicks while you hold right mouse",
            "Accent color and HUD"
    };

    // Remembered between openings
    private static int winX = -1, winY = -1;
    private static int tab = 0;

    private final List<List<Element>> pages = new ArrayList<List<Element>>();
    private final Toggle masterToggle;

    private boolean dragging;
    private int dragOffX, dragOffY;
    private Slider activeSlider;
    private float tabAnimY = -1;
    private long lastFrame = System.nanoTime();
    private float dt;

    public ClickerGui() {
        List<Element> left = new ArrayList<Element>();
        left.add(new Toggle("Left clicker", "Auto-click when holding LMB",
                () -> Settings.leftEnabled, v -> Settings.leftEnabled = v));
        left.add(new Slider("Min CPS", 1, Settings.MAX_CPS, () -> Settings.leftMinCps, v -> {
            Settings.leftMinCps = v;
            if (Settings.leftMaxCps < v) Settings.leftMaxCps = v;
        }, ""));
        left.add(new Slider("Max CPS", 1, Settings.MAX_CPS, () -> Settings.leftMaxCps, v -> {
            Settings.leftMaxCps = v;
            if (Settings.leftMinCps > v) Settings.leftMinCps = v;
        }, ""));
        left.add(new Toggle("Break blocks", "Stops clicking while you mine",
                () -> Settings.breakBlocks, v -> Settings.breakBlocks = v));
        pages.add(left);

        List<Element> right = new ArrayList<Element>();
        right.add(new Toggle("Right clicker", "Auto-click when holding RMB",
                () -> Settings.rightEnabled, v -> Settings.rightEnabled = v));
        right.add(new Slider("CPS", 1, Settings.MAX_CPS, () -> Settings.rightCps, v -> Settings.rightCps = v, ""));
        pages.add(right);

        List<Element> look = new ArrayList<Element>();
        look.add(new HueSlider());
        look.add(new Slider("Saturation", 0, 100, () -> Settings.accentSat, v -> Settings.accentSat = v, "%"));
        look.add(new Toggle("Show HUD", "Status pill in the top-left corner",
                () -> Settings.showHud, v -> Settings.showHud = v));
        look.add(new Button("Reset to purple", () -> { Settings.accentHue = 275; Settings.accentSat = 70; }));
        pages.add(look);

        masterToggle = new Toggle("", "", () -> Settings.enabled, v -> Settings.enabled = v);
    }

    @Override
    public void initGui() {
        if (winX < 0) {
            winX = (width - W) / 2;
            winY = (height - H) / 2;
        }
    }

    // ------------------------------------------------------------------ draw

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        long now = System.nanoTime();
        dt = Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
        lastFrame = now;

        drawRect(0, 0, width, height, 0x80000000);

        if (dragging) {
            winX = mouseX - dragOffX;
            winY = mouseY - dragOffY;
        }
        winX = Settings.clamp(winX, 0, Math.max(0, width - W));
        winY = Settings.clamp(winY, 0, Math.max(0, height - H));
        if (activeSlider != null) activeSlider.setFromMouse(mouseX);

        int accent = Settings.accent();
        int x = winX, y = winY;

        // glow, window, sidebar
        RenderUtil.roundedRect(x - 3, y - 3, x + W + 3, y + H + 3, RADIUS + 3, RenderUtil.withAlpha(accent, 0x22));
        RenderUtil.roundedRect(x - 1, y - 1, x + W + 1, y + H + 1, RADIUS + 1, RenderUtil.withAlpha(accent, 0x55));
        RenderUtil.roundedRect(x, y, x + W, y + H, RADIUS, BG_MAIN);
        RenderUtil.roundedRect(x, y, x + SIDE, y + H, RADIUS, BG_SIDE);
        drawRect(x + SIDE - RADIUS, y, x + SIDE, y + H, BG_SIDE);
        drawRect(x + SIDE, y, x + SIDE + 1, y + H, LINE);

        drawSidebar(x, y, mouseX, mouseY, accent);
        drawPage(x, y, mouseX, mouseY, accent);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawSidebar(int x, int y, int mx, int my, int accent) {
        // logo
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 12, y + 14, 0);
        GlStateManager.scale(1.35f, 1.35f, 1f);
        fontRendererObj.drawStringWithShadow("Purple", 0, 0, accent);
        fontRendererObj.drawStringWithShadow("Clicker", fontRendererObj.getStringWidth("Purple"), 0, TEXT);
        GlStateManager.popMatrix();
        small("v2.0", x + 12, y + 29, TEXT_DIM);

        // tabs
        int tabTop = y + 50;
        float target = tabTop + tab * 24;
        if (tabAnimY < 0) tabAnimY = target;
        tabAnimY += (target - tabAnimY) * Math.min(1f, dt * 14f);

        RenderUtil.roundedRect(x + 8, tabAnimY, x + SIDE - 8, tabAnimY + 20, 5, RenderUtil.withAlpha(accent, 0x30));
        RenderUtil.roundedRect(x + 8, tabAnimY + 5, x + 10.5f, tabAnimY + 15, 1.2f, accent);

        for (int i = 0; i < TABS.length; i++) {
            int ty = tabTop + i * 24;
            boolean hover = inside(mx, my, x + 8, ty, SIDE - 16, 20);
            int color = i == tab ? TEXT : (hover ? 0xFFC9C3D8 : TEXT_DIM);
            if (hover && i != tab) {
                RenderUtil.roundedRect(x + 8, ty, x + SIDE - 8, ty + 20, 5, 0x0CFFFFFF);
            }
            fontRendererObj.drawString(TABS[i], x + 18, ty + 6, color);
        }

        // master switch at the bottom
        int by = y + H - 46;
        drawRect(x + 10, by - 8, x + SIDE - 10, by - 7, LINE);
        fontRendererObj.drawString("Clicker", x + 12, by + 2, TEXT);
        masterToggle.drawSwitch(x + SIDE - 12 - 22, by + 1, accent);
        masterToggle.bx = x + 8;
        masterToggle.by = by - 3;
        masterToggle.bw = SIDE - 16;
        masterToggle.bh = 18;

        String keys = Keyboard.getKeyName(PurpleClicker.toggleKey.getKeyCode()) + " toggle  "
                + Keyboard.getKeyName(PurpleClicker.menuKey.getKeyCode()) + " menu";
        small(keys, x + 12, y + H - 16, TEXT_DIM);
    }

    private void drawPage(int x, int y, int mx, int my, int accent) {
        int cx = x + SIDE + 16;
        int right = x + W - 14;

        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, y + 15, 0);
        GlStateManager.scale(1.25f, 1.25f, 1f);
        fontRendererObj.drawString(TABS[tab], 0, 0, TEXT);
        GlStateManager.popMatrix();
        fontRendererObj.drawString(DESCS[tab], cx, y + 30, TEXT_DIM);

        List<Element> page = pages.get(tab);
        int cardTop = y + 48;
        int innerH = 0;
        for (Element e : page) innerH += e.height();
        RenderUtil.roundedRect(cx - 6, cardTop, right, cardTop + innerH + 8, 6, CARD);

        int ey = cardTop + 4;
        for (int i = 0; i < page.size(); i++) {
            Element e = page.get(i);
            e.bx = cx - 6;
            e.by = ey;
            e.bw = right - (cx - 6);
            e.bh = e.height();
            e.draw(cx + 4, ey, right - 10, mx, my, accent);
            if (i < page.size() - 1) drawRect(cx + 4, ey + e.height() - 1, right - 10, ey + e.height(), LINE);
            ey += e.height();
        }
    }

    private void small(String s, float x, float y, int color) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(0.75f, 0.75f, 1f);
        fontRendererObj.drawString(s, 0, 0, color);
        GlStateManager.popMatrix();
    }

    // ----------------------------------------------------------------- input

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        if (button == 0) {
            int x = winX, y = winY;
            // tabs
            for (int i = 0; i < TABS.length; i++) {
                if (inside(mouseX, mouseY, x + 8, y + 50 + i * 24, SIDE - 16, 20)) {
                    tab = i;
                    return;
                }
            }
            if (masterToggle.hit(mouseX, mouseY)) {
                masterToggle.click(mouseX);
                return;
            }
            for (Element e : pages.get(tab)) {
                if (e.hit(mouseX, mouseY)) {
                    e.click(mouseX);
                    return;
                }
            }
            if (inside(mouseX, mouseY, x, y, W, DRAG_STRIP)) {
                dragging = true;
                dragOffX = mouseX - winX;
                dragOffY = mouseY - winY;
            }
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        dragging = false;
        activeSlider = null;
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == PurpleClicker.menuKey.getKeyCode()) {
            mc.displayGuiScreen(null);
            return;
        }
        super.keyTyped(typedChar, keyCode); // ESC closes
    }

    @Override
    public void onGuiClosed() {
        Settings.save();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static float approach(float cur, float target, float dt) {
        return cur + (target - cur) * Math.min(1f, dt * 14f);
    }

    // -------------------------------------------------------------- elements

    private abstract class Element {
        final String label;
        int bx, by, bw, bh; // clickable area, set every frame
        Element(String label) { this.label = label; }
        abstract int height();
        /** x..right is the content area */
        abstract void draw(int x, int y, int right, int mx, int my, int accent);
        void click(int mx) {}
        boolean hit(int mx, int my) { return inside(mx, my, bx, by, bw, bh); }
        void hover(int mx, int my) {
            if (hit(mx, my) || activeSlider == this) drawRect(bx + 2, by, bx + bw - 2, by + bh, 0x08FFFFFF);
        }
    }

    private class Toggle extends Element {
        final String desc;
        final BooleanSupplier get;
        final Consumer<Boolean> set;
        float anim = -1;

        Toggle(String label, String desc, BooleanSupplier get, Consumer<Boolean> set) {
            super(label);
            this.desc = desc;
            this.get = get;
            this.set = set;
        }

        int height() { return 30; }

        void draw(int x, int y, int right, int mx, int my, int accent) {
            hover(mx, my);
            fontRendererObj.drawString(label, x, y + 7, TEXT);
            small(desc, x, y + 18, TEXT_DIM);
            drawSwitch(right - 22, y + 10, accent);
        }

        void drawSwitch(int sx, int sy, int accent) {
            boolean on = get.getAsBoolean();
            if (anim < 0) anim = on ? 1 : 0;
            anim = approach(anim, on ? 1 : 0, dt);
            int track = blend(OFF, accent, anim);
            RenderUtil.roundedRect(sx, sy, sx + 22, sy + 11, 5.5f, track);
            RenderUtil.circle(sx + 5.5f + anim * 11f, sy + 5.5f, 4f, 0xFFFFFFFF);
        }

        void click(int mx) { set.accept(!get.getAsBoolean()); }
    }

    private class Slider extends Element {
        final int min, max;
        final IntSupplier get;
        final IntConsumer set;
        final String suffix;
        int trackX, trackW;
        float anim = -1;

        Slider(String label, int min, int max, IntSupplier get, IntConsumer set, String suffix) {
            super(label);
            this.min = min;
            this.max = max;
            this.get = get;
            this.set = set;
            this.suffix = suffix;
        }

        int height() { return 32; }

        void draw(int x, int y, int right, int mx, int my, int accent) {
            hover(mx, my);
            int v = get.getAsInt();
            fontRendererObj.drawString(label, x, y + 6, TEXT);
            String val = v + suffix;
            fontRendererObj.drawString(val, right - fontRendererObj.getStringWidth(val), y + 6, accent);

            trackX = x;
            trackW = right - x;
            float t = (v - min) / (float) (max - min);
            if (anim < 0) anim = t;
            anim = approach(anim, t, dt);
            float ty = y + 21;
            drawTrack(trackX, ty, trackW, anim, accent);
            RenderUtil.circle(trackX + trackW * anim, ty + 1.5f, 4.5f, 0xFFFFFFFF);
            RenderUtil.circle(trackX + trackW * anim, ty + 1.5f, 2f, accent);
        }

        void drawTrack(int tx, float ty, int tw, float t, int accent) {
            RenderUtil.roundedRect(tx, ty, tx + tw, ty + 3, 1.5f, TRACK);
            RenderUtil.roundedRect(tx, ty, tx + Math.max(3, tw * t), ty + 3, 1.5f, accent);
        }

        void click(int mx) {
            activeSlider = this;
            setFromMouse(mx);
        }

        void setFromMouse(int mx) {
            if (trackW <= 0) return;
            float t = (mx - trackX) / (float) trackW;
            t = Math.max(0f, Math.min(1f, t));
            set.accept(Math.round(min + t * (max - min)));
        }
    }

    /** Hue slider with a rainbow track. */
    private class HueSlider extends Slider {
        HueSlider() {
            super("Accent color", 0, 360, () -> Settings.accentHue, v -> Settings.accentHue = v, "°");
        }

        @Override
        void drawTrack(int tx, float ty, int tw, float t, int accent) {
            float sat = Settings.accentSat / 100f;
            for (int i = 0; i < tw; i += 2) {
                int c = 0xFF000000 | (Color.HSBtoRGB(i / (float) tw, sat, 0.95f) & 0xFFFFFF);
                Gui.drawRect(tx + i, (int) ty, Math.min(tx + i + 2, tx + tw), (int) ty + 3, c);
            }
        }
    }

    private class Button extends Element {
        final Runnable action;
        float anim = 0;

        Button(String label, Runnable action) {
            super(label);
            this.action = action;
        }

        int height() { return 30; }

        void draw(int x, int y, int right, int mx, int my, int accent) {
            boolean hover = hit(mx, my);
            anim = approach(anim, hover ? 1 : 0, dt);
            int bg = blend(OFF, accent, anim);
            RenderUtil.roundedRect(x, y + 6, right, y + 24, 5, bg);
            int tw = fontRendererObj.getStringWidth(label);
            fontRendererObj.drawString(label, x + (right - x - tw) / 2, y + 11, 0xFFFFFFFF);
        }

        void click(int mx) { action.run(); }
    }

    private static int blend(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
        int br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
