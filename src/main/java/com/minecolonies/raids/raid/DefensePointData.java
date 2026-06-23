package com.minecolonies.raids.raid;

import com.minecolonies.raids.MineColoniesRaids;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class DefensePointData extends SavedData {
    private static final String DATA_NAME = MineColoniesRaids.MOD_ID + "_defense_points";
    private static final String POINTS_KEY = "DefensePoints";

    private int defensePoints;

    public static DefensePointData get(final ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(DefensePointData::new, DefensePointData::load, null),
                DATA_NAME);
    }

    private static DefensePointData load(final CompoundTag tag, final HolderLookup.Provider registries) {
        final DefensePointData data = new DefensePointData();
        data.defensePoints = tag.getInt(POINTS_KEY);
        return data;
    }

    public int addDefensePoints(final int amount) {
        this.defensePoints += amount;
        this.setDirty();
        return this.defensePoints;
    }

    @Override
    public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider registries) {
        tag.putInt(POINTS_KEY, this.defensePoints);
        return tag;
    }
}
