# Valkaw Client

Minecraft Java Edition 1.21.11 client and launcher, built on Fabric.

Two profiles ship from the same source: **Sodium** for OpenGL and **VulkanMod** for Vulkan. Modules cover HUD, performance and utility only. There is no combat, movement or server-side cheat.

## Download

Grab everything from [Releases](https://github.com/nlvtai25/valkaw-client/releases).

| File | What it is |
| --- | --- |
| `valkaw-launcher-v1.0.0-linux-amd64` | Launcher binary |
| `valkawclient-1.0.0-sodium.jar` | Sodium profile client |
| `valkawclient-1.0.0-vulkan.jar` | Vulkan profile client |

The launcher installs the selected profile into `~/.valkaw/<profile>/` and starts the game with tuned JVM flags.

## Modules

**HUD (16).** Armor, Combo, Coordinates, CPS, Crosshair, FPS, Keystrokes, PC Info, Ping, Potion, Server IP, Sneak, Speed, Sprint, Time, TPS.
Every HUD module can link to the left or right screen edge, and positions are dragged directly in the HUD Editor (hold `F6`).

**Performance (5).** Clear Fluids, Clouds, No Fog, Particles, Render Distance.

**Utility (13).** Block Outline, Chat Timestamps, Copy Chat, Copy Coords, Freelook, Fullbright, No Bobbing, No Fire, No Hurt Cam, No Pumpkin, Toggle Sneak, Toggle Sprint, Zoom.

Two extras sit outside the module lists. The **Custom Symbols** tab beside the chat input inserts Unicode symbols and `&` colour codes, which is handy when writing item names and lore. The **Crosshair Editor** draws a crosshair pixel by pixel on a 15x15 grid.

## Requirements

- Linux x64
- JDK 21 or newer
- Fabric 1.21.11 (`loader 0.20.1`)
- An account as offline username. Realms is not supported.

## Build the client

```sh
cd valkaw-client
./gradlew remapJar -Pvalkaw_variant=sodium
./gradlew remapJar -Pvalkaw_variant=vulkan
```

Output lands in `valkaw-client/build/libs/`. Both variants read the same `src/` tree, and `build.gradle` decides which renderer and which optimisation mods are bundled.

## Build the launcher

The launcher embeds the two client jars from `valkaw-launcher/assets/clients/1.21.11/`, so build the client first and copy the jars there.

```sh
cd valkaw-launcher
go build -o build/bin/valkaw-launcher .
# or, with the Wails toolchain: ./build.sh
```

## Layout

```
valkaw-client/     Fabric mod source, one tree, two build variants
valkaw-launcher/   Go + Wails launcher
```

## License

Apache License 2.0 — see [`LICENSE`](LICENSE). Bundled third-party mods keep their own licenses.
