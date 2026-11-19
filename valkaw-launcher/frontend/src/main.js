import './style.css';

import { GetConfig, SaveConfig, Launch, CheckUpdate, OpenReleases, InstallClient, CheckInstalled, DetectJava, InstallUpdate, InstallOffline, GetVersions, ListGPUs, CheckRequirements, InstallJava, SetGpuPreference, CheckVulkan, GetResourceBrowserContext, SearchModrinth, GetModrinthProject, GetModrinthProjectVersions, GetModrinthVersion, DownloadResource, OpenResourceDir, ScanConflictMods, GetVersionProfiles, ListModpackInstances, CreateInstance, InstallModpack, DeleteInstance, LaunchInstance, OpenInstanceFolder, GetInstance, SaveInstanceSettings, GetMinecraftVersions, GetLoaderVersions, ListInstalledJavaMajors, InstallTemurin, GetInstanceContentContext, DownloadResourceToInstance, ListInstanceContent, DeleteInstanceFile, OpenInstanceCategoryFolder, OpenFolder } from '../wailsjs/go/core/App';
import { EventsOn, BrowserOpenURL } from '../wailsjs/runtime/runtime';

const $ = (id) => document.getElementById(id);

const pages = {
    play: { title: 'Play', eyebrow: 'Ready to play' },
    settings: { title: 'Settings', eyebrow: 'Configure your client' },
    resources: { title: 'Resources', eyebrow: 'Browse content' },
};

function showPage(name) {
    document.querySelectorAll('.page').forEach(p => p.classList.add('hidden'));
    const pageEl = document.getElementById('page-' + name);
    if (pageEl) pageEl.classList.remove('hidden');
    document.querySelectorAll('.nav').forEach(b => b.classList.toggle('active', b.dataset.page === name));
    if (pages[name]) {
        const pt = $('pageTitle');
    if (pt) pt.textContent = pages[name].title;
        const eb = $('pageEyebrow');
        if (eb) eb.textContent = pages[name].eyebrow;
    }
    if (name === 'resources') {
        requestAnimationFrame(() => refreshResourceContext());
    }
}

document.querySelectorAll('.nav').forEach(b => b.addEventListener('click', () => showPage(b.dataset.page)));

let cfg = {};
let updateReady = false;
let lastProfile = 'gl';
let versionProfiles = {};

async function loadConfig() {
    cfg = await GetConfig();
    document.title = 'Valkaw Launcher ' + (cfg.launcherVersion || '');
    $('launcherVer').textContent = 'Launcher ' + (cfg.launcherVersion || '');
    const isWin = cfg.platform === 'windows';
    $('gpuList').classList.toggle('hidden', isWin);
    $('gpuWin').classList.toggle('hidden', !isWin);
    $('gpuPref').value = String(cfg.gpuPref || 0);
    const profile = cfg.profile || 'gl';
    const radio = document.querySelector(`input[name="profile"][value="${profile}"]`);
    if (radio) radio.checked = true;
    lastProfile = profile;
    $('javaPath').value = cfg.javaPath || 'java';
    $('gameDir').value = cfg.gameDir || '';
    await loadVersions();
    await loadGPUs();
    $('memoryMB').value = cfg.memoryMB || 4096;
    $('windowWidth').value = cfg.windowWidth || 1600;
    $('windowHeight').value = cfg.windowHeight || 900;
    syncWindowPreset();
    $('autoUpdate').checked = !!cfg.autoUpdate;
    // Unknown values (or an older config) must still leave one option picked,
    // otherwise saving the settings would read an empty radio group.
    const ar = document.querySelector(`input[name="afterRun"][value="${cfg.afterRun || 'hide'}"]`)
        || document.querySelector('input[name="afterRun"][value="hide"]');
    if (ar) ar.checked = true;
    updateUserChip();
    const wl = $('welcome');
    if (wl) wl.textContent = cfg.username ? `Welcome back, ${cfg.username}` : 'Welcome back';
    refreshInstallStatus();
}

async function loadVersions() {
    const sel = $('version');
    const versions = await GetVersions();
    sel.innerHTML = '<option value="" disabled>Select</option>' +
        versions.map(v => `<option value="${v}">${v}</option>`).join('');
    sel.value = versions.includes(cfg.version) ? cfg.version : '';
    cfg.version = sel.value;
    versionProfiles = await GetVersionProfiles();
    applyProfileAvailability();
    updateVersionValue();
    updateHeroMeta();
}

// Some game versions only ship one render profile (26.3 has no Vulkan build),
// so that pill is disabled and the selection falls back to Sodium.
function applyProfileAvailability() {
    const ver = $('version').value;
    const allowed = versionProfiles[ver] || ['gl', 'vk'];
    const vkInput = document.querySelector('input[name="profile"][value="vk"]');
    const blocked = !allowed.includes('vk');
    if (vkInput) {
        vkInput.disabled = blocked;
        const pill = vkInput.closest('.profile-pill');
        if (pill) {
            pill.classList.toggle('disabled', blocked);
            pill.title = blocked ? `No Vulkan build for Minecraft ${ver}` : '';
        }
    }
    if (blocked && vkInput?.checked) {
        const gl = document.querySelector('input[name="profile"][value="gl"]');
        if (gl) gl.checked = true;
        lastProfile = 'gl';
        cfg.profile = 'gl';
        syncProfilePills();
        SaveConfig(cfg);
    }
}

// The pill shows the selected version as plain text (the native select is an
// invisible overlay spanning the pill), so mirror the select's value here.
function updateVersionValue() {
    const el = $('versionValue');
    if (el) el.textContent = $('version').value;
}

function updateHeroMeta() {
    const meta = $('heroMeta');
    if (meta) meta.textContent = `Minecraft ${$('version').value} • Valkaw Client`;
}

// The greeting chip mirrors the active render profile so the Play screen
// states the current target at a glance.
function updateGreetProfile() {
    const chip = $('greetProfile');
    if (!chip) return;
    const val = document.querySelector('input[name="profile"]:checked')?.value || 'gl';
    chip.textContent = val === 'vk' ? 'VulkanMod' : 'Sodium';
}

const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

async function loadGPUs() {
    if (cfg.platform === 'windows') {
        $('gpuStatus').textContent = 'The game follows the preference above from the next launch.';
        return;
    }
    const gpus = await ListGPUs();
    const items = [{ id: '', name: 'Auto (system default)', vendor: '' }, ...(gpus || [])];
    $('gpuList').innerHTML = items.map(g => `
        <label class="seg gpu-seg">
            <input type="radio" name="gpu" value="${esc(g.id)}" hidden>
            <span>${esc(g.name)}</span>
            <span class="muted small">${g.vendor ? esc(g.vendor.toUpperCase() + (g.address ? ' • ' + g.address : '')) : 'Let the driver decide'}</span>
        </label>`).join('');
    const pick = (v) => document.querySelector(`input[name="gpu"][value="${v}"]`);
    (pick(cfg.gpu) || pick('')).checked = true;
    cfg.gpu = document.querySelector('input[name="gpu"]:checked').value;
    $('gpuStatus').textContent = gpus && gpus.length
        ? 'Game runs on the selected GPU from the next launch.'
        : 'No switchable GPU detected.';
}

$('gpuList').addEventListener('change', async (e) => {
    if (e.target.name !== 'gpu') return;
    cfg.gpu = e.target.value;
    const res = await SaveConfig(cfg);
    $('gpuStatus').textContent = res === 'ok' ? 'Saved — applies on next launch.' : res;
});

$('gpuPref').addEventListener('change', async () => {
    const pref = parseInt($('gpuPref').value) || 0;
    const res = await SetGpuPreference(pref);
    cfg.gpuPref = pref;
    $('gpuStatus').textContent = res === 'ok' ? 'Saved — applies on next launch.' : res;
});

// ------------------------------------------------------------- Vulkan profile
// The Vulkan profile is only accepted when the driver reports Vulkan 1.3.
const VK_TITLES = {
    'install-driver': ['// VULKAN MOD', 'Vulkan driver missing'],
    'update-driver': ['// DRIVER OUTDATED', 'Update your GPU driver'],
    'upgrade-gpu': ['// GPU NOT SUPPORTED', 'This GPU cannot run Vulkan 1.3'],
};

let vkDriverURL = '';

function showVulkanModal(c) {
    const [eyebrow, title] = VK_TITLES[c.fix] || ['// VULKAN MOD', 'Vulkan unavailable'];
    $('vkEyebrow').textContent = eyebrow;
    $('vkTitle').textContent = title;
    $('vkReason').textContent = c.reason || '';
    $('vkDevice').textContent = [
        c.device,
        c.apiVersion && 'Vulkan ' + c.apiVersion,
        c.driverVersion && 'driver ' + c.driverVersion,
    ].filter(Boolean).join('  //  ');
    $('vkFix').innerHTML = c.fixText ? reqRow('warn', 'How to fix', c.fixText) : '';
    vkDriverURL = c.driverUrl || '';
    $('vkDriver').classList.toggle('hidden', !vkDriverURL);
    $('vkModal').classList.remove('hidden');
}

function closeVulkan() { $('vkModal').classList.add('hidden'); }

async function vulkanReady(quiet) {
    const c = await CheckVulkan();
    if (c.supported) return true;
    if (!quiet) showVulkanModal(c);
    return false;
}

function syncProfilePills() {
    const val = document.querySelector('input[name="profile"]:checked')?.value || 'gl';
    document.querySelectorAll('.profile-pill').forEach(pill => {
        const input = pill.querySelector('input[name="profile"]');
        pill.classList.toggle('selected', !!input && input.value === val);
    });
    updateGreetProfile();
}
document.querySelectorAll('input[name="profile"]').forEach(r => r.addEventListener('change', async (e) => {
    if (e.target.value === 'vk' && !(await vulkanReady())) {
        const back = document.querySelector(`input[name="profile"][value="${lastProfile}"]`);
        if (back) back.checked = true;
        syncProfilePills();
        return;
    }
    lastProfile = e.target.value;
    cfg.profile = e.target.value;
    await SaveConfig(cfg);
    syncProfilePills();
    refreshInstallStatus();
}));
syncProfilePills();

$('vkDriver').addEventListener('click', () => { if (vkDriverURL) BrowserOpenURL(vkDriverURL); });
$('vkSodium').addEventListener('click', async () => {
    const gl = document.querySelector('input[name="profile"][value="gl"]');
    if (gl) gl.checked = true;
    lastProfile = 'gl';
    cfg.profile = 'gl';
    await SaveConfig(cfg);
    closeVulkan();
});
$('vkClose').addEventListener('click', closeVulkan);

$('version').addEventListener('change', async () => {    cfg.version = $('version').value;
    applyProfileAvailability();
    updateVersionValue();
    updateHeroMeta();
    await SaveConfig(cfg);
    refreshInstallStatus();
});

async function refreshInstallStatus() {
    let res = '';
    try {
        res = await CheckInstalled();
    } catch (_) {
        res = 'not installed';
    }
    const installed = res === 'installed';
    $('installHint').textContent = installed ? 'Installed — ready to play' : res;
    $('installBtn').classList.toggle('hidden', installed);
    $('playBtn').classList.toggle('hidden', !installed);
}

function setInstallProgress(stage, pct) {
    $('installStage').textContent = `${stage} ${pct || 0}%`;
    $('installBar').style.width = Math.max(0, Math.min(100, pct || 0)) + '%';
    if (!$('prereqModal').classList.contains('hidden')) {
        $('prereqStage').textContent = `${stage} ${pct || 0}%`;
        $('prereqBar').style.width = Math.max(0, Math.min(100, pct || 0)) + '%';
    }
}

EventsOn('install:progress', (stage, pct) => setInstallProgress(stage, pct));

// The player name is edited in-game now (main menu account pill), so the
// launcher only mirrors whatever was saved there.
function currentUsername() {
    return (cfg.username || 'Player').trim();
}

function updateUserChip() {
    $('userChip').textContent = currentUsername() || 'Guest';
}

async function refreshUpdateStatus() {
    const el = $('updateStatus');
    el.textContent = 'Checking...';
    try {
        const res = await CheckUpdate();
        el.textContent = res;
        updateReady = res.startsWith('update available');
        $('updateBtn').textContent = updateReady ? 'Update now' : 'Check updates';
        el.style.color = updateReady ? 'var(--warn)' : 'var(--muted)';
    } catch (e) {
        el.textContent = 'Update check failed';
    }
    return updateReady;
}

$('updateBtn').addEventListener('click', async () => {
    if (!updateReady) {
        refreshUpdateStatus();
        return;
    }
    if (!confirm('Download the launcher update and restart?')) return;
    $('updateStatus').textContent = 'Downloading update...';
    const res = await InstallUpdate();
    $('updateStatus').textContent = res;
});
$('openReleases').addEventListener('click', () => OpenReleases());

$('detectJavaBtn').addEventListener('click', async () => {
    const res = await DetectJava();
    $('javaStatus').textContent = res;
    if (!res.startsWith('error')) {
        const c = await GetConfig();
        $('javaPath').value = c.javaPath;
    }
});

$('offlineBtn').addEventListener('click', async () => {
    const profile = document.querySelector('input[name="profile"]:checked').value;
    $('installStage').textContent = 'Selecting jar...';
    const res = await InstallOffline(profile);
    if (res === 'canceled') {
        $('installStage').textContent = 'Offline install canceled';
        $('installBar').style.width = '0%';
    } else if (res.startsWith('error')) {
        $('installStage').textContent = res;
        $('installBar').style.width = '0%';
        $('installHint').textContent = 'Please re-download from the official source (GitHub release).';
    } else {
        setInstallProgress('Installed (offline)', 100);
        refreshInstallStatus();
        $('installHint').textContent = 'Installed from offline jar — ready to play';
    }
});

$('installBtn').addEventListener('click', async () => {
    const btn = $('installBtn');
    const profile = document.querySelector('input[name="profile"]:checked').value;
    btn.disabled = true;
    btn.textContent = 'Installing...';
    $('installStage').textContent = 'Starting...';
    cfg.profile = profile;
    cfg.version = $('version').value;
    cfg.windowWidth = parseInt($('windowWidth').value) || 1600;
    cfg.windowHeight = parseInt($('windowHeight').value) || 900;
    await SaveConfig(cfg);
    try {
        const res = await InstallClient(profile);
        if (res.startsWith('error')) {
            $('installStage').textContent = res;
            $('installBar').style.width = '0%';
        } else {
            setInstallProgress('Installed', 100);
            refreshInstallStatus();
        }
    } catch (e) {
        $('installStage').textContent = 'Install error: ' + e;
    } finally {
        btn.disabled = false;
        btn.textContent = '▶ Install';
    }
});

loadConfig().then(async () => {
    syncProfilePills();
    refreshInstallStatus();
    const reqs = await CheckRequirements();
    if (!reqs.ready) openPrereq(reqs);
    const ready = await refreshUpdateStatus();
    if (cfg.autoUpdate && ready) {
        $('updateStatus').textContent = 'Auto-updating...';
        $('updateStatus').textContent = await InstallUpdate();
    }
}).catch(err => {
    $('status').textContent = 'Config error: ' + err;
});

const JAVA_URL = 'https://adoptium.net/temurin/releases/?version=21&package=jre';

let graphicsWarned = false;

async function syncConfig() {
    cfg.profile = document.querySelector('input[name="profile"]:checked').value;
    cfg.version = $('version').value;
    cfg.windowWidth = parseInt($('windowWidth').value) || 1600;
    cfg.windowHeight = parseInt($('windowHeight').value) || 900;
    await SaveConfig(cfg);
}

function reqRow(state, title, text) {
    const cls = state === 'ok' ? ' ok' : state === 'warn' ? ' warn' : '';
    return `<li class="req${cls}"><span class="dot"></span><div><strong>${esc(title)}</strong><span>${esc(text)}</span></div></li>`;
}

function renderPrereq(reqs) {
    $('prereqList').innerHTML =
        reqRow(reqs.java ? 'ok' : 'bad', 'Java 21 runtime', reqs.java ? 'Found on this system' : reqs.javaText) +
        reqRow(reqs.files ? 'ok' : 'bad', 'Game files', reqs.files ? 'Minecraft, Fabric and Valkaw mods installed' : reqs.filesText) +
        reqRow(reqs.graphics ? 'ok' : 'warn', reqs.graphicsApi || 'Graphics', reqs.graphics ? reqs.graphicsText : reqs.graphicsText + ' This is a notice only — install it yourself, the launcher cannot.');
    $('prereqJava').classList.toggle('hidden', !!reqs.java);
}

function openPrereq(reqs) {
    renderPrereq(reqs);
    $('prereqBar').style.width = '0%';
    $('prereqStage').textContent = '';
    $('prereqInstall').disabled = false;
    $('prereqInstall').textContent = reqs.ready ? 'Continue anyway' : 'Install missing';
    $('prereqModal').classList.remove('hidden');
}

function closePrereq() {
    $('prereqModal').classList.add('hidden');
}

function setupFailed(msg) {
    $('prereqStage').textContent = msg;
    $('prereqBar').style.width = '0%';
}

async function installMissing() {
    const btn = $('prereqInstall');
    const stage = $('prereqStage');
    btn.disabled = true;
    btn.textContent = 'Installing...';
    graphicsWarned = true;
    try {
        const reqs = await CheckRequirements();
        if (!reqs.java) {
            stage.textContent = 'Downloading Java 21...';
            const java = await InstallJava();
            if (java.startsWith('error')) return setupFailed(java);
            const c = await GetConfig();
            cfg.javaPath = c.javaPath;
            $('javaPath').value = c.javaPath;
            $('javaStatus').textContent = 'Java 21 installed at ' + c.javaPath;
        }
        if (!reqs.files) {
            stage.textContent = 'Installing game files...';
            const res = await InstallClient(cfg.profile || 'gl');
            if (res.startsWith('error')) return setupFailed(res);
        }
        const now = await CheckRequirements();
        renderPrereq(now);
        if (!now.ready) return setupFailed('Setup incomplete: ' + (now.java ? now.filesText : now.javaText));
        stage.textContent = 'Ready';
        refreshInstallStatus();
        closePrereq();
        // Do not auto-launch: wait for the user to press Play.
    } catch (e) {
        setupFailed('Setup error: ' + e);
    } finally {
        btn.disabled = false;
        btn.textContent = 'Install missing';
    }
}

async function launchGame() {
    const status = $('status');
    const btn = $('playBtn');
    btn.disabled = true;
    btn.textContent = 'Launching...';
    status.textContent = 'Launching Valkaw Client...';
    try {
        status.textContent = await Launch();
    } catch (e) {
        status.textContent = 'Error: ' + e;
    } finally {
        btn.disabled = false;
        btn.textContent = '▶ Play';
    }
}

$('playBtn').addEventListener('click', async () => {
    await syncConfig();
    if (cfg.profile === 'vk' && !(await vulkanReady())) return;
    const reqs = await CheckRequirements();
    if (!reqs.ready || (!reqs.graphics && !graphicsWarned)) {
        openPrereq(reqs);
        return;
    }
    launchGame();
});

$('prereqInstall').addEventListener('click', installMissing);
$('prereqClose').addEventListener('click', closePrereq);
$('prereqJava').addEventListener('click', () => BrowserOpenURL(JAVA_URL));
document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
        closePrereq();
        closeVulkan();
        closeResourceBrowser();
    }
});

// Resources browser

let resourceCtx = { gameVersion: '', profile: 'gl', loader: 'fabric', gameDir: '' };
let resourceTab = 'mod';
let resourceQuery = '';
let resourceOffset = 0;
// 7 rows of 3 cards: the last row (Forge Config API Port, Collective, FancyMenu)
// still fits above the status line without clipping the grid.
const resourceLimit = 21;
let resourceResults = [];
let resourceSelectedProject = null;
let resourceSelectedVersions = [];
let resourceDownloading = false;

// Filter groups mirror Modrinth's own category lists per project type.
const RESOURCE_FILTER_CONFIG = {
    mod: [
        {
            key: 'category', title: 'Category', options: [
                ['adventure', 'Adventure'], ['cursed', 'Cursed'], ['decoration', 'Decoration'],
                ['economy', 'Economy'], ['equipment', 'Equipment'], ['food', 'Food'],
                ['game-mechanics', 'Game Mechanics'], ['library', 'Library'], ['magic', 'Magic'],
                ['management', 'Management'], ['minigame', 'Minigame'], ['mobs', 'Mobs'],
                ['optimization', 'Optimization'], ['social', 'Social'], ['storage', 'Storage'],
                ['technology', 'Technology'], ['transportation', 'Transportation'],
                ['utility', 'Utility'], ['worldgen', 'World Generation'],
            ],
        },
    ],
    resourcepack: [
        {
            key: 'category', title: 'Category', options: [
                ['combat', 'Combat'], ['cursed', 'Cursed'], ['decoration', 'Decoration'],
                ['modded', 'Modded'], ['realistic', 'Realistic'], ['simplistic', 'Simplistic'],
                ['themed', 'Themed'], ['tweaks', 'Tweaks'], ['utility', 'Utility'],
                ['vanilla-like', 'Vanilla Like'],
            ],
        },
        {
            key: 'feature', title: 'Feature', options: [
                ['audio', 'Audio'], ['blocks', 'Blocks'], ['core-shaders', 'Core Shaders'],
                ['entities', 'Entities'], ['environment', 'Environment'], ['equipment', 'Equipment'],
                ['fonts', 'Fonts'], ['gui', 'GUI'], ['items', 'Items'], ['locale', 'Locale'],
                ['models', 'Models'],
            ],
        },
        {
            key: 'resolution', title: 'Resolution', options: [
                ['8x-', '8x or lower'], ['16x', '16x'], ['32x', '32x'], ['48x', '48x'],
                ['64x', '64x'], ['128x', '128x'], ['256x', '256x'], ['512x+', '512x or higher'],
            ],
        },
    ],
};

// Set of selected slugs per filter group key for the active tab.
let resourceFilterState = {};

function clearResourceFilters() {
    resourceFilterState = {};
}

function renderResourceFilters() {
    const panel = $('resourceFilters');
    if (!panel) return;
    const groups = RESOURCE_FILTER_CONFIG[resourceTab] || [];
    panel.innerHTML = '';
    for (const group of groups) {
        const selected = resourceFilterState[group.key] || new Set();
        const el = document.createElement('div');
        el.className = 'resource-filter-group';
        const head = document.createElement('button');
        head.type = 'button';
        head.className = 'resource-filter-head';
        head.innerHTML = `<span>${esc(group.title)}</span><span class="chev">▾</span>`;
        head.addEventListener('click', () => el.classList.toggle('collapsed'));

        const items = document.createElement('div');
        items.className = 'resource-filter-items';
        for (const [slug, label] of group.options) {
            const row = document.createElement('label');
            row.className = 'resource-filter-item';
            const input = document.createElement('input');
            input.type = 'checkbox';
            input.checked = selected.has(slug);
            input.addEventListener('change', () => {
                const set = resourceFilterState[group.key] || new Set();
                if (input.checked) set.add(slug); else set.delete(slug);
                resourceFilterState[group.key] = set;
                resourceOffset = 0;
                searchResources(0);
            });
            const box = document.createElement('span');
            box.className = 'box';
            const text = document.createElement('span');
            text.textContent = label;
            row.append(input, box, text);
            items.appendChild(row);
        }
        el.append(head, items);
        panel.appendChild(el);
    }
}

// Build the AND-of-ORs facet payload Modrinth expects.
function buildResourceFilterPayload() {
    const groups = [];
    for (const group of Object.values(resourceFilterState)) {
        if (group && group.size) groups.push([...group]);
    }
    return groups.length ? JSON.stringify(groups) : '';
}

function resourceProjectTypeForTab(tab) {
    if (tab === 'resourcepack') return 'resourcepack';
    return 'mod';
}

function resourceDestTypeForTab(tab) {
    if (tab === 'resourcepack') return 'resourcepack';
    return 'mod';
}

async function refreshResourceContext() {
    try {
        resourceCtx = await GetResourceBrowserContext();
    } catch (e) {
        // ignore
    }
    const ctxEl = $('resourceContext');
    if (ctxEl) {
        const profile = resourceCtx.profileLabel || (resourceCtx.profile === 'vk' ? 'VulkanMod' : 'Sodium');
        ctxEl.textContent = `Minecraft ${resourceCtx.gameVersion || '-'} • ${resourceCtx.loader || 'fabric'} • ${profile}`;
    }
}

// Resolves the destination folder for the active profile so the UI can show
// exactly where a file landed.
function resourceTargetDir(destType) {
    if (destType === 'resourcepack') return resourceCtx.rpDir || '';
    return resourceCtx.modsDir || '';
}

function openResourceBrowser() {
    $('resourceModal').classList.remove('hidden');
    $('resourceListView').classList.remove('hidden');
    $('resourceDetailView').classList.add('hidden');
    $('resourceProgress').style.width = '0%';
    $('resourceStatus').textContent = '';
    renderResourceFilters();
    refreshResourceContext().then(() => searchResources(0));
}

function closeResourceBrowser() {
    $('resourceModal').classList.add('hidden');
}

// Wheel input arrives in coarse steps in WebKit and in WebView2, which reads
// as stuttering. Animate scrollTop toward a target instead of jumping.
const resourceScrollBody = () => $('resourceBody');
let resourceScrollTarget = 0;
let resourceScrollFrame = 0;

function resourceScrollStep() {
    const body = resourceScrollBody();
    const diff = resourceScrollTarget - body.scrollTop;
    if (Math.abs(diff) < 0.8) {
        body.scrollTop = resourceScrollTarget;
        resourceScrollFrame = 0;
        return;
    }
    body.scrollTop += diff * 0.3;
    resourceScrollFrame = requestAnimationFrame(resourceScrollStep);
}

function resetResourceScroll() {
    const body = resourceScrollBody();
    if (resourceScrollFrame) {
        cancelAnimationFrame(resourceScrollFrame);
        resourceScrollFrame = 0;
    }
    resourceScrollTarget = 0;
    body.scrollTop = 0;
}

if (!window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    const body = resourceScrollBody();
    body.addEventListener('wheel', (e) => {
        if (e.ctrlKey || e.deltaMode !== 0 || !e.deltaY) return;
        const max = body.scrollHeight - body.clientHeight;
        if (max <= 0) return;
        const from = resourceScrollFrame ? resourceScrollTarget : body.scrollTop;
        const next = Math.min(max, Math.max(0, from + e.deltaY));
        if (next === from) return;
        e.preventDefault();
        resourceScrollTarget = next;
        if (!resourceScrollFrame) resourceScrollFrame = requestAnimationFrame(resourceScrollStep);
    }, { passive: false });
    body.addEventListener('pointerdown', () => {
        if (resourceScrollFrame) {
            cancelAnimationFrame(resourceScrollFrame);
            resourceScrollFrame = 0;
        }
        resourceScrollTarget = body.scrollTop;
    });
}

$('openResourceBrowser').addEventListener('click', openResourceBrowser);
$('resourceClose').addEventListener('click', closeResourceBrowser);
// Opens the profile's mods folder so files downloaded outside the launcher can
// be dropped in directly.
$('openModsFolder').addEventListener('click', async () => {
    const btn = $('openModsFolder');
    const label = btn.textContent;
    try {
        await OpenResourceDir('mod');
        btn.textContent = 'Opened';
    } catch (e) {
        btn.textContent = 'Failed';
    }
    setTimeout(() => { btn.textContent = label; }, 1400);
});

// Detects mods the user installed separately that duplicate mods already
// bundled inside the client jar. Such duplicates would make Minecraft refuse
// to start, so we list them for removal.
$('checkModConflicts').addEventListener('click', async () => {
    const btn = $('checkModConflicts');
    const label = btn.textContent;
    const statusEl = $('resourceStatus');
    btn.disabled = true;
    btn.textContent = 'Scanning...';
    statusEl.textContent = '';
    try {
        const raw = await ScanConflictMods();
        if (raw === 'ok') {
            statusEl.textContent = 'No duplicate mods found. All installed mods are compatible with the bundled client.';
        } else if (raw.startsWith('error:')) {
            statusEl.textContent = raw;
        } else {
            const conflicts = JSON.parse(raw);
            if (!conflicts.length) {
                statusEl.textContent = 'No duplicate mods found.';
            } else {
                statusEl.textContent = 'Duplicate mods found (already inside the client): ' +
                    conflicts.map(c => c.file).join(', ') +
                    '. Remove them or Minecraft will refuse to start.';
            }
        }
    } catch (e) {
        statusEl.textContent = 'Scan failed: ' + e;
    } finally {
        btn.disabled = false;
        btn.textContent = label;
    }
});

document.querySelectorAll('.resource-tab').forEach(btn => {
    btn.addEventListener('click', () => {
        document.querySelectorAll('.resource-tab').forEach(b => b.classList.toggle('active', b === btn));
        resourceTab = btn.dataset.tab;
        $('resourceListView').classList.remove('hidden');
        $('resourceDetailView').classList.add('hidden');
        resourceOffset = 0;
        clearResourceFilters();
        renderResourceFilters();
        searchResources(0);
    });
});

$('resourceSearchBtn').addEventListener('click', () => {
    resourceQuery = $('resourceQuery').value || '';
    resourceOffset = 0;
    searchResources(0);
});
$('resourceQuery').addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
        e.preventDefault();
        resourceQuery = $('resourceQuery').value || '';
        resourceOffset = 0;
        searchResources(0);
    }
});

$('resourcePrev').addEventListener('click', () => {
    if (resourceOffset < resourceLimit) return;
    resourceOffset -= resourceLimit;
    searchResources(resourceOffset);
});
$('resourceNext').addEventListener('click', () => {
    resourceOffset += resourceLimit;
    searchResources(resourceOffset);
});
$('resourceBack').addEventListener('click', () => {
    resetResourceScroll();
    $('resourceListView').classList.remove('hidden');
    $('resourceDetailView').classList.add('hidden');
});

function renderResourceResults() {
    const grid = $('resourceResults');
    grid.innerHTML = '';
    if (!resourceResults.length) {
        grid.innerHTML = '<p class="muted small">No results found.</p>';
        $('resourcePageInfo').textContent = '';
        return;
    }
    for (const hit of resourceResults) {
        const item = document.createElement('button');
        item.className = 'resource-card-item';
        const icon = hit.icon_url || hit.icon_url_png || '';
        // Mods incompatible with the selected game version get a visible badge
        // even though the backend hides them from search for that version.
        const incompatible = (resourceCtx.incompatibleMods || [])
            .some(s => s.toLowerCase() === (hit.slug || '').toLowerCase());
        item.innerHTML = `
            <img class="resource-icon" src="${esc(icon)}" alt="" width="56" height="56"
                 loading="lazy" decoding="async" referrerpolicy="no-referrer"
                 onerror="this.style.display='none'">
            <div class="resource-card-body">
                <strong>${esc(hit.title || hit.name || hit.slug || '')}</strong>
                <span class="muted small">${esc(hit.author || hit.team || '')}</span>
                <span class="muted small">${hit.downloads ? hit.downloads.toLocaleString() + ' downloads' : ''}</span>
                ${incompatible ? '<span class="resource-badge-incompatible">Incompatible</span>' : ''}
            </div>
        `;
        item.addEventListener('click', () => openResourceProject(hit));
        grid.appendChild(item);
    }
    const page = Math.floor(resourceOffset / resourceLimit) + 1;
    $('resourcePageInfo').textContent = `Page ${page}`;
}

async function searchResources(offset) {
    resetResourceScroll();
    $('resourceStatus').textContent = 'Searching...';
    $('resourceProgress').style.width = '0%';
    try {
        const projectType = resourceProjectTypeForTab(resourceTab);
        // Only mods are tied to a loader; resource packs are not.
        const loader = projectType === 'mod' ? (resourceCtx.loader || 'fabric') : '';
        const res = await SearchModrinth(
            resourceQuery,
            projectType,
            resourceCtx.gameVersion || cfg.version || '',
            loader,
            'relevance',
            offset,
            resourceLimit,
            buildResourceFilterPayload(),
            true
        );
        const data = JSON.parse(res);
        resourceResults = data.hits || [];
        resourceOffset = offset;
        renderResourceResults();
        const hidden = data.hidden_bundled || 0;
        if (resourceResults.length) {
            $('resourceStatus').textContent = hidden
                ? `${hidden} result(s) hidden: already included in the client.`
                : '';
        } else {
            $('resourceStatus').textContent = 'No results found.';
        }
    } catch (e) {
        $('resourceStatus').textContent = 'Search failed: ' + e;
    }
}

async function openResourceProject(hit) {
    $('resourceListView').classList.add('hidden');
    $('resourceDetailView').classList.remove('hidden');
    $('resourceProgress').style.width = '0%';
    $('resourceStatus').textContent = 'Loading versions...';
    const slug = hit.slug || hit.project_id || hit.id;
    resourceSelectedProject = hit;
    try {
        const projectType = resourceProjectTypeForTab(resourceTab);
        const loader = projectType === 'mod' ? (resourceCtx.loader || 'fabric') : '';
        const [projRaw, verRaw] = await Promise.all([
            GetModrinthProject(slug),
            GetModrinthProjectVersions(slug, resourceCtx.gameVersion || cfg.version || '', loader)
        ]);
        const proj = JSON.parse(projRaw);
        resourceSelectedVersions = JSON.parse(verRaw) || [];
        $('resourceDetailTitle').textContent = proj.title || hit.title || slug;
        $('resourceDetailDesc').textContent = proj.description || hit.description || '';
        const deps = (proj.dependencies || []).filter(d => d.dependency_type === 'required');
        if (deps.length) {
            $('resourceDetailDeps').textContent = 'Required dependencies: ' + deps.map(d => d.project_id || '').filter(Boolean).join(', ');
        } else {
            $('resourceDetailDeps').textContent = '';
        }
        $('resourceDetailMeta').textContent = `Minecraft ${resourceCtx.gameVersion || '-'} • ${resourceCtx.loader || 'fabric'}`;
        renderResourceVersions();
        $('resourceStatus').textContent = '';
    } catch (e) {
        $('resourceStatus').textContent = 'Failed to load project: ' + e;
    }
}

function renderResourceVersions() {
    const list = $('resourceVersions');
    list.innerHTML = '';
    if (!resourceSelectedVersions.length) {
        list.innerHTML = '<p class="muted small">No compatible versions found for current Minecraft/loader.</p>';
        return;
    }
    for (const v of resourceSelectedVersions) {
        const item = document.createElement('div');
        item.className = 'resource-version';
        const file = (v.files || []).find(f => f.primary) || (v.files || [])[0];
        const name = file ? file.filename : (v.name || v.version_number);
        const url = file ? file.url : '';
        item.innerHTML = `
            <div class="resource-version-info">
                <strong>${esc(v.version_number || v.name || '')}</strong>
                <div class="resource-version-meta">
                    <span>${(v.game_versions || []).join(', ')}</span>
                    <span>•</span>
                    <span>${(v.loaders || []).join(', ')}</span>
                    <span>•</span>
                    <span>${v.version_type || ''}</span>
                    <span>•</span>
                    <span>${esc(name)}</span>
                </div>
            </div>
            <button class="btn ghost small" data-url="${esc(url)}" data-name="${esc(name)}">Download</button>
        `;
        const btn = item.querySelector('button');
        btn.addEventListener('click', async () => {
            if (resourceDownloading || !url) return;
            resourceDownloading = true;
            btn.disabled = true;
            btn.textContent = 'Downloading...';
            $('resourceStatus').textContent = 'Downloading ' + name;
            $('resourceProgress').style.width = '0%';
            try {
                await DownloadResource(url, name, resourceDestTypeForTab(resourceTab), resourceSelectedProject?.slug || '');
                const dest = resourceTargetDir(resourceDestTypeForTab(resourceTab));
                $('resourceStatus').textContent = dest
                    ? `Downloaded ${name} to ${dest}`
                    : `Downloaded ${name}`;
            } catch (e) {
                const msg = String(e);
                // A duplicate mod id is not a network failure; show it as-is.
                $('resourceStatus').textContent = msg.includes('already installed')
                    ? msg
                    : 'Download failed: ' + msg;
            } finally {
                resourceDownloading = false;
                btn.disabled = false;
                btn.textContent = 'Download';
            }
        });
        list.appendChild(item);
    }
}

EventsOn('resource:progress', (file, downloaded, total) => {
    if (!total || total <= 0) {
        $('resourceProgress').style.width = '30%';
        return;
    }
    const pct = Math.max(0, Math.min(100, (downloaded / total) * 100));
    $('resourceProgress').style.width = pct + '%';
});

function presetValue(w, h) {
    const hit = [...$('windowPreset').options].find(o => o.value === `${w}x${h}`);
    return hit ? hit.value : 'custom';
}

function syncWindowPreset() {
    $('windowPreset').value = presetValue(
        parseInt($('windowWidth').value) || 1600,
        parseInt($('windowHeight').value) || 900
    );
}

$('windowPreset').addEventListener('change', () => {
    if ($('windowPreset').value === 'custom') return;
    const [w, h] = $('windowPreset').value.split('x');
    $('windowWidth').value = w;
    $('windowHeight').value = h;
});
$('windowWidth').addEventListener('input', syncWindowPreset);
$('windowHeight').addEventListener('input', syncWindowPreset);

$('saveBtn').addEventListener('click', async () => {
    const status = $('saveStatus');
    cfg.javaPath = $('javaPath').value;
    cfg.gameDir = $('gameDir').value;
    cfg.version = $('version').value;
    cfg.memoryMB = parseInt($('memoryMB').value) || 4096;
    cfg.windowWidth = parseInt($('windowWidth').value) || 1600;
    cfg.windowHeight = parseInt($('windowHeight').value) || 900;
    cfg.autoUpdate = $('autoUpdate').checked;
    cfg.afterRun = document.querySelector('input[name="afterRun"]:checked').value;
    cfg.profile = document.querySelector('input[name="profile"]:checked').value;
    cfg.gpu = document.querySelector('input[name="gpu"]:checked')?.value || '';
    cfg.gpuPref = parseInt($('gpuPref').value) || 0;
    const res = await SaveConfig(cfg);
    status.textContent = res;
    updateUserChip();
    const wl = $('welcome');
    if (wl) wl.textContent = cfg.username ? `Welcome back, ${cfg.username}` : 'Welcome back';
    refreshInstallStatus();
});

/* Saturn scene: drive the moons with requestAnimationFrame, matching the
   website's MoonLayer math. Each moon sits on the ring-plane ellipse
   (rx/ry in % of the 780×420 viewBox; vertical centre = ring plane at
   54.76% height). Scaling and periods follow real Saturnian moons
   (NASA/JPL): radii grow outward past the A ring, periods obey Kepler's
   third law (T ∝ a^1.5), and phases are irregular so no two moons ever
   line up into a symmetric pattern. Moons swap front/back layering as
   they pass the planet. */
/* The launcher runs in one fixed frame (DisableResize in main.go), so the
   scene box is measured a single time by saturnSceneFit and shared here.
   Reading clientWidth every frame forced a layout pass per frame; the moons
   now read these cached numbers instead. */
const saturnSize = { w: 720, h: 388 };

(function saturnMoons() {
    const MOONS = [
        { id: 'moon1', rx: 32.9, ry: 17.1, durMs: 30000, phase: 0.6 }, // Janus
        { id: 'moon2', rx: 36.0, ry: 18.7, durMs: 34000, phase: 1.0 }, // Mimas
        { id: 'moon3', rx: 37.7, ry: 19.6, durMs: 37000, phase: 5.6 }, // Enceladus
        { id: 'moon4', rx: 39.5, ry: 20.5, durMs: 39000, phase: 1.9 }, // Tethys
        { id: 'moon5', rx: 42.2, ry: 21.9, durMs: 44000, phase: 4.0 }, // Dione
        { id: 'moon6', rx: 47.0, ry: 24.4, durMs: 51000, phase: 0.2 }, // Rhea
    ];
    const RING_Y = 50 + 4.76; // ring-plane centre on the vertical axis
    const els = MOONS.map(m => ({ m, el: document.getElementById(m.id) })).filter(o => o.el);
    if (!els.length) return;
    function frame(now) {
        const sx = saturnSize.w / 100;
        const sy = saturnSize.h / 100;
        for (const { m, el } of els) {
            const a = ((now % m.durMs) / m.durMs) * Math.PI * 2 + m.phase;
            const sin = Math.sin(a);
            const x = (50 + m.rx * Math.cos(a)) * sx;
            const y = (RING_Y + m.ry * sin) * sy;
            el.style.transform = `translate(${x}px, ${y}px) translate(-50%, -50%)`;
            el.style.zIndex = sin > 0 ? '5' : '0';
        }
        requestAnimationFrame(frame);
    }
    requestAnimationFrame(frame);
})();

/* Ring particles: hundreds of ice/rock grains orbit at Kepler speeds so the
   ring reads as a spinning disc — the same model as the website's
   RingParticles. Angles/periods are precomputed deterministically; per-frame
   updates write cx/cy straight to the SVG circles. */
(function saturnRingParticles() {
    const host = document.getElementById('ringParticles');
    if (!host) return;
    const COUNT = 340;
    const R_MIN = 150;
    const R_MAX = 292;
    const FLAT = 0.28; // ring-plane tilt (ry/rx), matches the website and game
    const CX = 390;
    const CY = 230;
    function mulberry32(seed) {
        let a = seed >>> 0;
        return function () {
            a |= 0; a = (a + 0x6D2B79F5) | 0;
            let t = Math.imul(a ^ (a >>> 15), 1 | a);
            t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
            return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
        };
    }
    const rand = mulberry32(20261109);
    const particles = [];
    let guard = 0;
    while (particles.length < COUNT && guard++ < 20000) {
        const r = R_MIN + rand() * (R_MAX - R_MIN);
        if (r > 213 && r < 233) continue; // Cassini division stays dark
        const period = 8 * Math.pow(r / 184, 1.5); // ≈8s at C ring outer edge
        particles.push({
            r,
            omega: (Math.PI * 2) / period,
            phase: rand() * Math.PI * 2,
            tone: rand(),
        });
    }
    for (const p of particles) {
        const c = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
        c.setAttribute('class', p.tone > 0.75 ? 'ring-particle ring-particle--dark' : 'ring-particle');
        c.setAttribute('cx', String(CX + p.r));
        c.setAttribute('cy', String(CY));
        c.setAttribute('r', String(0.9 + p.tone * 0.7));
        host.appendChild(c);
    }
    function frame(now) {
        const list = host.children;
        for (let i = 0; i < list.length; i++) {
            const p = particles[i];
            const el = list[i];
            const a = p.phase + p.omega * (now / 1000);
            el.setAttribute('cx', String(CX + p.r * Math.cos(a)));
            el.setAttribute('cy', String(CY + p.r * FLAT * Math.sin(a)));
        }
        requestAnimationFrame(frame);
    }
    requestAnimationFrame(frame);
})();

/* Fit the whole Saturn model (rings, moons and all) inside the area to the
   right of the sidebar. The window is a fixed 1600×900 frame with resize
   disabled, so this runs once at startup instead of on every resize event.
   The sheet is rotated -14°, so we measure its rotated footprint (bounding
   box) rather than the raw 1240×668 box — otherwise the ring tips would
   still clip under the UI. The scale is derived from that footprint plus
   margins, then the model is centered in the main content region. */
(function saturnSceneFit() {
    const scene = document.querySelector('.saturn-scene');
    const sidebar = document.querySelector('.sidebar');
    if (!scene) return;
    const BASE_W = 1240;
    const BASE_H = 668;
    const TILT = -14 * Math.PI / 180; // same rotation as the CSS scene
    const COS = Math.abs(Math.cos(TILT));
    const SIN = Math.abs(Math.sin(TILT));
    const BB_W = BASE_W * COS + BASE_H * SIN;
    const BB_H = BASE_W * SIN + BASE_H * COS;
    const PAD_L = 24; // gap from the sidebar
    const PAD_R = 24; // right edge margin
    const PAD_T = 112; // clear the greeting header at the top
    const PAD_B = 210; // clear the bottom command dock
    const sw = sidebar ? sidebar.getBoundingClientRect().width : 230;
    const availW = Math.max(360, window.innerWidth - sw - PAD_L - PAD_R);
    const availH = Math.max(360, window.innerHeight - PAD_T - PAD_B);
    const scale = Math.min(availW / BB_W, availH / BB_H) * 0.98;
    // Enlarge the finished fit by ~30%: the model is a decorative backdrop
    // behind the UI, so letting it run past the padding region is fine and
    // reads as more present. The max clamp is raised so the larger scale
    // isn't silently capped back down.
    const s = Math.max(0.4, Math.min(1.6, scale * 1.3));
    const w = BASE_W * s;
    const h = BASE_H * s;
    const cx = sw + PAD_L + availW / 2;
    const cy = PAD_T + availH * 0.52;
    scene.style.width = w + 'px';
    scene.style.height = h + 'px';
    scene.style.marginLeft = '0px';
    scene.style.marginTop = '0px';
    scene.style.left = (cx - w / 2) + 'px';
    scene.style.top = (cy - h / 2) + 'px';
    saturnSize.w = w;
    saturnSize.h = h;
})();

/* Starfield: scatter a dense layer of softly twinkling stars across the
   whole window behind the Saturn scene. Sizes, brightness and twinkle timing
   are random per star; a few are warm gold to match the scene's rim light. */
(function saturnStarfield() {
    const host = document.getElementById('starfield');
    if (!host) return;
    const count = window.innerWidth < 900 ? 60 : 120;
    const frag = document.createDocumentFragment();
    for (let i = 0; i < count; i++) {
        const s = document.createElement('span');
        const size = 1 + Math.random() * 1.6;
        const gold = Math.random() < 0.18;
        s.className = 'star' + (gold ? ' star--gold' : '');
        s.style.width = size + 'px';
        s.style.height = size + 'px';
        s.style.left = (Math.random() * 100).toFixed(2) + '%';
        s.style.top = (Math.random() * 100).toFixed(2) + '%';
        s.style.opacity = (0.35 + Math.random() * 0.45).toFixed(2);
        s.style.setProperty('--tw-dur', (3 + Math.random() * 4).toFixed(2) + 's');
        s.style.animationDelay = (-Math.random() * 6).toFixed(2) + 's';
        frag.appendChild(s);
    }
    host.appendChild(frag);
})();

/* ======================================================================
   Modrinth mode (Prism-style instances). Fully separate from Valkaw mode.
   ====================================================================== */

const MODE_PAGES = {
    valkaw: ['play', 'resources', 'settings'],
    modrinth: ['instances', 'settings'],
};

let currentMode = 'valkaw';
let instances = [];
let currentInstance = null;
let instCtx = null;
let instCType = 'mod';
let deletingId = null;
let niMCVersions = [];
let niMCSelected = '';
let niMCOrdered = [];

function loaderLabel(l) {
    if (l === 'neoforge') return 'NeoForge';
    if (l === 'fabric') return 'Fabric';
    if (l === 'vanilla') return 'Vanilla';
    return l || '';
}

function applyMode(mode) {
    currentMode = mode;
    document.body.classList.toggle('mode-modrinth', mode === 'modrinth');
    document.querySelectorAll('.mode-btn').forEach(b => b.classList.toggle('active', b.dataset.mode === mode));
    document.querySelectorAll('.nav').forEach(b => {
        const isSettings = b.dataset.page === 'settings';
        const ok = isSettings || (MODE_PAGES[mode] || []).includes(b.dataset.page);
        b.classList.toggle('hidden', !ok);
    });
    if (mode === 'modrinth') {
        showPage('instances');
        refreshInstances();
    } else {
        showPage('play');
    }
}

document.querySelectorAll('.mode-btn').forEach(b =>
    b.addEventListener('click', () => applyMode(b.dataset.mode))
);

async function refreshInstances() {
    const el = $('instanceList');
    $('instanceListStatus').textContent = '';
    try {
        instances = await ListModpackInstances();
    } catch (e) {
        el.innerHTML = `<p class="muted small">Failed to load instances: ${esc(e)}</p>`;
        return;
    }
    if (!instances.length) {
        el.innerHTML = '<div class="inst-empty"><p class="muted small">No instances yet. Click “+ New Instance” to get started.</p></div>';
        return;
    }
    el.innerHTML = '';
    for (const inst of instances) {
        const metaParts = [inst.mcVersion, loaderLabel(inst.loader), inst.loaderVersion && loaderLabel(inst.loader) !== 'Vanilla' ? inst.loaderVersion : ''].filter(Boolean);
        const row = document.createElement('div');
        row.className = 'instance-row';
        row.innerHTML = `
            <div class="instance-icon">${esc((inst.name || inst.id || '?')[0].toUpperCase())}</div>
            <div class="instance-info">
                <strong>${esc(inst.name || inst.id)}</strong>
                <span class="muted small">${esc(metaParts.join(' • '))}</span>
            </div>
            <div class="hub-actions">
                <button class="btn primary small play-inst" data-id="${esc(inst.id)}">▶ Play</button>
                <button class="btn ghost small open-inst" data-id="${esc(inst.id)}">Open</button>
                <button class="btn ghost small detail-inst" data-id="${esc(inst.id)}">Manage</button>
            </div>`;
        row.querySelector('.play-inst').addEventListener('click', () => launchInstance(inst.id));
        row.querySelector('.open-inst').addEventListener('click', () => OpenInstanceFolder(inst.id));
        row.querySelector('.detail-inst').addEventListener('click', () => openInstanceDetail(inst.id));
        el.appendChild(row);
    }
}

async function launchInstance(id) {
    const status = $('instanceListStatus');
    status.textContent = 'Launching…';
    try {
        const res = await LaunchInstance(id);
        status.textContent = res;
    } catch (e) {
        status.textContent = 'Launch error: ' + e;
    }
}

/* ----- New Instance dialog ----- */
function openNewInstance() {
    $('newInstanceModal').classList.remove('hidden');
    $('niCustom').classList.remove('hidden');
    $('niModpack').classList.add('hidden');
    document.querySelectorAll('#newInstanceModal .resource-tab').forEach(b =>
        b.classList.toggle('active', b.dataset.tab === 'custom'));
    $('niStatus').textContent = '';
    $('niPackStatus').textContent = '';
    if (!niMCVersions.length) {
        GetMinecraftVersions().then(list => {
            niMCVersions = list || [];
            niMCOrdered = [...niMCVersions].reverse();
            // Preselect the latest version as the convenient default.
            setNiMC(niMCOrdered[0] || '');
            renderNiMCList('');
            refreshNiLoaderVer();
        }).catch(() => {
            $('niMCValue').textContent = 'Failed to load';
        });
    } else {
        refreshNiLoaderVer();
    }
}

function setNiMC(v) {
    niMCSelected = v || '';
    $('niMCValue').textContent = niMCSelected || 'Select Minecraft version';
    refreshNiLoaderVer();
}

function renderNiMCList(filter) {
    const q = (filter || '').trim().toLowerCase();
    const listEl = $('niMCList');
    listEl.innerHTML = '';
    let count = 0;
    for (const v of niMCOrdered) {
        if (q && !v.toLowerCase().includes(q)) continue;
        count++;
        const opt = document.createElement('button');
        opt.type = 'button';
        opt.className = 'ni-combo-opt' + (v === niMCSelected ? ' sel' : '');
        opt.textContent = v;
        opt.addEventListener('click', () => { setNiMC(v); closeNiMC(); });
        listEl.appendChild(opt);
    }
    if (!count) listEl.innerHTML = '<p class="muted small ni-combo-empty">No version matches “' + (filter || '') + '”</p>';
}

function openNiMC() {
    $('niMCPanel').classList.remove('hidden');
    $('niMCFilter').value = '';
    renderNiMCList('');
    $('niMCFilter').focus();
}
function closeNiMC() { $('niMCPanel').classList.add('hidden'); }
function toggleNiMC() {
    if ($('niMCPanel').classList.contains('hidden')) openNiMC(); else closeNiMC();
}

function refreshNiLoaderVer() {
    const mc = niMCSelected, loader = $('niLoader').value;
    const sel = $('niLoaderVer');
    sel.innerHTML = '<option value="">Latest</option>';
    if (!mc || loader === 'vanilla') return;
    GetLoaderVersions(mc, loader).then(list => {
        sel.innerHTML = '<option value="">Latest</option>' +
            (list || []).map(v => `<option value="${v}">${v}</option>`).join('');
    }).catch(() => {});
}

document.querySelectorAll('#newInstanceModal .resource-tab').forEach(btn =>
    btn.addEventListener('click', () => {
        document.querySelectorAll('#newInstanceModal .resource-tab').forEach(b =>
            b.classList.toggle('active', b === btn));
        $('niCustom').classList.toggle('hidden', btn.dataset.tab !== 'custom');
        $('niModpack').classList.toggle('hidden', btn.dataset.tab !== 'modpack');
    })
);
$('niMCTrigger').addEventListener('click', toggleNiMC);
$('niMCFilter').addEventListener('input', () => renderNiMCList($('niMCFilter').value));
$('niMCFilter').addEventListener('keydown', e => { if (e.key === 'Enter') e.preventDefault(); });
$('niLoader').addEventListener('change', refreshNiLoaderVer);
$('niClose').addEventListener('click', () => { closeNiMC(); $('newInstanceModal').classList.add('hidden'); });
$('newInstanceBtn').addEventListener('click', openNewInstance);
// Close the MC combo when clicking anywhere outside it.
document.addEventListener('click', (e) => {
    const combo = $('niMCCombo');
    if (combo && !combo.contains(e.target)) closeNiMC();
});

$('niCreate').addEventListener('click', async () => {
    const name = $('niName').value.trim();
    const mc = niMCSelected;
    const loader = $('niLoader').value;
    const lv = $('niLoaderVer').value;
    if (!name || !mc) { $('niStatus').textContent = 'Name and Minecraft version are required.'; return; }
    $('niCreate').disabled = true;
    $('niStatus').textContent = 'Creating (downloading game files)…';
    try {
        const inst = await CreateInstance(name, mc, loader, lv);
        $('newInstanceModal').classList.add('hidden');
        showPage('instances');
        await refreshInstances();
        openInstanceDetail(inst.id);
    } catch (e) {
        $('niStatus').textContent = 'Error: ' + e;
    } finally {
        $('niCreate').disabled = false;
    }
});

/* ----- Modpack tab inside New Instance ----- */
function searchNiPacks() {
    const q = $('niPackQuery').value || '';
    $('niPackResults').innerHTML = '<p class="muted small">Searching…</p>';
    $('niPackVersions').innerHTML = '';
    SearchModrinth(q, 'modpack', '', '', 'relevance', 0, 12, '', false).then(raw => {
        const data = JSON.parse(raw);
        renderNiPackResults(data.hits || []);
    }).catch(e => { $('niPackStatus').textContent = 'Search failed: ' + e; });
}

function renderNiPackResults(hits) {
    const grid = $('niPackResults');
    grid.innerHTML = '';
    if (!hits.length) {
        grid.innerHTML = '<p class="muted small">No modpacks found.</p>';
        return;
    }
    for (const hit of hits) {
        const item = document.createElement('button');
        item.className = 'resource-card-item';
        const icon = hit.icon_url || hit.icon_url_png || '';
        item.innerHTML = `
            <img class="resource-icon" src="${esc(icon)}" alt="" width="40" height="40"
                 loading="lazy" decoding="async" referrerpolicy="no-referrer"
                 onerror="this.style.display='none'">
            <div class="resource-card-body">
                <strong>${esc(hit.title || hit.slug || '')}</strong>
                <span class="muted small">${hit.downloads ? hit.downloads.toLocaleString() + ' downloads' : ''}</span>
            </div>`;
        item.addEventListener('click', () => selectNiPack(hit));
        grid.appendChild(item);
    }
}

async function selectNiPack(hit) {
    const slug = hit.slug || hit.project_id || hit.id;
    $('niPackStatus').textContent = 'Loading versions…';
    try {
        const vers = JSON.parse(await GetModrinthProjectVersions(slug, '', '')) || [];
        const box = $('niPackVersions');
        box.innerHTML = '<h4 class="resource-versions-title">Choose a version</h4>';
        if (!vers.length) { box.innerHTML = '<p class="muted small">No versions available.</p>'; return; }
        for (const v of vers) {
            const file = (v.files || []).find(f => f.primary) || (v.files || [])[0];
            const url = file ? file.url : '';
            const name = file ? file.filename : (v.name || v.version_number);
            const row = document.createElement('div');
            row.className = 'resource-version';
            row.innerHTML = `
                <div class="resource-version-info">
                    <strong>${esc(v.version_number || v.name || '')}</strong>
                    <div class="resource-version-meta">
                        <span>${(v.game_versions || []).join(', ')}</span>
                        <span>•</span><span>${(v.loaders || []).join(', ')}</span>
                        <span>•</span><span>${esc(name)}</span>
                    </div>
                </div>
                <button class="btn ghost small">Install</button>`;
            row.querySelector('button').addEventListener('click', async () => {
                const b = row.querySelector('button');
                b.disabled = true; b.textContent = 'Installing…';
                $('niPackStatus').textContent = 'Installing modpack (this downloads the whole pack)…';
                try {
                    const id = await InstallModpack(slug, v.id || v.version_number);
                    $('niPackStatus').textContent = 'Installed.';
                    $('newInstanceModal').classList.add('hidden');
                    showPage('instances');
                    await refreshInstances();
                    openInstanceDetail(id);
                } catch (e) {
                    $('niPackStatus').textContent = 'Install error: ' + e;
                    b.disabled = false; b.textContent = 'Install';
                }
            });
            box.appendChild(row);
        }
        $('niPackStatus').textContent = '';
    } catch (e) {
        $('niPackStatus').textContent = 'Error: ' + e;
    }
}

$('niPackSearch').addEventListener('click', () => { $('niPackVersions').innerHTML = ''; searchNiPacks(); });
$('niPackQuery').addEventListener('keydown', e => { if (e.key === 'Enter') { e.preventDefault(); $('niPackVersions').innerHTML = ''; searchNiPacks(); } });

/* ----- Instance detail ----- */
let instTopTab = 'manage';
let instManageTab = 'mod';
const MANAGE_CATEGORIES = {
    mod: 'mods', shaderpack: 'shaderpacks', resourcepack: 'resourcepacks',
    images: 'screenshots', worlds: 'saves', servers: 'servers',
};

function syncRamOverride() {
    const ov = $('instRamOverride').checked;
    $('instMemoryMin').disabled = !ov;
    $('instMemoryMax').disabled = !ov;
    $('instMemoryHint').textContent = ov
        ? 'Per-instance RAM is used for this instance.'
        : 'Inheriting global RAM (' + (cfg.memoryMB || 3072) + ' MB).';
}
$('instRamOverride').addEventListener('change', syncRamOverride);

function showInstanceTab(tab) {
    instTopTab = tab;
    document.querySelectorAll('.inst-top-tab').forEach(b => b.classList.toggle('active', b.dataset.itab === tab));
    $('instManage').classList.toggle('hidden', tab !== 'manage');
    $('instDownload').classList.toggle('hidden', tab !== 'download');
    $('instSettings').classList.toggle('hidden', tab !== 'settings');
    if (tab === 'manage') {
        refreshManageList();
    } else if (tab === 'download') {
        instSearch();
    }
}

async function openInstanceDetail(id) {
    try {
        currentInstance = await GetInstance(id);
    } catch (e) {
        $('instanceListStatus').textContent = 'Error: ' + e;
        return;
    }
    showPage('instance');
    $('instDetailName').textContent = currentInstance.name || currentInstance.id;
    $('instDetailMeta').textContent =
        `${currentInstance.mcVersion} • ${loaderLabel(currentInstance.loader)}` +
        (currentInstance.loaderVersion && currentInstance.loader !== 'vanilla' ? ' • ' + currentInstance.loaderVersion : '');
    $('instRamOverride').checked = !!currentInstance.memoryOverride;
    $('instMemoryMin').value = currentInstance.memoryMinMB || '';
    $('instMemoryMax').value = currentInstance.memoryMaxMB || '';
    syncRamOverride();
    $('instJavaArgs').value = currentInstance.javaArgs || '';
    const ar = document.querySelector(`input[name="instAfterRun"][value="${currentInstance.afterRun || ''}"]`)
        || document.querySelector('input[name="instAfterRun"][value=""]');
    if (ar) ar.checked = true;
    $('instSettingsStatus').textContent = '';
    $('instContentStatus').textContent = '';
    $('instJavaPath').value = '';
    populateJavaSel();
    try { instCtx = await GetInstanceContentContext(id); } catch (_) {}
    showInstanceTab('manage');
}

function populateJavaSel() {
    const sel = $('instJavaSel');
    sel.innerHTML = '';
    const auto = document.createElement('option');
    auto.value = ''; auto.textContent = 'Auto (recommended)';
    sel.appendChild(auto);
    const custom = document.createElement('option');
    custom.value = 'custom'; custom.textContent = 'Custom path…';
    sel.appendChild(custom);
    ListInstalledJavaMajors().then(majors => {
        for (const m of majors) {
            const o = document.createElement('option');
            o.value = String(m); o.textContent = 'Temurin ' + m + ' (installed)';
            sel.appendChild(o);
        }
        const ov = currentInstance.javaOverride || '';
        if (ov && !/^\d+$/.test(ov)) { sel.value = 'custom'; $('instJavaPath').value = ov; }
        else if (ov) sel.value = ov;
        syncJavaSel();
    });
}

function syncJavaSel() {
    const v = $('instJavaSel').value;
    const custom = v === 'custom';
    $('instJavaCustomRow').classList.toggle('hidden', !custom);
    $('instJavaHint').textContent = custom
        ? 'Enter the full path to a java binary.'
        : (v ? 'Uses installed Temurin ' + v + '.' : 'Auto: chosen from the Minecraft version.');
}
$('instJavaSel').addEventListener('change', syncJavaSel);
$('instSaveSettings').addEventListener('click', async () => {
    let javaOverride = $('instJavaSel').value;
    if (javaOverride === 'custom') javaOverride = $('instJavaPath').value.trim();
    const ov = $('instRamOverride').checked;
    const memMin = parseInt($('instMemoryMin').value) || 0;
    const memMax = parseInt($('instMemoryMax').value) || 0;
    const javaArgs = $('instJavaArgs').value.trim();
    const afterRun = document.querySelector('input[name="instAfterRun"]:checked')?.value || '';
    $('instSaveSettings').disabled = true;
    try {
        await SaveInstanceSettings(currentInstance.id, ov, memMin, memMax, javaOverride, javaArgs, afterRun);
        $('instSettingsStatus').textContent = 'Saved.';
        currentInstance = await GetInstance(currentInstance.id);
    } catch (e) {
        $('instSettingsStatus').textContent = 'Error: ' + e;
    } finally {
        $('instSaveSettings').disabled = false;
    }
});
document.querySelectorAll('.inst-top-tab').forEach(b =>
    b.addEventListener('click', () => showInstanceTab(b.dataset.itab))
);
$('instBack').addEventListener('click', () => { showPage('instances'); refreshInstances(); });
$('instPlayBtn').addEventListener('click', () => launchInstance(currentInstance.id));

/* ----- Manage tab: installed content ----- */
async function refreshManageList() {
    const el = $('instManageList');
    el.innerHTML = '<p class="muted small">Loading…</p>';
    let names = [];
    try {
        names = await ListInstanceContent(currentInstance.id, instManageTab);
    } catch (e) {
        $('instManageStatus').textContent = 'Error: ' + e;
        el.innerHTML = '';
        return;
    }
    if (!names.length) {
        el.innerHTML = '<div class="inst-empty"><p class="muted small">Nothing here yet.</p></div>';
        $('instManageStatus').textContent = '';
        return;
    }
    el.innerHTML = '';
    for (const name of names) {
        const row = document.createElement('div');
        row.className = 'instance-row manage-row';
        row.innerHTML = `
            <div class="instance-icon">${esc((name[0] || '?').toUpperCase())}</div>
            <div class="instance-info"><strong>${esc(name)}</strong></div>
            <div class="hub-actions">
                <button class="btn danger small manage-del">Remove</button>
            </div>`;
        row.querySelector('.manage-del').addEventListener('click', async () => {
            row.querySelector('.manage-del').disabled = true;
            try {
                await DeleteInstanceFile(currentInstance.id, instManageTab, name);
                refreshManageList();
            } catch (e) {
                $('instManageStatus').textContent = 'Error: ' + e;
            }
        });
        el.appendChild(row);
    }
    $('instManageStatus').textContent = '';
}

document.querySelectorAll('#instManage .inst-content-tab').forEach(b =>
    b.addEventListener('click', () => {
        document.querySelectorAll('#instManage .inst-content-tab').forEach(x => x.classList.toggle('active', x === b));
        instManageTab = b.dataset.mtab;
        $('instManageStatus').textContent = '';
        refreshManageList();
    })
);
$('instManageOpenFolder').addEventListener('click', () => OpenInstanceCategoryFolder(currentInstance.id, instManageTab));

/* ----- Instance content browser ----- */
// Map our tab keys to Modrinth's search project_type tokens. Shaders are
// "shader" on the search API ("shaderpack" returns nothing).
function mrProjectType(t) {
    return t === 'shaderpack' ? 'shader' : t;
}

const INST_PAGE = 18; // 3 cols × 6 rows per load
let instOffset = 0;
let instTotal = 0;
let instLoading = false;

function instSearch() {
    const q = $('instQuery').value || '';
    const type = mrProjectType(instCType);
    const loader = type === 'mod' ? (instCtx.loader || 'fabric') : '';
    instOffset = 0; instTotal = 0;
    // Close any open detail popup and show the results grid.
    $('instDetailModal').classList.add('hidden');
    $('instContentStatus').textContent = 'Searching…';
    $('instContentResults').innerHTML = '';
    instLoadPage(q, type, loader, true);
}

async function instLoadPage(q, type, loader, reset) {
    if (instLoading) return;
    instLoading = true;
    try {
        const raw = await SearchModrinth(q, type, instCtx.gameVersion, loader, 'relevance', instOffset, INST_PAGE, '', false);
        const data = JSON.parse(raw);
        const hits = data.hits || [];
        instTotal = data.total_hits || 0;
        renderInstResults(hits, reset);
        instOffset += hits.length;
        if (!hits.length) $('instContentStatus').textContent = 'No results found.';
        else if (instOffset >= instTotal) $('instContentStatus').textContent = '';
        else $('instContentStatus').textContent = '';
    } catch (e) {
        $('instContentStatus').textContent = 'Search failed: ' + e;
    } finally {
        instLoading = false;
    }
}

function renderInstResults(hits, reset) {
    const grid = $('instContentResults');
    if (reset) grid.innerHTML = '';
    if (!hits.length) { if (reset) grid.innerHTML = '<p class="muted small">Nothing found.</p>'; return; }
    for (const hit of hits) {
        const item = document.createElement('button');
        item.className = 'resource-card-item';
        const icon = hit.icon_url || hit.icon_url_png || '';
        item.innerHTML = `
            <img class="resource-icon" src="${esc(icon)}" alt="" width="40" height="40"
                 loading="lazy" decoding="async" referrerpolicy="no-referrer"
                 onerror="this.style.display='none'">
            <div class="resource-card-body">
                <strong>${esc(hit.title || hit.slug || '')}</strong>
                <span class="muted small">${esc(hit.author || hit.team || '')}</span>
            </div>`;
        item.addEventListener('click', () => instSelectVersion(hit));
        grid.appendChild(item);
    }
}

// Infinite scroll: load the next 24 items when the instance page is scrolled
// near the bottom of the results grid, until Modrinth has no more results.
const instPageEl = $('page-instance');
if (instPageEl) {
    instPageEl.addEventListener('scroll', () => {
        if (instLoading || instOffset >= instTotal) return;
        const nearBottom = instPageEl.scrollTop + instPageEl.clientHeight >= instPageEl.scrollHeight - 140;
        if (!nearBottom) return;
        const q = $('instQuery').value || '';
        const type = mrProjectType(instCType);
        const loader = type === 'mod' ? (instCtx.loader || 'fabric') : '';
        instLoadPage(q, type, loader, false);
    });
}

function instSelectVersion(hit) {
    // Deprecated inline version list; replaced by openInstDetail below.
    // Left as a no-op to avoid breaking callers.
    openInstDetail(hit);
}

async function openInstDetail(hit) {
    const slug = hit.slug || hit.project_id || hit.id;
    const type = instCType;
    const loader = type === 'mod' ? (instCtx.loader || 'fabric') : '';
    $('instDetailModal').classList.remove('hidden');
    $('instDetailVersions').innerHTML = '';
    $('instDetailChangelog').innerHTML = '<p class="muted small">Loading…</p>';
    $('instDetailStatus').textContent = '';
    $('instContentStatus').textContent = '';
    try {
        const proj = JSON.parse(await GetModrinthProject(slug));
        $('instDetailTitle').textContent = proj.title || hit.title || slug;
        $('instDetailDesc').textContent = proj.description || '';
        const icon = proj.icon_url || proj.icon_url_png || hit.icon_url || '';
        $('instDetailIcon').src = icon;
        $('instDetailIcon').style.display = icon ? '' : 'none';
        const author = proj.author || proj.team || '';
        const downloads = proj.downloads != null ? proj.downloads.toLocaleString() + ' downloads' : '';
        $('instDetailSub').textContent = [author, downloads].filter(Boolean).join(' • ');
        $('instDetailMeta').textContent = `Minecraft ${instCtx.gameVersion} • ${loaderLabel(instCtx.loader)}`;

        const vers = JSON.parse(await GetModrinthProjectVersions(slug, instCtx.gameVersion, loader)) || [];
        renderInstDetailVersions(vers, type);
        if (vers.length) loadInstChangelog(vers[0].id);
    } catch (e) {
        $('instDetailChangelog').innerHTML = '<p class="muted small">Failed to load project: ' + esc(e) + '</p>';
    }
}

function renderInstDetailVersions(vers, type) {
    const box = $('instDetailVersions');
    box.innerHTML = '';
    if (!vers.length) {
        box.innerHTML = '<p class="muted small">No versions match this instance&#39;s Minecraft version / loader.</p>';
        return;
    }
    for (const v of vers) {
        const file = (v.files || []).find(f => f.primary) || (v.files || [])[0];
        const url = file ? file.url : '';
        const name = file ? file.filename : (v.version_number || v.name);
        const row = document.createElement('div');
        row.className = 'resource-version';
        row.innerHTML = `
            <div class="resource-version-info">
                <strong>${esc(v.version_number || v.name || '')}</strong>
                <div class="resource-version-meta">
                    <span>${(v.game_versions || []).join(', ')}</span>
                    <span>•</span><span>${(v.loaders || []).join(', ')}</span>
                    <span>•</span><span>${esc(name)}</span>
                </div>
            </div>
            <div class="hub-actions">
                <button class="btn ghost small inst-cl">Changelog</button>
                <button class="btn ghost small inst-install">Install</button>
            </div>`;
        row.querySelector('.inst-cl').addEventListener('click', () => loadInstChangelog(v.id));
        row.querySelector('.inst-install').addEventListener('click', async () => {
            const b = row.querySelector('.inst-install');
            b.disabled = true; b.textContent = 'Installing…';
            $('instDetailStatus').textContent = 'Downloading ' + name + '…';
            try {
                await DownloadResourceToInstance(url, name, type, currentInstance.id);
                $('instDetailStatus').textContent = 'Installed ' + name + ' ✅';
                b.textContent = 'Installed';
            } catch (e) {
                $('instDetailStatus').textContent = 'Error: ' + e;
                b.disabled = false; b.textContent = 'Install';
            }
        });
        box.appendChild(row);
    }
}

async function loadInstChangelog(versionID) {
    const el = $('instDetailChangelog');
    el.innerHTML = '<p class="muted small">Loading changelog…</p>';
    try {
        const v = JSON.parse(await GetModrinthVersion(versionID));
        el.innerHTML = '<h4 class="resource-versions-title">Changelog</h4><div class="inst-changelog-body">' +
            (esc(v.changelog || '(no changelog)').replace(/\n/g, '<br>')) + '</div>';
    } catch (e) {
        el.innerHTML = '<p class="muted small">Could not load changelog: ' + esc(e) + '</p>';
    }
}

$('instDetailClose').addEventListener('click', () => { $('instDetailModal').classList.add('hidden'); $('instDetailStatus').textContent = ''; });

document.querySelectorAll('#instDownload .inst-content-tab').forEach(b =>
    b.addEventListener('click', () => {
        document.querySelectorAll('#instDownload .inst-content-tab').forEach(x => x.classList.toggle('active', x === b));
        instCType = b.dataset.ctype;
        if (currentInstance) instSearch();
    })
);
$('instSearchBtn').addEventListener('click', () => instSearch());
$('instQuery').addEventListener('keydown', e => { if (e.key === 'Enter') { e.preventDefault(); instSearch(); } });

/* ----- Delete instance (two-step) ----- */
$('instDeleteBtn').addEventListener('click', () => {
    deletingId = currentInstance.id;
    $('diName').value = '';
    $('diName').placeholder = currentInstance.name || currentInstance.id;
    $('diConfirm').disabled = true;
    $('diStatus').textContent = '';
    $('deleteInstModal').classList.remove('hidden');
});
$('diName').addEventListener('input', () => {
    $('diConfirm').disabled = $('diName').value.trim() !== (currentInstance?.name || '');
});
$('diCancel').addEventListener('click', () => $('deleteInstModal').classList.add('hidden'));
$('diConfirm').addEventListener('click', async () => {
    $('diConfirm').disabled = true;
    $('diStatus').textContent = 'Deleting…';
    try {
        await DeleteInstance(deletingId);
        $('deleteInstModal').classList.add('hidden');
        showPage('instances');
        refreshInstances();
    } catch (e) {
        $('diStatus').textContent = 'Error: ' + e;
        $('diConfirm').disabled = false;
    }
});

/* Mirror install progress onto the Modrinth-mode progress bar. */
EventsOn('install:progress', (stage, pct) => {
    const p = Math.max(0, Math.min(100, pct || 0));
    $('instanceProgressBar').style.width = p + '%';
    $('instanceProgressStage').textContent = `${stage} ${pct || 0}%`;
});

/* ----- Crash popup (works in both Valkaw and Modrinth mode) ----- */
let crashDir = '';
EventsOn('game:crash', (p) => {
    crashDir = p.gameDir || '';
    $('crashReason').textContent = p.reason || 'The game exited unexpectedly.';
    $('crashPath').textContent = p.logPath || '';
    $('crashLog').textContent = p.log || '(no log content)';
    $('crashModal').classList.remove('hidden');
});
$('crashClose').addEventListener('click', () => $('crashModal').classList.add('hidden'));
$('crashCopy').addEventListener('click', async () => {
    const text = $('crashLog').textContent;
    try { await navigator.clipboard.writeText(text); } catch (_) {}
    const b = $('crashCopy');
    const old = b.textContent;
    b.textContent = 'Copied';
    setTimeout(() => { b.textContent = old; }, 1200);
});
$('crashOpenFolder').addEventListener('click', () => { if (crashDir) OpenFolder(crashDir); });
