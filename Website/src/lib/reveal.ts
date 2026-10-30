import { useEffect, type CSSProperties } from 'react';

/** Delay for staggered siblings, in 60 ms steps. */
export const revealDelay = (step: number): CSSProperties =>
  ({ '--reveal-delay': `${step * 60}ms` }) as CSSProperties;

/**
 * Fades in [data-reveal] blocks the first time they scroll into view.
 *
 * Blocks already on screen when the page mounts are marked as shown before the
 * `reveal-ready` class lands, so a route change never flashes them from
 * opacity 0. With reduced motion, or without JS, the class is never added and
 * every block simply renders.
 */
export function useScrollReveal() {
  useEffect(() => {
    if (typeof window === 'undefined') return;
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;

    const root = document.documentElement;
    const observer = new IntersectionObserver(
      entries => {
        for (const entry of entries) {
          if (!entry.isIntersecting) continue;
          entry.target.classList.add('is-in');
          observer.unobserve(entry.target);
        }
      },
      { rootMargin: '0px 0px -10% 0px', threshold: 0.08 },
    );

    const fold = window.innerHeight * 0.92;
    for (const el of document.querySelectorAll<HTMLElement>('[data-reveal]')) {
      if (el.getBoundingClientRect().top < fold) el.classList.add('is-in');
      else observer.observe(el);
    }
    root.classList.add('reveal-ready');

    return () => {
      observer.disconnect();
      root.classList.remove('reveal-ready');
    };
  }, []);
}
