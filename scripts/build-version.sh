#!/usr/bin/env bash
# Build one or more version snapshots and drop the artifacts into ../version/
# using the project-wide naming scheme: valkaw-<profile>-<game-ver>-<client-ver>.jar
#
# Usage:
#   ./scripts/build-version.sh 1.21.11
#   ./scripts/build-version.sh 26.2 --profiles sodium vulkan
#   ./scripts/build-version.sh 26.1.2 1.21.11 --profiles all
#   ./scripts/build-version.sh 1.21.11 --profiles all --clean
#
# Run from anywhere; paths are resolved relative to this file.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/version"
# Prefer JAVA_HOME, then the repo's usual JDK location, then whatever is on PATH.
if [[ -z "${JAVA_HOME:-}" && -d "$HOME/.local/share/jdk-25" ]]; then
	export JAVA_HOME="$HOME/.local/share/jdk-25"
fi

usage() {
	sed -n '2,11p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
	exit "${1:-0}"
}

VERSIONS=()
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
		--*) echo "unknown argument: $1" >&2; usage 1 ;;
		*) VERSIONS+=("$1"); shift ;;
	esac
done

[[ ${#VERSIONS[@]} -ge 1 ]] || usage 1

build_one() {
	local GAME_VER="$1"
	local PROJECT="$ROOT/$GAME_VER/valkaw-client"
	if [[ ! -d "$PROJECT" ]]; then
		echo "error: no snapshot for Minecraft $GAME_VER at $PROJECT" >&2
		echo "       available: $(cd "$ROOT" && ls -d [0-9]*/ 2>/dev/null | tr -d '/' | tr '\n' ' ')" >&2
		return 1
	fi
	if [[ ! -f "$PROJECT/gradle.properties" ]]; then
		echo "error: $PROJECT/gradle.properties missing — snapshot not populated" >&2
		return 1
	fi

	# Resolve profiles the same way as before: no flag -> sodium only; "all" ->
	# sodium plus vulkan when build.gradle actually handles it; otherwise use the
	# explicit list as-is.
	local PF=()
	if [[ ${#PROFILES[@]} -eq 0 ]]; then
		PF=(sodium)
	elif [[ ${PROFILES[0]} == "all" ]]; then
		PF=(sodium)
		# Snapshots without a Vulkan build (26.3) reject the variant.
		if grep -q 'valkawVariant == "vulkan"' "$PROJECT/build.gradle"; then
			PF+=(vulkan)
		fi
	else
		PF=("${PROFILES[@]}")
	fi

	local CLIENT_VER
	CLIENT_VER="$(sed -n 's/^version=//p' "$PROJECT/gradle.properties" | head -1)"
	[[ -n "$CLIENT_VER" ]] || { echo "error: no version= in gradle.properties" >&2; return 1; }

	# 1.21.x uses the remapping plugin and ships via `remapJar`; 26.x compiles against
	# the unobfuscated jar and only needs `jar`. Detect the plugin id, not the task name:
	# both build scripts mention `remapJar` in comments.
	local TASK
	if grep -q 'fabric-loom-remap' "$PROJECT/build.gradle" 2>/dev/null; then
		TASK="remapJar"
	else
		TASK="jar"
	fi

	mkdir -p "$OUT"
	echo "==> $GAME_VER · client $CLIENT_VER · task $TASK · profiles ${PF[*]}"

	for profile in "${PF[@]}"; do
		if [[ $CLEAN -eq 1 ]]; then
			( cd "$PROJECT" && ./gradlew clean >/dev/null )
		fi

		echo "==> building $profile"
		( cd "$PROJECT" && ./gradlew "$TASK" -Pvalkaw_variant="$profile" )

		local src="$PROJECT/build/libs/valkawclient-$CLIENT_VER-$profile.jar"
		if [[ ! -f "$src" ]]; then
			echo "error: expected artifact not found: $src" >&2
			return 1
		fi

		local dest="$OUT/valkaw-$profile-$GAME_VER-$CLIENT_VER.jar"
		cp -f "$src" "$dest"
		printf '    %s  (%s)\n' "$(basename "$dest")" "$(du -h "$dest" | cut -f1)"
	done
}

for v in "${VERSIONS[@]}"; do
	build_one "$v"
done

echo "==> artifacts in $OUT"
ls -1 "$OUT"/*.jar 2>/dev/null | sed 's|^.*/|    |' || true
