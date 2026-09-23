#!/usr/bin/env bash
# Local-only publication gate (nothing goes to Maven Central):
#   1. publish every artifact to build/local-repo
#   2. every POM: sources + javadoc jars present (except the BOM) and kotlin-stdlib pinned to the floor
#   3. build verification/consumer from those coordinates on the floor Kotlin, release + R8
#   4. R8 output still contains classes from every published Android artifact
set -euo pipefail
cd "$(dirname "$0")/.."
REPO_DIR="$PWD/build/local-repo"
NS="io.github.thanhng224.sdkbase"

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

MAPPING="verification/consumer/app/build/outputs/mapping/release/mapping.txt"
rm -f "$MAPPING"

echo "==> Building the external consumer (floor Kotlin, release, R8)"
( cd verification/consumer && ./gradlew assembleRelease -PsdkLocalRepo="$REPO_DIR" --no-daemon -q ) \
  || fail "the external consumer did not build"

if [ ! -f "$MAPPING" ]; then
  fail "R8 mapping file is missing; release minification may be disabled"
else
  echo "==> Checking R8 kept SDK code"
  for pkg in core otp otp.ui; do
    grep -Eq "^${NS//./\\.}\.${pkg//./\\.}\.[A-Za-z]" "$MAPPING" || fail "R8 kept no classes from $NS.$pkg"
  done
fi

[ "$status" -eq 0 ] && echo "Publication verified."
exit "$status"
