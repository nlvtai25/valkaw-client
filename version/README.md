# Built artifacts

Flat registry of client jars, one file per (profile × game version × client version).
Source snapshots live one level up (`../1.21.11/`, `../26.2/`, ...); this folder only
ever holds build output.

## Naming

```
valkaw-<profile>-<game-ver>-<client-ver>.jar
```

| Part | Values | Source |
| --- | --- | --- |
| `profile` | `sodium` (OpenGL) · `vulkan` (VulkanMod) | `-Pvalkaw_variant=<profile>` |
| `game-ver` | `1.21.11` · `26.1.2` · `26.2` · `26.3` | snapshot folder name |
| `client-ver` | `1.0.6` and up | `version=` in that snapshot's `gradle.properties` |

Examples:

```
valkaw-sodium-1.21.11-1.0.6.jar
valkaw-vulkan-1.21.11-1.0.6.jar
valkaw-sodium-26.2-1.0.6.jar
```

Sorting is `profile` → `game` → `client`, so `ls` groups a profile together and the
game version reads left to right. Matching is by `-`, never by splitting on `.`,
because `game-ver` and `client-ver` both contain dots.

## Producing a jar

```bash
./scripts/build-version.sh 1.21.11                    # sodium only
./scripts/build-version.sh 26.2 --profiles sodium vulkan
./scripts/build-version.sh 1.21.11 --profiles all --clean
```

The script reads the client version from the snapshot's `gradle.properties`, picks
`remapJar` for 1.21.x or `jar` for 26.x, builds, and copies the result here with the
name above. Nothing else writes to this folder.

## How the launcher consumes this folder

`valkaw-launcher/core/artifact.go` resolves a build for the selected game version and
profile before every install:

1. `VALKAW_VERSION_DIR` if set, otherwise the launcher data dir (`~/.valkaw/launcher/version`)
   and every `version/` folder found by walking up from the working directory and from
   the launcher executable — this is what makes a repo `MinecraftClient/version/` get
   picked up with no release step;
2. newest `valkaw-<profile>-<game-ver>-<client-ver>.jar` for that game version wins
   (dotted numeric compare, so `1.0.10` beats `1.0.9`);
3. the jar is copied into `<launcher-dir>/clients/<game-ver>/` under the same name, and
   GitHub releases remain the fallback when no local build exists.

The copy is skipped when the cached file already has the same size, so a launch after
the first one costs a `stat`, not a 15 MB copy. Legacy `clients/<game-ver>/<profile>.jar`
files are still read, and are deleted once a new-name jar lands.

The profile pills follow what this folder actually holds: a version with no Vulkan jar
(a local build such as 26.3) disables the VulkanMod pill for that version, and
`InstallClient` refuses it instead of failing at the download step. The always-offered
base versions (1.21.11 and 26.2) keep both profiles, because their builds come from a
release.

Because the launcher now finds builds here, `env` is the only knob needed for a
non-standard checkout:

```bash
VALKAW_VERSION_DIR=/path/to/MinecraftClient/version ./build/bin/valkaw-launcher
```

## Housekeeping

Jars here are build output and are git-ignored. Delete a jar here whenever its source
snapshot is rebuilt or removed — this folder is the single place to look for "what has
actually been built", so stale files defeat its purpose.
