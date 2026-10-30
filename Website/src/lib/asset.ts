/**
 * Resolve a file from `public/` against the deploy base.
 *
 * A root-relative path like "/shots/menu.webp" breaks when the site is served
 * from a subpath (GitHub Pages uses /valkaw-client/), so every runtime asset
 * reference goes through here.
 */
export function asset(path: string): string {
  return `${import.meta.env.BASE_URL}${path.replace(/^\//, '')}`;
}
