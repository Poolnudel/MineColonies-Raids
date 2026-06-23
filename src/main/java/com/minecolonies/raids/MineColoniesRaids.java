package com.minecolonies.raids;

import com.mojang.logging.LogUtils;
import com.minecolonies.raids.integration.minecolonies.TownHallInteractionHandler;
import com.minecolonies.raids.raid.RaidWaveManager;
import com.minecolonies.raids.registry.ModBlocks;
import com.minecolonies.raids.registry.ModMenus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(MineColoniesRaids.MOD_ID)
public final class MineColoniesRaids {
    public static final String MOD_ID = "minecolonies_raids";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MineColoniesRaids(final IEventBus modEventBus, final ModContainer modContainer) {
        ModBlocks.register(modEventBus);
        ModMenus.register(modEventBus);
        modEventBus.addListener(ModBlocks::addCreativeTabItems);
        NeoForge.EVENT_BUS.addListener(TownHallInteractionHandler::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(RaidWaveManager::onServerTick);

        LOGGER.info("Initializing MineColonies Raids");
    }
}
