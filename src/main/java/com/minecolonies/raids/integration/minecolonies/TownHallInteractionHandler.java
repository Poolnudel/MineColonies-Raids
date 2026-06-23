package com.minecolonies.raids.integration.minecolonies;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.raids.menu.SiegeDefenseMenuProvider;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class TownHallInteractionHandler {
    private TownHallInteractionHandler() {
    }

    public static void onRightClickBlock(final PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getEntity().isShiftKeyDown()) {
            return;
        }

        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }

        final Optional<MineColoniesIntegration.TownHallContext> context = MineColoniesIntegration.getTownHallContext(level, event.getPos());
        if (context.isEmpty()) {
            return;
        }

        final Optional<IColony> colony = MineColoniesIntegration.getColony(level, context.get().colonyId());
        if (colony.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.minecolonies_raids.siege_defense.no_colony"), false);
            return;
        }

        if (!MineColoniesIntegration.canManageColony(player, colony.get())) {
            player.displayClientMessage(Component.translatable("message.minecolonies_raids.siege_defense.no_permission"), false);
            return;
        }

        player.openMenu(new SiegeDefenseMenuProvider(context.get()), buffer -> {
            buffer.writeResourceLocation(context.get().dimension().location());
            buffer.writeVarInt(context.get().colonyId());
            buffer.writeBlockPos(context.get().townHallPos());
        });

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
