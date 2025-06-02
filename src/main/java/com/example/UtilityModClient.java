package com.example;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW; // For key codes
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT) // Mark this class as client-only
public class UtilityModClient implements ClientModInitializer{

    public static final Logger LOGGER = LoggerFactory.getLogger(UtilityMod.MOD_ID + "_client");
    public static boolean showLightLevelOverlay = false; // State for our overlay

    // 1. Declare the KeyBinding
    private static KeyBinding lightOverlayKeyBinding;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing client-side features for " + YourModMainClass.MOD_ID);

        // 2. Define and Register the KeyBinding
        lightOverlayKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
               "key." + UtilityMod.MOD_ID + ".toggle_light_overlay",
                InputUtil.Type.KEYSYM, // The type of input (keyboard key)
                GLFW.GLFW_KEY_L, // The default key (L key)
               "category." + UtilityMod.MOD_ID + ".main"
        ));

        // 3. Register a Client Tick Event to check for key presses
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // This code runs at the end of every client tick
            while (lightOverlayKeyBinding.wasPressed()) {
                showLightLevelOverlay = !showLightLevelOverlay; // Toggle the state
                if (showLightLevelOverlay) {
                    LOGGER.info("Light level overlay ENABLED");
                    // Later, you might want to send a message to the player:
                    // client.player.sendMessage(Text.literal("Light Level Overlay: ON"), false);
                } else {
                    LOGGER.info("Light level overlay DISABLED");
                    // client.player.sendMessage(Text.literal("Light Level Overlay: OFF"), false);
                }
            }
        });
    }
}
