package core

import (
	"archive/zip"
	"bytes"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestProfilePaths(t *testing.T) {
	a := &App{config: Config{GameDir: "/tmp/valkaw"}}
	if got := a.gamePath("gl"); got != filepath.Join("/tmp/valkaw", "sodium") {
		t.Fatalf("gl path = %q", got)
	}
	if got := a.gamePath("vk", "mods"); got != filepath.Join("/tmp/valkaw", "vulkan", "mods") {
		t.Fatalf("vk path = %q", got)
	}
	if got := a.installPath("assets"); got != filepath.Join("/tmp/valkaw", "assets") {
		t.Fatalf("shared path = %q", got)
	}
}

func TestClientVersions(t *testing.T) {
	t.Setenv("HOME", t.TempDir())
	t.Setenv("VALKAW_VERSION_DIR", t.TempDir())
	dir := filepath.Dir(clientJarPath(defaultMCVersion, "vk"))
	if err := os.MkdirAll(dir, 0755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(clientJarPath(defaultMCVersion, "vk"), []byte("x"), 0644); err != nil {
		t.Fatal(err)
	}
	got := availableVersions()
	if len(got) == 0 || got[0] != defaultMCVersion {
		t.Fatalf("versions = %v", got)
	}
	if _, err := clientJar(defaultMCVersion, "vk"); err != nil {
		t.Fatal(err)
	}
	if _, err := clientJar(defaultMCVersion, "gl"); err == nil {
		t.Fatal("expected missing sodium build")
	}
}

// ensureClientJar must keep the cached build when the release lookup fails.
func TestEnsureClientJarFallsBackToCache(t *testing.T) {
	t.Setenv("HOME", t.TempDir())
	t.Setenv("VALKAW_VERSION_DIR", t.TempDir())
	oldDL, oldPage := releaseDL, releasesPage
	releaseDL = "http://127.0.0.1:1/"
	releasesPage = "http://127.0.0.1:1/"
	defer func() { releaseDL, releasesPage = oldDL, oldPage }()

	p := clientJarPath(defaultMCVersion, "gl")
	if err := os.MkdirAll(filepath.Dir(p), 0755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(p, []byte("cached"), 0644); err != nil {
		t.Fatal(err)
	}
	if err := ensureClientJar(defaultMCVersion, "gl"); err != nil {
		t.Fatalf("fallback failed: %v", err)
	}
	if b, _ := os.ReadFile(p); string(b) != "cached" {
		t.Fatal("cached build was replaced")
	}
}

// ensureClientJar must refuse a jar built for a different Minecraft version
// instead of shipping a mismatched client into the selected version folder.
func TestEnsureClientJarTargetVersion(t *testing.T) {
	t.Setenv("HOME", t.TempDir())
	t.Setenv("VALKAW_VERSION_DIR", t.TempDir())
	oldDL, oldPage := releaseDL, releasesPage
	releaseDL = "http://127.0.0.1:1/"
	releasesPage = "http://127.0.0.1:1/"
	defer func() { releaseDL, releasesPage = oldDL, oldPage }()

	// A cached jar whose fabric.mod.json targets 26.2 must fail install for
	// 26.1.2, and pass for 26.2.
	buildJar := func(path, mc string) {
		if err := os.MkdirAll(filepath.Dir(path), 0755); err != nil {
			t.Fatal(err)
		}
		var buf bytes.Buffer
		zw := zip.NewWriter(&buf)
		f, err := zw.Create("fabric.mod.json")
		if err != nil {
			t.Fatal(err)
		}
		if _, err := f.Write([]byte(`{"id":"valkawclient","depends":{"minecraft":"~` + mc + `"}}`)); err != nil {
			t.Fatal(err)
		}
		if err := zw.Close(); err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(path, buf.Bytes(), 0644); err != nil {
			t.Fatal(err)
		}
	}

	p := clientJarPath("26.2", "gl")
	buildJar(p, "26.2")
	if err := ensureClientJar("26.2", "gl"); err != nil {
		t.Fatalf("matching build failed: %v", err)
	}
	if err := ensureClientJar("26.1.2", "gl"); err == nil {
		t.Fatal("mismatched build should fail install")
	}
}

func TestClientJarTargetVersion(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "valkaw-sodium-26.1.2-1.0.6.jar")
	var buf bytes.Buffer
	zw := zip.NewWriter(&buf)
	f, err := zw.Create("fabric.mod.json")
	if err != nil {
		t.Fatal(err)
	}
	if _, err := f.Write([]byte(`{"depends":{"minecraft":"~26.1.2"}}`)); err != nil {
		t.Fatal(err)
	}
	if err := zw.Close(); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, buf.Bytes(), 0644); err != nil {
		t.Fatal(err)
	}
	if got := clientJarTargetVersion(path); got != "26.1.2" {
		t.Fatalf("target version = %q, want 26.1.2", got)
	}
	if clientJarTargetVersion(filepath.Join(dir, "missing.jar")) != "" {
		t.Fatal("unreadable jar should return empty target")
	}
}

func TestVersionEquals(t *testing.T) {
	cases := []struct {
		a, b string
		want bool
	}{
		{"26.2", "26.2", true},
		{"26.1.2", "26.1.2", true},
		{"26.2", "26.1.2", false},
		{"26.1.2", "26.1", false},
		{"26", "26.1.2", false},
	}
	for _, c := range cases {
		if got := versionEquals(c.a, c.b); got != c.want {
			t.Fatalf("versionEquals(%q, %q) = %v, want %v", c.a, c.b, got, c.want)
		}
	}
}

func TestTagVersion(t *testing.T) {
	cases := map[string]string{
		"https://github.com/nlvtai25/valkaw-client/releases/tag/v1.0.1": "1.0.1",
		"https://github.com/nlvtai25/valkaw-client/releases/tag/1.0.1":  "1.0.1",
		"": "",
		"https://github.com/nlvtai25/valkaw-client/releases/latest": "",
	}
	for in, want := range cases {
		if got := tagVersion(in); got != want {
			t.Fatalf("tagVersion(%q) = %q, want %q", in, got, want)
		}
	}
}

func TestWindowSize(t *testing.T) {
	if w, h := windowSize(0, 0); w != 1600 || h != 900 {
		t.Fatalf("defaults = %dx%d", w, h)
	}
	if w, h := windowSize(1000, 700); w != 1000 || h != 700 {
		t.Fatalf("custom = %dx%d", w, h)
	}
	if w, h := windowSize(99999, 1); w != 7680 || h != 900 {
		t.Fatalf("clamp = %dx%d", w, h)
	}
}

func TestFullscreenEnabled(t *testing.T) {
	dir := t.TempDir()
	if fullscreenEnabled(dir) {
		t.Fatal("missing options.txt should be windowed")
	}
	if err := os.WriteFile(filepath.Join(dir, "options.txt"), []byte("fullscreen:false\n"), 0644); err != nil {
		t.Fatal(err)
	}
	if fullscreenEnabled(dir) {
		t.Fatal("fullscreen:false detected as fullscreen")
	}
	if err := os.WriteFile(filepath.Join(dir, "options.txt"), []byte("fullscreen:true\n"), 0644); err != nil {
		t.Fatal(err)
	}
	if !fullscreenEnabled(dir) {
		t.Fatal("fullscreen:true not detected")
	}
}

func TestInstallModsJij(t *testing.T) {
	t.Setenv("HOME", t.TempDir())
	p := clientJarPath("1.21.11", "vk")
	if err := os.MkdirAll(filepath.Dir(p), 0755); err != nil {
		t.Fatal(err)
	}
	var buf bytes.Buffer
	zw := zip.NewWriter(&buf)
	w, err := zw.Create("META-INF/jars/vulkanmod-0.6.8.jar")
	if err != nil {
		t.Fatal(err)
	}
	_, _ = w.Write([]byte("x"))
	if err := zw.Close(); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(p, buf.Bytes(), 0644); err != nil {
		t.Fatal(err)
	}
	a := &App{config: Config{GameDir: t.TempDir(), Version: "1.21.11", Profile: "vk"}}
	if err := a.installMods("vk"); err != nil {
		t.Fatal(err)
	}
	b, err := os.ReadFile(filepath.Join(a.gamePath("vk", "mods"), "valkawclient.jar"))
	if err != nil {
		t.Fatal(err)
	}
	zr, err := zip.NewReader(bytes.NewReader(b), int64(len(b)))
	if err != nil {
		t.Fatal(err)
	}
	found := false
	for _, f := range zr.File {
		if strings.Contains(f.Name, "META-INF/jars/vulkanmod") {
			found = true
		}
	}
	if !found {
		t.Fatal("vulkanmod not nested")
	}
}

func TestSupportedProfilesFollowAvailableBuilds(t *testing.T) {
	dir := t.TempDir()
	t.Setenv("VALKAW_VERSION_DIR", dir)

	// Published base versions always offer both render profiles.
	if got := supportedProfiles("26.2"); len(got) != 2 {
		t.Fatalf("base version profiles = %v, want gl and vk", got)
	}
	// A local version with no build of its own falls back to Sodium only.
	if got := supportedProfiles("26.9.9"); len(got) != 1 || got[0] != "gl" {
		t.Fatalf("unknown version profiles = %v, want [gl]", got)
	}

	// A Vulkan jar in a source folder makes the profile selectable again.
	jar := filepath.Join(dir, artifactFileName("vk", "26.9.9", "1.0.6"))
	if err := os.WriteFile(jar, []byte("not a real jar"), 0o644); err != nil {
		t.Fatal(err)
	}
	got := supportedProfiles("26.9.9")
	if len(got) != 2 || got[1] != "vk" {
		t.Fatalf("profiles after adding a vulkan build = %v, want [gl vk]", got)
	}
}
