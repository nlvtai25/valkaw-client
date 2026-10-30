package core

import (
	"context"
	"encoding/json"
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
	// repoDownloadsAPI lists the published launcher builds. assets/downloads is
	// the only source of truth for them, not GitHub release tags.
	repoDownloadsAPI = "https://api.github.com/repos/nlvtai25/valkaw-client/contents/assets/downloads"
	releasesPage     = "https://github.com/nlvtai25/valkaw-client/releases/latest"
	releaseDL        = "https://github.com/nlvtai25/valkaw-client/releases/latest/download/"
)

// updatePrefix is the file name marker used by this platform's build inside
// assets/downloads.
func updatePrefix() string {
	if runtime.GOOS == "windows" {
		return "valkaw-launcher-win-"
	}
	return "valkaw-launcher-"
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
		WindowWidth:     1280,
		WindowHeight:    720,
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
	installDesktopShortcut()
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
	// Older builds could leave an unknown mode behind (garbage in, nothing
	// happens on launch), so fall back to the platform default.
	switch a.config.AfterRun {
	case "hide", "close", "appear":
	default:
		a.config.AfterRun = "hide"
		if runtime.GOOS == "windows" {
			a.config.AfterRun = "appear"
		}
	}
	a.syncUsername()
	_ = a.saveConfig()
}

// syncUsername picks up the name saved from the in-game account menu so the
// launcher chip matches what the player actually sees in game.
func (a *App) syncUsername() {
	profile := a.config.Profile
	if profile != "gl" && profile != "vk" {
		profile = "gl"
	}
	data, err := os.ReadFile(a.gamePath(profile, "config", "valkaw-client.json"))
	if err != nil {
		return
	}
	var saved struct {
		Username string `json:"username"`
	}
	if json.Unmarshal(data, &saved) == nil && saved.Username != "" {
		a.config.Username = saved.Username
	}
}

// GetResourceBrowserContext tells the resources browser which Minecraft build
// is active and where each resource kind is installed for that profile.
func (a *App) GetResourceBrowserContext() map[string]any {
	profile := a.activeProfile()
	return map[string]any{
		"gameVersion":  a.config.Version,
		"profile":      profile,
		"loader":       "fabric",
		"gameDir":      a.gamePath(profile),
		"platform":     a.config.Platform,
		"modsDir":      a.gamePath(profile, "mods"),
		"rpDir":        a.gamePath(profile, "resourcepacks"),
		"shaderDir":    a.gamePath(profile, "shaderpacks"),
		"profileLabel": map[string]string{"gl": "Sodium", "vk": "VulkanMod"}[profile],
	}
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

// downloadEntry is one file published under assets/downloads.
type downloadEntry struct {
	Name string `json:"name"`
}

// fetchDownloadDir lists the builds published under assets/downloads. That
// directory is the single source of truth for launcher builds, so the update
// check never depends on GitHub release tags.
func (a *App) fetchDownloadDir() ([]string, error) {
	client := &http.Client{Timeout: 10 * time.Second}
	req, _ := http.NewRequest(http.MethodGet, repoDownloadsAPI, nil)
	req.Header.Set("User-Agent", "ValkawLauncher/1.0")
	req.Header.Set("Accept", "application/vnd.github+json")
	resp, err := client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		return nil, fmt.Errorf("HTTP %d", resp.StatusCode)
	}
	var entries []downloadEntry
	if err := json.NewDecoder(resp.Body).Decode(&entries); err != nil {
		return nil, err
	}
	names := make([]string, 0, len(entries))
	for _, e := range entries {
		names = append(names, e.Name)
	}
	return names, nil
}

// latestUpdate picks the newest build for this platform from a file listing and
// returns its version and file name.
func latestUpdate(names []string) (string, string) {
	prefix := updatePrefix()
	suffix := fmt.Sprintf("-%s-%s", runtime.GOOS, runtime.GOARCH)
	if runtime.GOOS == "windows" {
		suffix += ".exe"
	}
	version, file := "", ""
	for _, name := range names {
		if !strings.HasPrefix(name, prefix) || !strings.HasSuffix(name, suffix) {
			continue
		}
		v := strings.TrimSuffix(strings.TrimPrefix(name, prefix), suffix)
		if version == "" || newerVersion(v, version) {
			version, file = v, name
		}
	}
	return version, file
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

// CheckUpdate compares the running version with the newest published build.
func (a *App) CheckUpdate() string {
	names, err := a.fetchDownloadDir()
	if err != nil {
		return "update check failed: " + err.Error()
	}
	latest, _ := latestUpdate(names)
	if latest == "" || !newerVersion(latest, a.config.LauncherVersion) {
		return "up to date (" + a.config.LauncherVersion + ")"
	}
	return "update available: " + latest + " (current " + a.config.LauncherVersion + ")"
}

// InstallUpdate downloads the latest launcher binary and restarts it.
func (a *App) InstallUpdate() string {
	names, err := a.fetchDownloadDir()
	if err != nil {
		return "error: " + err.Error()
	}
	latest, name := latestUpdate(names)
	if name == "" || !newerVersion(latest, a.config.LauncherVersion) {
		return "already up to date"
	}
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
		return "updated, restart launcher manually (" + latest + ")"
	}
	wailsruntime.Quit(a.ctx)
	return "updated to " + latest
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
