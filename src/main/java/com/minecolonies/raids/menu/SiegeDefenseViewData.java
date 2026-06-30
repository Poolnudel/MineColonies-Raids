package com.minecolonies.raids.menu;

import com.minecolonies.raids.raid.ColonyRaidSavedData;
import com.minecolonies.raids.raid.ColonyRaidSnapshot;
import com.minecolonies.raids.raid.WaveCalculation;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;

public record SiegeDefenseViewData(
        int colonyId,
        int highestUnlockedWave,
        int highestCompletedWave,
        int lastStartedWave,
        int lastCompletedWave,
        int totalCompletedWaves,
        int defensePoints,
        boolean activeWave,
        int activeWaveNumber,
        int activeRemainingEnemies,
        WaveCalculation repeatCalculation,
        WaveCalculation nextCalculation) {
    public static final SiegeDefenseViewData EMPTY = new SiegeDefenseViewData(0, 1, 0, 0, 0, 0, 0, false, 0, 0, null, null);

    public boolean canRepeat() {
        return !this.activeWave && this.repeatCalculation != null;
    }

    public boolean canStartNext() {
        return !this.activeWave && this.nextCalculation != null;
    }

    public boolean allWavesCompleted() {
        return this.highestCompletedWave >= 4;
    }

    public static SiegeDefenseViewData from(
            final int colonyId,
            final ColonyRaidSavedData.Progress progress,
            final boolean activeWave,
            final int activeWaveNumber,
            final int activeRemainingEnemies,
            final WaveCalculation repeatCalculation,
            final WaveCalculation nextCalculation) {
        return new SiegeDefenseViewData(
                colonyId,
                progress.highestUnlockedWave(),
                progress.highestCompletedWave(),
                progress.lastStartedWave(),
                progress.lastCompletedWave(),
                progress.totalCompletedWaves(),
                progress.defensePoints(),
                activeWave,
                activeWaveNumber,
                activeRemainingEnemies,
                repeatCalculation,
                nextCalculation);
    }

    public static SiegeDefenseViewData read(final RegistryFriendlyByteBuf buffer) {
        return new SiegeDefenseViewData(
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                readNullableCalculation(buffer),
                readNullableCalculation(buffer));
    }

    public void write(final RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(this.colonyId);
        buffer.writeVarInt(this.highestUnlockedWave);
        buffer.writeVarInt(this.highestCompletedWave);
        buffer.writeVarInt(this.lastStartedWave);
        buffer.writeVarInt(this.lastCompletedWave);
        buffer.writeVarInt(this.totalCompletedWaves);
        buffer.writeVarInt(this.defensePoints);
        buffer.writeBoolean(this.activeWave);
        buffer.writeVarInt(this.activeWaveNumber);
        buffer.writeVarInt(this.activeRemainingEnemies);
        writeNullableCalculation(buffer, this.repeatCalculation);
        writeNullableCalculation(buffer, this.nextCalculation);
    }

    private static WaveCalculation readNullableCalculation(final RegistryFriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) {
            return null;
        }

        final int selectedWave = buffer.readVarInt();
        final int townHallContribution = buffer.readVarInt();
        final int builderContribution = buffer.readVarInt();
        final int residenceContribution = buffer.readVarInt();
        final int colonyScore = buffer.readVarInt();
        final double waveMultiplier = buffer.readDouble();
        final double rawCalculatedValue = buffer.readDouble();
        final int enemyCount = buffer.readVarInt();
        final ColonyRaidSnapshot snapshot = readSnapshot(buffer);
        return new WaveCalculation(selectedWave, townHallContribution, builderContribution, residenceContribution, colonyScore, waveMultiplier, rawCalculatedValue, enemyCount, snapshot);
    }

    private static void writeNullableCalculation(final RegistryFriendlyByteBuf buffer, final WaveCalculation calculation) {
        buffer.writeBoolean(calculation != null);
        if (calculation == null) {
            return;
        }

        buffer.writeVarInt(calculation.selectedWave());
        buffer.writeVarInt(calculation.townHallContribution());
        buffer.writeVarInt(calculation.builderContribution());
        buffer.writeVarInt(calculation.residenceContribution());
        buffer.writeVarInt(calculation.colonyScore());
        buffer.writeDouble(calculation.waveMultiplier());
        buffer.writeDouble(calculation.rawCalculatedValue());
        buffer.writeVarInt(calculation.enemyCount());
        writeSnapshot(buffer, calculation.snapshot());
    }

    private static ColonyRaidSnapshot readSnapshot(final RegistryFriendlyByteBuf buffer) {
        final int colonyId = buffer.readVarInt();
        final int townHallLevel = buffer.readVarInt();
        final List<Integer> builderLevels = readIntList(buffer);
        final int builderSum = buffer.readVarInt();
        final List<Integer> residenceLevels = readIntList(buffer);
        final int residenceSum = buffer.readVarInt();
        final int colonyScore = buffer.readVarInt();
        final List<String> warnings = new ArrayList<>();
        final int warningCount = buffer.readVarInt();
        for (int i = 0; i < warningCount; i++) {
            warnings.add(buffer.readUtf());
        }
        return new ColonyRaidSnapshot(colonyId, townHallLevel, builderLevels, builderSum, residenceLevels, residenceSum, colonyScore, warnings);
    }

    private static void writeSnapshot(final RegistryFriendlyByteBuf buffer, final ColonyRaidSnapshot snapshot) {
        buffer.writeVarInt(snapshot.colonyId());
        buffer.writeVarInt(snapshot.townHallLevel());
        writeIntList(buffer, snapshot.builderHutLevels());
        buffer.writeVarInt(snapshot.sumOfBuilderHutLevels());
        writeIntList(buffer, snapshot.residenceLevels());
        buffer.writeVarInt(snapshot.sumOfResidenceLevels());
        buffer.writeVarInt(snapshot.colonyScore());
        buffer.writeVarInt(snapshot.warnings().size());
        for (final String warning : snapshot.warnings()) {
            buffer.writeUtf(warning);
        }
    }

    private static List<Integer> readIntList(final RegistryFriendlyByteBuf buffer) {
        final int size = buffer.readVarInt();
        final List<Integer> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(buffer.readVarInt());
        }
        return List.copyOf(result);
    }

    private static void writeIntList(final RegistryFriendlyByteBuf buffer, final List<Integer> values) {
        buffer.writeVarInt(values.size());
        for (final int value : values) {
            buffer.writeVarInt(value);
        }
    }
}
