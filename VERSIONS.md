# Version snapshots

Local source-only copies of the client, one folder per Minecraft version. They are
working snapshots, not release branches: the canonical history stays in
`valkaw-client/` and in the 26.2 port workspace.

| Folder | Minecraft | Variants | State | Source of the copy |
| --- | --- | --- | --- | --- |
| `1.21.11/` | 1.21.11 | sodium, vulkan | builds | `valkaw-client/` |
| `26.1.2/` | 26.1.2 | sodium, vulkan | builds (API port) | `../26.2/valkaw-client/` |
| `26.2/` | 26.2 | sodium, vulkan | builds (full API port) | `../26.2_TEST/valkaw-client/` |
| `26.3/` | 26.3 | sodium only | builds (SDL input port) | `../26.2/valkaw-client/` |

Each folder contains `valkaw-client/` (source only, no `build/`, `.gradle/`,
`run/`, `logs/`) plus a README with the exact build command for that version.

## Scope — client only

These folders hold the **Fabric client** and nothing else. The rest of the ecosystem
stays at the repo root because it is version-agnostic:

| Path | Why it is not duplicated per version |
| --- | --- |
| `valkaw-launcher/` + `core/` | Go/Wails shell; ships one binary that selects a profile, it does not embed client code |
| `valkaw-launcher-win/` | thin Windows wrapper around the same `core/` |
| `Website/` | marketing site; reads `assets/downloads/latest.json`, never the client source |

The contract between the branches is the built jar: the client is published into
`~/.valkaw/<profile>/mods/valkawclient.jar`, and the launcher points each profile at
its own `mods/` folder. Adding a Minecraft version therefore never touches the
launcher or the site — it adds a folder here and a jar in `assets/downloads/`.

## Artifacts

Builds land in `version/` as `valkaw-<profile>-<game-ver>-<client-ver>.jar`:

```bash
./scripts/build-version.sh 1.21.11                  # sodium
./scripts/build-version.sh 26.1.2 --profiles all    # sodium + vulkan
./scripts/build-version.sh 26.2 --profiles all      # sodium + vulkan
./scripts/build-version.sh 26.3                     # sodium (26.3 has no Vulkan port)
```

The script resolves the snapshot, reads `version=` from its `gradle.properties`,
picks `remapJar` (1.21.x) or `jar` (26.x), and copies the result into `version/`.
The launcher reads that folder directly (see `core/artifact.go`), so a build is
runnable without a GitHub release; see `version/README.md` for the naming grammar
and the resolution order.

These folders are git-ignored: they duplicate source that already lives in the
repo and in the port workspace.
