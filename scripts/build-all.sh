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
    -h|--help) sed -n "2,14p" "${BASH_SOURCE[0]}" | sed "s/^# \{0,1\}//"; exit 0 ;;
    *) echo "unknown argument: $1" >&2; exit 1 ;;
  esac
done

if [[ $CLIENTS -eq 1 ]]; then
  echo "==> building client snapshots"
  for ver in 1.21.11 26.1.2 26.2 26.3; do
    if [[ -d "$ROOT/$ver/valkaw-client" ]]; then
      "$ROOT/scripts/build-version.sh" "$ver" --profiles all
    else
      echo "   (skip $ver: no snapshot)"
    fi
  done
fi

if [[ $LAUNCHERS -eq 1 ]]; then
  echo "==> building launcher (Linux)"
  ( cd "$ROOT/valkaw-launcher" && ./build.sh )
  echo "==> building launcher (Windows)"
  ( cd "$ROOT/valkaw-launcher-win" && ./build.sh )
fi

echo "==> done"
