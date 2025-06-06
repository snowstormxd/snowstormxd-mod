package com.example;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ArmorHudPositionScreen extends Screen {

    private boolean isDragging = false;
    private double dragStartX, dragStartY;

    // Must match the same constants in HudElementsRenderer
    private static final int PREVIEW_ICON_SIZE = UtilityModClient.HudElementsRenderer.ICON_SIZE;
    private static final int HUD_PREVIEW_WIDTH = PREVIEW_ICON_SIZE + 30;
    private static final int HUD_PREVIEW_ITEM_BLOCK_HEIGHT = UtilityModClient.HudElementsRenderer.HUD_ITEM_BLOCK_HEIGHT_CALC;
    private static final int HUD_PREVIEW_HEIGHT = (HUD_PREVIEW_ITEM_BLOCK_HEIGHT * 4)
                                                  - UtilityModClient.HudElementsRenderer.SPACING_BETWEEN_ITEMS;

    public ArmorHudPositionScreen(Text title) {
        super(title);
    }

    @Override
    protected void init() {
        super.init();
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> {
            UtilityModClient.saveConfig();
            this.client.setScreen(null);
        }).dimensions(this.width / 2 - 50, this.height - 30, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        // Draw a dark translucent background
        context.fill(0, 0, this.width, this.height, 0x50000000);
        Text instructions = Text.literal("Click and drag the Armor HUD to reposition. Press 'Done' or ESC to save.");
        context.drawTextWithShadow(
            this.textRenderer,
            instructions,
            (this.width - this.textRenderer.getWidth(instructions)) / 2,
            10,
            0xFFFFFF
        );

        PlayerEntity player = MinecraftClient.getInstance().player;
        List<ItemStack> previewArmorItems = new ArrayList<>();

        if (player != null) {
            // Read each armor piece via EquipmentSlot
            for (EquipmentSlot slot : new EquipmentSlot[]{
                    EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD
            }) {
                ItemStack stack = player.getEquippedStack(slot);
                previewArmorItems.add(stack);
            }
            Collections.reverse(previewArmorItems);
        } else {
            // No player (e.g. in main menu): show 4 empty slots
            for (int i = 0; i < 4; i++) {
                previewArmorItems.add(ItemStack.EMPTY);
            }
        }

        // Ensure exactly 4 elements
        while (previewArmorItems.size() < 4) {
            previewArmorItems.add(ItemStack.EMPTY);
        }
        if (previewArmorItems.size() > 4) {
            previewArmorItems = previewArmorItems.subList(0, 4);
        }

        UtilityModClient.HudElementsRenderer.renderArmorDisplay(
            context,
            previewArmorItems,
            UtilityModClient.armorHudX,
            UtilityModClient.armorHudY,
            true
        );

        // Draw a white border around the preview HUD
        context.drawBorder(
            UtilityModClient.armorHudX - 2,
            UtilityModClient.armorHudY - 2,
            HUD_PREVIEW_WIDTH + 4,
            HUD_PREVIEW_HEIGHT + 4,
            0xFFFFFFFF
        );
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= UtilityModClient.armorHudX &&
            mouseX <= UtilityModClient.armorHudX + HUD_PREVIEW_WIDTH &&
            mouseY >= UtilityModClient.armorHudY &&
            mouseY <= UtilityModClient.armorHudY + HUD_PREVIEW_HEIGHT &&
            button == 0) {

            isDragging = true;
            dragStartX = mouseX - UtilityModClient.armorHudX;
            dragStartY = mouseY - UtilityModClient.armorHudY;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (isDragging && button == 0) {
            UtilityModClient.armorHudX = (int) (mouseX - dragStartX);
            UtilityModClient.armorHudY = (int) (mouseY - dragStartY);

            UtilityModClient.armorHudX = Math.max(0, Math.min(this.width - HUD_PREVIEW_WIDTH, UtilityModClient.armorHudX));
            UtilityModClient.armorHudY = Math.max(0, Math.min(this.height - HUD_PREVIEW_HEIGHT, UtilityModClient.armorHudY));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isDragging && button == 0) {
            isDragging
