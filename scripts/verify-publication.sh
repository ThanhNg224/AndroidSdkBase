#!/usr/bin/env bash
# Local-only publication gate (nothing goes to Maven Central):
#   1. publish every artifact to build/local-repo
#   2. every POM: sources + javadoc jars present (except the BOM) and kotlin-stdlib pinned to the floor
#   3. build headless + UI + logging consumers on the floor compiler (or --current), release + R8
#   4. R8 output still contains classes from every published Android artifact
set -euo pipefail
cd "$(dirname "$0")/.."
REPO_DIR="$PWD/build/local-repo"
NS="io.github.thanhng224.sdkbase"
COMPILER="2.2.10"
if [ "${1:-}" = "--current" ] && [ "$#" -eq 1 ]; then
  COMPILER="$(sed -n 's/^kotlin = "\(.*\)"$/\1/p' gradle/libs.versions.toml)"
elif [ "$#" -ne 0 ]; then
  echo "usage: $0 [--current]" >&2
  exit 2
fi

if [ -z "${ANDROID_HOME:-}" ] && [ -f local.properties ]; then
  ANDROID_HOME="$(grep -E '^sdk\.dir=' local.properties | head -1 | cut -d= -f2-)"
fi
if [ -z "${ANDROID_HOME:-}" ] && [ -d "$HOME/Library/Android/sdk" ]; then
  ANDROID_HOME="$HOME/Library/Android/sdk"
fi
if [ -z "${ANDROID_HOME:-}" ] || [ ! -d "$ANDROID_HOME" ]; then
  echo "FAIL cannot locate the Android SDK. Set ANDROID_HOME, or sdk.dir in local.properties." >&2
  exit 1
fi
export ANDROID_HOME

status=0
fail() { echo "FAIL $*"; status=1; }

echo "==> Publishing to $REPO_DIR"
rm -rf "$REPO_DIR"
./gradlew publishAllPublicationsToLocalTestRepository --no-configuration-cache -q

FLOOR="$(grep -E '^kotlinStdlibFloor' gradle/libs.versions.toml | sed -E 's/.*"(.*)".*/\1/')"
echo "==> Checking POMs (kotlin-stdlib must be $FLOOR)"
while IFS= read -r pom; do
  base="${pom%.pom}"
  name="$(basename "$base")"
  read -r packaging stdlib < <(python3 - "$pom" <<'EOF'
import re, sys
text = open(sys.argv[1]).read()
packaging = (re.search(r"<packaging>([^<]+)</packaging>", text) or [None, "jar"])[1]
stdlib = "-"
for dep in re.findall(r"<dependency>(.*?)</dependency>", text, re.S):
    if re.search(r"<artifactId>kotlin-stdlib</artifactId>", dep):
        version = re.search(r"<version>([^<]+)</version>", dep)
        stdlib = version.group(1) if version else "unversioned"
print(packaging, stdlib)
EOF
)
  if [ "$packaging" = "pom" ]; then continue; fi
  for suffix in -sources.jar -javadoc.jar; do
    [ -f "${base}${suffix}" ] || fail "missing ${name}${suffix}"
  done
  [ "$stdlib" = "$FLOOR" ] || fail "$name declares kotlin-stdlib $stdlib, expected $FLOOR"
done < <(find "$REPO_DIR" -name '*.pom')

for module in app headless logging; do
  rm -f "verification/consumer/$module/build/outputs/mapping/release/mapping.txt"
done

echo "==> Building external consumers (compiler $COMPILER, stdlib $FLOOR, release, R8)"
( cd verification/consumer && ./gradlew :app:verifyRuntimeContracts :headless:verifyRuntimeContracts :logging:verifyRuntimeContracts \
    :app:assembleRelease :headless:assembleRelease :logging:assembleRelease -PconsumerKotlin="$COMPILER" \
    -PsdkStdlibFloor="$FLOOR" -PsdkLocalRepo="$REPO_DIR" --no-daemon -q ) \
  || fail "the external consumers did not build or violated a runtime contract"

for module in app headless logging; do
  MAPPING="verification/consumer/$module/build/outputs/mapping/release/mapping.txt"
  if [ ! -f "$MAPPING" ]; then
    fail "$module R8 mapping file is missing; release minification may be disabled"
    continue
  fi
  echo "==> Checking $module R8 kept SDK code"
  packages="core otp"
  if [ "$module" = "logging" ]; then packages="core eventlogging logging.file eventlogging.work"; fi
  if [ "$module" = "app" ]; then packages="$packages otp.ui"; fi
  for pkg in $packages; do
    grep -Eq "^${NS//./\\.}\.${pkg//./\\.}\.[A-Za-z]" "$MAPPING" || fail "R8 kept no classes from $NS.$pkg"
  done
  if [ "$module" = "logging" ]; then
    worker="$NS.eventlogging.work.internal.EventLoggingWorker"
    grep -Fxq "$worker -> $worker:" "$MAPPING" || fail "logging R8 did not preserve the reflective worker name"
  fi
done

[ "$status" -eq 0 ] && echo "Publication verified."
exit "$status"
