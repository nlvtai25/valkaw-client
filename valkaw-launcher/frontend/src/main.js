import './style.css';

import { GetConfig, SaveConfig, Launch, CheckUpdate, OpenReleases, InstallClient, CheckInstalled, DetectJava, InstallUpdate, GetVersions, ListGPUs, CheckRequirements, InstallJava, SetGpuPreference, CheckVulkan, GetResourceBrowserContext, SearchModrinth, GetModrinthProject, GetModrinthProjectVersions, DownloadResource, OpenResourceDir } from '../wailsjs/go/core/App';
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
        $('pageTitle').textContent = pages[name].title;
        $('pageEyebrow').textContent = pages[name].eyebrow;
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
    $('welcome').textContent = cfg.username ? `Welcome back, ${cfg.username}` : 'Welcome back';
    refreshInstallStatus();
}

async function loadVersions() {
    const sel = $('version');
    const versions = await GetVersions();
    sel.innerHTML = versions.map(v => `<option value="${v}">${v}</option>`).join('');
    sel.value = versions.includes(cfg.version) ? cfg.version : versions[0];
    cfg.version = sel.value;
    updateHeroMeta();
}

function updateHeroMeta() {
    $('heroMeta').textContent = `Minecraft ${$('version').value} • Valkaw Client`;
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
            <span class="muted small">${g.vendor ? esc(g.vendor.toUpperCase() + (g.address ? ' • ' + g.address : '')) : 'let the driver decide'}</span>
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

document.querySelectorAll('input[name="profile"]').forEach(r => r.addEventListener('change', async (e) => {
    if (e.target.value === 'vk' && !(await vulkanReady())) {
        const back = document.querySelector(`input[name="profile"][value="${lastProfile}"]`);
        if (back) back.checked = true;
        return;
    }
    lastProfile = e.target.value;
    cfg.profile = e.target.value;
    await SaveConfig(cfg);
}));

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
    updateHeroMeta();
    await SaveConfig(cfg);
    refreshInstallStatus();
});

async function refreshInstallStatus() {
    const res = await CheckInstalled();
    $('installHint').textContent = res === 'installed'
        ? 'Installed — ready to play'
        : res;
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
        el.textContent = 'update check failed';
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
    const reqs = await CheckRequirements();
    if (!reqs.ready) openPrereq(reqs);
    const ready = await refreshUpdateStatus();
    if (cfg.autoUpdate && ready) {
        $('updateStatus').textContent = 'Auto-updating...';
        $('updateStatus').textContent = await InstallUpdate();
    }
}).catch(err => {
    $('status').textContent = 'config error: ' + err;
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
                await DownloadResource(url, name, resourceDestTypeForTab(resourceTab));
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
    $('welcome').textContent = cfg.username ? `Welcome back, ${cfg.username}` : 'Welcome back';
    refreshInstallStatus();
});
