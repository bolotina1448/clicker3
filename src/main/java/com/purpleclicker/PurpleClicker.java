package com.purpleclicker;

import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.lang.reflect.Field;
import java.util.Random;

@Mod(modid = PurpleClicker.MODID, name = "PurpleClicker", version = "2.0",
     clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public class PurpleClicker {

    public static final String MODID = "purpleclicker";

    public static KeyBinding toggleKey;
    public static KeyBinding menuKey;

    private final Random random = new Random();
    private long nextLeft = 0;
    private long nextRight = 0;

    private static Field leftClickCounterField;
    private static boolean reflectionFailed = false;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        Settings.load(event.getSuggestedConfigurationFile());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Both rebindable in Options > Controls > "PurpleClicker"
        toggleKey = new KeyBinding("Toggle clicker", Keyboard.KEY_R, "PurpleClicker");
        menuKey = new KeyBinding("Open menu", Keyboard.KEY_RSHIFT, "PurpleClicker");
        ClientRegistry.registerKeyBinding(toggleKey);
        ClientRegistry.registerKeyBinding(menuKey);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (toggleKey.isPressed()) {
            Settings.enabled = !Settings.enabled;
            Settings.save();
            if (mc.thePlayer != null) {
                mc.thePlayer.addChatMessage(new ChatComponentText(
                        EnumChatFormatting.LIGHT_PURPLE + "[PurpleClicker] " +
                        (Settings.enabled ? EnumChatFormatting.GREEN + "ON" : EnumChatFormatting.RED + "OFF")));
            }
        }
        if (menuKey.isPressed() && mc.currentScreen == null) {
            mc.displayGuiScreen(new ClickerGui());
        }
    }

    private static boolean aimingAtBlock(Minecraft mc) {
        MovingObjectPosition mop = mc.objectMouseOver;
        return mop != null
                && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && mc.theWorld != null
                && mc.theWorld.getBlockState(mop.getBlockPos()).getBlock().getMaterial() != Material.air;
    }

    private static boolean aimingAtEntity(Minecraft mc) {
        MovingObjectPosition mop = mc.objectMouseOver;
        return mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY;
    }

    /**
     * Runs at the start of every game tick, before Minecraft handles mining.
     * A click into the air gives a 0.5 s "miss" cooldown that also blocks mining;
     * clearing it while you aim at a block lets you break blocks normally.
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.currentScreen != null) return;

        if (Settings.enabled && Settings.leftEnabled && Settings.breakBlocks
                && Mouse.isButtonDown(0) && aimingAtBlock(mc)) {
            resetMissCooldown(mc);
        }
    }

    private static void resetMissCooldown(Minecraft mc) {
        if (reflectionFailed) return;
        try {
            if (leftClickCounterField == null) {
                leftClickCounterField = ReflectionHelper.findField(Minecraft.class, "leftClickCounter", "field_71429_W");
            }
            leftClickCounterField.setInt(mc, 0);
        } catch (Throwable t) {
            reflectionFailed = true;
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();

        if (!Settings.enabled || mc.thePlayer == null || mc.currentScreen != null || !mc.inGameHasFocus) {
            nextLeft = 0;
            nextRight = 0;
            return;
        }
        long now = System.currentTimeMillis();

        // ---- Left clicker: runs while you hold the left mouse button ----
        // Pauses only while the crosshair is on a block; resumes as soon as the block is gone.
        boolean mining = Settings.breakBlocks && aimingAtBlock(mc);
        if (Settings.leftEnabled && Mouse.isButtonDown(0) && !mining) {
            if (nextLeft == 0) {
                nextLeft = now + delay(Settings.leftMinCps, Settings.leftMaxCps); // first click is your real one
            } else {
                // several clicks per frame if FPS is lower than CPS
                int guard = 0;
                while (now >= nextLeft && guard++ < 10) {
                    KeyBinding.onTick(mc.gameSettings.keyBindAttack.getKeyCode());
                    nextLeft += delay(Settings.leftMinCps, Settings.leftMaxCps);
                }
                if (now >= nextLeft) nextLeft = now + delay(Settings.leftMinCps, Settings.leftMaxCps);
            }
        } else {
            nextLeft = 0;
        }

        // ---- Right clicker: runs while you hold the right mouse button ----
        if (Settings.rightEnabled && Mouse.isButtonDown(1)) {
            if (nextRight == 0) {
                nextRight = now + delay(Settings.rightCps, Settings.rightCps);
            } else {
                int guard = 0;
                while (now >= nextRight && guard++ < 10) {
                    KeyBinding.onTick(mc.gameSettings.keyBindUseItem.getKeyCode());
                    nextRight += delay(Settings.rightCps, Settings.rightCps);
                }
                if (now >= nextRight) nextRight = now + delay(Settings.rightCps, Settings.rightCps);
            }
        } else {
            nextRight = 0;
        }
    }

    private long delay(int minCps, int maxCps) {
        double cps = minCps + random.nextDouble() * (maxCps - minCps);
        return (long) (1000.0 / Math.max(1.0, cps));
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.TEXT || !Settings.showHud) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings.showDebugInfo) return;
        FontRenderer fr = mc.fontRendererObj;

        String name = "PurpleClicker";
        String info = "";
        if (Settings.enabled) {
            StringBuilder sb = new StringBuilder();
            if (Settings.leftEnabled) sb.append(Settings.leftMinCps).append('-').append(Settings.leftMaxCps);
            if (Settings.rightEnabled) {
                if (sb.length() > 0) sb.append(" | ");
                sb.append("R ").append(Settings.rightCps);
            }
            info = sb.toString();
        } else {
            info = "off";
        }

        int x = 4, y = 4;
        int w = 16 + fr.getStringWidth(name) + (info.isEmpty() ? 0 : 6 + fr.getStringWidth(info)) + 6;
        RenderUtil.roundedRect(x, y, x + w, y + 16, 5, 0xB8141219);
        RenderUtil.circle(x + 8, y + 8, 2.5f, Settings.enabled ? Settings.accent() : 0xFF6A6478);
        fr.drawStringWithShadow(name, x + 14, y + 4, 0xFFEDEAF4);
        if (!info.isEmpty()) {
            fr.drawStringWithShadow(info, x + 14 + fr.getStringWidth(name) + 6, y + 4,
                    Settings.enabled ? Settings.accent() : 0xFF8C869C);
        }
    }
}
