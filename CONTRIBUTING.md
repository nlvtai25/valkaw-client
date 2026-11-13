# Contributing to Valkaw

Thanks for wanting to help. Valkaw is one project with three moving parts:

- **Client** — Fabric mod source in `1.21.11/valkaw-client/`, `26.1.2/valkaw-client/`, `26.2/valkaw-client/`, `26.3/valkaw-client/`.
- **Launcher** — Go + Wails in `valkaw-launcher/` (Linux) and `valkaw-launcher-win/` (Windows), shared core.
- **Website** — React + Vite in `Website/`.

## Before you start

Open an issue first to discuss what you want to change. Small fixes (typos, docs) can go straight to a PR.

## How to build

### Client

```sh
./scripts/build-version.sh 1.21.11 --profiles all
```

Artifacts land in `version/`.

### Launcher

```sh
cd valkaw-launcher && ./build.sh     # Linux
cd ../valkaw-launcher-win && ./build.sh   # Windows
```

Or build everything at once:

```sh
./scripts/build-all.sh
```

## Code style

- Java: follow the existing conventions in each snapshot; run `./gradlew spotless` if configured.
- Go: `gofmt -w .` and `go vet ./...` before committing.
- Frontend (launcher + website): Prettier + ESLint defaults.

## Commits

Use conventional commits where practical: `feat:`, `fix:`, `docs:`, `build:`, `chore:`.

## Pull requests

1. Fork, create a branch from `main`.
2. Build locally and confirm nothing breaks.
3. Open a PR describing what changed and why.
4. Wait for review; address review comments.

## License

By contributing, you agree your contributions are licensed under Apache 2.0 (see `LICENSE`).
