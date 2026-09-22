#!/usr/bin/env bash
# Turns this base into a new SDK project.
#
#   ./scripts/rename-project.sh --group com.acme --namespace com.acme.paykit --name PayKit
#
# Rewrites the Maven group, the root package/namespace, the Gradle root project name and the
# resource prefix, then renames the source directories to match. Run it on a clean tree.
set -euo pipefail
cd "$(dirname "$0")/.."

OLD_GROUP="io.github.thanhng224"
OLD_NS="io.github.thanhng224.sdkbase"
OLD_NAME="AndroidSdkBase"

NEW_GROUP=""; NEW_NS=""; NEW_NAME=""
while [ $# -gt 0 ]; do
  case "$1" in
    --group) NEW_GROUP="$2"; shift 2 ;;
    --namespace) NEW_NS="$2"; shift 2 ;;
    --name) NEW_NAME="$2"; shift 2 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done

[ -n "$NEW_GROUP" ] && [ -n "$NEW_NS" ] && [ -n "$NEW_NAME" ] || {
  echo "usage: $0 --group <maven.group> --namespace <root.package> --name <ProjectName>" >&2
  exit 2
}

if [ -n "$(git status --porcelain)" ]; then
  echo "refusing to run on a dirty tree — commit or stash first" >&2
  exit 1
fi

echo "==> Rewriting identifiers in text files"
# Order matters: the namespace contains the group, so replace the longer string first.
while IFS= read -r file; do
  perl -pi -e "s/\Q$OLD_NS\E/$NEW_NS/g; s/\Q$OLD_GROUP\E/$NEW_GROUP/g; s/\Q$OLD_NAME\E/$NEW_NAME/g" "$file"
done < <(git ls-files | grep -E '\.(kt|kts|java|xml|toml|properties|md|pro|sh|json|yml)$')

echo "==> Moving source directories"
OLD_PATH="$(echo "$OLD_NS" | tr '.' '/')"
NEW_PATH="$(echo "$NEW_NS" | tr '.' '/')"
while IFS= read -r dir; do
  target="${dir/$OLD_PATH/$NEW_PATH}"
  [ "$dir" = "$target" ] && continue
  mkdir -p "$(dirname "$target")"
  git mv "$dir" "$target" 2>/dev/null || mv "$dir" "$target"
done < <(find . -type d -path "*/$OLD_PATH" | sort -r)

echo "==> Removing now-empty parent directories"
find . -type d -empty -not -path './.git/*' -delete

echo "==> Discarding the base project's ABI baselines (a new project starts its own contract)"
find . -name '*.api' -path '*/api/*' -delete

echo
echo "Done. Next:"
echo "  1. ./gradlew clean check"
echo "  2. ./gradlew apiDump   # record YOUR project's first baseline"
echo "  3. review gradle/libs.versions.toml and docs/ for leftover references"
