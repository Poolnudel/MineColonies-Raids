# MineColonies integration

## Dependency setup

`build.gradle` declares the official LDTTeam Maven repository and adds:

```groovy
implementation "com.ldtteam:minecolonies:${minecolonies_version}"
```

`neoforge.mods.toml` also marks `minecolonies` as required, loads this addon after it, and applies the dependency on both client and server.

The dependency is pinned to `1.1.1331-1.21.1-snapshot`, the latest published 1.21.1 build verified on 2026-06-18. Its Maven POM supplies MineColonies' required LDTTeam dependencies transitively. Keep the version pinned so local and CI builds remain reproducible.

## Integration policy

- Use classes under `com.minecolonies.api` whenever they expose the required behavior.
- Put imports from `com.minecolonies.core` behind adapter classes in `integration.minecolonies`.
- Never copy MineColonies state into long-lived caches without an invalidation strategy.
- Perform colony and building queries on the logical server.
- Test against the exact pinned MineColonies build whenever upgrading.

## Upgrade checklist

1. Update `minecolonies_version`.
2. Refresh dependencies with `gradlew.bat --refresh-dependencies`.
3. Compile and inspect warnings for changed APIs.
4. Start a client and load a world containing a colony, Guard Tower, Barracks, and Barracks Towers.
5. Re-run integration tests for building discovery, permissions, raid targeting, and save/load behavior.
