# Deterministic Companions

**Deterministic Companions** is a Fabric mod for Minecraft Java Edition **26.2**. It creates server-authoritative fake players that perform deterministic movement, mining, building, combat, protection and area-guard tasks. The project uses Java 25 and Fabric; it has no machine learning, external services, or Baritone dependency.

## Documentation

- Read [PROJECT_README.md](PROJECT_README.md) for the full setup and feature overview.
- Read [COMMANDS.md](COMMANDS.md) for the command reference.
- Read [TESTING.md](TESTING.md) for automated and manual test scenarios.
- Read [docs/TOOLCHAIN.md](docs/TOOLCHAIN.md) for the verified Minecraft/Fabric toolchain and API notes.
- [SOURCE_TREE.txt](SOURCE_TREE.txt) lists the source files.

## Build

Requires JDK 25. From this folder run:

```powershell
.\gradlew.bat clean build
```

The build runs the JUnit suite. It currently runs **322 tests**. Gradle wrapper version: 9.5.1. Minecraft 26.2, Fabric Loader 0.19.3, Fabric API 0.156.0+26.2, Fabric Loom 1.17.20.

The installable compiled mod is attached to each [GitHub release](https://github.com/venkatavallabhaneni/Deterministic-Companions/releases) (`deterministic-companions-<version>.jar`); `./gradlew build` produces it locally in `build/libs/`. The project can also build a development server with:

```powershell
.\gradlew.bat runServer --args=nogui
```

Minecraft requires the operator to read and accept its EULA before running a server. This project does not accept it automatically.

## Install

Put the compiled mod JAR and Fabric API in the server's `mods` folder. To use Falcon's R-key target stick, install the same mod JAR and Fabric API on the Fabric client too. Other server commands work from an unmodified client. Give yourself operator permission, then try:

```text
/bot spawn bob
/bot schematic build 08_watch_tower
/bot follow bob
/bot spawn falcon flyer
/bot falcon get_stick
```

## Project map

- `src/main/java/dev/companions/command`: Brigadier command registration and helpers.
- `player`, `task`, `navigation`, `navigation/flight`: fake players, task lifecycle, ground A*, and Falcon's bounded 3D flight planner.
- `combat`, `inventory`, `survival`, `world`, `building`, `selection`, `persistence`, `ui`: focused behavior controllers.
- `schematic`: Sponge `.schem` reading, layer scheduling, scaffolding and the shared multi-companion build job ([docs/FEATURES-1.19.0.md](docs/FEATURES-1.19.0.md), [1.20.0](docs/FEATURES-1.20.0.md), [1.21.0](docs/FEATURES-1.21.0.md), [1.22.0](docs/FEATURES-1.22.0.md), [1.23.0](docs/FEATURES-1.23.0.md), [1.23.1](docs/FEATURES-1.23.1.md), [1.24.0](docs/FEATURES-1.24.0.md)).
- `src/test/java`: pure logic and Minecraft-bootstrap tests.
- `docs`: prior release notes, API verification and UI notes.

Falcon uses one independent flying slot in addition to four ordinary companions. Automatic Falcon hunting chooses visible hostile mobs with open sky above them; its R-key stick can designate any visible mob within its allowed range. Both ground and flight search are bounded and run on the server thread.

## Repository contents

The repository holds the source, resources, tests, documentation and Gradle wrapper. Build output (`build/`, `.gradle/`), runtime folders (`run/`, `logs/`) and built jars are not committed; compiled jars are published on the releases page.

## License

MIT — see [LICENSE](LICENSE).
