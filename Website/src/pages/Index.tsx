import type { CSSProperties } from 'react';
import { Link } from 'react-router-dom';
import { BuildBench } from '@/components/BuildBench';
import { ViewportDeck } from '@/components/ViewportDeck';
import { Seo } from '@/components/Seo';
import { BRAND, DOWNLOADS, FAQS, FEATURES } from '@/data/content';
import { PLATFORM_LABEL, detectPlatform, isMobileUA, preferredBuild, useLauncherBuilds } from '@/lib/launcher';
import { revealDelay, useScrollReveal } from '@/lib/reveal';
import type { FeatureItem } from '@/types/content';

/** The eight modules that show what the client is for. */
/** Six modules that show the range, with a short line each. */
const SHOWN: [string, string][] = [
  ['hud-crosshair', '15 x 15 pixel editor'],
  ['hud-pc-info', 'CPU, GPU, memory'],
  ['util-freelook', 'Look without turning'],
  ['util-zoom', 'Hold or toggle, adjustable'],
  ['util-fullbright', 'Client gamma, no shader'],
  ['perf-clear-fluids', 'Less water fog'],
];

const KIND = { hud: 'hud', performance: 'perf', utility: 'util' } as const;

const HOME_JSON_LD = {
  '@context': 'https://schema.org',
  '@type': 'SoftwareApplication',
  name: BRAND.name,
  applicationCategory: 'GameApplication',
  operatingSystem: 'Linux, Windows, macOS',
  description: BRAND.description,
  offers: { '@type': 'Offer', price: '0', priceCurrency: 'USD' },
};

function Hero() {
  const launcher = useLauncherBuilds();
  const platform = detectPlatform();
  const primary = preferredBuild(launcher.builds, platform);

  return (
    <section className="hero">
      <div className="hero-sky" aria-hidden="true" />
      <div className="container">
        <div className="hero-grid">
          <div className="hero-intro">
            <h1 style={{ '--i': 0 } as CSSProperties}>Minecraft, instrumented.</h1>
            <p className="lead" style={{ '--i': 1 } as CSSProperties}>
              FPS, ping and memory on one HUD, six render controls and 35 client-side modules for
              1.21.11 — nothing about how you move ever reaches the server.
            </p>
            <div className="actions" style={{ '--i': 2 } as CSSProperties}>
              {primary && !isMobileUA() ? (
                <a className="btn btn--primary" href={primary.url}>
                  Download for {PLATFORM_LABEL[primary.os]}
                </a>
              ) : (
                <Link to="/download" className="btn btn--primary">
                  See the downloads
                </Link>
              )}
              <Link to="/features" className="btn btn--quiet">
                Read the 35 modules
              </Link>
            </div>
            <div className="hero-meta" style={{ '--i': 3 } as CSSProperties}>
              <span>
                <b>v{launcher.version}</b>
                Launcher, {primary ? `${primary.sizeMB} MB` : 'Windows and Linux'}
              </span>
              <span>
                <b>Java 21 · Fabric</b>
                Required to run
              </span>
              <span>
                <b>Sodium or VulkanMod</b>
                Pick one build
              </span>
            </div>
          </div>

          <div className="hero__bench" style={{ '--i': 4 } as CSSProperties}>
            <div className="hero-shot">
              <img
                src="/shots/menu.webp"
                alt="Valkaw Client main menu: numbered navigation on the left, a session panel on the right"
                width={1600}
                height={900}
                fetchPriority="high"
              />
              <span className="tick tick--tl" aria-hidden="true" />
              <span className="tick tick--tr" aria-hidden="true" />
              <span className="tick tick--bl" aria-hidden="true" />
              <span className="tick tick--br" aria-hidden="true" />
            </div>
            <BuildBench compact />
          </div>
        </div>
      </div>
    </section>
  );
}

function Client() {
  return (
    <section id="client" className="deck-section" data-reveal>
      <h2 className="visually-hidden">Screens from the client</h2>
      <ViewportDeck />
    </section>
  );
}

function Modules() {
  const rows = SHOWN.map(([id, note]) => {
    const feature = FEATURES.find(f => f.id === id);
    return feature ? { feature, note } : null;
  }).filter((row): row is { feature: FeatureItem; note: string } => Boolean(row));

  return (
    <section className="band" id="modules">
      <div className="container">
        <div className="head-block" data-reveal>
          <h2>Six you will notice first</h2>
          <p className="lead">The other thirty are on the modules page.</p>
        </div>

        <table className="table" style={{ marginTop: 'var(--space-2xl)' }}>
          <thead>
            <tr>
              <th scope="col">Kind</th>
              <th scope="col">Module</th>
              <th scope="col">Does</th>
              <th scope="col" style={{ textAlign: 'right' }}>
                Default
              </th>
            </tr>
          </thead>
          <tbody>
            {rows.map(({ feature, note }, i) => (
              <tr key={feature.id} data-reveal style={revealDelay(i)}>
                <td className="cell-kind">{KIND[feature.category]}</td>
                <td className="cell-name">{feature.name}</td>
                <td className="cell-note">{note}</td>
                <td className="cell-state">
                  <span className={feature.enabledByDefault ? 'state state--on' : 'state'}>
                    {feature.enabledByDefault ? 'on' : 'off'}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        <div style={{ marginTop: 'var(--space-xl)' }}>
          <Link to="/features" className="link">
            All 35 modules and their defaults
          </Link>
        </div>
      </div>
    </section>
  );
}

function Builds() {
  return (
    <section className="section" id="builds">
      <div className="container">
        <div className="head-block" data-reveal>
          <h2>Sodium or VulkanMod</h2>
          <p className="lead">The modules are identical. The renderer and its bundled mods are not.</p>
        </div>

        <div className="panels" style={{ marginTop: 'var(--space-2xl)' }}>
          <div className="panel panel--sodium" data-reveal>
            <h3>Sodium</h3>
            <dl>
              <div>
                <dt>Renderer</dt>
                <dd>OpenGL</dd>
              </div>
              <div>
                <dt>Mods loaded</dt>
                <dd className="num">129</dd>
              </div>
              <div>
                <dt>Bundled jars</dt>
                <dd className="num">22</dd>
              </div>
              <div>
                <dt>Best for</dt>
                <dd>NVIDIA, AMD, Intel on stable drivers</dd>
              </div>
            </dl>
            <p className="panel-note">Start here if unsure.</p>
          </div>

          <div className="panel panel--vulkan" data-reveal style={revealDelay(1)}>
            <h3>VulkanMod</h3>
            <dl>
              <div>
                <dt>Renderer</dt>
                <dd>Vulkan 1.2</dd>
              </div>
              <div>
                <dt>Mods loaded</dt>
                <dd className="num">111</dd>
              </div>
              <div>
                <dt>Bundled jars</dt>
                <dd className="num">13</dd>
              </div>
              <div>
                <dt>Best for</dt>
                <dd>Drivers with solid Vulkan 1.2</dd>
              </div>
            </dl>
            <p className="panel-note">Menu Backdrop added in 1.0.2.</p>
          </div>
        </div>

        <p style={{ marginTop: 'var(--space-md)', fontSize: 'var(--text-sm)', color: 'var(--color-muted)' }}>
          Never load both jars in one profile.
        </p>
      </div>
    </section>
  );
}

function Statement() {
  return (
    <section className="section--s">
      <div className="container grid12">
        <p className="statement col-1-8" data-reveal>
          It changes what you see, never what the server sees.
        </p>
        <p className="statement-side col-10-12" data-reveal style={revealDelay(1)}>
          Every module is client-side. None touches movement, reach or hit registration.
        </p>
      </div>
    </section>
  );
}

function Faq() {
  const shown = FAQS.slice(0, 3);
  return (
    <section className="section" id="faq">
      <div className="container grid12">
        <div className="col-1-3" data-reveal>
          <h2>Before the jar</h2>
        </div>
        <div className="faq col-5-12">
          {shown.map((item, i) => (
            <details key={item.question} data-reveal style={revealDelay(i)}>
              <summary>
                {item.question}
                <span className="mark" aria-hidden="true">
                  +
                </span>
              </summary>
              <p className="answer">{item.answer}</p>
            </details>
          ))}
        </div>
      </div>
    </section>
  );
}

function Close() {
  const sodium = DOWNLOADS.find(b => b.id === 'sodium')!;
  const vulkan = DOWNLOADS.find(b => b.id === 'vulkan')!;
  const url = (b: typeof sodium) => `${BRAND.releases}/download/v${b.version}/${b.file}`;

  return (
    <section className="band band--close">
      <div className="container">
        <div className="head-block" data-reveal>
          <h2>Pick a renderer.</h2>
        </div>
        <div className="actions" data-reveal style={{ marginTop: 'var(--space-xl)' }}>
          <a className="btn" href={url(sodium)} target="_blank" rel="noreferrer noopener">
            Download Sodium jar
          </a>
          <a className="btn" href={url(vulkan)} target="_blank" rel="noreferrer noopener">
            Download Vulkan jar
          </a>
        </div>
      </div>
    </section>
  );
}

export default function Index() {
  useScrollReveal();

  return (
    <>
      <Seo
        title="Valkaw Client for Minecraft 1.21.11"
        description="A Fabric client for Minecraft 1.21.11: 16 HUD readouts, six render controls, utility tools, and separate Sodium and VulkanMod builds."
        jsonLd={HOME_JSON_LD}
      />
      <main>
        <Hero />
        <Client />
        <Modules />
        <Builds />
        <Statement />
        <Faq />
        <Close />
      </main>
    </>
  );
}
