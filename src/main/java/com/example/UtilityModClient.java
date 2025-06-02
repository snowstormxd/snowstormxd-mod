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
    private void renderArmorStatus(DrawContext drawContext, PlayerEntity player) {
        List<ItemStack> armorItems = new ArrayList<>();
        for (ItemStack stack : player.getInventory().armor) {
            armorItems.add(stack);
        }
        Collections.reverse(armorItems);

        // --- MODIFIED: Use dynamic X and Y positions ---
        int currentX = armorHudX;
        int currentY = armorHudY;
        // --- END MODIFIED ---
        int spacing = 20;

        for (ItemStack itemStack : armorItems) {
            if (!itemStack.isEmpty()) {
                // --- MODIFIED: Use currentX and currentY ---
                drawContext.drawItem(itemStack, currentX, currentY);
                // --- END MODIFIED ---

                if (itemStack.isDamageable()) {
                    int maxDamage = itemStack.getMaxDamage();
                    int currentDamage = itemStack.getDamage();
                    int remainingDurability = maxDamage - currentDamage;
                    String durabilityText = remainingDurability + "/" + maxDamage;

                    // --- MODIFIED: Adjust textX based on currentX ---
                    int textX = currentX + 18;
                    int textY = currentY + (16 - MinecraftClient.getInstance().textRenderer.fontHeight) / 2 + 1;
                    // --- END MODIFIED ---

                    drawContext.drawTextWithShadow(
                        MinecraftClient.getInstance().textRenderer,
                        Text.literal(durabilityText),
                        textX,
                        textY,
                        0xFFFFFF
                    );
                }
                // --- MODIFIED: Increment currentY for next item ---
                currentY += spacing;
                // --- END MODIFIED ---
            }
