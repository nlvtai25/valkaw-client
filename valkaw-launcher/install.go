package main

import (
	"archive/tar"
	"archive/zip"
	"compress/gzip"
	"context"
	"crypto/sha1"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"os/exec"
	"path"
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
		filepath.Join(home, ".local/share/jdk-*/bin", javaExe()),
		filepath.Join(home, ".jdks/*/bin", javaExe()),
		"/usr/lib/jvm/*/bin/" + javaExe(),
		"/opt/*/bin/" + javaExe(),
		filepath.Join(configDir(), "java", "*", "bin", javaExe()),
		filepath.Join(configDir(), "java", "*", "Contents", "Home", "bin", javaExe()),
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

// Prereq reports what the launcher still needs before a launch can start.
type Prereq struct {
	Java         bool   `json:"java"`
	JavaText     string `json:"javaText"`
	Files        bool   `json:"files"`
	FilesText    string `json:"filesText"`
	Graphics     bool   `json:"graphics"`
	GraphicsAPI  string `json:"graphicsApi"`
	GraphicsText string `json:"graphicsText"`
	Ready        bool   `json:"ready"`
}

// CheckRequirements inspects Java, the installed game files and the GPU driver.
func (a *App) CheckRequirements() Prereq {
	p := Prereq{Java: true, Files: true, Graphics: true}
	if _, _, err := findJava(a.config.JavaPath); err != nil {
		p.Java, p.JavaText = false, err.Error()
	}
	if err := a.checkInstalled(); err != nil {
		p.Files, p.FilesText = false, err.Error()
	}
	profile := a.config.Profile
	if profile != "gl" && profile != "vk" {
		profile = "gl"
	}
	p.GraphicsAPI, p.Graphics, p.GraphicsText = graphicsState(profile)
	p.Ready = p.Java && p.Files
	return p
}

const javaBinaryURL = "https://api.adoptium.net/v3/binary/latest/21/ga/%s/%s/jre/hotspot/normal/eclipse"

// InstallJava downloads a Java 21 runtime (Temurin JRE) and points the launcher at it.
func (a *App) InstallJava() string {
	plat, arch := javaPlatform()
	if plat == "" || arch == "" {
		return "error: no Java 21 build for " + runtime.GOOS + "/" + runtime.GOARCH
	}
	ext := ".tar.gz"
	if plat == "windows" {
		ext = ".zip"
	}
	a.emitInstall("Downloading Java 21...", 2)
	archive := filepath.Join(configDir(), "java21"+ext)
	_ = os.Remove(archive)
	url := fmt.Sprintf(javaBinaryURL, plat, arch)
	size := remoteSize(url)
	pt := &progressTracker{}
	if err := downloadFile(url, archive, size, "", func(n int64) {
		a.progress(pt, "Downloading Java 21...", 2, 90, n, size)
	}); err != nil {
		return "error: " + err.Error()
	}
	defer os.Remove(archive)
	a.emitInstall("Installing Java 21...", 92)
	dest := filepath.Join(configDir(), "java")
	_ = os.RemoveAll(dest)
	if err := os.MkdirAll(dest, 0755); err != nil {
		return "error: " + err.Error()
	}
	if err := extractArchive(archive, dest); err != nil {
		return "error: " + err.Error()
	}
	for _, pattern := range []string{
		filepath.Join(dest, "*", "bin", javaExe()),
		filepath.Join(dest, "*", "Contents", "Home", "bin", javaExe()),
	} {
		matches, _ := filepath.Glob(pattern)
		for _, m := range matches {
			if major := javaMajor(m); major >= 21 {
				a.config.JavaPath = m
				_ = a.saveConfig()
				a.emitInstall("Java 21 ready", 100)
				return "ok"
			}
		}
	}
	return "error: Java 21 not found in the downloaded files"
}

// remoteSize returns the download size of a URL, or 0 when unknown.
func remoteSize(rawURL string) int64 {
	req, err := http.NewRequestWithContext(context.Background(), http.MethodHead, rawURL, nil)
	if err != nil {
		return 0
	}
	req.Header.Set("User-Agent", "ValkawLauncher/1.0")
	resp, err := httpClient.Do(req)
	if err != nil {
		return 0
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return 0
	}
	return resp.ContentLength
}

func javaPlatform() (string, string) {
	plat, arch := "", ""
	switch runtime.GOOS {
	case "linux":
		plat = "linux"
	case "windows":
		plat = "windows"
	case "darwin":
		plat = "mac"
	}
	switch runtime.GOARCH {
	case "amd64":
		arch = "x64"
	case "arm64":
		arch = "aarch64"
	case "386":
		arch = "x86"
	}
	return plat, arch
}

func javaExe() string {
	if runtime.GOOS == "windows" {
		return "java.exe"
	}
	return "java"
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

func clientCacheDir() string {
	return filepath.Join(configDir(), "clients")
}

func clientJarPath(version, profile string) string {
	return filepath.Join(clientCacheDir(), version, profileDir(profile)+".jar")
}

func clientJar(version, profile string) ([]byte, error) {
	return os.ReadFile(clientJarPath(version, profile))
}

// latestClientVersion reads the tag from the /releases/latest redirect. The static
// URL is used instead of the REST API because unauthenticated API calls hit the
// 60 requests/hour/IP limit.
func latestClientVersion() (string, error) {
	client := &http.Client{
		Timeout: 30 * time.Second,
		CheckRedirect: func(req *http.Request, via []*http.Request) error {
			return http.ErrUseLastResponse
		},
	}
	req, err := http.NewRequestWithContext(context.Background(), http.MethodGet, releasesPage, nil)
	if err != nil {
		return "", err
	}
	req.Header.Set("User-Agent", "ValkawLauncher/1.0")
	resp, err := client.Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()
	tag := tagVersion(resp.Header.Get("Location"))
	if tag == "" {
		return "", fmt.Errorf("no release tag at %s (HTTP %d)", releasesPage, resp.StatusCode)
	}
	return tag, nil
}

// tagVersion turns .../releases/tag/v1.0.1 into 1.0.1.
func tagVersion(location string) string {
	u, err := url.Parse(location)
	if err != nil {
		return ""
	}
	tag := strings.TrimPrefix(path.Base(u.Path), "v")
	if tag == "." || tag == "/" || tag == "latest" {
		return ""
	}
	return tag
}

// ensureClientJar pulls the newest client build from GitHub releases into the local
// cache. An existing cache copy is used when the download fails.
func ensureClientJar(version, profile string) error {
	path := clientJarPath(version, profile)
	tmp := path + ".new"
	_ = os.Remove(tmp)
	clientVersion, err := latestClientVersion()
	if err == nil {
		want := fmt.Sprintf("valkawclient-%s-%s.jar", clientVersion, profileDir(profile))
		err = downloadFile(releaseDL+want, tmp, 0, "", nil)
	}
	if err != nil {
		_ = os.Remove(tmp)
		if _, e := os.Stat(path); e == nil {
			return nil
		}
		return err
	}
	if err := os.MkdirAll(filepath.Dir(path), 0755); err != nil {
		return err
	}
	if err := os.Rename(tmp, path); err != nil {
		_ = os.Remove(tmp)
		return err
	}
	return nil
}

func availableVersions() []string {
	dirs, _ := os.ReadDir(clientCacheDir())
	out := make([]string, 0, len(dirs))
	for _, d := range dirs {
		if d.IsDir() {
			out = append(out, d.Name())
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
	a.emitInstall("Fetching Valkaw client...", 1)
	if err := ensureClientJar(a.gameVersion(), profile); err != nil {
		return "error: " + err.Error()
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

// extractArchive unpacks a .zip or .tar.gz download into dest.
func extractArchive(archive, dest string) error {
	if strings.HasSuffix(archive, ".zip") {
		return unzipTo(archive, dest)
	}
	return untarGz(archive, dest)
}

// safeJoin keeps archive entries inside dest.
func safeJoin(dest, name string) (string, bool) {
	target := filepath.Join(dest, filepath.FromSlash(name))
	return target, strings.HasPrefix(target, filepath.Clean(dest)+string(os.PathSeparator))
}

func untarGz(archive, dest string) error {
	f, err := os.Open(archive)
	if err != nil {
		return err
	}
	defer f.Close()
	gz, err := gzip.NewReader(f)
	if err != nil {
		return err
	}
	defer gz.Close()
	tr := tar.NewReader(gz)
	for {
		h, err := tr.Next()
		if err == io.EOF {
			return nil
		}
		if err != nil {
			return err
		}
		target, ok := safeJoin(dest, h.Name)
		if !ok {
			continue
		}
		switch h.Typeflag {
		case tar.TypeDir:
			if err := os.MkdirAll(target, 0755); err != nil {
				return err
			}
		case tar.TypeReg:
			if err := os.MkdirAll(filepath.Dir(target), 0755); err != nil {
				return err
			}
			mode := h.FileInfo().Mode().Perm()
			if mode == 0 {
				mode = 0644
			}
			out, err := os.OpenFile(target, os.O_CREATE|os.O_TRUNC|os.O_WRONLY, mode)
			if err != nil {
				return err
			}
			_, err = io.Copy(out, tr)
			_ = out.Close()
			if err != nil {
				return err
			}
		}
	}
}

func unzipTo(archive, dest string) error {
	zr, err := zip.OpenReader(archive)
	if err != nil {
		return err
	}
	defer zr.Close()
	for _, f := range zr.File {
		target, ok := safeJoin(dest, f.Name)
		if !ok {
			continue
		}
		if f.FileInfo().IsDir() {
			if err := os.MkdirAll(target, 0755); err != nil {
				return err
			}
			continue
		}
		if err := os.MkdirAll(filepath.Dir(target), 0755); err != nil {
			return err
		}
		rc, err := f.Open()
		if err != nil {
			return err
		}
		mode := f.Mode().Perm()
		if mode == 0 {
			mode = 0644
		}
		out, err := os.OpenFile(target, os.O_CREATE|os.O_TRUNC|os.O_WRONLY, mode)
		if err != nil {
			_ = rc.Close()
			return err
		}
		_, err = io.Copy(out, rc)
		_ = out.Close()
		_ = rc.Close()
		if err != nil {
			return err
		}
	}
	return nil
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
		return "error: " + err.Error() + " — finish the setup first"
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
