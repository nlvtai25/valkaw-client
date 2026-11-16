package core

import (
	"archive/zip"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"slices"
	"strings"

	wailsruntime "github.com/wailsapp/wails/v2/pkg/runtime"
)

// InstallOffline lets the player install a client jar of their own (e.g.
// a copy they downloaded elsewhere) instead of the one fetched from the
// official release. The file is vetted before anything is copied into the
// cache: identity (fabric.mod.json id must be valkawclient), target
// Minecraft version, and a scan for tamper/virus indicators. Anything
// suspicious refuses to install and points the user back to the official
// source.
func (a *App) InstallOffline(profile string) string {
	if profile != "gl" && profile != "vk" {
		profile = "gl"
	}
	if !slices.Contains(supportedProfiles(a.gameVersion()), profile) {
		return fmt.Sprintf("error: no %s build for %s", profileDir(profile), a.gameVersion())
	}
	path, err := wailsruntime.OpenFileDialog(a.ctx, wailsruntime.OpenDialogOptions{
		Title: "Select a Valkaw client jar",
		Filters: []wailsruntime.FileFilter{{
			DisplayName: "Valkaw client jar",
			Pattern:     "*.jar",
		}},
	})
	if err != nil {
		return "error: " + err.Error()
	}
	if path == "" {
		return "canceled"
	}
	if msg := validateOfflineJar(path, a.gameVersion(), profile); msg != "" {
		return "error: " + msg
	}
	if !a.acquireInstallLock() {
		return "error: install already running"
	}
	defer a.releaseInstallLock()
	if msg := a.installOfflineJar(path, profile); msg != "" {
		return "error: " + msg
	}
	return a.installPipeline(profile)
}

// validateOfflineJar returns "" when the jar is clean, otherwise a
// user-facing reason the file is abnormal and should be re-downloaded from
// the official source.
func validateOfflineJar(path, gameVer, profile string) string {
	fi, err := os.Stat(path)
	if err != nil {
		return "cannot read file"
	}
	if fi.Size() < 1024 || fi.Size() > 2<<30 {
		return "file size out of range (expected a Minecraft client jar)"
	}
	zr, err := zip.OpenReader(path)
	if err != nil {
		return "not a valid jar/zip archive"
	}
	defer zr.Close()

	mod := readFabricMod(zr)
	if mod.ID != "valkawclient" {
		return "not a Valkaw client jar (fabric.mod.json id=" + mod.ID + ")"
	}
	if !versionEquals(mod.Minecraft, gameVer) {
		return "jar targets Minecraft " + mod.Minecraft + ", not " + gameVer
	}

	var totalUncompressed uint64
	for _, e := range zr.File {
		name := e.Name
		clean := filepath.ToSlash(name)
		if strings.Contains(clean, "../") || strings.Contains(clean, "..\\") ||
			strings.HasPrefix(clean, "/") || strings.HasPrefix(clean, "\\") ||
			strings.Contains(clean, ":") {
			return "suspicious path in archive: " + name
		}
		totalUncompressed += e.UncompressedSize64
		if totalUncompressed > 2<<30 {
			return "archive uncompresses too large (possible zip bomb)"
		}
		if suspiciousEntry(name) {
			return "suspicious file inside archive: " + name
		}
	}
	if len(zr.File) > 20000 {
		return "archive has an unusually large number of entries"
	}
	return ""
}

// suspiciousEntry flags filenames that should never appear in a Minecraft
// client jar: executables, scripts, shell/device artefacts, or anything
// that is clearly foreign to a Java mod.
func suspiciousEntry(name string) bool {
	lower := strings.ToLower(name)
	for _, ext := range []string{
		".exe", ".dll", ".so", ".dylib", ".bat", ".cmd", ".ps1", ".vbs",
		".sh", ".bin", ".elf", ".com", ".scr", ".pif", ".cpl", ".ocx",
		".sys", ".drv", ".msi", ".app", ".jarx",
	} {
		if strings.HasSuffix(lower, ext) {
			return true
		}
	}
	base := filepath.Base(lower)
	if base == "" || strings.HasPrefix(base, ".") || strings.HasPrefix(base, "~") {
		return base != ""
	}
	return false
}

func readFabricMod(zr *zip.ReadCloser) (m struct {
	ID        string
	Minecraft string
}) {
	for _, f := range zr.File {
		if f.Name != "fabric.mod.json" {
			continue
		}
		rc, err := f.Open()
		if err != nil {
			return
		}
		var mod struct {
			ID      string         `json:"id"`
			Depends map[string]any `json:"depends"`
		}
		if json.NewDecoder(rc).Decode(&mod) != nil {
			rc.Close()
			return
		}
		rc.Close()
		m.ID = mod.ID
		if mc, ok := mod.Depends["minecraft"].(string); ok {
			if i := strings.IndexAny(mc, "~^<>="); i >= 0 {
				mc = strings.TrimSpace(mc[i+1:])
			}
			m.Minecraft = strings.TrimSpace(mc)
		}
		return
	}
	return
}

// installOfflineJar copies a validated jar into the launcher cache under
// the canonical artifact name so downstream code treats it like any other
// build.
func (a *App) installOfflineJar(src, profile string) string {
	clientVer := "offline"
	dest := filepath.Join(clientCacheDir(), a.gameVersion(), artifactFileName(profile, a.gameVersion(), clientVer))
	if err := os.MkdirAll(filepath.Dir(dest), 0755); err != nil {
		return "error: " + err.Error()
	}
	if err := copyFileAtomic(src, dest); err != nil {
		return "error: " + err.Error()
	}
	if legacy := filepath.Join(clientCacheDir(), a.gameVersion(), profileDir(profile)+".jar"); legacy != dest {
		_ = os.Remove(legacy)
	}
	return "ok"
}
