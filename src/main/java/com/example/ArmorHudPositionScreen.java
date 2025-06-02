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
    private static final int HUD_PREVIEW_WIDTH = 16 + 50; // Icon width + text width (approx)
    private static final int HUD_PREVIEW_HEIGHT = 20 * 4; // 4 items * spacing

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

    private void renderPreviewArmorStatus(DrawContext drawContext, PlayerEntity player, int hudX, int hudY) {
        // This is a simplified version of your actual renderArmorStatus or you can call a shared method
        List<ItemStack> armorItems = new ArrayList<>();
        int itemCount = 0;
        for (ItemStack stack : player.getInventory().armor) {
            armorItems.add(stack);
            if (!stack.isEmpty()) itemCount++;
        }
        if (itemCount == 0) { // If no armor, show some placeholder text inside the box
             for (int i = 0; i < 4; i++) armorItems.add(ItemStack.EMPTY); // Simulate 4 slots for consistent height
        }

        Collections.reverse(armorItems); // Helmet first

        int currentX = hudX;
        int currentY = hudY;
        int spacing = 20;
        int drawnItems = 0;

        for (ItemStack itemStack : armorItems) {
            if (drawnItems >= 4 && itemStack.isEmpty()) continue; // Only show up to 4 slots for preview consistency if empty

            if (!itemStack.isEmpty()) {
                drawContext.drawItem(itemStack, currentX, currentY);
                if (itemStack.isDamageable()) {
                    String durabilityText = itemStack.getMaxDamage() - itemStack.getDamage() + "/" + itemStack.getMaxDamage();
                    int textX = currentX + 18;
                    int textY = currentY + (16 - MinecraftClient.getInstance().textRenderer.fontHeight) / 2 + 1;
                    drawContext.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal(durabilityText), textX, textY, 0xFFFFFF);
                }
            } else {
                 // Optionally draw empty slot placeholders if you want the preview to always have 4 slots shown
                 // drawContext.fill(currentX, currentY, currentX + 16, currentY + 16, 0x80808080); // Dim box
            }
            currentY += spacing;
            if (!itemStack.isEmpty()) drawnItems++;
            else if (itemCount == 0 && drawnItems < 4) drawnItems++; // Count placeholders if no armor
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
