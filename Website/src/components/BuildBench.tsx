import { useState } from 'react';
import { BRAND, DOWNLOADS } from '@/data/content';
import type { DownloadBuild } from '@/types/content';

const API: Record<DownloadBuild['id'], string> = {
  sodium: 'OpenGL',
  vulkan: 'Vulkan 1.2',
};

const LABEL: Record<DownloadBuild['id'], string> = {
  sodium: 'Sodium',
  vulkan: 'VulkanMod',
};

/**
 * The build bench: the page's one working control. It reads the real release
 * data from content.ts, so the numbers on screen are the numbers in the jar.
 */
export function BuildBench({ initial = 'sodium', compact = false }: { initial?: DownloadBuild['id']; compact?: boolean }) {
  const [id, setId] = useState<DownloadBuild['id']>(initial);
  const [copied, setCopied] = useState<'idle' | 'copied' | 'failed'>('idle');
  const build = DOWNLOADS.find(b => b.id === id) ?? DOWNLOADS[0];
  const url = `${BRAND.releases}/download/v${build.version}/${build.file}`;

  const copyHash = async () => {
    try {
      await navigator.clipboard.writeText(build.sha256);
      setCopied('copied');
    } catch {
      setCopied('failed');
    }
    window.setTimeout(() => setCopied('idle'), 2200);
  };

  return (
    <div className={compact ? 'bench bench--compact' : 'bench'}>
      <div className="bench-head">
        <p>Client release</p>
        <div className="bench-seg" role="group" aria-label="Renderer build">
          {DOWNLOADS.map(b => (
            <button
              key={b.id}
              type="button"
              aria-pressed={b.id === id}
              onClick={() => {
                setId(b.id);
                setCopied('idle');
              }}
            >
              {LABEL[b.id]}
            </button>
          ))}
        </div>
      </div>

      <div className="bench-body">
        <div className="bench-reading">
          <strong>{build.version}</strong>
          <span>{API[build.id]}</span>
        </div>
        <dl className="bench-rows">
          <div>
            <dt>Minecraft</dt>
            <dd>{build.mcVersions.join(' / ')}</dd>
          </div>
          <div>
            <dt>Loader</dt>
            <dd>{build.loaders.join(' / ')}</dd>
          </div>
          {!compact && (
            <div>
              <dt>Java</dt>
              <dd>21 or newer</dd>
            </div>
          )}
          <div>
            <dt>File</dt>
            <dd>{build.sizeMB} MB</dd>
          </div>
          {!compact && (
            <div>
              <dt>Released</dt>
              <dd>{build.releaseDate}</dd>
            </div>
          )}
        </dl>
      </div>

      {!compact && (
        <div className="hash-line">
          <code>{build.sha256}</code>
          <button type="button" onClick={copyHash}>
            {copied === 'copied' ? 'Copied' : copied === 'failed' ? 'Copy failed' : 'Copy hash'}
          </button>
          <span className="visually-hidden" role="status" aria-live="polite">
            {copied === 'copied' ? 'SHA256 copied to the clipboard' : copied === 'failed' ? 'Clipboard blocked, select the hash manually' : ''}
          </span>
        </div>
      )}

      <div className="bench-foot">
        {/* In the hero the launcher download is the primary action, so the jar
            button steps back instead of competing with it. */}
        <a className={compact ? 'btn' : 'btn btn--primary'} href={url} target="_blank" rel="noreferrer noopener">
          Download {LABEL[build.id]} jar
        </a>
        <p className="bench-note">
          {compact ? 'Java 21. One renderer only.' : 'Java 21. Put the jar in .minecraft/mods, one renderer only.'}
        </p>
      </div>
    </div>
  );
}

export default BuildBench;
