package core

import (
	"os"
	"path/filepath"
	"testing"
)

func TestArtifactFileNameRoundTrip(t *testing.T) {
	name := artifactFileName("vk", "26.2", "1.0.6")
	if name != "valkaw-vulkan-26.2-1.0.6.jar" {
		t.Fatalf("name = %q", name)
	}
	profile, game, client, ok := parseArtifactFileName(name)
	if !ok || profile != "vulkan" || game != "26.2" || client != "1.0.6" {
		t.Fatalf("parse = %q %q %q %v", profile, game, client, ok)
	}
}

func TestParseArtifactFileNameRejectsOtherShapes(t *testing.T) {
	bad := []string{
		"valkawclient-1.0.6-sodium.jar", // legacy release name
		"sodium.jar",                    // legacy cache name
		"valkaw-iris-26.2-1.0.6.jar",    // unknown profile
		"valkaw-sodium-26.2.jar",        // no client version
		"valkaw-vulkan-1.0.6.jar",       // no client version either
		"valkaw-sodium-26.2-1.0.6.zip",  // wrong extension
	}
	for _, name := range bad {
		if _, _, _, ok := parseArtifactFileName(name); ok {
			t.Fatalf("%q should not parse", name)
		}
	}
}

func TestNewestArtifactPicksHighestClientVersion(t *testing.T) {
	dir := t.TempDir()
	for _, name := range []string{
		"valkaw-sodium-26.2-1.0.6.jar",
		"valkaw-sodium-26.2-1.0.10.jar",
		"valkaw-sodium-26.2-1.0.9.jar",
		"valkaw-sodium-26.3-9.9.9.jar",
		"valkaw-vulkan-26.2-2.0.0.jar",
	} {
		if err := os.WriteFile(filepath.Join(dir, name), []byte(name), 0644); err != nil {
			t.Fatal(err)
		}
	}

	path, ver, ok := newestArtifact(dir, "sodium", "26.2")
	if !ok || ver != "1.0.10" {
		t.Fatalf("picked %q %v", ver, ok)
	}
	if filepath.Base(path) != "valkaw-sodium-26.2-1.0.10.jar" {
		t.Fatalf("path = %q", path)
	}
	if _, _, ok := newestArtifact(dir, "vulkan", "26.2"); !ok {
		t.Fatal("vulkan build not found")
	}
}

// A jar in VALKAW_VERSION_DIR is copied into the cache under the canonical name.
func TestInstallLocalClientJar(t *testing.T) {
	home := t.TempDir()
	t.Setenv("HOME", home)
	src := t.TempDir()
	t.Setenv("VALKAW_VERSION_DIR", src)

	jar := filepath.Join(src, "valkaw-sodium-"+defaultMCVersion+"-1.0.7.jar")
	if err := os.WriteFile(jar, []byte("built"), 0644); err != nil {
		t.Fatal(err)
	}

	dest, err := installLocalClientJar(defaultMCVersion, "gl")
	if err != nil {
		t.Fatal(err)
	}
	want := filepath.Join(clientCacheDir(), defaultMCVersion, "valkaw-sodium-"+defaultMCVersion+"-1.0.7.jar")
	if dest != want {
		t.Fatalf("dest = %q, want %q", dest, want)
	}
	if b, err := os.ReadFile(dest); err != nil || string(b) != "built" {
		t.Fatalf("cache content = %q err %v", b, err)
	}
	if got := clientJarPath(defaultMCVersion, "gl"); got != want {
		t.Fatalf("clientJarPath = %q", got)
	}

	// Second run must be a no-op, not a re-copy.
	info, _ := os.Stat(dest)
	if _, err := installLocalClientJar(defaultMCVersion, "gl"); err != nil {
		t.Fatal(err)
	}
	again, _ := os.Stat(dest)
	if !again.ModTime().Equal(info.ModTime()) {
		t.Fatal("cache jar was rewritten")
	}
}

// A build that has never been installed must still show up as selectable.
func TestAvailableVersionsIncludesSourceBuilds(t *testing.T) {
	t.Setenv("HOME", t.TempDir())
	src := t.TempDir()
	t.Setenv("VALKAW_VERSION_DIR", src)

	for _, name := range []string{
		"valkaw-sodium-1.21.11-1.0.6.jar",
		"valkaw-vulkan-26.2-1.0.6.jar",
		"valkawclient-9.9.9-sodium.jar", // legacy release name, ignored
	} {
		if err := os.WriteFile(filepath.Join(src, name), []byte("x"), 0644); err != nil {
			t.Fatal(err)
		}
	}

	got := availableVersions()
	want := []string{"26.2", "1.21.11"} // newest first
	if len(got) != len(want) {
		t.Fatalf("versions = %v", got)
	}
	for i := range want {
		if got[i] != want[i] {
			t.Fatalf("versions = %v, want %v", got, want)
		}
	}

	// A cached install and a source build of the same version must not duplicate.
	if err := os.MkdirAll(filepath.Join(clientCacheDir(), "26.2"), 0755); err != nil {
		t.Fatal(err)
	}
	if got := availableVersions(); len(got) != 2 {
		t.Fatalf("duplicate version survived: %v", got)
	}
}

// No build anywhere and the release lookup is dead: the cached jar must survive.
func TestEnsureClientJarWithoutSource(t *testing.T) {
	t.Setenv("HOME", t.TempDir())
	t.Setenv("VALKAW_VERSION_DIR", t.TempDir())
	oldDL, oldPage := releaseDL, releasesPage
	releaseDL = "http://127.0.0.1:1/"
	releasesPage = "http://127.0.0.1:1/"
	defer func() { releaseDL, releasesPage = oldDL, oldPage }()

	if err := ensureClientJar(defaultMCVersion, "gl"); err == nil {
		t.Fatal("expected an error with no build and no release")
	}

	cached := clientJarPath(defaultMCVersion, "gl")
	if err := os.MkdirAll(filepath.Dir(cached), 0755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(cached, []byte("cached"), 0644); err != nil {
		t.Fatal(err)
	}
	if err := ensureClientJar(defaultMCVersion, "gl"); err != nil {
		t.Fatalf("fallback failed: %v", err)
	}
	if b, _ := os.ReadFile(cached); string(b) != "cached" {
		t.Fatal("cached build was replaced")
	}
}
