package com.minecolonies.raids.menu;

import com.minecolonies.raids.raid.RaidWaveManager;
import com.minecolonies.raids.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class SiegeDefenseMenu extends AbstractContainerMenu {
    public static final int REPEAT_LAST_WAVE_BUTTON_ID = 0;
    public static final int START_NEXT_WAVE_BUTTON_ID = 1;

    private final ResourceKey<Level> dimension;
    private final int colonyId;
    private final BlockPos townHallPos;
    private final SiegeDefenseViewData viewData;

    public SiegeDefenseMenu(
            final int containerId,
            final Inventory playerInventory,
            final ResourceKey<Level> dimension,
            final int colonyId,
            final BlockPos townHallPos,
            final SiegeDefenseViewData viewData) {
        super(ModMenus.SIEGE_DEFENSE.get(), containerId);
        this.dimension = dimension;
        this.colonyId = colonyId;
        this.townHallPos = townHallPos;
        this.viewData = viewData;
    }

    public static SiegeDefenseMenu client(final int containerId, final Inventory inventory, final RegistryFriendlyByteBuf data) {
        final ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, data.readResourceLocation());
        final int colonyId = data.readVarInt();
        final BlockPos townHallPos = data.readBlockPos();
        final SiegeDefenseViewData viewData = SiegeDefenseViewData.read(data);
        return new SiegeDefenseMenu(containerId, inventory, dimension, colonyId, townHallPos, viewData);
    }

    public SiegeDefenseViewData viewData() {
        return this.viewData;
    }

    @Override
    public boolean clickMenuButton(final Player player, final int id) {
        if (id == REPEAT_LAST_WAVE_BUTTON_ID) {
            RaidWaveManager.startRepeatLastCompletedWave(player, this.dimension, this.colonyId, this.townHallPos);
            return true;
        }

        if (id == START_NEXT_WAVE_BUTTON_ID) {
            RaidWaveManager.startNextWave(player, this.dimension, this.colonyId, this.townHallPos);
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
        return player.isAlive();
    }
}
