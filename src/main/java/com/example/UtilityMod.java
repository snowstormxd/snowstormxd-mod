package com.example;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UtilityMod implements ModInitializer {
    // Define your MOD_ID. This MUST match the "id" in your fabric.mod.json
    // Your fabric.mod.json has "id": "snowstormxd_utility_mod"
    public static final String MOD_ID = "snowstormxd_utility_mod"; 
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, client-specific Dstuff like rendering and keybindings should
        // be handled in UtilityModClient.java.
        LOGGER.info("Hello from {}!", MOD_ID);

        // You can add other common initializations here if needed.
    }
}
