package com.example;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.Chunk;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

@Environment(EnvType.CLIENT)
public class UtilityModClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger(UtilityMod.MOD_ID + "_client");

    // Armor HUD position
    public static int armorHudX = 10;
    public static int armorHudY = 10;

    // Toggles
    private static KeyBinding lightOverlayKey;
    private static boolean showLightOverlay = false;
    private static KeyBinding positionHudKey;
    private static KeyBinding mobOverlayKey;
    public static boolean showMobOverlay = false;

    // Config file
    private static File configFile;

    @Override
    public void onInitializeClient() {
        // Load config
        configFile = new File(MinecraftClient.getInstance().runDirectory, "config/" + UtilityMod.MOD_ID + ".properties");
        loadConfig();

        // Keybinds
        lightOverlayKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key." + UtilityMod.MOD_ID + ".light_overlay",
            InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_L,
            "category." + UtilityMod.MOD_ID
        ));
        positionHudKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key." + UtilityMod.MOD_ID + ".position_hud",
            InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K,
            "category." + UtilityMod.MOD_ID
        ));
        mobOverlayKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key." + UtilityMod.MOD_ID + ".mob_overlay",
            InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_0,
            "category." + UtilityMod.MOD_ID
        ));

        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            while (lightOverlayKey.wasPressed()) {
                showLightOverlay = !showLightOverlay;
                LOGGER.info("Light overlay {}", showLightOverlay);
            }
            while (positionHudKey.wasPressed()) {
                c.setScreen(new ArmorHudPositionScreen(Text.literal("Position Armor HUD")));
            }
            while (mobOverlayKey.wasPressed()) {
                showMobOverlay = !showMobOverlay;
                LOGGER.info("Mob overlay {}", showMobOverlay);
            }
        });

        // Render HUDs
        HudRenderCallback.EVENT.register(this::onHudRender);
    }

    // Public so ArmorHudPositionScreen can call it
    public static void saveConfig() {
        var dir = configFile.getParentFile();
        if (!dir.exists() && !dir.mkdirs()) {
            LOGGER.error("Could not create config dir");
            return;
        }
        Properties p = new Properties();
        p.setProperty("armorHudX", String.valueOf(armorHudX));
        p.setProperty("armorHudY", String.valueOf(armorHudY));
        try (var w = new FileWriter(configFile)) {
            p.store(w, UtilityMod.MOD_ID + " config");
        } catch (IOException e) {
            LOGGER.error("Failed to save config", e);
        }
    }

    private static void loadConfig() {
        Properties p = new Properties();
        if (configFile.exists()) {
            try (var r = new FileReader(configFile)) {
                p.load(r);
                armorHudX = Integer.parseInt(p.getProperty("armorHudX", "10"));
                armorHudY = Integer.parseInt(p.getProperty("armorHudY", "10"));
            } catch (IOException|NumberFormatException e) {
                LOGGER.error("Failed to load config", e);
            }
        } else {
            saveConfig();
        }
    }

    private void onHudRender(DrawContext ctx, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity player = mc.player;
        if (player == null) return;

        // 1) Armor HUD
        if (mc.currentScreen == null) {
            renderArmorHud(ctx, player);
        }

        // 2) Light overlay
        if (showLightOverlay) {
            renderLightOverlay(ctx, player, mc.world);
        }

        // 3) Mob overlay stub (not implemented)
        // if (showMobOverlay) { ... }
    }

    private void renderArmorHud(DrawContext ctx, PlayerEntity p) {
        List<ItemStack> arr = new ArrayList<>();
        for (EquipmentSlot s : new EquipmentSlot[]{
            EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD
        }) {
            arr.add(p.getEquippedStack(s));
        }
        Collections.reverse(arr);
        HudElementsRenderer.renderArmorDisplay(ctx, arr, armorHudX, armorHudY, false);
    }

    private void renderLightOverlay(DrawContext ctx, PlayerEntity p, World w) {
        // Determine current chunk origin
        BlockPos bp = p.getBlockPos();
        int cx = bp.getX() >> 4, cz = bp.getZ() >> 4;
        Chunk chunk = w.getChunk(cx, cz);
        BlockPos origin = chunk.getPos().getStartPos();

        // Camera info
        Vec3d cam = ctx.getCamera().getPos();
        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = origin.getX() + dx;
                int z = origin.getZ() + dz;

                // Top block via Heightmap
                int y = w.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
                BlockPos pos = new BlockPos(x, y, z);

                int light = w.getLightLevel(LightType.BLOCK, pos);
                String txt = String.valueOf(light);

                // Project to screen (simple orthographic for HUD)
                double dxp = x + 0.5 - cam.x;
                double dyp = y + 1.2 - cam.y;
                double dzp = z + 0.5 - cam.z;

                // Map to 2D: center + offset (no real perspective)
                int sx = sw/2 + (int)(dxp * 8);
                int sy = sh/2 - (int)(dzp * 8) - (int)(dyp * 4);

                ctx.drawTextWithShadow(mc.textRenderer, Text.literal(txt),
                                       sx - mc.textRenderer.getWidth(txt)/2,
                                       sy, 0xFFFFFF);
            }
        }
    }

    // HUD Elements Renderer (unchanged)
    public static class HudElementsRenderer {
        public static final int ICON_SIZE = 16;
        public static final int PADDING_BELOW_TEXT = 2;
        public static final int SPACING_BETWEEN_ITEMS = 4;
        public static final int HUD_ITEM_BLOCK_HEIGHT_CALC =
            MinecraftClient.getInstance().textRenderer.fontHeight
            + PADDING_BELOW_TEXT
            + ICON_SIZE
            + SPACING_BETWEEN_ITEMS;

        public static void renderArmorDisplay(
            DrawContext ctx,
            List<ItemStack> items,
            int x, int y,
            boolean preview
        ) {
            var mc = MinecraftClient.getInstance();
            int curX = x, curY = y, th = mc.textRenderer.fontHeight;
            List<ItemStack> disp = new ArrayList<>(items);
            if (preview) {
                while (disp.size() < 4) disp.add(ItemStack.EMPTY);
                if (disp.size() > 4) disp.subList(4, disp.size()).clear();
            } else {
                disp.removeIf(ItemStack::isEmpty);
            }
            for (ItemStack it : disp) {
                String dt = "";
                if (!it.isEmpty() && it.isDamageable() && it.getMaxDamage() > 0) {
                    double pct = (it.getMaxDamage() - it.getDamage()) / (double)it.getMaxDamage() * 100;
                    dt = String.format("%.0f%%", pct);
                } else if (preview && it.isEmpty()) {
                    dt = "Slot";
                }

                int w = mc.textRenderer.getWidth(dt);
                if (!dt.isEmpty()) {
                    ctx.drawTextWithShadow(mc.textRenderer, Text.literal(dt),
                        curX + (ICON_SIZE - w)/2, curY,
                        dt.equals("Slot") ? 0xAAAAAA : 0xFFFFFF);
                }

                int iconY = curY + (!dt.isEmpty() ? th + PADDING_BELOW_TEXT : 0);
                if (!it.isEmpty()) ctx.drawItem(it, curX, iconY);
                else if (preview) ctx.fill(curX, iconY, curX+ICON_SIZE, iconY+ICON_SIZE, 0x50808080);

                curY += (!dt.isEmpty() ? th + PADDING_BELOW_TEXT : 0)
                        + ICON_SIZE + SPACING_BETWEEN_ITEMS;
            }
        }
    }
}
