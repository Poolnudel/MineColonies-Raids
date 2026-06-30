# MineColonies Raids MVP

This document describes the current playable MVP iteration for MineColonies Raids.

## Current behavior

- The player-facing entry point is the existing MineColonies Town Hall.
- Normal right-click on the Town Hall remains unchanged and opens the regular MineColonies UI.
- Shift-right-click on the Town Hall opens the addon-owned `Siege Defense` window.
- The window offers two actions:
  - `Repeat Last`
  - `Start Next`
- The window is linked to the colony that owns the interacted Town Hall.
- Starting a wave is server-authoritative and revalidates dimension, colony ID, Town Hall position, permissions, active wave state, and current MineColonies building data.
- One MVP wave may be active per colony.
- Active waves spawn vanilla Pillagers only.
- Spawned enemies receive a MineColonies Raids MVP entity tag and are tracked by UUID.
- Completion happens when all tracked enemies are dead, despawned, or otherwise removed.
- A server-side bossbar shows wave progress to nearby players.
- Defense Points and wave progression are persisted per colony.

## Four-wave prototype progression

Each colony has four prototype waves:

- Wave 1 is unlocked initially.
- Completing Wave 1 unlocks Wave 2.
- Completing Wave 2 unlocks Wave 3.
- Completing Wave 3 unlocks Wave 4.
- Wave 4 does not unlock additional waves.
- Completed earlier waves may be repeated.
- Repeating a completed wave does not change unlock progression.
- Failed or aborted waves do not unlock the next wave.
- Only one wave may be active per colony.

`Start Next` starts the highest unlocked wave that has not yet been completed. After all four waves are complete, `Start Next` is disabled and shown as complete. `Repeat Last` remains available and repeats the most recently completed wave, which is Wave 4 after full completion.

## Prototype formula

The current formula is intentionally simple and exists for technical validation of MineColonies building-data access. It is not final balancing.

```text
colonyScore =
    townHallLevel * 2
    + sumOfBuilderHutLevels
    + sumOfResidenceLevels
```

Wave multipliers:

| Wave | Multiplier |
| ---: | ---------: |
| 1 | 0.75 |
| 2 | 1.00 |
| 3 | 1.25 |
| 4 | 1.50 |

Enemy count:

```text
enemyCount = max(3, round(colonyScore * waveMultiplier))
```

Normal mathematical rounding is used.

## Colony data snapshot

Whenever the Siege Defense UI is opened, the server creates a colony snapshot containing:

- colony ID;
- Town Hall level;
- number of Builder's Huts;
- individual Builder's Hut levels;
- sum of Builder's Hut levels;
- number of Residences;
- individual Residence levels;
- sum of Residence levels;
- calculated colony score;
- warnings for incomplete data.

MineColonies-specific building queries are isolated in `com.minecolonies.raids.integration.minecolonies.ColonyRaidAnalyzer`.

Building matching uses MineColonies building registry entries:

- Town Hall: `ModBuildings.townHall`
- Builder's Hut: `ModBuildings.builder`
- Residence: `ModBuildings.home`

The UI does not query MineColonies buildings directly. It renders server-supplied snapshot and calculation data.

## Formula preview in the UI

The Siege Defense UI shows the server-supplied calculation preview for the next available progression wave, or the repeat wave when no next wave is available.

Visible data includes:

- unlocked/completed wave state;
- Defense Points;
- active wave and remaining enemies when applicable;
- selected preview wave;
- wave multiplier;
- Town Hall level and contribution;
- Builder's Hut count, levels, and contribution;
- Residence count, levels, and contribution;
- total colony score;
- final enemy count formula.

Example for a colony with Town Hall Level 2, one Builder's Hut Level 2, and Residence levels 1, 1, and 2:

```text
Town Hall: Level 2 x 2 = 4
Builder's Huts: 1 building
Levels: 2
Contribution: 2
Residences: 3 buildings
Levels: 1, 1, 2
Contribution: 4
Colony Score: 4 + 2 + 4 = 10
Enemy Count, Wave 2: round(10 x 1.00) = 10
```

## Refresh behavior

- Opening the UI refreshes the colony snapshot.
- Pressing a start button refreshes and recalculates the snapshot again on the server.
- The server-side snapshot at wave start is authoritative.
- Active waves are not recalculated after they start.
- If building levels changed after the UI was opened, the started wave uses the newer server-side calculation. The player receives a start message with the actual wave and calculated enemy count.

## Persistence

`ColonyRaidSavedData` stores progression in overworld saved data keyed by dimension plus colony ID.

Persisted per colony:

- highest unlocked wave;
- highest completed wave;
- last started wave;
- last completed wave;
- total completed waves;
- Defense Points.

This data survives world save and reload. Active in-memory wave entities are still not restored after a full server restart.

## Rewards

Rewards remain intentionally simple:

```text
Defense Points = 100 * completedWaveNumber
```

Defense Points are persistent and colony-based in this MVP iteration. There is no reward UI, trade integration, loot table, or item reward yet.

## Town Hall interaction

The addon listens for NeoForge block interaction events.

- No shift key:
  - the addon does nothing;
  - MineColonies handles the interaction normally.
- Shift-right-click:
  - server resolves the interacted block through MineColonies API;
  - the block must resolve to an `ITownHall` building;
  - the owning colony is resolved from the Town Hall building;
  - the player must have `Action.MANAGE_HUTS`;
  - only then does the addon open `Siege Defense` and cancel the normal Town Hall interaction.

Invalid or unauthorized interactions show a useful message and do not start a wave.

## Spawn position selection

The MVP uses a dedicated `RaidSpawnPlanner` instead of vertical fallback scanning.

The planner:

1. Resolves the colony center from the MineColonies colony, falling back to the Town Hall position.
2. Reads known MineColonies building positions.
3. Estimates a colony radius from the farthest known building position.
4. Builds a spawn ring outside the colony.
5. Tests 16 directions and multiple distances around the colony.
6. Resolves each candidate to the terrain surface with `Heightmap.Types.MOTION_BLOCKING_NO_LEAVES`.
7. Rejects candidates that:
   - are not loaded or entity-ticking,
   - are below the world surface and therefore likely underground or cave positions,
   - are inside the same MineColonies colony,
   - are too close to known colony buildings,
   - are too close to or too far from the colony,
   - are too far from the relevant player,
   - do not have solid safe ground,
   - do not have two free air blocks,
   - contain fluid,
   - contain lava, fire, magma, cactus, campfire, sweet berry bush, powder snow, or similar immediate hazards,
   - fail entity collision validation.
8. Scores valid anchors and selects a surface spawn anchor.
9. Builds a small group layout around that anchor and validates each individual spawn position separately.

The spawner consumes the calculated `enemyCount`; it does not recalculate the formula. If the full requested count cannot be spawned, the wave tracks only successfully spawned enemies and logs requested, spawned, and failed counts. If no enemies spawn successfully, the wave aborts without completion rewards.

## Progress bar behavior

- Each active wave owns one `ServerBossEvent`.
- Nearby players within 192 blocks of the colony center are added to the bossbar.
- Players outside that radius are removed from the bossbar.
- The title is updated every server tick:

  ```text
  Raid Wave <wave>: <remaining> remaining
  ```

- Progress is calculated from tracked alive enemies divided by the successfully spawned enemy count.
- The bar is removed when the wave completes or is discarded because its level is unavailable.

## Server logging

When a wave starts, the server logs one calculation summary, for example:

```text
Colony 7 wave calculation:
TownHall=2 contribution=4
Builders=[2] contribution=2
Residences=[1, 1, 2] contribution=4
Score=10 multiplier=1.00 raw=10.0 enemies=10
```

The calculation is not logged every tick.

## Test scenarios

### Scenario A: Minimal colony

1. Create a colony with Town Hall Level 1.
2. Add one Builder's Hut Level 1.
3. Use no Residence or one Residence Level 1.
4. Open the Siege Defense UI and verify the detected levels.
5. Start and complete Waves 1 through 4.
6. Verify each wave spawns at least three enemies and increases according to the multipliers.

### Scenario B: Multiple Residences

Use:

- Town Hall Level 2;
- Builder's Hut Level 2;
- Residence levels 1, 1, and 2.

Expected colony score:

```text
2 * 2 + 2 + (1 + 1 + 2) = 10
```

Expected enemy counts:

| Wave | Calculation | Enemies |
| ---: | ----------- | ------: |
| 1 | round(10 * 0.75) | 8 |
| 2 | round(10 * 1.00) | 10 |
| 3 | round(10 * 1.25) | 13 |
| 4 | round(10 * 1.50) | 15 |

### Scenario C: Building upgrade refresh

1. Open the Siege Defense UI.
2. Note the detected values.
3. Upgrade a Residence or Builder's Hut.
4. Reopen the UI.
5. Verify the preview reflects the new level.
6. Start the wave and verify the spawned amount matches the updated calculation.

### Scenario D: Persistence

1. Complete at least one wave.
2. Save and close the world.
3. Reload the world.
4. Reopen the Siege Defense UI.
5. Verify unlocked/completed wave progression and Defense Points remain.

### Scenario E: Repeat wave

1. Complete Wave 1.
2. Click `Repeat Last`.
3. Verify colony data is recalculated.
4. Verify Wave 2 remains unlocked.
5. Verify repeating Wave 1 does not unlock additional waves.

## Deprecated development entry points

The old `Raid Command Block` and experimental Structurize pack are retained temporarily as development-only migration artifacts.

They are no longer required for the player-facing MVP and should be removed once the Town Hall flow has been manually verified in-game.

## Intentional non-goals for this MVP

- No custom enemies.
- No dynamic balancing beyond the temporary formula above.
- No skill tree.
- No wall, gate, trench, tower, or trap buildings.
- No reward UI.
- No config screen.
- No multiple wave types.
- No boss enemies.
- No worker job.
- No research requirement.
- No preparation countdown.

## Known limitations

- Only one active MVP wave is allowed per colony.
- Active wave state is in memory and is not restored after server restart.
- Completion messages are sent to the starting player only if that player is online.
- The spawn algorithm uses a practical building-position radius, not MineColonies' full internal raid manager.
- The UI shows a compact formula preview rather than a full MineColonies-style tab.
- The old block/blueprint path still exists temporarily until cleanup.
