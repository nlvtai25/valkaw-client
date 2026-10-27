import './style.css';

import { GetConfig, SaveConfig, Launch, CheckUpdate, OpenReleases, InstallClient, CheckInstalled, DetectJava, InstallUpdate, GetVersions, ListGPUs } from '../wailsjs/go/main/App';
import { EventsOn } from '../wailsjs/runtime/runtime';

const $ = (id) => document.getElementById(id);

const pages = {
    play: { title: 'Play', eyebrow: 'Ready to play' },
    settings: { title: 'Settings', eyebrow: 'Configure your client' },
};

function showPage(name) {
    document.querySelectorAll('.page').forEach(p => p.classList.add('hidden'));
    document.getElementById('page-' + name).classList.remove('hidden');
    document.querySelectorAll('.nav').forEach(b => b.classList.toggle('active', b.dataset.page === name));
    $('pageTitle').textContent = pages[name].title;
    $('pageEyebrow').textContent = pages[name].eyebrow;
}

document.querySelectorAll('.nav').forEach(b => b.addEventListener('click', () => showPage(b.dataset.page)));

let cfg = {};
let updateReady = false;

async function loadConfig() {
    cfg = await GetConfig();
    $('username').value = cfg.username || '';
    const profile = cfg.profile || 'gl';
    const radio = document.querySelector(`input[name="profile"][value="${profile}"]`);
    if (radio) radio.checked = true;
    $('javaPath').value = cfg.javaPath || 'java';
    $('gameDir').value = cfg.gameDir || '';
    await loadVersions();
    await loadGPUs();
    $('memoryMB').value = cfg.memoryMB || 4096;
    $('windowWidth').value = cfg.windowWidth || 1600;
    $('windowHeight').value = cfg.windowHeight || 900;
    syncWindowPreset();
    $('autoUpdate').checked = !!cfg.autoUpdate;
    const ar = document.querySelector(`input[name="afterRun"][value="${cfg.afterRun || 'hide'}"]`);
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

$('version').addEventListener('change', async () => {
    cfg.version = $('version').value;
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
}

EventsOn('install:progress', (stage, pct) => setInstallProgress(stage, pct));

function currentUsername() {
    const name = $('username').value.trim();
    updateUserChip();
    return name;
}

function updateUserChip() {
    const name = $('username').value.trim() || 'Guest';
    $('userChip').textContent = name;
}

$('username').addEventListener('input', updateUserChip);
$('username').addEventListener('keydown', (e) => {
    if (e.key !== 'Enter') return;
    e.preventDefault();
    currentUsername();
    $('username').blur();
});

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
    cfg.username = currentUsername();
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
    const ready = await refreshUpdateStatus();
    if (cfg.autoUpdate && ready) {
        $('updateStatus').textContent = 'Auto-updating...';
        $('updateStatus').textContent = await InstallUpdate();
    }
}).catch(err => {
    $('status').textContent = 'config error: ' + err;
});

$('playBtn').addEventListener('click', async () => {
    const status = $('status');
    const btn = $('playBtn');
    const profile = document.querySelector('input[name="profile"]:checked').value;
    cfg.profile = profile;
    cfg.version = $('version').value;
    cfg.username = currentUsername();
    cfg.windowWidth = parseInt($('windowWidth').value) || 1600;
    cfg.windowHeight = parseInt($('windowHeight').value) || 900;
    await SaveConfig(cfg);
    btn.disabled = true;
    btn.textContent = 'Launching...';
    status.textContent = 'Launching Valkaw Client...';
    try {
        const res = await Launch();
        status.textContent = res;
    } catch (e) {
        status.textContent = 'Error: ' + e;
    } finally {
        btn.disabled = false;
        btn.textContent = '▶ Play';
    }
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
    cfg.username = currentUsername();
    cfg.profile = document.querySelector('input[name="profile"]:checked').value;
    cfg.gpu = document.querySelector('input[name="gpu"]:checked')?.value || '';
    const res = await SaveConfig(cfg);
    status.textContent = res;
    updateUserChip();
    $('welcome').textContent = cfg.username ? `Welcome back, ${cfg.username}` : 'Welcome back';
    refreshInstallStatus();
});
