package core

import (
	"archive/zip"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
)

// bundledModSlugs are the Modrinth projects shipped inside valkawclient.jar
// (both renderers). Installing one of them again duplicates a mod id, which
// makes Minecraft refuse to start, so the resource browser hides them.
var bundledModSlugs = []string{
	// Shared by the Sodium and the VulkanMod build.
	"fabric-api",
	"dynamic-fps",
	"lithium",
	"ferrite-core",
	"krypton",
	"badoptimizations",
	"clumps",
	"c2me-fabric",
	// Sodium (OpenGL) build.
	"sodium",
	"sodium-extra",
	"reeses-sodium-options",
	"iris",
	"immediatelyfast",
	"moreculling",
	"cloth-config",
	"particle-core",
	"fzzy-config",
	"fabric-language-kotlin",
	// VulkanMod build.
	"vulkanmod",
	"not-enough-vulkan",
	"beryl",
}

var bundledModSet = func() map[string]struct{} {
	set := make(map[string]struct{}, len(bundledModSlugs))
	for _, slug := range bundledModSlugs {
		set[slug] = struct{}{}
	}
	return set
}()

func isBundledModSlug(slug string) bool {
	_, ok := bundledModSet[strings.ToLower(slug)]
	return ok
}

// glOnlyModSlugs hook into Sodium or Iris, so they cannot run on VulkanMod.
var glOnlyModSlugs = []string{
	"entityculling",
	"indium",
	"continuity",
	"lambdynamiclights",
	"nvidium",
	"ok-zoomer",
	"sodium-shadowy-path-blocks",
	"fabricskyboxes",
}

// vkOnlyModSlugs hook into VulkanMod, so they cannot run on Sodium.
var vkOnlyModSlugs = []string{
	"vulkanmod",
	"not-enough-vulkan",
	"beryl",
}

// hiddenModSlugs returns the mods that must not be offered for a profile:
// everything already bundled in the client plus the renderer of the other
// profile.
func hiddenModSlugs(profile string) map[string]struct{} {
	hidden := make(map[string]struct{}, len(bundledModSlugs)+len(glOnlyModSlugs)+len(vkOnlyModSlugs))
	for _, slug := range bundledModSlugs {
		hidden[slug] = struct{}{}
	}
	cross := vkOnlyModSlugs
	if profile == "vk" {
		cross = glOnlyModSlugs
	}
	for _, slug := range cross {
		hidden[slug] = struct{}{}
	}
	return hidden
}

// filterSearchHits removes hidden mods from a search payload. Only mod results
// are affected; resource and shader packs have nothing bundled.
func filterSearchHits(payload []byte, projectType string, hidden map[string]struct{}) ([]byte, int, error) {
	if projectType != "mod" || len(hidden) == 0 {
		return payload, 0, nil
	}
	var result modrinthSearchResult
	if err := json.Unmarshal(payload, &result); err != nil {
		return payload, 0, err
	}
	kept := make([]map[string]any, 0, len(result.Hits))
	for _, hit := range result.Hits {
		slug, _ := hit["slug"].(string)
		if _, blocked := hidden[strings.ToLower(slug)]; blocked {
			continue
		}
		kept = append(kept, hit)
	}
	removed := len(result.Hits) - len(kept)
	if removed == 0 {
		return payload, 0, nil
	}
	result.Hits = kept
	result.HiddenBundled = removed
	out, err := json.Marshal(result)
	if err != nil {
		return payload, 0, err
	}
	return out, removed, nil
}

// modFileID reads the mod id declared by a jar's fabric.mod.json.
func modFileID(path string) (string, error) {
	reader, err := zip.OpenReader(path)
	if err != nil {
		return "", err
	}
	defer reader.Close()
	for _, file := range reader.File {
		if file.Name != "fabric.mod.json" {
			continue
		}
		rc, err := file.Open()
		if err != nil {
			return "", err
		}
		defer rc.Close()
		var meta struct {
			ID string `json:"id"`
		}
		if err := json.NewDecoder(io.LimitReader(rc, 1<<20)).Decode(&meta); err != nil {
			return "", err
		}
		return strings.TrimSpace(meta.ID), nil
	}
	return "", fmt.Errorf("%s has no fabric.mod.json", filepath.Base(path))
}

// findModJar reports the jar in dir that already provides modID. skip is the
// path being installed and is ignored, so a file never conflicts with itself.
func findModJar(dir string, modID string, skip string) (string, error) {
	entries, err := os.ReadDir(dir)
	if err != nil {
		return "", err
	}
	skip = filepath.Clean(skip)
	for _, entry := range entries {
		if entry.IsDir() || !strings.HasSuffix(strings.ToLower(entry.Name()), ".jar") {
			continue
		}
		path := filepath.Join(dir, entry.Name())
		if filepath.Clean(path) == skip {
			continue
		}
		id, err := modFileID(path)
		if err != nil || id == "" {
			continue
		}
		if id == modID {
			return entry.Name(), nil
		}
	}
	return "", nil
}
