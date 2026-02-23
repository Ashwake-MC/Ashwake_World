
# Ashwake World

`Ashwake World` is a NeoForge mod for Minecraft `1.21.1` that builds the Ashwake
starting experience around a custom volcano spawn and dynamic world-state systems.

## Main Features

- Procedural Ashwake volcano generation with a controlled world spawn.
- Hub/settlement world bootstrap and placement logic.
- Weather Core system with rotating state phases, omen timing, and client cache sync.
- Intro GUI flow (story + learn-more pages) with responsive layout behavior.
- Custom entities and renderers (for example `World Core Orb` and `Rune Disc`).
- HUD and visual state feedback hooks on the client.

## Requirements

- Java `21`
- Minecraft `1.21.1`
- NeoForge `21.1.219`

## Development

From the project root:

```powershell
.\gradlew.bat compileJava
.\gradlew.bat runClient
.\gradlew.bat runServer
.\gradlew.bat build
```

If dependencies or IDE sync become stale:

```powershell
.\gradlew.bat --refresh-dependencies
.\gradlew.bat clean
```

## Configuration

Core mod settings are defined in `src/main/java/com/ashwake/ashwake/config/AshwakeConfig.java`.
At runtime, common config values are exposed through NeoForge's normal config pipeline.

## Project Layout

- Java sources: `src/main/java/com/ashwake/ashwake`
- Resources: `src/main/resources/assets/ashwake`
- Mod metadata template: `src/main/templates/META-INF/neoforge.mods.toml`

## License

This repository is licensed as **All Rights Reserved**.
See `LICENSE` for terms.

NeoForge template-origin files remain covered by `TEMPLATE_LICENSE.txt`.
