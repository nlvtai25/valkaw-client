#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
export PATH="$HOME/go/bin:$PATH"
wails build -tags webkit2_41 "$@"
