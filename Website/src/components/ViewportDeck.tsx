import { useState } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { SCREENSHOTS } from '@/data/content';

/** The client's own screens: one wide viewport, a caption, a rail. */
export function ViewportDeck() {
  const [index, setIndex] = useState(1);
  const shot = SCREENSHOTS[index];
  const total = SCREENSHOTS.length;

  const step = (delta: number) => setIndex(i => (i + delta + total) % total);

  return (
    <div
      className="deck"
      role="group"
      aria-label="Client screenshots"
      onKeyDown={event => {
        if (event.key === 'ArrowRight') {
          event.preventDefault();
          step(1);
        }
        if (event.key === 'ArrowLeft') {
          event.preventDefault();
          step(-1);
        }
      }}
    >
      <div className="deck-screen">
        <img key={shot.src} src={shot.src} alt={shot.caption} width={1600} height={900} loading="lazy" decoding="async" />
        <span className="tick tick--tl" aria-hidden="true" />
        <span className="tick tick--tr" aria-hidden="true" />
        <span className="tick tick--bl" aria-hidden="true" />
        <span className="tick tick--br" aria-hidden="true" />
      </div>

      <div className="container deck-chrome">
        <div className="deck-bar">
          <div className="deck-caption" aria-live="polite">
            <h3>{shot.title}</h3>
            <p>{shot.caption}</p>
          </div>
          <div className="deck-controls">
            <span className="deck-count">
              {String(index + 1).padStart(2, '0')} / {String(total).padStart(2, '0')}
            </span>
            <button type="button" className="icon-btn" onClick={() => step(-1)} aria-label="Previous screen">
              <ChevronLeft className="size-4" aria-hidden="true" />
            </button>
            <button type="button" className="icon-btn" onClick={() => step(1)} aria-label="Next screen">
              <ChevronRight className="size-4" aria-hidden="true" />
            </button>
          </div>
        </div>

        <div className="deck-thumbs">
          {SCREENSHOTS.map((item, i) => (
            <button
              key={item.src}
              type="button"
              className="thumb"
              aria-current={i === index}
              aria-label={`${item.title}, screen ${i + 1}`}
              onClick={() => setIndex(i)}
            >
              <img src={item.src} alt="" width={320} height={180} loading="lazy" />
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}

export default ViewportDeck;
