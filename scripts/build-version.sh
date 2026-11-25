#!/usr/bin/env bash
# Build one version snapshot and drop the artifact into ../version/ using the
# project-wide naming scheme: valkaw-<profile>-<game-ver>-<client-ver>.jar
#
# Usage:
#   ./scripts/build-version.sh 1.21.11
#   ./scripts/build-version.sh 26.2 --profiles sodium vulkan
#   ./scripts/build-version.sh 1.21.11 --profiles all --clean
#
# Run from anywhere; paths are resolved relative to this file.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/version"

# Windows (Git Bash / MSYS / Cygwin) vs POSIX.
IS_WINDOWS=0
case "$(uname -s)" in
	MINGW*|MSYS*|CYGWIN*) IS_WINDOWS=1 ;;
esac

# Prefer JAVA_HOME, then the repo's usual JDK location, then a Windows JDK.
if [[ -z "${JAVA_HOME:-}" ]]; then
	if [[ -d "$HOME/.local/share/jdk-25" ]]; then
		export JAVA_HOME="$HOME/.local/share/jdk-25"
	elif [[ $IS_WINDOWS -eq 1 ]]; then
		# Locate an installed JDK (Temurin/JDK 25 preferred) under standard roots.
		for root in "${PROGRAMFILES:-}" "$LOCALAPPDATA/Programs"; do
			[[ -n "$root" ]] || continue
			for d in "$root"/Eclipse\ Adoptium/* "$root"/Java/* "$root"/Microsoft/*; do
				case "$d" in
					*jdk-25*|*jdk-21*|*jdk*) ;;
					*) continue ;;
				esac
				if [[ -d "$d/bin" ]]; then
					export JAVA_HOME="$d"
					break 2
				fi
			done
		done
	fi
fi

# Use the platform's Gradle wrapper: gradlew on POSIX, gradlew.bat on Windows.
if [[ $IS_WINDOWS -eq 1 ]]; then
	GRADLEW=(cmd //c gradlew.bat)
else
	GRADLEW=(./gradlew)
fi

usage() {
	sed -n '2,9p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
	exit "${1:-0}"
}

[[ $# -ge 1 ]] || usage 1
case "$1" in -h|--help) usage 0 ;; esac

GAME_VER="$1"; shift
PROFILES=()
CLEAN=0

while [[ $# -gt 0 ]]; do
	case "$1" in
		--profiles)
			shift
			while [[ $# -gt 0 && "$1" != --* ]]; do PROFILES+=("$1"); shift; done
			;;
		--clean) CLEAN=1; shift ;;
		-h|--help) usage 0 ;;
		*) echo "unknown argument: $1" >&2; usage 1 ;;
	esac
done

PROJECT="$ROOT/$GAME_VER/valkaw-client"
if [[ ! -d "$PROJECT" ]]; then
	echo "error: no snapshot for Minecraft $GAME_VER at $PROJECT" >&2
	echo "       available: $(cd "$ROOT" && ls -d [0-9]*/ 2>/dev/null | tr -d '/' | tr '\n' ' ')" >&2
	exit 1
fi
if [[ ! -f "$PROJECT/gradle.properties" ]]; then
	echo "error: $PROJECT/gradle.properties missing — snapshot not populated" >&2
	exit 1
fi

if [[ ${#PROFILES[@]} -eq 0 ]]; then
	PROFILES=(sodium)
elif [[ ${PROFILES[0]} == "all" ]]; then
	PROFILES=(sodium)
	# Snapshots without a Vulkan build (26.3) reject the variant, so only add it
	# when build.gradle actually handles it.
	if grep -q 'valkawVariant == "vulkan"' "$PROJECT/build.gradle"; then
		PROFILES+=(vulkan)
	fi
fi

CLIENT_VER="$(sed -n 's/^version=//p' "$PROJECT/gradle.properties" | head -1)"
[[ -n "$CLIENT_VER" ]] || { echo "error: no version= in gradle.properties" >&2; exit 1; }

# 1.21.x uses the remapping plugin and ships via `remapJar`; 26.x compiles against
# the unobfuscated jar and only needs `jar`. Detect the plugin id, not the task name:
# both build scripts mention `remapJar` in comments.
if grep -q 'fabric-loom-remap' "$PROJECT/build.gradle" 2>/dev/null; then
	TASK="remapJar"
else
	TASK="jar"
fi

mkdir -p "$OUT"
echo "==> $GAME_VER · client $CLIENT_VER · task $TASK · profiles ${PROFILES[*]}"

for profile in "${PROFILES[@]}"; do
	if [[ $CLEAN -eq 1 ]]; then
		( cd "$PROJECT" && "${GRADLEW[@]}" clean >/dev/null )
	fi

	echo "==> building $profile"
	( cd "$PROJECT" && "${GRADLEW[@]}" "$TASK" -Pvalkaw_variant="$profile" )

	src="$PROJECT/build/libs/valkawclient-$CLIENT_VER-$profile.jar"
	if [[ ! -f "$src" ]]; then
		echo "error: expected artifact not found: $src" >&2
		exit 1
	fi

	dest="$OUT/valkaw-$profile-$GAME_VER-$CLIENT_VER.jar"
	cp -f "$src" "$dest"
	printf '    %s  (%s)\n' "$(basename "$dest")" "$(du -h "$dest" | cut -f1)"
done

echo "==> artifacts in $OUT"
ls -1 "$OUT"/*.jar 2>/dev/null | sed 's|^.*/|    |' || true
