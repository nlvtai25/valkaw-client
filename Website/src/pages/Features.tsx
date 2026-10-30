import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Seo } from '@/components/Seo';
import { BRAND, CATEGORY_META, COMPARISONS, FEATURES } from '@/data/content';
import { useScrollReveal } from '@/lib/reveal';
import type { FeatureCategory } from '@/types/content';

type Filter = 'all' | FeatureCategory;

const COUNTS: Record<Filter, number> = {
  all: FEATURES.length,
  hud: FEATURES.filter(f => f.category === 'hud').length,
  performance: FEATURES.filter(f => f.category === 'performance').length,
  utility: FEATURES.filter(f => f.category === 'utility').length,
};

const KIND: Record<FeatureCategory, string> = {
  hud: 'hud',
  performance: 'perf',
  utility: 'util',
};

const FILTERS: { key: Filter; label: string }[] = [
  { key: 'all', label: 'All' },
  ...CATEGORY_META.map(c => ({ key: c.key as Filter, label: c.label })),
];

const FEATURES_JSON_LD = {
  '@context': 'https://schema.org',
  '@type': 'SoftwareApplication',
  name: BRAND.name,
  applicationCategory: 'GameApplication',
  operatingSystem: 'Linux, Windows, macOS',
  description: BRAND.description,
  featureList: FEATURES.map(f => f.name),
  offers: { '@type': 'Offer', price: '0', priceCurrency: 'USD' },
};

function Compare() {
  return (
    <div className="compare-wrap">
      <table className="table compare">
        <caption className="visually-hidden">
          Valkaw compared with vanilla Minecraft and Sodium on Fabric
        </caption>
        <thead>
          <tr>
            <th scope="col">Capability</th>
            <th scope="col">Vanilla</th>
            <th scope="col">Sodium + Fabric</th>
            <th scope="col">Valkaw</th>
          </tr>
        </thead>
        <tbody>
          {COMPARISONS.map(row => {
            const negative = (value: string) => value === 'No' || value === 'Not supported';
            return (
              <tr key={row.capability}>
                <th scope="row" className="compare-name">
                  {row.capability}
                </th>
                <td data-label="Vanilla" className={negative(row.vanilla) ? 'cell-note' : undefined}>
                  {row.vanilla}
                </td>
                <td data-label="Sodium" className={negative(row.sodium) ? 'cell-note' : undefined}>
                  {row.sodium}
                </td>
                <td data-label="Valkaw" className="compare-us">
                  {row.valkaw}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

export default function Features() {
  useScrollReveal();
  const [filter, setFilter] = useState<Filter>('all');
  const list = filter === 'all' ? FEATURES : FEATURES.filter(f => f.category === filter);

  return (
    <>
      <Seo
        title="Modules — Valkaw Client"
        description="All 37 Valkaw Client features: 16 HUD readouts, 6 performance controls and 15 utility tools, with what each one changes and its default state."
        jsonLd={FEATURES_JSON_LD}
      />
      <main>
        <section className="section" data-reveal>
          <div className="container">
            <div className="page-head">
              <h1>Thirty-six modules, no combat.</h1>
              <p className="lead">HUD, performance and utility only. Every switch stays client-side.</p>
            </div>

            <h2 className="visually-hidden">All modules</h2>

            <div className="filters" role="group" aria-label="Filter modules by kind" style={{ marginTop: 'var(--space-2xl)' }}>
              {FILTERS.map(item => (
                <button
                  key={item.key}
                  type="button"
                  aria-pressed={filter === item.key}
                  onClick={() => setFilter(item.key)}
                >
                  {item.label}
                  <span className="count">{COUNTS[item.key]}</span>
                </button>
              ))}
            </div>

            <div className="modules" style={{ marginTop: 'var(--space-sm)' }}>
              {list.map(feature => (
                <details className="module" key={feature.id}>
                  <summary>
                    <span className="module-name">
                      <span className="module-kind">{KIND[feature.category]}</span>
                      {feature.name}
                    </span>
                    <span className="module-note">{feature.description}</span>
                    <span className={feature.enabledByDefault ? 'state state--on' : 'state'}>
                      {feature.enabledByDefault ? 'on' : 'off'}
                    </span>
                  </summary>
                  <div className="module-body">
                    <p className="module-note" style={{ color: 'var(--color-ink-2)' }}>
                      {feature.benefit}
                    </p>
                    <ul className="module-details">
                      {feature.details.map(detail => (
                        <li key={detail}>{detail}</li>
                      ))}
                    </ul>
                  </div>
                </details>
              ))}
            </div>

            <p
              style={{
                marginTop: 'var(--space-md)',
                fontSize: 'var(--text-sm)',
                color: 'var(--color-muted)',
              }}
            >
              Showing {list.length} of {FEATURES.length}. Open a row for its options.
            </p>
          </div>
        </section>

        <section className="section band" id="comparison">
          <div className="container" data-reveal>
            <div className="head-block">
              <h2>What Sodium does not ship with</h2>
              <p className="lead">
                Sodium makes frames. Valkaw adds the HUD, the settings screen and the quiet tools around it.
              </p>
            </div>
            <div style={{ marginTop: 'var(--space-2xl)' }}>
              <Compare />
            </div>
          </div>
        </section>

        <section className="section--s" data-reveal>
          <div className="container">
            <div className="head-block">
              <h2>Pick a renderer and start.</h2>
              <p className="lead">Both builds share the same modules. Only the renderer and its bundled mods differ.</p>
            </div>
            <div className="actions" style={{ marginTop: 'var(--space-lg)' }}>
              <Link to="/download" className="btn">
                Download 1.0.2.1
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
