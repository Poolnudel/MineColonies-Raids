# Contributing

Use JDK 21 and keep changes compatible with Minecraft 1.21.1 and NeoForge 21.1.x.

Before submitting a change:

1. Keep direct MineColonies implementation access inside `integration.minecolonies`.
2. Prefer public `com.minecolonies.api` types over internal implementation classes.
3. Run `gradlew.bat build` and, for runtime-affecting changes, `gradlew.bat runClient`.
4. Update the relevant document when changing architecture or dependency versions.

Do not commit generated build output, IDE settings, or the `run/` game directory.
