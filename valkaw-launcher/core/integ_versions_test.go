package core

import (
	"path/filepath"
	"slices"
	"testing"
)

func TestGetVersionsIncludesLocalBuilds(t *testing.T) {
	repo := filepath.Join("..", "..", "version")
	abs, err := filepath.Abs(repo)
	if err != nil || !fileExists(filepath.Join(abs, "valkaw-sodium-26.3-1.0.6.jar")) {
		t.Skip("repo version folder holds no 26.3 build")
	}
	t.Setenv("HOME", t.TempDir())
	t.Setenv("VALKAW_VERSION_DIR", abs)

	a := &App{config: Config{GameDir: t.TempDir()}}
	versions := a.GetVersions()
	if !slices.Contains(versions, "26.1.2") {
		t.Fatalf("26.1.2 missing from versions: %v", versions)
	}
	if !slices.Contains(versions, "26.3") {
		t.Fatalf("26.3 missing from versions: %v", versions)
	}
	if got := supportedProfiles("26.1.2"); !slices.Contains(got, "gl") || !slices.Contains(got, "vk") {
		t.Fatalf("26.1.2 profiles = %v, want gl+vk", got)
	}
	if got := supportedProfiles("26.3"); !slices.Contains(got, "gl") || slices.Contains(got, "vk") {
		t.Fatalf("26.3 profiles = %v, want gl only", got)
	}
}
