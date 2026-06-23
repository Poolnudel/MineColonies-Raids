package com.minecolonies.raids.menu;

import com.minecolonies.raids.raid.RaidWaveManager;
import com.minecolonies.raids.registry.ModBlocks;
import com.minecolonies.raids.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

public class RaidCommandMenu extends AbstractContainerMenu {
    public static final int START_RAID_BUTTON_ID = 0;

    private final BlockPos blockPos;
    private final ContainerLevelAccess access;

    public RaidCommandMenu(final int containerId, final Inventory playerInventory, final BlockPos blockPos) {
        super(ModMenus.RAID_COMMAND.get(), containerId);
        this.blockPos = blockPos;
        this.access = ContainerLevelAccess.create(playerInventory.player.level(), blockPos);
    }

    public static RaidCommandMenu client(final int containerId, final Inventory inventory, final net.minecraft.network.RegistryFriendlyByteBuf data) {
        return new RaidCommandMenu(containerId, inventory, data.readBlockPos());
    }

    @Override
    public boolean clickMenuButton(final Player player, final int id) {
        if (id == START_RAID_BUTTON_ID) {
            if (player.level() instanceof ServerLevel level) {
                RaidWaveManager.startTestWaveFromPosition(player, level, this.blockPos);
            }
            return true;
        }

        return false;
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(final Player player) {
        return stillValid(this.access, player, ModBlocks.RAID_COMMAND_BLOCK.get());
    }
}
