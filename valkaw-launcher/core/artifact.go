package core

import (
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
)

// Client artifacts use one flat naming scheme everywhere:
//
//	valkaw-<profile>-<game-ver>-<client-ver>.jar
//
// e.g. valkaw-sodium-1.21.11-1.0.6.jar. profile is the directory name used in the
// game folder ("sodium" | "vulkan"), not the launcher profile id ("gl" | "vk").
const (
	artifactPrefix = "valkaw-"
	artifactSuffix = ".jar"
)

// artifactFileName builds the canonical name for a built client jar.
func artifactFileName(profile, gameVer, clientVer string) string {
	return artifactPrefix + profileDir(profile) + "-" + gameVer + "-" + clientVer + artifactSuffix
}

// parseArtifactFileName splits a canonical name into its three parts. It returns
// the profile directory name so callers can compare it with profileDir().
func parseArtifactFileName(name string) (profile, gameVer, clientVer string, ok bool) {
	if !strings.HasPrefix(name, artifactPrefix) || !strings.HasSuffix(name, artifactSuffix) {
		return "", "", "", false
	}
	rest := strings.TrimSuffix(strings.TrimPrefix(name, artifactPrefix), artifactSuffix)
	for _, p := range []string{"sodium", "vulkan"} {
		if !strings.HasPrefix(rest, p+"-") {
			continue
		}
		rem := rest[len(p)+1:]
		// Game versions never contain a dash, client versions never do either, so
		// the last dash is the only boundary between them.
		i := strings.LastIndex(rem, "-")
		if i <= 0 || i == len(rem)-1 {
			return "", "", "", false
		}
		return p, rem[:i], rem[i+1:], true
	}
	return "", "", "", false
}

// newestArtifact returns the highest-versioned jar in dir matching profile and
// game version.
func newestArtifact(dir, profile, gameVer string) (path, clientVer string, ok bool) {
	entries, err := os.ReadDir(dir)
	if err != nil {
		return "", "", false
	}
	for _, e := range entries {
		if e.IsDir() {
			continue
		}
		p, g, c, valid := parseArtifactFileName(e.Name())
		if !valid || p != profile || g != gameVer {
			continue
		}
		if !ok || newerVersion(c, clientVer) {
			path, clientVer, ok = filepath.Join(dir, e.Name()), c, true
		}
	}
	return path, clientVer, ok
}

// clientSourceDirs lists the folders searched for built jars, most specific first.
// VALKAW_VERSION_DIR replaces the search path entirely; otherwise the launcher data
// dir and every "version" folder found by walking up from the working directory and
// from the executable are used.
func clientSourceDirs() []string {
	if dir := strings.TrimSpace(os.Getenv("VALKAW_VERSION_DIR")); dir != "" {
		if abs, err := filepath.Abs(dir); err == nil {
			return []string{abs}
		}
		return []string{dir}
	}

	var dirs []string
	seen := map[string]bool{}
	add := func(dir string) {
		abs, err := filepath.Abs(dir)
		if err != nil || seen[abs] {
			return
		}
		seen[abs] = true
		dirs = append(dirs, abs)
	}

	add(filepath.Join(launcherDir(), "version"))

	roots := []string{}
	if wd, err := os.Getwd(); err == nil {
		roots = append(roots, wd)
	}
	if exe, err := os.Executable(); err == nil {
		roots = append(roots, filepath.Dir(exe))
	}
	for _, root := range roots {
		dir := root
		for depth := 0; depth < 6; depth++ {
			add(filepath.Join(dir, "version"))
			parent := filepath.Dir(dir)
			if parent == dir {
				break
			}
			dir = parent
		}
	}
	return dirs
}

// findSourceClientJar returns the newest built jar for a game version and profile
// from the source folders.
func findSourceClientJar(gameVer, profile string) (path, clientVer string, ok bool) {
	want := profileDir(profile)
	for _, dir := range clientSourceDirs() {
		p, c, found := newestArtifact(dir, want, gameVer)
		if found {
			return p, c, true
		}
	}
	return "", "", false
}

// builtGameVersions lists the game versions that already have a built jar in one
// of the source folders.
func builtGameVersions() []string {
	seen := map[string]bool{}
	var out []string
	for _, dir := range clientSourceDirs() {
		entries, err := os.ReadDir(dir)
		if err != nil {
			continue
		}
		for _, e := range entries {
			if e.IsDir() {
				continue
			}
			_, game, _, ok := parseArtifactFileName(e.Name())
			if !ok || seen[game] {
				continue
			}
			seen[game] = true
			out = append(out, game)
		}
	}
	return out
}

// installLocalClientJar copies a built jar from a source folder into the launcher
// cache. It returns the cached path, or "" when no matching build is available.
func installLocalClientJar(gameVer, profile string) (string, error) {
	src, clientVer, ok := findSourceClientJar(gameVer, profile)
	if !ok {
		return "", nil
	}
	dest := filepath.Join(clientCacheDir(), gameVer, artifactFileName(profile, gameVer, clientVer))
	if sameSize(src, dest) {
		return dest, nil
	}
	if err := os.MkdirAll(filepath.Dir(dest), 0755); err != nil {
		return "", err
	}
	if err := copyFileAtomic(src, dest); err != nil {
		return "", err
	}
	if legacy := filepath.Join(clientCacheDir(), gameVer, profileDir(profile)+".jar"); legacy != dest {
		_ = os.Remove(legacy)
	}
	return dest, nil
}

func sameSize(a, b string) bool {
	sa, err := os.Stat(a)
	if err != nil {
		return false
	}
	sb, err := os.Stat(b)
	if err != nil {
		return false
	}
	return sa.Size() == sb.Size()
}

// copyFileAtomic copies src to dest through a temporary file so an interrupted
// copy never leaves a truncated jar behind.
func copyFileAtomic(src, dest string) error {
	in, err := os.Open(src)
	if err != nil {
		return err
	}
	defer in.Close()

	tmp := dest + ".new"
	out, err := os.Create(tmp)
	if err != nil {
		return err
	}
	if _, err := io.Copy(out, in); err != nil {
		out.Close()
		_ = os.Remove(tmp)
		return err
	}
	if err := out.Close(); err != nil {
		_ = os.Remove(tmp)
		return err
	}
	if err := os.Rename(tmp, dest); err != nil {
		_ = os.Remove(tmp)
		return fmt.Errorf("move %s: %w", filepath.Base(dest), err)
	}
	return nil
}
