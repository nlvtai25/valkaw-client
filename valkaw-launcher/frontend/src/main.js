import './style.css';

import { GetConfig, SaveConfig, Launch, CheckUpdate, OpenReleases, InstallClient, CheckInstalled, DetectJava, InstallUpdate, GetVersions, ListGPUs, CheckRequirements, InstallJava, SetGpuPreference, CheckVulkan, GetResourceBrowserContext, SearchModrinth, GetModrinthProject, GetModrinthProjectVersions, DownloadResource, OpenResourceDir, ScanConflictMods } from '../wailsjs/go/core/App';
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
    sel.innerHTML = versions.map(v => `<option value="${v}">${v}</option>`).join('');
    sel.value = versions.includes(cfg.version) ? cfg.version : versions[0];
    cfg.version = sel.value;
    updateVersionValue();
    updateHeroMeta();
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
        btn.textContent = 'Install / Repair';
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
        launchGame();
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
        item.innerHTML = `
            <img class="resource-icon" src="${esc(icon)}" alt="" width="56" height="56"
                 loading="lazy" decoding="async" referrerpolicy="no-referrer"
                 onerror="this.style.display='none'">
            <div class="resource-card-body">
                <strong>${esc(hit.title || hit.name || hit.slug || '')}</strong>
                <span class="muted small">${esc(hit.author || hit.team || '')}</span>
                <span class="muted small">${hit.downloads ? hit.downloads.toLocaleString() + ' downloads' : ''}</span>
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
            buildResourceFilterPayload()
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
