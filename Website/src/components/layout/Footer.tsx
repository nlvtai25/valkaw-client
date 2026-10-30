import { Link } from 'react-router-dom';
import { BRAND } from '@/data/content';
import { Logo } from './Logo';

interface FooterLink {
  label: string;
  to?: string;
  href?: string;
}

const COLUMNS: { title: string; links: FooterLink[] }[] = [
  {
    title: 'Client',
    links: [
      { label: 'All 35 modules', to: '/features' },
      { label: 'Download', to: '/download' },
      { label: 'Install guide', to: '/download#install' },
      { label: 'Changelog', to: '/download#changelog' },
    ],
  },
  {
    title: 'Elsewhere',
    links: [
      { label: 'Source code', href: BRAND.github },
      { label: 'Issues', href: BRAND.issues },
      { label: 'Releases', href: BRAND.releases },
    ],
  },
];

export function Footer() {
  return (
    <footer className="site-footer">
      <div className="container">
        <div className="footer-grid">
          <div>
            <Link to="/" className="wordmark" aria-label="Valkaw Client, home">
              <Logo />
            </Link>
            <p className="footer-statement" style={{ marginTop: 'var(--space-md)' }}>
              Fabric client for {BRAND.minecraft}. Apache 2.0.
            </p>
          </div>

          {COLUMNS.map(column => (
            <nav key={column.title} className="footer-col" aria-label={column.title}>
              <h2>{column.title}</h2>
              <ul>
                {column.links.map(link =>
                  link.href ? (
                    <li key={link.label}>
                      <a href={link.href} target="_blank" rel="noreferrer noopener">
                        {link.label}
                      </a>
                    </li>
                  ) : (
                    <li key={link.label}>
                      <Link to={link.to!}>{link.label}</Link>
                    </li>
                  ),
                )}
              </ul>
            </nav>
          ))}
        </div>

        <div className="footer-legal">
          <p>
            © {new Date().getFullYear()} {BRAND.name}. {BRAND.license}.
          </p>
          <p>Not affiliated with Mojang or Microsoft.</p>
        </div>
      </div>
    </footer>
  );
}

export default Footer;
