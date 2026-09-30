#!/usr/bin/env bash
# Verify composition + optional callback adapter from published Maven coordinates in an isolated copy.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

status_before="$(git status --porcelain=v1 --untracked-files=all)"
source_digest() {
  python3 - "$ROOT" <<'PY'
import hashlib, os, subprocess, sys
root = sys.argv[1]
paths = subprocess.check_output(
    ["git", "-C", root, "ls-files", "--cached", "--others", "--exclude-standard", "-z"]
).split(b"\0")
digest = hashlib.sha256()
for raw in sorted(p for p in paths if p):
    rel = os.fsdecode(raw)
    if rel == "build" or rel.startswith("build/"):
        continue
    full = os.path.join(root, rel)
    digest.update(raw + b"\0")
    if os.path.islink(full):
        digest.update(b"link\0" + os.fsencode(os.readlink(full)))
    elif os.path.isfile(full):
        with open(full, "rb") as source:
            for chunk in iter(lambda: source.read(1024 * 1024), b""):
                digest.update(chunk)
    else:
        digest.update(b"missing\0")
print(digest.hexdigest())
PY
}
digest_before="$(source_digest)"
mkdir -p "$ROOT/build"
WORK="$(mktemp -d "$ROOT/build/integration-proof.XXXXXX")"
cleanup() {
  result=$?
  trap - EXIT
  cd "$ROOT"
  rm -rf "$WORK"
  status_after="$(git status --porcelain=v1 --untracked-files=all)"
  digest_after="$(source_digest)"
  if [[ "$status_after" != "$status_before" || "$digest_after" != "$digest_before" ]]; then
    echo "FAIL source tree changed while integration verification ran; existing edits were preserved" >&2
    result=1
  fi
  exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

if [[ -z "${ANDROID_HOME:-}" ]]; then
  if [[ -f "$ROOT/local.properties" ]]; then
    ANDROID_HOME="$(sed -n 's/^sdk\.dir=//p' "$ROOT/local.properties" | head -n1)"
  elif [[ -d "$HOME/Library/Android/sdk" ]]; then
    ANDROID_HOME="$HOME/Library/Android/sdk"
  fi
fi
if [[ -z "${ANDROID_HOME:-}" || ! -d "$ANDROID_HOME" ]]; then
  echo "FAIL set ANDROID_HOME or sdk.dir in local.properties" >&2
  exit 1
fi
export ANDROID_HOME

SOURCE="$WORK/source"
mkdir -p "$SOURCE"
command -v rsync >/dev/null || { echo "FAIL rsync is required to copy the source tree safely" >&2; exit 1; }
rsync -a --exclude='/.git/' --exclude='**/.git/**' --exclude='/.gradle/***' --exclude='**/.gradle/***' --exclude='/.kotlin/***' --exclude='**/.kotlin/***' --exclude='/build/***' --exclude='**/build/***' \
  "$ROOT/" "$SOURCE/"

CORE_NAMESPACE="$(sed -n 's/^ *namespace = "\(.*\)\.core"$/\1/p' "$SOURCE/sdk/core/build.gradle.kts" | head -n1)"
[[ -n "$CORE_NAMESPACE" ]] || { echo "FAIL cannot derive core namespace" >&2; exit 1; }
SDK_GROUP="$(sed -n 's/^sdkbase\.group=//p' "$SOURCE/gradle.properties" | head -n1)"
SDK_VERSION="$(sed -n 's/^sdkbase\.version=//p' "$SOURCE/gradle.properties" | head -n1)"
AGP_VERSION="$(sed -n 's/^agp = "\(.*\)"$/\1/p' "$SOURCE/gradle/libs.versions.toml" | head -n1)"
KOTLIN_VERSION="$(sed -n 's/^kotlin = "\(.*\)"$/\1/p' "$SOURCE/gradle/libs.versions.toml" | head -n1)"
COMPILE_SDK="$(sed -n 's/^compileSdk = "\(.*\)"$/\1/p' "$SOURCE/gradle/libs.versions.toml" | head -n1)"
MIN_SDK="$(sed -n 's/^minSdk = "\(.*\)"$/\1/p' "$SOURCE/gradle/libs.versions.toml" | head -n1)"
KOTLIN_STDLIB_FLOOR="$(sed -n 's/^kotlinStdlibFloor = "\(.*\)"$/\1/p' "$SOURCE/gradle/libs.versions.toml" | head -n1)"
for value in SDK_GROUP SDK_VERSION AGP_VERSION KOTLIN_VERSION COMPILE_SDK MIN_SDK KOTLIN_STDLIB_FLOOR; do
  [[ -n "${!value}" ]] || { echo "FAIL could not derive $value from the base" >&2; exit 1; }
done

copy_fixture() {
  local from="$1" to="$2"
  mkdir -p "$(dirname "$SOURCE/$to")"
  cp -R "$SOURCE/verification/integration-fixtures/$from" "$SOURCE/$to"
}
copy_fixture profile sdk/features/profile
copy_fixture onboarding sdk/composition/onboarding
copy_fixture profile-callback sdk/adapters/profile-callback

python3 - "$SOURCE" "$CORE_NAMESPACE" "$SDK_GROUP" "$SDK_VERSION" "$AGP_VERSION" "$KOTLIN_VERSION" "$COMPILE_SDK" "$MIN_SDK" "$KOTLIN_STDLIB_FLOOR" <<'PY'
from pathlib import Path
import sys
root = Path(sys.argv[1])
values = {
    "{{SDK_NAMESPACE}}": sys.argv[2], "{{SDK_GROUP}}": sys.argv[3],
    "{{SDK_VERSION}}": sys.argv[4], "{{AGP_VERSION}}": sys.argv[5],
    "{{KOTLIN_VERSION}}": sys.argv[6], "{{COMPILE_SDK}}": sys.argv[7],
    "{{MIN_SDK}}": sys.argv[8], "{{KOTLIN_STDLIB_FLOOR}}": sys.argv[9],
}
for folder in (root / "sdk/features/profile", root / "sdk/composition/onboarding",
               root / "sdk/adapters/profile-callback", root / "verification/integration-fixtures/consumer"):
    for path in folder.rglob("*"):
        if not path.is_file():
            continue
        try:
            text = path.read_text()
        except UnicodeDecodeError:
            continue
        for token, value in values.items():
            text = text.replace(token, value)
        path.write_text(text)
PY

# Settings derives included modules from this registry after their build files exist.
python3 - "$SOURCE/gradle/module-topology.gradle.kts" <<'PY'
from pathlib import Path
import re, sys
topology = Path(sys.argv[1])
t = topology.read_text()

def append_to_list(text, header, item):
    """Appends `item` to the Kotlin listOf(...) that follows `header`, whatever it already holds."""
    match = re.search(re.escape(header) + r"listOf(?:<String>)?\(", text)
    if match is None:
        raise SystemExit(f"FAIL topology has no list after {header.strip()}")
    depth, i = 1, match.end()
    while depth:
        depth += {"(": 1, ")": -1}.get(text[i], 0)
        i += 1
    close = i - 1
    body = text[match.end():close].strip()
    quoted = f'"{item}"'
    if not body:
        return text[:match.start()] + f"{header}listOf({quoted})" + text[close + 1:]
    inner = text[match.end():close]
    if "\n" not in inner:  # single-line list
        return text[:close] + f", {quoted}" + text[close:]
    indent = re.search(r"\n( *)\S", inner).group(1)
    head = text[:close].rstrip()
    return head + f"\n{indent}{quoted}," + text[len(head):]

t = append_to_list(t, '"feature" to ', ":sdk:features:profile")
t = append_to_list(t, '"composition" to ', ":sdk:composition:onboarding")
t = append_to_list(t, '"adapter" to ', ":sdk:adapters:profile-callback")
for item in (":sdk:features:profile", ":sdk:composition:onboarding", ":sdk:adapters:profile-callback"):
    t = append_to_list(t, 'extra["publishedArtifacts"] = ', item)
topology.write_text(t)
PY

cd "$SOURCE"
FIXTURE_KOTLIN="2.2.10"
echo "==> Dumping ABI baselines for fixture modules in isolated copy"
./gradlew :sdk:features:profile:apiDump :sdk:composition:onboarding:apiDump :sdk:adapters:profile-callback:apiDump \
  -Psdkbase.warningsAsErrors=true --no-configuration-cache --no-daemon -q
echo "==> Running fixture checks and publishing to isolated Maven repository"
./gradlew :sdk:features:profile:check :sdk:composition:onboarding:check :sdk:adapters:profile-callback:check \
  publishAllPublicationsToLocalTestRepository -Psdkbase.warningsAsErrors=true --no-configuration-cache --no-daemon -q

REPO="$SOURCE/build/local-repo"
[[ -d "$REPO" ]] || { echo "FAIL fixture publication repository missing" >&2; exit 1; }
CONSUMER="$SOURCE/verification/integration-fixtures/consumer"
for variant in direct adapter; do
  rm -f "$CONSUMER/$variant/build/outputs/mapping/release/mapping.txt"
done

echo "==> Building direct and adapter Maven-coordinate consumers with release R8"
./gradlew -p "$CONSUMER" :direct:verifyFixtureRuntime :direct:assembleRelease \
  :adapter:verifyFixtureRuntime :adapter:assembleRelease \
  -PfixtureRepo="$REPO" -PfixtureKotlin="$FIXTURE_KOTLIN" -PfixtureStdlib="$KOTLIN_STDLIB_FLOOR" \
  --no-configuration-cache --no-daemon -q

python3 - "$CONSUMER" "$CORE_NAMESPACE" <<'PY'
from pathlib import Path
import sys
consumer = Path(sys.argv[1])
namespace = sys.argv[2]
expected = {
    "direct": ["core.", "otp.", "profile.", "onboarding."],
    "adapter": ["core.", "otp.", "profile.", "onboarding.", "profilecallback."],
}
for variant, packages in expected.items():
    mapping = consumer / variant / "build/outputs/mapping/release/mapping.txt"
    if not mapping.is_file():
        raise SystemExit(f"FAIL {variant} R8 mapping missing")
    classes = []
    for line in mapping.read_text().splitlines():
        if " -> " in line and line.endswith(":"):
            classes.append(line.split(" -> ", 1)[0])
    for suffix in packages:
        prefix = f"{namespace}.{suffix}"
        if not any(name.startswith(prefix) for name in classes):
            raise SystemExit(f"FAIL {variant} R8 mapping retained no classes under {prefix}")
    print(f"{variant}: fresh R8 mapping retains " + ", ".join(f"{namespace}.{p[:-1]}" for p in packages))
PY

echo "Integration fixture verified in isolated workspace."
