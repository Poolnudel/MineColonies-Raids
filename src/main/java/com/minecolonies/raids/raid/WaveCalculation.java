package com.minecolonies.raids.raid;

public record WaveCalculation(
        int selectedWave,
        int townHallContribution,
        int builderContribution,
        int residenceContribution,
        int colonyScore,
        double waveMultiplier,
        double rawCalculatedValue,
        int enemyCount,
        ColonyRaidSnapshot snapshot) {
}
