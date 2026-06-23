package com.minecolonies.raids.integration.minecolonies;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.workerbuildings.ITownHall;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.raids.MineColoniesRaids;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Narrow adapter for MineColonies API calls used by the addon.
 */
public final class MineColoniesIntegration {
    private MineColoniesIntegration() {
    }

    public static Optional<TownHallContext> getTownHallContext(final ServerLevel level, final BlockPos pos) {
        try {
            final IBuilding building = IMinecoloniesAPI.getInstance().getColonyManager().getBuilding(level, pos);
            if (building instanceof ITownHall townHall && building.getColony() != null) {
                return Optional.of(fromTownHall(level.dimension(), townHall));
            }
        } catch (RuntimeException exception) {
            MineColoniesRaids.LOGGER.warn("Could not resolve MineColonies building at {}", pos, exception);
        }

        try {
            final IColony colony = IMinecoloniesAPI.getInstance().getColonyManager().getColonyByPosFromWorld(level, pos);
            if (colony == null || colony.getServerBuildingManager() == null || colony.getServerBuildingManager().getTownHall() == null) {
                return Optional.empty();
            }

            final ITownHall townHall = colony.getServerBuildingManager().getTownHall();
            if (townHall.getPosition().equals(pos)) {
                return Optional.of(fromTownHall(level.dimension(), townHall));
            }
        } catch (RuntimeException exception) {
            MineColoniesRaids.LOGGER.warn("Could not resolve MineColonies Town Hall at {}", pos, exception);
        }

        return Optional.empty();
    }

    public static Optional<IColony> getColony(final ServerLevel level, final int colonyId) {
        try {
            return Optional.ofNullable(IMinecoloniesAPI.getInstance().getColonyManager().getColonyByWorld(colonyId, level));
        } catch (RuntimeException exception) {
            MineColoniesRaids.LOGGER.warn("Could not resolve MineColonies colony {} in {}", colonyId, level.dimension().location(), exception);
            return Optional.empty();
        }
    }

    public static boolean canManageColony(final Player player, final IColony colony) {
        try {
            return colony.getPermissions().hasPermission(player, Action.MANAGE_HUTS);
        } catch (RuntimeException exception) {
            MineColoniesRaids.LOGGER.warn("Could not evaluate MineColonies permissions for player {} in colony {}", player.getGameProfile().getName(), colony.getID(), exception);
            return false;
        }
    }

    private static TownHallContext fromTownHall(final ResourceKey<Level> dimension, final ITownHall townHall) {
        final IColony colony = townHall.getColony();
        return new TownHallContext(dimension, colony.getID(), townHall.getPosition(), colony.getCenter());
    }

    public record TownHallContext(ResourceKey<Level> dimension, int colonyId, BlockPos townHallPos, BlockPos colonyCenter) {
    }
}
