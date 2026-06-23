package com.minecolonies.raids.registry;

import com.minecolonies.raids.MineColoniesRaids;
import com.minecolonies.raids.block.RaidCommandBlock;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MineColoniesRaids.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MineColoniesRaids.MOD_ID);

    public static final DeferredBlock<RaidCommandBlock> RAID_COMMAND_BLOCK = BLOCKS.registerBlock(
            "raid_command_block",
            RaidCommandBlock::new,
            BlockBehaviour.Properties.of()
                    .strength(2.5F, 6.0F)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops());

    public static final DeferredItem<BlockItem> RAID_COMMAND_BLOCK_ITEM = ITEMS.registerSimpleBlockItem(RAID_COMMAND_BLOCK);

    private ModBlocks() {
    }

    public static void register(final IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }

    public static void addCreativeTabItems(final BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(RAID_COMMAND_BLOCK_ITEM.get());
        }
    }
}
