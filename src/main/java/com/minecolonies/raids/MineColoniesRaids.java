package com.minecolonies.raids;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(MineColoniesRaids.MOD_ID)
public final class MineColoniesRaids {
    public static final String MOD_ID = "minecolonies_raids";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MineColoniesRaids(final IEventBus modEventBus, final ModContainer modContainer) {
        LOGGER.info("Initializing MineColonies Raids");
    }
}
