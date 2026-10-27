package main

import (
	"archive/zip"
	"context"
	"crypto/sha1"
	"embed"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"io/fs"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"runtime"
	"sort"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	wailsruntime "github.com/wailsapp/wails/v2/pkg/runtime"
)

const (
	defaultMCVersion   = "1.21.11"
	fabricLoader       = "0.20.1"
	fabricInstaller    = "1.0.1"
	versionManifestURL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
	fabricInstallerURL = "https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.0.1/fabric-installer-1.0.1.jar"
)

//go:embed assets/clients/*/*.jar
var clientFS embed.FS

var httpClient = &http.Client{Timeout: 30 * time.Minute}

type versionJSON struct {
	ID           string `json:"id"`
	InheritsFrom string `json:"inheritsFrom"`
	MainClass    string `json:"mainClass"`
	Type         string `json:"type"`
	Assets       string `json:"assets"`
	AssetIndex   struct {
		ID   string `json:"id"`
		URL  string `json:"url"`
		SHA1 string `json:"sha1"`
		Size int64  `json:"size"`
	} `json:"assetIndex"`
	Libraries []library `json:"libraries"`
	Arguments struct {
		Game []any `json:"game"`
		JVM  []any `json:"jvm"`
	} `json:"arguments"`
	Downloads struct {
		Client struct {
			URL  string `json:"url"`
			SHA1 string `json:"sha1"`
			Size int64  `json:"size"`
		} `json:"client"`
	} `json:"downloads"`
	Logging struct {
		Client struct {
			Argument string `json:"argument"`
			File     struct {
				ID   string `json:"id"`
				URL  string `json:"url"`
				SHA1 string `json:"sha1"`
				Size int64  `json:"size"`
			} `json:"file"`
		} `json:"client"`
	} `json:"logging"`
}

type library struct {
	Name      string `json:"name"`
	URL       string `json:"url"`
	Downloads struct {
		Artifact struct {
			Path string `json:"path"`
			URL  string `json:"url"`
			SHA1 string `json:"sha1"`
			Size int64  `json:"size"`
		} `json:"artifact"`
	} `json:"downloads"`
	Rules []rule `json:"rules"`
}

type rule struct {
	Action   string          `json:"action"`
	OS       ruleOS          `json:"os"`
	Features map[string]bool `json:"features"`
}

type ruleOS struct {
	Name    string `json:"name"`
	Arch    string `json:"arch"`
	Version string `json:"version"`
}

type assetIndex struct {
	Objects map[string]struct {
		Hash string `json:"hash"`
		Size int64  `json:"size"`
	} `json:"objects"`
}

type mojangManifest struct {
	Versions []struct {
		ID  string `json:"id"`
		URL string `json:"url"`
	} `json:"versions"`
}

type progressTracker struct {
	mu      sync.Mutex
	last    time.Time
	lastPct int
}

func (a *App) emitInstall(stage string, pct int) {
	if a.ctx != nil {
		wailsruntime.EventsEmit(a.ctx, "install:progress", stage, pct)
	}
}

func (a *App) progress(p *progressTracker, stage string, lo, hi int, done, total int64) {
	pct := hi
	if total > 0 {
		pct = lo + int(done*int64(hi-lo)/total)
		if pct > hi {
			pct = hi
		}
	}
	p.mu.Lock()
	if pct == p.lastPct && time.Since(p.last) < 250*time.Millisecond {
		p.mu.Unlock()
		return
	}
	p.last = time.Now()
	p.lastPct = pct
	p.mu.Unlock()
	a.emitInstall(stage, pct)
}

// DetectJava finds a Java 21+ runtime and saves it.
func (a *App) DetectJava() string {
	path, major, err := findJava(a.config.JavaPath)
	if err != nil {
		return "error: " + err.Error()
	}
	a.config.JavaPath = path
	_ = a.saveConfig()
	return fmt.Sprintf("%s (Java %d)", path, major)
}

func findJava(configured string) (string, int, error) {
	var candidates []string
	seen := map[string]bool{}
	add := func(p string) {
		if p == "" {
			return
		}
		p = filepath.Clean(p)
		if !seen[p] {
			seen[p] = true
			candidates = append(candidates, p)
		}
	}
	if configured != "" && configured != "java" {
		if fi, err := os.Stat(configured); err == nil && !fi.IsDir() {
			if major := javaMajor(configured); major >= 21 {
				return configured, major, nil
			}
		}
	}
	if home := os.Getenv("JAVA_HOME"); home != "" {
		add(filepath.Join(home, "bin", "java"))
	}
	if p, err := exec.LookPath("java"); err == nil {
		add(p)
	}
	home, _ := os.UserHomeDir()
	for _, pattern := range []string{
		filepath.Join(home, ".local/share/jdk-*/bin/java"),
		filepath.Join(home, ".jdks/*/bin/java"),
		"/usr/lib/jvm/*/bin/java",
		"/opt/*/bin/java",
	} {
		matches, _ := filepath.Glob(pattern)
		for _, m := range matches {
			add(m)
		}
	}
	best, bestMajor := "", 0
	for _, p := range candidates {
		if fi, err := os.Stat(p); err != nil || fi.IsDir() {
			continue
		}
		if major := javaMajor(p); major >= 21 && (bestMajor == 0 || major < bestMajor) {
			best, bestMajor = p, major
		}
	}
	if best != "" {
		return best, bestMajor, nil
	}
	return "", 0, errors.New("Java 21+ not found; install Java 21 or set the Java path")
}

var javaVersionRe = regexp.MustCompile(`version "([0-9]+)(?:\.([0-9]+))?`)

func javaMajor(path string) int {
	out, err := exec.Command(path, "-version").CombinedOutput()
	if err != nil {
		return 0
	}
	m := javaVersionRe.FindStringSubmatch(string(out))
	if len(m) == 0 {
		return 0
	}
	major, _ := strconv.Atoi(m[1])
	if major == 1 && len(m) > 2 && m[2] != "" {
		major, _ = strconv.Atoi(m[2])
	}
	return major
}

func (a *App) gameVersion() string {
	if v := strings.TrimSpace(a.config.Version); v != "" {
		return v
	}
	return defaultMCVersion
}

func (a *App) fabricID() string {
	return fmt.Sprintf("fabric-loader-%s-%s", fabricLoader, a.gameVersion())
}

func profileDir(profile string) string {
	if profile == "vk" {
		return "vulkan"
	}
	return "sodium"
}

func (a *App) baseDir() string {
	if strings.TrimSpace(a.config.GameDir) == "" {
		return defaultGameDir()
	}
	return a.config.GameDir
}

func (a *App) installPath(parts ...string) string {
	return filepath.Join(append([]string{a.baseDir()}, parts...)...)
}

func (a *App) gamePath(profile string, parts ...string) string {
	return filepath.Join(append([]string{a.baseDir(), profileDir(profile)}, parts...)...)
}

func clientJar(version, profile string) ([]byte, error) {
	return clientFS.ReadFile("assets/clients/" + version + "/" + profileDir(profile) + ".jar")
}

func availableVersions() []string {
	matches, _ := fs.Glob(clientFS, "assets/clients/*/*.jar")
	seen := map[string]bool{}
	out := make([]string, 0, len(matches))
	for _, m := range matches {
		v := filepath.Base(filepath.Dir(m))
		if !seen[v] {
			seen[v] = true
			out = append(out, v)
		}
	}
	sort.Slice(out, func(i, j int) bool {
		a, b := versionParts(out[i]), versionParts(out[j])
		for k := 0; k < len(a) && k < len(b); k++ {
			if a[k] != b[k] {
				return a[k] > b[k]
			}
		}
		return len(a) > len(b)
	})
	return out
}

// GetVersions lists Minecraft versions that have a bundled Valkaw client build.
func (a *App) GetVersions() []string {
	if v := availableVersions(); len(v) > 0 {
		return v
	}
	return []string{defaultMCVersion}
}

// InstallClient downloads the vanilla game, Fabric loader and Valkaw mods.
func (a *App) InstallClient(profile string) string {
	a.installMu.Lock()
	if a.installing {
		a.installMu.Unlock()
		return "error: install already running"
	}
	a.installing = true
	a.installMu.Unlock()
	defer func() {
		a.installMu.Lock()
		a.installing = false
		a.installMu.Unlock()
	}()

	if profile != "gl" && profile != "vk" {
		profile = "gl"
	}
	if _, err := clientJar(a.gameVersion(), profile); err != nil {
		return "error: no Valkaw client build for " + a.gameVersion()
	}
	java, major, err := findJava(a.config.JavaPath)
	if err != nil {
		return "error: " + err.Error()
	}
	if major < 21 {
		return "error: Java 21+ required"
	}
	a.emitInstall("Preparing Fabric...", 2)
	if err := a.installFabric(java); err != nil {
		return "error: fabric: " + err.Error()
	}
	a.emitInstall("Downloading game files...", 10)
	if err := a.installVanilla(); err != nil {
		return "error: game files: " + err.Error()
	}
	a.emitInstall("Installing mods...", 96)
	if err := a.installMods(profile); err != nil {
		return "error: mods: " + err.Error()
	}
	a.emitInstall("Ready", 100)
	return "ok"
}

func (a *App) installFabric(java string) error {
	id := a.fabricID()
	profilePath := a.installPath("versions", id, id+".json")
	loaderPath := a.installPath("libraries", "net", "fabricmc", "fabric-loader", fabricLoader, "fabric-loader-"+fabricLoader+".jar")
	if fileExists(profilePath) && fileExists(loaderPath) {
		return nil
	}
	a.emitInstall("Preparing Fabric...", 3)

	// Fabric installer refuses to run without a launcher profile file.
	profilesPath := a.installPath("launcher_profiles.json")
	if !fileExists(profilesPath) {
		_ = os.MkdirAll(a.baseDir(), 0755)
		if err := os.WriteFile(profilesPath, []byte("{\"profiles\":{},\"settings\":{},\"version\":3}\n"), 0644); err != nil {
			return err
		}
	}

	installer := a.installPath("valkaw", "fabric-installer-"+fabricInstaller+".jar")
	if err := downloadFile(fabricInstallerURL, installer, 0, "", nil); err != nil {
		return err
	}
	a.emitInstall("Installing Fabric...", 6)
	cmd := exec.Command(java, "-jar", installer, "client",
		"-dir", a.baseDir(),
		"-mcversion", a.gameVersion(),
		"-loader", fabricLoader)
	out, err := cmd.CombinedOutput()
	if err != nil {
		msg := strings.TrimSpace(string(out))
		if len(msg) > 400 {
			msg = msg[len(msg)-400:]
		}
		return fmt.Errorf("%v: %s", err, msg)
	}
	if !fileExists(profilePath) {
		return errors.New("Fabric profile was not created")
	}
	a.emitInstall("Fabric installed", 10)
	return nil
}

func (a *App) installVanilla() error {
	versionID := a.gameVersion()
	vanillaDir := a.installPath("versions", versionID)
	vanillaPath := filepath.Join(vanillaDir, versionID+".json")
	if err := os.MkdirAll(vanillaDir, 0755); err != nil {
		return err
	}
	var manifest mojangManifest
	if err := getJSON(versionManifestURL, &manifest); err != nil {
		return err
	}
	var versionURL string
	for _, v := range manifest.Versions {
		if v.ID == versionID {
			versionURL = v.URL
			break
		}
	}
	if versionURL == "" {
		return fmt.Errorf("Minecraft %s not in manifest", versionID)
	}
	var version versionJSON
	if err := getJSON(versionURL, &version); err != nil {
		return err
	}
	data, _ := json.MarshalIndent(version, "", "  ")
	if err := os.WriteFile(vanillaPath, data, 0644); err != nil {
		return err
	}

	a.emitInstall("Downloading client jar...", 12)
	clientJarPath := filepath.Join(vanillaDir, versionID+".jar")
	if err := downloadFile(version.Downloads.Client.URL, clientJarPath, version.Downloads.Client.Size, version.Downloads.Client.SHA1, nil); err != nil {
		return err
	}

	var libs []library
	for _, l := range version.Libraries {
		if rulesAllow(l.Rules) {
			libs = append(libs, l)
		}
	}
	var total int64
	for _, l := range libs {
		total += l.Downloads.Artifact.Size
	}
	var done int64
	pt := &progressTracker{}
	for _, l := range libs {
		p := libraryPath(l)
		if p == "" {
			continue
		}
		dest := a.installPath("libraries", filepath.FromSlash(p))
		libURL := l.Downloads.Artifact.URL
		if libURL == "" {
			libURL = strings.TrimRight(l.URL, "/") + "/" + p
		}
		size := l.Downloads.Artifact.Size
		err := downloadFile(libURL, dest, size, l.Downloads.Artifact.SHA1, func(n int64) {
			atomic.AddInt64(&done, n)
			a.progress(pt, "Downloading libraries...", 15, 40, atomic.LoadInt64(&done), total)
		})
		if err != nil {
			return err
		}
	}

	a.emitInstall("Downloading assets...", 40)
	if err := a.installAssets(version.AssetIndex.ID, version.AssetIndex.URL); err != nil {
		return err
	}
	if version.Logging.Client.File.URL != "" {
		dest := a.installPath("assets", "log_configs", version.Logging.Client.File.ID)
		_ = downloadFile(version.Logging.Client.File.URL, dest, version.Logging.Client.File.Size, version.Logging.Client.File.SHA1, nil)
	}
	if err := a.extractNatives(); err != nil {
		return err
	}
	return nil
}

func (a *App) installAssets(indexID, indexURL string) error {
	indexPath := a.installPath("assets", "indexes", indexID+".json")
	if indexURL == "" {
		data, _ := os.ReadFile(a.installPath("versions", a.gameVersion(), a.gameVersion()+".json"))
		var v struct {
			AssetIndex struct {
				URL string `json:"url"`
			} `json:"assetIndex"`
		}
		if json.Unmarshal(data, &v) == nil && v.AssetIndex.URL != "" {
			indexURL = v.AssetIndex.URL
		}
	}
	if err := downloadFile(indexURL, indexPath, 0, "", nil); err != nil {
		return err
	}
	data, err := os.ReadFile(indexPath)
	if err != nil {
		return err
	}
	var idx assetIndex
	if err := json.Unmarshal(data, &idx); err != nil {
		return err
	}

	var total int64
	for _, obj := range idx.Objects {
		total += obj.Size
	}
	var done int64
	pt := &progressTracker{}
	jobs := make(chan struct {
		hash string
		size int64
	})
	errs := make(chan error, 1)
	var wg sync.WaitGroup
	workers := 8
	for i := 0; i < workers; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for j := range jobs {
				dest := a.installPath("assets", "objects", j.hash[:2], j.hash)
				objURL := "https://resources.download.minecraft.net/" + j.hash[:2] + "/" + j.hash
				err := downloadFile(objURL, dest, j.size, j.hash, func(n int64) {
					atomic.AddInt64(&done, n)
					a.progress(pt, "Downloading assets...", 40, 95, atomic.LoadInt64(&done), total)
				})
				if err != nil {
					select {
					case errs <- err:
					default:
					}
					return
				}
			}
		}()
	}
	for _, obj := range idx.Objects {
		select {
		case err := <-errs:
			close(jobs)
			wg.Wait()
			return err
		case jobs <- struct {
			hash string
			size int64
		}{obj.Hash, obj.Size}:
		}
	}
	close(jobs)
	wg.Wait()
	select {
	case err := <-errs:
		return err
	default:
	}
	return nil
}

func (a *App) installMods(profile string) error {
	version := a.gameVersion()
	modsDir := a.gamePath(profile, "mods")
	if err := os.MkdirAll(modsDir, 0755); err != nil {
		return err
	}
	for _, prefix := range []string{"fabric-api-", "sodium-", "sodium-extra-", "VulkanMod-", "vulkanmod-"} {
		removeByPrefix(modsDir, prefix)
	}
	jar, err := clientJar(version, profile)
	if err != nil {
		return err
	}
	return os.WriteFile(filepath.Join(modsDir, "valkawclient.jar"), jar, 0644)
}

func (a *App) extractNatives() error {
	id := a.fabricID()
	nativesDir := a.installPath("versions", id, "natives")
	if err := os.MkdirAll(nativesDir, 0755); err != nil {
		return err
	}
	v, err := loadVersion(a.baseDir(), a.fabricID())
	if err != nil {
		return err
	}
	extracted := 0
	for _, l := range v.Libraries {
		if !rulesAllow(l.Rules) {
			continue
		}
		p := libraryPath(l)
		if p == "" {
			continue
		}
		jar := a.installPath("libraries", filepath.FromSlash(p))
		n, err := extractNativeJar(jar, nativesDir)
		if err != nil {
			continue
		}
		extracted += n
	}
	if extracted == 0 {
		return errors.New("no native libraries found")
	}
	return nil
}

func extractNativeJar(jarPath, destDir string) (int, error) {
	zr, err := zip.OpenReader(jarPath)
	if err != nil {
		return 0, err
	}
	defer zr.Close()
	n := 0
	for _, f := range zr.File {
		name := strings.ToLower(f.Name)
		if !strings.HasSuffix(name, ".so") && !strings.HasSuffix(name, ".dll") && !strings.HasSuffix(name, ".dylib") && !strings.HasSuffix(name, ".jnilib") {
			continue
		}
		rc, err := f.Open()
		if err != nil {
			continue
		}
		outPath := filepath.Join(destDir, filepath.Base(f.Name))
		out, err := os.OpenFile(outPath, os.O_CREATE|os.O_TRUNC|os.O_WRONLY, 0755)
		if err == nil {
			_, _ = io.Copy(out, rc)
			_ = out.Close()
			n++
		}
		_ = rc.Close()
	}
	return n, nil
}

func loadVersion(gameDir, id string) (*versionJSON, error) {
	return loadVersionDepth(gameDir, id, 0)
}

func loadVersionDepth(gameDir, id string, depth int) (*versionJSON, error) {
	if depth > 5 {
		return nil, errors.New("version inheritance too deep")
	}
	path := filepath.Join(gameDir, "versions", id, id+".json")
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	var v versionJSON
	if err := json.Unmarshal(data, &v); err != nil {
		return nil, err
	}
	if v.InheritsFrom == "" {
		return &v, nil
	}
	parent, err := loadVersionDepth(gameDir, v.InheritsFrom, depth+1)
	if err != nil {
		return nil, err
	}
	parent.Libraries = append(parent.Libraries, v.Libraries...)
	parent.Arguments.Game = append(parent.Arguments.Game, v.Arguments.Game...)
	parent.Arguments.JVM = append(parent.Arguments.JVM, v.Arguments.JVM...)
	if v.MainClass != "" {
		parent.MainClass = v.MainClass
	}
	if v.AssetIndex.ID != "" {
		parent.AssetIndex.ID = v.AssetIndex.ID
	}
	if v.AssetIndex.URL != "" {
		parent.AssetIndex.URL = v.AssetIndex.URL
		parent.AssetIndex.SHA1 = v.AssetIndex.SHA1
		parent.AssetIndex.Size = v.AssetIndex.Size
	}
	if v.Downloads.Client.URL != "" {
		parent.Downloads = v.Downloads
	}
	if v.Logging.Client.File.URL != "" {
		parent.Logging = v.Logging
	}
	parent.ID = v.ID
	return parent, nil
}

func fullscreenEnabled(gameDir string) bool {
	data, err := os.ReadFile(filepath.Join(gameDir, "options.txt"))
	if err != nil {
		return false
	}
	for _, line := range strings.Split(string(data), "\n") {
		if strings.TrimSpace(line) == "fullscreen:true" {
			return true
		}
	}
	return false
}

func (a *App) Launch() string {
	java, major, err := findJava(a.config.JavaPath)
	if err != nil {
		return "error: " + err.Error()
	}
	if major < 21 {
		return "error: Java 21+ required"
	}
	profile := a.config.Profile
	if profile != "gl" && profile != "vk" {
		profile = "gl"
	}
	if err := a.checkInstalled(); err != nil {
		return "error: " + err.Error() + " — click Install"
	}
	versionID := a.gameVersion()
	id := a.fabricID()
	gameDir := a.gamePath(profile)
	if err := os.MkdirAll(gameDir, 0755); err != nil {
		return "error: " + err.Error()
	}
	v, err := loadVersion(a.baseDir(), id)
	if err != nil {
		return "error: " + err.Error()
	}

	var classpath []string
	for _, l := range v.Libraries {
		if !rulesAllow(l.Rules) {
			continue
		}
		p := libraryPath(l)
		if p == "" {
			continue
		}
		jar := a.installPath("libraries", filepath.FromSlash(p))
		if fileExists(jar) {
			classpath = append(classpath, jar)
		}
	}
	clientJarPath := a.installPath("versions", versionID, versionID+".jar")
	classpath = append(classpath, clientJarPath)

	natives := a.installPath("versions", id, "natives")
	repl := map[string]string{
		"natives_directory":     natives,
		"launcher_name":         "valkaw-launcher",
		"launcher_version":      a.config.LauncherVersion,
		"classpath":             strings.Join(classpath, string(os.PathListSeparator)),
		"classpath_separator":   string(os.PathListSeparator),
		"library_directory":     a.installPath("libraries"),
		"version_name":          id,
		"game_directory":        gameDir,
		"assets_root":           a.installPath("assets"),
		"assets_index_name":     v.AssetIndex.ID,
		"auth_player_name":      a.config.Username,
		"auth_uuid":             "00000000-0000-0000-0000-000000000000",
		"auth_access_token":     "0",
		"clientid":              "",
		"auth_xuid":             "",
		"user_type":             "legacy",
		"version_type":          "release",
		"resolution_width":      strconv.Itoa(a.config.WindowWidth),
		"resolution_height":     strconv.Itoa(a.config.WindowHeight),
		"quickPlayPath":         "",
		"quickPlaySingleplayer": "",
		"quickPlayMultiplayer":  "",
		"quickPlayRealms":       "",
	}

	jvmArgs := []string{
		fmt.Sprintf("-Xms%dM", a.config.MemoryMB),
		fmt.Sprintf("-Xmx%dM", a.config.MemoryMB),
		"-XX:+UnlockExperimentalVMOptions",
		"-XX:+UseG1GC",
		"-XX:+ParallelRefProcEnabled",
		"-XX:MaxGCPauseMillis=50",
		"-XX:+AlwaysPreTouch",
		"-XX:G1NewSizePercent=30",
		"-XX:G1MaxNewSizePercent=40",
		"-XX:G1HeapRegionSize=8M",
		"-XX:G1ReservePercent=20",
		"-XX:G1HeapWastePercent=5",
		"-XX:G1MixedGCCountTarget=4",
		"-XX:InitiatingHeapOccupancyPercent=15",
		"-XX:G1MixedGCLiveThresholdPercent=90",
		"-XX:G1RSetUpdatingPauseTimePercent=5",
		"-XX:SurvivorRatio=32",
		"-XX:+PerfDisableSharedMem",
		"-XX:MaxTenuringThreshold=1",
	}
	for _, arg := range resolveArgs(v.Arguments.JVM, repl, false) {
		if strings.TrimSpace(arg) != "" {
			jvmArgs = append(jvmArgs, expand(arg, repl))
		}
	}
	if v.Logging.Client.File.ID != "" {
		logPath := a.installPath("assets", "log_configs", v.Logging.Client.File.ID)
		if fileExists(logPath) {
			jvmArgs = append(jvmArgs, "-Dlog4j.configurationFile="+logPath)
		}
	}
	jvmArgs = append(jvmArgs, v.MainClass)
	gameArgs := resolveArgs(v.Arguments.Game, repl, false)
	// Passing --width/--height while options.txt has fullscreen:true makes Minecraft
	// toggle fullscreen after creating a 1600x900 window, which leaves a stale,
	// offset framebuffer on some drivers. Start fullscreen directly instead.
	if fullscreenEnabled(gameDir) {
		gameArgs = append(gameArgs, "--fullscreen")
	}
	jvmArgs = append(jvmArgs, gameArgs...)

	cmd := exec.Command(java, jvmArgs...)
	cmd.Dir = gameDir
	if env := gpuEnv(findGPU(a.config.GPU), profile); len(env) > 0 {
		cmd.Env = append(os.Environ(), env...)
	}
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	if err := cmd.Start(); err != nil {
		return "error: " + err.Error()
	}
	go func() { _ = cmd.Wait() }()
	switch a.config.AfterRun {
	case "hide":
		wailsruntime.WindowHide(a.ctx)
	case "close":
		wailsruntime.Quit(a.ctx)
	case "appear":
		wailsruntime.WindowShow(a.ctx)
	}
	return "launched"
}

// CheckInstalled reports whether the game files are ready.
func (a *App) CheckInstalled() string {
	if err := a.checkInstalled(); err != nil {
		return err.Error()
	}
	return "installed"
}

func (a *App) checkInstalled() error {
	id := a.fabricID()
	versionID := a.gameVersion()
	profile := a.config.Profile
	if profile != "gl" && profile != "vk" {
		profile = "gl"
	}
	if !fileExists(a.installPath("versions", id, id+".json")) {
		return errors.New("game not installed")
	}
	if !fileExists(a.installPath("versions", versionID, versionID+".jar")) {
		return errors.New("game not installed")
	}
	if !fileExists(a.gamePath(profile, "mods", "valkawclient.jar")) {
		return errors.New("client mod missing")
	}
	v, err := loadVersion(a.baseDir(), id)
	if err != nil {
		return err
	}
	if v.AssetIndex.ID == "" || !fileExists(a.installPath("assets", "indexes", v.AssetIndex.ID+".json")) {
		return errors.New("assets missing")
	}
	return nil
}

func rulesAllow(rules []rule) bool {
	if len(rules) == 0 {
		return true
	}
	allowed := false
	for _, r := range rules {
		if ruleMatches(r) {
			allowed = r.Action == "allow"
		}
	}
	return allowed
}

func ruleMatches(r rule) bool {
	if r.OS.Name != "" && r.OS.Name != osName() {
		return false
	}
	if r.OS.Arch != "" && r.OS.Arch != archName() {
		return false
	}
	for feature, want := range r.Features {
		if want && feature == "has_custom_resolution" {
			continue
		}
		if want {
			return false // launcher does not enable demo/quick-play features
		}
		_ = feature
	}
	return true
}

func osName() string {
	switch runtime.GOOS {
	case "darwin":
		return "osx"
	case "windows":
		return "windows"
	default:
		return "linux"
	}
}

func archName() string {
	switch runtime.GOARCH {
	case "amd64":
		return "x86_64"
	case "386":
		return "x86"
	case "arm64":
		return "arm64"
	default:
		return runtime.GOARCH
	}
}

func resolveArgs(args []any, repl map[string]string, features bool) []string {
	var out []string
	for _, a := range args {
		switch v := a.(type) {
		case string:
			out = append(out, expand(v, repl))
		case []any:
			for _, x := range v {
				if s, ok := x.(string); ok {
					out = append(out, expand(s, repl))
				}
			}
		case map[string]any:
			var rl []rule
			if raw, ok := v["rules"]; ok {
				b, _ := json.Marshal(raw)
				_ = json.Unmarshal(b, &rl)
			}
			if !rulesAllow(rl) {
				continue
			}
			switch val := v["value"].(type) {
			case string:
				out = append(out, expand(val, repl))
			case []any:
				for _, x := range val {
					if s, ok := x.(string); ok {
						out = append(out, expand(s, repl))
					}
				}
			}
		}
	}
	return out
}

var placeholderRe = regexp.MustCompile(`\$\{([^}]+)\}`)

func expand(s string, repl map[string]string) string {
	return placeholderRe.ReplaceAllStringFunc(s, func(m string) string {
		key := strings.TrimSuffix(strings.TrimPrefix(m, "${"), "}")
		return repl[key]
	})
}

func libraryPath(l library) string {
	if l.Downloads.Artifact.Path != "" {
		return l.Downloads.Artifact.Path
	}
	return mavenPath(l.Name)
}

func mavenPath(name string) string {
	parts := strings.Split(name, ":")
	if len(parts) < 3 {
		return ""
	}
	group := strings.ReplaceAll(parts[0], ".", "/")
	artifact, version := parts[1], parts[2]
	filename := artifact + "-" + version
	if len(parts) > 3 && parts[3] != "" {
		filename += "-" + parts[3]
	}
	return group + "/" + artifact + "/" + version + "/" + filename + ".jar"
}

func fileExists(path string) bool {
	fi, err := os.Stat(path)
	return err == nil && !fi.IsDir()
}

func removeByPrefix(dir, prefix string) {
	matches, _ := filepath.Glob(filepath.Join(dir, prefix+"*.jar"))
	for _, m := range matches {
		_ = os.Remove(m)
	}
}

func getJSON(rawURL string, out any) error {
	req, err := http.NewRequestWithContext(context.Background(), http.MethodGet, rawURL, nil)
	if err != nil {
		return err
	}
	req.Header.Set("User-Agent", "ValkawLauncher/1.0")
	resp, err := httpClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("HTTP %d from %s", resp.StatusCode, rawURL)
	}
	return json.NewDecoder(resp.Body).Decode(out)
}

func downloadFile(rawURL, dest string, size int64, sha1sum string, onDelta func(int64)) error {
	if fi, err := os.Stat(dest); err == nil && !fi.IsDir() && (size <= 0 || fi.Size() == size) {
		if onDelta != nil {
			onDelta(fi.Size())
		}
		return nil
	}
	if err := os.MkdirAll(filepath.Dir(dest), 0755); err != nil {
		return err
	}
	var lastErr error
	for attempt := 0; attempt < 3; attempt++ {
		if attempt > 0 {
			time.Sleep(time.Duration(attempt) * time.Second)
		}
		lastErr = downloadOnce(rawURL, dest, size, sha1sum, onDelta)
		if lastErr == nil {
			return nil
		}
	}
	return lastErr
}

func downloadOnce(rawURL, dest string, size int64, sha1sum string, onDelta func(int64)) error {
	tmp := dest + ".part"
	_ = os.Remove(tmp)
	req, err := http.NewRequestWithContext(context.Background(), http.MethodGet, rawURL, nil)
	if err != nil {
		return err
	}
	req.Header.Set("User-Agent", "ValkawLauncher/1.0")
	resp, err := httpClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("HTTP %d from %s", resp.StatusCode, rawURL)
	}
	f, err := os.Create(tmp)
	if err != nil {
		return err
	}
	h := sha1.New()
	buf := make([]byte, 64*1024)
	var written int64
	for {
		n, rerr := resp.Body.Read(buf)
		if n > 0 {
			if _, werr := f.Write(buf[:n]); werr != nil {
				_ = f.Close()
				_ = os.Remove(tmp)
				return werr
			}
			_, _ = h.Write(buf[:n])
			written += int64(n)
			if onDelta != nil {
				onDelta(int64(n))
			}
		}
		if rerr == io.EOF {
			break
		}
		if rerr != nil {
			_ = f.Close()
			_ = os.Remove(tmp)
			return rerr
		}
	}
	if err := f.Sync(); err != nil {
		_ = f.Close()
		return err
	}
	if err := f.Close(); err != nil {
		return err
	}
	if size > 0 && written != size {
		_ = os.Remove(tmp)
		return fmt.Errorf("size mismatch for %s: got %d, want %d", rawURL, written, size)
	}
	if sha1sum != "" {
		sum := hex.EncodeToString(h.Sum(nil))
		if !strings.EqualFold(sum, sha1sum) {
			_ = os.Remove(tmp)
			return fmt.Errorf("sha1 mismatch for %s", rawURL)
		}
	}
	return os.Rename(tmp, dest)
}
