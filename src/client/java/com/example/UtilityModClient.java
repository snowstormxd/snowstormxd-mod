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
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.util.Identifier;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.world.LightType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
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

    // ARGB colors (semi-transparent)
    private static final int COLOR_RED = 0x70FF0000;
    private static final int COLOR_YELLOW = 0x70FFFF00;
    private static final int COLOR_GREEN = 0x7000FF00;

    // Scan radius around player
    private static final int SCAN_RADIUS_HORIZONTAL = 8;
    private static final int SCAN_RADIUS_VERTICAL = 4;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing client‐side features for " + UtilityMod.MOD_ID);

        // 1) Set up config file path
        configFile = new File(MinecraftClient.getInstance().runDirectory, "config/" + UtilityMod.MOD_ID + ".properties");
        loadConfig();

        // 2) Register keybindings
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
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null && client.currentScreen == null) {
                renderArmorStatus(drawContext, client.player);
            }
        });

        // 5) Draw the mob spawn highlights at the end of the world‐render pass
        WorldRenderEvents.END.register(context -> {
            if (showMobSpawnHighlightOverlay && context.world() != null && context.camera() != null) {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null) {
                    renderMobSpawnHighlights(context);
                }
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

    // ── RENDER MOB SPAWN HIGHLIGHTS ─────────────────────────────────────────────────

    private void renderMobSpawnHighlights(WorldRenderContext context) {
        var world = context.world();
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerEntity player = client.player;
        if (player == null || world == null) return;

        Vec3d cameraPos = context.camera().getPos();
        var matrices = context.matrixStack();

        // Loop over blocks around the player
        BlockPos playerPos = player.getBlockPos();
        BlockPos.Mutable mutablePos = new BlockPos.Mutable();

        for (int x = playerPos.getX() - SCAN_RADIUS_HORIZONTAL; x <= playerPos.getX() + SCAN_RADIUS_HORIZONTAL; x++) {
            for (int z = playerPos.getZ() - SCAN_RADIUS_HORIZONTAL; z <= playerPos.getZ() + SCAN_RADIUS_HORIZONTAL; z++) {
                for (int y = playerPos.getY() - SCAN_RADIUS_VERTICAL; y <= playerPos.getY() + SCAN_RADIUS_VERTICAL; y++) {
                    mutablePos.set(x, y, z);
                    BlockPos surfacePos = mutablePos.down();

                    BlockState surfaceState = world.getBlockState(surfacePos);
                    BlockState spawnSpaceState = world.getBlockState(mutablePos);

                    if (surfaceState.isSolid() &&
                        surfaceState.isFullCube(world, surfacePos) &&
                        !spawnSpaceState.isSolid()) {

                        int blockLight = world.getLightLevel(LightType.BLOCK, mutablePos);

                        int color;
                        if (blockLight <= LIGHT_LEVEL_RED_MAX) {
                            color = COLOR_RED;
                        } else if (blockLight <= LIGHT_LEVEL_YELLOW_MAX) {
                            color = COLOR_YELLOW;
                        } else {
                            color = COLOR_GREEN;
                        }

                        // Push matrix so translation is undone later
                        matrices.push();
                        matrices.translate(
                            surfacePos.getX() - cameraPos.x,
                            surfacePos.getY() - cameraPos.y + 1.0,
                            surfacePos.getZ() - cameraPos.z
                        );
                        float offset = 0.005f;
                        var matrix = matrices.peek().getPositionMatrix();

                        // Instead of BufferBuilder + Tessellator, use the context's VertexConsumer:
                        VertexConsumer consumer = context.consumers()
                            .getBuffer(RenderLayer.getPositionColor());

                        // Break ARGB into RGBA bytes (0–255)
                        int alpha = (color >> 24) & 0xFF;
                        int red   = (color >> 16) & 0xFF;
                        int green = (color >> 8)  & 0xFF;
                        int blue  =  color        & 0xFF;

                        // Draw a 1×1 quad at (0, offset, 0) in local space
                        consumer.vertex(matrix, 0.0f, offset, 0.0f)
                                .color(red, green, blue, alpha)
                                .next();
                        consumer.vertex(matrix, 0.0f, offset, 1.0f)
                                .color(red, green, blue, alpha)
                                .next();
                        consumer.vertex(matrix, 1.0f, offset, 1.0f)
                                .color(red, green, blue, alpha)
                                .next();
                        consumer.vertex(matrix, 1.0f, offset, 0.0f)
                                .color(red, green, blue, alpha)
                                .next();

                        matrices.pop();
                    }
                }
            }
        }
    }

    // ── RENDER ARMOR STATUS HUD ─────────────────────────────────────────────────────

    private void renderArmorStatus(DrawContext drawContext, PlayerEntity player) {
        // Collect the four armor stacks (boots→helmet) from the inventory list directly
        List<ItemStack> armorItems = new ArrayList<>();
        // In 1.21.5 Yarn, inventory.armor is a DefaultedList<ItemStack>
        var inventory = player.getInventory();
        for (int slot = 0; slot < 4; slot++) {
            // boots=slot0, leggings=slot1, chest=slot2, helmet=slot3
            ItemStack stack = inventory.armor.get(slot);
            armorItems.add(stack);
        }
        Collections.reverse(armorItems); // show helmet first
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
         * Renders an armor HUD (4‐slot vertical) at (x,y). If isPreview==true,
         * always draw exactly 4 slots (possibly empty). Otherwise, only non‐empty.
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

            // Decide which ItemStacks to draw
            List<ItemStack> itemsToDisplay = new ArrayList<>();
            if (isPreview) {
                // Copy exactly, then pad or truncate to 4
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
                // Only non‐empty slots
                for (ItemStack stack : armorItemsInput) {
                    if (!stack.isEmpty()) {
                        itemsToDisplay.add(stack);
                    }
                }
            }

            // Now draw each slot vertically
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
