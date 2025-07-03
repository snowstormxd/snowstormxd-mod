package com.example;

import org.joml.Matrix4f;
import org.joml.Vector4f;

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
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.WorldChunk;

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

    // Armor HUD position
    public static int armorHudX = 10;
    public static int armorHudY = 10;

    // Toggles
    private static KeyBinding lightOverlayKey;
    private static boolean showLightOverlay = false;
    private static KeyBinding positionHudKey;
    private static KeyBinding mobOverlayKey;
    public static boolean showMobOverlay = false;

    // Config file
    private static File configFile;

    @Override
    public void onInitializeClient() {
        // --- Load or create config ---
        configFile = new File(MinecraftClient.getInstance().runDirectory, "config/" + UtilityMod.MOD_ID + ".properties");
        loadConfig();

        // --- Keybindings ---
        lightOverlayKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key." + UtilityMod.MOD_ID + ".light_overlay",
            InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_L,
            "category." + UtilityMod.MOD_ID
        ));
        positionHudKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key." + UtilityMod.MOD_ID + ".position_hud",
            InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K,
            "category." + UtilityMod.MOD_ID
        ));
        mobOverlayKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key." + UtilityMod.MOD_ID + ".mob_overlay",
            InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_0,
            "category." + UtilityMod.MOD_ID
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (lightOverlayKey.wasPressed()) {
                showLightOverlay = !showLightOverlay;
                LOGGER.info("Light overlay {}", showLightOverlay);
            }
            while (positionHudKey.wasPressed()) {
                client.setScreen(new ArmorHudPositionScreen(Text.literal("Position Armor HUD")));
            }
            while (mobOverlayKey.wasPressed()) {
                showMobOverlay = !showMobOverlay;
                LOGGER.info("Mob overlay {}", showMobOverlay);
            }
        });

        // --- HUD Rendering (Armor) ---
        HudRenderCallback.EVENT.register((ctx, delta) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null && mc.currentScreen == null) {
                renderArmorHud(ctx, mc.player);
            }
        });

        // --- World Rendering (Light Overlay) ---
        WorldRenderEvents.END.register(this::renderLightOverlay);
        // You can also register mob overlay similarly...
    }

    // --- Config load/save ---
    private static void loadConfig() {
        Properties props = new Properties();
        if (configFile.exists()) {
            try (var r = new FileReader(configFile)) {
                props.load(r);
                armorHudX = Integer.parseInt(props.getProperty("armorHudX", "10"));
                armorHudY = Integer.parseInt(props.getProperty("armorHudY", "10"));
            } catch (IOException|NumberFormatException e) {
                LOGGER.error("Failed to load config", e);
            }
        } else saveConfig();
    }
    private static void saveConfig() {
        var dir = configFile.getParentFile();
        if (!dir.exists() && !dir.mkdirs()) return;
        Properties props = new Properties();
        props.setProperty("armorHudX", String.valueOf(armorHudX));
        props.setProperty("armorHudY", String.valueOf(armorHudY));
        try (var w = new FileWriter(configFile)) {
            props.store(w, UtilityMod.MOD_ID + " config");
        } catch (IOException e) {
            LOGGER.error("Failed to save config", e);
        }
    }

    // --- Armor HUD ---
    private void renderArmorHud(DrawContext ctx, PlayerEntity player) {
        List<ItemStack> items = new ArrayList<>();
        for (EquipmentSlot slot : new EquipmentSlot[]{
            EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD
        }) {
            items.add(player.getEquippedStack(slot));
        }
        Collections.reverse(items);
        HudElementsRenderer.renderArmorDisplay(ctx, items, armorHudX, armorHudY, false);
    }

    // --- Light Overlay ---
    private void renderLightOverlay(WorldRenderContext ctx) {
        if (!showLightOverlay) return;

        var mc = MinecraftClient.getInstance();
        PlayerEntity p = mc.player;
        World w = mc.world;
        if (p == null || w == null) return;

        // Chunk bounds
        WorldChunk chunk = w.getChunk(p.getBlockPos());
        BlockPos origin = chunk.getPos().getStartPos();

        // Build VP matrix
        Matrix4f proj = ctx.projectionMatrix();
        Matrix4f view = ctx.matrixStack().peek().getPositionMatrix();
        Matrix4f vp = new Matrix4f(proj).mul(view);

        Vec3d cam = ctx.camera().getPos();
        VertexConsumerProvider.Immediate provider = mc.getBufferBuilders().getEntityVertexConsumers();

        int fbW = mc.getWindow().getFramebufferWidth();
        int fbH = mc.getWindow().getFramebufferHeight();

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = origin.getX() + dx;
                int z = origin.getZ() + dz;

                // Find top block using heightmap
                int y = w.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
                BlockPos pos = new BlockPos(x, y, z);

                int light = w.getLightLevel(LightType.BLOCK, pos);
                String txt = String.valueOf(light);
                if (light < 0) continue;

                // World→clip
                float wx = (float)(x + 0.5 - cam.x),
                      wy = (float)(y + 1.2 - cam.y),
                      wz = (float)(z + 0.5 - cam.z);
                Vector4f v = new Vector4f(wx, wy, wz, 1f).mul(vp);
                if (v.w() <= 0) continue;

                float ndcX = v.x()/v.w();
                float ndcY = v.y()/v.w();

                int sx = (int)((ndcX*0.5f + 0.5f)*fbW);
                int sy = (int)((-ndcY*0.5f + 0.5f)*fbH);

                // Draw on screen
                OrderedText ot = Text.literal(txt).asOrderedText();
                mc.textRenderer.draw(ot, sx - mc.textRenderer.getWidth(txt)/2f, sy, 0xFFFFFF,
                    false, proj, provider, false, 0, 15728880);
            }
        }

        provider.draw();
    }

    // --- HUD Elements Renderer (unchanged) ---
    public static class HudElementsRenderer {
        public static final int ICON_SIZE = 16;
        public static final int PADDING_BELOW_TEXT = 2;
        public static final int SPACING_BETWEEN_ITEMS = 4;
        public static final int HUD_ITEM_BLOCK_HEIGHT_CALC =
            MinecraftClient.getInstance().textRenderer.fontHeight
            + PADDING_BELOW_TEXT + ICON_SIZE + SPACING_BETWEEN_ITEMS;

        public static void renderArmorDisplay(
                DrawContext ctx,
                List<ItemStack> input,
                int x, int y,
                boolean preview
        ) {
            var mc = MinecraftClient.getInstance();
            int curX = x, curY = y, th = mc.textRenderer.fontHeight;
            var items = new ArrayList<>(input);
            if (preview) {
                while (items.size() < 4) items.add(ItemStack.EMPTY);
                if (items.size() > 4) items.subList(4, items.size()).clear();
            } else {
                items.removeIf(ItemStack::isEmpty);
            }
            for (ItemStack it : items) {
                String dt = "";
                if (!it.isEmpty() && it.isDamageable() && it.getMaxDamage()>0) {
                    double pct = (it.getMaxDamage()-it.getDamage())/(double)it.getMaxDamage()*100;
                    dt = String.format("%.0f%%", pct);
                } else if (preview && it.isEmpty()) dt = "Slot";

                int w = mc.textRenderer.getWidth(dt);
                if (!dt.isEmpty()) {
                    ctx.drawTextWithShadow(mc.textRenderer, Text.literal(dt), curX + (ICON_SIZE-w)/2, curY,
                        dt.equals("Slot")?0xAAAAAA:0xFFFFFF);
                }

                int iconY = curY + (!dt.isEmpty()?th+PADDING_BELOW_TEXT:0);
                if (!it.isEmpty()) ctx.drawItem(it, curX, iconY);
                else if (preview) ctx.fill(curX,iconY,curX+ICON_SIZE,iconY+ICON_SIZE,0x50808080);

                curY += (!dt.isEmpty()?th+PADDING_BELOW_TEXT:0) + ICON_SIZE + SPACING_BETWEEN_ITEMS;
            }
        }
    }
}
