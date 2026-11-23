#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
# Wails links GTK/WebKit through cgo, so the Linux binary can only be built on
# Linux: CI (ubuntu runner) or WSL. On Windows use ../valkaw-launcher-win/build.sh.
[ "$(uname -s)" = Linux ] || {
  echo "error: build the Linux launcher on Linux (CI or WSL), not $(uname -s)" >&2
  exit 1
}
export PATH="$HOME/go/bin:$PATH"
wails build -tags webkit2_41 "$@"
