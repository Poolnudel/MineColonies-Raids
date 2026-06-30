# Architecture

MineColonies Raids uses a small core with explicit boundaries so that MineColonies internals do not spread through the addon.

## Packages

- `com.minecolonies.raids`: mod bootstrap and shared constants only.
- `com.minecolonies.raids.raid`: raid rules, state, scheduling, and outcomes. This package should remain independent of UI code.
- `com.minecolonies.raids.integration.minecolonies`: adapters that query colonies, buildings, citizens, and military capabilities.
- `com.minecolonies.raids.registry`: items, blocks, menus, data components, and other NeoForge registrations owned by this mod.

Future client-only code belongs under `client`; networking belongs under `network`; persistent raid state belongs under `raid.persistence`.

## Design rules

1. Prefer MineColonies API interfaces and registries.
2. Wrap unavoidable implementation-class access in the integration package.
3. Keep world mutation server-side and synchronize only presentation state.
4. Identify colonies and buildings by stable IDs/positions, not retained entity references.
5. Treat colony/building availability as transient because chunks unload and buildings can be removed or upgraded.

## MVP interaction flow

The production MVP entry point is the MineColonies Town Hall.

UI entry points must not contain raid logic. They should resolve or receive minimal context and call services in `com.minecolonies.raids.raid`.

Current flow:

1. `TownHallInteractionHandler` listens for Shift-right-click on blocks.
2. `MineColoniesIntegration` resolves the interacted block to an `ITownHall`, colony ID, dimension, and Town Hall position.
3. The server validates `Action.MANAGE_HUTS`.
4. The server opens the addon-owned `SiegeDefenseMenu`.
5. The menu button calls `RaidWaveManager` with dimension, colony ID, and Town Hall position.
6. `RaidWaveManager` revalidates the colony, Town Hall, permissions, and active-wave state before mutating the world.

Raid ownership is colony-based. Active wave state is keyed by dimension plus colony ID, with the Town Hall position used only as a secondary validation lookup.

## Dynamic wave components

The current MVP separates dynamic wave sizing into small components:

- `ColonyRaidAnalyzer`
  - package: `com.minecolonies.raids.integration.minecolonies`
  - reads MineColonies colony/building data;
  - resolves Town Hall, Builder's Hut, and Residence levels through MineColonies building registries;
  - produces an immutable `ColonyRaidSnapshot`;
  - logs warnings and continues when individual building data cannot be read.
- `ColonyRaidSnapshot`
  - package: `com.minecolonies.raids.raid`
  - immutable server-side data object used by calculation and UI presentation;
  - contains colony ID, Town Hall level, Builder's Hut levels, Residence levels, sums, score, and warnings.
- `WaveCalculator`
  - package: `com.minecolonies.raids.raid`
  - contains the temporary prototype formula and wave multipliers;
  - produces `WaveCalculation`;
  - does not spawn entities, query MineColonies, or write saved data.
- `WaveCalculation`
  - package: `com.minecolonies.raids.raid`
  - immutable result object consumed by the spawner and serialized to the UI.
- `RaidSpawnPlanner`
  - package: `com.minecolonies.raids.raid.spawn`
  - creates a validated surface spawn plan outside the colony;
  - uses heightmaps instead of vertical cave-prone scanning;
  - rejects unloaded chunks, underground positions, unsafe terrain, fluid, dangerous blocks, collisions, inside-colony positions, and positions too far from the relevant player;
  - returns `RaidSpawnPlan` with an anchor, concrete spawn positions, and `RaidSpawnDiagnostics`.
- `ColonyRaidSavedData`
  - package: `com.minecolonies.raids.raid`
  - stores per-colony progression and Defense Points in world saved data;
  - keyed by dimension plus colony ID;
  - independent of UI state.
- `SiegeDefenseViewData`
  - package: `com.minecolonies.raids.menu`
  - serializes server-confirmed preview data to the client screen;
  - keeps MineColonies building access out of the UI.

## Dynamic wave start flow

1. Player Shift-right-clicks a MineColonies Town Hall.
2. `TownHallInteractionHandler` resolves and permission-checks the colony.
3. `RaidWaveManager.createViewData` refreshes the current colony snapshot.
4. `WaveCalculator` calculates preview values for repeat/next wave actions.
5. `SiegeDefenseViewData` is written into the menu open payload.
6. The client renders the formula preview from that server-supplied payload.
7. When a start button is pressed, `RaidWaveManager` revalidates the colony, permissions, active state, and wave progression.
8. `ColonyRaidAnalyzer` takes a fresh authoritative snapshot.
9. `WaveCalculator` produces the authoritative `WaveCalculation`.
10. `RaidSpawnPlanner` creates a surface-only spawn plan near the colony.
11. The spawner consumes only `WaveCalculation.enemyCount` and the validated positions from `RaidSpawnPlan`.
12. Completion updates `ColonyRaidSavedData` and grants persistent colony Defense Points.

The UI preview is informative, not authoritative. The calculation at wave start is authoritative so building upgrades made after opening the screen are respected.
