#!/usr/bin/env bash
# Scaffolds a new, registered, green feature module under sdk/features/<name>.
#
#   ./scripts/new-feature.sh face-match
set -euo pipefail
cd "$(dirname "$0")/.."

NAME="${1:-}"
if [ -z "$NAME" ]; then
  echo "usage: $0 <name>   (e.g. 'face-match')" >&2
  exit 2
fi
if ! [[ "$NAME" =~ ^[a-z][a-z0-9]*(-[a-z0-9]+)*$ ]]; then
  echo "refusing: '$NAME' is not a valid feature name (expected e.g. 'face-match')" >&2
  exit 2
fi
IFS='-' read -r -a SEGMENTS <<< "$NAME"
for segment in "${SEGMENTS[@]}"; do
  if [ "$segment" = "ui" ]; then
    echo "refusing: '$NAME' contains a 'ui' segment, reserved for <feature>-ui-compose modules" >&2
    exit 2
  fi
done

DIR="sdk/features/$NAME"
if [ -e "$DIR" ]; then
  echo "refusing: $DIR already exists" >&2
  exit 2
fi
TOPO=gradle/module-topology.gradle.kts
MODULE_PATH=":sdk:features:$NAME"
if grep -Fq "\"$MODULE_PATH\"" "$TOPO"; then
  echo "refusing: $MODULE_PATH is already registered in $TOPO" >&2
  exit 2
fi
if [ -n "$(git status --porcelain -- "$TOPO")" ]; then
  echo "refusing: $TOPO has uncommitted changes — commit or stash it first" >&2
  exit 2
fi

CORE_NAMESPACE="$(sed -n 's/^ *namespace = "\(.*\)\.core"$/\1/p' sdk/core/build.gradle.kts | head -n1)"
PROJECT_NAME="$(sed -n 's/^rootProject\.name = "\(.*\)"$/\1/p' settings.gradle.kts | head -n1)"
if [ -z "$CORE_NAMESPACE" ] || [ -z "$PROJECT_NAME" ]; then
  echo "refusing: could not infer the project namespace or name" >&2
  exit 2
fi
JOINED="${NAME//-/}"
KOTLIN_KEYWORDS="as break class continue do else false for fun if in interface is null object package return super this throw true try typealias typeof val var when while"
for kw in $KOTLIN_KEYWORDS; do
  if [ "$JOINED" = "$kw" ]; then
    echo "refusing: '$NAME' becomes Kotlin keyword package segment '$JOINED'" >&2
    exit 2
  fi
done
NEW_NAMESPACE="$CORE_NAMESPACE.$JOINED"
while IFS= read -r -d '' file; do
  if grep -qF "namespace = \"$NEW_NAMESPACE\"" "$file"; then
    echo "refusing: '$NAME' collides with namespace $NEW_NAMESPACE in $file" >&2
    exit 2
  fi
done < <(find sdk -name build.gradle.kts -print0)

echo "==> Generating $DIR"
python3 scripts/render-feature.py "$NAME" "$CORE_NAMESPACE" "$PROJECT_NAME"
python3 scripts/register-module.py feature "$MODULE_PATH" published

echo "==> Recording the initial ABI baseline"
./gradlew ":sdk:features:$NAME:apiDump" -q

cat <<MSG

Done. $DIR is registered and green.

Next steps:
  1. Implement the generated gateway/config for this feature's host calls.
  2. Choose an unused 3xxx range for feature business errors.
  3. Add session state and operations, then implement them through SessionScope.
  4. Add demo usage and run ./gradlew check -Psdkbase.warningsAsErrors=true.
MSG
