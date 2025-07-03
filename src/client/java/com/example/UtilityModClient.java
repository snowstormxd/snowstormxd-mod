package com.example;

import org.joml.Matrix4f;
import org.joml.Vector4f;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

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
    public static boolean showLightLevelOverlay = false;

    public static int armorHudX = 10;
    public static int armorHudY = 10;

    private static KeyBinding lightOverlayKeyBinding;
    private static KeyBinding positionHudKeyBinding;
    private static KeyBinding mobSpawnHighlightKeyBinding;

    private static File configFile;

    @Override
    public void onInitializeClient() {
        configFile = new File(MinecraftClient.getInstance().runDirectory, "config/" + UtilityMod.MOD_ID + ".properties");
        loadConfig();

        lightOverlayKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key." + UtilityMod.MOD_ID + ".toggle_light_overlay",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_L,
            "category." + UtilityMod.MOD_ID + ".main"
        ));
        positionHudKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key." + UtilityMod.MOD_ID + ".position_armor_hud",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "category." + UtilityMod.MOD_ID + ".main"
        ));
        mobSpawnHighlightKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key." + UtilityMod.MOD_ID + ".toggle_mob_spawn_highlight",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_0,
            "category." + UtilityMod.MOD_ID + ".main"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (lightOverlayKeyBinding.wasPressed()) {
                showLightLevelOverlay = !showLightLevelOverlay;
                LOGGER.info("Light overlay " + (showLightLevelOverlay ? "ENABLED" : "DISABLED"));
            }
            while (positionHudKeyBinding.wasPressed()) {
                client.setScreen(new ArmorHudPositionScreen(Text.literal("Position Armor HUD")));
            }
            while (mobSpawnHighlightKeyBinding.wasPressed()) {
                LOGGER.info("Mob spawn overlay toggled");
            }
        });

        HudRenderCallback.EVENT.register((ctx, delta) -> {
            var mc = MinecraftClient.getInstance();
            if (mc.player != null && mc.currentScreen == null) {
                renderArmorStatus(ctx, mc.player);
            }
        });

        WorldRenderEvents.END.register(ctx -> {
            if (showLightLevelOverlay) {
                renderLightLevelOverlay(ctx);
            }
        });
    }

    public static void loadConfig() {
        Properties props = new Properties();
        if (configFile.exists()) {
            try (var r = new FileReader(configFile)) {
                props.load(r);
                armorHudX = Integer.parseInt(props.getProperty("armorHudX", "10"));
                armorHudY = Integer.parseInt(props.getProperty("armorHudY", "10"));
            } catch (IOException|NumberFormatException e) {
                LOGGER.error("Failed to load config", e);
            }
        } else saveConfig();
    }

    public static void saveConfig() {
        var dir = configFile.getParentFile();
        if (!dir.exists() && !dir.mkdirs()) return;
        var props = new Properties();
        props.setProperty("armorHudX", String.valueOf(armorHudX));
        props.setProperty("armorHudY", String.valueOf(armorHudY));
        try (var w = new FileWriter(configFile)) {
            props.store(w, UtilityMod.MOD_ID + " config");
        } catch (IOException e) {
            LOGGER.error("Failed to save config", e);
        }
    }

    private void renderArmorStatus(DrawContext ctx, PlayerEntity player) {
        List<ItemStack> items = new ArrayList<>();
        for (EquipmentSlot s : new EquipmentSlot[]{ EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD }) {
            items.add(player.getEquippedStack(s));
        }
        Collections.reverse(items);
        HudElementsRenderer.renderArmorDisplay(ctx, items, armorHudX, armorHudY, false);
    }

    private void renderLightLevelOverlay(WorldRenderContext ctx) {
        var mc = MinecraftClient.getInstance();
        PlayerEntity p = mc.player;
        if (p == null) return;

        World w = mc.world;
        int cx = p.getBlockX() >> 4, cz = p.getBlockZ() >> 4;
        int baseX = cx << 4, baseZ = cz << 4;

        Matrix4f proj = ctx.projectionMatrix();
        Matrix4f view = ctx.matrixStack().peek().getModelViewMatrix();
        Matrix4f vp = new Matrix4f(proj).mul(view);

        Vec3d cam = ctx.camera().getPos();
        int fbW = mc.getWindow().getFramebufferWidth();
        int fbH = mc.getWindow().getFramebufferHeight();

        for (int x = baseX; x < baseX + 16; x++) {
            for (int z = baseZ; z < baseZ + 16; z++) {
                int y = w.getTopY() - 1;
                while (y > 0 && w.getBlockState(new BlockPos(x, y, z)).isAir()) y--;

                int light = w.getLightLevel(LightType.BLOCK, new BlockPos(x, y, z));
                String s = String.valueOf(light);

                float wx = (float)(x + 0.5 - cam.x),
                      wy = (float)(y + 1.2 - cam.y),
                      wz = (float)(z + 0.5 - cam.z);
                Vector4f v4 = new Vector4f(wx, wy, wz, 1f);
                v4.mul(vp);
                if (v4.w() <= 0) continue;

                float ndcX = v4.x() / v4.w();
                float ndcY = v4.y() / v4.w();

                int sx = (int)((ndcX * 0.5f + 0.5f) * fbW);
                int sy = (int)((-ndcY * 0.5f + 0.5f) * fbH);

                mc.textRenderer.draw(s, sx - mc.textRenderer.getWidth(s) / 2, sy, 0xFFFFFF);
            }
        }
    }

    public static class HudElementsRenderer {
        public static final int ICON_SIZE = 16;
        public static final int PADDING_BELOW_TEXT = 2;
        public static final int SPACING_BETWEEN_ITEMS = 4;
        public static final int HUD_ITEM_BLOCK_HEIGHT_CALC =
            MinecraftClient.getInstance().textRenderer.fontHeight
            + PADDING_BELOW_TEXT
            + ICON_SIZE
            + SPACING_BETWEEN_ITEMS;

        public static void renderArmorDisplay(DrawContext ctx,
                                              List<ItemStack> items,
                                              int x, int y,
                                              boolean preview) {
            var mc = MinecraftClient.getInstance();
            int curX = x, curY = y, th = mc.textRenderer.fontHeight;
            List<ItemStack> disp = new ArrayList<>();
            if (preview) {
                disp.addAll(items);
                while (disp.size() < 4) disp.add(ItemStack.EMPTY);
                if (disp.size() > 4) disp = disp.subList(0, 4);
            } else {
                for (var it : items) if (!it.isEmpty()) disp.add(it);
            }
            for (ItemStack it : disp) {
                String txt = "";
                if (!it.isEmpty() && it.isDamageable() && it.getMaxDamage() > 0) {
                    int rem = it.getMaxDamage() - it.getDamage();
                    txt = String.format("%.0f%%", (rem / (double) it.getMaxDamage()) * 100);
                } else if (!it.isEmpty() && it.isDamageable()) {
                    txt = "100%";
                } else if (preview && it.isEmpty()) {
                    txt = "Slot";
                }
                int w = mc.textRenderer.getWidth(txt), tx = curX + (ICON_SIZE - w) / 2;
                if (!txt.isEmpty()) ctx.drawTextWithShadow(mc.textRenderer, Text.literal(txt), tx, curY,
                    (preview && it.isEmpty()) ? 0xAAAAAA : 0xFFFFFF);
                int iconY = curY + (!txt.isEmpty() ? th + PADDING_BELOW_TEXT : 0);
                if (!it.isEmpty()) ctx.drawItem(it, curX, iconY);
                else if (preview) ctx.fill(curX, iconY, curX + ICON_SIZE, iconY + ICON_SIZE, 0x50808080);

                curY += (!txt.isEmpty() ? th + PADDING_BELOW_TEXT : 0) + ICON_SIZE + SPACING_BETWEEN_ITEMS;
            }
        }
    }
}
