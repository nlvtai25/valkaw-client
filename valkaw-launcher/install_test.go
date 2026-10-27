package main

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
	got := availableVersions()
	if len(got) == 0 || got[0] != defaultMCVersion {
		t.Fatalf("versions = %v", got)
	}
	if _, err := clientJar(defaultMCVersion, "gl"); err != nil {
		t.Fatal(err)
	}
	if _, err := clientJar(defaultMCVersion, "vk"); err != nil {
		t.Fatal(err)
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
