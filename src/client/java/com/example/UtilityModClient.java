package com.example;

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
import net.minecraft.block.BlockState;

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

    // Default position for the Armor HUD
    public static int armorHudX = 10;
    public static int armorHudY = 10;

    private static KeyBinding lightOverlayKeyBinding;
    private static KeyBinding positionHudKeyBinding;
    private static KeyBinding mobSpawnHighlightKeyBinding;

    private static File configFile;

    // Toggle for the mob‐spawn highlight overlay
    public static boolean showMobSpawnHighlightOverlay = false;

    // Light‐level thresholds (block light)
    private static final int LIGHT_LEVEL_RED_MAX = 0;    // ≤ 0 → RED
    private static final int LIGHT_LEVEL_YELLOW_MAX = 7; // 1–7 → YELLOW, >7 → GREEN

    // ARGB colors (semi‐transparent)
    private static final int COLOR_RED = 0x70FF0000;
    private static final int COLOR_YELLOW = 0x70FFFF00;
    private static final int COLOR_GREEN = 0x7000FF00;

    // Scan radius around the player
    private static final int SCAN_RADIUS_HORIZONTAL = 8;
    private static final int SCAN_RADIUS_VERTICAL = 4;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing client‐side features for " + UtilityMod.MOD_ID);

        // 1) Set up config file path
        configFile = new File(MinecraftClient.getInstance().runDirectory, "config/" + UtilityMod.MOD_ID + ".properties");
        loadConfig();

        // 2) Register key bindings
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

        // 3) Listen for key presses each tick
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (lightOverlayKeyBinding.wasPressed()) {
                showLightLevelOverlay = !showLightLevelOverlay;
                LOGGER.info("Light level overlay " + (showLightLevelOverlay ? "ENABLED" : "DISABLED"));
            }
            while (positionHudKeyBinding.wasPressed()) {
                client.setScreen(new ArmorHudPositionScreen(Text.literal("Position Armor HUD")));
            }
            while (mobSpawnHighlightKeyBinding.wasPressed()) {
                showMobSpawnHighlightOverlay = !showMobSpawnHighlightOverlay;
                LOGGER.info("Mob Spawn Highlight Overlay " + (showMobSpawnHighlightOverlay ? "ENABLED" : "DISABLED"));
            }
        });

        // 4) Draw the Armor HUD on the in‐game HUD
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null && mc.currentScreen == null) {
                renderArmorStatus(drawContext, mc.player);
            }
        });

        // 5) Stubbed‐out mob spawn highlights (no rendering calls to missing APIs)
        WorldRenderEvents.END.register(context -> {
            if (showMobSpawnHighlightOverlay) {
                // We’re stubbing this out because the old rendering methods no longer exist.
                // If you want to add real highlighting in 1.21.5, you’ll need to use
                // the new Fabric rendering pipeline (VertexConsumers, custom RenderLayer, etc.).
                // For now, do nothing so it compiles.
            }
        });
    }

    // ── CONFIG LOADING / SAVING ─────────────────────────────────────────────────────

    public static void loadConfig() {
        Properties properties = new Properties();
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                properties.load(reader);
                armorHudX = Integer.parseInt(properties.getProperty("armorHudX", "10"));
                armorHudY = Integer.parseInt(properties.getProperty("armorHudY", "10"));
                LOGGER.info("Loaded Armor HUD position: X=" + armorHudX + ", Y=" + armorHudY);
            } catch (IOException | NumberFormatException e) {
                LOGGER.error("Failed to load config, using defaults.", e);
            }
        } else {
            saveConfig();
        }
    }

    public static void saveConfig() {
        File configDir = configFile.getParentFile();
        if (!configDir.exists() && !configDir.mkdirs()) {
            LOGGER.error("Could not create config directory: " + configDir.getAbsolutePath());
            return;
        }
        Properties properties = new Properties();
        properties.setProperty("armorHudX", String.valueOf(armorHudX));
        properties.setProperty("armorHudY", String.valueOf(armorHudY));
        try (FileWriter writer = new FileWriter(configFile)) {
            properties.store(writer, UtilityMod.MOD_ID + " Config");
            LOGGER.info("Saved Armor HUD position: X=" + armorHudX + ", Y=" + armorHudY);
        } catch (IOException e) {
            LOGGER.error("Failed to save config.", e);
        }
    }

    // ── RENDER MOB SPAWN HIGHLIGHTS (STUB) ─────────────────────────────────────────────

    private void renderMobSpawnHighlights(WorldRenderContext context) {
        // Intentionally empty—no calls to Tessellator, RenderSystem, etc., so it compiles.
    }

    // ── RENDER ARMOR STATUS HUD ─────────────────────────────────────────────────────

    private void renderArmorStatus(DrawContext drawContext, PlayerEntity player) {
        List<ItemStack> armorItems = new ArrayList<>();
        // Read each armor piece via EquipmentSlot
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD
        }) {
            ItemStack stack = player.getEquippedStack(slot);
            armorItems.add(stack);
        }
        Collections.reverse(armorItems); // helmet first
        HudElementsRenderer.renderArmorDisplay(drawContext, armorItems, armorHudX, armorHudY, false);
    }

    // ── HUD ELEMENTS RENDERER ────────────────────────────────────────────────────────

    public static class HudElementsRenderer {
        public static final int ICON_SIZE = 16;
        public static final int PADDING_BELOW_TEXT = 2;
        public static final int SPACING_BETWEEN_ITEMS = 4;
        public static final int HUD_ITEM_BLOCK_HEIGHT_CALC =
            MinecraftClient.getInstance().textRenderer.fontHeight
            + PADDING_BELOW_TEXT
            + ICON_SIZE
            + SPACING_BETWEEN_ITEMS;

        /**
         * Renders a vertical, 4‐slot armor HUD at (x, y).
         * If isPreview is true, always show exactly 4 slots (possibly empty).
         * Otherwise, only non‐empty armor pieces are displayed.
         */
        public static void renderArmorDisplay(
                DrawContext drawContext,
                List<ItemStack> armorItemsInput,
                int x, int y,
                boolean isPreview
        ) {
            MinecraftClient client = MinecraftClient.getInstance();
            int currentX = x;
            int currentY = y;
            int textHeight = client.textRenderer.fontHeight;

            List<ItemStack> itemsToDisplay = new ArrayList<>();
            if (isPreview) {
                // Copy input, then pad or trim to 4
                for (ItemStack stack : armorItemsInput) {
                    itemsToDisplay.add(stack);
                }
                while (itemsToDisplay.size() < 4) {
                    itemsToDisplay.add(ItemStack.EMPTY);
                }
                if (itemsToDisplay.size() > 4) {
                    itemsToDisplay = itemsToDisplay.subList(0, 4);
                }
            } else {
                // Only show non‐empty armor pieces
                for (ItemStack stack : armorItemsInput) {
                    if (!stack.isEmpty()) {
                        itemsToDisplay.add(stack);
                    }
                }
            }

            for (ItemStack itemStack : itemsToDisplay) {
                String durabilityText = "";
                if (!itemStack.isEmpty() && itemStack.isDamageable() && itemStack.getMaxDamage() > 0) {
                    int maxDamage = itemStack.getMaxDamage();
                    int currentDamage = itemStack.getDamage();
                    int remaining = maxDamage - currentDamage;
                    double percent = ((double) remaining / maxDamage) * 100.0;
                    durabilityText = String.format("%.0f%%", percent);
                } else if (!itemStack.isEmpty() && itemStack.isDamageable()) {
                    durabilityText = "100%";
                } else if (isPreview && itemStack.isEmpty()) {
                    durabilityText = "Slot";
                }

                int textWidth = client.textRenderer.getWidth(durabilityText);
                int textX = currentX + (ICON_SIZE - textWidth) / 2;
                if (!durabilityText.isEmpty()) {
                    drawContext.drawTextWithShadow(
                        client.textRenderer,
                        Text.literal(durabilityText),
                        textX,
                        currentY,
                        (isPreview && itemStack.isEmpty()) ? 0xAAAAAA : 0xFFFFFF
                    );
                }

                int iconY = currentY + (!durabilityText.isEmpty() ? textHeight + PADDING_BELOW_TEXT : 0);
                if (!itemStack.isEmpty()) {
                    drawContext.drawItem(itemStack, currentX, iconY);
                } else if (isPreview) {
                    drawContext.fill(
                        currentX, iconY,
                        currentX + ICON_SIZE,
                        iconY + ICON_SIZE,
                        0x50808080
                    );
                }

                currentY += (!durabilityText.isEmpty() ? textHeight + PADDING_BELOW_TEXT : 0)
                            + ICON_SIZE
                            + SPACING_BETWEEN_ITEMS;
            }
        }
    }
}
