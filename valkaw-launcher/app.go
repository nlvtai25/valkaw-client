package main

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"sync"
	"time"

	wailsruntime "github.com/wailsapp/wails/v2/pkg/runtime"
)

// Config holds launcher settings.
type Config struct {
	LauncherVersion string `json:"launcherVersion"`
	JavaPath        string `json:"javaPath"`
	GameDir         string `json:"gameDir"` // shared root, default ~/.valkaw
	Version         string `json:"version"` // Minecraft version with a bundled client build
	Profile         string `json:"profile"` // "gl" | "vk"
	GPU             string `json:"gpu"`     // selected GPU pci id, "" = system default
	Username        string `json:"username"`
	MemoryMB        int    `json:"memoryMB"`
	WindowWidth     int    `json:"windowWidth"`
	WindowHeight    int    `json:"windowHeight"`
	AutoUpdate      bool   `json:"autoUpdate"`
	AfterRun        string `json:"afterRun"` // "hide" | "close" | "appear"
}

type App struct {
	ctx        context.Context
	config     Config
	cfgPath    string
	installMu  sync.Mutex
	installing bool
}

func NewApp() *App {
	a := &App{}
	a.config = Config{
		LauncherVersion: "v1.0.0",
		JavaPath:        "java",
		GameDir:         defaultGameDir(),
		Version:         defaultMCVersion,
		Profile:         "gl",
		Username:        "Player",
		MemoryMB:        4096,
		WindowWidth:     1600,
		WindowHeight:    900,
		AutoUpdate:      true,
		AfterRun:        "hide",
	}
	return a
}

func defaultGameDir() string {
	home, _ := os.UserHomeDir()
	return filepath.Join(home, ".valkaw")
}

func (a *App) startup(ctx context.Context) {
	a.ctx = ctx
	a.cfgPath = filepath.Join(configDir(), "config.json")
	if data, err := os.ReadFile(a.cfgPath); err == nil {
		_ = json.Unmarshal(data, &a.config)
	}
	if a.config.LauncherVersion == "" {
		a.config.LauncherVersion = "v1.0.0"
	}
	if a.config.MemoryMB == 0 {
		a.config.MemoryMB = 4096
	}
	a.config.WindowWidth, a.config.WindowHeight = windowSize(a.config.WindowWidth, a.config.WindowHeight)
	if a.config.Profile == "" {
		a.config.Profile = "gl"
	}
	if a.config.GameDir == "" || a.config.GameDir == filepath.Join(mustHome(), ".minecraft") {
		a.config.GameDir = defaultGameDir()
	}
	if _, err := clientJar(a.config.Version, a.config.Profile); err != nil {
		a.config.Version = defaultMCVersion
	}
	_ = os.MkdirAll(a.config.GameDir, 0755)
	if a.config.AfterRun == "" {
		a.config.AfterRun = "hide"
	}
	_ = a.saveConfig()
}

func mustHome() string {
	home, _ := os.UserHomeDir()
	return home
}

func configDir() string {
	home, _ := os.UserHomeDir()
	d := filepath.Join(home, ".valkaw-launcher")
	_ = os.MkdirAll(d, 0755)
	return d
}

func (a *App) GetConfig() Config { return a.config }

func windowSize(w, h int) (int, int) {
	if w < 320 {
		w = 1600
	}
	if h < 240 {
		h = 900
	}
	if w > 7680 {
		w = 7680
	}
	if h > 4320 {
		h = 4320
	}
	return w, h
}

func (a *App) SaveConfig(c Config) string {
	if c.Profile != "gl" && c.Profile != "vk" {
		c.Profile = "gl"
	}
	if _, err := clientJar(c.Version, c.Profile); err != nil {
		c.Version = defaultMCVersion
	}
	if c.GPU != "" && findGPU(c.GPU).ID == "" {
		c.GPU = "" // selected GPU is gone: back to system default
	}
	c.WindowWidth, c.WindowHeight = windowSize(c.WindowWidth, c.WindowHeight)
	a.config = c
	if err := a.saveConfig(); err != nil {
		return "error: " + err.Error()
	}
	return "ok"
}

func (a *App) saveConfig() error {
	data, err := json.MarshalIndent(a.config, "", "  ")
	if err != nil {
		return err
	}
	return os.WriteFile(a.cfgPath, data, 0644)
}

func (a *App) GetInstances() []string {
	entries, err := os.ReadDir(a.config.GameDir)
	if err != nil {
		return nil
	}
	var out []string
	for _, e := range entries {
		if e.IsDir() {
			out = append(out, e.Name())
		}
	}
	return out
}

type ghRelease struct {
	TagName string `json:"tag_name"`
	HTMLURL string `json:"html_url"`
	Assets  []struct {
		Name string `json:"name"`
		URL  string `json:"browser_download_url"`
	} `json:"assets"`
}

func fetchLatestRelease() (*ghRelease, error) {
	client := &http.Client{Timeout: 10 * time.Second}
	req, _ := http.NewRequest(http.MethodGet, "https://api.github.com/repos/nlvtai25/valkaw-client/releases/latest", nil)
	req.Header.Set("User-Agent", "ValkawLauncher/1.0")
	resp, err := client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		return nil, fmt.Errorf("HTTP %d", resp.StatusCode)
	}
	var r ghRelease
	if err := json.NewDecoder(resp.Body).Decode(&r); err != nil {
		return nil, err
	}
	if r.TagName == "" {
		return nil, errors.New("no release found")
	}
	return &r, nil
}

func versionParts(v string) []int {
	v = strings.TrimPrefix(strings.TrimSpace(v), "v")
	base := strings.SplitN(v, "-", 2)[0]
	var out []int
	for _, p := range strings.Split(base, ".") {
		n, _ := strconv.Atoi(p)
		out = append(out, n)
	}
	return out
}

func newerVersion(latest, current string) bool {
	l, c := versionParts(latest), versionParts(current)
	for i := 0; i < len(l) || i < len(c); i++ {
		var lv, cv int
		if i < len(l) {
			lv = l[i]
		}
		if i < len(c) {
			cv = c[i]
		}
		if lv != cv {
			return lv > cv
		}
	}
	return false
}

// CheckUpdate queries GitHub releases.
func (a *App) CheckUpdate() string {
	r, err := fetchLatestRelease()
	if err != nil {
		return "update check failed: " + err.Error()
	}
	if !newerVersion(r.TagName, a.config.LauncherVersion) {
		return "up to date (" + a.config.LauncherVersion + ")"
	}
	return "update available: " + r.TagName + " (current " + a.config.LauncherVersion + ")"
}

func updateAsset(r *ghRelease) (string, string) {
	ext := ""
	if runtime.GOOS == "windows" {
		ext = ".exe"
	}
	names := []string{
		fmt.Sprintf("valkaw-launcher-%s-%s%s", runtime.GOOS, runtime.GOARCH, ext),
		fmt.Sprintf("valkaw-launcher-%s%s", runtime.GOOS, ext),
		"valkaw-launcher" + ext,
	}
	for _, want := range names {
		for _, asset := range r.Assets {
			if strings.EqualFold(asset.Name, want) {
				return asset.Name, asset.URL
			}
		}
	}
	for _, asset := range r.Assets {
		name := strings.ToLower(asset.Name)
		if strings.Contains(name, "launcher") && strings.Contains(name, runtime.GOOS) {
			return asset.Name, asset.URL
		}
	}
	return "", ""
}

// InstallUpdate downloads the latest launcher binary and restarts it.
func (a *App) InstallUpdate() string {
	r, err := fetchLatestRelease()
	if err != nil {
		return "error: " + err.Error()
	}
	if !newerVersion(r.TagName, a.config.LauncherVersion) {
		return "already up to date"
	}
	name, assetURL := updateAsset(r)
	if assetURL == "" {
		return "error: no launcher asset for " + runtime.GOOS + "/" + runtime.GOARCH
	}
	exe, err := os.Executable()
	if err != nil {
		return "error: " + err.Error()
	}
	if resolved, err := filepath.EvalSymlinks(exe); err == nil {
		exe = resolved
	}
	if runtime.GOOS == "windows" {
		return "error: update " + name + " manually on Windows"
	}
	tmp := exe + ".new"
	_ = os.Remove(tmp)
	if err := downloadFile(assetURL, tmp, 0, "", func(n int64) {
		a.emitInstall("Downloading launcher update...", 0)
	}); err != nil {
		_ = os.Remove(tmp)
		return "error: " + err.Error()
	}
	if err := os.Chmod(tmp, 0755); err != nil {
		_ = os.Remove(tmp)
		return "error: " + err.Error()
	}
	if err := os.Rename(tmp, exe); err != nil {
		_ = os.Remove(tmp)
		return "error: " + err.Error()
	}
	cmd := exec.Command(exe)
	cmd.Dir = filepath.Dir(exe)
	if err := cmd.Start(); err != nil {
		return "updated, restart launcher manually (" + r.TagName + ")"
	}
	wailsruntime.Quit(a.ctx)
	return "updated to " + r.TagName
}

// OpenReleases opens the releases page in the browser.
func (a *App) OpenReleases() {
	if a.ctx != nil {
		wailsruntime.BrowserOpenURL(a.ctx, "https://github.com/nlvtai25/valkaw-client/releases")
	}
}
