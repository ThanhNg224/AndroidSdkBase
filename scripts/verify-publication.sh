#!/usr/bin/env bash
# Publishes every artifact to a local file repo, then builds verification/consumer against it.
# Nothing is published to Maven Central here. This is the gate that must pass BEFORE any real release.
set -euo pipefail
cd "$(dirname "$0")/.."

REPO_DIR="$PWD/build/local-repo"

# The consumer is a SEPARATE Gradle build with no local.properties of its own (that file is
# gitignored, by design). It therefore cannot find the Android SDK unless we tell it. Take the
# location from this project's own local.properties, falling back to an already-exported
# ANDROID_HOME, so this script works in a clean shell and in CI without manual setup.
if [ -z "${ANDROID_HOME:-}" ] && [ -f local.properties ]; then
  ANDROID_HOME="$(grep -E '^sdk\.dir=' local.properties | head -1 | cut -d= -f2-)"
fi
if [ -z "${ANDROID_HOME:-}" ] && [ -d "$HOME/Library/Android/sdk" ]; then
  ANDROID_HOME="$HOME/Library/Android/sdk"
fi
if [ -z "${ANDROID_HOME:-}" ] || [ ! -d "$ANDROID_HOME" ]; then
  echo "FAIL cannot locate the Android SDK. Set ANDROID_HOME, or put sdk.dir=... in local.properties." >&2
  exit 1
fi
export ANDROID_HOME
echo "==> Using Android SDK at $ANDROID_HOME"
rm -rf "$REPO_DIR"

echo "==> Publishing to $REPO_DIR"
./gradlew publishAllPublicationsToLocalTestRepository --no-configuration-cache

echo "==> Artifacts produced"
find "$REPO_DIR" -name '*.aar' -o -name '*.jar' | sed "s|$REPO_DIR/||" | sort

echo "==> Checking every artifact has sources and javadoc (Maven Central requires both)"
status=0
while IFS= read -r pom; do
  base="${pom%.pom}"
  for suffix in -sources.jar -javadoc.jar; do
    if ! ls "${base}${suffix}" >/dev/null 2>&1; then
      echo "FAIL missing ${base##*/}${suffix}"
      status=1
    fi
  done
done < <(find "$REPO_DIR" -name '*.pom' ! -name '*maven-metadata*')

echo "==> Building the external consumer against the published artifacts"
( cd verification/consumer && ./gradlew assembleDebug -PsdkLocalRepo="$REPO_DIR" --no-daemon )

echo "==> Verifying the Kotlin metadata floor"
./scripts/verify-kotlin-metadata.sh

exit "$status"
