# Changelog

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
