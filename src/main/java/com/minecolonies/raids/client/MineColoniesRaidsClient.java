package com.minecolonies.raids.client;

import com.minecolonies.raids.MineColoniesRaids;
import com.minecolonies.raids.client.screen.RaidCommandScreen;
import com.minecolonies.raids.client.screen.SiegeDefenseScreen;
import com.minecolonies.raids.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = MineColoniesRaids.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MineColoniesRaidsClient {
    private MineColoniesRaidsClient() {
    }

    @SubscribeEvent
    public static void registerScreens(final RegisterMenuScreensEvent event) {
        event.register(ModMenus.RAID_COMMAND.get(), RaidCommandScreen::new);
        event.register(ModMenus.SIEGE_DEFENSE.get(), SiegeDefenseScreen::new);
    }
}
