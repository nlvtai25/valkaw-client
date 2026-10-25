# Local and Online Pack Source Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Ship client v1.4 single-JAR bundles and launcher v1.5 with equivalent Online (GitHub) and Offline (Local JAR) install flows on Windows and Linux.

**Architecture:** Reuse the current pack-install path on both launchers, adding one verified source choice before install. Use WinForms/Tk native file pickers and existing settings stores; validate/stage archive contents before touching game files. Build client JAR bundles from existing variant ZIP staging output, then replace Release only after all tests pass and archive the old Release directory.

**Tech Stack:** C# 5 / .NET Framework 4 WinForms, Python 3 standard library / Tk, Gradle Fabric Loom, PowerShell 5.1, `ZipArchive`/`zipfile`.

**Spec:** `Music\docs\superpowers\specs\2026-10-24-pack-source-design.md`

## Global Constraints

- Keep client Minecraft target at `1.21.11`; client becomes v1.4 and launcher becomes v1.5.
- Release asset names: `Sodium_v1.4.jar`, `Vulkan_v1.4.jar`, `Valkaw.Launcher-1.5.exe`, `Valkaw.Launcher-linux-1.5.py`.
- Source defaults to Online on each launcher start; persist only last picker directory, separately by OS; selected local JAR is session-only.
- Offline skips pack download only; existing Minecraft/Fabric/runtime/assets setup may still need network.
- Local bundle JARs must match selected Sodium/Vulkan id and `depends.minecraft=1.21.11`.
- Extract only `config/`, `resourcepacks/`, and root `options.txt`; reject traversal and never overwrite existing user config/options.
- Preserve unrelated mods/saves and the selected local JAR. Validate/stage before replacing the managed bundle or writing markers.
- Keep Linux launcher one stdlib Python file; do not upload a GitHub release.
- Move the old `Music\Release` directory to a timestamped backup outside Release before creating the new four-file Release.
- `Music` is not a Git worktree; do not add commit steps.

## File Map

- `Music\shared\src\main\java\mc\client\standalone\ClientMod.java` — Right Shift edge detection and persistent Auto Sprint toggle.
- `Music\shared\src\main\java\mc\client\standalone\MinecraftGame.java` — Auto Sprint state/comment.
- `Music\shared\src\main\java\mc\client\standalone\Keybinds.java` and `mixin\GameOptionsKeysMixin.java` — key registration before option loading, idempotently.
- `Music\shared\src\main\java\mc\client\hud\HudConfig.java` — persist Auto Sprint.
- `Music\shared\src\main\java\mc\client\mixin\ClearFluidMixin.java` — return a copied fog color.
- `Music\client_sodium\Build-Release.ps1`, `Music\client_vulkanmod\Build-Release.ps1` — versioned source ZIP staging.
- New `Music\tools\make-bundle.ps1` — turn a source pack ZIP into a validated Fabric wrapper JAR.
- `Music\launcher\Launcher.cs` — Windows source selector, dialog, bundle validation, safe install, self-tests.
- `Music\launcher\build-launcher.ps1` — reference built-in `System.Web.Extensions` JSON parser, build versioned exe with changelog resource, and accept an output Release directory for staging.
- `Music\Build-LinuxRelease.ps1` — accept the same output Release directory so Linux asset can be staged before replacing Release.
- `Music\launcher\linux\valkaw.py` — Linux source selector, dialog, bundle validation, safe install, self-tests, shared changelog source.
- `Music\client_{sodium,vulkanmod}\client\config\simplemenu.json5` and the installed `%APPDATA%\.valkaw\game{,_vulkan}\config\simplemenu.json5` — bump visible title to client v1.4.
- `Music\AGENTS.md`, `Music\HANDOFF.md` — release naming/version record and handoff.
- `Music\Release` — final assets only, replaced at the last task after backup.

## Review Focus

1. A corrupt, wrong-variant, or wrong-Minecraft local JAR must fail before installed files change — Task 3/4 self-tests assert the old managed file and marker remain unchanged.
2. A malicious archive entry containing `..`, an absolute path, or an invalid separator must not write outside gameDir — Task 3/4 path-safety tests use temporary archives and assert no outside file appears.
3. A changed JAR with the same filename must reinstall, while the source JAR remains intact — Task 3/4 self-tests compare content-hash markers and source-file existence.
4. Offline must not call the pack network selector/download; legacy Online ZIP and same-version JAR-over-ZIP selection must still work — Task 3/4 source-selection self-tests assert both paths.
5. Cancelling or reselecting Offline must preserve/reopen the right state and last OS-specific directory — Task 3/4 picker-state tests assert first-use Downloads fallback, last-dir reuse, and cancel behavior.

---

### Task 1: Port tested client fixes and bump client version

**Files:**
- Modify the six shared Java files listed in File Map.
- Modify both variant `Build-Release.ps1` scripts, both variant `simplemenu.json5`, installed game config titles, and `Music\AGENTS.md` version header/log.

**Interfaces:**
- `ClientMod` loads `HudConfig.autoSprint` once after HUD config initialization; Z toggles and saves through the existing `CustomHudOverlay.save()` path.
- `GameOptionsKeysMixin` injects at `load` HEAD; `Keybinds.appendAll(KeyBinding[])` is idempotent.

- [ ] Port the tested TESTING diffs to the six shared Java files; keep the existing package/mod ids and do not touch unrelated audit findings.
- [ ] Bump versioned pack staging names, both source title configs, both installed game title configs, and `AGENTS.md` current version/log to client v1.4.
- [ ] Build Sodium and Vulkan with `cd Music\client_sodium; .\gradlew.bat build --console=plain` and the equivalent Vulkan command; both must exit 0.

### Task 2: Produce versioned, self-describing bundle JARs

**Files:**
- Create `Music\tools\make-bundle.ps1` from the tested packer, extending outer Fabric metadata with `depends.minecraft=1.21.11`.
- Modify the two variant `Build-Release.ps1` files only to use v1.4 intermediate names; pass a staging `OutDir` outside final Release.

**Interfaces:**
- Packer inputs: source ZIP, output JAR path, Fabric id (`valkaw-pack-sodium` or `valkaw-pack-vulkan`), version `1.4`, name, extras `config,resourcepacks`, include `options.txt`.
- Outer manifest validation: id, version, Minecraft dependency, and each declared `jars[].file` exists exactly once under `mods/`.

- [ ] Add the tested ZIP-to-JAR builder and ensure it preserves existing skin/cape image exclusions from the source ZIP.
- [ ] Run each variant Build-Release script with a temporary `OutDir` and v1.4 output name.
- [ ] Bundle both staging ZIPs to `Music\_release-staging\Sodium_v1.4.jar` and `Vulkan_v1.4.jar`; do not touch current `Music\Release` yet.
- [ ] Inspect both manifests and assert every declared nested JAR exists, variant ids differ correctly, MC dependency is `1.21.11`, extras are limited to the approved paths, and no image exists under `config/{skins-local,capes-local,adaptivecapes}`.

### Task 3: Implement Linux Online/Offline source and safe pack install

**Files:**
- Modify `Music\launcher\linux\valkaw.py`, `Music\Build-LinuxRelease.ps1`.

**Interfaces:**
- `pick_pack(variant)` returns the best Online JAR or legacy ZIP using version and JAR tie precedence.
- `verify_bundle(path, variant, mc_version)` returns parsed metadata or raises before gameDir changes.
- `ensure_pack(gdir, variant, local_jar=None)` uses `None` for Online and a validated selected path for Offline.
- Settings key: `offline.lastdir.linux`; session state defaults Online and holds selected JAR only until process exit.

- [ ] Extend `--self-test` with JAR/ZIP tie selection, wrong variant/MC, corrupt/missing JAR, missing nested entries, duplicate entries, and traversal archive cases; assert failure leaves gameDir unchanged.
- [ ] Run `--self-test` before implementation to confirm the new cases fail for missing behavior.
- [ ] Bump `APP_VERSION` to 1.5, `CLIENT_VERSION` to 1.4, and add the new placeholder changelog block at the top.
- [ ] Add Source to the existing footer left of Client/Version; use regular font for Source and Client selectors.
- [ ] Use `filedialog.askopenfilename` with JAR filter and `~/Downloads` fallback; persist/reuse `offline.lastdir.linux`; repeated Offline selection reopens, cancel preserves prior valid selection or returns Online.
- [ ] Implement strict JSON/path validation, stage safe extras, hash marker identity, preserve user files, and never delete selected local source.
- [ ] Make `build_run` pass selected local JAR only for Offline; runtime/asset setup stays unchanged.
- [ ] From `Music`, run `python3 -m py_compile launcher\linux\valkaw.py`, `python3 launcher\linux\valkaw.py --self-test`, and `Build-LinuxRelease.ps1 -ReleaseDir .\_release-staging`; verify `Valkaw.Launcher-linux-1.5.py` lands in staging.

### Task 4: Implement Windows Online/Offline source and safe pack install

**Files:**
- Modify `Music\launcher\Launcher.cs`, `Music\launcher\build-launcher.ps1`.

**Interfaces:**
- Keep `PickPack(root, variant)` for Online assets; recognize `.jar` and legacy `.zip`, choose highest pack version, and prefer JAR at equal version.
- `VerifyBundle(path, variant, mcVersion)` returns validated metadata or throws before gameDir changes.
- Parse the small outer manifest with .NET Framework's built-in `JavaScriptSerializer`; add `/r:System.Web.Extensions.dll` to `build-launcher.ps1` (no package dependency).
- `EnsurePack(root, gameDir, variant, localJar)` uses `localJar == null` for Online and a validated selected path for Offline.
- Settings key: `offline.lastdir.windows`; session fields: `sourceMode`, `offlineJar`.

- [ ] Extend `--self-test` with asset, manifest, variant/MC, missing/corrupt, declared-entry, traversal, and no-write-on-failure cases; run it before implementation to verify the new cases fail.
- [ ] Bump `AppVersion` to 1.5 and `ClientVersion` to 1.4.
- [ ] Add Source selector left of Version at the existing footer position; use regular font for Source and Version only; preserve action button emphasis.
- [ ] Add Online/Offline picker behavior using WinForms `OpenFileDialog`, initial `Downloads` then `offline.lastdir.windows`; repeated Offline opens the dialog; cancellation retains a prior valid local selection or returns Online.
- [ ] Implement strict JSON/path validation, stage safe extras, hash marker identity, preserve user files, and never delete a selected local source file.
- [ ] Make Play call `EnsurePack` with selected source; show a clear error and restore Play state on invalid/missing local file without silent Online fallback.
- [ ] From `Music\launcher`, run the exe `--self-test` and `build-launcher.ps1 -ReleaseDir ..\_release-staging` after Linux changelog is updated; verify embedded changelog and `Valkaw.Launcher-1.5.exe` lands in staging.

### Task 5: End-to-end verify and replace Release

**Files:**
- Replace contents of `Music\Release` only after creating timestamped archive backup outside that folder.
- Update `Music\HANDOFF.md` with newest section.

- [ ] Run both variant builds and bundle validations from Task 2 plus both launcher release builds; verify exactly four release assets are staged.
- [ ] Windows manual test: Online legacy ZIP fallback; synthetic self-test for new JAR asset precedence; Offline local JAR first-use Downloads, remember folder, cancel/reselect, wrong-variant rejection, one-JAR install, config preservation, and Play launch.
- [ ] In both game variants, recheck Right Shift editor, Z Auto Sprint and persistence after restart, F4, and Clear Fluid in water/lava where a test location is available; use only a focus-guarded harness or manual input to avoid sending keys to another app.
- [ ] Linux manual GUI test where available; otherwise retain Python syntax/self-test evidence and record that GUI/runtime verification was unavailable.
- [ ] Check logs for 96 Sodium / 123 Vulkan mod load counts, correct v1.4 titles, no mixin failure, no Java exception, and no files outside gameDir after malicious-archive tests.
- [ ] Archive the existing `Music\Release` directory intact, create clean Release, and move exactly `Sodium_v1.4.jar`, `Vulkan_v1.4.jar`, `Valkaw.Launcher-1.5.exe`, `Valkaw.Launcher-linux-1.5.py` from staging.
- [ ] Verify new files and backup exist; do not upload to GitHub.

## Implementation method

The source/version work and packer are shared prerequisites; Linux changelog must be updated before the Windows exe embeds it; final Release staging depends on all prior checks. Do tasks 1–2 first, then Linux (Task 3), Windows (Task 4), then Task 5. Because the UI is owned by a designer and separate implementations can be reviewed independently, use subagent-driven implementation for the two launcher files only if their write scopes remain disjoint; otherwise implement sequentially and run one cross-platform review at the end.
