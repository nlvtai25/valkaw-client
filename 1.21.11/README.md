# Valkaw Client — 1.21.11

Source-only snapshot of the 1.21.11 client.

| Field | Value |
| --- | --- |
| Minecraft | 1.21.11 |
| Fabric Loader | 0.20.1 |
| Fabric API | 0.141.6+1.21.11 |
| Mappings | Mojang official (via Loom) |
| Gradle plugin | `net.fabricmc.fabric-loom-remap` 1.18-SNAPSHOT |
| Java | 21 |
| Source | `../valkaw-client` (main working tree) |
| Variants | `sodium` (OpenGL), `vulkan` (VulkanMod) |

## Layout

```
valkaw-client/
  src/main/            resources, fabric.mod.json, assets, lang
  src/client/java/     all client code (gui, hud, module, mixin, keybinds)
  libs/                local jars nested into the shipped mod
  build.gradle         Loom setup + jar-in-jar list
  gradle.properties    Minecraft / loader / API versions
  gradlew              wrapper (offline-safe, jar checked in)
```

## Build

```bash
# OpenGL / Sodium variant
JAVA_HOME=$HOME/.local/share/jdk-25 ./gradlew remapJar -Pvalkaw_variant=sodium \
  --no-configuration-cache --no-parallel

# Vulkan variant
JAVA_HOME=$HOME/.local/share/jdk-25 ./gradlew remapJar -Pvalkaw_variant=vulkan \
  --no-configuration-cache --no-parallel
```

Output: `build/libs/valkawclient-<version>-<variant>.jar`.

`scripts/build-version.sh 1.21.11` builds both variants and drops the shippable jars
into `version/valkaw-<profile>-1.21.11-<client-version>.jar` at the repository root.

## What was kept out

Excluded from the copy: `build/`, `.gradle/`, `run/`, `logs/`, editor and harness
files (`crush.db*`, `.crush/`, `.design-os/`). The `libs/` folder was pruned to the
jars actually referenced by `build.gradle`; `ImmediatelyFast-Fabric-1.14.3+1.21.11.jar`
was removed because the mod is pulled from Modrinth Maven instead.

Build artifacts are not part of this snapshot — regenerate them with the command above.
