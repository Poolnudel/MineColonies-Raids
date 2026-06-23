package com.minecolonies.raids.menu;

import com.minecolonies.raids.integration.minecolonies.MineColoniesIntegration;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

public record SiegeDefenseMenuProvider(MineColoniesIntegration.TownHallContext context) implements MenuProvider {
    private static final Component TITLE = Component.translatable("screen.minecolonies_raids.siege_defense.title");

    @Override
    public Component getDisplayName() {
        return TITLE;
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(final int containerId, final Inventory inventory, final Player player) {
        return new SiegeDefenseMenu(containerId, inventory, this.context.dimension(), this.context.colonyId(), this.context.townHallPos());
    }
}
