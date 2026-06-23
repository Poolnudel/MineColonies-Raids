package com.minecolonies.raids.registry;

import com.minecolonies.raids.MineColoniesRaids;
import com.minecolonies.raids.menu.RaidCommandMenu;
import com.minecolonies.raids.menu.SiegeDefenseMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(BuiltInRegistries.MENU, MineColoniesRaids.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<RaidCommandMenu>> RAID_COMMAND = MENUS.register(
            "raid_command",
            () -> IMenuTypeExtension.create(RaidCommandMenu::client));

    public static final DeferredHolder<MenuType<?>, MenuType<SiegeDefenseMenu>> SIEGE_DEFENSE = MENUS.register(
            "siege_defense",
            () -> IMenuTypeExtension.create(SiegeDefenseMenu::client));

    private ModMenus() {
    }

    public static void register(final IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
