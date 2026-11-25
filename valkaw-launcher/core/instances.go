package core

import (
	"encoding/json"
	"errors"
	"fmt"
	"io"
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
)

// ---------------------------------------------------------------------------
// Instance model
// ---------------------------------------------------------------------------

// Instance is one self-contained Prism-style game instance. It lives under
// ~/.valkaw/instances/<id>/ and shares assets/libraries/versions with Valkaw
// mode through a.baseDir().
type Instance struct {
	ID           string    `json:"id"`
	Name         string    `json:"name"`
	MCVersion    string    `json:"mcVersion"`
	Loader       string    `json:"loader"` // "vanilla" | "fabric" | "neoforge"
	LoaderVersion string   `json:"loaderVersion"`
	JavaOverride  string    `json:"javaOverride"` // "" = auto; "17" = Temurin 17; else a path
	JavaArgs     string    `json:"javaArgs"`     // extra JVM args, space-separated
	AfterRun     string    `json:"afterRun"`     // "" = inherit global; hide|close|appear
	MemoryMB     int       `json:"memoryMB"`     // legacy single value (0 = inherit global)
	MemoryOverride bool    `json:"memoryOverride"` // true = use Min/Max below, false = inherit global
	MemoryMinMB  int       `json:"memoryMinMB"`
	MemoryMaxMB  int       `json:"memoryMaxMB"`
	Created      time.Time `json:"created"`
	ModpackSlug  string    `json:"modpackSlug,omitempty"`
	ModpackName  string    `json:"modpackName,omitempty"`
}

func instancesRoot() string { return filepath.Join(dataDir(), "instances") }
func registryPath() string   { return filepath.Join(instancesRoot(), "registry.json") }
func instanceDir(id string) string { return filepath.Join(instancesRoot(), id) }
func (a *App) sharedRoot() string { return a.baseDir() }

func loadRegistry() ([]Instance, error) {
	data, err := os.ReadFile(registryPath())
	if err != nil {
		if os.IsNotExist(err) {
			return nil, nil
		}
		return nil, err
	}
	var list []Instance
	if err := json.Unmarshal(data, &list); err != nil {
		return nil, err
	}
	return list, nil
}

func saveRegistry(list []Instance) error {
	if err := os.MkdirAll(instancesRoot(), 0755); err != nil {
		return err
	}
	data, err := json.MarshalIndent(list, "", "  ")
	if err != nil {
		return err
	}
	return os.WriteFile(registryPath(), data, 0644)
}

func slugify(s string) string {
	s = strings.ToLower(strings.TrimSpace(s))
	re := regexp.MustCompile(`[^a-z0-9]+`)
	s = re.ReplaceAllString(s, "-")
	s = strings.Trim(s, "-")
	if s == "" {
		s = "instance"
	}
	return s
}

func (a *App) findInstance(id string) (*Instance, error) {
	list, err := loadRegistry()
	if err != nil {
		return nil, err
	}
	for i := range list {
		if list[i].ID == id {
			return &list[i], nil
		}
	}
	return nil, errors.New("instance not found: " + id)
}

func (a *App) instanceIDAvailable(id string) bool {
	list, err := loadRegistry()
	if err != nil {
		return true
	}
	for _, in := range list {
		if in.ID == id {
			return false
		}
	}
	return true
}

// ListModpackInstances returns all registered instances.
func (a *App) ListModpackInstances() ([]Instance, error) {
	list, err := loadRegistry()
	if err != nil {
		return nil, err
	}
	sort.Slice(list, func(i, j int) bool { return list[i].Created.After(list[j].Created) })
	return list, nil
}

// GetInstance returns a single instance by id.
func (a *App) GetInstance(id string) (Instance, error) {
	in, err := a.findInstance(id)
	if err != nil {
		return Instance{}, err
	}
	return *in, nil
}

func (a *App) saveInstance(in Instance) error {
	in.ID = slugify(in.ID)
	if in.Name == "" {
		in.Name = in.ID
	}
	list, err := loadRegistry()
	if err != nil {
		return err
	}
	replaced := false
	for i := range list {
		if list[i].ID == in.ID {
			list[i] = in
			replaced = true
			break
		}
	}
	if !replaced {
		in.Created = time.Now()
		list = append(list, in)
	}
	if err := os.MkdirAll(instanceDir(in.ID), 0755); err != nil {
		return err
	}
	return saveRegistry(list)
}

// SaveInstanceSettings updates the mutable runtime settings of an instance
// (RAM, Java override, extra JVM args, after-run behaviour).
func (a *App) SaveInstanceSettings(id string, memoryOverride bool, memoryMin, memoryMax int, javaOverride, javaArgs, afterRun string) error {
	in, err := a.findInstance(id)
	if err != nil {
		return err
	}
	in.MemoryOverride = memoryOverride
	in.MemoryMinMB = memoryMin
	in.MemoryMaxMB = memoryMax
	in.JavaOverride = javaOverride
	in.JavaArgs = javaArgs
	switch afterRun {
	case "hide", "close", "appear":
		in.AfterRun = afterRun
	default:
		in.AfterRun = ""
	}
	return a.saveInstance(*in)
}

// DeleteInstance permanently removes an instance (game folder + registry entry).
func (a *App) DeleteInstance(id string) error {
	list, err := loadRegistry()
	if err != nil {
		return err
	}
	out := list[:0]
	found := false
	for _, in := range list {
		if in.ID == id {
			found = true
			continue
		}
		out = append(out, in)
	}
	if !found {
		return errors.New("instance not found: " + id)
	}
	if err := os.RemoveAll(instanceDir(id)); err != nil {
		return err
	}
	return saveRegistry(out)
}

func (a *App) OpenInstanceFolder(id string) error {
	dir := instanceDir(id)
	if err := os.MkdirAll(dir, 0755); err != nil {
		return err
	}
	return openFolder(dir)
}

func openFolder(dir string) error {
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

// ---------------------------------------------------------------------------
// Minecraft version / loader discovery
// ---------------------------------------------------------------------------

const (
	launcherManifestURL = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json"
	fabricMetaURL        = "https://meta.fabricmc.net/v2/versions/loader/"
	neoforgeMavenBase   = "https://maven.neoforged.net/releases/net/neoforged/neoforge/"
	neoforgeMetaURL     = neoforgeMavenBase + "maven-metadata.xml"
)

type metaManifest struct {
	Versions []struct {
		ID   string `json:"id"`
		Type string `json:"type"`
	} `json:"versions"`
}

// GetMinecraftVersions lists release versions, oldest first.
func (a *App) GetMinecraftVersions() ([]string, error) {
	var m metaManifest
	if err := getJSON(launcherManifestURL, &m); err != nil {
		return nil, err
	}
	var out []string
	for _, v := range m.Versions {
		if v.Type == "release" {
			out = append(out, v.ID)
		}
	}
	// The manifest is newest-first; flip for oldest-first.
	for i, j := 0, len(out)-1; i < j; i, j = i+1, j-1 {
		out[i], out[j] = out[j], out[i]
	}
	return out, nil
}

type fabricVersionsResp struct {
	Loader struct {
		Version string `json:"version"`
		Stable  bool   `json:"stable"`
	} `json:"loader"`
}

// GetLoaderVersions lists available loader versions for a Minecraft version.
func (a *App) GetLoaderVersions(mcVersion, loader string) ([]string, error) {
	switch loader {
	case "fabric":
		var list []fabricVersionsResp
		if err := getJSON(fabricMetaURL+mcVersion, &list); err != nil {
			return nil, err
		}
		var out []string
		for _, v := range list {
			out = append(out, v.Loader.Version)
		}
		return out, nil
	case "neoforge":
		return latestNeoForgeVersions()
	default:
		return nil, nil
	}
}

func latestFabricLoader(mcVersion string) (string, error) {
	var list []fabricVersionsResp
	if err := getJSON(fabricMetaURL+mcVersion, &list); err != nil {
		return "", err
	}
	for _, v := range list {
		if v.Loader.Stable {
			return v.Loader.Version, nil
		}
	}
	if len(list) > 0 {
		return list[0].Loader.Version, nil
	}
	return "", errors.New("no Fabric loader found for " + mcVersion)
}

var mavenVersionRe = regexp.MustCompile(`<version>([^<]+)</version>`)
var mavenLatestRe = regexp.MustCompile(`<latest>([^<]+)</latest>`)

func latestNeoForgeVersions() ([]string, error) {
	req, err := http.NewRequestWithContext(netCtx, http.MethodGet, neoforgeMetaURL, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("User-Agent", "ValkawLauncher/1.0")
	resp, err := httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	body, err := io.ReadAll(io.LimitReader(resp.Body, 2<<20))
	if err != nil {
		return nil, err
	}
	text := string(body)
	matches := mavenVersionRe.FindAllStringSubmatch(text, -1)
	if len(matches) == 0 {
		return nil, errors.New("no NeoForge versions found")
	}
	var out []string
	seen := map[string]bool{}
	for _, m := range matches {
		v := m[1]
		if strings.Contains(v, "-") { // skip -rc/-beta snapshots
			continue
		}
		if !seen[v] {
			seen[v] = true
			out = append(out, v)
		}
	}
	sort.Slice(out, func(i, j int) bool { return numericVersionLess(out[i], out[j]) })
	return out, nil
}

func latestNeoForge() (string, error) {
	versions, err := latestNeoForgeVersions()
	if err != nil {
		return "", err
	}
	if len(versions) == 0 {
		return "", errors.New("no stable NeoForge version")
	}
	return versions[len(versions)-1], nil
}

// numericVersionLess compares dotted numeric versions; used to sort versions
// so the "latest" is the greatest, not lexicographic.
func numericVersionLess(a, b string) bool {
	pa, pb := strings.Split(a, "."), strings.Split(b, ".")
	n := len(pa)
	if len(pb) > n {
		n = len(pb)
	}
	for i := 0; i < n; i++ {
		va, vb := 0, 0
		if i < len(pa) {
			va, _ = strconv.Atoi(pa[i])
		}
		if i < len(pb) {
			vb, _ = strconv.Atoi(pb[i])
		}
		if va != vb {
			return va < vb
		}
	}
	return false
}

// ---------------------------------------------------------------------------
// Java
// ---------------------------------------------------------------------------

// requireJavaForMC maps a Minecraft version to the Java major it needs.
func requireJavaForMC(mc string) int {
	parts := strings.Split(mc, ".")
	if len(parts) == 0 {
		return 21
	}
	if parts[0] == "1" {
		if len(parts) < 2 {
			return 8
		}
		major, _ := strconv.Atoi(parts[1])
		minor := 0
		if len(parts) > 2 {
			minor, _ = strconv.Atoi(parts[2])
		}
		switch {
		case major <= 16:
			return 8
		case major <= 19:
			return 17
		case major == 20:
			if minor <= 4 {
				return 17
			}
			return 21
		default:
			return 21
		}
	}
	major, _ := strconv.Atoi(parts[0])
	if major >= 26 {
		return 25
	}
	return 21
}

func temurinURL(major int) string {
	plat, arch := javaPlatform()
	if plat == "" || arch == "" {
		return ""
	}
	return fmt.Sprintf("https://api.adoptium.net/v3/binary/latest/%d/ga/%s/%s/jre/hotspot/normal/eclipse", major, plat, arch)
}

// installTemurin downloads a Temurin JRE of the given major into
// ~/.valkaw/jre/<major>/ and returns the path to its java binary.
func (a *App) installTemurin(major int) (string, error) {
	url := temurinURL(major)
	if url == "" {
		return "", fmt.Errorf("no Temurin %d build for %s/%s", major, runtime.GOOS, runtime.GOARCH)
	}
	ext := ".tar.gz"
	if runtime.GOOS == "windows" {
		ext = ".zip"
	}
	archive := filepath.Join(launcherDir(), fmt.Sprintf("temurin%d%s", major, ext))
	_ = os.Remove(archive)
	defer os.Remove(archive)
	size := remoteSize(url)
	pt := &progressTracker{}
	stage := fmt.Sprintf("Downloading Temurin %d...", major)
	a.emitInstall(stage, 2)
	if err := downloadFile(url, archive, size, "", func(n int64) {
		a.progress(pt, stage, 2, 90, n, size)
	}); err != nil {
		return "", err
	}
	dest := filepath.Join(javaDir(), strconv.Itoa(major))
	_ = os.RemoveAll(dest)
	if err := os.MkdirAll(dest, 0755); err != nil {
		return "", err
	}
	a.emitInstall(fmt.Sprintf("Installing Temurin %d...", major), 92)
	if err := extractArchive(archive, dest); err != nil {
		return "", err
	}
	for _, pat := range []string{
		filepath.Join(dest, "*", "bin", javaExe()),
		filepath.Join(dest, "*", "Contents", "Home", "bin", javaExe()),
		filepath.Join(dest, "bin", javaExe()),
	} {
		matches, _ := filepath.Glob(pat)
		for _, m := range matches {
			if javaMajor(m) == major {
				a.emitInstall(fmt.Sprintf("Temurin %d ready", major), 100)
				return m, nil
			}
		}
	}
	return "", errors.New("Temurin not found in downloaded archive")
}

func findTemurin(major int) string {
	base := filepath.Join(javaDir(), strconv.Itoa(major))
	for _, pat := range []string{
		filepath.Join(base, "*", "bin", javaExe()),
		filepath.Join(base, "*", "Contents", "Home", "bin", javaExe()),
		filepath.Join(base, "bin", javaExe()),
	} {
		matches, _ := filepath.Glob(pat)
		for _, m := range matches {
			if javaMajor(m) == major {
				return m
			}
		}
	}
	return ""
}

// ListInstalledJavaMajors lists the Temurin majors currently installed.
func (a *App) ListInstalledJavaMajors() []int {
	entries, err := os.ReadDir(javaDir())
	if err != nil {
		return nil
	}
	var out []int
	for _, e := range entries {
		if !e.IsDir() {
			continue
		}
		if m, err := strconv.Atoi(e.Name()); err == nil && findTemurin(m) != "" {
			out = append(out, m)
		}
	}
	sort.Ints(out)
	return out
}

// InstallTemurin downloads a Temurin JRE for the instance (per-instance Java).
func (a *App) InstallTemurin(major int) (string, error) {
	return a.installTemurin(major)
}

// resolveJavaForVersion returns a Java binary whose major satisfies mc's
// requirement: any available Java of at least that major, else an installed
// Temurin of exactly that major, else install one.
func (a *App) resolveJavaForVersion(mc string) (string, error) {
	req := requireJavaForMC(mc)
	if p, major, err := a.resolvedJava(); err == nil && major >= req {
		return p, nil
	}
	if p := findTemurin(req); p != "" {
		return p, nil
	}
	if p, err := a.installTemurin(req); err == nil {
		return p, nil
	}
	return "", fmt.Errorf("no Java %d available; install Temurin %d first", req, req)
}

// resolveJavaForInstance resolves the Java binary for an instance.
func (a *App) resolveJavaForInstance(in Instance) (string, error) {
	ov := strings.TrimSpace(in.JavaOverride)
	if ov != "" {
		if m, err := strconv.Atoi(ov); err == nil {
			if p := findTemurin(m); p != "" {
				return p, nil
			}
			return a.installTemurin(m)
		}
		if !fileExists(ov) {
			return "", fmt.Errorf("Java path not found: %s", ov)
		}
		return ov, nil
	}
	return a.resolveJavaForVersion(in.MCVersion)
}

// ---------------------------------------------------------------------------
// Vanilla install
// ---------------------------------------------------------------------------

// installVanillaFor downloads the vanilla game, libraries and assets for a
// given version into the shared root (a.baseDir()).
func (a *App) installVanillaFor(dir, versionID string) error {
	vanillaDir := filepath.Join(dir, "versions", versionID)
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
	type libJob struct {
		url  string
		dest string
		size int64
		sha1 string
	}
	jobs := make(chan libJob)
	errs := make(chan error, 1)
	var wg sync.WaitGroup
	workers := 8
	for i := 0; i < workers; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for j := range jobs {
				err := downloadFile(j.url, j.dest, j.size, j.sha1, func(n int64) {
					atomic.AddInt64(&done, n)
					a.progress(pt, "Downloading libraries...", 15, 40, atomic.LoadInt64(&done), total)
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
	for _, l := range libs {
		p := libraryPath(l)
		if p == "" {
			continue
		}
		libURL := l.Downloads.Artifact.URL
		if libURL == "" {
			libURL = strings.TrimRight(l.URL, "/") + "/" + p
		}
		job := libJob{
			url:  libURL,
			dest: filepath.Join(dir, "libraries", filepath.FromSlash(p)),
			size: l.Downloads.Artifact.Size,
			sha1: l.Downloads.Artifact.SHA1,
		}
		select {
		case err := <-errs:
			close(jobs)
			wg.Wait()
			return err
		case jobs <- job:
		}
	}
	close(jobs)
	wg.Wait()
	select {
	case err := <-errs:
		return err
	default:
	}

	a.emitInstall("Downloading assets...", 40)
	if err := a.installAssets(version.AssetIndex.ID, version.AssetIndex.URL); err != nil {
		return err
	}
	if version.Logging.Client.File.URL != "" {
		dest := a.installPath("assets", "log_configs", version.Logging.Client.File.ID)
		_ = downloadFile(version.Logging.Client.File.URL, dest, version.Logging.Client.File.Size, version.Logging.Client.File.SHA1, nil)
	}
	return nil
}

func (a *App) ensureVanilla(dir, mcVersion string) error {
	vanillaPath := filepath.Join(dir, "versions", mcVersion, mcVersion+".json")
	clientJar := filepath.Join(dir, "versions", mcVersion, mcVersion+".jar")
	if fileExists(vanillaPath) && fileExists(clientJar) {
		return nil
	}
	if err := a.installVanillaFor(dir, mcVersion); err != nil {
		return err
	}
	return a.extractNativesFor(dir, mcVersion)
}

// extractNativesFor extracts native libraries for a version id into
// <root>/versions/<id>/natives.
func (a *App) extractNativesFor(dir, id string) error {
	v, err := loadVersion(dir, id)
	if err != nil {
		return err
	}
	nativesDir := filepath.Join(dir, "versions", id, "natives")
	if err := os.MkdirAll(nativesDir, 0755); err != nil {
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
		jar := filepath.Join(dir, "libraries", filepath.FromSlash(p))
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
