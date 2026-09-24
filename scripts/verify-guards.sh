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

add_constraint() { # FILE LINE — inserts LINE at the top of the last `constraints {` block in FILE
  python3 - "$1" "$2" <<'EOF'
import sys
path, line = sys.argv[1], sys.argv[2]
text = open(path).read()
i = text.rindex("constraints {") + len("constraints {")
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
run_case zone-bom-constraint-to-app \
  "add_constraint sdk/bom/build.gradle.kts 'api(project(\":apps:demo\"))'" \
  "./gradlew help -q" ":sdk:bom \\[bom\\] -> :apps:demo \\[app\\] is not allowed"
run_case zone-registered-but-not-included \
  "edit $TOPO '\"feature\" to listOf(' '\"feature\" to listOf(\":sdk:features:ghost\", '" \
  "./gradlew help -q" ":sdk:features:ghost is registered but not included"

# --- ABI (exact match: any change to the public surface fails until the dump is regenerated) ----
CORE=sdk/core/src/main/kotlin/io/github/thanhng224/sdkbase/core
OTP=sdk/features/otp/src/main/kotlin/io/github/thanhng224/sdkbase/otp
ABI_CORE='Public ABI of :sdk:core differs'
run_case abi-delete-signature \
  "edit $CORE/error/SdkErrors.kt 'public fun notStarted(): SdkError =' 'internal fun notStarted(): SdkError ='" \
  "./gradlew :sdk:core:apiCheck -q" "$ABI_CORE"
run_case abi-move-toplevel-function \
  "python3 - <<'EOF'
d='$CORE/result/'
marker = 'public fun <T> SdkResult<T>.getOrNull(): T? = (this as? SdkResult.Success<T>)?.value\n\n'
t = open(d+'SdkResult.kt').read()
i = t.index(marker)
open(d+'SdkResult.kt', 'w').write(t[:i] + t[i+len(marker):])
open(d+'SdkResultGetOrNull.kt', 'w').write(
    'package io.github.thanhng224.sdkbase.core.result\n\n' + marker
)
EOF" \
  "./gradlew :sdk:core:apiCheck -q" "$ABI_CORE"
run_case abi-add-abstract-to-host-interface \
  "edit $CORE/concurrency/DispatcherProvider.kt '    public val io: CoroutineDispatcher
}' '    public val io: CoroutineDispatcher

    public val unconfined: CoroutineDispatcher
}' && edit $CORE/concurrency/AndroidDispatchers.kt '    override val io: CoroutineDispatcher get() = Dispatchers.IO
}' '    override val io: CoroutineDispatcher get() = Dispatchers.IO
    override val unconfined: CoroutineDispatcher get() = Dispatchers.Unconfined
}'" \
  "./gradlew :sdk:core:apiCheck -q" "$ABI_CORE"
run_case abi-add-sealed-subtype \
  "edit $CORE/error/SdkError.kt '    public class Lifecycle(' '    public class Security(code: Int, reason: String, cause: Throwable? = null) :
        SdkError(code, reason, cause)

    public class Lifecycle('" \
  "./gradlew :sdk:core:apiCheck -q" "$ABI_CORE"
run_case abi-android-module-addition \
  "printf 'package io.github.thanhng224.sdkbase.otp.session\n\npublic fun OtpState.isTerminal(): Boolean = phase == OtpState.Phase.Verified\n' > $OTP/session/OtpStateExt.kt" \
  "./gradlew :sdk:features:otp:apiCheck -q" "Public ABI of :sdk:features:otp differs"
run_case abi-garbage-in-baseline \
  "printf 'garbage line\n' >> sdk/features/otp/api/otp.api" \
  "./gradlew :sdk:features:otp:apiCheck -q" "Public ABI of :sdk:features:otp differs"
run_case abi-missing-baseline \
  "rm sdk/features/otp-ui-compose/api/otp-ui-compose.api" \
  "./gradlew :sdk:features:otp-ui-compose:apiCheck -q" "Missing ABI baseline"

# --- Kotlin floor and R8 canary (slow: each runs the whole publication gate) --------------------
PUB='./scripts/verify-publication.sh'
ACT=verification/consumer/app/src/main/kotlin/io/github/thanhng224/consumer/ConsumerActivity.kt
# Flipping the property ALONE is not a real violation: every convention also declares an explicit
# `"api"(libs.kotlin.stdlib)`, and that pin wins regardless of the property (verified: toggling the
# property with the pin left in place changes nothing in any POM). The real protection is the pin,
# so the mutation removes it too — with the property back on, that is what actually lets AGP's
# built-in Kotlin and KGP fall back to the toolchain's own (unpinned) kotlin-stdlib.
run_case floor-stdlib-unpinned \
  "edit gradle.properties 'kotlin.stdlib.default.dependency=false' 'kotlin.stdlib.default.dependency=true' &&
   edit build-logic/src/main/kotlin/sdkbase.android.library.gradle.kts '\"api\"(libs.kotlin.stdlib)
    ' ''" \
  "$PUB" "declares kotlin-stdlib"
# The `sdkbase.kotlin.jvm` convention no longer exists (every module, including core, is now an
# Android library) — this case alone covers the floor for all of them.
run_case floor-unpin-android-library \
  "edit build-logic/src/main/kotlin/sdkbase.android.library.gradle.kts 'languageVersion.set(floor)' '' &&
   edit build-logic/src/main/kotlin/sdkbase.android.library.gradle.kts 'apiVersion.set(floor)' ''" \
  "$PUB" "compiled with an incompatible version of Kotlin"
run_case r8-canary-unreachable-sdk \
  "edit $ACT '/* SDK_CALLS_BEGIN */' '/* SDK_CALLS_BEGIN' && edit $ACT '/* SDK_CALLS_END */' 'SDK_CALLS_END */'" \
  "$PUB" "R8 kept no classes from"
run_case r8-canary-minify-disabled \
  "edit verification/consumer/app/build.gradle.kts 'isMinifyEnabled = true' 'isMinifyEnabled = false'" \
  "$PUB" "R8 mapping file is missing"

# --- Cases appended by later tasks go above this line ------------------------------------------

if [ "$failures" -gt 0 ]; then
  echo "$failures guard case(s) did not behave. Logs: $LOGS"
  exit 1
fi
echo "All guard cases failed their gate for the right reason."
