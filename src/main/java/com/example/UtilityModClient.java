package com.example;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
// Import the new screen class we will create
import net.minecraft.client.gui.screen.Screen; // Required for new screen
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Environment(EnvType.CLIENT)
public class UtilityModClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger(UtilityMod.MOD_ID + "_client");
    public static boolean showLightLevelOverlay = false;

    // --- NEW: Variables to store HUD position ---
    // Default position (e.g., top-left corner)
    public static int armorHudX = 10;
    public static int armorHudY = 10;
    // --- END NEW ---

    private static KeyBinding lightOverlayKeyBinding;
    // --- NEW: Keybinding for opening the HUD position GUI ---
    private static KeyBinding positionHudKeyBinding;
    // --- END NEW ---


    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing client-side features for " + UtilityMod.MOD_ID);

        lightOverlayKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key." + UtilityMod.MOD_ID + ".toggle_light_overlay",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_L,
                "category." + UtilityMod.MOD_ID + ".main"
        ));

        // --- NEW: Define and Register the KeyBinding for positioning GUI ---
        positionHudKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key." + UtilityMod.MOD_ID + ".position_armor_hud", // New key in en_us.json
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K, // Example: K key
                "category." + UtilityMod.MOD_ID + ".main"
        ));
        // --- END NEW ---

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (lightOverlayKeyBinding.wasPressed()) {
                showLightLevelOverlay = !showLightLevelOverlay;
                if (showLightLevelOverlay) {
                    LOGGER.info("Light level overlay ENABLED");
                } else {
                    LOGGER.info("Light level overlay DISABLED");
                }
            }

            // --- NEW: Check for position HUD key press ---
            while (positionHudKeyBinding.wasPressed()) {
                // We will create ArmorHudPositionScreen soon
                client.setScreen(new ArmorHudPositionScreen(Text.literal("Position Armor HUD")));
            }
            // --- END NEW ---
        });

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null && client.currentScreen == null) {
                renderArmorStatus(drawContext, client.player);
            }
        });
    }

    // Modified to use armorHudX and armorHudY
    // Renders the Armor HUD with percentage durability above each item
    private void renderArmorStatus(DrawContext drawContext, PlayerEntity player) {
        List<ItemStack> armorItems = new ArrayList<>();
        for (ItemStack stack : player.getInventory().armor) {
            armorItems.add(stack);
        }
        Collections.reverse(armorItems); // Helmet first

        int currentX = armorHudX;
        int currentY = armorHudY;
        
        int iconHeight = 16;
        int textHeight = MinecraftClient.getInstance().textRenderer.fontHeight; // Usually 8px
        int paddingBelowText = 2; // Space between percentage text and icon
        int spacingBetweenItems = 4; // Vertical space between full item blocks (text + icon)

        for (ItemStack itemStack : armorItems) {
            if (!itemStack.isEmpty()) {
                String durabilityText = "";
                if (itemStack.isDamageable() && itemStack.getMaxDamage() > 0) {
                    int maxDamage = itemStack.getMaxDamage();
                    int currentDamage = itemStack.getDamage();
                    int remainingDurability = maxDamage - currentDamage;
                    double percentage = ((double) remainingDurability / maxDamage) * 100.0;
                    durabilityText = String.format("%.0f%%", percentage); // Format as whole number percentage
                } else if (itemStack.isDamageable()) { // Max damage is 0 but damageable (unlikely but handle)
                    durabilityText = "100%";
                }
                // For non-damageable items, durabilityText remains empty, or you can add placeholder

                // Calculate X position for centered text above the icon
                int textWidth = MinecraftClient.getInstance().textRenderer.getWidth(durabilityText);
                int textX = currentX + (iconHeight - textWidth) / 2; // Center text over the 16px icon

                // Draw durability text
                if (!durabilityText.isEmpty()) {
                    drawContext.drawTextWithShadow(
                        MinecraftClient.getInstance().textRenderer,
                        Text.literal(durabilityText),
                        textX,
                        currentY, // Text at the top of the current item's block
                        0xFFFFFF // White
                    );
                }

                // Draw the armor item icon below the text
                int iconY = currentY + (!durabilityText.isEmpty() ? textHeight + paddingBelowText : 0);
                drawContext.drawItem(itemStack, currentX, iconY);

                // Move Y position down for the next item block
                // Total height for this item: text (if any) + padding + icon + spacing for next
                currentY += (!durabilityText.isEmpty() ? textHeight + paddingBelowText : 0) + iconHeight + spacingBetweenItems;
            }
        }
    }
