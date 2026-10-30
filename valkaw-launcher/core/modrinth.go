package core

import (
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"time"

	wailsruntime "github.com/wailsapp/wails/v2/pkg/runtime"
)

type modrinthSearchResult struct {
	Hits      []map[string]any `json:"hits"`
	TotalHits int              `json:"total_hits"`
	Offset    int              `json:"offset"`
	Limit     int              `json:"limit"`
	// HiddenBundled counts mods removed because they ship inside the client.
	HiddenBundled int `json:"hidden_bundled,omitempty"`
}

// modrinthHTTP is shared so downloads and searches reuse connections.
var modrinthHTTP = &http.Client{Timeout: 30 * time.Second}

func (a *App) modrinthClient() *http.Client {
	return modrinthHTTP
}

func (a *App) modrinthHeaders(req *http.Request) {
	req.Header.Set("User-Agent", "Valkaw-Launcher/"+a.version)
	req.Header.Set("Accept", "application/json")
}

// SearchModrinth queries the Modrinth search API.
// filters is a JSON array of arrays: each inner array is a group of category
// slugs joined with OR, groups are combined with AND.
func (a *App) SearchModrinth(query string, projectType string, gameVersion string, loader string, sort string, offset int, limit int, filters string) (string, error) {
	if limit <= 0 {
		limit = 20
	}
	if offset < 0 {
		offset = 0
	}
	if sort == "" {
		sort = "relevance"
	}
	if projectType == "" {
		projectType = "mod"
	}

	params := url.Values{}
	if query != "" {
		params.Set("query", query)
	}
	params.Set("limit", fmt.Sprintf("%d", limit))
	params.Set("offset", fmt.Sprintf("%d", offset))
	params.Set("index", sort)

	facets := [][]string{}
	if projectType != "" {
		facets = append(facets, []string{"project_type:" + projectType})
	}
	if gameVersion != "" {
		facets = append(facets, []string{"versions:" + gameVersion})
	}
	// Loader categories only apply to mods; resource and shader packs have no
	// loader category on Modrinth and would be filtered out entirely.
	if projectType == "mod" && loader != "" && loader != "none" {
		facets = append(facets, []string{"categories:" + loader})
	}
	if filters != "" {
		var groups [][]string
		if err := json.Unmarshal([]byte(filters), &groups); err == nil {
			for _, group := range groups {
				or := make([]string, 0, len(group))
				for _, slug := range group {
					if slug != "" {
						or = append(or, "categories:"+slug)
					}
				}
				if len(or) > 0 {
					facets = append(facets, or)
				}
			}
		}
	}
	if len(facets) > 0 {
		facetBytes, _ := json.Marshal(facets)
		params.Set("facets", string(facetBytes))
	}

	// Mods already inside valkawclient.jar, and renderer mods for the other
	// profile, are hidden: installing them duplicates a mod id or drags the
	// wrong renderer into the game, and Minecraft refuses to start.
	hidden := hiddenModSlugs(a.activeProfile())
	applyHidden := projectType == "mod" && len(hidden) > 0

	collected := make([]map[string]any, 0, limit)
	hiddenCount := 0
	result := modrinthSearchResult{Offset: offset, Limit: limit}
	cursor := offset
	for {
		params.Set("offset", strconv.Itoa(cursor))
		page, err := a.searchPage(params)
		if err != nil {
			if cursor == offset {
				return "", err
			}
			break
		}
		result.TotalHits = page.TotalHits
		for _, hit := range page.Hits {
			if applyHidden {
				if slug, ok := hit["slug"].(string); ok {
					if _, skip := hidden[strings.ToLower(slug)]; skip {
						hiddenCount++
						continue
					}
				}
			}
			collected = append(collected, hit)
		}
		cursor += len(page.Hits)
		// A short page means Modrinth has nothing left. Otherwise keep asking
		// until the page is full, so hidden mods do not leave gaps in the grid.
		if len(collected) >= limit || len(page.Hits) < limit || cursor >= page.TotalHits {
			break
		}
	}
	if len(collected) > limit {
		collected = collected[:limit]
	}
	result.Hits = collected
	result.HiddenBundled = hiddenCount

	out, err := json.Marshal(result)
	if err != nil {
		return "", err
	}
	return string(out), nil
}

// searchPage runs a single search request and decodes the Modrinth response.
func (a *App) searchPage(params url.Values) (modrinthSearchResult, error) {
	req, err := http.NewRequest("GET", "https://api.modrinth.com/v2/search?"+params.Encode(), nil)
	if err != nil {
		return modrinthSearchResult{}, err
	}
	a.modrinthHeaders(req)
	resp, err := a.modrinthClient().Do(req)
	if err != nil {
		return modrinthSearchResult{}, err
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 400 {
		body, _ := io.ReadAll(io.LimitReader(resp.Body, 4096))
		return modrinthSearchResult{}, fmt.Errorf("modrinth search failed: %s %s", resp.Status, string(body))
	}
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return modrinthSearchResult{}, err
	}
	var page modrinthSearchResult
	if err := json.Unmarshal(body, &page); err != nil {
		return modrinthSearchResult{}, err
	}
	return page, nil
}

func (a *App) GetModrinthProject(projectId string) (string, error) {
	if projectId == "" {
		return "", errors.New("projectId required")
	}
	req, err := http.NewRequest("GET", "https://api.modrinth.com/v2/project/"+url.PathEscape(projectId), nil)
	if err != nil {
		return "", err
	}
	a.modrinthHeaders(req)
	resp, err := a.modrinthClient().Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 400 {
		body, _ := io.ReadAll(io.LimitReader(resp.Body, 4096))
		return "", fmt.Errorf("modrinth project failed: %s %s", resp.Status, string(body))
	}
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", err
	}
	return string(body), nil
}

func (a *App) GetModrinthProjectVersions(projectId string, gameVersion string, loader string) (string, error) {
	if projectId == "" {
		return "", errors.New("projectId required")
	}
	params := url.Values{}
	if gameVersion != "" {
		if raw, err := json.Marshal([]string{gameVersion}); err == nil {
			params.Set("game_versions", string(raw))
		}
	}
	if loader != "" && loader != "none" {
		if raw, err := json.Marshal([]string{loader}); err == nil {
			params.Set("loaders", string(raw))
		}
	}
	endpoint := "https://api.modrinth.com/v2/project/" + url.PathEscape(projectId) + "/version"
	if len(params) > 0 {
		endpoint += "?" + params.Encode()
	}
	req, err := http.NewRequest("GET", endpoint, nil)
	if err != nil {
		return "", err
	}
	a.modrinthHeaders(req)
	resp, err := a.modrinthClient().Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 400 {
		body, _ := io.ReadAll(io.LimitReader(resp.Body, 4096))
		return "", fmt.Errorf("modrinth versions failed: %s %s", resp.Status, string(body))
	}
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", err
	}
	return string(body), nil
}

// activeProfile reports the profile the game will run with. Anything unknown
// falls back to the Sodium (OpenGL) profile, matching launch behaviour.
func (a *App) activeProfile() string {
	if a.config.Profile == "vk" {
		return "vk"
	}
	return "gl"
}

// resourceSubdir maps a resource kind or Modrinth project type to the folder
// Minecraft reads it from.
func resourceSubdir(resourceType string) (string, error) {
	switch strings.ToLower(resourceType) {
	case "mod", "mods":
		return "mods", nil
	case "resourcepack", "resourcepacks", "resource_pack", "texturepack":
		return "resourcepacks", nil
	case "shaderpack", "shaderpacks", "shader_pack", "shader":
		return "shaderpacks", nil
	}
	return "", fmt.Errorf("unknown resource type: %s", resourceType)
}

// resourceDirForType resolves the download folder inside the selected profile,
// e.g. ~/.valkaw/minecraft/sodium/mods or .../vulkan/mods.
func (a *App) resourceDirForType(resourceType string) (string, error) {
	sub, err := resourceSubdir(resourceType)
	if err != nil {
		return "", err
	}
	dir := a.gamePath(a.activeProfile(), sub)
	if err := os.MkdirAll(dir, 0755); err != nil {
		return "", err
	}
	return dir, nil
}

func sanitizeFileName(name string) string {
	name = filepath.Base(strings.ReplaceAll(name, "\\", "/"))
	name = strings.TrimSpace(name)
	if name == "" || name == "." || name == ".." {
		return "download"
	}
	return name
}

func (a *App) DownloadResource(downloadUrl string, fileName string, resourceType string) error {
	if downloadUrl == "" {
		return errors.New("downloadUrl required")
	}
	if fileName == "" {
		return errors.New("fileName required")
	}
	dir, err := a.resourceDirForType(resourceType)
	if err != nil {
		return err
	}
	safe := sanitizeFileName(fileName)
	dest := filepath.Join(dir, safe)

	req, err := http.NewRequest("GET", downloadUrl, nil)
	if err != nil {
		return err
	}
	a.modrinthHeaders(req)
	resp, err := a.modrinthClient().Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 400 {
		body, _ := io.ReadAll(io.LimitReader(resp.Body, 4096))
		return fmt.Errorf("download failed: %s %s", resp.Status, string(body))
	}

	total := resp.ContentLength
	// Stream into a temporary file first: an interrupted download must never
	// leave a broken jar in the mods folder, and a mod can only be identified
	// once its fabric.mod.json is on disk.
	tmp, err := os.CreateTemp(dir, safe+".*.part")
	if err != nil {
		return err
	}
	tmpPath := tmp.Name()
	keep := false
	defer func() {
		tmp.Close()
		if !keep {
			os.Remove(tmpPath)
		}
	}()

	buf := make([]byte, 32*1024)
	var downloaded int64
	last := time.Now()
	for {
		n, readErr := resp.Body.Read(buf)
		if n > 0 {
			written, werr := tmp.Write(buf[:n])
			downloaded += int64(written)
			if werr != nil {
				return werr
			}
			if time.Since(last) > 150*time.Millisecond {
				wailsruntime.EventsEmit(a.ctx, "resource:progress", fileName, downloaded, total)
				last = time.Now()
			}
		}
		if readErr == io.EOF {
			break
		}
		if readErr != nil {
			return readErr
		}
	}
	if err := tmp.Close(); err != nil {
		return err
	}

	// Second safety net: refuse a jar whose mod id is already provided by
	// another jar in the same mods folder.
	if sub, serr := resourceSubdir(resourceType); serr == nil && sub == "mods" {
		if id, ierr := modFileID(tmpPath); ierr == nil && id != "" {
			existing, ferr := findModJar(dir, id, dest)
			if ferr == nil && existing != "" {
				return fmt.Errorf("%s is already installed as %s; remove it first to change version", safe, existing)
			}
		}
	}

	if err := os.Remove(dest); err != nil && !os.IsNotExist(err) {
		return err
	}
	if err := os.Rename(tmpPath, dest); err != nil {
		return err
	}
	keep = true
	wailsruntime.EventsEmit(a.ctx, "resource:progress", fileName, downloaded, total)
	return nil
}

func (a *App) OpenResourceDir(resourceType string) error {
	dir, err := a.resourceDirForType(resourceType)
	if err != nil {
		return err
	}
	var cmd *exec.Cmd
	switch runtime.GOOS {
	case "windows":
		cmd = exec.Command("explorer", dir)
	case "darwin":
		cmd = exec.Command("open", dir)
	default:
		cmd = exec.Command("xdg-open", dir)
	}
	return cmd.Start()
}
