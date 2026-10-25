#!/usr/bin/env python3
"""Valkaw launcher for Linux.

Tai pack mod tu GitHub Releases, dam bao Minecraft 1.21.11 + Fabric loader
( dung ~/.minecraft neu co, khong thi tai tu Mojang ) roi chay game.

Chay: python3 valkaw.py            (GUI neu co tkinter, khong thi CLI)
      python3 valkaw.py --cli      (luon CLI)
      python3 valkaw.py --install  (tao menu entry ~/.local/share/applications)
      python3 valkaw.py --prefetch (tai san pack + runtime, khong chay game)
"""

import base64
import concurrent.futures
import json
import os
import platform
import re
import shutil
import subprocess
import sys
import tarfile
import threading
import time
import urllib.request
import zipfile

MC = "1.21.11"
# Ghim loader dung voi ban Windows da test (launch.ps1 chon 0.19.5 trong ~/.minecraft).
# Doi khi nang cap mod; neu meta khong con version nay thi tu lay ban moi nhat.
FABRIC_LOADER = "0.19.5"
REPO = "https://github.com/nlvtai25/valkaw-client"
VARIANTS = ["Sodium", "VulkanMod"]
ROOT = os.path.join(os.path.expanduser("~"), ".valkaw")
UA = "ValkawLauncher/linux"

# Version cua launcher (doc lap voi version client). Ban phat hanh la 1 file nen asset
# phai ten "Valkaw.Launcher-linux-<APP_VERSION>.py" - launcher so version trong ten de biet
# co ban moi, roi tai de va ghi de chinh no.
APP_VERSION = "1.6"
CLIENT_VERSION = "1.5"   # khop ten pack <Variant>_v<ver>.jar, hien trong header changelog
SELF_PREFIX = "Valkaw.Launcher-linux-"
UPDATE_DIR = os.path.join(ROOT, "update")

# >>> CHANGELOG - nguon duy nhat cua o Changelog trong launcher (ca Windows: build-launcher.ps1
# doc khoi nay va nhung vao exe thanh resource "changelog_text"). Moi lan phat hanh: them 1 khoi
# moi LEN DAU. Dong dau "Valkaw <ver> - <ngay> - client <client ver>", sau do "Launcher" / "Client"
# va cac dong bat dau bang "+ ". Mac dinh tieng Anh.
# Khoi MOI NHAT dung placeholder {launcher} / {client} de version luon lay tu APP_VERSION /
# CLIENT_VERSION (sua 1 cho, khong bao gio lech ten version). Khoi cu de nguyen so da phat hanh.
CHANGELOG = """Valkaw {launcher} - client v{client} - 25/10/2026
Launcher
+ Launcher version 1.6; client stays v1.5 (no bump requested).
+ Single-source changelog block; titles pull the real version from the launcher constants.
+ Launcher UI: footer reorganized into 2 rows, changelog card with scroll padding and status line made bolder.
Client
+ Client Settings redesigned: 400px app shell with left nav rail, header and footer; adaptive module grid.
+ Tabs, search and footer action align to one content grid; all tabs keep scrolling.
Valkaw 1.5 - 23/10/2026 - client v1.4
Launcher
+ Checks for a new launcher build on every start (Windows + Linux) and asks before updating.
+ Windows: replaces itself and relaunches. Linux: applies the update on the next start.
+ New Changelog tab: launcher and client changes; click a version to collapse or expand it.
+ Fixed the Play button when Username is empty (Linux).
Client
+ No client changes - still v1.3.
Valkaw 1.3 - 22/10/2026 - client v1.3
Launcher
+ Reads pack file names from GitHub Releases (cached); a new build only needs an uploaded zip.
+ Separate log file per game directory.
Client
+ Fixed the Symbols panel: Faces/Nature tabs were not clickable (wrong hitbox argument order).
+ Valkaw keybinds in Options > Controls (HUD Editor, Auto Sprint, Freelook).
+ HUD: Options/Skins tabs scroll, ArmorPart crash fixed, skin/cape images no longer packed.
Valkaw 1.2 - 22/10/2026 - client v1.2
Launcher
+ Removed the installer: a single portable file, no admin rights needed.
Client
+ Infinity Chat History: keeps up to 10,000 chat lines (Perf tab).
+ Split the HUD tabs into PerfTab / OptionsTab / SkinTab.
Valkaw 1.1 - 22/10/2026 - client v1.1
Client
+ Fixed the mixin crash when joining a server (dropped Packet Fixer from the Vulkan build).
Valkaw 1.0 - 21/10/2026 - client v1.0
Launcher
+ First build: downloads the pack, extracts it into the game directory, starts the game.
Client
+ Sodium v1.0 and VulkanMod v1.0, HUD editor, SimpleMenu.
"""
# <<< CHANGELOG


def changelog_blocks(text=None):
    """[(tieu de, [(ten nhom, [dong...]), ...]), ...] tu CHANGELOG.

    Tieu de co {launcher}/{client} duoc thay bang version that, nen khong bao gio lech ten version.
    """
    blocks, cur = [], None
    for raw in (CHANGELOG if text is None else text).splitlines():
        line = raw.rstrip()
        if not line.strip():
            continue
        if line.startswith("Valkaw "):
            title = (line.strip().replace("{launcher}", APP_VERSION)
                     .replace("{client}", CLIENT_VERSION))
            cur = (title, [])
            blocks.append(cur)
        elif cur is not None:
            if line.startswith("+"):
                if not cur[1]:
                    cur[1].append(("", []))
                cur[1][-1][1].append(line.lstrip("+ ").strip())
            else:
                cur[1].append((line.strip(), []))
    return blocks

# ZGC chi hop voi MC 26.2+; 1.21.11 dung G1 (giong bo flag trong launch.ps1 cua ban Windows).
DEFAULT_JVM = ("-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 "
               "-XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch "
               "-XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 "
               "-XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 "
               "-XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 "
               "-XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 "
               "-XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1")


# Icon 68x68 va logo 96x96 nhung san (PNG base64) de launcher chi la 1 file.
ICON_B64 = (
    "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"
    "HwAwogD4/wFtE9l5pNmqNQAAAABJRU5ErkJggg=="
)
LOGO_B64 = (
    "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"
)

_lock = threading.Lock()
_status_cb = lambda s: None


def say(msg):
    print(msg, flush=True)
    try:
        _status_cb(msg)
    except Exception:
        pass


# ---------------------------------------------------------------- settings

def settings_file():
    return os.path.join(ROOT, "settings.txt")


def load_settings():
    cfg = {"ram": "6", "priority": "Normal", "after": "Hide",
           "gamedir": "", "variant": "Sodium",
           "update": "1", "update.skip": "",
           "offline.lastdir": ""}
    try:
        with open(settings_file(), encoding="utf-8") as f:
            for line in f:
                if "=" not in line:
                    continue
                k, v = line.split("=", 1)
                k, v = k.strip(), v.strip()
                if k in cfg:
                    cfg[k] = v
        if cfg["variant"] not in VARIANTS:
            cfg["variant"] = "Sodium"
    except OSError:
        pass
    return cfg


def save_settings(cfg):
    os.makedirs(ROOT, exist_ok=True)
    lines = ["ram=%s" % cfg.get("ram", "6"), "priority=%s" % cfg.get("priority", "Normal"),
             "after=%s" % cfg.get("after", "Hide"), "gamedir=%s" % cfg.get("gamedir", ""),
             "variant=%s" % cfg.get("variant", "Sodium"),
             "offline.lastdir=%s" % cfg.get("offline.lastdir", ""),
             "update.skip=%s" % cfg.get("update.skip", "")]
    with open(settings_file(), "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


def username():
    try:
        with open(os.path.join(ROOT, "username.txt"), encoding="utf-8") as f:
            u = f.read().strip()
        return u or "Player"
    except OSError:
        return "Player"


def game_dir(cfg):
    base = (cfg.get("gamedir") or "").strip()
    if cfg.get("variant") == "VulkanMod":
        d = base + "_vulkan" if base else os.path.join(ROOT, "game_vulkan")
    else:
        d = base or os.path.join(ROOT, "game_sodium")
        old = os.path.join(ROOT, "game")
        if not os.path.isdir(d) and os.path.isdir(old):
            try:
                os.rename(old, d)
            except OSError:
                pass
    return d


# ---------------------------------------------------------------- network

def http(url, timeout=60):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read()


def download(url, dest, size=None, sha1=None):
    """Tai 1 file, co retry. Bo qua neu file da ton tai va dung kich thuoc."""
    import hashlib
    if os.path.isfile(dest) and size is not None and os.path.getsize(dest) == size:
        return
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    tmp = dest + ".part"
    last = None
    for attempt in range(3):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            h = hashlib.sha1() if sha1 else None
            with urllib.request.urlopen(req, timeout=90) as r, open(tmp, "wb") as f:
                while True:
                    chunk = r.read(1 << 18)
                    if not chunk:
                        break
                    if h:
                        h.update(chunk)
                    f.write(chunk)
            if sha1 and h.hexdigest() != sha1:
                raise IOError("sha1 mismatch: " + os.path.basename(dest))
            os.replace(tmp, dest)
            return
        except Exception as e:  # noqa: BLE001 - thu lai roi moi bao loi
            last = e
            time.sleep(1 + attempt)
    raise IOError("Khong tai duoc %s (%s)" % (url, last))


def latest_assets():
    """Ten moi asset trong release moi nhat; cache lai de chay offline van biet.
    Giu ca ten khong phai .zip de pick_self() tim duoc ban launcher."""
    names = []
    cache = os.path.join(ROOT, "release-assets.txt")
    try:
        api = ("https://api.github.com/repos/" + REPO.split("github.com/", 1)[1].strip("/")
               + "/releases/latest")
        data = http(api, 30).decode("utf-8", "replace")
        names = [m.group(1) for m in re.finditer(r'"name"\s*:\s*"([^"]+)"', data)]
        if names:
            os.makedirs(ROOT, exist_ok=True)
            with open(cache, "w", encoding="utf-8") as f:
                f.write("\n".join(names))
    except Exception:  # noqa: BLE001 - offline: dung cache
        pass
    if not names and os.path.isfile(cache):
        with open(cache, encoding="utf-8") as f:
            names = [line.strip() for line in f if line.strip()]
    return names


def pick_pack(variant):
    prefix = ("Vulkan" if variant == "VulkanMod" else "Sodium") + "_"
    best, best_ver = None, None
    for n in latest_assets():
        if not n.lower().startswith(prefix.lower()):
            continue
        if not n.lower().endswith(".jar"):
            continue
        m = re.search(r"\d+(?:\.\d+)+", n)
        ver = None
        if m:
            try:
                ver = tuple(int(x) for x in m.group(0).split("."))
            except ValueError:
                ver = None
        if best is None or (ver and (best_ver is None or ver > best_ver)):
            best, best_ver = n, ver
    if not best:
        raise RuntimeError("Khong tim thay %s*.jar tren GitHub Releases "
                           "(kiem tra mang hoac tai tay)." % prefix)
    return best


def pick_local_jar(variant, lastdir=None):
    """Mo file picker de chon JAR local. Tra ve (path, lastdir) hoac (None, lastdir)."""
    from tkinter import filedialog
    initial = lastdir or os.path.join(os.path.expanduser("~"), "Downloads")
    if not os.path.isdir(initial):
        initial = os.path.expanduser("~")
    path = filedialog.askopenfilename(
        title="Chon pack JAR", initialdir=initial,
        filetypes=[("Fabric bundle JAR", "*.jar"), ("All files", "*.*")])
    if not path:
        return None, lastdir
    return path, os.path.dirname(path)


# ---------------------------------------------------------------- self-update

def parse_ver(s):
    """Lay phan so trong ten file/string: 'Valkaw.Launcher-linux-1.10.zip' -> (1, 10)."""
    m = re.search(r"\d+(?:\.\d+)+", s or "")
    if not m:
        return None
    try:
        return tuple(int(x) for x in m.group(0).split("."))
    except ValueError:
        return None


def ver_str(v):
    return ".".join(str(x) for x in (v or ()))


def ver_newer(a, b):
    """a moi hon b; so sanh duoc tuple khac do dai (1.10 > 1.9, 1.9 == 1.9.0)."""
    if not a or not b:
        return False
    n = max(len(a), len(b))
    return a + (0,) * (n - len(a)) > b + (0,) * (n - len(b))


def pick_self(names=None):
    """Asset launcher moi nhat cua Linux trong release: (ten, version) hoac (None, None)."""
    best, best_ver = None, None
    for n in (latest_assets() if names is None else names):
        low = n.lower()
        if not low.startswith(SELF_PREFIX.lower()) or not low.endswith(".py"):
            continue
        v = parse_ver(n[len(SELF_PREFIX):])
        if v and (best_ver is None or v > best_ver):
            best, best_ver = n, v
    return (best, best_ver) if best else (None, None)


def update_disabled(cfg):
    return str(cfg.get("update", "1")).strip().lower() in ("0", "false", "off", "no")


def check_self_update(cfg):
    """Co ban moi hon ban dang chay (va khong bi skip) thi tra (ten, version)."""
    if update_disabled(cfg):
        return None, None
    name, v = pick_self()
    if not name or not ver_newer(v, parse_ver(APP_VERSION)):
        return None, None
    if (cfg.get("update.skip") or "").strip() == ver_str(v):
        return None, None
    return name, v


def apply_pending(update_dir=None, install_dir=None):
    """Cai ban da tai trong <ROOT>/update/pending.py. True neu da thay file.

    Ban phat hanh la 1 file valkaw.py nen chi kiem tra cu phap (compile) roi ghi de;
    file hong bi tu choi va giu lai de thu lai lan sau (khong bao gio lam hong launcher dang chay).
    """
    udir = update_dir or UPDATE_DIR
    dst = os.path.join(install_dir or os.path.dirname(os.path.abspath(__file__)), "valkaw.py")
    src = os.path.join(udir, "pending.py")
    if not os.path.isfile(src):
        return False
    try:
        with open(src, "rb") as f:
            code = f.read()
        compile(code, "pending.py", "exec")
        if not re.search(rb"(?m)^APP_VERSION\s*=", code):
            raise IOError("khong phai valkaw.py")
        os.replace(src, dst)
        os.chmod(dst, 0o755)
    except (OSError, IOError, SyntaxError, ValueError):
        return False
    shutil.rmtree(udir, ignore_errors=True)
    return True


def download_self(name):
    os.makedirs(UPDATE_DIR, exist_ok=True)
    dest = os.path.join(UPDATE_DIR, "pending.py")
    download(REPO + "/releases/latest/download/" + name, dest)
    return dest


def apply_startup_update():
    """Ban tai tu lan truoc: thay file truoc khi nap GUI (file dang chay da nam trong RAM)."""
    src = os.path.join(UPDATE_DIR, "pending.py")
    if not os.path.isfile(src):
        return False
    try:
        with open(os.path.join(UPDATE_DIR, "pending.ver"), encoding="utf-8") as f:
            v = parse_ver(f.read())
    except OSError:
        v = None
    if not ver_newer(v, parse_ver(APP_VERSION)):
        shutil.rmtree(UPDATE_DIR, ignore_errors=True)
        return False
    return apply_pending()


def verify_bundle(path, variant, mc_version=MC):
    try:
        with zipfile.ZipFile(path) as z:
            names = [e.filename.replace("\\", "/") for e in z.infolist() if not e.is_dir()]
            if "fabric.mod.json" not in names:
                raise RuntimeError("Bundle thieu fabric.mod.json")
            mod_entries = [n for n in names if n.startswith("mods/") and n.endswith(".jar")]
            if not mod_entries:
                raise RuntimeError("Bundle khong co mod nao trong mods/")
            with z.open("fabric.mod.json") as f:
                meta = json.load(f)
            expected_id = "valkaw-pack-sodium" if variant != "VulkanMod" else "valkaw-pack-vulkan"
            if meta.get("id") != expected_id:
                raise RuntimeError("Bundle variant khong khop: can %s, co %s" % (expected_id, meta.get("id")))
            depends = meta.get("depends", {})
            if depends.get("minecraft") != mc_version:
                raise RuntimeError("Bundle MC dependency khong khop: can %s, co %s" % (mc_version, depends.get("minecraft")))
            declared = [j.get("file") for j in meta.get("jars", [])]
            if len(declared) != len(mod_entries):
                raise RuntimeError("Bundle lech: %d file trong mods/, %d khai bao" % (len(mod_entries), len(declared)))
            for ref in declared:
                if ref not in names:
                    raise RuntimeError("Bundle thieu nested jar: %s" % ref)
            for n in names:
                if n.startswith("/") or ".." in n.split("/"):
                    raise RuntimeError("Entry path khong an toan: %s" % n)
    except (zipfile.BadZipFile, json.JSONDecodeError, OSError) as e:
        raise RuntimeError("Bundle hong hoac doc loi: %s" % e)


def ensure_pack(gdir, variant, local_jar=None):
    if local_jar:
        zip_name = os.path.basename(local_jar)
        verify_bundle(local_jar, variant)
        tmp = local_jar
        is_local = True
    else:
        zip_name = pick_pack(variant)
        tmp = os.path.join(ROOT, zip_name)
        is_local = False
        say("Dang tai %s ..." % zip_name)
        download(REPO + "/releases/latest/download/" + zip_name, tmp)
    marker = os.path.join(gdir, ".pack-" + re.sub(r"[^0-9A-Za-z._-]", "_", zip_name))
    if os.path.isfile(marker):
        if is_local:
            try:
                with open(marker, encoding="utf-8") as f:
                    old_hash = f.read().strip()
                if old_hash:
                    h = hashlib.sha256()
                    with open(tmp, "rb") as fh:
                        for chunk in iter(lambda: fh.read(65536), b""):
                            h.update(chunk)
                    if h.hexdigest() == old_hash:
                        return
            except Exception:
                pass
        else:
            return
    mods = os.path.join(gdir, "mods")
    os.makedirs(mods, exist_ok=True)
    for f in os.listdir(mods):
        if f.endswith(".jar") and (f.startswith(("Sodium_", "VulkanMod_",
                                                  "mc-client-standalone-"))):
            try:
                os.remove(os.path.join(mods, f))
            except OSError:
                pass
    for d in (mods, gdir):
        for f in os.listdir(d):
            if f.startswith((".jar-v", ".defaults-v", ".pack-")):
                try:
                    os.remove(os.path.join(d, f))
                except OSError:
                    pass
    is_jar = zip_name.lower().endswith(".jar")
    with zipfile.ZipFile(tmp) as z:
        for e in z.infolist():
            if e.is_dir():
                continue
            rel = e.filename.replace("\\", "/")
            if is_jar and (rel.startswith("mods/") or rel == "fabric.mod.json"):
                continue
            dest = os.path.join(gdir, *rel.split("/"))
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            if not rel.startswith("mods/") and os.path.exists(dest):
                continue
            with z.open(e) as src, open(dest, "wb") as out:
                shutil.copyfileobj(src, out)
    if is_jar:
        shutil.copyfile(tmp, os.path.join(mods, zip_name))
    if not is_local:
        try:
            os.remove(tmp)
        except OSError:
            pass
    h = hashlib.sha256()
    src_for_hash = tmp if is_local else os.path.join(mods, zip_name)
    with open(src_for_hash, "rb") as fh:
        for chunk in iter(lambda: fh.read(65536), b""):
            h.update(chunk)
    with open(marker, "w", encoding="utf-8") as f:
        f.write(h.hexdigest())
    say("Da cai %s vao %s" % (zip_name, gdir))
    marker = os.path.join(gdir, ".pack-" + re.sub(r"[^0-9A-Za-z._-]", "_", zip_name))
    if os.path.isfile(marker):
        return
    say("Dang tai %s ..." % zip_name)
    tmp = os.path.join(ROOT, zip_name)
    download(REPO + "/releases/latest/download/" + zip_name, tmp)
    mods = os.path.join(gdir, "mods")
    os.makedirs(mods, exist_ok=True)
    for f in os.listdir(mods):
        if f.endswith(".jar") and (f.startswith(("Sodium_", "VulkanMod_",
                                                 "mc-client-standalone-"))):
            try:
                os.remove(os.path.join(mods, f))
            except OSError:
                pass
    for d in (mods, gdir):
        for f in os.listdir(d):
            if f.startswith((".jar-v", ".defaults-v", ".pack-")):
                try:
                    os.remove(os.path.join(d, f))
                except OSError:
                    pass
    with zipfile.ZipFile(tmp) as z:
        for e in z.infolist():
            if e.is_dir():
                continue
            rel = e.filename.replace("\\", "/")
            dest = os.path.join(gdir, *rel.split("/"))
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            # mods/ do launcher quan ly -> ghi de; config/save cua nguoi choi -> giu
            if not rel.startswith("mods/") and os.path.exists(dest):
                continue
            with z.open(e) as src, open(dest, "wb") as out:
                shutil.copyfileobj(src, out)
    try:
        os.remove(tmp)
    except OSError:
        pass
    with open(marker, "w", encoding="utf-8") as f:
        f.write("")
    say("Da cai %s vao %s" % (zip_name, gdir))


# ---------------------------------------------------------------- java

def _run_out(cmd):
    try:
        p = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
        return (p.stdout or "") + (p.stderr or "")
    except Exception:  # noqa: BLE001
        return ""


def java_major(exe):
    m = re.search(r'version "(\d+)(?:\.(\d+))?', _run_out([exe, "-version"]))
    if not m:
        return 0
    major = int(m.group(1))
    return int(m.group(2)) if major == 1 and m.group(2) else major


def find_java():
    cands = []
    if os.environ.get("JAVA_HOME"):
        cands.append(os.path.join(os.environ["JAVA_HOME"], "bin", "java"))
    exe = shutil.which("java")
    if exe:
        cands.append(exe)
    for base in ("/usr/lib/jvm", os.path.join(ROOT, "jdk"), "/opt"):
        if not os.path.isdir(base):
            continue
        for name in sorted(os.listdir(base), reverse=True):
            for sub in ("bin/java", "jdk/bin/java"):
                p = os.path.join(base, name, *sub.split("/"))
                if os.path.isfile(p) and os.access(p, os.X_OK):
                    cands.append(p)
    best = None
    for c in cands:
        if not c or not os.path.isfile(c):
            continue
        major = java_major(c)
        if major == 21:
            return c
        if major >= 21 and best is None:
            best = c
    return best


def ensure_java():
    java = find_java()
    if java:
        return java
    arch = {"x86_64": "x64", "amd64": "x64", "aarch64": "aarch64",
            "arm64": "aarch64"}.get(platform.machine().lower())
    if not arch:
        raise RuntimeError("Khong ho tro kien truc %s" % platform.machine())
    say("Khong thay Java 21 - dang tai Temurin 21 (%s) ..." % arch)
    url = ("https://api.adoptium.net/v3/binary/latest/21/ga/linux/%s/jdk/hotspot/"
           "normal/eclipse" % arch)
    tgz = os.path.join(ROOT, "jdk21.tar.gz")
    download(url, tgz)
    dest = os.path.join(ROOT, "jdk")
    os.makedirs(dest, exist_ok=True)
    say("Dang giai nen JDK ...")
    with tarfile.open(tgz, "r:gz") as t:
        t.extractall(dest, filter="data") if hasattr(tarfile, "data_filter") else t.extractall(dest)
    os.remove(tgz)
    java = find_java()
    if not java:
        raise RuntimeError("Tai JDK xong nhung khong tim thay java 21")
    return java


# ---------------------------------------------------------------- minecraft

def _os_name():
    return "linux"


def rules_allow(lib):
    rules = lib.get("rules")
    if not rules:
        return True
    arch = {"x86_64": "x86", "amd64": "x86", "i386": "x86", "i686": "x86",
            "aarch64": "arm64", "arm64": "arm64"}.get(platform.machine().lower())
    allow = False
    for r in rules:
        o = r.get("os") or {}
        if o.get("name") and o["name"] != _os_name():
            continue
        a = o.get("arch")
        if a and a != arch:
            continue
        allow = r.get("action") == "allow"
    return allow


def maven_path(name):
    p = name.split(":")
    group, art, ver = p[0], p[1], p[2]
    cls = p[3] if len(p) > 3 else None
    fn = "%s-%s%s.jar" % (art, ver, "-" + cls if cls else "")
    return "/".join(group.split(".")) + "/%s/%s/%s" % (art, ver, fn)


def vanilla_json():
    cache = os.path.join(ROOT, "cache", MC + ".json")
    try:
        man = json.loads(http("https://piston-meta.mojang.com/mc/game/"
                              "version_manifest_v2.json", 30))
        entry = next((v for v in man["versions"] if v["id"] == MC), None)
        if not entry:
            raise RuntimeError("Mojang khong con version %s" % MC)
        data = json.loads(http(entry["url"], 60))
        os.makedirs(os.path.dirname(cache), exist_ok=True)
        with open(cache, "w", encoding="utf-8") as f:
            json.dump(data, f)
        return data
    except Exception:  # noqa: BLE001 - offline
        if os.path.isfile(cache):
            with open(cache, encoding="utf-8") as f:
                return json.load(f)
        raise


def fabric_json():
    """Profile json cua fabric-loader (co mainClass + danh sach libraries)."""
    cache = os.path.join(ROOT, "cache", "fabric-" + MC + ".json")
    try:
        versions = json.loads(http("https://meta.fabricmc.net/v2/versions/loader/" + MC, 30))
        have = [v["loader"]["version"] for v in versions]
        loader = FABRIC_LOADER if FABRIC_LOADER in have else have[0]
        data = json.loads(http("https://meta.fabricmc.net/v2/versions/loader/%s/%s/"
                               "profile/json" % (MC, loader), 30))
        os.makedirs(os.path.dirname(cache), exist_ok=True)
        with open(cache, "w", encoding="utf-8") as f:
            json.dump(data, f)
        return data
    except Exception:  # noqa: BLE001
        if os.path.isfile(cache):
            with open(cache, encoding="utf-8") as f:
                return json.load(f)
        raise


def mc_root(vanilla):
    """Uu tien ~/.minecraft neu da co 1.21.11, khong thi cai rieng trong ~/.valkaw."""
    home = os.path.join(os.path.expanduser("~"), ".minecraft")
    if os.path.isfile(os.path.join(home, "versions", MC, MC + ".json")):
        return home
    return os.path.join(ROOT, "minecraft")


def ensure_libs(root, vanilla, fabric):
    """Tai moi library con thieu (song song). Tra ve (classpath, jar natives-linux)."""
    libdir = os.path.join(root, "libraries")
    cp, natives_jars, jobs = [], [], []

    def add(url, rel, size=None, sha1=None):
        dest = os.path.join(libdir, *rel.split("/"))
        cp.append(dest)
        jobs.append((url, dest, size, sha1))
        return dest

    for lib in vanilla.get("libraries", []):
        if not rules_allow(lib):
            continue
        art = (lib.get("downloads") or {}).get("artifact")
        if art and art.get("path"):
            dest = add(art["url"], art["path"], art.get("size"), art.get("sha1"))
            if "natives-linux" in lib["name"]:
                natives_jars.append(dest)
        elif lib.get("name"):
            rel = maven_path(lib["name"])
            add("https://libraries.minecraft.net/" + rel, rel)
    for lib in fabric.get("libraries", []):
        name = lib.get("name")
        if not name or not rules_allow(lib):
            continue
        rel = maven_path(name)
        base = (lib.get("url") or "https://maven.fabricmc.net/").rstrip("/") + "/"
        add(base + rel, rel, lib.get("size"), lib.get("sha1"))

    todo, seen = [], set()
    for job in jobs:
        if job[1] in seen:
            continue
        seen.add(job[1])
        todo.append(job)
    if todo:
        done = [0]

        def fetch(job):
            download(*job)
            with _lock:
                done[0] += 1
                if done[0] % 20 == 0:
                    say("  library %d/%d" % (done[0], len(todo)))

        with concurrent.futures.ThreadPoolExecutor(max_workers=12) as ex:
            for _ in ex.map(fetch, todo):
                pass
    return list(dict.fromkeys(cp)), natives_jars


def ensure_assets(root, vanilla):
    idx = vanilla["assetIndex"]
    path = os.path.join(root, "assets", "indexes", idx["id"] + ".json")
    if not os.path.isfile(path):
        download(idx["url"], path, idx.get("size"), idx.get("sha1"))
    with open(path, encoding="utf-8") as f:
        objects = json.load(f)["objects"]
    todo = []
    for obj in objects.values():
        h = obj["hash"]
        dest = os.path.join(root, "assets", "objects", h[:2], h)
        if not os.path.isfile(dest) or os.path.getsize(dest) != obj["size"]:
            todo.append((h, obj["size"]))
    if not todo:
        return
    say("Dang tai %d asset (%d MB) ..." % (len(todo), sum(s for _, s in todo) // (1 << 20)))
    done = [0]

    def fetch(item):
        h, size = item
        dest = os.path.join(root, "assets", "objects", h[:2], h)
        download("https://resources.download.minecraft.net/%s/%s" % (h[:2], h), dest, size)
        with _lock:
            done[0] += 1
            if done[0] % 400 == 0:
                say("  asset %d/%d" % (done[0], len(todo)))

    with concurrent.futures.ThreadPoolExecutor(max_workers=16) as ex:
        for r in ex.map(fetch, todo):
            pass


def ensure_runtime():
    if platform.machine().lower() not in ("x86_64", "amd64"):
        say("Canh bao: Mojang khong phat hanh natives-linux cho %s - game co the khong "
            "chay duoc (can ban 1.21.11 co natives arm64)." % platform.machine())
    vanilla = vanilla_json()
    fabric = fabric_json()
    root = mc_root(vanilla)
    client_jar = os.path.join(root, "versions", MC, MC + ".jar")
    vjson = os.path.join(root, "versions", MC, MC + ".json")
    if not os.path.isfile(vjson):
        os.makedirs(os.path.dirname(vjson), exist_ok=True)
        with open(vjson, "w", encoding="utf-8") as f:
            json.dump(vanilla, f)
    if not os.path.isfile(client_jar):
        say("Dang tai client %s ..." % MC)
        download(vanilla["downloads"]["client"]["url"], client_jar,
                 vanilla["downloads"]["client"].get("size"))
    say("Kiem tra libraries ...")
    cp, natives_jars = ensure_libs(root, vanilla, fabric)
    ensure_assets(root, vanilla)
    return {"root": root, "vanilla": vanilla, "fabric": fabric,
            "client_jar": client_jar, "cp": cp, "natives_jars": natives_jars}


def extract_natives(jars, gdir):
    natives = os.path.join(gdir, "natives")
    os.makedirs(natives, exist_ok=True)
    for j in jars:
        try:
            with zipfile.ZipFile(j) as z:
                for e in z.infolist():
                    if e.filename.endswith("/"):
                        continue
                    base = os.path.basename(e.filename)
                    if not base or not (base.endswith(".so") or base.endswith(".so.1")
                                        or base.endswith(".dylib")):
                        continue
                    with z.open(e) as src, open(os.path.join(natives, base), "wb") as out:
                        shutil.copyfileobj(src, out)
        except (OSError, zipfile.BadZipFile):
            pass
    return natives


def jvm_args():
    f = os.path.join(ROOT, "jvmargs.txt")
    try:
        with open(f, encoding="utf-8") as fh:
            txt = fh.read().strip()
        if txt:
            return txt.split()
    except OSError:
        pass
    return DEFAULT_JVM.split()


# ---------------------------------------------------------------- launch

def build_run(cfg, local_jar=None, warn=print):
    gdir = game_dir(cfg)
    os.makedirs(os.path.join(gdir, "mods"), exist_ok=True)
    ensure_pack(gdir, cfg.get("variant", "Sodium"), local_jar=local_jar)
    java = ensure_java()
    rt = ensure_runtime()
    natives = extract_natives(rt["natives_jars"], gdir)

    try:
        ram = float(str(cfg.get("ram", "6")).replace(",", "."))
    except ValueError:
        ram = 6.0
    ram = max(1.0, min(64.0, ram))
    ram_mb = int(round(ram * 1024))

    cp = ":".join([rt["client_jar"]] + rt["cp"])
    cmd = [java,
           "-Djava.library.path=" + natives,
           "-Dfabric.gameJarPath=" + rt["client_jar"],
           "-Dfabric.development=false",
           "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
           "--add-modules=jdk.incubator.vector",
           "-Xms512m", "-Xmx%dM" % ram_mb]
    cmd += jvm_args()
    cmd += ["-cp", cp, rt["fabric"]["mainClass"],
            "--username", username(), "--version", MC,
            "--gameDir", gdir, "--assetsDir", os.path.join(rt["root"], "assets"),
            "--assetIndex", rt["vanilla"]["assetIndex"]["id"],
            "--uuid", "00000000000000000000000000000001",
            "--accessToken", "0", "--userType", "legacy",
            "--width", "1600", "--height", "900"]
    return cmd, gdir


def prepare(cfg, warn=print):
    """Cai dat moi thu va tra ve (cmd, gdir). Goi tu thread."""
    return build_run(cfg, warn)


def run_game(cfg, on_done=None):
    try:
        cmd, gdir = prepare(cfg)
    except Exception as e:  # noqa: BLE001
        say("LOI: %s" % e)
        if on_done:
            on_done(1)
        return 1

    logdir = os.path.join(ROOT, "log_launcher", os.path.basename(gdir.rstrip("/")))
    os.makedirs(logdir, exist_ok=True)
    stamp = time.strftime("%d-%m-%Y %H-%M-%S")
    say("=== Valkaw launcher (linux) | %s ===" % time.strftime("%d-%m-%Y %H:%M:%S"))
    say("GameDir : %s" % gdir)
    say("Java    : %s" % cmd[0])
    say("JVM     : %s" % " ".join(cmd[1:cmd.index("-cp")]))
    try:
        os.makedirs(ROOT, exist_ok=True)
        os.chdir(gdir)  # mod ghi config theo CWD
    except OSError as e:
        say("Canh bao: khong chdir duoc (%s)" % e)

    # High: uu tien cao hon qua nice (khong can quyen root; that bai thi van chay)
    if (cfg.get("priority") or "Normal") == "High" and shutil.which("nice"):
        cmd = ["nice", "-n", "-5"] + cmd

    code = 1
    with open(os.path.join(logdir, "latest.log"), "w", encoding="utf-8") as latest, \
            open(os.path.join(logdir, stamp + ".log"), "w", encoding="utf-8") as runlog:
        say("Dang chay game ...")
        try:
            p = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                 text=True, errors="replace", bufsize=1)
        except FileNotFoundError as e:
            say("LOI: khong chay duoc java (%s)" % e)
            if on_done:
                on_done(1)
            return 1
        for line in p.stdout:
            latest.write(line)
            runlog.write(line)
            latest.flush()
        p.wait()
        code = p.returncode
    say("=== Ket thuc: exit=%d | %s ===" % (code, time.strftime("%H:%M:%S")))
    if on_done:
        on_done(code)
    return code


# ---------------------------------------------------------------- gui

THEMES = {
    "dark": dict(bg="#1B1B1F", panel="#26262C", card="#34343C", hover="#40404A",
                 txt="#F5F5F7", dim="#B9B9C4", line="#3C3C45",
                 accent="#F59E0B", accent_hi="#FBBF24", ink="#FBBF24",
                 on_accent="#1A1200"),
    "light": dict(bg="#F6F6F8", panel="#ECECF1", card="#FFFFFF", hover="#E2E2E8",
                  txt="#202024", dim="#6B6B73", line="#D3D3DA",
                  accent="#F59E0B", accent_hi="#FBBF24", ink="#B45309",
                  on_accent="#2A1D00"),
}

HERE = os.path.dirname(os.path.abspath(__file__))


def _read_text(name, default=""):
    try:
        with open(os.path.join(ROOT, name), encoding="utf-8") as f:
            return f.read()
    except OSError:
        return default


def _write_text(name, text):
    try:
        os.makedirs(ROOT, exist_ok=True)
        with open(os.path.join(ROOT, name), "w", encoding="utf-8") as f:
            f.write(text)
    except OSError:
        pass


def load_users():
    return [line.strip() for line in _read_text("usernames.txt").splitlines()
            if line.strip()]


def save_users(users):
    users = list(dict.fromkeys(u for u in users if u))
    _write_text("usernames.txt", "\n".join(users) + ("\n" if users else ""))


def run_gui(cfg):
    import queue
    import webbrowser
    import tkinter as tk
    import tkinter.font as tkfont
    from tkinter import ttk, filedialog, simpledialog, messagebox

    theme = "dark" if _read_text("theme.txt").strip().lower() == "dark" else "light"
    P = THEMES[theme]
    EXTRA = {"ok": "#3FB950", "err": "#F85149"}
    # Thanh cuộn/timestamp có thumb accent tương phản rõ ở cả 2 theme
    win = tk.Tk()
    gtk = ttk.Style(win)
    gtk.configure("Val.TScrollbar", thumbcolor=P["accent"], troughcolor=P["bg"],
                  background=P["bg"], bordercolor=P["bg"], darkcolor=P["accent"],
                  lightcolor=P["accent_hi"], arrowcolor=P["dim"])
    fam = tkfont.nametofont("TkDefaultFont").actual("family")
    F = {"base": (fam, 10), "small": (fam, 9), "bold": (fam, 10, "bold"),
         "title": (fam, 16, "bold"), "play": (fam, 12, "bold")}

    win.title("Valkaw Launcher")
    win.geometry("990x560")
    win.minsize(930, 540)
    win.configure(bg=P["bg"])

    box = queue.Queue()
    w = {}


    def show_version(on):
        """Offline: an o Client/Version (variant do file jar quyet dinh), hien lai sau khi cai."""
        for k in ("ver_label", "ver_om"):
            wd = w.get(k)
            if wd is None:
                continue
            try:
                wd.grid() if on else wd.grid_remove()
            except tk.TclError:
                pass


    def photo(name, b64):
        try:
            return tk.PhotoImage(data=b64)
        except Exception:  # noqa: BLE001 - Tk cu: thu file canh script
            try:
                return tk.PhotoImage(file=os.path.join(HERE, name))
            except Exception:
                return None

    icon, logo = photo("valkaw.png", ICON_B64), photo("logo.png", LOGO_B64)
    if icon:
        win.iconphoto(True, icon)

    var = {k: tk.StringVar(value=str(v)) for k, v in {
        "variant": cfg.get("variant", "Sodium"), "ram": cfg.get("ram", "6"),
        "user": username(), "gdir": cfg.get("gamedir", ""),
        "prio": cfg.get("priority", "Normal"), "after": cfg.get("after", "Hide"),
        "source": "Online",
    }.items()}

    def pick_local_jar_click():
        path, newdir = pick_local_jar(var["variant"].get(), state["offline_lastdir"])
        if path:
            state["offline_jar"] = path
            state["offline_lastdir"] = newdir
            cfg["offline.lastdir"] = newdir
            save_settings(cfg)
            var["source"].set("Offline")
            state["source_mode"] = "Offline"
            say("Da chon pack local: %s" % os.path.basename(path))
        else:
            var["source"].set("Online")
            state["source_mode"] = "Online"
            state["offline_jar"] = None

    def on_source_change(*_):
        is_offline = var["source"].get() == "Offline"
        show_version(not is_offline)
        if "jar_btn" in w:
            try:
                w["jar_btn"].grid() if is_offline else w["jar_btn"].grid_remove()
            except tk.TclError:
                pass
        if is_offline and not state["offline_jar"]:
            pick_local_jar_click()

    var["source"].trace_add("write", on_source_change)
    state = {"page": 0, "status": "Ready."}
    state['source_mode'] = 'Online'
    state['offline_jar'] = None
    state['offline_lastdir'] = cfg.get('offline.lastdir', '') or os.path.join(os.path.expanduser('~'), 'Downloads')

    def save(*_):
        c = dict(cfg)
        c.update({"variant": var["variant"].get(), "ram": var["ram"].get().strip() or "6",
                  "priority": var["prio"].get(), "after": var["after"].get(),
                  "gamedir": var["gdir"].get().strip()})
        save_settings(c)
        _write_text("username.txt", (var["user"].get().strip() or "Player") + "\n")
        if "jvm" in w:
            _write_text("jvmargs.txt", w["jvm"].get("1.0", "end").strip() + "\n")
        return c

    for k in ("variant", "ram", "user", "gdir", "prio", "after"):
        var[k].trace_add("write", save)

    # ---- widgets -------------------------------------------------
    def flat(parent, text, bg, fg, hover, cmd, font=None, **kw):
        return tk.Button(parent, text=text, bg=bg, fg=fg, activebackground=hover,
                         activeforeground=fg, relief="flat", bd=0, highlightthickness=0,
                         font=font or F["bold"], command=cmd, cursor="hand2", **kw)

    def entry(parent, textvar, width=None):
        return tk.Entry(parent, textvariable=textvar, bg=P["card"], fg=P["txt"],
                        insertbackground=P["txt"], relief="flat", highlightthickness=1,
                        highlightbackground=P["line"], highlightcolor=P["accent"],
                        font=F["base"], width=width)

    def label(parent, text, dim=False, font=None, bg=None):
        return tk.Label(parent, text=text, bg=bg or P["bg"],
                        fg=P["dim"] if dim else P["txt"], font=font or F["base"],
                        anchor="w", justify="left")

    def open_path(path):
        os.makedirs(path, exist_ok=True)
        for opener in (["xdg-open"], ["gio", "open"]):
            try:
                subprocess.Popen(opener + [path], stdout=subprocess.DEVNULL,
                                 stderr=subprocess.DEVNULL)
                return
            except OSError:
                continue

    def _lum(c):
        r, g, b = int(c[1:3], 16) / 255, int(c[3:5], 16) / 255, int(c[5:7], 16) / 255
        def f(x):
            return x / 12.92 if x <= 0.03928 else ((x + 0.055) / 1.055) ** 2.4
        return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b)

    def _contrast(a, b):
        la, lb = _lum(a), _lum(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)

    def show_page(idx):
        state["page"] = idx
        for i, page in enumerate(w["pages"]):
            if i == idx:
                page.tkraise()
        for i, b in enumerate(w["nav"]):
            active = i == idx
            b.config(bg=P["hover"] if active else P["panel"],
                     fg=P["accent"] if active else P["txt"])
            if "nav_inds" in w:
                w["nav_inds"][i].config(bg=P["accent"] if active else P["panel"])

    def on_scale(value):
        try:
            var["ram"].set("%g" % (float(value) / 4.0))
        except ValueError:
            pass

    def add_user():
        u = simpledialog.askstring("Valkaw", "Username moi:", parent=win)
        if u and u.strip():
            users = load_users() + [u.strip()]
            save_users(users)
            var["user"].set(u.strip())

    def pick_user():
        users = load_users()
        top = tk.Toplevel(win)
        top.title("Users")
        top.configure(bg=P["bg"], padx=14, pady=12)
        top.transient(win)
        lb = tk.Listbox(top, bg=P["card"], fg=P["txt"], selectbackground=P["accent"],
                        selectforeground=P["on_accent"], relief="flat", height=8,
                        activestyle="none", highlightthickness=0, font=F["base"])
        for u in users:
            lb.insert("end", u)
        lb.pack(fill="both", expand=True)

        def use():
            sel = lb.curselection()
            if sel:
                var["user"].set(lb.get(sel[0]))
                save_users([lb.get(sel[0])] + [u for u in users if u != lb.get(sel[0])])
                top.destroy()

        def remove():
            sel = lb.curselection()
            if sel:
                users.pop(sel[0])
                lb.delete(sel[0])
                save_users(users)

        bar = tk.Frame(top, bg=P["bg"])
        bar.pack(fill="x", pady=(10, 0))
        flat(bar, "Dung", P["accent"], P["on_accent"], P["accent_hi"], use).pack(side="left")
        flat(bar, "Xoa", P["card"], P["txt"], P["hover"], remove).pack(side="left", padx=8)
        flat(bar, "Dong", P["card"], P["txt"], P["hover"], top.destroy).pack(side="right")

    # ---- build ---------------------------------------------------
    def build_changelog(page):
        """Moi version 1 khoi thu gon duoc, lay tu CHANGELOG (ca launcher + client)."""
        tk.Label(page, text="What's new", bg=P["bg"], fg=P["txt"],
                 font=F["title"], anchor="w").pack(anchor="w", pady=(0, 12))
        canvas = tk.Canvas(page, bg=P["bg"], highlightthickness=0, bd=0)
        sb = ttk.Scrollbar(page, command=canvas.yview, style="Val.TScrollbar")
        canvas.configure(yscrollcommand=sb.set)
        sb.pack(side="right", fill="y")
        canvas.pack(side="left", fill="both", expand=True)
        inner = tk.Frame(canvas, bg=P["bg"])
        canvas.create_window((0, 0), window=inner, anchor="nw", tags="inner")
        wrap = []

        def toggle(body, arrow):
            if body.winfo_manager():
                body.pack_forget()
                arrow.config(text="\u25b8")
            else:
                body.pack(fill="x", pady=(0, 10), after=body.head)
                arrow.config(text="\u25be")

        for i, (title, groups) in enumerate(changelog_blocks()):
            head = tk.Frame(inner, bg=P["panel"], cursor="hand2")
            head.pack(fill="x", pady=(0 if i == 0 else 8, 0))
            arrow = tk.Label(head, text="\u25b8", bg=P["panel"], fg=P["accent"],
                             font=F["base"], cursor="hand2")
            arrow.pack(side="left", padx=(10, 6), pady=7)
            cap = tk.Label(head, text=title, bg=P["panel"], fg=P["txt"], font=F["bold"],
                           anchor="w", cursor="hand2")
            cap.pack(side="left", fill="x", expand=True, pady=7)

            body = tk.Frame(inner, bg=P["panel"], padx=12, pady=2)
            body.head = head
            for name, items in groups:
                if name:
                    tk.Label(body, text=name, bg=P["panel"], fg=P["accent"],
                             font=F["bold"], anchor="w").pack(fill="x", pady=(10, 4))
                for it in items:
                    lbl = tk.Label(body, text="\u2022  " + it, bg=P["panel"], fg=P["dim"],
                                   font=F["small"], anchor="w", justify="left")
                    lbl.pack(fill="x", pady=1)
                    wrap.append(lbl)

            def on_click(_e, body=body, arrow=arrow):
                toggle(body, arrow)

            for wdg in (head, cap, arrow):
                wdg.bind("<Button-1>", on_click)
            if i == 0:  # ban moi nhat mo san
                body.pack(fill="x", pady=(0, 10))
                arrow.config(text="\u25be")

        def resize(_e=None):
            width = canvas.winfo_width()
            if width > 10:
                canvas.itemconfig("inner", width=width)
                for lbl in wrap:
                    lbl.configure(wraplength=max(160, width - 70))

        inner.bind("<Configure>",
                   lambda _e: canvas.configure(scrollregion=canvas.bbox("all")))
        canvas.bind("<Configure>", resize)
        canvas.after(60, resize)

        def on_wheel(e):
            step = -1 if (getattr(e, "num", 0) == 4 or getattr(e, "delta", 0) > 0) else 1
            canvas.yview_scroll(step, "units")

        canvas.bind("<Enter>", lambda _e: [canvas.bind_all("<MouseWheel>", on_wheel),
                                           canvas.bind_all("<Button-4>", on_wheel),
                                           canvas.bind_all("<Button-5>", on_wheel)])
        canvas.bind("<Leave>", lambda _e: [canvas.unbind_all("<MouseWheel>"),
                                           canvas.unbind_all("<Button-4>"),
                                           canvas.unbind_all("<Button-5>")])

    def build():
        for child in win.winfo_children():
            child.destroy()
        w.clear()

        style = ttk.Style(win)
        try:
            style.theme_use("clam")  # de to duoc mau cho progressbar
        except tk.TclError:
            pass
        style.configure("V.Horizontal.TProgressbar", troughcolor=P["line"],
                        background=P["accent"], bordercolor=P["dim"],
                        lightcolor=P["accent_hi"], darkcolor=P["accent"], thickness=6)

        head = tk.Frame(win, bg=P["bg"], height=74)
        head.pack(fill="x")
        head.pack_propagate(False)
        if logo:
            tk.Label(head, image=logo, bg=P["bg"]).place(x=14, y=8, width=58, height=58)
        tk.Label(head, text="VALKAW", bg=P["bg"], fg=P["accent"],
                 font=F["title"]).place(x=86, y=10)
        subtitle_fg = P["txt"] if _contrast(P["dim"], P["bg"]) < 4.5 else P["dim"]
        tk.Label(head, text="Minecraft %s  \u00b7  Sodium / VulkanMod" % MC,
                 bg=P["bg"], fg=subtitle_fg, font=F["small"]).place(x=88, y=40)
        tk.Frame(win, bg=P["accent"], height=2).pack(fill="x")

        body = tk.Frame(win, bg=P["bg"])
        body.pack(fill="both", expand=True)
        side = tk.Frame(body, bg=P["panel"], width=136)
        side.pack(side="left", fill="y")
        side.pack_propagate(False)
        content = tk.Frame(body, bg=P["bg"])
        content.pack(side="left", fill="both", expand=True)
        content.grid_rowconfigure(0, weight=1)
        content.grid_columnconfigure(0, weight=1)

        w["pages"] = []
        for _ in range(5):
            page = tk.Frame(content, bg=P["bg"], padx=24, pady=20)
            page.grid(row=0, column=0, sticky="nsew")
            w["pages"].append(page)
        p_cl, p_ram, p_jvm, p_opt, p_dir = w["pages"]

        # Changelog (mo tab nay dau tien)
        build_changelog(p_cl)

        # RAM
        label(p_ram, "RAM allocation", font=F["bold"]).pack(anchor="w")
        label(p_ram, "How much memory to give Minecraft.", dim=True,
              font=F["small"]).pack(anchor="w", pady=(2, 12))
        tk.Scale(p_ram, from_=4, to=64, resolution=1, orient="horizontal",
                 showvalue=False, bg=P["bg"], fg=P["txt"], troughcolor=P["card"],
                 highlightthickness=0, bd=0, sliderrelief="flat", width=10,
                 activebackground=P["accent"], command=on_scale).pack(
                     fill="x", pady=(0, 10))
        row = tk.Frame(p_ram, bg=P["bg"])
        row.pack(anchor="w")
        entry(row, var["ram"], 8).pack(side="left", ipady=4)
        label(row, "GB").pack(side="left", padx=8)

        # JVM args
        label(p_jvm, "JVM arguments", font=F["bold"]).pack(anchor="w")
        label(p_jvm, "Extra flags passed to Java. Use spaces between flags.",
              dim=True, font=F["small"]).pack(anchor="w", pady=(2, 12))
        wrap = tk.Frame(p_jvm, bg=P["bg"])
        wrap.pack(fill="both", expand=True, pady=(0, 0))
        w["jvm"] = tk.Text(wrap, bg=P["card"], fg=P["txt"], insertbackground=P["txt"],
                           relief="flat", highlightthickness=1, highlightbackground=P["line"],
                           highlightcolor=P["accent"], wrap="word", height=8,
                           font=(fam, 9))
        w["jvm"].insert("1.0", _read_text("jvmargs.txt").strip())
        sb = tk.Scrollbar(wrap, command=w["jvm"].yview, bg=P["line"],
                          troughcolor=P["bg"], relief="flat", bd=0)
        w["jvm"].config(yscrollcommand=sb.set)
        w["jvm"].pack(side="left", fill="both", expand=True)
        sb.pack(side="right", fill="y")
        flat(p_jvm, "Ve mac dinh (G1)", P["card"], P["txt"], P["hover"],
             lambda: (w["jvm"].delete("1.0", "end"),
                      w["jvm"].insert("1.0", DEFAULT_JVM), save())).pack(anchor="w",
                                                                       pady=(12, 0))

        # Options
        label(p_opt, "Launch options", font=F["bold"]).pack(anchor="w")
        label(p_opt, "Process priority and launcher behavior after starting.",
              dim=True, font=F["small"]).pack(anchor="w", pady=(2, 12))
        for title, key, items in (("Priority", "prio", ["Normal", "High"]),
                                  ("After run", "after", ["Hide", "Keep", "Close"])):
            label(p_opt, title, dim=True, font=F["small"]).pack(anchor="w")
            box_row = tk.Frame(p_opt, bg=P["panel"], padx=10, pady=6)
            box_row.pack(fill="x", pady=(2, 12))
            for it in items:
                tk.Radiobutton(box_row, text=it, value=it, variable=var[key],
                               bg=P["panel"], fg=P["txt"], selectcolor=P["card"],
                               activebackground=P["panel"], activeforeground=P["txt"],
                               highlightthickness=0, bd=0, font=F["base"],
                               anchor="w").pack(side="left", padx=(0, 18))
        label(p_opt, "High = nice -5 (khong can root); Realtime khong ho tro tren Linux.",
              dim=True, font=F["small"]).pack(anchor="w")
        label(p_opt, "Hide = an launcher khi dang choi.", dim=True,
              font=F["small"]).pack(anchor="w")

        # Game dir
        label(p_dir, "Game directory", font=F["bold"]).pack(anchor="w")
        label(p_dir, "Where the game instance and logs live.", dim=True,
              font=F["small"]).pack(anchor="w", pady=(2, 12))
        row = tk.Frame(p_dir, bg=P["bg"])
        row.pack(fill="x", pady=(0, 8))
        entry(row, var["gdir"]).pack(side="left", fill="x", expand=True, ipady=4)
        flat(row, "Chon...", P["card"], P["txt"], P["hover"],
             lambda: var["gdir"].set(filedialog.askdirectory() or var["gdir"].get())
             ).pack(side="left", padx=8)
        row2 = tk.Frame(p_dir, bg=P["bg"])
        row2.pack(anchor="w")
        flat(row2, "Mo ~/.valkaw", P["card"], P["txt"], P["hover"],
             lambda: open_path(ROOT)).pack(side="left")
        flat(row2, "Mo log", P["card"], P["txt"], P["hover"],
             lambda: open_path(os.path.join(ROOT, "log_launcher"))).pack(side="left", padx=8)
        label(p_dir, "Trong = ~/.valkaw/game_sodium (Vulkan: game_vulkan)", dim=True,
              font=F["small"]).pack(anchor="w", pady=(10, 0))

        # sidebar nav with accent active indicator
        w["nav"] = []
        w["nav_inds"] = []
        names = ["Changelog", "RAM", "JVM Args", "Options", "Game Dir"]
        for i, name in enumerate(names):
            fr = tk.Frame(side, bg=P["panel"])
            fr.pack(fill="x", padx=8, pady=(8 if i == 0 else 2, 0))
            ind = tk.Frame(fr, bg=P["panel"], width=3)
            ind.pack(side="left", fill="y")
            ind.pack_propagate(False)
            b = flat(fr, name, P["panel"], P["txt"], P["hover"],
                     lambda i=i: show_page(i), font=F["base"], anchor="w", padx=12)
            b.pack(side="left", fill="x", expand=True, ipady=6)
            w["nav"].append(b)
            w["nav_inds"].append(ind)

        # footer
        tk.Frame(win, bg=P["line"], height=1).pack(fill="x")
        foot = tk.Frame(win, bg=P["panel"])
        foot.pack(fill="x")

        foot_main = tk.Frame(foot, bg=P["panel"], padx=16, pady=12)
        foot_main.pack(fill="x")
        foot_main.grid_columnconfigure(4, weight=1)

        # 0: Account
        acct_g = tk.Frame(foot_main, bg=P["panel"])
        acct_g.grid(row=0, column=0, rowspan=2, sticky="nw", padx=(0, 24))
        label(acct_g, "Account", dim=True, font=F["small"],
              bg=P["panel"]).grid(row=0, column=0, sticky="w")
        urow = tk.Frame(acct_g, bg=P["panel"])
        urow.grid(row=1, column=0, sticky="w", pady=(4, 0))
        entry(urow, var["user"], 16).pack(side="left", ipady=4)
        flat(urow, "+", P["card"], P["txt"], P["hover"], add_user,
             width=2, height=1).pack(side="left", padx=(6, 0))
        flat(urow, "\u25BC", P["card"], P["txt"], P["hover"], pick_user,
             width=2, height=1).pack(side="left", padx=(4, 0))

        # 1: Source
        src_g = tk.Frame(foot_main, bg=P["panel"])
        src_g.grid(row=0, column=1, rowspan=2, sticky="nw", padx=(0, 24))
        label(src_g, "Source", dim=True, font=F["small"],
              bg=P["panel"]).grid(row=0, column=0, sticky="w")
        src_om = tk.OptionMenu(src_g, var["source"], "Online", "Offline")
        src_om.config(bg=P["card"], fg=P["txt"], activebackground=P["hover"],
                      activeforeground=P["txt"], relief="flat", bd=0,
                      highlightthickness=0, font=F["base"], width=9, anchor="w")
        src_om["menu"].config(bg=P["card"], fg=P["txt"], activebackground=P["hover"],
                              activeforeground=P["txt"], font=F["base"])
        src_om.grid(row=1, column=0, sticky="w", pady=(4, 0))

        # 2: Client/Version
        ver_g = tk.Frame(foot_main, bg=P["panel"])
        ver_g.grid(row=0, column=2, rowspan=2, sticky="nw", padx=(0, 24))
        w["ver_label"] = label(ver_g, "Client", dim=True, font=F["small"],
                               bg=P["panel"])
        w["ver_label"].grid(row=0, column=0, sticky="w")
        om = tk.OptionMenu(ver_g, var["variant"], *VARIANTS)
        om.config(bg=P["card"], fg=P["txt"], activebackground=P["hover"],
                  activeforeground=P["txt"], relief="flat", bd=0,
                  highlightthickness=0, font=F["base"], width=9, anchor="w")
        om["menu"].config(bg=P["card"], fg=P["txt"], activebackground=P["hover"],
                          activeforeground=P["txt"], font=F["base"])
        om.grid(row=1, column=0, sticky="w", pady=(4, 0))
        w["ver_om"] = om

        # 3: Offline JAR picker (aligned with controls, not label row)
        w["jar_btn"] = flat(foot_main, "Chon JAR", P["card"], P["txt"], P["hover"],
                            lambda: pick_local_jar_click(), width=9, height=1)
        w["jar_btn"].grid(row=1, column=3, sticky="w", padx=(0, 24))

        # 5: status + progress directly above Play
        w["status"] = tk.Label(foot_main, text=state["status"], bg=P["panel"],
                               fg=P["dim"], font=F["small"], anchor="w")
        w["status"].grid(row=0, column=5, sticky="sw", padx=(0, 16))
        w["bar"] = ttk.Progressbar(foot_main, mode="indeterminate", length=160,
                                   style="V.Horizontal.TProgressbar")
        w["bar"].grid(row=1, column=5, sticky="ew", padx=(0, 16), pady=(6, 0))

        # 6: Play (dominant, right)
        w["play"] = flat(foot_main, "Play", P["accent"], P["on_accent"],
                         P["accent_hi"], start, font=F["play"], width=14)
        w["play"].grid(row=0, column=6, rowspan=2, sticky="nse", ipady=9)

        # Restore status colour hint after rebuild
        txt = state["status"]
        if txt == "Ready.":
            w["status"].config(fg=EXTRA["ok"])
        elif "loi" in txt.lower():
            w["status"].config(fg=EXTRA["err"])

        is_offline = state.get("source_mode") == "Offline"
        show_version(not is_offline)
        if "jar_btn" in w:
            try:
                w["jar_btn"].grid() if is_offline else w["jar_btn"].grid_remove()
            except tk.TclError:
                pass

        # tertiary bar: theme bottom-left, GitHub bottom-right
        tert = tk.Frame(foot, bg=P["panel"], padx=16, pady=10)
        tert.pack(fill="x")
        flat(tert, "\u2600 Light" if theme == "dark" else "\u263D Dark",
             P["card"], P["txt"], P["hover"], toggle_theme,
             font=F["small"]).pack(side="left")
        link = tk.Label(tert, text="GitHub Releases \u2014 tai / cap nhat thu cong",
                        bg=P["panel"], fg=P["ink"], font=F["small"], cursor="hand2")
        link.pack(side="right")
        link.bind("<Button-1>", lambda _e: webbrowser.open(REPO + "/releases/latest"))

        show_page(min(state["page"], 4))
        # dat lai scale theo ram hien tai
        try:
            quarters = max(4, min(64, int(round(float(var["ram"].get().replace(",", ".")) * 4))))
            for child in p_ram.winfo_children():
                if isinstance(child, tk.Scale):
                    child.set(quarters)
        except ValueError:
            pass

    def toggle_theme():
        nonlocal theme, P
        theme = "light" if theme == "dark" else "dark"
        P = THEMES[theme]
        _write_text("theme.txt", theme)
        build()

    def on_done(code):
        box.put(("done", code))

    def ask_update(name, v):
        if not win.winfo_exists():
            return
        ans = messagebox.askyesnocancel(
            "Valkaw Launcher",
            "Da co Valkaw Launcher %s (ban dang dung %s). Cap nhat ngay?"
            % (ver_str(v), APP_VERSION), parent=win)
        if ans is True:
            threading.Thread(target=do_update, args=(name, v), daemon=True).start()
        elif ans is False:
            cfg["update.skip"] = ver_str(v)
            save_settings(cfg)

    def do_update(name, v):
        try:
            box.put(("say", "Dang tai ban %s ..." % ver_str(v)))
            download_self(name)
            os.makedirs(UPDATE_DIR, exist_ok=True)
            with open(os.path.join(UPDATE_DIR, "pending.ver"), "w", encoding="utf-8") as f:
                f.write(ver_str(v))
            # File dang chay da nam trong RAM nen thay ngay duoc; mo lai la dung ban moi.
            if apply_pending():
                box.put(("say", "Da cap nhat len ban %s - mo lai launcher de dung ban moi."
                                % ver_str(v)))
            else:
                box.put(("say", "Khong thay duoc file - se thu lai o lan mo sau."))
        except Exception as e:  # noqa: BLE001 - loi mang/zip: hien status, khong crash
            box.put(("say", "Cap nhat loi: %s" % e))

    def check_update():
        name, v = check_self_update(cfg)
        if name:
            box.put(("update", (name, v)))

    def start():
        c = save()
        if not var["user"].get().strip():
            messagebox.showinfo("Valkaw Launcher", "Nhap Username truoc khi choi.", parent=win)
            return
        local_jar = state.get("offline_jar") if state.get("source_mode") == "Offline" else None
        if state.get("source_mode") == "Offline" and not local_jar:
            pick_local_jar_click()
            local_jar = state.get("offline_jar")
            if not local_jar:
                var["source"].set("Online")
                state["source_mode"] = "Online"
        w["play"].config(state="disabled")
        w["bar"].start(12)
        if local_jar:
            show_version(True)   # da co pack local -> hien lai o Client de biet variant dang cai
        state["status"] = "Dang chuan bi ..."
        if var["after"].get() == "Hide":
            win.iconify()
        threading.Thread(target=run_game, args=(c, on_done, local_jar), daemon=True).start()

    def pump():
        try:
            while True:
                kind, val = box.get_nowait()
                if kind == "say":
                    state["status"] = val
                    if "status" in w:
                        w["status"].config(text=val)
                elif kind == "update":
                    ask_update(*val)
                elif kind == "done":
                    w["bar"].stop()
                    w["play"].config(state="normal")
                    if var["after"].get() == "Close":
                        close()
                        return
                    if var["after"].get() == "Hide":
                        win.deiconify()
                    if val == 0:
                        state["status"] = "Ready."
                        status_fg = EXTRA["ok"]
                    else:
                        state["status"] = "Game thoat voi loi (xem ~/.valkaw/log_launcher)"
                        status_fg = EXTRA["err"]
                    if "status" in w:
                        w["status"].config(text=state["status"], fg=status_fg)
        except queue.Empty:
            pass
        try:
            if win.winfo_exists():
                state["after_id"] = win.after(200, pump)
        except tk.TclError:  # cua so da dong
            pass

    def close():
        save()
        try:
            if state.get("after_id"):
                win.after_cancel(state["after_id"])
        except tk.TclError:
            pass
        win.destroy()

    global _status_cb
    _status_cb = lambda s: box.put(("say", s))

    win.protocol("WM_DELETE_WINDOW", close)
    build()
    state["after_id"] = win.after(200, pump)
    if not update_disabled(cfg):
        threading.Thread(target=check_update, daemon=True).start()
    win.mainloop()
    return 0


# ---------------------------------------------------------------- install

def install_desktop():
    """Tao menu entry + icon; copy chinh file nay vao ~/.local/share/valkaw."""
    base = os.path.join(os.path.expanduser("~"), ".local", "share")
    data = os.path.join(base, "valkaw")
    apps = os.path.join(base, "applications")
    os.makedirs(data, exist_ok=True)
    os.makedirs(apps, exist_ok=True)
    me = os.path.abspath(__file__)
    dst = os.path.join(data, "valkaw.py")
    if os.path.abspath(dst) != me:
        shutil.copyfile(me, dst)
    icon = os.path.join(data, "valkaw.png")
    with open(icon, "wb") as f:
        f.write(base64.b64decode(ICON_B64))
    entry = os.path.join(apps, "valkaw.desktop")
    with open(entry, "w", encoding="utf-8") as f:
        f.write("[Desktop Entry]\nType=Application\nName=Valkaw Launcher\n"
                "Comment=Minecraft %s - Sodium / VulkanMod\n"
                "Exec=%s %s\nIcon=%s\nTerminal=false\nCategories=Game;\n"
                % (MC, sys.executable or "python3", dst, icon))
    os.chmod(entry, 0o755)
    say("Menu entry: %s" % entry)
    say("Launcher:   %s" % dst)
    return 0


def self_test():
    """Kiem tra logic update (khong mang, khong GUI): python3 valkaw.py --self-test"""
    import tempfile
    ok = [True]

    def chk(cond, msg):
        if not cond:
            ok[0] = False
        print("%s %s" % ("PASS" if cond else "FAIL", msg))

    chk(parse_ver("Valkaw.Launcher-linux-1.10.py") == (1, 10), "parse_ver 1.10")
    chk(ver_newer((1, 10), (1, 9)), "1.10 > 1.9")
    chk(ver_newer((1, 10), (1, 9, 3)), "1.10 > 1.9.3 (khac do dai)")
    chk(not ver_newer((1, 4), (1, 4)), "1.4 == 1.4")
    chk(not ver_newer((1, 3), (1, 4)), "1.3 < 1.4")
    chk(pick_self(["Sodium_v1.3.zip", "Vulkan_v1.3.zip", "Valkaw.Launcher-1.10.exe",
                   "Valkaw.Launcher-linux-1.9.py", "Valkaw.Launcher-linux-1.10.py"])
        == ("Valkaw.Launcher-linux-1.10.py", (1, 10)), "pick_self lay ban cao nhat")
    chk(pick_self(["Sodium_v1.3.zip", "Valkaw.Launcher-9.9.exe"]) == (None, None),
        "pick_self bo qua asset cua he khac")

    blocks = changelog_blocks()
    chk(len(blocks) >= 5, "changelog co >= 5 khoi")
    chk(blocks[0][0].startswith("Valkaw " + APP_VERSION + " - "), "changelog: version tu APP_VERSION")
    chk("client v" + CLIENT_VERSION in blocks[0][0], "changelog: client version tu CLIENT_VERSION")
    chk(blocks[0][1][0][0] == "Launcher", "changelog: nhom Launcher truoc")
    chk("{" not in blocks[0][0], "changelog: khong con placeholder")

    tmp = tempfile.mkdtemp(prefix="valkaw-selftest-")
    try:
        udir = os.path.join(tmp, "update")
        inst = os.path.join(tmp, "install")
        os.makedirs(udir)
        os.makedirs(inst)
        with open(os.path.join(udir, "pending.py"), "w", encoding="utf-8") as f:
            f.write('APP_VERSION = "1.9"\nprint("new")\n')
        chk(apply_pending(udir, inst), "apply_pending cai duoc")
        chk(os.path.isfile(os.path.join(inst, "valkaw.py")), "apply_pending ghi valkaw.py")
        chk(not os.path.isdir(udir), "apply_pending don thu muc update")

        # File hong (sai cu phap) phai bi tu choi, ban dang chay giu nguyen
        os.makedirs(udir)
        with open(os.path.join(udir, "pending.py"), "w", encoding="utf-8") as f:
            f.write("APP_VERSION = \n def (:\n")
        before = open(os.path.join(inst, "valkaw.py"), encoding="utf-8").read()
        chk(not apply_pending(udir, inst), "apply_pending tu choi file hong")
        chk(open(os.path.join(inst, "valkaw.py"), encoding="utf-8").read() == before,
            "apply_pending khong ghi de khi file hong")
    finally:
        shutil.rmtree(tmp, ignore_errors=True)

    print("OK" if ok[0] else "FAILED")
    return 0 if ok[0] else 1


def main(argv):
    cfg = load_settings()
    if "--self-test" in argv:
        return self_test()
    if "--install" in argv:
        install_desktop()
        return 0
    if "--prefetch" in argv:
        ensure_pack(game_dir(cfg), cfg["variant"])
        r = ensure_runtime()
        print("Runtime: %s" % r["root"])
        return 0
    apply_startup_update()
    if "--cli" in argv:
        name, v = check_self_update(cfg)
        if name:
            say("Co ban launcher moi %s - tai tay tai %s/releases/latest"
                % (ver_str(v), REPO))
        return run_game(cfg)
    try:
        return run_gui(cfg)
    except Exception as e:  # noqa: BLE001 - thieu tkinter/khong co DISPLAY -> CLI
        say("GUI khong dung duoc (%s) - chay CLI." % e)
        return run_game(cfg)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
