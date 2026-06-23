package com.minecolonies.raids.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

public record RaidCommandMenuProvider(BlockPos blockPos) implements MenuProvider {
    private static final Component TITLE = Component.translatable("screen.minecolonies_raids.raid_command.title");

    @Override
    public Component getDisplayName() {
        return TITLE;
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(final int containerId, final Inventory inventory, final Player player) {
        return new RaidCommandMenu(containerId, inventory, this.blockPos);
    }
}
