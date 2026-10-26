#!/bin/bash
# Builds a signed release APK on this machine and publishes it as a GitHub
# release (same result as the CI workflow, for when Actions is unavailable).
#
#   scripts/release.sh 0.1.0
set -euo pipefail

VERSION="${1:?usage: scripts/release.sh <version, e.g. 0.1.0>}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ -n "$(git status --porcelain)" ]]; then
  echo "Commit your changes first." >&2
  exit 1
fi

export JAVA_HOME="${JAVA_HOME:-$(ls -d "$HOME"/.local/jdk/jdk-17* | head -1)}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
# Commit count keeps versionCode increasing so each release installs as an update
export PIF_VERSION_CODE="$(git rev-list --count HEAD)"
export PIF_VERSION_NAME="$VERSION"

(cd app/jni/mkxp-z && bash make_xxd.sh > /dev/null)
./gradlew --no-daemon -q assembleRelease

APK="$ROOT/app/build/outputs/apk/release/InfiniteFusion-$VERSION-release.apk"
OUT="$ROOT/app/build/InfiniteFusion-$VERSION.apk"
cp "$APK" "$OUT"

git tag "v$VERSION"
git push -q origin "v$VERSION"
gh release create "v$VERSION" "$OUT" --title "v$VERSION" --notes "Built locally from $(git rev-parse --short HEAD)."
