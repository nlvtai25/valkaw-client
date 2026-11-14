# Valkaw Client — 26.3

Source-only snapshot of the 26.3 port, derived from the 26.2 tree.
Sodium only: 26.3 has no VulkanMod or Not Enough Vulkan build to pair with.

| Field | Value |
| --- | --- |
| Minecraft | 26.3 |
| Fabric Loader | 0.20.1 |
| Fabric API | 0.166.0+26.3 |
| Mappings | none — 26.x client jars ship unobfuscated, compiled directly against the named jar |
| Gradle plugin | `net.fabricmc.fabric-loom` 1.16.1 (no remap step) |
| Java | 21 |
| Source | `../26.2/valkaw-client` |
| Variants | `sodium` only; any other `-Pvalkaw_variant` fails the build on purpose |

## Layout

```
valkaw-client/
  src/main/            resources, fabric.mod.json, assets, lang
  src/client/java/     all client code (gui, hud, module, mixin, keybinds)
  libs/                local jars nested into the shipped mod (26.3 builds only)
  build.gradle         Loom setup, no remapJar, sodium-only variant guard
  gradle.properties    Minecraft / loader / API versions
  gradlew              wrapper (offline-safe, jar checked in)
```

## Build

```bash
JAVA_HOME=$HOME/.local/share/jdk-25 ./gradlew jar -Pvalkaw_variant=sodium \
  --no-configuration-cache --no-parallel
```

Output: `build/libs/valkawclient-<version>-sodium.jar`.

## API differences from 26.2

26.3 is the release where the window layer moved from GLFW to SDL3
(`org.lwjgl.glfw` is gone; the game depends on `org.lwjgl:lwjgl-sdl` and exposes
`com.mojang.blaze3d.platform.SDLEventHandler`). Three migrations were required:

| 26.2 | 26.3 |
| --- | --- |
| `org.lwjgl.glfw.GLFW.GLFW_KEY_*` | `InputConstants.KEY_*` (SDL scancodes) |
| `InputConstants.isKeyDown(window, key)` | `InputConstants.isKeyDown(key)` |
| `GLFW.glfwGetMouseButton(...) == GLFW_PRESS` | `mc.mouseHandler.isLeftPressed()` / `isRightPressed()` / `isMiddlePressed()` |
| `GLFW.GLFW_KEY_UNKNOWN` | `InputConstants.UNKNOWN.getValue()` |
| `InputConstants.Type.KEYSYM` | `InputConstants.Type.KEYBOARD` |
| `Window.isFullscreen()` | `mc.options.fullscreen().get()` |
| `new OptionsScreen(parent, options, false)` | `new OptionsScreen(parent, options)` |
| `net.minecraft.client.renderer.ItemInHandRenderer` | `net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer` |
| `ScreenEffectRenderer.submit(...)` reads `LocalPlayer.isOnFire()` | reads `PlayerRenderState.isOnFire` / `PlayerRenderState.blockOverlay` |
| `IconSet.getStandardIcons(PackResources)` | `IconSet.getStandardIcons(PackMetadataResources)` |

### Key codes changed space

`InputConstants.KEY_*` on 26.3 are SDL scancodes, not GLFW key codes
(`KEY_ESCAPE = 41`, `KEY_SPACE = 44`, `KEY_F6 = 63`). Any key binding stored by an
older build (`valkaw-client.json`, `module.setKey`) points at the wrong physical
key after upgrading. Only matters if a config file is carried over from a 26.2 or
1.21.11 profile; a fresh profile is unaffected.

### Freelook mixin

`ItemInHandRendererMixin` became `FirstPersonHandsAndItemsRendererMixin`. The
injected method kept its name but changed its parameters:

```java
submitHandsWithItems(float, PoseStack, SubmitNodeCollector,
                     PlayerRenderState, FirstPersonHandsAndItemsRenderState)
```

### Overlay mixin

`ScreenEffectRendererMixin` no longer redirects `LocalPlayer.isOnFire()`; the
renderer only reads the player render state now. The mixin injects at the head of
`submit(...)` and clears `PlayerRenderState.blockOverlay` (pumpkin) and
`PlayerRenderState.isOnFire` (fire) when the matching module is on.

## Artifact

`scripts/build-version.sh 26.3` (sodium is the default profile) writes
`version/valkaw-sodium-26.3-<client-version>.jar` at the repository root.

## Not verified

Compiles and packages (33 nested mods), but has not been launched in a real game
session yet.
