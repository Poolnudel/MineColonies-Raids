# MineColonies technical research

This analysis targets MineColonies `1.1.1331-1.21.1-snapshot` on NeoForge 1.21.1. Class names and signatures were verified against that published artifact on 2026-06-18. MineColonies does not publish a separate API artifact (`projectHasApi=false`), so the full mod is the compile dependency even when only `com.minecolonies.api` types are used.

## Executive summary

MineColonies already has a complete colony event and raid subsystem. An addon should compose with it through `IMinecoloniesAPI`, `IColonyManager`, colony managers, public building interfaces, and the MineColonies event bus. Replacing colony tick logic, mutating internal maps, or injecting directly into citizen AI would be high-risk.

For MineColonies Raids, the safest architecture is:

1. Resolve the target `IColony` on the logical server.
2. Read military capacity through public building data and `IGuardBuilding`.
3. Schedule or observe raids through `IRaiderManager` and `IEventManager`.
4. Subscribe to MineColonies mod events for lifecycle invalidation.
5. Keep all implementation-class checks in `integration.minecolonies`.

## API entry points

### `IMinecoloniesAPI`

`com.minecolonies.api.IMinecoloniesAPI.getInstance()` is the primary service locator. Relevant services include:

- `getColonyManager()` for colony and building lookup.
- `getBuildingRegistry()` and `getBuildingDataManager()` for registered building types.
- `getGuardTypeRegistry()` and `getGuardTypeDataManager()` for military role metadata.
- `getColonyEventRegistry()` for colony-event types.
- `getEventBus()` for MineColonies-specific mod events.

Prefer this entry point over importing a concrete singleton from `com.minecolonies.core`.

### `IColonyManager`

`IColonyManager` supports server-side discovery without scanning chunks:

- `getColonyByWorld(id, level)` and `getColonyByDimension(...)` resolve stable colony IDs.
- `getColonyByPosFromWorld(level, pos)` resolves ownership at a position.
- `getBuilding(level, pos)` resolves a building by hut position.
- `getColonies(level)`, `getAllColonies()`, and `getClosestIColony(...)` support broader selection.
- `isCoordinateInAnyColony(level, pos)` is a low-level territory check.

Use colony ID plus dimension as persistent identity. A position-only reference is insufficient when dimensions are involved.

## Colony model

`com.minecolonies.api.colony.IColony` is the central server model. Useful raid-facing state includes:

- Identity and geography: `getID()`, `getDimension()`, `getCenter()`, `isCoordInColony(...)`.
- Lifecycle: `isActive()`, `getState()`, `getWorld()`.
- Population: `getCitizenManager()` and `getCitizen(id)`.
- Buildings: `getServerBuildingManager()`.
- Existing raids: `getRaiderManager()`, `getEventManager()`, and `isColonyUnderAttack()`.
- Permissions and targeting: `getPermissions()`, `isValidAttackingPlayer(...)`, and `isValidAttackingGuard(...)`.
- Progression inputs: `getResearchManager()`, `getStatisticsManager()`, `getOverallHappiness()`, and `getDay()`.

Do not retain an `IColony` across world unloads. Persist its ID and dimension, then resolve it again through `IColonyManager`.

## Building system

### Storage and lookup

`IColony.getServerBuildingManager()` returns `IRegisteredStructureManager`. Its common parent exposes:

- `getBuildings()`: map from hut `BlockPos` to `IBuilding`.
- `getBuilding(pos)`: direct lookup.
- `getFirstBuildingMatching(predicate)` and `getRandomBuilding(predicate)`.
- `hasBuilding(resourceLocation, minimumLevel, includeBuildingUnderConstruction)`.

This manager is preferable to world block scans. Building entries can disappear during removal or world transitions, so consumers must tolerate a missing result.

### `IBuilding` and `ICommonBuilding`

The public abstractions expose the data needed for raid strength calculations:

- `getBuildingType()` returns the registered `BuildingEntry`.
- `getBuildingLevel()` and `getBuildingLevelEquivalent()` expose progression.
- `getPosition()` and `getColony()` provide context.
- `isBuilt()`, `isPendingConstruction()`, and `isInBuilding(pos)` describe construction and bounds.
- `getAllAssignedCitizen()` provides staffing.
- `getMaxEquipmentLevel()` gives a public equipment-capability signal.
- Module access is available through the building module container inherited by `IBuilding`.

Identify building kinds by registry entry/resource key where possible. `instanceof` checks against `com.minecolonies.core` are useful only when no public semantic interface exists.

## Guard Towers

The concrete class is `com.minecolonies.core.colony.buildings.workerbuildings.BuildingGuardTower`. It derives from `AbstractBuildingGuards`, which implements the public `IGuardBuilding` interface.

Important behavior:

- One guard building owns military settings, patrol targets, rally/follow state, and assigned workers.
- Building level affects claim radius, bonus health, and vision.
- `IGuardBuilding` exposes task, patrol distance, patrol targets, guard position, rally location, low-health retreat behavior, manual-target requirements, and bonus vision.
- `BuildingGuardTower.requiresManualTarget()` and `getBonusHealth()` refine tower-specific behavior.

Addon implication: calculate defensive coverage through `IGuardBuilding` plus building level and staffing. Avoid changing `AbstractBuildingGuards` settings directly unless the feature explicitly owns that setting change.

## Barracks and Barracks Towers

The Barracks is represented by `BuildingBarracks`; its subordinate military posts are `BuildingBarracksTower` instances.

Verified relationships:

- `BuildingBarracks.getTowers()` stores the Barracks Tower hut positions.
- Barracks Tower implements `IGuardBuilding` through `AbstractBuildingGuards`.
- `BuildingBarracksTower.addBarracks(pos)` records its parent link.
- Tower upgrades are constrained by their Barracks relationship; `requestUpgrade(...)` and `canDeconstruct()` are specialized.
- Barracks cleanup and upgrades coordinate subordinate towers through `onDestroyed()` and `onUpgradeComplete(...)`.

Addon implication: do not count the Barracks itself as a guard post. Resolve `getTowers()` to buildings, validate each result, then count built/staffed Barracks Towers. Because `getTowers()` is currently on a core class rather than a dedicated public Barracks interface, isolate this access in one adapter and cover it with an integration test.

## Existing raid and colony-event systems

### `IRaiderManager`

Every `IColony` exposes an `IRaiderManager`. It already owns raid eligibility, timing, spawn selection, difficulty, casualty tracking, and completion hooks:

- `canHaveRaiderEvents()`, `canRaid()`, `willRaidTonight()`, `isRaided()`.
- `setRaidNextNight(RaidSettings)` and `raiderEvent(RaidSettings)`.
- `calculateSpawnLocation()`, `getLastSpawnPoints()`, and `calculateRaiderAmount(...)`.
- `getColonyRaidLevel()` and `getRaidDifficultyModifier()`.
- `onLostCitizen(...)`, `onRaiderDeath(...)`, and `onRaidEventFinished(...)`.

`RaidSettings` supports forced spawn, explicit raid type, ship allowance, optional raider count, and optional location. Calling `raiderEvent` bypasses part of normal scheduling; use it only from deliberate server-authoritative orchestration. For normal gameplay, `setRaidNextNight` is less invasive.

### `IEventManager`

`IEventManager` owns active `IColonyEvent` instances, event IDs, participating entities, nightfall processing, death callbacks, and persistence. Existing raids implement `IColonyRaidEvent` and should remain registered with this manager so MineColonies can save, tick, and finish them correctly.

A new raid family should be implemented as a MineColonies colony event only after confirming that its extension contracts are stable enough. A lower-risk first milestone is an addon-owned scheduler that invokes documented `IRaiderManager` operations and stores only addon-specific metadata.

## Events useful to an addon

Subscribe through `IMinecoloniesAPI.getInstance().getEventBus().subscribe(...)`. Relevant published events include:

- `ColonyManagerLoadedModEvent` / `ColonyManagerUnloadedModEvent`.
- `ColonyCreatedModEvent` / `ColonyDeletedModEvent`.
- `BuildingAddedModEvent`, `BuildingRemovedModEvent`, and `BuildingConstructionModEvent`.
- `CitizenAddedModEvent`, `CitizenRemovedModEvent`, `CitizenDiedModEvent`, and `CitizenJobChangedModEvent`.
- `ColonyPlayerRankChangedModEvent` and territory enter/leave events.

Use these events to invalidate cached military snapshots and update addon state. Event callbacks should schedule world mutation onto the appropriate server thread if the event contract does not guarantee it.

## Recommended addon boundary

Expose an addon-owned interface such as `ColonyMilitarySnapshotProvider` from the integration package. Its immutable result can contain:

- colony ID and dimension;
- built Guard Tower count and levels;
- built Barracks count;
- valid Barracks Tower count and levels;
- assigned guard count;
- current raid state and MineColonies difficulty modifier.

The raid domain then consumes this snapshot without importing MineColonies classes. This makes balancing testable and limits breakage when a snapshot release changes internal classes.

## Risks and constraints

- **Snapshot API drift:** the pinned dependency is a snapshot release. Keep it pinned and review diffs before upgrading.
- **No separate API artifact:** compile against the full mod, but enforce package boundaries in code review.
- **Core-only Barracks relationship:** `BuildingBarracks.getTowers()` is an implementation dependency.
- **Server/client split:** `IColony` is authoritative server state; `IColonyView` and building views are client projections and must not drive gameplay decisions.
- **Lifecycle:** colonies, buildings, chunks, and citizen entities can unload independently.
- **Compatibility:** direct AI replacement, mixins into colony ticks, and mutation of internal manager collections are likely to conflict with MineColonies and other addons.

## Primary references

- [MineColonies source repository](https://github.com/ldtteam/minecolonies)
- [MineColonies 1.21 release branch](https://github.com/ldtteam/minecolonies/tree/release/1.21)
- [Release 1.21.1-1.1.1331-snapshot](https://github.com/ldtteam/minecolonies/releases/tag/v1.21.1-1.1.1331-snapshot)
- [MineColonies wiki](https://minecolonies.com/wiki/)
- [LDTTeam Maven repository](https://ldtteam.jfrog.io/ldtteam/modding/)
