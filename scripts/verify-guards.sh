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

new_module() { # DIR [PLUGIN] — a minimal library module at DIR (e.g. sdk/features/rogue), included in settings
  local dir="$1" plugin="${2:-sdkbase.android.library}" name="${1##*/}"
  local pkg="rogue.${name//-/_}" extra=""
  # Without the convention plugin nothing sets compileSdk, and AGP refuses to configure the module.
  [[ "$plugin" == sdkbase.* ]] || extra="; compileSdk = libs.versions.compileSdk.get().toInt()"
  mkdir -p "$dir/src/main/kotlin/rogue"
  printf 'plugins { id("%s") }\nandroid { namespace = "%s"%s }\ndependencies {\n}\n' "$plugin" "$pkg" "$extra" > "$dir/build.gradle.kts"
  printf 'package %s\n\npublic object Rogue\n' "$pkg" > "$dir/src/main/kotlin/rogue/Rogue.kt"
  printf '\ninclude(":%s")\n' "${dir//\//:}" >> settings.gradle.kts
}

register() { # ZONE PATH — adds PATH to ZONE in the module registry
  python3 - "$TOPO" "$1" "$2" <<'PY'
import re, sys
path, zone, module = sys.argv[1:4]
text = open(path).read()
new, n = re.subn(r'("%s" to listOf(?:<String>)?\()' % re.escape(zone), r'\1"%s", ' % module, text, count=1)
if n != 1:
    sys.exit(f"register: zone {zone!r} not found in {path}")
open(path, "w").write(new)
PY
}

ROGUE='new_module sdk/features/rogue'

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
  "$ROGUE && register feature :sdk:features:rogue &&
   add_dep sdk/features/otp/build.gradle.kts 'implementation(project(\":sdk:features:rogue\"))'" \
  "./gradlew help -q" ":sdk:features:otp is published but depends on unpublished :sdk:features:rogue"
run_case zone-bom-constraint-to-app \
  "add_constraint sdk/bom/build.gradle.kts 'api(project(\":apps:demo\"))'" \
  "./gradlew help -q" ":sdk:bom \\[bom\\] -> :apps:demo \\[app\\] is not allowed"
run_case zone-registered-but-not-included \
  "register feature :sdk:features:ghost" \
  "./gradlew help -q" ":sdk:features:ghost is registered but not included"
# `otp-extra` shares otp's name prefix but is not its UI module: a prefix match alone must not pass.
run_case zone-feature-to-peer-feature \
  "new_module sdk/features/otp-extra && register feature :sdk:features:otp-extra &&
   add_dep sdk/features/otp-extra/build.gradle.kts 'implementation(project(\":sdk:features:otp\"))'" \
  "./gradlew help -q" ":sdk:features:otp-extra \\[feature\\] -> :sdk:features:otp \\[feature\\] is not allowed: a feature"
run_case zone-feature-to-own-ui-module \
  "add_dep sdk/features/otp/build.gradle.kts 'implementation(project(\":sdk:features:otp-ui-compose\"))'" \
  "./gradlew help -q" ":sdk:features:otp \\[feature\\] -> :sdk:features:otp-ui-compose \\[feature\\] is not allowed: a feature"
run_case zone-feature-to-composition \
  "new_module sdk/composition/rogue && register composition :sdk:composition:rogue &&
   add_dep sdk/features/otp/build.gradle.kts 'implementation(project(\":sdk:composition:rogue\"))'" \
  "./gradlew help -q" ":sdk:features:otp \\[feature\\] -> :sdk:composition:rogue \\[composition\\] is not allowed"
run_case zone-feature-to-adapter \
  "new_module sdk/adapters/rogue && register adapter :sdk:adapters:rogue &&
   add_dep sdk/features/otp/build.gradle.kts 'implementation(project(\":sdk:adapters:rogue\"))'" \
  "./gradlew help -q" ":sdk:features:otp \\[feature\\] -> :sdk:adapters:rogue \\[adapter\\] is not allowed"
run_case zone-policed-module-without-dependency-policy \
  "new_module sdk/composition/rogue com.android.library && register composition :sdk:composition:rogue" \
  "./gradlew help -q" ":sdk:composition:rogue \\[composition\\] has no checkDependencyPolicy"
# The published test kit is only for testImplementation; a main-configuration edge to it is a
# zone violation like any other, even though `:sdk:core-testing` itself is published.
run_case zone-feature-main-to-testing \
  "add_dep sdk/features/otp/build.gradle.kts 'implementation(project(\":sdk:core-testing\"))'" \
  "./gradlew help -q" ":sdk:features:otp \\[feature\\] -> :sdk:core-testing \\[testing\\] is not allowed"

# --- Dependency policy (resolves real coordinates: needs network or a warm Gradle cache) ----------
run_case deps-http-client-in-feature \
  "add_dep sdk/features/otp/build.gradle.kts 'implementation(\"com.squareup.okhttp3:okhttp:4.12.0\")'" \
  "./gradlew :sdk:features:otp:checkDependencyPolicy -q" "releaseRuntimeClasspath: com.squareup.okhttp3:okhttp:4.12.0 \\(HTTP client\\)"
run_case deps-di-compileOnly-in-core \
  "add_dep sdk/core/build.gradle.kts 'compileOnly(\"com.google.dagger:dagger:2.51.1\")'" \
  "./gradlew :sdk:core:checkDependencyPolicy -q" "releaseCompileClasspath: com.google.dagger:dagger:2.51.1 \\(DI framework\\)"
# Reached only through another library: the whole graph is checked, not just declared edges.
run_case deps-transitive-http-client \
  "add_dep sdk/features/otp/build.gradle.kts 'implementation(\"com.squareup.picasso:picasso:2.8\")'" \
  "./gradlew :sdk:features:otp:checkDependencyPolicy -q" "com.squareup.okhttp3:okhttp:[0-9.]+ \\(HTTP client\\) via .*picasso"

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
  "edit $CORE/error/SdkError.kt '    public class Lifecycle @JvmOverloads constructor(' '    public class Security(code: Int, reason: String) :
        SdkError(code, reason, null, false)

    public class Lifecycle @JvmOverloads constructor('" \
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

# --- Source rules (checkSourceRules: GlobalScope, android.util.Log, an owned CoroutineScope in a
# feature/composition module, a public multi-property data class) -------------------------------
run_case source-global-scope-in-feature \
  "edit $OTP/OtpSdk.kt 'public object OtpSdk {' 'public object OtpSdk {

    internal val leaked = GlobalScope'" \
  "./gradlew :sdk:features:otp:checkSourceRules -q" "global-scope"
run_case source-android-log-in-feature \
  "edit $OTP/OtpSdk.kt 'import kotlinx.coroutines.CancellationException' 'import kotlinx.coroutines.CancellationException
import android.util.Log'" \
  "./gradlew :sdk:features:otp:checkSourceRules -q" "android-log"
run_case source-own-coroutine-scope-in-feature \
  "edit $OTP/OtpSdk.kt 'public object OtpSdk {' 'public object OtpSdk {

    internal val extra = CoroutineScope(SupervisorJob())'" \
  "./gradlew :sdk:features:otp:checkSourceRules -q" "own-coroutine-scope"
# LogcatSink.kt is the one file in core where android.util.Log is allowed — a different core file
# must still be flagged (the exception is by file name AND zone, not a blanket core exemption).
run_case source-android-log-in-core-non-logcat-file \
  "printf 'package io.github.thanhng224.sdkbase.core.logging\n\nimport android.util.Log\n' > $CORE/logging/RogueLog.kt" \
  "./gradlew :sdk:core:checkSourceRules -q" "android-log"
run_case source-public-data-class-in-core \
  "printf 'package io.github.thanhng224.sdkbase.core.error\n\npublic data class RogueConfig(public val a: Int, public val b: Int)\n' > $CORE/error/RogueConfig.kt" \
  "./gradlew :sdk:core:checkSourceRules -q" "public-data-class"
run_case source-module-without-rules-task \
  "new_module sdk/composition/rogue com.android.library && register composition :sdk:composition:rogue" \
  "./gradlew help -q" ":sdk:composition:rogue \\[composition\\] has no checkSourceRules"

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

# --- Scaffold (scripts/new-feature.sh) self-test ------------------------------------------------
# Positive checks: the scaffold script and the gate it hands off to must SUCCEED — the opposite of
# every case above, whose gate must FAIL on a real violation. Written by hand rather than through
# run_case, which asserts the gate fails.

if [[ "scaffold-new-feature-is-green" == "$FILTER"* ]]; then
  name=scaffold-new-feature-is-green
  log="$LOGS/$name.log"
  sync_tree
  # sync_tree excludes build/ and .gradle/, so a face-match left behind by an earlier run of this
  # same case keeps its (now stale) build/ dir — rsync's --delete cannot remove a directory it is
  # not allowed to empty — and a stale configuration-cache entry that assumes the convention
  # plugin's generated consumer-rules.pro is still on disk. Clear both so the case is repeatable.
  rm -rf "$WORK/sdk/features/face-match" "$WORK/.gradle/configuration-cache"
  if ( cd "$WORK" && ./scripts/new-feature.sh face-match &&
       ./gradlew check -Psdkbase.warningsAsErrors=true -q ) >"$log" 2>&1; then
    echo "ok    $name"
  else
    echo "FAIL  $name — new-feature.sh face-match, then check, did not both succeed (see $log)"
    failures=$((failures + 1))
  fi
fi

if [[ "scaffold-rejects-bad-names" == "$FILTER"* ]]; then
  name=scaffold-rejects-bad-names
  log="$LOGS/$name.log"
  sync_tree
  ok=true
  # Face_Match: not lowercase/dash-case. otp-ui-x: a "ui" segment, reserved for <feature>-ui-<toolkit>.
  # otp: sdk/features/otp already exists. in: a Kotlin hard keyword as the joined package segment.
  # core: <ns>.core collides with sdk/core's own namespace. Each command sits in an `if`, not bare,
  # so a refusal's non-zero exit (the expected outcome) does not trip this script's own `set -e`.
  for bad in Face_Match otp-ui-x otp in core; do
    if ( cd "$WORK" && ./scripts/new-feature.sh "$bad" ) >>"$log" 2>&1; then
      echo "new-feature.sh '$bad' unexpectedly succeeded" >>"$log"
      ok=false
    else
      status=$?
      if [ "$status" -ne 2 ]; then
        echo "new-feature.sh '$bad' exited $status, expected 2" >>"$log"
        ok=false
      fi
    fi
  done
  if [ -n "$(cd "$WORK" && git status --porcelain -- settings.gradle.kts gradle/module-topology.gradle.kts)" ]; then
    echo "settings.gradle.kts or module-topology.gradle.kts changed despite every name being refused" >>"$log"
    ok=false
  fi
  if $ok; then
    echo "ok    $name"
  else
    echo "FAIL  $name — see $log"
    failures=$((failures + 1))
  fi
fi

# --- Cases appended by later tasks go above this line ------------------------------------------

if [ "$failures" -gt 0 ]; then
  echo "$failures guard case(s) did not behave. Logs: $LOGS"
  exit 1
fi
echo "All guard cases failed their gate for the right reason."
