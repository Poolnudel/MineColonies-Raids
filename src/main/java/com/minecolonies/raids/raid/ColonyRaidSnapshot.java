package com.minecolonies.raids.raid;

import java.util.List;

public record ColonyRaidSnapshot(
        int colonyId,
        int townHallLevel,
        List<Integer> builderHutLevels,
        int sumOfBuilderHutLevels,
        List<Integer> residenceLevels,
        int sumOfResidenceLevels,
        int colonyScore,
        List<String> warnings) {
    public int builderHutCount() {
        return this.builderHutLevels.size();
    }

    public int residenceCount() {
        return this.residenceLevels.size();
    }

    public boolean hasWarnings() {
        return !this.warnings.isEmpty();
    }
}
