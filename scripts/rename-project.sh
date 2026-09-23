#!/usr/bin/env bash
# Turns this base into a new SDK project. Run on a clean tree, then review `git diff`.
#
#   ./scripts/rename-project.sh --group com.acme --namespace com.acme.paykit --name PayKit \
#     --developer-id acme --developer-name "Acme Inc." --developer-url https://acme.com \
#     --repo-url https://github.com/acme/paykit
set -euo pipefail
cd "$(dirname "$0")/.."

OLD_GROUP="io.github.thanhng224"
OLD_NS="io.github.thanhng224.sdkbase"
OLD_NAME="AndroidSdkBase"

NEW_GROUP="" NEW_NS="" NEW_NAME="" DEV_ID="" DEV_NAME="" DEV_URL="" REPO_URL=""
while [ $# -gt 0 ]; do
  case "$1" in
    --group) NEW_GROUP="$2"; shift 2 ;;
    --namespace) NEW_NS="$2"; shift 2 ;;
    --name) NEW_NAME="$2"; shift 2 ;;
    --developer-id) DEV_ID="$2"; shift 2 ;;
    --developer-name) DEV_NAME="$2"; shift 2 ;;
    --developer-url) DEV_URL="$2"; shift 2 ;;
    --repo-url) REPO_URL="$2"; shift 2 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done
for v in NEW_GROUP NEW_NS NEW_NAME DEV_ID DEV_NAME DEV_URL REPO_URL; do
  [ -n "${!v}" ] || { sed -n '2,7p' "$0" >&2; exit 2; }
done
[ -z "$(git status --porcelain)" ] || { echo "refusing to run on a dirty tree" >&2; exit 1; }

# Docs and scripts also spell the namespace/group as a slash-separated source path
# (e.g. `io/github/thanhng224/sdkbase/...`), not just the dotted package form.
OLD_NS_PATH="${OLD_NS//.//}" NEW_NS_PATH="${NEW_NS//.//}"
OLD_GROUP_PATH="${OLD_GROUP//.//}" NEW_GROUP_PATH="${NEW_GROUP//.//}"

echo "==> Rewriting identifiers"
# Replace the longer string first in each form (dotted, then slash-separated), so a shorter
# prefix (the group) never clobbers part of a longer match (the namespace) first.
git ls-files | grep -E '\.(kt|kts|java|xml|toml|properties|md|pro|sh|json|yml)$' | while IFS= read -r f; do
  OLD_NS="$OLD_NS" NEW_NS="$NEW_NS" OLD_NS_PATH="$OLD_NS_PATH" NEW_NS_PATH="$NEW_NS_PATH" \
    OLD_GROUP="$OLD_GROUP" NEW_GROUP="$NEW_GROUP" OLD_GROUP_PATH="$OLD_GROUP_PATH" NEW_GROUP_PATH="$NEW_GROUP_PATH" \
    OLD_NAME="$OLD_NAME" NEW_NAME="$NEW_NAME" \
    perl -pi -e 's/\Q$ENV{OLD_NS}\E/$ENV{NEW_NS}/g; s/\Q$ENV{OLD_NS_PATH}\E/$ENV{NEW_NS_PATH}/g; s/\Q$ENV{OLD_GROUP}\E/$ENV{NEW_GROUP}/g; s/\Q$ENV{OLD_GROUP_PATH}\E/$ENV{NEW_GROUP_PATH}/g; s/\Q$ENV{OLD_NAME}\E/$ENV{NEW_NAME}/g' "$f"
done

echo "==> Writing POM identity and NOTICE"
python3 - "$DEV_ID" "$DEV_NAME" "$DEV_URL" "$REPO_URL" "$NEW_NAME" <<'EOF'
import re, sys, datetime
dev_id, dev_name, dev_url, repo_url, name = sys.argv[1:6]
p = "gradle.properties"
t = open(p).read()
for key, value in {"url": repo_url, "developerId": dev_id, "developerName": dev_name,
                   "developerUrl": dev_url, "inceptionYear": str(datetime.date.today().year)}.items():
    t = re.sub(rf"^sdkbase\.pom\.{key}=.*$", f"sdkbase.pom.{key}={value}", t, flags=re.M)
open(p, "w").write(t)
# Replace the name and copyright lines; keep the license paragraph that follows them.
notice = open("NOTICE").read().split("\n", 2)
rest = notice[2] if len(notice) > 2 else "\n"
open("NOTICE", "w").write(f"{name}\nCopyright {datetime.date.today().year} {dev_name}\n{rest}")
EOF

echo "==> Moving source directories"
find . -type d -path "*/$OLD_NS_PATH" -not -path '*/build/*' -not -path './.git/*' | sort -r | while IFS= read -r dir; do
  target="${dir/$OLD_NS_PATH/$NEW_NS_PATH}"
  mkdir -p "$(dirname "$target")"
  git mv "$dir" "$target"
done
find . -type d -empty -not -path './.git/*' -delete

echo "==> Recording the new project's ABI baselines"
./gradlew apiDump -q

echo
echo "Done. Next: ./gradlew check && ./scripts/verify-publication.sh, then review git diff."
