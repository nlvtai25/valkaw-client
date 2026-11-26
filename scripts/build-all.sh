#!/usr/bin/env bash
# Build the whole Valkaw project: all client snapshots + both launcher binaries.
#
# Usage:
#   ./scripts/build-all.sh               # everything
#   ./scripts/build-all.sh --clients     # client jars only
#   ./scripts/build-all.sh --launchers   # launcher binaries only
#
# Run from anywhere; paths are resolved relative to this file.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CLIENTS=1
LAUNCHERS=1

while [[ $# -gt 0 ]]; do
  case "$1" in
    --clients) CLIENTS=1; LAUNCHERS=0; shift ;;
    --launchers) CLIENTS=0; LAUNCHERS=1; shift ;;
    -h|--help) sed -n "2,9p" "${BASH_SOURCE[0]}" | sed "s/^# \{0,1\}//"; exit 0 ;;
    *) echo "unknown argument: $1" >&2; exit 1 ;;
  esac
done

if [[ $CLIENTS -eq 1 ]]; then
  echo "==> building client snapshots"
  # One snapshot per [0-9]*/ directory; no version list to keep in sync.
  for dir in "$ROOT"/[0-9]*/; do
    [[ -d "$dir/valkaw-client" ]] || continue
    "$ROOT/scripts/build-version.sh" "$(basename "$dir")" --profiles all
  done
fi

if [[ $LAUNCHERS -eq 1 ]]; then
  if [[ "$(uname -s)" == Linux ]]; then
    echo "==> building launcher (Linux)"
    ( cd "$ROOT/valkaw-launcher" && ./build.sh )
  else
    echo "   (skip Linux launcher: only buildable on Linux)"
  fi
  echo "==> building launcher (Windows)"
  ( cd "$ROOT/valkaw-launcher-win" && ./build.sh )
fi

echo "==> done"
