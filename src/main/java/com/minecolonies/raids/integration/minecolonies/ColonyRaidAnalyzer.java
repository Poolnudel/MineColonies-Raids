package com.minecolonies.raids.integration.minecolonies;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.buildings.workerbuildings.ITownHall;
import com.minecolonies.raids.MineColoniesRaids;
import com.minecolonies.raids.raid.ColonyRaidSnapshot;
import java.util.ArrayList;
import java.util.List;

public final class ColonyRaidAnalyzer {
    private ColonyRaidAnalyzer() {
    }

    public static ColonyRaidSnapshot createSnapshot(final IColony colony) {
        int townHallLevel = 0;
        final List<Integer> builderLevels = new ArrayList<>();
        final List<Integer> residenceLevels = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();

        try {
            final ITownHall townHall = colony.getServerBuildingManager().getTownHall();
            if (townHall == null) {
                warnings.add("Town Hall building was not available.");
                MineColoniesRaids.LOGGER.warn("Colony {} has no resolvable Town Hall while creating raid snapshot", colony.getID());
            } else {
                townHallLevel = Math.max(0, townHall.getBuildingLevel());
            }
        } catch (RuntimeException exception) {
            warnings.add("Town Hall level could not be read.");
            MineColoniesRaids.LOGGER.warn("Could not read Town Hall level for colony {}", colony.getID(), exception);
        }

        try {
            for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values()) {
                if (building == null || building.getBuildingType() == null) {
                    warnings.add("A building entry could not be resolved.");
                    MineColoniesRaids.LOGGER.warn("Colony {} contains an unresolved building entry", colony.getID());
                    continue;
                }

                final int level = Math.max(0, building.getBuildingLevel());
                if (building.getBuildingType() == ModBuildings.builder.get()) {
                    builderLevels.add(level);
                } else if (building.getBuildingType() == ModBuildings.home.get()) {
                    residenceLevels.add(level);
                }
            }
        } catch (RuntimeException exception) {
            warnings.add("Some colony buildings could not be read.");
            MineColoniesRaids.LOGGER.warn("Could not fully read building levels for colony {}", colony.getID(), exception);
        }

        final int builderSum = builderLevels.stream().mapToInt(Integer::intValue).sum();
        final int residenceSum = residenceLevels.stream().mapToInt(Integer::intValue).sum();
        final int colonyScore = townHallLevel * 2 + builderSum + residenceSum;

        return new ColonyRaidSnapshot(
                colony.getID(),
                townHallLevel,
                List.copyOf(builderLevels),
                builderSum,
                List.copyOf(residenceLevels),
                residenceSum,
                colonyScore,
                List.copyOf(warnings));
    }
}
