# MineColonies Raids MVP

This document describes the current playable MVP iteration for MineColonies Raids.

## Current behavior

- The player-facing entry point is the existing MineColonies Town Hall.
- Normal right-click on the Town Hall remains unchanged and opens the regular MineColonies UI.
- Shift-right-click on the Town Hall opens the addon-owned `Siege Defense` window.
- The window contains one action button: `Start Wave`.
- The window is linked to the colony that owns the interacted Town Hall.
- Starting a wave is server-authoritative and revalidates:
  - the referenced dimension,
  - the colony ID,
  - the Town Hall position,
  - player permissions,
  - active wave state.
- One MVP wave may be active per colony.
- The active wave is fixed:
  - 5 vanilla Pillagers.
  - Server-side spawning only.
  - Spawned enemies receive a MineColonies Raids MVP entity tag and are tracked by UUID.
- Completion happens when all tracked enemies are dead, despawned, or otherwise removed.
- Completion rewards 100 Defense Points.
- Defense Points are stored in simple world-level saved data.
- A server-side bossbar shows wave progress to nearby players:
  - `Raid Wave: 5 remaining`
  - The bar shrinks as tracked enemies are removed.
  - The bar is removed when the wave completes or fails.

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

## Permissions

The MVP uses:

```java
colony.getPermissions().hasPermission(player, Action.MANAGE_HUTS)
```

This matches MineColonies' existing permission category for GUI button interactions and hut management.

## How to test the Town Hall flow

1. Start the client:

   ```powershell
   .\gradlew.bat runClient --no-configuration-cache
   ```

2. Create or load a world.
3. Create or use a MineColonies colony with a Town Hall.
4. Right-click the Town Hall normally.
   - Expected: standard MineColonies Town Hall UI opens.
5. Shift-right-click the Town Hall.
   - Expected: `Siege Defense` opens.
6. Click `Start Wave`.
7. Verify that:
   - `Raid wave started.` appears.
   - A bossbar appears.
   - 5 Pillagers spawn outside the colony.
8. Try clicking `Start Wave` again while the wave is active.
   - Expected: `Wave in progress.`
9. Kill or remove the Pillagers.
10. Verify that:
    - The bossbar disappears.
    - `Raid wave completed.` appears.
    - `Reward: 100 Defense Points.` appears.

## Spawn position selection

The MVP uses a defensive approximation of MineColonies-style outside-colony spawning:

1. Resolve the colony from the Town Hall context.
2. Read the colony center.
3. Read known MineColonies building positions from the colony building manager.
4. Estimate a colony radius from the farthest known building position, with a minimum radius of 48 blocks.
5. Pick a random horizontal direction.
6. Try candidate spawn positions at increasing distances outside the estimated colony radius.
7. Reject candidates that:
   - are still inside the same colony,
   - are closer than 32 blocks to a known colony building,
   - do not have solid ground,
   - do not have two air blocks of headroom.

If no valid spawn position is found, the wave does not start and the player receives a useful error message.

## Progress bar behavior

- Each active wave owns one `ServerBossEvent`.
- Nearby players within 192 blocks of the colony center are added to the bossbar.
- Players outside that radius are removed from the bossbar.
- The title is updated every server tick:

  ```text
  Raid Wave: <remaining> remaining
  ```

- Progress is calculated from tracked alive enemies divided by the original spawned enemy count.

## Deprecated development entry points

The old `Raid Command Block` and experimental Structurize pack are retained temporarily as development-only migration artifacts.

They are no longer required for the player-facing MVP and should be removed once the Town Hall flow has been manually verified in-game.

Cleanup candidates:

- `RaidCommandBlock`
- `RaidCommandMenu`
- `RaidCommandMenuProvider`
- Raid command block models/blockstates/lang entries
- `blueprints/minecolonies_raids/mvp`
- creative-tab registration for the Raid Command Block

## Why this is not a real Town Hall tab yet

The MVP intentionally does not modify MineColonies Town Hall GUI resources or inject into MineColonies UI classes.

Avoided for this iteration:

- overwriting MineColonies XML files;
- replacing existing Town Hall resources;
- Mixins into MineColonies GUI classes;
- patching `AbstractWindowTownHall`;
- duplicating the full Town Hall UI.

The addon owns its own screen and opens it from a server-validated Town Hall interaction.

## Intentional non-goals for this MVP

- No dynamic wave scaling.
- No skill tree.
- No custom enemies.
- No wall, gate, trench, tower, or trap buildings.
- No reward UI.
- No balancing system.
- No config screen.
- No multiple wave types.
- No boss enemies.
- No worker job.
- No research requirement.
- No building levels or upgrades.

## Known limitations

- Only one active MVP wave is allowed per colony.
- Defense Points are global world saved data, not per-colony data.
- Active wave state is in memory and is not restored after server restart.
- Completion messages are sent to the starting player only if that player is online.
- The spawn algorithm uses a practical building-position radius, not MineColonies' full internal raid manager.
- The old block/blueprint path still exists temporarily until cleanup.
