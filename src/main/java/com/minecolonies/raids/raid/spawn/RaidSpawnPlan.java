package com.minecolonies.raids.raid.spawn;

import java.util.List;
import net.minecraft.core.BlockPos;

public record RaidSpawnPlan(BlockPos anchor, List<BlockPos> positions, RaidSpawnDiagnostics diagnostics) {
    public boolean hasSpawns() {
        return !this.positions.isEmpty();
    }
}
