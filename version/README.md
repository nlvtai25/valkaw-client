# Built client artifacts

This folder holds only the **built client jars** (no source). Source lives in the
per-version folders (`1.21.11/`, `26.1.2/`, `26.2/`, `26.3/`).

## Naming

```
valkaw-<profile>-<game-ver>-<client-ver>.jar
```

- `profile` is the **game-folder directory name** — `sodium` or `vulkan`
  (not the launcher profile id `gl`/`vk`).
- `game-ver` / `client-ver` never contain a dash; the last dash is the only
  boundary between them.

Example: `valkaw-sodium-1.21.11-1.0.6.jar`.

Variant availability: `sodium` builds for every version; `vulkan` builds for
1.21.11 / 26.1.2 / 26.2 only (26.3 is sodium-only by design).

## How the launcher finds jars

Search order (most specific first), in `valkaw-launcher/core/artifact.go`:

1. `VALKAW_VERSION_DIR` env var, if set — replaces the search entirely.
2. `<launcher-data>/version`.
3. Every `version` subfolder found by walking up to 6 levels from the current
   working directory and from the launcher executable.

In each folder the **newest client version** matching the profile + game version
is picked. The chosen jar is copied into the launcher cache
(`~/.valkaw/launcher/clients/<game-ver>/<profile>-<client-ver>.jar`), removing
any legacy `<profile>.jar` in that folder.

These jars are gitignored (see `.gitignore`).
