package com.purpleclicker;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/** Small helpers for rounded shapes in 1.8.9 GUIs. */
public final class RenderUtil {

    private RenderUtil() {}

    public static void roundedRect(float x, float y, float x2, float y2, float r, int color) {
        r = Math.max(0f, Math.min(r, Math.min((x2 - x) / 2f, (y2 - y) / 2f)));
        float a = (color >> 24 & 255) / 255f;
        float red = (color >> 16 & 255) / 255f;
        float g = (color >> 8 & 255) / 255f;
        float b = (color & 255) / 255f;

        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.color(red, g, b, a);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        GlStateManager.disableCull();

        // Same winding as vanilla Gui.drawRect (counter-clockwise on screen)
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(GL11.GL_POLYGON, DefaultVertexFormats.POSITION);
        corner(wr, x + r, y + r, r, 270, 180);   // top-left
        corner(wr, x + r, y2 - r, r, 180, 90);   // bottom-left
        corner(wr, x2 - r, y2 - r, r, 90, 0);    // bottom-right
        corner(wr, x2 - r, y + r, r, 0, -90);    // top-right
        tess.draw();

        if (cull) GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    public static void circle(float cx, float cy, float r, int color) {
        roundedRect(cx - r, cy - r, cx + r, cy + r, r, color);
    }

    private static void corner(WorldRenderer wr, float cx, float cy, float r, int from, int to) {
        int step = from <= to ? 10 : -10;
        for (int deg = from; step > 0 ? deg <= to : deg >= to; deg += step) {
            double rad = Math.toRadians(deg);
            wr.pos(cx + Math.cos(rad) * r, cy + Math.sin(rad) * r, 0).endVertex();
        }
    }

    /** Same color with a different alpha (0-255). */
    public static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }
}
