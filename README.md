<div align="center">

# ⚡ VALKAW CLIENT

### Minecraft 1.21.11 · Fabric · Two performance profiles · Custom HUD

A lightweight Minecraft client with two distinct profiles, a custom in-game HUD, and a launcher that downloads and installs the selected profile for you.

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-55a630?style=for-the-badge&logo=minecraft&logoColor=white)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Loader-Fabric-dbd0b4?style=for-the-badge)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-21%2B-ed8b00?style=for-the-badge&logo=openjdk&logoColor=white)](https://adoptium.net/)
[![Windows](https://img.shields.io/badge/Platform-Windows-0078d4?style=for-the-badge&logo=windows&logoColor=white)](https://www.microsoft.com/windows/)
[![Linux](https://img.shields.io/badge/Platform-Linux-fcc624?style=for-the-badge&logo=linux&logoColor=black)](https://www.linux.org/)

<br>

[⬇️ **DOWNLOADS**](#downloads)　·　[📦 Get client v1.5](https://github.com/nlvtai25/valkaw-client/releases/tag/v1.5)　·　[🧩 Mods & sources](#mods-and-sources)　·　[🚀 Getting started](#getting-started)

</div>

---

## ✨ The Valkaw experience

| ⚡ Two ways to play | 🎛️ A launcher that remembers |
|---|---|
| Pick **Sodium** for a broadly compatible, performance-focused renderer, or **VulkanMod** for Vulkan-based rendering on a compatible system. Each profile ships with its own curated mod set. | Choose **Online** to use a package from GitHub Releases or **Offline** to install a downloaded local bundle JAR. Your username, preferences, and existing settings are preserved. |
| 🧭 **A HUD you can make yours** | 🧰 **Everyday conveniences** |
| Edit and share HUD profiles. Arrange widgets for system information, armor, biome, hitboxes, quick chat, and more. | Tune RAM and JVM arguments, select a player name, switch themes, and choose what happens when the game closes. Game files and logs are separated by profile. |
| 🖥️ **Lightweight Windows and Linux launchers** | 📦 **A ready-to-play setup** |
| Choose the portable Windows launcher or the Python-based Linux launcher. The launcher can set up Minecraft, Fabric, libraries, assets, and Java 21 when needed. | Install or update a profile from the launcher's **Play** action instead of manually copying a folder of mods. |

> **Made for Minecraft Java Edition 1.21.11 on Windows and Linux.** The game requires Java 21 or later; the launcher can install Java and the required Minecraft runtime files when they are missing.

## ⬇️ Downloads

### Valkaw Launcher 1.6

Both current launchers are included **directly in this repository**:

| Platform | Download | Size | SHA-256 |
|---|---|---:|---|
| **Windows** | [Valkaw.Launcher-1.6.exe](downloads/Valkaw.Launcher-1.6.exe) | 636,416 bytes | `2AEAE3025A43A7B4D99F8E46AA6F127FBD9D3CB980F886FA3740AD9CE572FC42` |
| **Linux** | [Valkaw.Launcher-linux-1.6.py](downloads/Valkaw.Launcher-linux-1.6.py) | 193,170 bytes | `8DD77513723BDF55A4361D1C75EAFCC1558D037561741963B3063CD3A02E9212` |

> The Windows launcher is unsigned, so Windows may show a warning when you open it. Linux requires Python 3; the launcher opens a GUI when Tkinter is available and otherwise provides a command-line interface.

### Client v1.5

Each profile is distributed as one Fabric bundle JAR containing its nested mod JARs, configuration, options, and resource packs. Download the profile directly from the [v1.5 GitHub Release](https://github.com/nlvtai25/valkaw-client/releases/tag/v1.5):

| Profile | Release asset | Included mod JARs | File size |
|---|---|---:|---:|
| **Sodium** | [Sodium_v1.5.jar](https://github.com/nlvtai25/valkaw-client/releases/download/v1.5/Sodium_v1.5.jar) | 37 | 33.9 MiB |
| **VulkanMod** | [Vulkan_v1.5.jar](https://github.com/nlvtai25/valkaw-client/releases/download/v1.5/Vulkan_v1.5.jar) | 28 | 54.7 MiB |

To install the client manually, download the JAR for your profile, set the launcher's **Source** to **Offline**, and select the downloaded file when prompted. **Online** mode fetches the matching profile asset from the [latest GitHub Release](https://github.com/nlvtai25/valkaw-client/releases/latest). Offline mode skips the profile-package download; the launcher may still need an internet connection to obtain Minecraft, Fabric, libraries, assets, or Java.

## 🚀 Getting started

1. Download and open `Valkaw.Launcher-1.6.exe` on Windows, or run `Valkaw.Launcher-linux-1.6.py` on Linux.
2. Choose the **Sodium** or **VulkanMod** profile and enter a player name.
3. Choose **Online** to use a GitHub Release package or **Offline** to select a downloaded profile JAR from this page.
4. Press **Play**. On first use, the launcher installs the profile and sets up missing game runtime files, then starts Minecraft.
5. For troubleshooting, check the profile-specific `latest.log` under `log_launcher`.

### Linux quick start

1. Install Python 3; the launcher can install Java 21 if it is missing.
2. Download [Valkaw.Launcher-linux-1.6.py](downloads/Valkaw.Launcher-linux-1.6.py).
3. Run it with `python3 Valkaw.Launcher-linux-1.6.py`. Add `--cli` to use the command-line interface, `--install` to create an application menu entry, or `--prefetch` to download the client and runtime without launching the game.

The default game directories are `%AppData%\.valkaw\game_sodium` and `%AppData%\.valkaw\game_vulkan` on Windows, and `~/.valkaw/game_sodium` and `~/.valkaw/game_vulkan` on Linux. A previous `%AppData%\.valkaw\game` directory is migrated to the Sodium profile when possible.

**Authentication:** The launcher currently starts Minecraft with an offline username and a fixed UUID; it does not provide Microsoft/Mojang sign-in. Servers that require an authenticated account may refuse the connection. Do not enter account credentials into this launcher.

### Launcher options

| Option | Description |
|---|---|
| **Profile** | Choose Sodium or VulkanMod. Each has its own game directory, mods, and log folder. |
| **RAM** | Allocate 1–16 GB in 0.25 GB increments; the default is 6 GB. Leave enough memory for Windows and other applications. |
| **Username** | Set a player name and keep a list of names for later use. |
| **JVM Args** | Add JVM arguments separated by spaces. |
| **Priority** | Choose Normal, High, or Realtime. Higher priorities may require Administrator privileges. |
| **After run** | Hide the launcher to the system tray, keep it open, or close it when Minecraft exits. |
| **Game directory** | Set a custom location; leave it blank to use the default profile directories. |
| **Appearance** | Switch between Light and Dark themes. |

### Package updates and settings

When a profile bundle is installed or updated, the launcher extracts the nested mods and bundled defaults. Default settings are only copied when the corresponding file does not already exist, so your existing configuration is preserved. The launcher keeps separate game directories and logs for Sodium and VulkanMod.

## 🧩 Mods and sources

The profile tables below list every mod JAR included in the current profile bundle JARs, grouped into **shared**, **Sodium-only**, and **VulkanMod-only** entries. Version numbers come from the installed JAR metadata. Source links point to project pages or repositories identified by that metadata.

### Shared by both profiles · 22 mods

| Mod | Version | Source |
|---|---:|---|
| Architectury | 19.0.1 | [GitHub](https://github.com/architectury/architectury-api) |
| BadOptimizations | 2.4.1 | [GitHub](https://github.com/imthosea/BadOptimizations/) |
| Cloth Config | 21.11.153 | [GitHub](https://github.com/shedaniel/ClothConfig) |
| Collective | 8.32 | [GitHub](https://github.com/Serilum/Collective) |
| Debugify | 1.21.11+1.2 | [GitHub](https://github.com/isXander/Debugify) |
| Dynamic FPS | 3.11.6 | [GitHub](https://github.com/juliand665/Dynamic-FPS) |
| EntityCulling | 1.11.3 | [GitHub](https://github.com/tr7zw/EntityCulling) |
| Fabric API | 0.141.6+1.21.11 | [GitHub](https://github.com/FabricMC/fabric) |
| Fadeless | 2.0.7 | [GitHub](https://github.com/DerpDerpling/Fadeless) |
| Fast IP Ping | 1.0.12 | [GitHub](https://github.com/Fallen-Breath/fast-ip-ping) |
| FerriteCore | 8.2.0 | [GitHub](https://github.com/malte0811/FerriteCore) |
| Gamma Utils | 2.5.10 | [GitHub](https://github.com/Sjouwer/gamma-utils) |
| Lazy Language Loader | 0.3.9 | [GitHub](https://github.com/ChachyDev/lazy-language-loader) |
| Lithium | 0.21.4+1.21.11 | [GitHub](https://github.com/CaffeineMC/lithium-fabric) |
| ModernFix | 5.26.3-build.2 | [GitHub](https://github.com/embeddedt/ModernFix) |
| More Culling | 1.6.2 | [GitHub](https://github.com/fxmorin/moreculling) |
| SignFaster | 1.0.3 | **Source unverified** |
| Simple Menu | 2.1 | [GitHub](https://github.com/Serilum/Simple-Menu) |
| ThreadTweak | 0.1.8+1.21.11 | [GitHub](https://github.com/getchoo/threadtweak) |
| Valkaw | 1.0.0 | [This repository](https://github.com/nlvtai25/valkaw-client) |
| WI Zoom | 1.7-MC1.21.11 | [GitHub](https://github.com/Wurst-Imperium/WI-Zoom) |
| ZenyFast | 1.0.0 | [Modrinth](https://modrinth.com/mod/zenyfast) |

### Sodium-only · 15 mods

| Mod | Version | Source |
|---|---:|---|
| Better Block Entities (BBE) | 1.3.4+1.21.11 | [GitHub](https://github.com/ceeden/betterblockentities) |
| Clumps | 29.0.0.1 | [GitHub](https://github.com/jaredlll08/Clumps) |
| Cubes Without Borders | 3.0.0-build.14+1.21.11 | [GitHub](https://github.com/Kir-Antipov/cubes-without-borders) |
| FastQuit | 3.1.3+1.21.11 | [GitHub](https://github.com/contariaa/FastQuit) |
| ImmediatelyFast | 1.14.3+1.21.11 | [GitHub](https://github.com/RaphiMC/ImmediatelyFast) |
| Krypton | 0.2.10 | [GitHub](https://github.com/astei/krypton) |
| Ksyxis | 1.4.6 | [GitHub](https://github.com/VidTu/Ksyxis) |
| Language Reload | 1.7.7+1.21.11 | [GitHub](https://github.com/Jerozgen/LanguageReload) |
| Not Enough FPS | 1.21.11-1.0.2 | [GitHub](https://github.com/Thedustbustr/Not-Enough-FPS) |
| Reese's Sodium Options | 2.2.6-beta.1+1.21.11 | [GitHub](https://github.com/FlashyReese/reeses-sodium-options) |
| Reflex AntiLag | 1.1.2 | [GitHub](https://github.com/Tythee/Minecraft-Reflex) |
| Sodium | 0.8.15-beta.1+1.21.11 | [GitHub](https://github.com/CaffeineMC/sodium) |
| Sodium Extra | 0.9.4+1.21.11 | [GitHub](https://github.com/FlashyReese/sodium-extra-fabric) |
| Sodium Shadowy Path Blocks | 6.0.0 | [GitHub](https://github.com/Rynnavinx/sodium-shadowy-path-blocks) |
| ThreatenGL | 2.0.4-release.10 | [GitHub](https://github.com/Richy-Z/ThreatenGL) |

### VulkanMod-only · 6 mods

| Mod | Version | Source |
|---|---:|---|
| Beryl | 0.2.1-alpha | [Modrinth](https://modrinth.com/mod/beryl) |
| Concurrent Chunk Management Engine (C2ME) | 0.4.0-alpha.0.28+1.21.11 | [GitHub](https://github.com/RelativityMC/C2ME-fabric) |
| Lomka | 0.7.0 | [GitHub](https://github.com/Starlevka/Lomka) |
| Not Enough Vulkan | 1.6.2+1.21.11 | [GitHub](https://github.com/amiralimollaei/not-enough-vulkan) |
| Optimized Block Entities | 1.1.52 | [GitHub](https://github.com/maDU59/OptimisedBlockEntities) |
| VulkanMod | 0.6.8+1.21.11 | [GitHub](https://github.com/xCollateral/VulkanMod) |

> **Attribution note:** The installed `SignFaster` JAR does not provide an author name, homepage, or source URL, and lists its license as `All-Rights-Reserved`. Its upstream source could not be verified; do not redistribute it separately without checking the author's terms.

## 🎨 Included resource packs

This is the resource pack present in both profile bundle JARs. Other packs from previous local installations are not included in the current bundles.

| Resource pack | Source |
|---|---|
| SapixCraft 32x r1.4 (Minecraft 1.21.11) | [SapixCraft on Modrinth](https://modrinth.com/resourcepack/sapixcraft) |
## 📝 Changelog

### Client v1.5

- Redesigned Client Settings as a compact app-style layout with a left navigation rail, header, and footer.
- Made the settings content responsive, with an adaptive module grid and consistent alignment for tabs, search, and footer controls.
- Removed the Transparent Buttons resource pack from both profiles.
- Switched distribution to single-JAR bundles; ZIP client packages are no longer supported.

### Launcher v1.6

- Updated package installation to accept JAR bundles only.
- Refined the launcher footer and changelog presentation.

### Client v1.4

- Repackaged Sodium and VulkanMod as single Fabric bundle JARs, each containing its profile's nested mods and defaults.
- Fixed HUD editor modifier-key handling, saved Auto Sprint across launches, and prevented Clear Fluid from mutating the copied fog color.

### Launcher v1.5

- Added **Online** and **Offline (Local JAR)** package sources; the launcher remembers the last local JAR folder and detects the profile from the selected bundle.
- Added first-launch setup for Minecraft 1.21.11, Fabric Loader, libraries, assets, and Java 21 when needed. The clean-install flow passed on Windows.

### v1.3

- Refreshed the profile window titles to identify the current client version.
- Removed a locally bundled cape image from both packages.
- Verified Packet Fixer and its configuration are absent from the Sodium and VulkanMod packages.

### v1.2

- Updated the Sodium and VulkanMod profile packages and refreshed their window titles.
- Kept Packet Fixer out of both profiles to avoid the previously identified conflict with Lomka.
- Removed stale Packet Fixer configuration from both packages.

### v1.1

- Identified and resolved a compatibility conflict between Packet Fixer and Lomka during testing.
- Removed Packet Fixer from the distributed client packages.

### v1.0.1

- Fixed technical issues discovered during testing.
- Added more thorough fixes to address underlying causes and resolve issues carried over from earlier versions.
- Refined Valkaw Launcher for a lightweight, user-friendly setup and update experience.

## 📜 Copyright and attribution

Valkaw Client is not affiliated with Mojang or Microsoft. Minecraft is a trademark of Mojang Studios/Microsoft. Each mod and resource pack remains the property of its respective author and is subject to its own license and redistribution terms. Links in this README provide attribution and point to original sources; they do not grant this repository permission to redistribute third-party files. See [LICENSE](LICENSE) for the license covering this repository's own content.

<div align="center">

**Play smoother. Make it yours.**
*Valkaw Client · Fabric 1.21.11*

</div>
