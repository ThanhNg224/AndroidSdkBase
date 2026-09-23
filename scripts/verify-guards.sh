#!/usr/bin/env bash
# Proves each build guard can fail. Every case applies ONE real violation to a scratch copy of the
# working tree and expects its gate to fail with a specific message. A guard that cannot fail is
# worse than no guard, because people trust it. Never touches the real working tree.
#
#   ./scripts/verify-guards.sh           # every case
#   ./scripts/verify-guards.sh abi-      # only cases whose name starts with "abi-"
set -euo pipefail
cd "$(dirname "$0")/.."
SRC="$PWD"
WORK="$SRC/build/guard-selftest"
LOGS="$SRC/build/guard-logs"
FILTER="${1:-}"
mkdir -p "$WORK" "$LOGS"
failures=0

sync_tree() {
  # Restores the scratch copy to the current working tree; keeps its build outputs and caches.
  rsync -a --delete --exclude 'build/' --exclude '.gradle/' --exclude '.kotlin/' --exclude '.git/' \
    "$SRC/" "$WORK/"
}

add_dep() { # FILE LINE — inserts LINE at the top of the last `dependencies {` block in FILE
  python3 - "$1" "$2" <<'EOF'
import sys
path, line = sys.argv[1], sys.argv[2]
text = open(path).read()
i = text.rindex("dependencies {") + len("dependencies {")
open(path, "w").write(text[:i] + "\n    " + line + text[i:])
EOF
}

edit() { # FILE OLD NEW — replaces the first OLD with NEW; fails if OLD is absent
  python3 - "$1" "$2" "$3" <<'EOF'
import sys
path, old, new = sys.argv[1:4]
text = open(path).read()
if old not in text:
    sys.exit(f"edit: pattern not found in {path}: {old!r}")
open(path, "w").write(text.replace(old, new, 1))
EOF
}

run_case() { # NAME MUTATE GATE EXPECT_REGEX
  local name="$1" mutate="$2" gate="$3" expect="$4" log="$LOGS/$1.log"
  [[ "$name" == "$FILTER"* ]] || return 0
  sync_tree
  if ! ( cd "$WORK" && eval "$mutate" ) >"$log" 2>&1; then
    echo "ERROR $name — the mutation itself failed (see $log)"; failures=$((failures + 1)); return 0
  fi
  if ( cd "$WORK" && eval "$gate" ) >>"$log" 2>&1; then
    echo "FAIL  $name — the gate PASSED on a real violation (see $log)"; failures=$((failures + 1))
  elif grep -Eq "$expect" "$log"; then
    echo "ok    $name"
  else
    echo "FAIL  $name — the gate failed for an unrelated reason (see $log)"; failures=$((failures + 1))
  fi
}

# Control: the unmodified tree must pass, otherwise every case "fails" for the wrong reason.
sync_tree
( cd "$WORK" && ./gradlew help -q ) >"$LOGS/control.log" 2>&1 \
  || { echo "ERROR the unmodified tree does not configure (see $LOGS/control.log)"; exit 1; }

TOPO=gradle/module-topology.gradle.kts
ROGUE='mkdir -p sdk/features/rogue/src/main/kotlin/rogue &&
  printf "plugins { id(\"sdkbase.android.library\") }\nandroid { namespace = \"rogue\" }\n" > sdk/features/rogue/build.gradle.kts &&
  printf "package rogue\n\npublic object Rogue\n" > sdk/features/rogue/src/main/kotlin/rogue/Rogue.kt &&
  printf "\ninclude(\":sdk:features:rogue\")\n" >> settings.gradle.kts'

# --- Zone guard ------------------------------------------------------------------------------
run_case zone-core-to-feature-implementation \
  "add_dep sdk/core/build.gradle.kts 'implementation(project(\":sdk:features:otp\"))'" \
  "./gradlew help -q" ":sdk:core \[core\] -> :sdk:features:otp"
run_case zone-core-to-feature-compileOnly \
  "add_dep sdk/core/build.gradle.kts 'compileOnly(project(\":sdk:features:otp\"))'" \
  "./gradlew help -q" ":sdk:core \[core\] -> :sdk:features:otp"
run_case zone-core-to-feature-runtimeOnly \
  "add_dep sdk/core/build.gradle.kts 'runtimeOnly(project(\":sdk:features:otp\"))'" \
  "./gradlew help -q" ":sdk:core \[core\] -> :sdk:features:otp"
run_case zone-feature-to-app-releaseImplementation \
  "add_dep sdk/features/otp/build.gradle.kts '\"releaseImplementation\"(project(\":apps:demo\"))'" \
  "./gradlew help -q" ":sdk:features:otp \[feature\] -> :apps:demo \[app\]"
run_case zone-unregistered-leaf-module "$ROGUE" \
  "./gradlew help -q" ":sdk:features:rogue is not registered"
run_case zone-published-depends-on-unpublished \
  "$ROGUE && edit $TOPO '\"feature\" to listOf(' '\"feature\" to listOf(\":sdk:features:rogue\", ' &&
   add_dep sdk/features/otp/build.gradle.kts 'implementation(project(\":sdk:features:rogue\"))'" \
  "./gradlew help -q" ":sdk:features:otp is published but depends on unpublished :sdk:features:rogue"
run_case zone-registered-but-not-included \
  "edit $TOPO '\"feature\" to listOf(' '\"feature\" to listOf(\":sdk:features:ghost\", '" \
  "./gradlew help -q" ":sdk:features:ghost is registered but not included"

# --- ABI (exact match: any change to the public surface fails until the dump is regenerated) ----
CORE=sdk/core/src/main/kotlin/io/github/thanhng224/sdkbase/core
OTP=sdk/features/otp/src/main/kotlin/io/github/thanhng224/sdkbase/otp
ABI_CORE='Public ABI of :sdk:core differs'
run_case abi-delete-signature \
  "edit $CORE/SdkErrors.kt 'public fun notStarted(): SdkError =' 'internal fun notStarted(): SdkError ='" \
  "./gradlew :sdk:core:apiCheck -q" "$ABI_CORE"
run_case abi-move-toplevel-function \
  "python3 - <<'EOF'
d='$CORE/'
t=open(d+'SdkLogger.kt').read(); i=t.index('/**\n * Masks all')
open(d+'SdkLogger.kt','w').write(t[:i]+'/** Added API. */\npublic fun noOpLogger(): SdkLogger = SdkLogger.NoOp\n')
open(d+'Redact.kt','w').write('package io.github.thanhng224.sdkbase.core\n\n'+t[i:])
EOF" \
  "./gradlew :sdk:core:apiCheck -q" "$ABI_CORE"
run_case abi-add-abstract-to-host-interface \
  "edit $CORE/SdkLogger.kt '    public fun info(tag: String, message: String)
' '    public fun info(tag: String, message: String)

    public fun warn(tag: String, message: String)
' && edit $CORE/SdkLogger.kt '            override fun info(tag: String, message: String): Unit = Unit
' '            override fun info(tag: String, message: String): Unit = Unit
            override fun warn(tag: String, message: String): Unit = Unit
'" \
  "./gradlew :sdk:core:apiCheck -q" "$ABI_CORE"
run_case abi-add-sealed-subtype \
  "edit $CORE/SdkResult.kt '    public data class Failure' '    public data object Pending : SdkResult<Nothing>

    public data class Failure'" \
  "./gradlew :sdk:core:apiCheck -q" "$ABI_CORE"
run_case abi-android-module-addition \
  "printf 'package io.github.thanhng224.sdkbase.otp\n\npublic fun OtpState.isTerminal(): Boolean = phase == OtpState.Phase.Verified\n' > $OTP/OtpStateExt.kt" \
  "./gradlew :sdk:features:otp:apiCheck -q" "Public ABI of :sdk:features:otp differs"
run_case abi-garbage-in-baseline \
  "printf 'garbage line\n' >> sdk/features/otp/api/otp.api" \
  "./gradlew :sdk:features:otp:apiCheck -q" "Public ABI of :sdk:features:otp differs"
run_case abi-missing-baseline \
  "rm sdk/features/otp-ui-compose/api/otp-ui-compose.api" \
  "./gradlew :sdk:features:otp-ui-compose:apiCheck -q" "Missing ABI baseline"

# --- Cases appended by later tasks go above this line ------------------------------------------

if [ "$failures" -gt 0 ]; then
  echo "$failures guard case(s) did not behave. Logs: $LOGS"
  exit 1
fi
echo "All guard cases failed their gate for the right reason."
