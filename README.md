=======================================
      snowstormxd Utility Mod
=======================================

This mod provides several quality-of-life utilities for Minecraft 1.21.5, including an Armor HUD and overlays for light levels and mob spawns.

-----------------------
 Requirements
-----------------------
* Java Development Kit (JDK) 21 or newer (for building)
* Minecraft 1.21.5
* Fabric Loader (latest version for 1.21.5 recommended)
* Fabric API (needed for the mod to run)

-----------------------
 How to Build
-----------------------
1. Clone or download the source code for this mod.
2. Open a terminal or command prompt in the root directory of the mod's source code.
3. Make the Gradle wrapper executable (if necessary):
   On Linux/macOS: `chmod +x ./gradlew`
4. Run the Gradle build command:
   On Linux/macOS: `./gradlew build`
   On Windows: `gradlew build`
5. The compiled mod JAR file (e.g., `snowstormxd_utility_mod-1.0.0.jar`) will be located in the `build/libs/` directory.

-----------------------
 How to Install
-----------------------
1. Ensure you have Fabric Loader installed for your Minecraft 1.21.5 client.
2. Download and install the Fabric API JAR from CurseForge or Modrinth. Place it in your Minecraft `mods` folder.
3. Take the `snowstormxd_utility_mod-X.X.X.jar` (the one you built or downloaded) and place it into your Minecraft `mods` folder.
   (The `mods` folder is usually located at `%appdata%/.minecraft/mods` on Windows, or `~/Library/Application Support/minecraft/mods` on macOS, or `~/.minecraft/mods` on Linux).
4. Launch Minecraft using the Fabric profile.

-----------------------
 Features
-----------------------
* Armor Status HUD (Press 'K' to position, by default)
* Toggle Light Level Overlay (Press 'L' by default)
* Toggle Mob Spawn Highlight Overlay (Press '0' by default)
* Keybindings can be changed in Minecraft's controls settings under "snowstormxd Utilities".

-----------------------
 Default Keybinds
-----------------------
* Toggle Light Overlay: L
* Position Armor HUD: K
* Toggle Mob Spawn Highlight: 0 (zero key)

-----------------------
 Configuration
-----------------------
The Armor HUD position is saved in `config/snowstormxd_utility_mod.properties` in your Minecraft directory.

Enjoy the mod!
