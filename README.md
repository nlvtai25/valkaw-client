# Valkaw Client & Launcher

Valkaw is a lightweight Minecraft quality-of-life client built on Fabric, with its own launcher for Linux and Windows. It is a collection of **HUD, performance and utility modules** — no cheats or hacks.

## The client

A Fabric mod that ships as a ready-to-use jar for four Minecraft snapshots:

- `1.21.11`
- `26.1.2`
- `26.2`
- `26.3`

Two renderer profiles are available:

| Profile | Renderer | Versions |
| --- | --- | --- |
| Sodium | OpenGL | 1.21.11, 26.1.2, 26.2, 26.3 |
| VulkanMod | Vulkan | 1.21.11, 26.1.2, 26.2 |

Highlights: an in-game HUD editor, a custom Symbols/Color chat panel, a hand-written sci-fi main menu, a Hide Players module, and a pixel crosshair editor.

## The launcher

A Go + Wails desktop app for **Linux** and **Windows**. It picks the renderer, installs the profile, downloads the matching client jar, sets up Java 21 if needed, and launches the game — **offline-friendly**.

Pre-built launcher binaries live in `assets/downloads/`, tracked by `assets/downloads/latest.json` for automatic updates.

## Building

- Client: each snapshot folder has its own `README.md` (e.g. `1.21.11/README.md`).
- Launcher: `valkaw-launcher/` (Linux) and `valkaw-launcher-win/` (Windows).

Build everything from the repo root:

```bash
./scripts/build-all.sh            # all client snapshots + both launchers
./scripts/build-all.sh --clients  # client jars only
```

## Downloads

- Client jars are published as GitHub Releases — see **Releases → latest**.
- Launcher binaries live in `assets/downloads/`.

## License

This project is licensed under the **Apache License 2.0**. See the
[`LICENSE`](LICENSE) file for the full legal text.

- License: [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0)
- Copyright: © 2026 Confiac1563
