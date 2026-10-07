#!/usr/bin/env bash
# Cut a Reed release that Obtainium can install: tag, build a signed APK, publish a GitHub release.
# Usage: scripts/release.sh 0.2.0
set -euo pipefail

VERSION="${1:?usage: scripts/release.sh <version, e.g. 0.2.0>}"
TAG="v$VERSION"
cd "$(dirname "$0")/.."

[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "Version must look like 1.2.3"; exit 1; }
git diff --quiet && git diff --cached --quiet || { echo "Commit or stash your changes first."; exit 1; }
git rev-parse -q --verify "refs/tags/$TAG" >/dev/null && { echo "$TAG already exists."; exit 1; }
grep -q '^REED_KEYSTORE_FILE=' "$HOME/.gradle/gradle.properties" 2>/dev/null \
  || { echo "Signing key not configured in ~/.gradle/gradle.properties (see CLAUDE.md)."; exit 1; }

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
git tag -a "$TAG" -m "Reed $VERSION"
trap 'git tag -d "$TAG" >/dev/null 2>&1; echo "Release aborted; tag removed."' ERR

./gradlew --quiet :app:assembleRelease
APK="build/reed-$VERSION.apk"
cp app/build/outputs/apk/release/app-release.apk "$APK"

# Refuse to publish anything not signed with the personal key: Obtainium couldn't update over it.
APKSIGNER="$(ls -d "$HOME"/Library/Android/sdk/build-tools/*/apksigner | tail -1)"
"$APKSIGNER" verify --print-certs "$APK" | grep -q "O=Reed (personal)" \
  || { echo "APK is not signed with the Reed key."; false; }

git push --quiet origin "$TAG"
trap - ERR
gh release create "$TAG" "$APK" --title "Reed $VERSION" --generate-notes
echo "Published $TAG. Obtainium will offer it as an update."
