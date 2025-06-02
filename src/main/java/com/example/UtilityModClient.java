package com.example;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback; // New import
import net.minecraft.client.MinecraftClient; // New import
import net.minecraft.client.gui.DrawContext; // New import
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity; // New import
import net.minecraft.item.ItemStack; // New import
import net.minecraft.text.Text; // New import
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList; // New import
import java.util.Collections; // New import
import java.util.List; // New import

@Environment(EnvType.CLIENT)
public class UtilityModClient implements ClientModInitializer {

    // Ensure UtilityMod.MOD_ID is correctly referenced from your UtilityMod class
    public static final Logger LOGGER = LoggerFactory.getLogger(UtilityMod.MOD_ID + "_client");
    public static boolean showLightLevelOverlay = false;

    private static KeyBinding lightOverlayKeyBinding;

    @Override
    public void onInitializeClient() {
        // Use UtilityMod.MOD_ID for consistency
        LOGGER.info("Initializing client-side features for " + UtilityMod.MOD_ID);

        lightOverlayKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key." + UtilityMod.MOD_ID + ".toggle_light_overlay", // References UtilityMod.MOD_ID
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_L,
                "category." + UtilityMod.MOD_ID + ".main" // References UtilityMod.MOD_ID
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (lightOverlayKeyBinding.wasPressed()) {
                showLightLevelOverlay = !showLightLevelOverlay;
                if (showLightLevelOverlay) {
                    LOGGER.info("Light level overlay ENABLED");
                    // if (client.player != null) {
                    // client.player.sendMessage(Text.literal("Light Level Overlay: ON"), false);
                    // }
                } else {
                    LOGGER.info("Light level overlay DISABLED");
                    // if (client.player != null) {
                    // client.player.sendMessage(Text.literal("Light Level Overlay: OFF"), false);
                    // }
                }
            }
        });

        // --- Armor Status HUD ---
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null && client.currentScreen == null) { // Also check if no screen is open
                renderArmorStatus(drawContext, client.player);
            }
        });
    }

    private void renderArmorStatus(DrawContext drawContext, PlayerEntity player) {
        // Get armor items. Vanilla order is feet, legs, chest, head.
        // We'll reverse it for a more natural top-to-bottom display (helmet first).
        List<ItemStack> armorItems = new ArrayList<>();
        for (ItemStack stack : player.getInventory().armor) {
            // We add even empty ItemStacks to maintain order if you want to show empty slots later
            // but for now, we'll filter non-empty ones before drawing.
            // If you want to draw placeholders for empty slots, you'd adjust the logic here.
            armorItems.add(stack); 
        }
        Collections.reverse(armorItems); // Helmet is now at index 0

        int xPosition = 10; // X position from the left edge of the screen
        int yPosition = 10; // Y position from the top edge of the screen
        int spacing = 20;   // Vertical spacing between armor items (icon is 16px, text might add more)

        for (ItemStack itemStack : armorItems) {
            if (!itemStack.isEmpty()) {
                // Draw the armor item icon
                drawContext.drawItem(itemStack, xPosition, yPosition);

                // Draw durability text if the item is damageable
                if (itemStack.isDamageable()) {
                    int maxDamage = itemStack.getMaxDamage();
                    int currentDamage = itemStack.getDamage();
                    int remainingDurability = maxDamage - currentDamage;
                    String durabilityText = remainingDurability + "/" + maxDamage;

                    // Slightly adjust text position to be to the right of and centered with the icon
                    int textX = xPosition + 18; 
                    int textY = yPosition + (16 - MinecraftClient.getInstance().textRenderer.fontHeight) / 2 + 1; // Center text vertically with icon

                    drawContext.drawTextWithShadow(
                        MinecraftClient.getInstance().textRenderer,
                        Text.literal(durabilityText),
                        textX,
                        textY,
                        0xFFFFFF // Color (white)
                    );
                }
                yPosition += spacing; // Move down for the next item
            }
            // If you wanted to show empty slots, you would still increment yPosition here
            // else if (itemStack.isEmpty()){
            //    yPosition += spacing; // or a smaller spacing for empty slots
            // }
        }
    }
}
