// TEMPORARY dev-only shim: mocks the Wails bindings so the launcher frontend
// can run in a plain browser (Firefox) for debugging. NOT shipped.
window.go = window.go || { core: { App: {} } };
const App = window.go.core.App;
const ok = data => Promise.resolve(JSON.stringify(data ?? {}));

const DUMMY_CFG = {
  launcherVersion: 'v1.0.9', javaPath: 'java', gameDir: '/tmp/vk',
  version: '1.21.11', profile: 'gl', gpu: '', username: 'Player',
  memoryMB: 3072, windowWidth: 1280, windowHeight: 720,
  autoUpdate: true, afterRun: 'hide', platform: 'linux', gpuPref: 0,
};

App.GetConfig = () => ok(DUMMY_CFG);
App.SaveConfig = () => ok('ok');
App.Launch = () => ok('launched');
App.CheckUpdate = () => ok('up-to-date');
App.OpenReleases = () => ok('ok');
App.InstallClient = () => ok('ok');
App.CheckInstalled = () => ok('installed');
App.DetectJava = () => ok('java');
App.InstallUpdate = () => ok('ok');
App.GetVersions = () => ok(['1.21.11']);
App.ListGPUs = () => ok([]);
App.CheckRequirements = () => ok({ ready: true, java: true, files: true, graphics: true, graphicsApi: 'vulkan', javaText: '', filesText: '', graphicsText: '' });
App.InstallJava = () => ok('ok');
App.SetGpuPreference = () => ok('ok');
App.CheckVulkan = () => ok({ supported: true, loader: '', verified: true, device: 'Mock GPU', apiVersion: '1.4', driverVersion: '', reason: '', fix: '', fixText: '', driverUrl: '', summary: '' });
App.GetResourceBrowserContext = () => ok({ profile: 'gl', profileLabel: 'Sodium', gameVersion: '1.21.11', loader: 'fabric', modsDir: '/tmp/vk/mods', rpDir: '/tmp/vk/resourcepacks' });
App.SearchModrinth = () => ok(JSON.stringify({ hits: [] }));
App.GetModrinthProject = () => ok(JSON.stringify({}));
App.GetModrinthProjectVersions = () => ok(JSON.stringify([]));
App.DownloadResource = () => ok('ok');
App.OpenResourceDir = () => ok('ok');
App.ScanConflictMods = () => ok('no conflicts');

window['go']['core']['App'] = App;
window.addEventListener('load', () => console.log('[mock-wails] bindings installed'));
