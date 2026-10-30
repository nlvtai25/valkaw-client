import { useEffect, useState } from 'react';
import { Link, NavLink } from 'react-router-dom';
import { Menu, X } from 'lucide-react';
import { Logo } from './Logo';

const NAV_LINKS = [
  { to: '/', label: 'Home', end: true },
  { to: '/features', label: 'Modules', end: false },
  { to: '/download', label: 'Download', end: false },
];

export function Navbar() {
  const [stuck, setStuck] = useState(false);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    const onScroll = () => setStuck(window.scrollY > 8);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  useEffect(() => {
    if (!open) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open]);

  return (
    <header className="site-header" data-stuck={stuck}>
      <nav className="container nav" aria-label="Main">
        <Link to="/" className="wordmark" aria-label="Valkaw Client, home">
          <Logo />
        </Link>

        <div className="nav-links">
          {NAV_LINKS.map(link => (
            <NavLink key={link.to} to={link.to} end={link.end} className="nav-link">
              {link.label}
            </NavLink>
          ))}
        </div>

        <div className="nav-actions">
          <button
            type="button"
            className="nav-toggle"
            aria-expanded={open}
            aria-controls="mobile-nav"
            aria-label={open ? 'Close menu' : 'Open menu'}
            onClick={() => setOpen(v => !v)}
          >
            {open ? <X className="size-5" aria-hidden="true" /> : <Menu className="size-5" aria-hidden="true" />}
          </button>
        </div>
      </nav>

      <div className="nav-drawer" id="mobile-nav" data-open={open} hidden={!open}>
        <div className="container">
          {NAV_LINKS.map(link => (
            <NavLink key={link.to} to={link.to} end={link.end} onClick={() => setOpen(false)}>
              {link.label}
            </NavLink>
          ))}
          <Link
            to="/download"
            className="btn"
            style={{ marginTop: 'var(--space-md)' }}
            onClick={() => setOpen(false)}
          >
            Get 1.0.2
          </Link>
        </div>
      </div>
    </header>
  );
}

export default Navbar;
