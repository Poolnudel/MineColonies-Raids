package com.minecolonies.raids.client.screen;

import com.minecolonies.raids.menu.SiegeDefenseMenu;
import com.minecolonies.raids.menu.SiegeDefenseViewData;
import com.minecolonies.raids.raid.WaveCalculation;
import java.util.stream.Collectors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class SiegeDefenseScreen extends AbstractContainerScreen<SiegeDefenseMenu> {
    private static final Component REPEAT_LAST = Component.translatable("screen.minecolonies_raids.siege_defense.repeat_last");
    private static final Component START_NEXT = Component.translatable("screen.minecolonies_raids.siege_defense.start_next");

    public SiegeDefenseScreen(final SiegeDefenseMenu menu, final Inventory playerInventory, final Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 300;
        this.imageHeight = 232;
        this.inventoryLabelY = 10_000;
    }

    @Override
    protected void init() {
        super.init();
        final SiegeDefenseViewData viewData = this.menu.viewData();
        final Button repeatButton = Button.builder(REPEAT_LAST, button -> {
            if (this.minecraft != null && this.minecraft.gameMode != null) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, SiegeDefenseMenu.REPEAT_LAST_WAVE_BUTTON_ID);
            }
        }).bounds(this.leftPos + 18, this.topPos + 196, 126, 20).build();
        repeatButton.active = viewData.canRepeat();
        this.addRenderableWidget(repeatButton);

        final Button nextButton = Button.builder(viewData.allWavesCompleted()
                ? Component.translatable("screen.minecolonies_raids.siege_defense.all_complete")
                : START_NEXT, button -> {
            if (this.minecraft != null && this.minecraft.gameMode != null) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, SiegeDefenseMenu.START_NEXT_WAVE_BUTTON_ID);
            }
        }).bounds(this.leftPos + 156, this.topPos + 196, 126, 20).build();
        nextButton.active = viewData.canStartNext();
        this.addRenderableWidget(nextButton);
    }

    @Override
    protected void renderBg(final GuiGraphics graphics, final float partialTick, final int mouseX, final int mouseY) {
        graphics.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, 0xE0101010);
        graphics.renderOutline(this.leftPos, this.topPos, this.imageWidth, this.imageHeight, 0xFF8A2A2A);
        renderText(graphics);
    }

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    private void renderText(final GuiGraphics graphics) {
        final SiegeDefenseViewData viewData = this.menu.viewData();
        int y = this.topPos + 14;
        graphics.drawString(this.font, this.title, this.leftPos + 14, y, 0xFFE0D0A0, false);
        y += 14;
        graphics.drawString(this.font, "Unlocked: " + viewData.highestUnlockedWave() + "  Completed: " + viewData.highestCompletedWave() + "  DP: " + viewData.defensePoints(), this.leftPos + 14, y, 0xFFFFFFFF, false);
        y += 12;
        if (viewData.activeWave()) {
            graphics.drawString(this.font, "Wave in progress: " + viewData.activeWaveNumber() + " (" + viewData.activeRemainingEnemies() + " remaining)", this.leftPos + 14, y, 0xFFFFD080, false);
            y += 14;
        }

        final WaveCalculation calculation = viewData.nextCalculation() != null ? viewData.nextCalculation() : viewData.repeatCalculation();
        if (calculation == null) {
            graphics.drawString(this.font, viewData.allWavesCompleted() ? "All four prototype waves are complete." : "Repeat unavailable: no completed wave yet.", this.leftPos + 14, y, 0xFFFFD080, false);
            return;
        }

        graphics.drawString(this.font, "Preview - Wave: " + calculation.selectedWave() + "  Multiplier: x" + String.format("%.2f", calculation.waveMultiplier()), this.leftPos + 14, y, 0xFFFFFFFF, false);
        y += 12;
        graphics.drawString(this.font, "Town Hall: Level " + calculation.snapshot().townHallLevel() + " x 2 = " + calculation.townHallContribution(), this.leftPos + 14, y, 0xFFFFFFFF, false);
        y += 12;
        graphics.drawString(this.font, "Builder's Huts: " + calculation.snapshot().builderHutCount() + " building(s)", this.leftPos + 14, y, 0xFFFFFFFF, false);
        y += 12;
        graphics.drawString(this.font, "Levels: " + levels(calculation.snapshot().builderHutLevels()), this.leftPos + 24, y, 0xFFFFFFFF, false);
        y += 12;
        graphics.drawString(this.font, "Contribution: " + calculation.builderContribution(), this.leftPos + 24, y, 0xFFFFFFFF, false);
        y += 12;
        graphics.drawString(this.font, "Residences: " + calculation.snapshot().residenceCount() + " building(s)", this.leftPos + 14, y, 0xFFFFFFFF, false);
        y += 12;
        graphics.drawString(this.font, "Levels: " + levels(calculation.snapshot().residenceLevels()), this.leftPos + 24, y, 0xFFFFFFFF, false);
        y += 12;
        graphics.drawString(this.font, "Contribution: " + calculation.residenceContribution(), this.leftPos + 24, y, 0xFFFFFFFF, false);
        y += 12;
        graphics.drawString(this.font, "Colony Score: " + calculation.townHallContribution() + " + " + calculation.builderContribution() + " + " + calculation.residenceContribution() + " = " + calculation.colonyScore(), this.leftPos + 14, y, 0xFFFFFFFF, false);
        y += 12;
        graphics.drawString(this.font, "Enemy Count: round(" + calculation.colonyScore() + " x " + String.format("%.2f", calculation.waveMultiplier()) + ") = " + calculation.enemyCount(), this.leftPos + 14, y, 0xFFFFFFFF, false);
        if (calculation.snapshot().hasWarnings()) {
            y += 12;
            graphics.drawString(this.font, "Warning: colony data could not be read completely.", this.leftPos + 14, y, 0xFFFF8080, false);
        }
    }

    private static String levels(final java.util.List<Integer> levels) {
        if (levels.isEmpty()) {
            return "none";
        }
        return levels.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }
}
