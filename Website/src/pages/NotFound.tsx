import { Link } from 'react-router-dom';
import { Seo } from '@/components/Seo';

export default function NotFound() {
  return (
    <>
      <Seo
        title="Page not found — Valkaw Client"
        description="This page is not part of the Valkaw Client site. Head back to the overview or the download page."
      />
      <main className="section--l">
        <div className="container grid12">
          <div style={{ gridColumn: '1 / span 8' }}>
            <p className="num" style={{ color: 'var(--color-cool)', fontSize: 'var(--text-sm)' }}>
              404
            </p>
            <h1 style={{ marginTop: 'var(--space-sm)' }}>Signal lost.</h1>
            <p className="lead" style={{ marginTop: 'var(--space-lg)' }}>
              This page is not in the build. It may have moved, or it never existed.
            </p>
            <div className="actions" style={{ marginTop: 'var(--space-xl)' }}>
              <Link to="/" className="btn">
                Back to the overview
              </Link>
              <Link to="/download" className="btn btn--quiet">
                Go to downloads
              </Link>
            </div>
          </div>
        </div>
      </main>
    </>
  );
}
