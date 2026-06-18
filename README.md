# MineColonies Raids

MineColonies Raids is a NeoForge 1.21.1 addon that will extend MineColonies with raid-focused gameplay. The repository currently provides a clean, buildable foundation and an explicit integration boundary for future features.

## Requirements

- JDK 21
- Minecraft 1.21.1
- NeoForge 21.1.233 or newer compatible 21.1.x release
- MineColonies 1.21.1

## Development

```text
./gradlew build
./gradlew runClient
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

MineColonies is resolved from the official LDTTeam Maven repository and is declared as both a compile/runtime dependency and a required NeoForge mod dependency. See [docs/MineColoniesIntegration.md](docs/MineColoniesIntegration.md) for dependency and upgrade guidance.

## Project layout

```text
src/main/java/com/minecolonies/raids/
├── integration/minecolonies/  MineColonies adapter boundary
├── raid/                       Loader-independent raid domain
├── registry/                   NeoForge deferred registrations
└── MineColoniesRaids.java      Mod entry point
src/main/resources/             Runtime assets and data
src/main/templates/             Generated NeoForge metadata
docs/                           Architecture and research notes
```

## Documentation

- [Architecture](docs/Architecture.md)
- [MineColonies integration](docs/MineColoniesIntegration.md)
- [MineColonies technical research](docs/MineColoniesResearch.md)
- [Contributing](CONTRIBUTING.md)

## Status and license

This project is in early development. The project metadata currently declares **All Rights Reserved**; no permission to redistribute modified builds is implied.
