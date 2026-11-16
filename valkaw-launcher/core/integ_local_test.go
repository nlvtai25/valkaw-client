package core

import (
	"path/filepath"
	"testing"
)

// TestEnsureClientJarLocalSourceInstall proves that when the repo version folder
// holds a correct build for a game version, ensureClientJar installs it into the
// cache and the cached jar targets that game version, without hitting GitHub.
func TestEnsureClientJarLocalSourceInstall(t *testing.T) {
	repo := filepath.Join("..", "..", "version")
	abs, err := filepath.Abs(repo)
	if err != nil || !fileExists(filepath.Join(abs, "valkaw-sodium-26.1.2-1.0.6.jar")) {
		t.Skip("repo version folder holds no 26.1.2 build")
	}
	t.Setenv("HOME", t.TempDir())
	t.Setenv("VALKAW_VERSION_DIR", abs)
	oldDL, oldPage := releaseDL, releasesPage
	releaseDL = "http://127.0.0.1:1/"
	releasesPage = "http://127.0.0.1:1/"
	defer func() { releaseDL, releasesPage = oldDL, oldPage }()

	if err := ensureClientJar("26.1.2", "gl"); err != nil {
		t.Fatalf("install 26.1.2 sodium from local source failed: %v", err)
	}
	path := clientJarPath("26.1.2", "gl")
	if !fileExists(path) {
		t.Fatalf("cached jar missing: %s", path)
	}
	if target := clientJarTargetVersion(path); target != "26.1.2" {
		t.Fatalf("cached jar targets %s, want 26.1.2", target)
	}
}

