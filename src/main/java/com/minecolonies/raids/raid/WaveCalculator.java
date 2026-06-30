package com.minecolonies.raids.raid;

public final class WaveCalculator {
    public static final int MIN_WAVE = 1;
    public static final int MAX_WAVE = 4;

    private WaveCalculator() {
    }

    public static WaveCalculation calculate(final ColonyRaidSnapshot snapshot, final int selectedWave) {
        final int clampedWave = Math.max(MIN_WAVE, Math.min(MAX_WAVE, selectedWave));
        final int townHallContribution = snapshot.townHallLevel() * 2;
        final int builderContribution = snapshot.sumOfBuilderHutLevels();
        final int residenceContribution = snapshot.sumOfResidenceLevels();
        final int colonyScore = townHallContribution + builderContribution + residenceContribution;
        final double multiplier = multiplierFor(clampedWave);
        final double raw = colonyScore * multiplier;
        final int enemyCount = Math.max(3, (int) Math.round(raw));

        return new WaveCalculation(
                clampedWave,
                townHallContribution,
                builderContribution,
                residenceContribution,
                colonyScore,
                multiplier,
                raw,
                enemyCount,
                snapshot);
    }

    public static double multiplierFor(final int wave) {
        return switch (wave) {
            case 1 -> 0.75D;
            case 2 -> 1.00D;
            case 3 -> 1.25D;
            case 4 -> 1.50D;
            default -> throw new IllegalArgumentException("Unsupported prototype wave: " + wave);
        };
    }
}
