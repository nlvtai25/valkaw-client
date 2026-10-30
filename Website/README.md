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
- `/features` — all 37 features (35 modules plus the HUD and crosshair editors), filterable by kind and expandable, plus a comparison table.
- `/download` — Sodium and VulkanMod jars, SHA256, install steps, launcher, requirements, changelog.

Release data, module copy and screenshot captions live in `src/data/content.ts`. Change a version, file name or checksum there and every page follows.

Launcher builds are not hardcoded: `src/lib/launcher.ts` reads `assets/downloads/latest.json` from the repository at runtime, so publishing a launcher update needs no site edit. `FALLBACK_BUILDS` in the same file is the copy shown when that request fails, and it does need editing on every release.

Images in `public/shots/` were captured from the running client, not mocked up. The launcher capture comes from the app itself.

## Deploy

Upload `dist/` to any static host. The app uses client-side routing, so rewrite unknown paths to `index.html`:

```text
/*  /index.html  200
```

A `_redirects` file for Netlify/Cloudflare Pages and a `404.html` fallback are included in `public/`.

## License

Apache 2.0. Minecraft is a trademark of Mojang Studios. This project is not affiliated with Mojang or Microsoft.
