# Pack source and release promotion

## Intent

Promote the tested one-file Fabric pack bundles into `Music\Release` and let both production
launchers install them either from GitHub Releases or from a local JAR selected by the user.
This is pack-source selection, not a second Minecraft runtime or a replacement for the vanilla
Minecraft client JAR.

## Approved scope

- Production Windows launcher (`launcher\Launcher.cs`) and Linux launcher
  (`launcher\linux\valkaw.py`) get matching Source choices:
  - **Online (GitHub)**: current latest-release lookup/download behavior, extended to `.jar`;
    still accept old `.zip` assets while they remain on Releases. Choose the highest pack version,
    preferring `.jar` over `.zip` at the same version.
  - **Offline (Local)**: selecting it opens a native file chooser filtered to `*.jar`. First folder
    is the user's `Downloads`; later opens start at the most recently selected directory. Selecting
    only chooses the source file; installation happens when Play is pressed.
- The selected file is a Valkaw outer Fabric bundle (`fabric.mod.json`, nested `mods/*.jar`, and
  optional `config/`, `resourcepacks/`, `options.txt`). Install it as the single pack JAR in the
  game's `mods/`, extracting only the existing pack extras. Do not replace the versioned Minecraft
  client JAR. Keep the chosen source file intact.
- If the picker is cancelled, retain a previously valid Offline selection; if none exists, return
  to Online. Selecting Offline again must reopen the picker even if Offline is already selected.
- Source defaults to Online each launcher start. The selected JAR path is session-only; persist
  the last directory separately per platform in the shared settings file so Windows paths do not
  overwrite Linux paths (and vice versa).
- Place Source immediately left of the current Version selector. Use regular font weight for both
  selection controls; keep existing action-button emphasis and the rest of the UI unchanged.
- Offline means **no network fetch for the pack JAR**. Missing Minecraft/Fabric/runtime/assets
  still follow the launcher's existing setup behavior and may require network access.
- Preserve the current single Minecraft target (`1.21.11`); do not implement multiple MC versions
  in this change. Provide a design recommendation separately.
- Carry the client fixes already tested in TESTING into `Music\shared\src`: load keybindings
  before `options.txt` parsing; edge-detect the Right Shift editor binding; persist Auto Sprint;
  return a copied fog-color vector. Bump client to v1.4 and launcher to v1.5, update titles/version
  log/changelog per `Music\AGENTS.md`.
- Archive the current `Music\Release` directory to a timestamped path outside `Release`, then
  create a clean release folder containing only `Sodium_v1.4.jar`, `Vulkan_v1.4.jar`,
  `Valkaw.Launcher-1.5.exe`, and `Valkaw.Launcher-linux-1.5.py`. Do not copy TESTING's `data/`,
  `launcher.dev`, or debug README.

## Safety and installation behavior

- Validate before deleting/replacing any managed mod or writing the completion marker. For a bundle
  JAR (Online or Offline), require a Fabric manifest identifying the selected Sodium/Vulkan variant,
  `depends.minecraft=1.21.11`, existing declared nested JARs, and at least one nested mod. New outer
  manifests include that Minecraft dependency so a JAR for a future game version cannot be
  accidentally installed into this fixed-version launcher. For legacy Online ZIPs only, validate
  the expected `mods/*.jar` and allowed extras; they have no outer Fabric manifest.
- Treat local JAR contents as untrusted archive input. Extract only `config/`, `resourcepacks/`, and
  root `options.txt`; reject absolute paths, `..`, invalid separators, and any canonical destination
  outside the selected game directory. Never extract the wrapper's `fabric.mod.json` or nested
  `mods/*` entries as loose files.
- Stage/verify first; replace the launcher-managed bundle and write its marker only after successful
  install. Preserve unrelated mods, saves, and existing user config. Online temp downloads are
  removed after use; a user-selected Offline JAR is never removed.
- For Offline selection, reject a missing, unreadable, corrupt, or wrong-variant JAR with a clear
  error; do not silently fall back to Online. If the cached pack marker is based on a local file,
  distinguish changed content even when the filename is unchanged (content hash is the call).

## Implementation shape

- Use WinForms `OpenFileDialog` on Windows and existing Tk `filedialog` on Linux; no dependency.
- Reuse current settings persistence and pack installation path (`EnsurePack` / `ensure_pack`) so
  Online and Offline share one verified extract/install routine.
- Build reproducibly: promote the already-tested `TESTING\tools\make-bundle.ps1` into `Music\tools`;
  each existing variant `Build-Release.ps1` produces its normal source ZIP in a staging directory,
  then the bundle script wraps it as the versioned JAR with variant id and Minecraft dependency.
  Staging ZIPs do not remain in final `Music\Release`.
- Add source/picker behavior to the Source control so choosing Offline again can re-open the file
  chooser; status text shows the selected JAR basename without adding another footer row.
- Keep the existing regular/normal theme; only remove bold from Source/Version selectors.
- Linux remains a single stdlib Python file. Keep changelog source in `valkaw.py`; build Windows
  changelog resource from it as before.

## Release / version surfaces

- Client: `CLIENT_VERSION` / `ClientVersion`, both variant config titles and the 2 installed game
  configs, version log, and JAR names become v1.4. Build both Sodium and Vulkan from Music source.
- Update both variant `Build-Release.ps1` versioned intermediate names to v1.4, and verify the
  bundle builder carries forward the existing no-personal-skin/cape-image packaging rule.
- Launcher: `APP_VERSION` / `AppVersion` become 1.5; add one new placeholder-based changelog block
  at the top; build versioned Windows and Linux assets.
- Keep prior Release assets in the archive backup; do not upload a GitHub release.

## Verification

- Build both client variants and verify resulting bundle JARs (manifest, selected variant, every
  nested JAR reference, Minecraft dependency, and allowed extra entries).
- Windows `--self-test`: latest asset picking, same-version JAR-over-ZIP preference, correct/wrong
  variant/MC mismatch, missing/corrupt local file, safe archive paths, changelog/version agreement.
- Linux `--self-test`: equivalent asset/source selection and manifest/path validation without GUI.
- Manual Windows GUI: Online download, Offline selection, first-open Downloads, remembered folder,
  reselect Offline, Cancel behavior, wrong-variant rejection, Play installing one JAR and preserving
  user files. Verify no pack network request in Offline mode.
- Linux: syntax/self-test, GUI chooser and same source-flow checks where a Linux runtime is available.
- Before replacing `Music\Release`, verify backups exist, exact four release assets are staged, and
  both pack variants boot to menu with the right titles/mod counts.

## Future Minecraft-version suggestion (not part of implementation)

When adding MC 26.2/26.3, keep Source independent. Evolve the current selector into three explicit
choices: **Minecraft** (`1.21.11`, `26.2`, `26.3`), **Client** (`Sodium`, `Vulkan`), and **Source**
(`Online`, `Offline`). Keep Source beside the game/client selectors rather than adding each MC
version as a Source option. The release pack lookup then matches the selected MC + client pair; do
not add the extra selector until a second MC version is actually supported.

## Builder calls (not user requirements)

- Source mode resets to Online each process launch; only the last chooser directory persists. This
  avoids silently using a stale local file after a restart.
- Cancel retains an existing valid Offline file selection in the current session; otherwise it
  reverts to Online.
- Release replacement is reversible by archiving the whole old directory, including its existing
  `backup/` subdirectory.
