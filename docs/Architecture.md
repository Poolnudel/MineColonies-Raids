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
