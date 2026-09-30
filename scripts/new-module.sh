#!/usr/bin/env bash
# Scaffolds a registered Compose UI, adapter, or composition module.
#
#   ./scripts/new-module.sh --zone ui <feature-name>
#   ./scripts/new-module.sh --zone adapter <name>
#   ./scripts/new-module.sh --zone composition <name>
set -euo pipefail
cd "$(dirname "$0")/.."

ZONE=""
while [ $# -gt 0 ]; do
  case "$1" in
    --zone)
      [ -z "$ZONE" ] && [ $# -ge 2 ] || { echo "usage: $0 --zone ui|adapter|composition <name>" >&2; exit 2; }
      ZONE="$2"
      shift 2
      ;;
    *) break ;;
  esac
done
NAME="${1:-}"
[ $# -eq 1 ] && [ -n "$ZONE" ] || { echo "usage: $0 --zone ui|adapter|composition <name>" >&2; exit 2; }
case "$ZONE" in
  ui|adapter|composition) ;;
  *) echo "refusing: unsupported zone '$ZONE' (expected ui, adapter, or composition)" >&2; exit 2 ;;
esac
if ! [[ "$NAME" =~ ^[a-z][a-z0-9]*(-[a-z0-9]+)*$ ]]; then
  echo "refusing: '$NAME' is not a valid name (expected e.g. 'face-match')" >&2
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

TOPO=gradle/module-topology.gradle.kts
CORE_NAMESPACE="$(sed -n 's/^ *namespace = "\(.*\)\.core"$/\1/p' sdk/core/build.gradle.kts | head -n1)"
PROJECT_NAME="$(sed -n 's/^rootProject\.name = "\(.*\)"$/\1/p' settings.gradle.kts | head -n1)"
if [ -z "$CORE_NAMESPACE" ] || [ -z "$PROJECT_NAME" ]; then
  echo "refusing: could not infer the project namespace or name" >&2
  exit 2
fi

case "$ZONE" in
  ui)
    OWNER="$NAME"
    FEATURE_DIR="sdk/features/$OWNER"
    if [ ! -f "$FEATURE_DIR/build.gradle.kts" ] || ! grep -Fq '":sdk:features:'"$OWNER"'"' "$TOPO"; then
      echo "refusing: '$OWNER' is not a registered feature with a build.gradle.kts" >&2
      exit 2
    fi
    FEATURE_NAMESPACE="$(sed -n 's/^ *namespace = "\(.*\)"$/\1/p' "$FEATURE_DIR/build.gradle.kts" | head -n1)"
    if [ -z "$FEATURE_NAMESPACE" ]; then
      echo "refusing: could not read the namespace for feature '$OWNER'" >&2
      exit 2
    fi
    MODULE_NAME="$OWNER-ui-compose"
    MODULE_DIR="sdk/features/$MODULE_NAME"
    TOPO_ZONE=feature
    NAMESPACE="$FEATURE_NAMESPACE.ui"
    ;;
  composition|adapter)
    OWNER=""
    MODULE_NAME="$NAME"
    if [ "$ZONE" = composition ]; then
      MODULE_DIR="sdk/composition/$MODULE_NAME"
      NAMESPACE="$CORE_NAMESPACE.$JOINED"
      TOPO_ZONE=composition
    else
      MODULE_DIR="sdk/adapters/$MODULE_NAME"
      NAMESPACE="$CORE_NAMESPACE.$JOINED"
      TOPO_ZONE=adapter
    fi
    ;;
esac
if [ -e "$MODULE_DIR" ]; then
  echo "refusing: $MODULE_DIR already exists" >&2
  exit 2
fi
MODULE_PATH=":${MODULE_DIR//\//:}"
if grep -Fq "\"$MODULE_PATH\"" "$TOPO"; then
  echo "refusing: $MODULE_PATH is already registered in $TOPO" >&2
  exit 2
fi
while IFS= read -r -d '' file; do
  if grep -qF "namespace = \"$NAMESPACE\"" "$file"; then
    echo "refusing: '$MODULE_NAME' collides with namespace $NAMESPACE in $file" >&2
    exit 2
  fi
done < <(find sdk -name build.gradle.kts -print0)

# The renderer writes only the new module; registry registration appends to one existing zone list.
# This allows a feature scaffold and its optional UI module to be generated in one working tree.
python3 scripts/render-module.py "$ZONE" "$MODULE_DIR" "$MODULE_NAME" "$NAMESPACE" "$PROJECT_NAME" "$OWNER"
python3 scripts/register-module.py "$TOPO_ZONE" "$MODULE_PATH" published
./gradlew ":${MODULE_DIR//\//:}:apiDump" -q

echo "Done. $MODULE_DIR is registered and has an ABI baseline."
