# Valkaw Client — 26.1.2

Source-only snapshot of the 26.1.2 port, derived from the 26.2 tree.

| Field | Value |
| --- | --- |
| Minecraft | 26.1.2 |
| Fabric Loader | 0.20.1 |
| Fabric API | 0.155.3+26.1.2 |
| Mappings | none — 26.x client jars ship unobfuscated, compiled directly against the named jar |
| Gradle plugin | `net.fabricmc.fabric-loom` 1.16.1 (no remap step) |
| Java | 21 |
| Source | `../26.2/valkaw-client` |
| Variants | `sodium` (OpenGL), `vulkan` (VulkanMod 0.6.8 in `libs/`) |

## Layout

```
valkaw-client/
  src/main/            resources, fabric.mod.json, assets, lang
  src/client/java/     all client code (gui, hud, module, mixin, keybinds)
  libs/                local jars nested into the shipped mod (26.1.2 builds only)
  build.gradle         Loom setup, no remapJar, nested jars attached to `jar`
  gradle.properties    Minecraft / loader / API versions
  gradlew              wrapper (offline-safe, jar checked in)
```

## Build

```bash
JAVA_HOME=$HOME/.local/share/jdk-25 ./gradlew jar -Pvalkaw_variant=sodium \
  --no-configuration-cache --no-parallel

JAVA_HOME=$HOME/.local/share/jdk-25 ./gradlew jar -Pvalkaw_variant=vulkan \
  --no-configuration-cache --no-parallel
```

Output: `build/libs/valkawclient-<version>-<variant>.jar`. 26.x has no `remapJar`
task; the plain `jar` task produces the shippable artifact.

## API differences from 26.2

26.1.2 is the release line before the HUD split. Everything else in the render
state model is already in place:

| 26.2 | 26.1.2 |
| --- | --- |
| `net.minecraft.client.gui.Hud` (own class) | `net.minecraft.client.gui.Gui` (one class) |
| `mc.gui.screen()` / `mc.gui.setScreen(...)` | field `mc.screen` / `mc.setScreen(...)` |
| `mc.gui.hud.getChat()`, `mc.gui.hud.setOverlayMessage(...)` | `mc.gui.getChat()`, `mc.gui.setOverlayMessage(...)` |
| `gameRenderer.gameRenderState()` | `gameRenderer.getGameRenderState()` |
| `gameRenderer.mainRenderTarget()` | `mc.getMainRenderTarget()` |
| `guiRenderState.isHudHidden` | `mc.options.hideGui` |
| `Hud.getMobEffectSprite(...)` | `Gui.getMobEffectSprite(...)` |
| `Gui.setScreen(...)` (moved off `Minecraft` in 26.2) | `Minecraft.setScreen(...)` |
| `ItemInHandRenderer.submitHandsWithItems(...)` | `ItemInHandRenderer.renderHandsWithItems(...)` |
| `ScreenEffectRenderer.submit(...)` | `ScreenEffectRenderer.renderScreenEffect(...)` |
| `LevelRenderer.submitHitOutline(...)` | `LevelRenderer.renderHitOutline(...)` |

Applied in: `gui/ChatCopyButtons`, `hud/HudRenderer`, `hud/InputState`,
`keybinds/Keybinds`, `mixin/GuiCrosshairMixin`, `mixin/GuiHudMixin`,
`mixin/MinecraftScreenMixin` (target moved from `Gui` back to `Minecraft`),
`mixin/ItemInHandRendererMixin` and `mixin/ScreenEffectRendererMixin` (injected
method names), `module/ModuleManager`,
`module/utility/{AutoText,CopyCoords,Freelook,Zoom}Module`, `SnapShot`,
`module/hud/PotionHudModule`.

### Block outline

`renderHitOutline` on 26.1.2 ends in `(int color, float alpha)`, while 26.2 added a
width argument in front of the boolean. `LevelRendererMixin` therefore only
overrides the colour here; the `thickness` option of the `block-outline` module is
inert on this version and the alpha stays vanilla.

### Mixin targets checked

Every mixin target class, injected method, `@Accessor`/`@Invoker` name and shadow
field was verified against the mapped client jar with `javap` (25 mixins, 0
mismatches), because a wrong name compiles fine and only fails at class load.

## Artifact

`scripts/build-version.sh 26.1.2 --profiles all` writes
`version/valkaw-{sodium,vulkan}-26.1.2-<client-version>.jar` at the repository root.

## Not verified

Both variants compile and package (33 nested mods on sodium, 26 on vulkan), but
neither has been launched in a real game session yet.
