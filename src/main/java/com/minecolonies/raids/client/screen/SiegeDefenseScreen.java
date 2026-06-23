package com.minecolonies.raids.client.screen;

import com.minecolonies.raids.menu.SiegeDefenseMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class SiegeDefenseScreen extends AbstractContainerScreen<SiegeDefenseMenu> {
    private static final Component START_WAVE = Component.translatable("screen.minecolonies_raids.siege_defense.start_wave");

    public SiegeDefenseScreen(final SiegeDefenseMenu menu, final Inventory playerInventory, final Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 96;
        this.inventoryLabelY = 10_000;
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(Button.builder(START_WAVE, button -> {
            if (this.minecraft != null && this.minecraft.gameMode != null) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, SiegeDefenseMenu.START_WAVE_BUTTON_ID);
            }
        }).bounds(this.leftPos + 38, this.topPos + 42, 100, 20).build());
    }

    @Override
    protected void renderBg(final GuiGraphics graphics, final float partialTick, final int mouseX, final int mouseY) {
        graphics.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, 0xE0101010);
        graphics.renderOutline(this.leftPos, this.topPos, this.imageWidth, this.imageHeight, 0xFF8A2A2A);
    }

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }
}
