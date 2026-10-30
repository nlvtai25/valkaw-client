import { Suspense, lazy, useEffect } from 'react';
import { BrowserRouter, Route, Routes, useLocation } from 'react-router-dom';
import { Navbar } from '@/components/layout/Navbar';
import { Footer } from '@/components/layout/Footer';
import Index from './pages/Index';

// The landing page ships in the first chunk; the other routes load on demand.
const Features = lazy(() => import('./pages/Features'));
const Download = lazy(() => import('./pages/Download'));
const NotFound = lazy(() => import('./pages/NotFound'));

/** Jump to the hash target when there is one, otherwise to the top. */
function ScrollManager() {
  const { pathname, hash } = useLocation();

  useEffect(() => {
    if (hash) {
      const el = document.querySelector(hash);
      if (el) {
        el.scrollIntoView({ behavior: 'smooth', block: 'start' });
        return;
      }
    }
    window.scrollTo({ top: 0, left: 0, behavior: 'auto' });
  }, [pathname, hash]);

  return null;
}

function Screen() {
  const { pathname } = useLocation();
  return (
    <div className="route" key={pathname}>
      <Suspense fallback={<div className="route-loading" aria-hidden="true" />}>
        <Routes>
          <Route path="/" element={<Index />} />
          <Route path="/features" element={<Features />} />
          <Route path="/download" element={<Download />} />
          <Route path="*" element={<NotFound />} />
        </Routes>
      </Suspense>
    </div>
  );
}

export default function App() {
  // Vite exposes the deploy prefix (e.g. /valkaw-client/ on GitHub Pages), and
  // the router has to strip it before matching routes.
  const basename = import.meta.env.BASE_URL.replace(/\/$/, '') || '/';

  return (
    <BrowserRouter basename={basename}>
      <ScrollManager />
      <div className="page-sky" aria-hidden="true" />
      <div style={{ position: 'relative', zIndex: 1, display: 'flex', flexDirection: 'column', minHeight: '100svh' }}>
        <Navbar />
        <div style={{ flex: 1 }}>
          <Screen />
        </div>
        <Footer />
      </div>
    </BrowserRouter>
  );
}
