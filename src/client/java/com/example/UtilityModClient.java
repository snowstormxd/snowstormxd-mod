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
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
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

    // Toggles & keybinds
    private static KeyBinding lightOverlayKey;
    private static boolean showLightOverlay = false;
    private static KeyBinding positionHudKey;
    private static KeyBinding mobOverlayKey;
    public static boolean showMobOverlay = false;

    // Config file
    private static File configFile;

    @Override
    public void onInitializeClient() {
        // Config
        configFile = new File(MinecraftClient.getInstance().runDirectory,
                              "config/" + UtilityMod.MOD_ID + ".properties");
        loadConfig();

        // Keybindings
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

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (lightOverlayKey.wasPressed()) {
                showLightOverlay = !showLightOverlay;
                LOGGER.info("Light overlay {}", showLightOverlay);
            }
            while (positionHudKey.wasPressed()) {
                client.setScreen(new ArmorHudPositionScreen(Text.literal("Position Armor HUD")));
            }
            while (mobOverlayKey.wasPressed()) {
                showMobOverlay = !showMobOverlay;
                LOGGER.info("Mob overlay {}", showMobOverlay);
            }
        });

        // HUD render: armor HUD + light overlay
        HudRenderCallback.EVENT.register((ctx, tick) -> {
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
        });
    }

    // Public so the screen can save config
    public static void saveConfig() {
        var dir = configFile.getParentFile();
        if (!dir.exists() && !dir.mkdirs()) {
            LOGGER.error("Could not create config directory");
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

    // Renders the 4‑slot armor HUD at (armorHudX, armorHudY)
    private void renderArmorHud(DrawContext ctx, PlayerEntity p) {
        List<ItemStack> items = new ArrayList<>();
        for (EquipmentSlot s : new EquipmentSlot[]{
            EquipmentSlot.FEET, EquipmentSlot.LEGS,
            EquipmentSlot.CHEST, EquipmentSlot.HEAD
        }) {
            items.add(p.getEquippedStack(s));
        }
        Collections.reverse(items);
        HudElementsRenderer.renderArmorDisplay(ctx, items, armorHudX, armorHudY, false);
    }

    // Renders block light levels as text for the 16×16 chunk around the player
    private void renderLightOverlay(DrawContext ctx, PlayerEntity p, World world) {
        BlockPos bp = p.getBlockPos();
        int cx = bp.getX() >> 4, cz = bp.getZ() >> 4;
        Chunk chunk = world.getChunk(cx, cz);
        BlockPos origin = chunk.getPos().getStartPos();

        Vec3d cam = MinecraftClient.getInstance()
                        .gameRenderer.getCamera().getPos();
        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = origin.getX() + dx;
                int z = origin.getZ() + dz;

                // Surface block via Heightmap
                int y = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
                BlockPos pos = new BlockPos(x, y, z);

                int light = world.getLightLevel(LightType.BLOCK, pos);
                String s = String.valueOf(light);

                // Simple world→HUD coords:
                double wx = x + 0.5 - cam.x;
                double wy = y + 1.2 - cam.y;
                double wz = z + 0.5 - cam.z;

                int sx = sw / 2 + (int)(wx * 4);
                int sy = sh / 2 - (int)(wz * 4) - (int)(wy * 4);

                ctx.drawTextWithShadow(
                    MinecraftClient.getInstance().textRenderer,
                    Text.literal(s),
                    sx - MinecraftClient.getInstance()
                         .textRenderer.getWidth(s)/2,
                    sy,
                    0xFFFFFF
                );
            }
        }
    }


    // Nested class to render the Armor HUD slots
    public static class HudElementsRenderer {
        public static final int ICON_SIZE = 16;
        public static final int PADDING_BELOW_TEXT = 2;
        public static final int SPACING_BETWEEN_ITEMS = 4;
        public static final int HUD_ITEM_BLOCK_HEIGHT_CALC =
            MinecraftClient.getInstance()
                         .textRenderer.fontHeight
            + PADDING_BELOW_TEXT
            + ICON_SIZE
            + SPACING_BETWEEN_ITEMS;

        public static void renderArmorDisplay(
            DrawContext ctx,
            List<ItemStack> input,
            int x, int y,
            boolean preview
        ) {
            var mc = MinecraftClient.getInstance();
            int curX = x, curY = y, th = mc.textRenderer.fontHeight;

            List<ItemStack> items = new ArrayList<>(input);
            if (preview) {
                while (items.size() < 4) items.add(ItemStack.EMPTY);
                if (items.size() > 4) items.subList(4, items.size()).clear();
            } else {
                items.removeIf(ItemStack::isEmpty);
            }

            for (ItemStack it : items) {
                String dt = "";
                if (!it.isEmpty() && it.isDamageable() && it.getMaxDamage() > 0) {
                    double pct = (it.getMaxDamage() - it.getDamage())
                                 / (double)it.getMaxDamage() * 100;
                    dt = String.format("%.0f%%", pct);
                } else if (preview && it.isEmpty()) {
                    dt = "Slot";
                }

                int w = mc.textRenderer.getWidth(dt);
                if (!dt.isEmpty()) {
                    ctx.drawTextWithShadow(
                        mc.textRenderer,
                        Text.literal(dt),
                        curX + (ICON_SIZE - w)/2,
                        curY,
                        dt.equals("Slot") ? 0xAAAAAA : 0xFFFFFF
                    );
                }

                int iconY = curY
                          + (!dt.isEmpty() ? th + PADDING_BELOW_TEXT : 0);
                if (!it.isEmpty()) {
                    ctx.drawItem(it, curX, iconY);
                } else if (preview) {
                    ctx.fill(
                        curX, iconY,
                        curX + ICON_SIZE,
                        iconY + ICON_SIZE,
                        0x50808080
                    );
                }

                curY += (!dt.isEmpty() ? th + PADDING_BELOW_TEXT : 0)
                        + ICON_SIZE
                        + SPACING_BETWEEN_ITEMS;
            }
        }
    }
}
