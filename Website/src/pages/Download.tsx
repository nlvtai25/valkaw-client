import { asset } from '@/lib/asset';
import { Link } from 'react-router-dom';
import { BuildBench } from '@/components/BuildBench';
import { Seo } from '@/components/Seo';
import { BRAND, DOWNLOADS, INSTALL_STEPS, REQUIREMENTS } from '@/data/content';
import { PLATFORM_LABEL, detectPlatform, preferredBuild, useLauncherBuilds } from '@/lib/launcher';
import { revealDelay, useScrollReveal } from '@/lib/reveal';
import type { ChangelogEntry } from '@/types/content';

const TAG: Record<ChangelogEntry['type'], string> = {
  added: 'Added',
  improved: 'Improved',
  fixed: 'Fixed',
};

const DOWNLOAD_JSON_LD = {
  '@context': 'https://schema.org',
  '@type': 'SoftwareApplication',
  name: BRAND.name,
  applicationCategory: 'GameApplication',
  operatingSystem: 'Linux, Windows, macOS',
  offers: { '@type': 'Offer', price: '0', priceCurrency: 'USD' },
};

export default function Download() {
  useScrollReveal();
  const launcher = useLauncherBuilds();
  const platform = detectPlatform();
  const primary = preferredBuild(launcher.builds, platform);
  const others = launcher.builds.filter(build => build !== primary);
  const sizes = launcher.builds.map(build => `${PLATFORM_LABEL[build.os]} (${build.sizeMB} MB)`);
  const sizeLine =
    sizes.length > 2
      ? `${sizes.slice(0, -1).join(', ')} and ${sizes[sizes.length - 1]}`
      : sizes.join(' and ');

  return (
    <>
      <Seo
        title="Download — Valkaw Client 1.0.2"
        description="Download Valkaw Client 1.0.2 for Minecraft 1.21.11: Sodium and VulkanMod jars, SHA256 hashes, system requirements and the launcher for Linux and Windows."
        jsonLd={DOWNLOAD_JSON_LD}
      />
      <main>
        <section className="section--l" style={{ paddingBottom: 0 }} data-reveal>
          <div className="container">
            <h2 className="visually-hidden">Current builds</h2>
            <div className="split">
              <div>
                <h1 style={{ maxWidth: '12ch' }}>Two jars, one version.</h1>
                <p className="lead" style={{ marginTop: 'var(--space-lg)' }}>
                  Sodium for OpenGL, VulkanMod for Vulkan. Both are 1.0.2.
                </p>
                <ul className="fact-list">
                  <li>Never put both jars in the same profile</li>
                  <li>Java 21 or newer, 4096 MB allocated by default</li>
                  <li>SHA256 published for each file</li>
                </ul>
              </div>
              <BuildBench />
            </div>
          </div>
        </section>

        <section className="section" id="install" data-reveal>
          <div className="container">
            <div className="head-block">
              <h2>Four steps, launcher or manual</h2>
              <p className="lead">
                The jar path works on any OS. The launcher runs the same steps and keeps the last jar for offline
                starts.
              </p>
            </div>
            <ol className="steps" style={{ marginTop: 'var(--space-2xl)' }}>
              {INSTALL_STEPS.map((step, i) => (
                <li key={step.title} data-reveal style={revealDelay(i)}>
                  <h3>{step.title}</h3>
                  <p>{step.description}</p>
                </li>
              ))}
            </ol>
          </div>
        </section>

        <section className="section band" id="launcher">
          <div className="container" data-reveal>
            <div className="split split--even">
              <div>
                <h2>Valkaw Launcher v{launcher.version}</h2>
                <p className="lead" style={{ marginTop: 'var(--space-md)' }}>
                  Pick a renderer, install the profile, press Play. It downloads the newest client jar and caches the
                  last working copy for offline starts.
                </p>
                <ul className="fact-list">
                  <li>{sizeLine}, no bundled client jar</li>
                  <li>Auto-update on by default, reading the published builds folder</li>
                  <li>Game directory stays at ~/.valkaw/&lt;profile&gt;</li>
                  <li>Tuned G1GC flags for lower pause times</li>
                </ul>
                <div className="actions" style={{ marginTop: 'var(--space-xl)' }}>
                  {primary && (
                    <a className="btn btn--primary" href={primary.url} target="_blank" rel="noreferrer noopener">
                      Download for {PLATFORM_LABEL[primary.os]}
                    </a>
                  )}
                  {others.map(build => (
                    <a
                      key={build.file}
                      className="btn btn--quiet"
                      href={build.url}
                      target="_blank"
                      rel="noreferrer noopener"
                    >
                      {PLATFORM_LABEL[build.os]}
                    </a>
                  ))}
                </div>
                <p style={{ marginTop: 'var(--space-sm)', fontSize: 'var(--text-sm)', color: 'var(--color-muted)' }}>
                  macOS is not published yet. Use the jar with Fabric there.
                </p>
              </div>
              <figure className="split-figure">
                <img
                  src={asset('/shots/launcher.webp')}
                  alt="Valkaw Launcher with a Sodium profile and a VulkanMod profile"
                  width={1600}
                  height={900}
                  loading="lazy"
                />
                <figcaption>Two profiles, one client directory, auto-update enabled.</figcaption>
              </figure>
            </div>
          </div>
        </section>

        <section className="section" id="requirements" data-reveal>
          <div className="container">
            <div className="head-block">
              <h2>What your PC needs</h2>
              <p className="lead">
                Valkaw adds a client layer, it does not replace Minecraft or the JVM. Render distance and memory matter
                more than the label.
              </p>
            </div>

            <div className="panels panels--three" style={{ marginTop: 'var(--space-2xl)' }}>
              {REQUIREMENTS.map((tier, i) => (
                <div className="panel" key={tier.name} data-reveal style={revealDelay(i)}>
                  <h3>
                    {tier.name} <span>{tier.badge}</span>
                  </h3>
                  <dl>
                    <div>
                      <dt>CPU</dt>
                      <dd>{tier.cpu}</dd>
                    </div>
                    <div>
                      <dt>GPU</dt>
                      <dd>{tier.gpu}</dd>
                    </div>
                    <div>
                      <dt>Memory</dt>
                      <dd>{tier.ram}</dd>
                    </div>
                    <div>
                      <dt>Java</dt>
                      <dd>{tier.java}</dd>
                    </div>
                    <div>
                      <dt>Storage</dt>
                      <dd>{tier.storage}</dd>
                    </div>
                  </dl>
                  <p className="panel-note">{tier.targetFps}</p>
                </div>
              ))}
            </div>
          </div>
        </section>

        <section className="section band" id="changelog">
          <div className="container" data-reveal>
            <div className="head-block">
              <h2>What changed in 1.0.2</h2>
              <p className="lead">Both builds share the module work. Renderer fixes are listed under their own build.</p>
            </div>

            <div className="trail" style={{ marginTop: 'var(--space-2xl)' }}>
              {DOWNLOADS.map(build => (
                <article className="trail-entry" key={build.id}>
                  <div className="trail-meta">
                    <strong>{build.id === 'sodium' ? 'Sodium' : 'VulkanMod'}</strong>
                    <span style={{ fontSize: 'var(--text-sm)', color: 'var(--color-muted)' }}>
                      v{build.version}, {build.sizeMB} MB
                    </span>
                    <time dateTime={build.releaseDate}>{build.releaseDate}</time>
                  </div>
                  <ul className="trail-list">
                    {build.changelog.map((entry, i) => (
                      <li key={i}>
                        <span className={`tag tag--${entry.type}`}>{TAG[entry.type]}</span>
                        <span>{entry.text}</span>
                      </li>
                    ))}
                  </ul>
                </article>
              ))}
            </div>

            <p style={{ marginTop: 'var(--space-lg)', fontSize: 'var(--text-sm)', color: 'var(--color-muted)' }}>
              Older tags live in the{' '}
              <a href={BRAND.releases} className="link" target="_blank" rel="noreferrer noopener">
                release list
              </a>
              .
            </p>
          </div>
        </section>

        <section className="section--s" data-reveal>
          <div className="container">
            <div className="head-block">
              <h2>Want the module list first?</h2>
              <p className="lead">
                Thirty-six modules, their defaults and their option lines, before you download anything.
              </p>
            </div>
            <div className="actions" style={{ marginTop: 'var(--space-lg)' }}>
              <Link to="/features" className="btn">
                Read the modules
              </Link>
              <Link to="/" className="btn btn--quiet">
                Back to the overview
              </Link>
            </div>
          </div>
        </section>
      </main>
    </>
  );
}
