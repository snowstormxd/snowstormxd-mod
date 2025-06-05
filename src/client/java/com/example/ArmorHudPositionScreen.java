package com.example;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.entity.player.PlayerEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ArmorHudPositionScreen extends Screen {

    private boolean isDragging = false; //
    private double dragStartX, dragStartY; //
    
    // Use constants from the renderer or define them consistently
    private static final int PREVIEW_ICON_SIZE = UtilityModClient.HudElementsRenderer.ICON_SIZE;
    // Approximate width: icon size + some space for text (can be refined)
    private static final int HUD_PREVIEW_WIDTH = PREVIEW_ICON_SIZE + 30; 
    // Calculate height based on 4 items, using the block height from the renderer logic
    private static final int HUD_PREVIEW_ITEM_BLOCK_HEIGHT = UtilityModClient.HudElementsRenderer.HUD_ITEM_BLOCK_HEIGHT_CALC;
    private static final int HUD_PREVIEW_HEIGHT = (HUD_PREVIEW_ITEM_BLOCK_HEIGHT * 4) - UtilityModClient.HudElementsRenderer.SPACING_BETWEEN_ITEMS; // Subtract last item's bottom spacing

    public ArmorHudPositionScreen(Text title) {
        super(title);
    }

    @Override
    protected void init() {
        super.init();
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> {
            UtilityModClient.saveConfig(); // Save position
            this.client.setScreen(null); 
        }).dimensions(this.width / 2 - 50, this.height - 30, 100, 20).build()); //
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta); 
        context.fill(0, 0, this.width, this.height, 0x50000000); 
        Text instructions = Text.literal("Click and drag the Armor HUD to reposition. Press 'Done' or ESC to save."); 
        context.drawTextWithShadow(this.textRenderer, instructions, 
                                   (this.width - this.textRenderer.getWidth(instructions)) / 2, 10, 0xFFFFFF);

        PlayerEntity player = MinecraftClient.getInstance().player;
        // For preview, always show 4 slots. If player is null, pass empty list.
    List<ItemStack> previewArmorItems = new ArrayList<>();
        if (player != null) {
            // Get each of the 4 armor slots:
            for (int slot = 0; slot < 4; slot++) {
                 ItemStack stack = player.getInventory().getArmorStack(slot);
                 previewArmorItems.add(stack);
            }
            Collections.reverse(previewArmorItems);
        } else {
            // If no player, fill with 4 empty stacks:
            for (int i = 0; i < 4; i++) {
                previewArmorItems.add(ItemStack.EMPTY);
            }
        }

        // Ensure the list is definitely 4 items for preview consistency for the renderer
        while(previewArmorItems.size() < 4) {
            previewArmorItems.add(ItemStack.EMPTY);
        }
        if(previewArmorItems.size() > 4) {
            previewArmorItems = previewArmorItems.subList(0,4);
        }


        UtilityModClient.HudElementsRenderer.renderArmorDisplay(context, previewArmorItems, UtilityModClient.armorHudX, UtilityModClient.armorHudY, true);

        context.drawBorder(UtilityModClient.armorHudX - 2, UtilityModClient.armorHudY - 2, 
                           HUD_PREVIEW_WIDTH + 4, HUD_PREVIEW_HEIGHT + 4, 0xFFFFFFFF); 
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= UtilityModClient.armorHudX && mouseX <= UtilityModClient.armorHudX + HUD_PREVIEW_WIDTH &&
            mouseY >= UtilityModClient.armorHudY && mouseY <= UtilityModClient.armorHudY + HUD_PREVIEW_HEIGHT) {
            if (button == 0) { 
                this.isDragging = true; 
                this.dragStartX = mouseX - UtilityModClient.armorHudX; 
                this.dragStartY = mouseY - UtilityModClient.armorHudY; 
                return true; 
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.isDragging && button == 0) { 
            UtilityModClient.armorHudX = (int) (mouseX - this.dragStartX); 
            UtilityModClient.armorHudY = (int) (mouseY - this.dragStartY); 

            UtilityModClient.armorHudX = Math.max(0, Math.min(this.width - HUD_PREVIEW_WIDTH, UtilityModClient.armorHudX)); 
            UtilityModClient.armorHudY = Math.max(0, Math.min(this.height - HUD_PREVIEW_HEIGHT, UtilityModClient.armorHudY)); 
            return true; 
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.isDragging && button == 0) { 
            this.isDragging = false; 
            // Position is now saved when screen is closed or "Done" is pressed.
            // No explicit save here, but logging is fine.
            UtilityModClient.LOGGER.info("Armor HUD position temporarily set to X: " + UtilityModClient.armorHudX + ", Y: " + UtilityModClient.armorHudY + " (will save on close)");
            return true; 
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void close() { 
        UtilityModClient.saveConfig(); // Save position on close (e.g. ESC)
        super.close();
    }

    @Override
    public boolean isPauseScreen() {
        return false; 
    }
}
