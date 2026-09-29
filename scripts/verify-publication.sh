#!/usr/bin/env bash
# Local-only publication and consumer checks. Default mode runs every check.
set -euo pipefail
cd "$(dirname "$0")/.."

REPO_DIR="$PWD/build/local-repo"
NS="io.github.thanhng224.sdkbase"
COMPILER="2.2.10"
MODE="${1:-all}"
MODULE="${2:-}"

case "$MODE" in
  all|--publish-only|--poms|--floor)
    if [ "$#" -gt 1 ]; then echo "usage: $0 [--current|--publish-only|--poms|--floor|--consumer-check MODULE|--consumer-r8 MODULE]" >&2; exit 2; fi ;;
  --current)
    if [ "$#" -ne 1 ]; then echo "usage: $0 [--current]" >&2; exit 2; fi
    COMPILER="$(sed -n 's/^kotlin = "\(.*\)"$/\1/p' gradle/libs.versions.toml)" ;;
  --consumer-check|--consumer-r8)
    if [ "$#" -ne 2 ]; then echo "usage: $0 $MODE {app|headless|logging}" >&2; exit 2; fi ;;
  *) echo "usage: $0 [--current|--publish-only|--poms|--floor|--consumer-check MODULE|--consumer-r8 MODULE]" >&2; exit 2 ;;
esac
if [[ "$MODE" == --consumer-check || "$MODE" == --consumer-r8 ]]; then
  case "$MODULE" in app|headless|logging) ;; *) echo "unknown consumer: $MODULE" >&2; exit 2 ;; esac
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

if [[ "$MODE" == --consumer-check || "$MODE" == --consumer-r8 ]]; then
  if [ ! -d "$REPO_DIR" ]; then echo "FAIL local SDK repository is missing; run --publish-only first." >&2; exit 1; fi
else
  echo "==> Publishing to $REPO_DIR"
  rm -rf "$REPO_DIR"
  ./gradlew publishAllPublicationsToLocalTestRepository --no-configuration-cache -q
fi

FLOOR="$(grep -E '^kotlinStdlibFloor' gradle/libs.versions.toml | sed -E 's/.*"(.*)".*/\1/')"

consumer_gradle() {
  ( cd verification/consumer && ./gradlew "$@" -PconsumerKotlin="$COMPILER" \
      -PsdkStdlibFloor="$FLOOR" -PsdkLocalRepo="$REPO_DIR" -q )
}

check_poms() {
  local status=0
  # A vendor binary has no Maven coordinate, and an adapter built on one is host-owned: neither may
  # be published.
  local leaked
  leaked="$(find "$REPO_DIR" -path '*fake-sms*' -print -quit)"
  if [ -n "$leaked" ]; then echo "FAIL a vendor module or its adapter was published: $leaked"; status=1; fi
  echo "==> Checking POMs (kotlin-stdlib must be $FLOOR)"
  while IFS= read -r pom; do
    local base="${pom%.pom}" name
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
      if [ ! -f "${base}${suffix}" ]; then echo "FAIL missing ${name}${suffix}"; status=1; fi
    done
    if [ "$stdlib" != "$FLOOR" ]; then echo "FAIL $name declares kotlin-stdlib $stdlib, expected $FLOOR"; status=1; fi
  done < <(find "$REPO_DIR" -name '*.pom')
  return "$status"
}

check_r8() {
  local module="$1" mapping="verification/consumer/$1/build/outputs/mapping/release/mapping.txt"
  if [ ! -f "$mapping" ]; then echo "FAIL $module R8 mapping file is missing; release minification may be disabled"; return 1; fi
  echo "==> Checking $module R8 kept SDK code"
  local packages="core otp"
  if [ "$module" = logging ]; then packages="core eventlogging logging.file eventlogging.work"; fi
  if [ "$module" = app ]; then packages="$packages otp.ui ui"; fi
  for pkg in $packages; do
    if ! grep -Eq "^${NS//./\\.}\\.${pkg//./\\.}\\.[A-Za-z]" "$mapping"; then
      echo "FAIL R8 kept no classes from $NS.$pkg"; return 1
    fi
  done
  if [ "$module" = logging ]; then
    local worker="$NS.eventlogging.work.internal.EventLoggingWorker"
    if ! grep -Fxq "$worker -> $worker:" "$mapping"; then echo "FAIL logging R8 did not preserve the reflective worker name"; return 1; fi
  fi
}

case "$MODE" in
  --publish-only) exit 0 ;;
  --poms) check_poms; exit $? ;;
  --floor)
    echo "==> Compiling consumers with Kotlin $COMPILER and stdlib $FLOOR"
    consumer_gradle :app:compileReleaseKotlin :logging:compileReleaseKotlin
    exit $? ;;
  --consumer-check)
    echo "==> Checking $MODULE consumer runtime contracts"
    consumer_gradle ":$MODULE:verifyRuntimeContracts"
    exit $? ;;
  --consumer-r8)
    rm -f "verification/consumer/$MODULE/build/outputs/mapping/release/mapping.txt"
    echo "==> Building $MODULE consumer with R8"
    consumer_gradle ":$MODULE:verifyRuntimeContracts" ":$MODULE:assembleRelease"
    check_r8 "$MODULE"
    exit $? ;;
esac

status=0
check_poms || status=1
for module in app headless logging; do
  rm -f "verification/consumer/$module/build/outputs/mapping/release/mapping.txt"
done

echo "==> Building external consumers (compiler $COMPILER, stdlib $FLOOR, release, R8)"
consumer_gradle :app:verifyRuntimeContracts :headless:verifyRuntimeContracts :logging:verifyRuntimeContracts \
  :app:assembleRelease :headless:assembleRelease :logging:assembleRelease \
  || { echo "FAIL the external consumers did not build or violated a runtime contract"; status=1; }

for module in app headless logging; do check_r8 "$module" || status=1; done
[ "$status" -eq 0 ] && echo "Publication verified."
exit "$status"
