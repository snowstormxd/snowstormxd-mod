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
    
    private static final int ICON_HEIGHT_PREVIEW = 16; //
    private static final int TEXT_HEIGHT_PREVIEW = 8; //
    private static final int PADDING_BELOW_TEXT_PREVIEW = 2; //
    private static final int SPACING_BETWEEN_ITEMS_PREVIEW = 4; //
    private static final int HUD_ITEM_BLOCK_HEIGHT = TEXT_HEIGHT_PREVIEW + PADDING_BELOW_TEXT_PREVIEW + ICON_HEIGHT_PREVIEW + SPACING_BETWEEN_ITEMS_PREVIEW; //
    private static final int HUD_PREVIEW_WIDTH = 16 + 50; //
    private static final int HUD_PREVIEW_HEIGHT = (HUD_ITEM_BLOCK_HEIGHT * 4) - SPACING_BETWEEN_ITEMS_PREVIEW; //

    public ArmorHudPositionScreen(Text title) {
        super(title);
    }

    @Override
    protected void init() {
        super.init();
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> {
            // --- MODIFIED: Save config before closing ---
            UtilityModClient.saveConfig();
            // --- END MODIFIED ---
            this.client.setScreen(null); // Close the screen
        }).dimensions(this.width / 2 - 50, this.height - 30, 100, 20).build()); //
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta); //
        context.fill(0, 0, this.width, this.height, 0x50000000); //
        Text instructions = Text.literal("Click and drag the Armor HUD to reposition. Press 'Done' or ESC to save."); //
        context.drawTextWithShadow(this.textRenderer, instructions,  //
                                   (this.width - this.textRenderer.getWidth(instructions)) / 2, 10, 0xFFFFFF); //

        PlayerEntity player = MinecraftClient.getInstance().player; //
        if (player != null) {
            renderPreviewArmorStatus(context, player, UtilityModClient.armorHudX, UtilityModClient.armorHudY); //
        }
        context.drawBorder(UtilityModClient.armorHudX - 2, UtilityModClient.armorHudY - 2,  //
                           HUD_PREVIEW_WIDTH + 4, HUD_PREVIEW_HEIGHT + 4, 0xFFFFFFFF); //
    }

    private void renderPreviewArmorStatus(DrawContext drawContext, PlayerEntity player, int hudX, int hudY) {
        List<ItemStack> armorItemsToDisplay = new ArrayList<>(); //
        int actualItemCount = 0; //

        if (player != null) {
            for (ItemStack stack : player.getInventory().armor) { //
                if (!stack.isEmpty()) { //
                    actualItemCount++; //
                }
                armorItemsToDisplay.add(stack); //
            }
        }
        
        if (actualItemCount == 0) { //
            armorItemsToDisplay.clear(); //
            for (int i = 0; i < 4; i++) {
                armorItemsToDisplay.add(ItemStack.EMPTY); //
            }
        }
        Collections.reverse(armorItemsToDisplay); //

        int currentX = hudX; //
        int currentY = hudY; //
        int textHeight = TEXT_HEIGHT_PREVIEW; //
        int paddingBelowText = PADDING_BELOW_TEXT_PREVIEW; //
        int iconHeight = ICON_HEIGHT_PREVIEW; //
        int spacingBetweenItems = SPACING_BETWEEN_ITEMS_PREVIEW; //
        int slotsToRender = 4; //

        for (int i = 0; i < slotsToRender; i++) { //
            ItemStack itemStack = (i < armorItemsToDisplay.size()) ? armorItemsToDisplay.get(i) : ItemStack.EMPTY; //

            String durabilityText = "";
            if (!itemStack.isEmpty() && itemStack.isDamageable() && itemStack.getMaxDamage() > 0) { //
                int maxDamage = itemStack.getMaxDamage(); //
                int currentDamage = itemStack.getDamage(); //
                int remainingDurability = maxDamage - currentDamage; //
                double percentage = ((double) remainingDurability / maxDamage) * 100.0; //
                durabilityText = String.format("%.0f%%", percentage); //
            } else if (!itemStack.isEmpty() && itemStack.isDamageable()){ //
                 durabilityText = "100%"; //
            } else if (itemStack.isEmpty()){ //
                 durabilityText = "Slot"; //
            }

            int textWidth = MinecraftClient.getInstance().textRenderer.getWidth(durabilityText); //
            int textX = currentX + (iconHeight - textWidth) / 2; //

            if (!durabilityText.isEmpty()) {
                drawContext.drawTextWithShadow( //
                    MinecraftClient.getInstance().textRenderer, //
                    Text.literal(durabilityText), //
                    textX, //
                    currentY, //
                    itemStack.isEmpty() ? 0xAAAAAA : 0xFFFFFF //
                );
            }

            int iconY = currentY + (!durabilityText.isEmpty() ? textHeight + paddingBelowText : 0); //
            
            if (!itemStack.isEmpty()) { //
                drawContext.drawItem(itemStack, currentX, iconY); //
            } else {
                drawContext.fill(currentX, iconY, currentX + iconHeight, iconY + iconHeight, 0x50808080); //
            }
            currentY += (!durabilityText.isEmpty() ? textHeight + paddingBelowText : 0) + iconHeight + spacingBetweenItems; //
        }
    }
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= UtilityModClient.armorHudX && mouseX <= UtilityModClient.armorHudX + HUD_PREVIEW_WIDTH && //
            mouseY >= UtilityModClient.armorHudY && mouseY <= UtilityModClient.armorHudY + HUD_PREVIEW_HEIGHT) { //
            if (button == 0) { //
                this.isDragging = true; //
                this.dragStartX = mouseX - UtilityModClient.armorHudX; //
                this.dragStartY = mouseY - UtilityModClient.armorHudY; //
                return true; //
            }
        }
        return super.mouseClicked(mouseX, mouseY, button); //
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.isDragging && button == 0) { //
            UtilityModClient.armorHudX = (int) (mouseX - this.dragStartX); //
            UtilityModClient.armorHudY = (int) (mouseY - this.dragStartY); //
            UtilityModClient.armorHudX = Math.max(0, Math.min(this.width - HUD_PREVIEW_WIDTH, UtilityModClient.armorHudX)); //
            UtilityModClient.armorHudY = Math.max(0, Math.min(this.height - HUD_PREVIEW_HEIGHT, UtilityModClient.armorHudY)); //
            return true; //
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY); //
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.isDragging && button == 0) { //
            this.isDragging = false; //
            // Config is now saved when screen is closed or "Done" is pressed
            UtilityModClient.LOGGER.info("Armor HUD position set to X: " + UtilityModClient.armorHudX + ", Y: " + UtilityModClient.armorHudY); //
            return true; //
        }
        return super.mouseReleased(mouseX, mouseY, button); //
    }

    @Override
    public void close() { //
        // --- MODIFIED: Save config on explicit close (e.g. ESC key) ---
        UtilityModClient.saveConfig();
        // --- END MODIFIED ---
        super.close(); //
    }

    @Override
    public boolean isPauseScreen() {
        return false; //
    }
}
