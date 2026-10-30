# Changelog

## v1.0.6 — 2026-10-31

### Launcher
- The update check reads a small `assets/downloads/latest.json` index over plain HTTPS instead of the GitHub Contents API, so a busy IP no longer sees `HTTP 403`.
- When that index is unreachable the launcher falls back to the API and then to the last listing it downloaded successfully.
- A rate limited API now says "GitHub rate limit reached" instead of a bare status code.
- Update checks are covered by tests for the index, the fallback and the cached listing.

## v1.0.5 — 2026-10-31

### Launcher
- The client jar is written to a temporary file and renamed into place, so a game that is already running keeps its open jar instead of reading a half-written one.
- The username field is gone. The launcher reads the name you set in the game and shows it in the top bar.
- Windows carries the same core as Linux: resources browser, updates from `assets/downloads` and the username sync.
- Windows gets the Valkaw icon and the warm launcher colours instead of the old navy background.
- The game window now carries the Valkaw icon instead of the default grass block.
- On Linux the launcher registers desktop entries for itself and for the game window, so docks and taskbars show the Valkaw icon instead of a generic placeholder.

## v1.0.2 — 2026-10-31

### Client
- New main menu in the Saturn style: planet backdrop, wordmark, navigation rail and an account pill that opens a list of saved names.
- Usernames are created and switched in game. Each name gets a stable offline UUID and is written to the client config.
- Client Settings rebuilt: category rail, search, All/On filters, compact cards and eased scrolling. Action modules show the value they hold, key-held modules show `Hold <key>`.
- HUD editor links elements by placing their edges together: right, left, below or above. Links survive a restart, and the element being dragged no longer snaps back to the edge.
- HUD stays on screen while chat, inventory and containers are open. F1 is the only switch-off.
- Module keybinds appear under **Valkaw Client** in Controls, so they can be rebound like vanilla keys.
- New Performance module **Menu Backdrop**: one slider for how dark the screen gets behind in-world menus.
- Menus follow the configured framerate limit instead of the vanilla 60 fps cap.
- Inter replaces the vanilla font through a bundled font provider, with the vanilla font and unifont as fallback.
- New logo, window icons and a high-resolution wordmark; no more scaled bitmap text in the main menu.
- Armor HUD shows the item and its durability side by side, vertical by default.
- PC Info rows line up in one block, with values right-aligned.
- No Bobbing now cancels camera bobbing directly instead of rewriting the player's video setting.
- Zoom and Freelook are key-held only, with no Enable/Disable switch.
- The client ships a default config, so a fresh install starts with the curated HUD layout and module set.
- All 34 modules were smoke-tested before release.

## v1.0.4.1 — 2026-10-30

### Launcher
- The resources browser no longer lists mods that already ship inside the client, so a second copy cannot break the game with a duplicate mod id.
- Renderer mods for the other profile are hidden too: Sodium and Iris addons on VulkanMod, VulkanMod and Beryl on Sodium.
- A downloaded jar is checked before it is installed. If another jar already provides the same mod id, the download is dropped and the reason names the file to remove.
- Downloads are written to a temporary file first, so an interrupted transfer cannot leave a half-written jar in the mods folder.
- Search results now report how many mods were hidden because they are part of the client.

## v1.0.4 — 2026-10-30

### Launcher
- Auto-update reads the published builds in `assets/downloads` instead of release tags, so a newer launcher is found on both Linux and Windows.
- A failed resource download no longer leaves a broken jar or zip in the profile folder.
- File names coming from Modrinth are sanitized against path traversal, including Windows-style separators.
- The Modrinth HTTP client is reused between requests and version queries are JSON-encoded instead of hand-built.
- The resources modal keeps its header and tabs fixed and scrolls the body on its own layer, so long category and result lists move smoothly.
- Added tests for file name sanitizing, folder mapping, version comparison and the update listing.

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
- New Resources page. It opens a Modrinth browser inside the launcher for mods, resource packs and shader packs, filtered by the selected Minecraft version and, for mods, by the Fabric loader.
- A filter column on the left narrows results by category. Resource packs add features and resolution, shader packs add features and performance impact.
- Downloads go into the selected profile, so Sodium and VulkanMod each keep their own `mods`, `resourcepacks` and `shaderpacks` folders. A progress bar follows the file and a button opens the folder.
- Launcher updates now read `assets/downloads` instead of GitHub releases, so both builds are found as soon as the files are published.
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
