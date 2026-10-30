import { useEffect, useState } from 'react';

/**
 * Launcher builds live in assets/downloads and nowhere else. Reading that
 * folder keeps the site in step with what the launcher's own updater sees.
 */
const REPO = 'nlvtai25/valkaw-client';
const CONTENTS_API = `https://api.github.com/repos/${REPO}/contents/assets/downloads`;
const RAW_BASE = `https://github.com/${REPO}/raw/main/assets/downloads/`;

export type Platform = 'linux' | 'windows' | 'darwin';

export interface LauncherBuild {
  version: string;
  os: Platform;
  arch: string;
  file: string;
  sizeMB: number;
  url: string;
}

export interface LauncherBuilds {
  version: string;
  builds: LauncherBuild[];
  /** False when the numbers come from the bundled fallback rather than GitHub. */
  live: boolean;
}

/** Last published release. Shown when the API is unreachable or rate limited. */
export const FALLBACK_BUILDS: LauncherBuilds = {
  version: '1.0.6',
  live: false,
  builds: [
    {
      version: '1.0.6',
      os: 'linux',
      arch: 'amd64',
      file: 'valkaw-launcher-v1.0.6-linux-amd64',
      sizeMB: 10.4,
      url: `${RAW_BASE}valkaw-launcher-v1.0.6-linux-amd64`,
    },
    {
      version: '1.0.6',
      os: 'windows',
      arch: 'amd64',
      file: 'valkaw-launcher-win-v1.0.6-windows-amd64.exe',
      sizeMB: 13.1,
      url: `${RAW_BASE}valkaw-launcher-win-v1.0.6-windows-amd64.exe`,
    },
  ],
};

export const PLATFORM_LABEL: Record<Platform, string> = {
  linux: 'Linux x64',
  windows: 'Windows x64',
  darwin: 'macOS',
};

/** Matches both `valkaw-launcher-v1.0.6-linux-amd64` and the `-win-` variant. */
const FILE_NAME = /^valkaw-launcher-(?:win-)?v?(\d+(?:\.\d+)*)-(linux|windows|darwin)-([a-z0-9]+)(?:\.exe)?$/;

interface ContentEntry {
  name: string;
  size: number;
}

function isNewer(a: string, b: string): boolean {
  const left = a.split('.').map(Number);
  const right = b.split('.').map(Number);
  for (let i = 0; i < Math.max(left.length, right.length); i++) {
    const diff = (left[i] ?? 0) - (right[i] ?? 0);
    if (diff !== 0) return diff > 0;
  }
  return false;
}

function parseListing(entries: ContentEntry[]): LauncherBuilds | null {
  const builds: LauncherBuild[] = [];
  for (const entry of entries) {
    const match = FILE_NAME.exec(entry.name);
    if (!match) continue;
    builds.push({
      version: match[1],
      os: match[2] as Platform,
      arch: match[3],
      file: entry.name,
      sizeMB: Math.round((entry.size / 1048576) * 10) / 10,
      url: RAW_BASE + entry.name,
    });
  }
  if (builds.length === 0) return null;

  const version = builds.reduce((best, build) => (isNewer(build.version, best) ? build.version : best), builds[0].version);
  const latest = builds.filter(build => build.version === version).sort((a, b) => a.os.localeCompare(b.os));
  return { version, builds: latest, live: true };
}

let pending: Promise<LauncherBuilds> | null = null;

/** One request per page load, shared by every component that asks for it. */
export function loadLauncherBuilds(): Promise<LauncherBuilds> {
  if (pending) return pending;

  pending = (async () => {
    const controller = new AbortController();
    const timer = window.setTimeout(() => controller.abort(), 8000);
    try {
      const res = await fetch(CONTENTS_API, {
        headers: { Accept: 'application/vnd.github+json' },
        signal: controller.signal,
      });
      if (!res.ok) throw new Error(`GitHub responded ${res.status}`);
      return parseListing((await res.json()) as ContentEntry[]) ?? FALLBACK_BUILDS;
    } finally {
      window.clearTimeout(timer);
    }
  })().catch(() => FALLBACK_BUILDS);

  return pending;
}

export function detectPlatform(): Platform | null {
  if (typeof navigator === 'undefined') return null;
  const ua = navigator.userAgent;
  if (/Windows/i.test(ua)) return 'windows';
  if (/Mac OS X|Macintosh/i.test(ua)) return 'darwin';
  if (/Linux|X11|CrOS/i.test(ua)) return 'linux';
  return null;
}

/** Phone and tablet browsers report Linux or macOS, but cannot run the launcher. */
export function isMobileUA(): boolean {
  if (typeof navigator === 'undefined') return false;
  return /Android|iPhone|iPad|iPod|Mobile/i.test(navigator.userAgent);
}

/** Starts on the bundled fallback so the first paint never waits on the network. */
export function useLauncherBuilds(): LauncherBuilds {
  const [state, setState] = useState<LauncherBuilds>(FALLBACK_BUILDS);

  useEffect(() => {
    let active = true;
    loadLauncherBuilds().then(next => {
      if (active) setState(next);
    });
    return () => {
      active = false;
    };
  }, []);

  return state;
}

/** The build to offer first: the visitor's OS, then Linux, then whatever exists. */
export function preferredBuild(builds: LauncherBuild[], platform: Platform | null): LauncherBuild | undefined {
  return (
    builds.find(build => build.os === platform && build.arch === 'amd64') ??
    builds.find(build => build.os === platform) ??
    builds.find(build => build.os === 'linux' && build.arch === 'amd64') ??
    builds[0]
  );
}
