# Valkaw Client — website

Marketing site for [Valkaw Client](https://github.com/nlvtai25/valkaw-client), a Fabric client for Minecraft 1.21.11. Static output, no backend.

## Stack

React 19, Vite 7, TypeScript, Tailwind CSS v4 (preflight plus a few utilities). No component library: the tokens, layout and controls live in `src/index.css`.

## Design

The site follows the client's own register: a graphite ground in three steps, hairline panels instead of cards, Host Grotesk for text and Atkinson Hyperlegible Mono for anything a machine measured (version, loader, size, hash, date). Amber marks the one control a person presses; cool blue marks a reading.

Design tokens are the first block in `src/index.css`. The file opens with the build stamp: theme, hero posture, the waiver on the hero, and the pre-emit critique.

## Run locally

```bash
npm install
npm run dev      # http://localhost:5173
npm run build    # dist/
npm run preview  # serve dist/
```

`npm run build` runs `tsc -b` first, so type errors fail the build.

## What is here

- `/` — hero with the build bench, the screenshot deck, six module rows, the two renderers, a statement, three questions.
- `/features` — all 40 features (38 modules plus the HUD and crosshair editors), filterable by kind and expandable, plus a comparison table.
- `/download` — Sodium and VulkanMod jars, SHA256, install steps, launcher, requirements, changelog.

Release data, module copy and screenshot captions live in `src/data/content.ts`. Change a version, file name or checksum there and every page follows.

Launcher builds are not hardcoded: `src/lib/launcher.ts` reads `assets/downloads/latest.json` from the repository at runtime, so publishing a launcher update needs no site edit. `FALLBACK_BUILDS` in the same file is the copy shown when that request fails, and it does need editing on every release.

Images in `public/shots/` were captured from the running client, not mocked up. The launcher capture comes from the app itself.

## Deploy

GitHub Pages builds this folder from `.github/workflows/deploy-website.yml`: any push that touches `Website/` runs `npm ci` and `npm run build`, then publishes `dist/`. The workflow sets `VITE_BASE=/valkaw-client/` so asset paths and the router prefix match the project URL, and copies `index.html` to `404.html`, because Pages has no rewrite rules and a deep link such as `/features` has to boot the app.

For any other static host, upload `dist/` and rewrite unknown paths to `index.html`. `public/_redirects` covers Netlify and Cloudflare Pages.

## Keeping the site in sync

When a client or launcher release is published, update this site in the same pull or follow-up commit and let the workflow deploy it:

1. **Client release** — bump `DOWNLOADS` in `src/data/content.ts` (version, file names, size, SHA256, release date, changelog for both builds) and the version strings in `Navbar.tsx`, `Features.tsx` and `Download.tsx`. The jar download buttons stay pointed at `releases/latest/download/`, so after the entries change no URL edit is needed.
2. **Launcher release** — the site reads `assets/downloads/latest.json` at runtime (see `src/lib/launcher.ts`), but `FALLBACK_BUILDS` in that file still has to be bumped by hand.
3. **Counts** — module totals live in `STATS`, `BRAND.description`, the hero strings in `Index.tsx` and the meta descriptions in `index.html`; grep for the old number before pushing.
4. Check `CHANGELOG.md` first, then `npm run build`, then push. A push that touches anything under `Website/` redeploys automatically — run the workflow manually (Actions → Deploy website → Run workflow) if the deploy failed.

So: launcher or client update → always update the site data → deploy follows on push. Do not leave the site naming an older release than the repository.

## License

Apache 2.0. Minecraft is a trademark of Mojang Studios. This project is not affiliated with Mojang or Microsoft.
