package com.example;

import com.mojang.blaze3d.opengl.GlStateManager;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.systems.RenderSystem;
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

    // Toggle state for the mob‐spawn highlight overlay
    public static boolean showMobSpawnHighlightOverlay = false;

    // Light‐level thresholds
    private static final int LIGHT_LEVEL_RED_MAX = 0;    // ≤ 0 → RED
    private static final int LIGHT_LEVEL_YELLOW_MAX = 7; // 1–7 → YELLOW, > 7 → GREEN

    // ARGB colors (semi-transparent)
    private static final int COLOR_RED = 0x70FF0000;
    private static final int COLOR_YELLOW = 0x70FFFF00;
    private static final int COLOR_GREEN = 0x7000FF00;

    // Radius around the player to scan for spawnable blocks
    private static final int SCAN_RADIUS_HORIZONTAL = 8;
    private static final int SCAN_RADIUS_VERTICAL = 4;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing client‐side features for " + UtilityMod.MOD_ID);

        // Set up config file path: runDirectory/config/<modid>.properties
        configFile = new File(MinecraftClient.getInstance().runDirectory, "config/" + UtilityMod.MOD_ID + ".properties");
        loadConfig();

        // Key binding: toggle light‐level overlay (press L)
        lightOverlayKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key." + UtilityMod.MOD_ID + ".toggle_light_overlay",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_L,
                "category." + UtilityMod.MOD_ID + ".main"
        ));

        // Key binding: open the Armor‐HUD position screen (press K)
        positionHudKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key." + UtilityMod.MOD_ID + ".position_armor_hud",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                "category." + UtilityMod.MOD_ID + ".main"
        ));

        // Key binding: toggle mob-spawn highlight overlay (press 0)
        mobSpawnHighlightKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key." + UtilityMod.MOD_ID + ".toggle_mob_spawn_highlight",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_0,
                "category." + UtilityMod.MOD_ID + ".main"
        ));

        // Tick event: watch for key presses
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (lightOverlayKeyBinding.wasPressed()) {
                showLightLevelOverlay = !showLightLevelOverlay;
                if (showLightLevelOverlay) {
                    LOGGER.info("Light level overlay ENABLED");
                } else {
                    LOGGER.info("Light level overlay DISABLED");
                }
            }

            while (positionHudKeyBinding.wasPressed()) {
                client.setScreen(new ArmorHudPositionScreen(Text.literal("Position Armor HUD")));
            }

            while (mobSpawnHighlightKeyBinding.wasPressed()) {
                showMobSpawnHighlightOverlay = !showMobSpawnHighlightOverlay;
                if (showMobSpawnHighlightOverlay) {
                    LOGGER.info("Mob Spawn Highlight Overlay ENABLED");
                } else {
                    LOGGER.info("Mob Spawn Highlight Overlay DISABLED");
                }
            }
        });

        // Render the armor‐status HUD on the client’s HUD
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null && client.currentScreen == null) {
                renderArmorStatus(drawContext, client.player);
            }
        });

        // Render event at end of world render: draw mob‐spawn highlights if enabled
        WorldRenderEvents.END.register(context -> {
            if (showMobSpawnHighlightOverlay && context.gameRenderer() != null && context.world() != null && context.camera() != null) {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null) {
                    renderMobSpawnHighlights(context);
                }
            }
        });
    }

    // ─── CONFIG LOADING / SAVING ─────────────────────────────────────────────────

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
            saveConfig(); // Write default values if no config file exists
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

    // ─── MOB-SPAWN HIGHLIGHT RENDERING ──────────────────────────────────────────────

    private void renderMobSpawnHighlights(WorldRenderContext context) {
    var world = context.world();
    MinecraftClient client = MinecraftClient.getInstance();
    PlayerEntity player = client.player;
    if (player == null || world == null) return;

    Vec3d cameraPos = context.camera().getPos();
    var matrices = context.matrixStack();

    // — Use RenderSystem.enableBlend() (still valid in 1.21.5)
    RenderSystem.enableBlend();
    // Set the blend function directly via GlStateManager:
    GlStateManager.blendFunc(
        GlStateManager.SrcFactor.SRC_ALPHA,
        GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA
    );

    BlockPos.Mutable mutablePos = new BlockPos.Mutable();
    BlockPos playerPos = player.getBlockPos();

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

                    matrices.push();
                    matrices.translate(
                        surfacePos.getX() - cameraPos.x,
                        surfacePos.getY() - cameraPos.y + 1.0,
                        surfacePos.getZ() - cameraPos.z
                    );

                    float offset = 0.005f;
                    var matrix = matrices.peek().getPositionMatrix();

                    // — NEW: Use Tessellator’s buffer + draw() rather than BufferRenderer
                    Tessellator tessellator = Tessellator.getInstance();
                    BufferBuilder bufferBuilder = tessellator.getBuffer();
                    RenderSystem.setShader(GameRenderer::getPositionColorShader);
                    bufferBuilder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

                    int alpha = (color >> 24) & 0xFF;
                    int red   = (color >> 16) & 0xFF;
                    int green = (color >> 8)  & 0xFF;
                    int blue  =  color        & 0xFF;

                    bufferBuilder.vertex(matrix, 0.0f, offset, 0.0f)
                                 .color(red, green, blue, alpha)
                                 .next();
                    bufferBuilder.vertex(matrix, 0.0f, offset, 1.0f)
                                 .color(red, green, blue, alpha)
                                 .next();
                    bufferBuilder.vertex(matrix, 1.0f, offset, 1.0f)
                                 .color(red, green, blue, alpha)
                                 .next();
                    bufferBuilder.vertex(matrix, 1.0f, offset, 0.0f)
                                 .color(red, green, blue, alpha)
                                 .next();

                    tessellator.draw();

                    GlStateManager.disableBlend();
                    matrices.pop();
                }
            }
        }
    }
}


    // ─── ARMOR HUD RENDERING ────────────────────────────────────────────────────────

    private void renderArmorStatus(DrawContext drawContext, PlayerEntity player) {
        List<ItemStack> armorItems = new ArrayList<>();
        // MC 1.21.5 no longer has getArmorStacks(); iterate slots 0..3 instead
        for (int slot = 0; slot < 4; slot++) {
            ItemStack stack = player.getInventory().getArmorStack(slot);
            armorItems.add(stack);
        }
        Collections.reverse(armorItems); // Helmet first
        HudElementsRenderer.renderArmorDisplay(drawContext, armorItems, armorHudX, armorHudY, false);
    }

    // ─── NESTED HUD ELEMENTS RENDERER ───────────────────────────────────────────────

    public static class HudElementsRenderer {
        public static final int ICON_SIZE = 16;
        public static final int PADDING_BELOW_TEXT = 2;
        public static final int SPACING_BETWEEN_ITEMS = 4;
        public static final int HUD_ITEM_BLOCK_HEIGHT_CALC =
            MinecraftClient.getInstance().textRenderer.fontHeight
            + PADDING_BELOW_TEXT
            + ICON_SIZE
            + SPACING_BETWEEN_ITEMS;

        /** Renders the four‐slot armor display at (x, y). If isPreview==true, always draw exactly 4 slots. */
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
                // Always show 4 slots in preview
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
                // In real HUD, show only non‐empty armor items
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
