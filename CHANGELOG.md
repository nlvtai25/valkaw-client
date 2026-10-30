# Changelog

## v1.0.3 — 2026-10-30

### Launcher
- Everything the launcher writes now lives under one folder: `~/.valkaw` on Linux, `%USERPROFILE%\.valkaw` on Windows. `launcher/` holds the config and the cached client jars, `jre/` holds the Java runtime, `minecraft/` holds the game files.
- Existing installs are moved into the new layout on first start, including the downloaded Java runtime, so nothing has to be downloaded again.
- The Vulkan profile is checked before it can be picked. The launcher reads the GPU name, driver version and Vulkan version from the driver and, when the profile cannot work, refuses it and opens a dialog that says why.
- Three cases get their own answer: no Vulkan driver (install the GPU driver), driver too old (update it, the GPU can do Vulkan 1.3), and a GPU that cannot reach Vulkan 1.3 with any driver (play with Sodium, or use a GPU that supports Vulkan 1.3).
- The dialog shows the detected GPU, Vulkan and driver versions, links to the vendor's driver page, and can switch to the Sodium profile in one click.
- Play checks the same thing, so the Vulkan profile cannot start from an old config either.
- The setup popup reports the real Vulkan version and GPU instead of only saying the driver was found.
- The Windows launcher now carries the WebView2 bootstrapper inside the exe. If the runtime is missing it installs silently on first start, per user and without admin rights.
- Version `v1.0.3`.

## v1.0.2 — 2026-10-29

### Launcher
- New setup popup. It checks three things and says which are missing: Java 21, the game files, and the graphics driver for the selected profile.
- Pressing Play without Java or game files now opens the popup instead of failing on the status line.
- `Install missing` downloads Temurin JRE 21 into `~/.valkaw-launcher/java`, installs the selected profile, then starts the game. Progress is reported inside the popup.
- The graphics row is a notice only. It names the API in use (OpenGL or Vulkan) and what was missing, and the launcher never installs drivers. Play works either way.
- On start the popup opens only when Java or game files are missing.
- Launcher version now comes from the binary, so a config saved by an older build cannot report the old version.
- A Windows launcher ships alongside the Linux one: `assets/downloads/valkaw-launcher-win-v1.0.2-windows-amd64.exe`. Both share one Go core and one interface; the Windows build adds a GPU preference in Settings, starts the game with `javaw.exe` so no console window appears, and can replace its own exe when updating.
- Version `v1.0.2`.

## v1.0.1 — 2026-10-28

### Freelook
- Freelook now works like Toggle Perspective: the camera sits in third person and still turns freely, while the player body keeps its direction.
- Options: `WHEN PRESSING` picks `THIRD-PERSON BACK` or `THIRD-PERSON FRONT`; `MODE` picks `TOGGLE` or `PRESSING` (hold the key).
- Camera distance respects the vanilla camera distance attribute and clips against blocks, so it does not pass through walls.
- Fixed the module switching itself off while held in toggle mode.

### Options screens
- Modules that do not draw styled text no longer show the colour/prefix panel: Freelook, Copy Chat, Chat Timestamps, No Bobbing, No Hurt Cam, Copy Coords, No Fire, Clear Water / Lava, No Pumpkin, Zoom, Toggle Sprint, Toggle Sneak.
- Their options list now uses the full panel width.
- HUD Sprint and Sneak keep their colour options.

### Chat
- Chat Timestamps are white by default.
- A format reset follows each timestamp, so colour codes from the server do not bleed into it, and the message no longer inherits the timestamp colour.

### Build
- Client version is now `1.0.1`. The main menu build label reads the mod version instead of a hardcoded string.

### Launcher
- Version `v1.0.1`.
- The client jar download now reads the newest release tag from the `releases/latest` redirect instead of a hardcoded client version, so new client releases need no launcher rebuild.
- Falls back to the cached jar when GitHub is unreachable.
