<div align="center">

# ⚡ VALKAW CLIENT

### Minecraft 1.21.11 · Fabric · Performance-first · Custom HUD

A lightweight Minecraft client for players who want to get into the game quickly, tune performance to their system, and arrange their HUD their way.

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-55a630?style=for-the-badge&logo=minecraft&logoColor=white)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Loader-Fabric-dbd0b4?style=for-the-badge)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-21%2B-ed8b00?style=for-the-badge&logo=openjdk&logoColor=white)](https://adoptium.net/)

<br>

[⬇️ **DOWNLOAD VALKAW LAUNCHER**](downloads/Valkaw%20Launcher.exe)　·　[📦 Mods](#mods)　·　[🧩 Resource packs](#resource-packs)　·　[🚀 Getting started](#getting-started)

</div>

---

## ✨ The Valkaw experience

| ⚡ Performance | 🎛️ Your settings |
|---|---|
| A Fabric mod collection focused on rendering, memory, networking, and resource-loading time. | Choose Sodium or VulkanMod, allocate RAM in 0.25 GB increments, and add JVM arguments if needed. |
| 🧭 **Valkaw HUD** | 🪄 **In-game utilities** |
| Valkaw includes a HUD editor and widgets for system information, armor, biome, hitboxes, quick chat, and overlays. | Gamma/brightness controls, zoom, a customizable menu, reduced background activity, and other utilities are listed in the mods section. |
| 🖥️ **Windows launcher** | 🧰 **Separate profiles** |
| Light/dark themes, username lists, a system tray, launch status, and error logs. | Sodium and VulkanMod use separate game directories to avoid overwriting each other's mods and configurations. |

> **Designed for Minecraft Java 1.21.11 on Windows.** Valkaw Launcher requires Java 21 or later and Minecraft libraries/assets available locally or in a `minecraft` folder next to the launcher.

## ⬇️ Launcher downloads

> The launcher is stored **directly in this repository**, not in a GitHub Release.

<div align="center">

### [Download Valkaw Launcher.exe](downloads/Valkaw%20Launcher.exe)

`downloads/Valkaw Launcher.exe` · Windows · approximately 0.6 MB

SHA-256: `DF845346F7DDE96AAF39B94B598F00034972F302AC50C77912111FE69CAF072E`

> **Windows note:** The launcher is currently unsigned, so Windows may show a warning when you open it.

</div>

<a id="getting-started"></a>
## 🚀 Getting started

1. Download the launcher above and open `Valkaw Launcher.exe`.
2. Choose a player name and the **Sodium** or **VulkanMod** profile.
3. Make sure Java 21 or later is installed; adjust the RAM in the **RAM** tab if needed.
4. Click **Play**. If the game encounters an error, check `log_launcher/latest.log` in the launcher folder.

**Authentication note:** The launcher currently starts the game with an offline username and a fixed UUID; it does not support Microsoft/Mojang sign-in. Some servers require an authenticated account and will not allow this client to connect. Use an account and launcher supported by the server. Do not enter account credentials into this launcher.

### Launcher options

| Option | Description |
|---|---|
| RAM | 1–16 GB in 0.25 GB increments; 6 GB by default. Only allocate as much memory as your system can spare for the game. |
| Profile | Sodium or VulkanMod; each profile uses a separate game directory. |
| JVM Args | Add extra JVM arguments, separated by spaces. |
| Priority | Normal, High, or Realtime; higher priority may require Administrator privileges. |
| After run | Hide to the system tray, keep the launcher open, or close it when the game exits. |
| Game Dir | Choose a custom directory; leave blank to use `%AppData%\.valkaw\game`. |
| Appearance | Switch between Light and Dark themes; the launcher can also minimize to the system tray. |

<a id="mods"></a>
## 🧩 Mods & sources

The list below was collected from the existing client `mods` folder. Versions are taken from each mod's metadata; links point to upstream project pages or source repositories published by the authors.

| Mod | Version | Description | Source |
|---|---:|---|---|
| Architectury API | 19.0.1 | A cross-platform modding API and dependency library. | [GitHub](https://github.com/architectury/architectury-api) |
| BBE (Better Block Entities) | 1.3.4 | Optimizes and customizes block entity rendering. | [GitHub](https://github.com/ceeden/betterblockentities) |
| Cloth Config | 21.11.153 | An API for mod configuration screens. | [GitHub](https://github.com/shedaniel/ClothConfig) |
| Collective | 8.32 | A shared library used by Serilum's mods. | [GitHub](https://github.com/Serilum/Collective) |
| Dynamic FPS | 3.11.6 | Reduces resource usage while the game is in the background or idle. | [GitHub](https://github.com/juliand665/Dynamic-FPS) |
| EntityCulling | 1.11.3 | Hides entities and block entities that are not visible to reduce rendering work. | [GitHub](https://github.com/tr7zw/EntityCulling) |
| Fabric API | 0.141.6+1.21.11 | Core APIs and compatibility hooks for Fabric. | [GitHub](https://github.com/FabricMC/fabric) |
| Fabric Language Kotlin | 1.14.2 (Kotlin 2.4.21) | Runs Kotlin-based mods on Fabric. | [GitHub](https://github.com/FabricMC/fabric-language-kotlin) |
| Fast IP Ping | 1.0.12 | Skips slow reverse DNS lookups for literal server IP addresses. | [GitHub](https://github.com/Fallen-Breath/fast-ip-ping) |
| FerriteCore | 8.2.0 | Reduces memory usage. | [GitHub](https://github.com/malte0811/FerriteCore) |
| Gamma Utils | 2.5.10 | Adjusts gamma/brightness and night vision. | [GitHub](https://github.com/Sjouwer/gamma-utils) |
| ImmediatelyFast | 1.14.3 | Optimizes immediate-mode rendering. | [GitHub](https://github.com/RaphiMC/ImmediatelyFast) |
| Krypton | 0.2.10 | Optimizes Minecraft's networking stack and entity tracker. | [GitHub](https://github.com/astei/krypton) |
| Lazy Language Loader | 0.3.9 | Loads only the language resources needed during reloads. | [GitHub](https://github.com/ChachyDev/lazy-language-loader) |
| Lithium | 0.21.4+1.21.11 | Optimizes a range of game logic. | [GitHub](https://github.com/CaffeineMC/lithium-fabric) |
| Lomka | 0.7.0 | A collection of small Minecraft optimizations. | [GitHub](https://github.com/Starlevka/Lomka) |
| Valkaw | 1.0.0 | Valkaw's own client mod with HUD widgets and optimizations for 1.21.11. | [Repository](https://github.com/nlvtai25/valkaw-client) |
| ModernFix | 5.26.3-build.2 | A range of performance improvements and bug fixes. | [GitHub](https://github.com/embeddedt/ModernFix) |
| Mod Menu | 17.0.1 | Displays the installed mod list in-game. | [GitHub](https://github.com/TerraformersMC/ModMenu) |
| More Culling | 1.6.2 | Adds additional culling optimizations. | [GitHub](https://github.com/fxmorin/moreculling) |
| Not Enough FPS | 1.21.11-1.0.2 | Raises Minecraft's FPS limit. | [GitHub](https://github.com/Thedustbustr/Not-Enough-FPS) |
| Reese's Sodium Options | 2.2.6-beta.1 | Replaces Sodium's options screen. | [GitHub](https://github.com/FlashyReese/reeses-sodium-options) |
| Reflex AntiLag | 1.1.2 | Integrates NVIDIA Reflex to reduce rendering latency. | [GitHub](https://github.com/Tythee/Minecraft-Reflex) |
| SignFaster | 1.0.3 | Sign editing/rendering mod; the JAR does not provide a description. | **Unverified** |
| Simple Menu | 2.1 | Customizes the main-menu and game-window appearance. | [GitHub](https://github.com/Serilum/Simple-Menu) |
| Sodium | 0.8.15-beta.1+1.21.11 | A rendering engine designed to improve frame rates and reduce micro-stutter. | [GitHub](https://github.com/CaffeineMC/sodium) |
| Sodium Extra | 0.9.4+1.21.11 | Adds extra options for Sodium. | [GitHub](https://github.com/FlashyReese/sodium-extra-fabric) |
| Sodium Shadowy Path Blocks | 6.0.0 | Restores vanilla-style shading for certain blocks when using Sodium. | [GitHub](https://github.com/Rynnavinx/sodium-shadowy-path-blocks) |
| ThreatenGL | 2.0.4-release.10 | Allows Minecraft to use a different OpenGL version. | [GitHub](https://github.com/Richy-Z/ThreatenGL) |
| WI Zoom | 1.7-MC1.21.11 | A standalone zoom feature. | [GitHub](https://github.com/Wurst-Imperium/WI-Zoom) |
| ZenyFast | 1.0.0 | Speeds up resource/data-pack loading and removes the post-loading overlay fade-out. | [Modrinth](https://modrinth.com/mod/zenyfast) |

> **Source needs verification:** `SignFaster` 1.0.3 provides no author name, homepage, or source URL in its `fabric.mod.json`; its metadata lists the license as `All-Rights-Reserved`. Do not redistribute this mod without first verifying its author and terms. Add its upstream link once that information is available.

<a id="resource-packs"></a>
## 🎨 Resource packs

The packs below were found in the client's resource-pack folder. The two marked **Enabled** match the `resourcePacks` list in the game configuration; the other two are installed but not selected. This repository links to the authors' pages and does not redistribute third-party textures.

| Resource pack | Status | Source |
|---|---|---|
| SapixCraft 32x r1.4 (1.21.11) | Enabled | [SapixCraft on Modrinth](https://modrinth.com/resourcepack/sapixcraft) |
| Transparent Buttons | Enabled | [Project on Modrinth](https://modrinth.com/resourcepack/transparent-buttons) |
| Excalibur V26.3 | Installed · not enabled | [Maffhew's Excalibur on Modrinth](https://modrinth.com/resourcepack/excal) |
| Excalibur — Clarent Edition | Installed · not enabled | [GrandPappyJay's Clarent on Modrinth](https://modrinth.com/resourcepack/clarent) |

## 📜 Copyright & attribution

Valkaw Client is not affiliated with Mojang or Microsoft. Minecraft is a trademark of Mojang Studios/Microsoft. Each mod and resource pack remains the property of its respective author and is subject to its own license and redistribution terms. The links in this README provide attribution and direct users to the original sources; they do not grant this repository permission to redistribute those files. See [LICENSE](LICENSE) for the license covering this repository's own content.

<div align="center">

**Play smoother. Make it yours.**
*Valkaw Client · Fabric 1.21.11*

</div>
