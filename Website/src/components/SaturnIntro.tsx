import { useEffect, useRef, useState } from 'react';
import { asset } from '@/lib/asset';

/**
 * Scene geometry: viewBox 780x420, ring centre (390, 210). Ring-plane tilt:
 * ry/rx = 0.28 (~16°). The outer ring reaches ~2.9 planet radii, matched to
 * the portrait. Radii below are in viewBox units.
 */
const CX = 390;
const CY = 210;
const PLANET = { rx: 92, ry: 86 };

/** The ring plane sits lower on screen than the globe's centre — the
distance implies that, and it keeps the near slab crossing low on the disc,
just like the portrait. */
const RING_DY = 20;
const RING_CY = CY + RING_DY;

/** Slabs of the ring system, inner to outer. */
const SLABS = [
  { rxo: 184, ryo: 51.5, rxi: 160, ryi: 44.8, fill: '#7E7A72', opacity: 0.7 }, // C
  { rxo: 212, ryo: 59.4, rxi: 184, ryi: 51.5, fill: '#F1EADC', opacity: 1 }, // B
  { rxo: 234, ryo: 65.5, rxi: 212, ryi: 59.4, fill: '#151413', opacity: 1 }, // Cassini
  { rxo: 270, ryo: 75.6, rxi: 234, ryi: 65.5, fill: '#E3DED4', opacity: 0.98 }, // A
];
const FAINT = { rxo: 292, ryo: 81.8, rxi: 282, ryi: 79, fill: '#9D9990', opacity: 0.45 };

/** Bands that visibly cross the globe's face: B and A only, so no dark seam. */
const CROSS = SLABS.filter(s => s.fill !== '#151413');

/** Six real Saturnian moons (Janus, Mimas, Enceladus, Tethys, Dione,
 Rhea) on orbits just outside the ring system, in viewBox units. Radii,
 periods (Kepler) and phases are shared with the launcher and the in-game
 main menu so all three render the same system. */
const MOONS = [
  { rx: 257, ry: 72, dur: 30, phase: 0.6, cls: 'moon--small' }, // Janus
  { rx: 281, ry: 79, dur: 34, phase: 1.0, cls: 'moon--gold' }, // Mimas
  { rx: 294, ry: 82, dur: 37, phase: 5.6, cls: 'moon--ice' }, // Enceladus
  { rx: 308, ry: 86, dur: 39, phase: 1.9, cls: 'moon--cream' }, // Tethys
  { rx: 329, ry: 92, dur: 44, phase: 4.0, cls: 'moon--gold' }, // Dione
  { rx: 367, ry: 103, dur: 51, phase: 0.2, cls: 'moon--ice' }, // Rhea
];

/** Percent positions for the moon layer (centre 50/50 of the scene box). */
const MOON_POS = MOONS.map(m => ({
  rx: (m.rx / 780) * 100,
  ry: (m.ry / 420) * 100,
  dur: m.dur,
  phase: m.phase,
  cls: m.cls,
}));

/** Hover-tooltip facts for each moon (shared with the launcher). */
const MOON_INFO = [
  { title: 'Janus', kind: 'Inner shepherd moon', orbit: '151,400 km', period: '0.70 d', fact: 'Co-orbital with Epimetheus; they swap orbits roughly every 4 years.' },
  { title: 'Mimas', kind: 'Cratered ice moon', orbit: '185,500 km', period: '0.94 d', fact: 'The Herschel crater gives it a Death Star look.' },
  { title: 'Enceladus', kind: 'Cryovolcanic ice moon', orbit: '238,000 km', period: '1.37 d', fact: 'South-pole geysers feed Saturn\u2019s E ring.' },
  { title: 'Tethys', kind: 'Moderate ice moon', orbit: '294,700 km', period: '1.89 d', fact: 'Ithaca Chasma canyon stretches ~2,000 km.' },
  { title: 'Dione', kind: 'Fractured ice moon', orbit: '377,400 km', period: '2.74 d', fact: 'Wispy bright streaks mark its trailing hemisphere.' },
  { title: 'Rhea', kind: 'Large icy moon', orbit: '527,100 km', period: '4.52 d', fact: 'Saturn\u2019s second-largest moon; a thin oxygen exosphere.' },
];

const SATURN_INFO = {
  title: 'Saturn',
  kind: 'Gas giant \u00b7 6th planet',
  orbit: '1.43 billion km from the Sun',
  period: 'Day \u2248 10.7 h \u00b7 Year \u2248 29.5 yr',
  fact: 'Its rings span ~282,000 km yet are only ~10 m thick.',
};

/**
 * Moons are driven by requestAnimationFrame, but instead of re-rendering
 * React every frame, each frame writes transform/stacking directly to the DOM
 * element. This avoids layout churn (the jitter) and is GPU-friendly.
 * A larger invisible hit zone (.moon-hit) captures hover; hovering pauses that
 * moon's orbit and asks the parent to show its info tip.
 */
function MoonLayer({ onHover, onHoverLeave }: {
  onHover: (i: number, e: React.PointerEvent) => void;
  onHoverLeave: () => void;
}) {
  const sceneRef = useRef<HTMLDivElement>(null);
  const moonRefs = useRef<(HTMLSpanElement | null)[]>([]);
  // paused lives in a ref so the animation loop never restarts (which would
  // reset every moon to the start of its orbit). Only the hover callback
  // re-renders the parent's tooltip.
  const pausedRef = useRef<number | null>(null);

  useEffect(() => {
    const scene = sceneRef.current;
    if (!scene) return;
    if (typeof window !== 'undefined' &&
        window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    let raf = 0;
    const start = performance.now();
    const loop = (ts: number) => {
      const scene = sceneRef.current;
      const w = scene ? scene.clientWidth : 0;
      const h = scene ? scene.clientHeight : 0;
      const t = (ts - start) / 1000;
      MOON_POS.forEach((m, i) => {
        if (i === pausedRef.current) return;
        const el = moonRefs.current[i];
        if (!el) return;
        const angle = m.phase + (t * 2 * Math.PI) / m.dur;
        const front = Math.sin(angle) > 0;
        const x = ((50 + m.rx * Math.cos(angle)) / 100) * w;
        const y = ((50 + (RING_DY / 420) * 100 + m.ry * Math.sin(angle)) / 100) * h;
        el.style.transform = `translate(${x}px, ${y}px) translate(-50%, -50%)`;
        el.style.zIndex = front ? '5' : '0';
      });
      raf = requestAnimationFrame(loop);
    };
    raf = requestAnimationFrame(loop);
    return () => cancelAnimationFrame(raf);
  }, []);

  return (
    <div className="moon-layer" ref={sceneRef}>
      {MOON_POS.map((m, i) => (
        <span
          key={i}
          ref={el => { moonRefs.current[i] = el; }}
          className="moon-hit"
          onPointerEnter={e => { pausedRef.current = i; onHover(i, e); }}
          onPointerLeave={() => { pausedRef.current = null; onHoverLeave(); }}
        >
          <span className={`moon ${m.cls}`} />
        </span>
      ))}
    </div>
  );
}

/** One flat ring slab between two concentric ellipses (even-odd fill). */
function slab(rxo: number, ryo: number, rxi: number, ryi: number): string {
  const ring = (rx: number, ry: number) =>
    `M ${CX - rx} ${RING_CY} A ${rx} ${ry} 0 1 0 ${CX + rx} ${RING_CY} A ${rx} ${ry} 0 1 0 ${CX - rx} ${RING_CY} Z`;
  const outer = ring(rxo, ryo);
  const inner = ring(rxi, ryi);
  return `${outer} ${inner}`;
}

/** A full ring ellipse (used for the fine sub-band drifts). */
function ringEllipse(rx: number, ry: number): string {
  return `M ${CX - rx} ${RING_CY} A ${rx} ${ry} 0 1 0 ${CX + rx} ${RING_CY} A ${rx} ${ry} 0 1 0 ${CX - rx} ${RING_CY} Z`;
}

/** Deterministic PRNG (mulberry32) so the particle field is stable. */
function mulberry32(seed: number) {
  let a = seed >>> 0;
  return () => {
    a |= 0; a = (a + 0x6D2B79F5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const RING_PARTICLE_COUNT = 340;
const RING_R_MIN = 150;
const RING_R_MAX = 292;
const RING_FLATTEN = 0.28; // ring-plane tilt (ry/rx), matches the game

/** Precomputed ring particles: radius, angular speed (Kepler: omega ∝
    r^-1.5) and phase. The Cassini division is left dark. */
const RING_PARTICLES = (() => {
  const rand = mulberry32(20261109);
  const out: { r: number; omega: number; phase: number; tone: number }[] = [];
  let guard = 0;
  while (out.length < RING_PARTICLE_COUNT && guard++ < 20000) {
    const r = RING_R_MIN + rand() * (RING_R_MAX - RING_R_MIN);
    if (r > 213 && r < 233) continue; // Cassini division
    const period = 8 * Math.pow(r / 184, 1.5); // ≈8s at C ring outer edge
    out.push({ r, omega: (2 * Math.PI) / period, phase: rand() * Math.PI * 2, tone: rand() });
  }
  return out;
})();

/** Hundreds of individual ring particles orbit at Kepler speeds, so the ring
    reads as a spinning disc of ice and rock — exactly like a real simulation.
    Each frame writes cx/cy straight to the DOM (no React re-render). */
function RingParticles() {
  const refs = useRef<(SVGCircleElement | null)[]>([]);
  useEffect(() => {
    if (typeof window !== 'undefined' &&
        window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    let raf = 0;
    const loop = (ts: number) => {
      RING_PARTICLES.forEach((p, i) => {
        const el = refs.current[i];
        if (!el) return;
        const a = p.phase + p.omega * (ts / 1000);
        el.setAttribute('cx', String(CX + p.r * Math.cos(a)));
        el.setAttribute('cy', String(RING_CY + p.r * RING_FLATTEN * Math.sin(a)));
      });
      raf = requestAnimationFrame(loop);
    };
    raf = requestAnimationFrame(loop);
    return () => cancelAnimationFrame(raf);
  }, []);

  return (
    <g className="ring-particles">
      {RING_PARTICLES.map((p, i) => (
        <circle
          key={i}
          ref={el => { refs.current[i] = el; }}
          className={p.tone > 0.75 ? 'ring-particle ring-particle--dark' : 'ring-particle'}
          cx={CX + p.r}
          cy={RING_CY}
          r={0.9 + p.tone * 0.7}
        />
      ))}
    </g>
  );
}

/** A thin dashed orbit line for each moon. */
/**
 * Full-viewport preview built like an observatory plate. There is ONE ring
 * plate, masked out of the globe's silhouette, so no half-split seam exists.
 * The only element crossing the disc is the near-side B and A band, clipped to
 * the globe, which is exactly where the ring plane passes in front of it.
 */
type TipData = { title: string; kind: string; orbit: string; period: string; fact: string; x: number; y: number };

export function SaturnIntro() {
  const [tip, setTip] = useState<TipData | null>(null);
  const sceneRef = useRef<HTMLDivElement>(null);

  const tipFromEvent = (e: React.PointerEvent, info: Omit<TipData, 'x' | 'y'>) => {
    const scene = sceneRef.current;
    if (!scene) return;
    const r = scene.getBoundingClientRect();
    // Clamp so the ~220px-wide, ~150px-tall tip stays fully inside the scene.
    const TIP_W = 112;   // half the tip width
    const TIP_H = 160;   // approx tip height
    const x = Math.max(TIP_W, Math.min(e.clientX - r.left, r.width - TIP_W));
    const y = Math.max(TIP_H, Math.min(e.clientY - r.top, r.height - 10));
    setTip({ ...info, x, y });
  };

  return (
    <section className="intro" aria-label="Valkaw Client preview">
      <div className="intro-scene" aria-hidden="true">
        <span className="intro-star intro-star--a" />
        <span className="intro-star intro-star--b" />
        <span className="intro-star intro-star--c" />
        <span className="intro-star intro-star--d" />
        <span className="intro-star intro-star--e" />
        <Starfield />

        <div className="scene" ref={sceneRef}>
          {/* The single ring plate, punched out of the globe's silhouette. */}
          <svg className="rings-back" viewBox="0 0 780 420" xmlns="http://www.w3.org/2000/svg">
            <defs>
              <filter id="ring-soft" x="-5%" y="-10%" width="110%" height="120%">
                <feGaussianBlur stdDeviation="0.6" />
              </filter>
              <filter id="ring-faint" x="-10%" y="-20%" width="120%" height="140%">
                <feGaussianBlur stdDeviation="2.2" />
              </filter>
              <filter id="pool" x="-30%" y="-60%" width="160%" height="220%">
                <feGaussianBlur stdDeviation="12" />
              </filter>
              <filter id="sheen-blur" x="-20%" y="-40%" width="140%" height="180%">
                <feGaussianBlur stdDeviation="3" />
              </filter>
              <linearGradient id="ring-sheen" x1="0" y1="0" x2="1" y2="0.4">
                <stop offset="0%" stopColor="#FFF8E7" stopOpacity="0" />
                <stop offset="42%" stopColor="#FFF8E7" stopOpacity="0.5" />
                <stop offset="58%" stopColor="#FFF4D6" stopOpacity="0.4" />
                <stop offset="100%" stopColor="#FFF8E7" stopOpacity="0" />
              </linearGradient>
            </defs>
            <g>
              <g fillRule="evenodd" filter="url(#ring-soft)">
                {SLABS.map((s, i) => (
                  <path key={i} d={slab(s.rxo, s.ryo, s.rxi, s.ryi)} fill={s.fill} opacity={s.opacity} />
                ))}
              </g>
              <g fillRule="evenodd" filter="url(#ring-faint)">
                <path d={slab(FAINT.rxo, FAINT.ryo, FAINT.rxi, FAINT.ryi)} fill={FAINT.fill} opacity={FAINT.opacity} />
              </g>
              {/* Sunlit sheen sweeping the far ring bands (B and A). */}
              <g fillRule="evenodd" filter="url(#sheen-blur)" opacity="0.5">
                {CROSS.map((s, i) => (
                  <path key={`back-sheen-${i}`} d={slab(s.rxo, s.ryo, s.rxi, s.ryi)} fill="url(#ring-sheen)" />
                ))}
              </g>
              {/* Fine structure: static sub-band streaks (light wisps + dark
                  gaps) that texture the rings. Rings do not rotate — the spin
                  is told by the moons orbiting outside (MoonLayer). */}
              <g fill="none" className="ring-structure">
                <path className="wisp-c" d={ringEllipse(176, 49)} />
                <path className="shade-c" d={ringEllipse(168, 47)} />
                <path className="wisp-b" d={ringEllipse(202, 56.5)} />
                <path className="wisp-b" d={ringEllipse(193, 54)} />
                <path className="shade-b" d={ringEllipse(187, 52.2)} />
                <path className="wisp-a" d={ringEllipse(256, 71.5)} />
                <path className="shade-a" d={ringEllipse(245, 68.5)} />
              </g>
              {/* Hundreds of particles orbit at Kepler speeds — the spin. */}
              <RingParticles />
              {/* The planet's shadow pooling on the rings behind it. */}
              <ellipse cx={CX} cy={RING_CY - 6} rx="150" ry="34" stroke="#040403" strokeWidth="34"
                opacity="0.6" fill="none" filter="url(#pool)" />
            </g>
          </svg>

          {/* The globe itself, drawn whole. */}
          <svg className="planet-svg" viewBox="0 0 780 420" xmlns="http://www.w3.org/2000/svg"
            onPointerEnter={e => tipFromEvent(e, SATURN_INFO)}
            onPointerLeave={() => setTip(null)}>
            <defs>
              <linearGradient id="haze-bands" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="#463726" />
                <stop offset="12%" stopColor="#6B563E" />
                <stop offset="28%" stopColor="#B49A76" />
                <stop offset="45%" stopColor="#EBDDC6" />
                <stop offset="50%" stopColor="#F7EFE0" />
                <stop offset="55%" stopColor="#EBDDC6" />
                <stop offset="72%" stopColor="#B49A76" />
                <stop offset="88%" stopColor="#6B563E" />
                <stop offset="100%" stopColor="#463726" />
              </linearGradient>
              <radialGradient id="limb-shade" cx="36%" cy="28%" r="90%">
                <stop offset="0%" stopColor="#FFF3DF" stopOpacity="0.34" />
                <stop offset="38%" stopColor="#FFF3DF" stopOpacity="0.05" />
                <stop offset="68%" stopColor="#FFF3DF" stopOpacity="0" />
                <stop offset="88%" stopColor="#1C1103" stopOpacity="0.55" />
                <stop offset="100%" stopColor="#120B02" stopOpacity="0.82" />
              </radialGradient>
              <radialGradient id="polar-cap" cx="50%" cy="0%" r="90%">
                <stop offset="0%" stopColor="#544C42" stopOpacity="0.55" />
                <stop offset="100%" stopColor="#544C42" stopOpacity="0" />
              </radialGradient>
              <radialGradient id="atmos-glow" cx="50%" cy="50%" r="50%">
                <stop offset="68%" stopColor="#E8D4AC" stopOpacity="0.32" />
                <stop offset="100%" stopColor="#E8D4AC" stopOpacity="0" />
              </radialGradient>
              <radialGradient id="sun-glint" cx="50%" cy="50%" r="50%">
                <stop offset="0%" stopColor="#FFF6DD" stopOpacity="0.85" />
                <stop offset="55%" stopColor="#FFE9B8" stopOpacity="0.35" />
                <stop offset="100%" stopColor="#FFE9B8" stopOpacity="0" />
              </radialGradient>
              <linearGradient id="disc-sheen" x1="0.25" y1="0" x2="0.75" y2="1">
                <stop offset="0%" stopColor="#FFF3DC" stopOpacity="0" />
                <stop offset="40%" stopColor="#FFF3DC" stopOpacity="0.14" />
                <stop offset="62%" stopColor="#FFF3DC" stopOpacity="0.04" />
                <stop offset="100%" stopColor="#FFF3DC" stopOpacity="0" />
              </linearGradient>
              <clipPath id="globe">
                <ellipse cx={CX} cy={CY} rx={PLANET.rx} ry={PLANET.ry} />
              </clipPath>
            </defs>

            {/* Soft atmospheric halo beyond the disc. */}
            <ellipse cx={CX} cy={CY} rx={PLANET.rx + 14} ry={PLANET.ry + 14} fill="url(#atmos-glow)" />

            <g clipPath="url(#globe)">
              {/* Hand-drawn disc mimicking the in-game texture: a warm tan
                  base that darkens toward the poles (sampled from
                  saturn.png), subtle cloud bands, and sunlit-left shading. */}
              <ellipse cx={CX} cy={CY} rx={PLANET.rx} ry={PLANET.ry} fill="url(#haze-bands)" />
              {/* Axial rotation: cloud bands drift horizontally across the disc. */}
              <g className="planet-bands">
                <ellipse cx={CX} cy={CY - 14} rx={PLANET.rx + 8} ry={PLANET.ry * 0.28} fill="#F0DFBC" opacity="0.16" />
                <ellipse cx={CX} cy={CY + 22} rx={PLANET.rx + 8} ry={PLANET.ry * 0.22} fill="#A58C6C" opacity="0.1" />
                <ellipse cx={CX} cy={CY + 2} rx={PLANET.rx + 6} ry={PLANET.ry * 0.14} fill="#F0DFBC" opacity="0.08" />
                <ellipse cx={CX} cy={CY + 38} rx={PLANET.rx + 6} ry={PLANET.ry * 0.12} fill="#A58C6C" opacity="0.07" />
                <ellipse cx={CX} cy={CY - 34} rx={PLANET.rx + 8} ry={PLANET.ry * 0.1} fill="#F0DFBC" opacity="0.06" />
                <ellipse cx={CX} cy={CY + 56} rx={PLANET.rx + 7} ry={PLANET.ry * 0.1} fill="#F0DFBC" opacity="0.07" />
              </g>
              {/* Soft specular sheen from the sunlit side of the disc. */}
              <ellipse cx={CX} cy={CY} rx={PLANET.rx} ry={PLANET.ry} fill="url(#disc-sheen)" />
              {/* Subtle high-altitude streamers, tilted with the wind shear. */}
              <ellipse cx={CX} cy={CY - 30} rx={PLANET.rx * 0.9} ry={PLANET.ry * 0.06}
                fill="#F6E9CB" opacity="0.1" transform={`rotate(-8 ${CX} ${CY})`} />
              <ellipse cx={CX} cy={CY + 44} rx={PLANET.rx * 0.95} ry={PLANET.ry * 0.05}
                fill="#7A6140" opacity="0.12" transform={`rotate(6 ${CX} ${CY})`} />
              {/* Sun glint — a bright grazing reflection near the lit limb. */}
              <ellipse cx={CX - 34} cy={CY - 30} rx="16" ry="9" fill="url(#sun-glint)"
                transform={`rotate(-20 ${CX - 34} ${CY - 30})`} />
              <ellipse cx={CX} cy={CY - PLANET.ry + 18} rx={PLANET.rx * 0.84} ry={PLANET.ry * 0.46} fill="url(#polar-cap)" />
              <ellipse cx={CX} cy={CY} rx={PLANET.rx} ry={PLANET.ry} fill="url(#limb-shade)" />
            </g>
          </svg>

          {/* The near band crossing the globe's face: B and A only. */}
          <svg className="rings-front" viewBox="0 0 780 420" xmlns="http://www.w3.org/2000/svg">
            <defs>
              <clipPath id="globe-strip">
                <ellipse cx={CX} cy={CY} rx={PLANET.rx} ry={PLANET.ry} />
              </clipPath>
              <clipPath id="near-strip"><rect y={CY + RING_DY} width="780" height="210" /></clipPath>
              <filter id="cross-soft" x="-10%" y="-20%" width="120%" height="140%">
                <feGaussianBlur stdDeviation="1.4" />
              </filter>
              <linearGradient id="front-sheen" x1="0" y1="0" x2="1" y2="0.35">
                <stop offset="0%" stopColor="#FFF8E7" stopOpacity="0" />
                <stop offset="38%" stopColor="#FFF8E7" stopOpacity="0.5" />
                <stop offset="62%" stopColor="#FFF4D6" stopOpacity="0.35" />
                <stop offset="100%" stopColor="#FFF8E7" stopOpacity="0" />
              </linearGradient>
            </defs>

            <g clipPath="url(#globe-strip)">
              <g clipPath="url(#near-strip)" fillRule="evenodd" filter="url(#cross-soft)">
                {CROSS.map((s, i) => (
                  <path key={i} d={slab(s.rxo, s.ryo, s.rxi, s.ryi)} fill={s.fill} opacity={s.opacity} />
                ))}
                {/* Warm sunlit sheen sweeping the near ring band. */}
                {CROSS.map((s, i) => (
                  <path key={`front-sheen-${i}`} d={slab(s.rxo, s.ryo, s.rxi, s.ryi)} fill="url(#front-sheen)" opacity="0.55" />
                ))}
              </g>
            </g>
          </svg>

          <MoonLayer
            onHover={(i, e) => tipFromEvent(e, MOON_INFO[i])}
            onHoverLeave={() => setTip(null)}
          />

          {tip && (
            <div className="saturn-tip" style={{ left: tip.x, top: tip.y }}>
              <span className="tip-title">{tip.title}</span>
              <span className="tip-kind">{tip.kind}</span>
              <span className="tip-line">Orbit · {tip.orbit}</span>
              <span className="tip-line">Period · {tip.period}</span>
              <span className="tip-fact">{tip.fact}</span>
            </div>
          )}
        </div>
      </div>

      <div className="intro-content">
        <img className="intro-logo" src={asset('/valkaw-logo.png')} alt="" width={512} height={512} />
        <h1 className="intro-name">Valkaw</h1>
        <p className="intro-sub">A lighter Fabric client for Minecraft, with its own corner of the sky.</p>
      </div>

      <a className="scroll-cue" href="#overview" aria-label="Scroll to overview">
        <span className="cue-chevron" />
      </a>
    </section>
  );
}

/* A dense, softly twinkling star layer generated at runtime inside the intro
   scene (mirrors the launcher's starfield). Sizes, brightness and twinkle
   timing are random; a few stars are warm gold to match Saturn's rim light. */
function Starfield() {
  const ref = useRef<HTMLDivElement | null>(null);
  useEffect(() => {
    const host = ref.current;
    if (!host) return;
    const count = window.innerWidth < 900 ? 60 : 120;
    const frag = document.createDocumentFragment();
    for (let i = 0; i < count; i++) {
      const s = document.createElement('span');
      const size = 1 + Math.random() * 1.6;
      const gold = Math.random() < 0.18;
      s.className = 'star' + (gold ? ' star--gold' : '');
      s.style.width = `${size}px`;
      s.style.height = `${size}px`;
      s.style.left = `${(Math.random() * 100).toFixed(2)}%`;
      s.style.top = `${(Math.random() * 100).toFixed(2)}%`;
      s.style.opacity = (0.35 + Math.random() * 0.45).toFixed(2);
      s.style.setProperty('--tw-dur', `${(3 + Math.random() * 4).toFixed(2)}s`);
      s.style.animationDelay = `${(-Math.random() * 6).toFixed(2)}s`;
      frag.appendChild(s);
    }
    host.appendChild(frag);
    return () => { host.innerHTML = ''; };
  }, []);
  return <div className="starfield" ref={ref} aria-hidden="true" />;
}

export default SaturnIntro;
