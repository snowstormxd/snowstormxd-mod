package com.example;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget; // For a close button
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.entity.player.PlayerEntity; // To get armor for preview

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ArmorHudPositionScreen extends Screen {

    private boolean isDragging = false;
    private double dragStartX, dragStartY; // Mouse position when drag started relative to HUD corner
    
    // Define a rough bounding box for the HUD preview for dragging purposes
    // These should be based on how many items are typically shown (e.g., 4 armor slots)
    private static final int ICON_HEIGHT_PREVIEW = 16;
    private static final int TEXT_HEIGHT_PREVIEW = 8; // Approximate text height
    private static final int PADDING_BELOW_TEXT_PREVIEW = 2;
    private static final int SPACING_BETWEEN_ITEMS_PREVIEW = 4;
    private static final int HUD_ITEM_BLOCK_HEIGHT = TEXT_HEIGHT_PREVIEW + PADDING_BELOW_TEXT_PREVIEW + ICON_HEIGHT_PREVIEW + SPACING_BETWEEN_ITEMS_PREVIEW;
    private static final int HUD_PREVIEW_WIDTH = 16 + 50; // Icon width + approx text width (can be refined)
    private static final int HUD_PREVIEW_HEIGHT = (HUD_ITEM_BLOCK_HEIGHT * 4) - SPACING_BETWEEN_ITEMS_PREVIEW; // Height for 4 items

    public ArmorHudPositionScreen(Text title) {
        super(title);
    }

    @Override
    protected void init() {
        super.init();
        // Add a button to close the screen
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> {
            this.client.setScreen(null); // Close the screen
        }).dimensions(this.width / 2 - 50, this.height - 30, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta); // Renders the background if any

        // Draw a semi-transparent background or an overlay to indicate GUI is active
        context.fill(0, 0, this.width, this.height, 0x50000000); // Semi-transparent black

        // Draw instructions
        Text instructions = Text.literal("Click and drag the Armor HUD to reposition. Press 'Done' or ESC to save.");
        context.drawTextWithShadow(this.textRenderer, instructions, 
                                   (this.width - this.textRenderer.getWidth(instructions)) / 2, 10, 0xFFFFFF);

        // Render a preview of the armor HUD at its current position
        // This uses the same logic as renderArmorStatus but directly here for preview
        PlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            renderPreviewArmorStatus(context, player, UtilityModClient.armorHudX, UtilityModClient.armorHudY);
        }

        // Optionally, draw a border around the draggable area for clarity
        context.drawBorder(UtilityModClient.armorHudX - 2, UtilityModClient.armorHudY - 2, 
                           HUD_PREVIEW_WIDTH + 4, HUD_PREVIEW_HEIGHT + 4, 0xFFFFFFFF); // White border
    }

    // Renders a preview of the armor HUD with percentage durability above each item
    private void renderPreviewArmorStatus(DrawContext drawContext, PlayerEntity player, int hudX, int hudY) {
        List<ItemStack> armorItemsToDisplay = new ArrayList<>();
        int actualItemCount = 0;

        if (player != null) {
            for (ItemStack stack : player.getInventory().armor) {
                if (!stack.isEmpty()) {
                    actualItemCount++;
                }
                armorItemsToDisplay.add(stack); // Add all, including empty, for consistent slot display
            }
        }
        
        // If no armor equipped, fill with 4 empty slots for preview consistency
        if (actualItemCount == 0) {
            armorItemsToDisplay.clear();
            for (int i = 0; i < 4; i++) {
                armorItemsToDisplay.add(ItemStack.EMPTY);
            }
        }
        Collections.reverse(armorItemsToDisplay); // Helmet first

        int currentX = hudX;
        int currentY = hudY;

        // Use constants defined above for consistency in preview
        int textHeight = TEXT_HEIGHT_PREVIEW;
        int paddingBelowText = PADDING_BELOW_TEXT_PREVIEW;
        int iconHeight = ICON_HEIGHT_PREVIEW;
        int spacingBetweenItems = SPACING_BETWEEN_ITEMS_PREVIEW;
        int slotsToRender = 4; // Always render 4 slots in preview

        for (int i = 0; i < slotsToRender; i++) {
            ItemStack itemStack = (i < armorItemsToDisplay.size()) ? armorItemsToDisplay.get(i) : ItemStack.EMPTY;

            String durabilityText = "";
            if (!itemStack.isEmpty() && itemStack.isDamageable() && itemStack.getMaxDamage() > 0) {
                int maxDamage = itemStack.getMaxDamage();
                int currentDamage = itemStack.getDamage();
                int remainingDurability = maxDamage - currentDamage;
                double percentage = ((double) remainingDurability / maxDamage) * 100.0;
                durabilityText = String.format("%.0f%%", percentage);
            } else if (!itemStack.isEmpty() && itemStack.isDamageable()){
                 durabilityText = "100%";
            } else if (itemStack.isEmpty()){
                 durabilityText = "Slot"; // Placeholder for empty slots in preview
            }
            // For non-damageable items, durabilityText can remain empty or be a placeholder

            int textWidth = MinecraftClient.getInstance().textRenderer.getWidth(durabilityText);
            int textX = currentX + (iconHeight - textWidth) / 2; // Center text

            if (!durabilityText.isEmpty()) {
                drawContext.drawTextWithShadow(
                    MinecraftClient.getInstance().textRenderer,
                    Text.literal(durabilityText),
                    textX,
                    currentY,
                    itemStack.isEmpty() ? 0xAAAAAA : 0xFFFFFF // Dim empty slot text
                );
            }

            int iconY = currentY + (!durabilityText.isEmpty() ? textHeight + paddingBelowText : 0);
            
            if (!itemStack.isEmpty()) {
                drawContext.drawItem(itemStack, currentX, iconY);
            } else {
                // Draw a placeholder box for empty slots in the preview
                drawContext.fill(currentX, iconY, currentX + iconHeight, iconY + iconHeight, 0x50808080); // Dim box
            }

            currentY += (!durabilityText.isEmpty() ? textHeight + paddingBelowText : 0) + iconHeight + spacingBetweenItems;
        }
    }
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check if the click is within the bounds of our HUD preview
        if (mouseX >= UtilityModClient.armorHudX && mouseX <= UtilityModClient.armorHudX + HUD_PREVIEW_WIDTH &&
            mouseY >= UtilityModClient.armorHudY && mouseY <= UtilityModClient.armorHudY + HUD_PREVIEW_HEIGHT) {
            if (button == 0) { // Left mouse button
                this.isDragging = true;
                // Calculate the offset from the HUD's top-left corner to the mouse click point
                this.dragStartX = mouseX - UtilityModClient.armorHudX;
                this.dragStartY = mouseY - UtilityModClient.armorHudY;
                return true; // Event handled
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.isDragging && button == 0) { // Left mouse button
            // Update the HUD position based on mouse drag, accounting for the initial click offset
            UtilityModClient.armorHudX = (int) (mouseX - this.dragStartX);
            UtilityModClient.armorHudY = (int) (mouseY - this.dragStartY);

            // Optional: Clamp position to screen bounds
            UtilityModClient.armorHudX = Math.max(0, Math.min(this.width - HUD_PREVIEW_WIDTH, UtilityModClient.armorHudX));
            UtilityModClient.armorHudY = Math.max(0, Math.min(this.height - HUD_PREVIEW_HEIGHT, UtilityModClient.armorHudY));
            return true; // Event handled
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.isDragging && button == 0) { // Left mouse button
            this.isDragging = false;
            // Here you would ideally save the new UtilityModClient.armorHudX and UtilityModClient.armorHudY
            // to a config file. For now, they are just stored in memory.
            UtilityModClient.LOGGER.info("Armor HUD position set to X: " + UtilityModClient.armorHudX + ", Y: " + UtilityModClient.armorHudY);
            return true; // Event handled
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void close() { // Changed from removed() to close() for modern MC versions for explicit close actions
        // This is also a good place to save the configuration
        // For now, it just closes the screen.
        super.close();
    }

    @Override
    public boolean isPauseScreen() {
        return false; // Game should not pause when this screen is open
    }
}
