package com.example;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen; // Required for new screen
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File; // NEW IMPORT
import java.io.FileReader; // NEW IMPORT
import java.io.FileWriter; // NEW IMPORT
import java.io.IOException; // NEW IMPORT
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties; // NEW IMPORT

@Environment(EnvType.CLIENT)
public class UtilityModClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger(UtilityMod.MOD_ID + "_client");
    public static boolean showLightLevelOverlay = false;

    public static int armorHudX = 10;
    public static int armorHudY = 10;

    private static KeyBinding lightOverlayKeyBinding;
    private static KeyBinding positionHudKeyBinding;

    // --- NEW: Config file ---
    private static File configFile;
    // --- END NEW ---


    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing client-side features for " + UtilityMod.MOD_ID);

        // --- NEW: Initialize and load config ---
        configFile = new File(MinecraftClient.getInstance().runDirectory, "config/" + UtilityMod.MOD_ID + ".properties");
        loadConfig();
        // --- END NEW ---

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
                client.setScreen(new ArmorHudPositionScreen(Text.literal("Position Armor HUD"))); //
            }
        });

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null && client.currentScreen == null) {
                renderArmorStatus(drawContext, client.player);
            }
        });
    }

    // --- NEW: Load config method ---
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
            // If config doesn't exist, save defaults (which also creates the file)
            saveConfig();
        }
    }
    // --- END NEW ---

    // --- NEW: Save config method ---
    public static void saveConfig() {
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
    // --- END NEW ---

    private void renderArmorStatus(DrawContext drawContext, PlayerEntity player) {
        List<ItemStack> armorItems = new ArrayList<>();
        for (ItemStack stack : player.getInventory().armor) {
            armorItems.add(stack);
        }
        Collections.reverse(armorItems); // Helmet first

        int currentX = armorHudX; //
        int currentY = armorHudY; //
        
        int iconHeight = 16; //
        int textHeight = MinecraftClient.getInstance().textRenderer.fontHeight; // Usually 8px
        int paddingBelowText = 2; // Space between percentage text and icon
        int spacingBetweenItems = 4; // Vertical space between full item blocks (text + icon)

        for (ItemStack itemStack : armorItems) {
            if (!itemStack.isEmpty()) { //
                String durabilityText = "";
                if (itemStack.isDamageable() && itemStack.getMaxDamage() > 0) { //
                    int maxDamage = itemStack.getMaxDamage(); //
                    int currentDamage = itemStack.getDamage(); //
                    int remainingDurability = maxDamage - currentDamage; //
                    double percentage = ((double) remainingDurability / maxDamage) * 100.0; //
                    durabilityText = String.format("%.0f%%", percentage); // Format as whole number percentage
                } else if (itemStack.isDamageable()) { // Max damage is 0 but damageable (unlikely but handle)
                    durabilityText = "100%"; //
                }

                int textWidth = MinecraftClient.getInstance().textRenderer.getWidth(durabilityText); //
                int textX = currentX + (iconHeight - textWidth) / 2; // Center text over the 16px icon

                if (!durabilityText.isEmpty()) {
                    drawContext.drawTextWithShadow( //
                        MinecraftClient.getInstance().textRenderer, //
                        Text.literal(durabilityText), //
                        textX, //
                        currentY, // Text at the top of the current item's block
                        0xFFFFFF // White
                    );
                }

                int iconY = currentY + (!durabilityText.isEmpty() ? textHeight + paddingBelowText : 0); //
                drawContext.drawItem(itemStack, currentX, iconY); //

                currentY += (!durabilityText.isEmpty() ? textHeight + paddingBelowText : 0) + iconHeight + spacingBetweenItems; //
            }
        }
    }
}
