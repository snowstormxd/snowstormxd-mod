package com.example;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
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

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.world.LightType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.RenderLayer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.world.chunk.light.ChunkLightProvider;

@Environment(EnvType.CLIENT)
public class UtilityModClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger(UtilityMod.MOD_ID + "_client");
    public static boolean showLightLevelOverlay = false;

    // Default position (e.g., top-left corner)
    public static int armorHudX = 10;
    public static int armorHudY = 10;

    private static KeyBinding lightOverlayKeyBinding;
    private static KeyBinding positionHudKeyBinding;

    private static File configFile;

    @Override
    // Inside UtilityModClient.java

@Override
public void onInitializeClient() {
    LOGGER.info("Initializing client-side features for " + UtilityMod.MOD_ID); //
    configFile = new File(MinecraftClient.getInstance().runDirectory, "config/" + UtilityMod.MOD_ID + ".properties"); //
    loadConfig(); //

    lightOverlayKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key." + UtilityMod.MOD_ID + ".toggle_light_overlay", //
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_L, //
            "category." + UtilityMod.MOD_ID + ".main" //
    ));

    positionHudKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key." + UtilityMod.MOD_ID + ".position_armor_hud", //
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_K, //
            "category." + UtilityMod.MOD_ID + ".main" //
    ));

    // NEW: Register mob spawn highlight keybinding
    mobSpawnHighlightKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key." + UtilityMod.MOD_ID + ".toggle_mob_spawn_highlight", // We'll add this to en_us.json
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_0, // The '0' key
            "category." + UtilityMod.MOD_ID + ".main"
    ));

    ClientTickEvents.END_CLIENT_TICK.register(client -> {
        while (lightOverlayKeyBinding.wasPressed()) { //
            showLightLevelOverlay = !showLightLevelOverlay; //
            if (showLightLevelOverlay) { //
                LOGGER.info("Light level overlay ENABLED"); //
            } else {
                LOGGER.info("Light level overlay DISABLED"); //
            }
        }

        while (positionHudKeyBinding.wasPressed()) { //
            client.setScreen(new ArmorHudPositionScreen(Text.literal("Position Armor HUD"))); //
        }

        // NEW: Handle mob spawn highlight keybinding
        while (mobSpawnHighlightKeyBinding.wasPressed()) {
            showMobSpawnHighlightOverlay = !showMobSpawnHighlightOverlay;
            if (showMobSpawnHighlightOverlay) {
                LOGGER.info("Mob Spawn Highlight Overlay ENABLED");
            } else {
                LOGGER.info("Mob Spawn Highlight Overlay DISABLED");
            }
        }
    });

    HudRenderCallback.EVENT.register((drawContext, tickDelta) -> { //
        MinecraftClient client = MinecraftClient.getInstance(); //
        if (client.player != null && client.currentScreen == null) { //
            renderArmorStatus(drawContext, client.player); //
        }
    });

    // NEW: Register world rendering event for mob spawn highlights
    WorldRenderEvents.END.register(context -> {
        if (showMobSpawnHighlightOverlay && context.gameRenderer() != null && context.world() != null && context.camera() != null) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                renderMobSpawnHighlights(context);
            }
        }
    });
}
    public static void loadConfig() {
        Properties properties = new Properties();
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                properties.load(reader);
                armorHudX = Integer.parseInt(properties.getProperty("armorHudX", "10"));
                armorHudY = Integer.parseInt(properties.getProperty("armorHudY", "10"));
                LOGGER.info("Loaded Armor HUD position from config: X=" + armorHudX + ", Y=" + armorHudY);
            } catch (IOException | NumberFormatException e) {
                LOGGER.error("Failed to load config for " + UtilityMod.MOD_ID + ", using defaults.", e);
            }
        } else {
            saveConfig(); // Create config with defaults if it doesn't exist
        }
    }

    public static void saveConfig() {
        // Ensure the config directory exists
        File configDir = configFile.getParentFile();
        if (!configDir.exists()) {
            if (!configDir.mkdirs()) {
                LOGGER.error("Could not create config directory: " + configDir.getAbsolutePath());
                return;
            }
        }
        
        Properties properties = new Properties();
        properties.setProperty("armorHudX", String.valueOf(armorHudX));
        properties.setProperty("armorHudY", String.valueOf(armorHudY));

        try (FileWriter writer = new FileWriter(configFile)) {
            properties.store(writer, UtilityMod.MOD_ID + " Config");
            LOGGER.info("Saved Armor HUD position to config: X=" + armorHudX + ", Y=" + armorHudY);
        } catch (IOException e) {
            LOGGER.error("Failed to save config for " + UtilityMod.MOD_ID, e);
        }
    }

    private void renderArmorStatus(DrawContext drawContext, PlayerEntity player) {
        List<ItemStack> armorItems = new ArrayList<>();
        for (ItemStack stack : player.getInventory().armor) {
            armorItems.add(stack);
        }
        Collections.reverse(armorItems); // Helmet first

        HudElementsRenderer.renderArmorDisplay(drawContext, armorItems, armorHudX, armorHudY, false);
    }

    public static class HudElementsRenderer {
        public static final int ICON_SIZE = 16;
        public static final int PADDING_BELOW_TEXT = 2;
        public static final int SPACING_BETWEEN_ITEMS = 4;
        // Calculated height for a single item block (text + padding + icon + spacing)
        // This is used by ArmorHudPositionScreen for its bounding box.
        public static final int HUD_ITEM_BLOCK_HEIGHT_CALC = MinecraftClient.getInstance().textRenderer.fontHeight + PADDING_BELOW_TEXT + ICON_SIZE + SPACING_BETWEEN_ITEMS;


        public static void renderArmorDisplay(DrawContext drawContext, List<ItemStack> armorItemsInput, int x, int y, boolean isPreview) {
            MinecraftClient client = MinecraftClient.getInstance();
            int currentX = x;
            int currentY = y;
            int textHeight = client.textRenderer.fontHeight;

            List<ItemStack> itemsToDisplay = new ArrayList<>();
            if (isPreview) {
                // For preview, always prepare 4 slots.
                // Start with actual items, then fill with EMPTY.
                for(ItemStack stack : armorItemsInput) {
                    itemsToDisplay.add(stack);
                }
                while (itemsToDisplay.size() < 4) {
                    itemsToDisplay.add(ItemStack.EMPTY);
                }
                // Ensure only 4 items for preview if more were somehow passed
                if (itemsToDisplay.size() > 4) {
                    itemsToDisplay = itemsToDisplay.subList(0, 4);
                }
            } else {
                // For actual HUD, only show non-empty items from input.
                for (ItemStack stack : armorItemsInput) {
                    if (!stack.isEmpty()) {
                        itemsToDisplay.add(stack);
                    }
                }
            }
            
            // Note: The input `armorItemsInput` for renderArmorStatus is already reversed.
            // If `armorItemsInput` for preview wasn't reversed before calling, it would need it here.

            for (ItemStack itemStack : itemsToDisplay) {
                // In non-preview mode, we've already filtered for non-empty.
                // In preview mode, we always process the slot.
                String durabilityText = "";
                if (!itemStack.isEmpty() && itemStack.isDamageable() && itemStack.getMaxDamage() > 0) {
                    int maxDamage = itemStack.getMaxDamage();
                    int currentDamage = itemStack.getDamage();
                    int remainingDurability = maxDamage - currentDamage;
                    double percentage = ((double) remainingDurability / maxDamage) * 100.0;
                    durabilityText = String.format("%.0f%%", percentage);
                } else if (!itemStack.isEmpty() && itemStack.isDamageable()) { // Should have maxDamage > 0, but good fallback
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
                    drawContext.fill(currentX, iconY, currentX + ICON_SIZE, iconY + ICON_SIZE, 0x50808080);
                }

                currentY += (!durabilityText.isEmpty() ? textHeight + PADDING_BELOW_TEXT : 0) + ICON_SIZE + SPACING_BETWEEN_ITEMS;
            }
        }
    }
}
