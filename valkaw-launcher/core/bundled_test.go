package core

import (
	"archive/zip"
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
)

func writeTestJar(t *testing.T, dir string, name string, modID string) string {
	t.Helper()
	path := filepath.Join(dir, name)
	file, err := os.Create(path)
	if err != nil {
		t.Fatalf("create jar: %v", err)
	}
	writer := zip.NewWriter(file)
	entry, err := writer.Create("fabric.mod.json")
	if err != nil {
		t.Fatalf("create entry: %v", err)
	}
	if _, err := entry.Write([]byte(`{"id":"` + modID + `","version":"1.0.0"}`)); err != nil {
		t.Fatalf("write entry: %v", err)
	}
	if err := writer.Close(); err != nil {
		t.Fatalf("close zip: %v", err)
	}
	if err := file.Close(); err != nil {
		t.Fatalf("close file: %v", err)
	}
	return path
}

func TestHiddenModSlugs(t *testing.T) {
	vk := hiddenModSlugs("vk")
	for _, slug := range []string{"sodium", "iris", "vulkanmod", "entityculling"} {
		if _, ok := vk[slug]; !ok {
			t.Errorf("vulkan profile should hide %s", slug)
		}
	}
	if _, ok := vk["jei"]; ok {
		t.Error("vulkan profile should not hide unrelated mods")
	}

	gl := hiddenModSlugs("gl")
	if _, ok := gl["entityculling"]; ok {
		t.Error("sodium profile should allow sodium-only addons")
	}
	if _, ok := gl["vulkanmod"]; !ok {
		t.Error("sodium profile should hide vulkan mods")
	}
	if _, ok := gl["sodium"]; !ok {
		t.Error("sodium is bundled and must be hidden")
	}
}

func TestFilterSearchHitsRemovesHiddenMods(t *testing.T) {
	payload := []byte(`{"hits":[{"slug":"sodium"},{"slug":"jei"},{"slug":"vulkanmod"}],"total_hits":3,"offset":0,"limit":20}`)
	out, hidden, err := filterSearchHits(payload, "mod", hiddenModSlugs("vk"))
	if err != nil {
		t.Fatalf("filter: %v", err)
	}
	if hidden != 2 {
		t.Fatalf("hidden = %d, want 2", hidden)
	}
	var result modrinthSearchResult
	if err := json.Unmarshal(out, &result); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	if len(result.Hits) != 1 || result.Hits[0]["slug"] != "jei" {
		t.Fatalf("unexpected hits: %v", result.Hits)
	}
	if result.HiddenBundled != 2 {
		t.Fatalf("HiddenBundled = %d, want 2", result.HiddenBundled)
	}
}

func TestFilterSearchHitsLeavesOtherTypesAlone(t *testing.T) {
	payload := []byte(`{"hits":[{"slug":"sodium"}],"total_hits":1}`)
	out, hidden, err := filterSearchHits(payload, "shader", hiddenModSlugs("vk"))
	if err != nil {
		t.Fatalf("filter: %v", err)
	}
	if hidden != 0 || string(out) != string(payload) {
		t.Fatalf("shader results must pass through, got %s", out)
	}
}

func TestModFileIDAndDuplicateLookup(t *testing.T) {
	dir := t.TempDir()
	writeTestJar(t, dir, "jei-1.0.jar", "jei")
	conflict := writeTestJar(t, dir, "sodium-2.0.jar", "sodium")
	if err := os.WriteFile(filepath.Join(dir, "notes.txt"), []byte("ignore me"), 0644); err != nil {
		t.Fatalf("write notes: %v", err)
	}

	if got, err := modFileID(conflict); err != nil || got != "sodium" {
		t.Fatalf("modFileID = %q, %v", got, err)
	}

	found, err := findModJar(dir, "sodium", filepath.Join(dir, "iris.jar"))
	if err != nil || found != "sodium-2.0.jar" {
		t.Fatalf("findModJar = %q, %v", found, err)
	}
	if found, err := findModJar(dir, "sodium", conflict); err != nil || found != "" {
		t.Fatalf("a jar must not conflict with itself: %q, %v", found, err)
	}
	if found, err := findModJar(dir, "create", ""); err != nil || found != "" {
		t.Fatalf("unknown id must not match: %q, %v", found, err)
	}
}
