# Valkaw Client — 26.2

Source-only snapshot of the 26.2 port.

| Field | Value |
| --- | --- |
| Minecraft | 26.2 |
| Fabric Loader | 0.20.1 |
| Fabric API | 0.161.0+26.2 |
| Mappings | none — 26.x client jars ship unobfuscated, compiled directly against the named jar |
| Gradle plugin | `net.fabricmc.fabric-loom` 1.16.1 (no remap step) |
| Java | 21 |
| Source | `../../26.2_TEST/valkaw-client` (port workspace) |
| Variants | `sodium` (OpenGL), `vulkan` (VulkanMod dev build in `libs/`) |

## Layout

```
valkaw-client/
  src/main/            resources, fabric.mod.json, assets, lang
  src/client/java/     all client code (gui, hud, module, mixin, keybinds)
  libs/                local jars nested into the shipped mod (26.2 builds only)
  build.gradle         Loom setup, no remapJar, nested jars attached to `jar`
  gradle.properties    Minecraft / loader / API versions
  gradlew              wrapper (offline-safe, jar checked in)
```

## Build

```bash
# OpenGL / Sodium variant
JAVA_HOME=$HOME/.local/share/jdk-25 ./gradlew jar -Pvalkaw_variant=sodium \
  --no-configuration-cache --no-parallel

# Vulkan variant
JAVA_HOME=$HOME/.local/share/jdk-25 ./gradlew jar -Pvalkaw_variant=vulkan \
  --no-configuration-cache --no-parallel
```

Output: `build/libs/valkawclient-<version>-<variant>.jar`. There is no `remapJar`
task on 26.x — the plain `jar` task produces the shippable artifact.

## Notes carried over from the port

- `PORTING_26.2.md` documents the API migration (chat/HUD split, render-state model,
  `GuiGraphicsExtractor`, `KeyMapping` accessor).
- The stale `yarn_mappings` property has been dropped from `gradle.properties`: 26.x
  compiles against the unobfuscated jar, so there is no mappings layer to configure.

## Artifact

`scripts/build-version.sh 26.2` builds both variants and drops the shippable jars into
`version/valkaw-<profile>-26.2-<client-version>.jar` at the repository root.

## What was kept out

Excluded from the copy: `build/`, `.gradle/`, `run/`, `logs/`, harness and editor
files. `libs/` was pruned from 21 jars to the 11 that 26.2 actually references:
every leftover 1.21.11 build (`entityculling`, `lambdynamiclights`,
`lightweight-inventory-sorting`, `shulkerboxtooltip` 5.2.16, `sodiumfpscapfix`,
`greedy-meshing`, `chunksfadein`, `offlinemodefix`, `rpc`, `ImmediatelyFast`) was
removed. VulkanMod 26.2 stays because `build.gradle` puts it on the dev classpath.
