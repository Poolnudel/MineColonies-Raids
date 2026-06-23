package com.minecolonies.raids.block;

import com.minecolonies.raids.menu.RaidCommandMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * MVP command point used to open the raid control screen.
 */
public class RaidCommandBlock extends Block {
    public RaidCommandBlock(final Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(
            final BlockState state,
            final Level level,
            final BlockPos pos,
            final Player player,
            final BlockHitResult hitResult) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }

        serverPlayer.openMenu(new RaidCommandMenuProvider(pos), buffer -> buffer.writeBlockPos(pos));
        return InteractionResult.CONSUME;
    }
}
