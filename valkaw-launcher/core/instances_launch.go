package core

import (
	"archive/zip"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"sort"
	"strconv"
	"strings"
	"sync/atomic"
	"time"

	wailsruntime "github.com/wailsapp/wails/v2/pkg/runtime"
)

// ---------------------------------------------------------------------------
// Loader install
// ---------------------------------------------------------------------------

func (a *App) ensureFabric(dir, mcVersion, loaderVersion string) (string, error) {
	if loaderVersion == "" {
		lv, err := latestFabricLoader(mcVersion)
		if err != nil {
			return "", err
		}
		loaderVersion = lv
	}
	id := fmt.Sprintf("fabric-loader-%s-%s", loaderVersion, mcVersion)
	profilePath := filepath.Join(dir, "versions", id, id+".json")
	loaderPath := filepath.Join(dir, "libraries", "net", "fabricmc", "fabric-loader", loaderVersion, "fabric-loader-"+loaderVersion+".jar")
	if fileExists(profilePath) && fileExists(loaderPath) {
		_ = a.extractNativesFor(dir, id)
		return id, nil
	}

	profilesPath := filepath.Join(dir, "launcher_profiles.json")
	if !fileExists(profilesPath) {
		_ = os.MkdirAll(dir, 0755)
		if err := os.WriteFile(profilesPath, []byte("{\"profiles\":{},\"settings\":{},\"version\":3}\n"), 0644); err != nil {
			return "", err
		}
	}
	installer := a.installPath("valkaw", "fabric-installer-"+fabricInstaller+".jar")
	if err := downloadFile(fabricInstallerURL, installer, 0, "", nil); err != nil {
		return "", err
	}
	java, _, err := a.resolvedJava()
	if err != nil {
		return "", err
	}
	a.emitInstall("Installing Fabric...", 30)
	cmd := exec.Command(java, "-jar", installer, "client",
		"-dir", dir,
		"-mcversion", mcVersion,
		"-loader", loaderVersion)
	out, err := cmd.CombinedOutput()
	if err != nil {
		msg := strings.TrimSpace(string(out))
		if len(msg) > 400 {
			msg = msg[len(msg)-400:]
		}
		return "", fmt.Errorf("%v: %s", err, msg)
	}
	if !fileExists(profilePath) {
		return "", errors.New("Fabric profile was not created")
	}
	if err := a.extractNativesFor(dir, id); err != nil {
		return "", err
	}
	return id, nil
}

func (a *App) ensureNeoForge(dir, mcVersion, loaderVersion string) (string, error) {
	if loaderVersion == "" {
		lv, err := latestNeoForge()
		if err != nil {
			return "", err
		}
		loaderVersion = lv
	}
	id := fmt.Sprintf("neoforge-%s", loaderVersion)
	profilePath := filepath.Join(dir, "versions", id, id+".json")
	if fileExists(profilePath) {
		_ = a.extractNativesFor(dir, id)
		return id, nil
	}

	installer := a.installPath("valkaw", "neoforge-"+loaderVersion+"-installer.jar")
	url := neoforgeMavenBase + loaderVersion + "/neoforge-" + loaderVersion + "-installer.jar"
	if err := downloadFile(url, installer, 0, "", nil); err != nil {
		return "", err
	}
	java, _, err := a.resolvedJava()
	if err != nil {
		return "", err
	}
	a.emitInstall("Installing NeoForge...", 30)
	cmd := exec.Command(java, "-jar", installer, "--install-client", dir)
	out, err := cmd.CombinedOutput()
	if err != nil {
		msg := strings.TrimSpace(string(out))
		if len(msg) > 600 {
			msg = msg[len(msg)-600:]
		}
		return "", fmt.Errorf("%v: %s", err, msg)
	}
	if !fileExists(profilePath) {
		return "", errors.New("NeoForge profile was not created")
	}
	if err := a.extractNativesFor(dir, id); err != nil {
		return "", err
	}
	return id, nil
}

func loaderVersionID(in Instance) string {
	switch in.Loader {
	case "fabric":
		return fmt.Sprintf("fabric-loader-%s-%s", in.LoaderVersion, in.MCVersion)
	case "neoforge":
		return fmt.Sprintf("neoforge-%s", in.LoaderVersion)
	default:
		return in.MCVersion
	}
}

// ---------------------------------------------------------------------------
// Create instance
// ---------------------------------------------------------------------------

// CreateInstance builds a new instance: vanilla game + chosen loader, then a
// fresh empty game folder.
func (a *App) CreateInstance(name, mcVersion, loader, loaderVersion string) (Instance, error) {
	if name == "" || mcVersion == "" {
		return Instance{}, errors.New("name and Minecraft version required")
	}
	if loader == "" {
		loader = "vanilla"
	}
	switch loader {
	case "vanilla", "fabric", "neoforge":
	default:
		return Instance{}, errors.New("unknown loader: " + loader)
	}

	baseID := slugify(name)
	id := baseID
	for i := 2; !a.instanceIDAvailable(id); i++ {
		id = fmt.Sprintf("%s-%d", baseID, i)
	}

	in := Instance{
		ID:        id,
		Name:      strings.TrimSpace(name),
		MCVersion: mcVersion,
		Loader:    loader,
		Created:   time.Now(),
	}

	dir := a.sharedRoot()
	if err := a.ensureVanilla(dir, mcVersion); err != nil {
		return Instance{}, fmt.Errorf("vanilla: %w", err)
	}
	switch loader {
	case "fabric":
		lv, err := a.ensureFabric(dir, mcVersion, loaderVersion)
		if err != nil {
			return Instance{}, err
		}
		in.LoaderVersion = strings.TrimPrefix(strings.TrimSuffix(lv, "-"+mcVersion), "fabric-loader-")
	case "neoforge":
		lv, err := a.ensureNeoForge(dir, mcVersion, loaderVersion)
		if err != nil {
			return Instance{}, err
		}
		in.LoaderVersion = strings.TrimPrefix(lv, "neoforge-")
	}

	for _, sub := range []string{"mods", "shaderpacks", "resourcepacks", "datapacks", "saves", "config"} {
		if err := os.MkdirAll(filepath.Join(instanceDir(id), sub), 0755); err != nil {
			return Instance{}, err
		}
	}
	if err := a.saveInstance(in); err != nil {
		return Instance{}, err
	}
	return in, nil
}

// ---------------------------------------------------------------------------
// Launch
// ---------------------------------------------------------------------------

// LaunchInstance boots an instance.
func (a *App) LaunchInstance(id string) string {
	in, err := a.findInstance(id)
	if err != nil {
		return "error: " + err.Error()
	}
	java, err := a.resolveJavaForInstance(*in)
	if err != nil {
		return "error: " + err.Error()
	}
	runJava := java
	if runtime.GOOS == "windows" {
		if w := filepath.Join(filepath.Dir(java), "javaw.exe"); fileExists(w) {
			runJava = w
		}
	}

	dir := a.sharedRoot()
	versionID := loaderVersionID(*in)
	gameDir := instanceDir(id)
	if err := os.MkdirAll(gameDir, 0755); err != nil {
		return "error: " + err.Error()
	}
	v, err := loadVersion(dir, versionID)
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
		jar := filepath.Join(dir, "libraries", filepath.FromSlash(p))
		if fileExists(jar) {
			classpath = append(classpath, jar)
		}
	}
	clientJarPath := filepath.Join(dir, "versions", in.MCVersion, in.MCVersion+".jar")
	classpath = append(classpath, clientJarPath)

	natives := filepath.Join(dir, "versions", versionID, "natives")
	repl := map[string]string{
		"natives_directory":     natives,
		"launcher_name":         "valkaw-launcher",
		"launcher_version":      a.config.LauncherVersion,
		"classpath":             strings.Join(classpath, string(os.PathListSeparator)),
		"classpath_separator":   string(os.PathListSeparator),
		"library_directory":     filepath.Join(dir, "libraries"),
		"version_name":          versionID,
		"game_directory":        gameDir,
		"assets_root":           filepath.Join(dir, "assets"),
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
		fmt.Sprintf("-Xms%dM", a.instanceMemoryMin(*in)),
		fmt.Sprintf("-Xmx%dM", a.instanceMemoryMax(*in)),
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
		logPath := filepath.Join(dir, "assets", "log_configs", v.Logging.Client.File.ID)
		if fileExists(logPath) {
			jvmArgs = append(jvmArgs, "-Dlog4j.configurationFile="+logPath)
		}
	}
	jvmArgs = append(jvmArgs, v.MainClass)
	gameArgs := resolveArgs(v.Arguments.Game, repl, false)
	if fullscreenEnabled(gameDir) {
		gameArgs = append(gameArgs, "--fullscreen")
	}
	jvmArgs = append(jvmArgs, gameArgs...)
	// Extra user-supplied JVM args for this instance, e.g. -Dfoo=bar.
	if args := strings.Fields(in.JavaArgs); len(args) > 0 {
		jvmArgs = append(jvmArgs, args...)
	}

	cmd := exec.Command(runJava, jvmArgs...)
	cmd.Dir = gameDir
	if env := gpuEnv(findGPU(a.config.GPU), "gl"); len(env) > 0 {
		cmd.Env = append(os.Environ(), env...)
	}
	if runtime.GOOS != "windows" {
		cmd.Stdout = os.Stdout
		cmd.Stderr = os.Stderr
	}
	if err := cmd.Start(); err != nil {
		return "error: " + err.Error()
	}
	afterRun := a.config.AfterRun
	if in.AfterRun != "" {
		afterRun = in.AfterRun
	}
	go func() {
		err := cmd.Wait()
		a.handleGameExit(err, gameDir)
		if afterRun == "hide" {
			wailsruntime.WindowShow(a.ctx)
			wailsruntime.WindowUnminimise(a.ctx)
		}
	}()
	switch afterRun {
	case "hide":
		wailsruntime.WindowHide(a.ctx)
	case "close":
		wailsruntime.Quit(a.ctx)
	case "appear":
		wailsruntime.WindowShow(a.ctx)
		wailsruntime.WindowUnminimise(a.ctx)
	}
	return "ok"
}

func (a *App) globalMemory() int {
	if a.config.MemoryMB > 0 {
		return a.config.MemoryMB
	}
	return 3072
}

// instanceMemoryMin/Max resolve the -Xms/-Xmx values for an instance. Without
// an override the instance inherits the global RAM; with an override it uses
// the per-instance Min/Max (falling back to the global value when unset).
func (a *App) instanceMemoryMin(in Instance) int {
	if in.MemoryOverride {
		if in.MemoryMinMB > 0 {
			return in.MemoryMinMB
		}
		return a.globalMemory()
	}
	return a.globalMemory()
}

func (a *App) instanceMemoryMax(in Instance) int {
	if in.MemoryOverride {
		max := in.MemoryMaxMB
		if max > 0 {
			if min := a.instanceMemoryMin(in); max < min {
				max = min
			}
			return max
		}
		return a.instanceMemoryMin(in)
	}
	return a.globalMemory()
}

// ---------------------------------------------------------------------------
// Modpack install
// ---------------------------------------------------------------------------

type mrpackIndex struct {
	Files []struct {
		Path      string            `json:"path"`
		Hashes    map[string]string  `json:"hashes"`
		Downloads []string           `json:"downloads"`
		Env       map[string]*bool   `json:"env"`
		FileSize  int64              `json:"fileSize"`
	} `json:"files"`
	Dependencies map[string]any `json:"dependencies"`
	GameVersions []string       `json:"gameVersions"`
	Loaders      []string       `json:"loaders"`
}

// InstallModpack installs a Modrinth modpack (by project id + version id) into
// a fresh instance and returns the instance id.
func (a *App) InstallModpack(projectID, versionID string) (string, error) {
	if projectID == "" {
		return "", errors.New("projectId required")
	}
	raw, err := a.GetModrinthProjectVersions(projectID, "", "")
	if err != nil {
		return "", err
	}
	var versions []map[string]any
	if err := json.Unmarshal([]byte(raw), &versions); err != nil {
		return "", err
	}
	var chosen map[string]any
	for _, v := range versions {
		id, _ := v["id"].(string)
		num, _ := v["version_number"].(string)
		if id == versionID || num == versionID {
			chosen = v
			break
		}
	}
	if chosen == nil {
		return "", errors.New("modpack version not found")
	}
	projRaw, err := a.GetModrinthProject(projectID)
	if err != nil {
		return "", err
	}
	var proj struct {
		Title string `json:"title"`
		Slug  string `json:"slug"`
	}
	_ = json.Unmarshal([]byte(projRaw), &proj)

	var fileURL string
	if files, ok := chosen["files"].([]any); ok && len(files) > 0 {
		f := files[0].(map[string]any)
		fileURL, _ = f["url"].(string)
	}
	if fileURL == "" {
		return "", errors.New("modpack file not found")
	}

	// Resolve MC version + loader from the version metadata.
	mcVersion := ""
	loader := "fabric"
	if gv, ok := chosen["game_versions"].([]any); ok && len(gv) > 0 {
		mcVersion, _ = gv[0].(string)
	}
	if ld, ok := chosen["loaders"].([]any); ok && len(ld) > 0 {
		loader, _ = ld[0].(string)
		switch loader {
		case "neoforge":
		default:
			if loader != "fabric" && loader != "vanilla" {
				loader = "fabric"
			}
		}
	}
	if mcVersion == "" {
		return "", errors.New("cannot determine Minecraft version from modpack")
	}

	// Create the instance (installs vanilla + loader).
	instanceName := proj.Title
	if instanceName == "" {
		instanceName = proj.Slug
	}
	if instanceName == "" {
		instanceName = projectID
	}
	in, err := a.CreateInstance(instanceName, mcVersion, loader, "")
	if err != nil {
		return "", fmt.Errorf("create instance: %w", err)
	}
	in.ModpackSlug = proj.Slug
	in.ModpackName = instanceName
	_ = a.saveInstance(in)

	// Download the .mrpack.
	tmp, err := os.CreateTemp("", "valkaw-*.mrpack")
	if err != nil {
		return "", err
	}
	tmpPath := tmp.Name()
	tmp.Close()
	defer os.Remove(tmpPath)
	if err := downloadFile(fileURL, tmpPath, 0, "", nil); err != nil {
		return "", fmt.Errorf("download modpack: %w", err)
	}

	if err := a.installMrpack(tmpPath, in); err != nil {
		return "", err
	}
	return in.ID, nil
}

func (a *App) installMrpack(mrpackPath string, in Instance) error {
	zr, err := zip.OpenReader(mrpackPath)
	if err != nil {
		return err
	}
	defer zr.Close()

	var index *mrpackIndex
	overrideFiles := map[string]*zip.File{}
	for _, f := range zr.File {
		if f.Name == "modrinth.index.json" {
			rc, err := f.Open()
			if err != nil {
				return err
			}
			idx := &mrpackIndex{}
			err = json.NewDecoder(io.LimitReader(rc, 16<<20)).Decode(idx)
			rc.Close()
			if err != nil {
				return fmt.Errorf("bad modrinth.index.json: %w", err)
			}
			index = idx
		} else if strings.HasPrefix(f.Name, "overrides/") {
			overrideFiles[f.Name] = f
		}
	}
	if index == nil {
		return errors.New("modrinth.index.json missing from modpack")
	}

	root := instanceDir(in.ID)
	var total int64
	for _, f := range index.Files {
		total += f.FileSize
	}
	var done int64
	pt := &progressTracker{}
	stage := "Installing modpack..."
	a.emitInstall(stage, 50)

	for _, f := range index.Files {
		if e := f.Env["client"]; e != nil && !*e {
			continue
		}
		target, ok := safeJoin(root, f.Path)
		if !ok {
			continue
		}
		if err := os.MkdirAll(filepath.Dir(target), 0755); err != nil {
			return err
		}
		url := ""
		if len(f.Downloads) > 0 {
			url = f.Downloads[0]
		}
		if url == "" {
			continue
		}
		sha := f.Hashes["sha1"]
		pct := 50
		if total > 0 {
			pct = 50 + int(done*40/total)
		}
		if err := downloadFile(url, target, f.FileSize, sha, func(n int64) {
			atomic.AddInt64(&done, n)
			a.progress(pt, stage, 50, 90, atomic.LoadInt64(&done), total)
			_ = pct
		}); err != nil {
			return fmt.Errorf("modpack file %s: %w", f.Path, err)
		}
	}

	// Apply overrides/.
	for name, f := range overrideFiles {
		rel := strings.TrimPrefix(name, "overrides/")
		target, ok := safeJoin(root, rel)
		if !ok {
			continue
		}
		if f.FileInfo().IsDir() {
			_ = os.MkdirAll(target, 0755)
			continue
		}
		if err := os.MkdirAll(filepath.Dir(target), 0755); err != nil {
			return err
		}
		rc, err := f.Open()
		if err != nil {
			return err
		}
		out, err := os.Create(target)
		if err != nil {
			rc.Close()
			return err
		}
		_, err = io.Copy(out, rc)
		out.Close()
		rc.Close()
		if err != nil {
			return err
		}
	}

	a.emitInstall("Modpack installed", 100)
	return nil
}

// ---------------------------------------------------------------------------
// Content download into an instance
// ---------------------------------------------------------------------------

// GetInstanceContentContext returns the content folders for an instance so the
// frontend can install mods / packs into them.
func (a *App) GetInstanceContentContext(id string) (map[string]any, error) {
	in, err := a.findInstance(id)
	if err != nil {
		return nil, err
	}
	root := instanceDir(id)
	return map[string]any{
		"gameVersion":  in.MCVersion,
		"loader":       in.Loader,
		"gameDir":      root,
		"modsDir":      filepath.Join(root, "mods"),
		"rpDir":        filepath.Join(root, "resourcepacks"),
		"shaderDir":    filepath.Join(root, "shaderpacks"),
		"datapackDir":  filepath.Join(root, "datapacks"),
		"instanceName": in.Name,
	}, nil
}

// DownloadResourceToInstance downloads a mod/pack into an instance's folder.
// For datapacks it also copies into existing worlds' datapacks folders.
func (a *App) DownloadResourceToInstance(downloadUrl, fileName, resourceType, instanceID string) error {
	if downloadUrl == "" || fileName == "" || instanceID == "" {
		return errors.New("url, filename and instance required")
	}
	in, err := a.findInstance(instanceID)
	if err != nil {
		return err
	}
	sub, err := resourceSubdir(resourceType)
	if err != nil {
		return err
	}
	root := instanceDir(in.ID)
	safe := sanitizeFileName(fileName)
	baseDir := filepath.Join(root, sub)
	if err := os.MkdirAll(baseDir, 0755); err != nil {
		return err
	}
	dest := filepath.Join(baseDir, safe)
	if err := downloadStreaming(downloadUrl, dest); err != nil {
		return err
	}

	// Datapacks: mirror into all existing worlds.
	if sub == "datapacks" {
		saves := filepath.Join(root, "saves")
		if entries, err := os.ReadDir(saves); err == nil {
			for _, e := range entries {
				if !e.IsDir() {
					continue
				}
				dpDir := filepath.Join(saves, e.Name(), "datapacks")
				_ = os.MkdirAll(dpDir, 0755)
				_ = copyFile(dest, filepath.Join(dpDir, safe))
			}
		}
	}
	return nil
}

func downloadStreaming(url, dest string) error {
	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return err
	}
	req.Header.Set("User-Agent", "Valkaw-Launcher/1.0")
	resp, err := modrinthHTTP.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 400 {
		body, _ := io.ReadAll(io.LimitReader(resp.Body, 4096))
		return fmt.Errorf("download failed: %s %s", resp.Status, string(body))
	}
	tmp, err := os.CreateTemp(filepath.Dir(dest), filepath.Base(dest)+".*.part")
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
	if _, err := io.Copy(tmp, resp.Body); err != nil {
		return err
	}
	if err := tmp.Close(); err != nil {
		return err
	}
	if err := os.Rename(tmpPath, dest); err != nil {
		return err
	}
	keep = true
	return nil
}

func copyFile(src, dest string) error {
	data, err := os.ReadFile(src)
	if err != nil {
		return err
	}
	return os.WriteFile(dest, data, 0644)
}

// ---------------------------------------------------------------------------
// Installed-content management (the Manage tab)
// ---------------------------------------------------------------------------

// instanceCategoryDir maps a Manage sub-tab to its folder inside the game dir.
func instanceCategoryDir(root, category string) string {
	switch category {
	case "mod":
		return filepath.Join(root, "mods")
	case "shaderpack":
		return filepath.Join(root, "shaderpacks")
	case "resourcepack":
		return filepath.Join(root, "resourcepacks")
	case "images":
		return filepath.Join(root, "screenshots")
	case "worlds":
		return filepath.Join(root, "saves")
	case "servers":
		return filepath.Join(root, "servers")
	}
	return filepath.Join(root, category)
}

// ListInstanceContent returns the names of items installed in an instance
// folder for a Manage category (mods, shaderpacks, resourcepacks, screenshots,
// saves, servers).
func (a *App) ListInstanceContent(id, category string) ([]string, error) {
	in, err := a.findInstance(id)
	if err != nil {
		return nil, err
	}
	dir := instanceCategoryDir(instanceDir(in.ID), category)
	if err := os.MkdirAll(dir, 0755); err != nil {
		return nil, err
	}
	entries, err := os.ReadDir(dir)
	if err != nil {
		return nil, err
	}
	names := make([]string, 0, len(entries))
	for _, e := range entries {
		names = append(names, e.Name())
	}
	sort.Strings(names)
	return names, nil
}

// DeleteInstanceFile removes one installed item (file or folder) from an
// instance category folder.
// OpenInstanceCategoryFolder opens a Manage category folder of an instance in
// the system file manager.
func (a *App) OpenInstanceCategoryFolder(id, category string) error {
	in, err := a.findInstance(id)
	if err != nil {
		return err
	}
	return openFolder(instanceCategoryDir(instanceDir(in.ID), category))
}

func (a *App) DeleteInstanceFile(id, category, name string) error {
	in, err := a.findInstance(id)
	if err != nil {
		return err
	}
	dir := instanceCategoryDir(instanceDir(in.ID), category)
	target := filepath.Join(dir, filepath.Base(name))
	if !strings.HasPrefix(target, filepath.Clean(dir)+string(os.PathSeparator)) {
		return errors.New("invalid filename")
	}
	if !fileExists(target) && !dirExists(target) {
		return errors.New("file not found")
	}
	return os.RemoveAll(target)
}
