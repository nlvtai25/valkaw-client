package core

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

const (
	repoDownloads = "https://github.com/nlvtai25/valkaw-client/raw/main/assets/downloads/"
)

var (
	releasesAPI  = "https://api.github.com/repos/nlvtai25/valkaw-client/releases/latest"
	releasesList = "https://api.github.com/repos/nlvtai25/valkaw-client/releases?per_page=30"
	releasesPage = "https://github.com/nlvtai25/valkaw-client/releases/latest"
	releaseDL    = "https://github.com/nlvtai25/valkaw-client/releases/latest/download/"
)

// updatePrefix is the asset name marker used by this platform's build. Linux
// ships from every release, Windows only from releases that carry a Windows file.
func updatePrefix() string {
	if runtime.GOOS == "windows" {
		return "valkaw-launcher-win-"
	}
	return ""
}

// updateAssetName is the file published under assets/downloads for this platform.
func updateAssetName(tag string) string {
	if runtime.GOOS == "windows" {
		return fmt.Sprintf("valkaw-launcher-win-%s-%s-%s.exe", tag, runtime.GOOS, runtime.GOARCH)
	}
	return fmt.Sprintf("valkaw-launcher-%s-%s-%s", tag, runtime.GOOS, runtime.GOARCH)
}

// Config holds launcher settings.
type Config struct {
	LauncherVersion string `json:"launcherVersion"`
	JavaPath        string `json:"javaPath"`
	GameDir         string `json:"gameDir"` // shared root, default ~/.valkaw/minecraft
	Version         string `json:"version"` // Minecraft version with a bundled client build
	Profile         string `json:"profile"` // "gl" | "vk"
	GPU             string `json:"gpu"`     // selected GPU pci id, "" = system default
	Username        string `json:"username"`
	MemoryMB        int    `json:"memoryMB"`
	WindowWidth     int    `json:"windowWidth"`
	WindowHeight    int    `json:"windowHeight"`
	AutoUpdate      bool   `json:"autoUpdate"`
	AfterRun        string `json:"afterRun"` // "hide" | "close" | "appear"
	Platform        string `json:"platform"` // filled at startup: windows | linux | darwin
	GpuPref         int    `json:"gpuPref"`  // windows only: 0 auto, 1 power saving, 2 high performance
}

type App struct {
	ctx        context.Context
	config     Config
	cfgPath    string
	version    string // identifies the running binary, not the saved config
	installMu  sync.Mutex
	installing bool
}

func NewApp(version string) *App {
	a := &App{version: version}
	a.config = Config{
		LauncherVersion: version,
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
	if runtime.GOOS == "windows" {
		// A hidden Wails window has no taskbar entry on Windows, so the player
		// would have no way back after closing the game.
		a.config.AfterRun = "appear"
	}
	return a
}

// Everything the launcher owns sits under ~/.valkaw:
//
//	launcher/   config.json and the cached client jars
//	jre/        the Java runtime the launcher downloads
//	minecraft/  game files, default GameDir, one folder per profile inside
func dataDir() string { return filepath.Join(mustHome(), ".valkaw") }

func defaultGameDir() string { return filepath.Join(dataDir(), "minecraft") }

func launcherDir() string {
	d := filepath.Join(dataDir(), "launcher")
	_ = os.MkdirAll(d, 0755)
	return d
}

func javaDir() string { return filepath.Join(dataDir(), "jre") }

// migrateLayout moves state from the older ~/.valkaw-launcher folder into the
// current tree, so an existing install keeps its config and Java runtime.
func migrateLayout() {
	_ = os.MkdirAll(dataDir(), 0755)
	old := filepath.Join(mustHome(), ".valkaw-launcher")
	launcher := filepath.Join(dataDir(), "launcher")
	if dirExists(old) && !dirExists(launcher) {
		if err := os.Rename(old, launcher); err == nil {
			if !dirExists(javaDir()) {
				_ = os.Rename(filepath.Join(launcher, "java"), javaDir())
			}
		}
	}
	if dirExists(filepath.Join(launcher, "java")) && !dirExists(javaDir()) {
		_ = os.Rename(filepath.Join(launcher, "java"), javaDir())
	}
}

// adoptGameDir moves game files out of ~/.valkaw into ~/.valkaw/minecraft.
// Launcher data and the Java runtime stay where they are.
func (a *App) adoptGameDir() {
	dest := defaultGameDir()
	_ = os.MkdirAll(dest, 0755)
	for _, name := range []string{
		"assets", "libraries", "versions", "valkaw", "launcher_profiles.json",
		"gl", "vk", "mods", "logs", "crash-reports", "options.txt",
		"resourcepacks", "shaderpacks", "screenshots",
	} {
		src := filepath.Join(dataDir(), name)
		if !fileExists(src) || fileExists(filepath.Join(dest, name)) {
			continue
		}
		_ = os.Rename(src, filepath.Join(dest, name))
	}
	a.config.GameDir = dest
}

func (a *App) Startup(ctx context.Context) {
	a.ctx = ctx
	migrateLayout()
	a.cfgPath = filepath.Join(launcherDir(), "config.json")
	if data, err := os.ReadFile(a.cfgPath); err == nil {
		_ = json.Unmarshal(data, &a.config)
	}
	a.config.LauncherVersion = a.version
	a.config.Platform = runtime.GOOS
	if a.config.MemoryMB == 0 {
		a.config.MemoryMB = 4096
	}
	a.config.WindowWidth, a.config.WindowHeight = windowSize(a.config.WindowWidth, a.config.WindowHeight)
	if a.config.Profile == "" {
		a.config.Profile = "gl"
	}
	if filepath.Clean(a.config.GameDir) == filepath.Clean(dataDir()) {
		a.adoptGameDir()
	}
	if a.config.GameDir == "" || a.config.GameDir == filepath.Join(mustHome(), ".minecraft") {
		a.config.GameDir = defaultGameDir()
	}
	if a.config.JavaPath != "" && a.config.JavaPath != "java" && !fileExists(a.config.JavaPath) {
		a.config.JavaPath = "java" // the JRE may have moved or been removed
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
	return launcherDir()
}

func dirExists(path string) bool {
	fi, err := os.Stat(path)
	return err == nil && fi.IsDir()
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
	c.LauncherVersion = a.version
	c.Platform = runtime.GOOS
	if c.GpuPref < 0 || c.GpuPref > 2 {
		c.GpuPref = 0
	}
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

// SetGpuPreference stores the Windows per-program GPU choice for the Java runtime.
// Windows keeps this in the registry, so it survives driver updates and applies
// to the JVM the launcher starts rather than to the launcher window.
func (a *App) SetGpuPreference(pref int) string {
	if pref < 0 || pref > 2 {
		return "error: invalid preference"
	}
	if runtime.GOOS != "windows" {
		return "error: GPU preference is Windows only"
	}
	java, _, err := findJava(a.config.JavaPath)
	if err != nil || !fileExists(java) {
		return "error: install Java 21 first"
	}
	if err := applyGpuPreference(java, pref); err != nil {
		return "error: " + err.Error()
	}
	a.config.GpuPref = pref
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

// fetchLatestRelease returns the newest release, or the newest release that
// carries an asset starting with assetPrefix. A nil release means nothing to
// compare against, which callers report as up to date.
func fetchLatestRelease(assetPrefix string) (*ghRelease, error) {
	client := &http.Client{Timeout: 10 * time.Second}
	url := releasesAPI
	if assetPrefix != "" {
		url = releasesList
	}
	req, _ := http.NewRequest(http.MethodGet, url, nil)
	req.Header.Set("User-Agent", "ValkawLauncher/1.0")
	resp, err := client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		return nil, fmt.Errorf("HTTP %d", resp.StatusCode)
	}
	if assetPrefix == "" {
		var r ghRelease
		if err := json.NewDecoder(resp.Body).Decode(&r); err != nil {
			return nil, err
		}
		if r.TagName == "" {
			return nil, errors.New("no release found")
		}
		return &r, nil
	}
	var all []ghRelease
	if err := json.NewDecoder(resp.Body).Decode(&all); err != nil {
		return nil, err
	}
	for i := range all {
		for _, asset := range all[i].Assets {
			if strings.HasPrefix(asset.Name, assetPrefix) {
				return &all[i], nil
			}
		}
	}
	return nil, nil
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
	r, err := fetchLatestRelease(updatePrefix())
	if err != nil {
		return "update check failed: " + err.Error()
	}
	if r == nil || !newerVersion(r.TagName, a.config.LauncherVersion) {
		return "up to date (" + a.config.LauncherVersion + ")"
	}
	return "update available: " + r.TagName + " (current " + a.config.LauncherVersion + ")"
}

// InstallUpdate downloads the latest launcher binary and restarts it.
func (a *App) InstallUpdate() string {
	r, err := fetchLatestRelease(updatePrefix())
	if err != nil {
		return "error: " + err.Error()
	}
	if r == nil || !newerVersion(r.TagName, a.config.LauncherVersion) {
		return "already up to date"
	}
	name := updateAssetName(r.TagName)
	assetURL := repoDownloads + name
	exe, err := os.Executable()
	if err != nil {
		return "error: " + err.Error()
	}
	if resolved, err := filepath.EvalSymlinks(exe); err == nil {
		exe = resolved
	}
	tmp := exe + ".new"
	_ = os.Remove(tmp)
	if err := downloadFile(assetURL, tmp, 0, "", func(n int64) {
		a.emitInstall("Downloading launcher update...", 0)
	}); err != nil {
		_ = os.Remove(tmp)
		return "error: " + err.Error()
	}
	if runtime.GOOS == "windows" {
		return a.swapWindows(exe, tmp)
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

// swapWindows hands the file move to a detached script: Windows refuses to
// replace an exe that is still running, so the script waits for this process to
// exit, moves the new binary into place, and starts it again.
func (a *App) swapWindows(exe, downloaded string) string {
	newExe := exe + ".new.exe"
	_ = os.Remove(newExe)
	if err := os.Rename(downloaded, newExe); err != nil {
		_ = os.Remove(downloaded)
		return "error: " + err.Error()
	}
	bat := exe + ".update.bat"
	script := "@echo off\r\n" +
		"ping -n 4 127.0.0.1 >nul\r\n" +
		"move /y \"" + newExe + "\" \"" + exe + "\" >nul\r\n" +
		"start \"\" \"" + exe + "\"\r\n" +
		"del \"%~f0\"\r\n"
	if err := os.WriteFile(bat, []byte(script), 0644); err != nil {
		return "error: " + err.Error()
	}
	cmd := exec.Command("cmd", "/c", bat)
	hideWindow(cmd)
	if err := cmd.Start(); err != nil {
		return "updated, restart launcher manually"
	}
	wailsruntime.Quit(a.ctx)
	return "updated, restarting"
}

// OpenReleases opens the releases page in the browser.
func (a *App) OpenReleases() {
	if a.ctx != nil {
		wailsruntime.BrowserOpenURL(a.ctx, "https://github.com/nlvtai25/valkaw-client/releases")
	}
}
