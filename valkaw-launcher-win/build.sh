#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
export PATH="$HOME/go/bin:$PATH"

# One frontend for both launchers: build it in valkaw-launcher, then copy the
# bundle here. The HTML, CSS and JS stay in a single place.
cd ../valkaw-launcher/frontend
[ -d node_modules ] || npm install
npm run build
cd ../../valkaw-launcher-win

rm -rf frontend/dist
mkdir -p frontend
cp -r ../valkaw-launcher/frontend/dist frontend/dist

wails build -platform windows/amd64 -o valkaw-launcher-win.exe "$@"
