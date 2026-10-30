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

# Evergreen WebView2 bootstrapper, embedded in the exe and run silently on first
# start when the runtime is missing. Gitignored: it is a download, not source.
mkdir -p webview2
if [ ! -s webview2/MicrosoftEdgeWebView2Setup.exe ]; then
  curl -fsSL "https://go.microsoft.com/fwlink/p/?LinkId=2124703" -o webview2/MicrosoftEdgeWebView2Setup.exe
fi

wails build -platform windows/amd64 -o valkaw-launcher-win.exe "$@"
