package com.minecolonies.raids.raid;

import com.minecolonies.raids.MineColoniesRaids;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

public class ColonyRaidSavedData extends SavedData {
    private static final String DATA_NAME = MineColoniesRaids.MOD_ID + "_colony_raid_progress";

    private final Map<String, Progress> progressByColony = new HashMap<>();

    public static ColonyRaidSavedData get(final ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ColonyRaidSavedData::new, ColonyRaidSavedData::load, null),
                DATA_NAME);
    }

    private static ColonyRaidSavedData load(final CompoundTag tag, final HolderLookup.Provider registries) {
        final ColonyRaidSavedData data = new ColonyRaidSavedData();
        final ListTag entries = tag.getList("Colonies", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            final CompoundTag entry = entries.getCompound(i);
            data.progressByColony.put(entry.getString("Key"), Progress.load(entry));
        }
        return data;
    }

    public Progress getProgress(final ResourceKey<Level> dimension, final int colonyId) {
        return this.progressByColony.computeIfAbsent(key(dimension, colonyId), ignored -> new Progress());
    }

    public void markWaveStarted(final ResourceKey<Level> dimension, final int colonyId, final int wave) {
        this.getProgress(dimension, colonyId).lastStartedWave = wave;
        this.setDirty();
    }

    public int markWaveCompleted(final ResourceKey<Level> dimension, final int colonyId, final int wave, final int defensePoints) {
        final Progress progress = this.getProgress(dimension, colonyId);
        progress.highestCompletedWave = Math.max(progress.highestCompletedWave, wave);
        if (wave < WaveCalculator.MAX_WAVE) {
            progress.highestUnlockedWave = Math.max(progress.highestUnlockedWave, wave + 1);
        }
        progress.lastCompletedWave = wave;
        progress.totalCompletedWaves++;
        progress.defensePoints += defensePoints;
        this.setDirty();
        return progress.defensePoints;
    }

    @Override
    public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider registries) {
        final ListTag entries = new ListTag();
        this.progressByColony.forEach((key, progress) -> {
            final CompoundTag entry = progress.save();
            entry.putString("Key", key);
            entries.add(entry);
        });
        tag.put("Colonies", entries);
        return tag;
    }

    private static String key(final ResourceKey<Level> dimension, final int colonyId) {
        return dimension.location() + "#" + colonyId;
    }

    public static final class Progress {
        private int highestUnlockedWave = 1;
        private int highestCompletedWave = 0;
        private int lastStartedWave = 0;
        private int lastCompletedWave = 0;
        private int totalCompletedWaves = 0;
        private int defensePoints = 0;

        private static Progress load(final CompoundTag tag) {
            final Progress progress = new Progress();
            progress.highestUnlockedWave = Math.max(1, tag.getInt("HighestUnlockedWave"));
            progress.highestCompletedWave = tag.getInt("HighestCompletedWave");
            progress.lastStartedWave = tag.getInt("LastStartedWave");
            progress.lastCompletedWave = tag.getInt("LastCompletedWave");
            progress.totalCompletedWaves = tag.getInt("TotalCompletedWaves");
            progress.defensePoints = tag.getInt("DefensePoints");
            return progress;
        }

        private CompoundTag save() {
            final CompoundTag tag = new CompoundTag();
            tag.putInt("HighestUnlockedWave", this.highestUnlockedWave);
            tag.putInt("HighestCompletedWave", this.highestCompletedWave);
            tag.putInt("LastStartedWave", this.lastStartedWave);
            tag.putInt("LastCompletedWave", this.lastCompletedWave);
            tag.putInt("TotalCompletedWaves", this.totalCompletedWaves);
            tag.putInt("DefensePoints", this.defensePoints);
            return tag;
        }

        public int highestUnlockedWave() {
            return this.highestUnlockedWave;
        }

        public int highestCompletedWave() {
            return this.highestCompletedWave;
        }

        public int lastStartedWave() {
            return this.lastStartedWave;
        }

        public int lastCompletedWave() {
            return this.lastCompletedWave;
        }

        public int totalCompletedWaves() {
            return this.totalCompletedWaves;
        }

        public int defensePoints() {
            return this.defensePoints;
        }
    }
}
