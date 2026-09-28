#!/usr/bin/env bash
# Scaffolds a new, registered, green feature module under sdk/features/<name>.
#
#   ./scripts/new-feature.sh face-match
#
# Namespace and project name are read from the tree (never hardcoded), so this keeps working after
# scripts/rename-project.sh has renamed the base.
set -euo pipefail
cd "$(dirname "$0")/.."

NAME="${1:-}"
if [ -z "$NAME" ]; then
  echo "usage: $0 <name>   (e.g. face-match)" >&2
  exit 2
fi

# Lowercase, dash-separated segments, starting with a letter (e.g. "face-match", "kyc").
if ! [[ "$NAME" =~ ^[a-z][a-z0-9]*(-[a-z0-9]+)*$ ]]; then
  echo "refusing: '$NAME' is not a valid feature name (expected e.g. 'face-match')" >&2
  exit 2
fi
# "-ui-<toolkit>" is reserved for a feature's own UI module (see otp-ui-compose); a feature named
# with a "ui" segment would collide with that convention.
IFS='-' read -r -a SEGMENTS <<< "$NAME"
for segment in "${SEGMENTS[@]}"; do
  if [ "$segment" = "ui" ]; then
    echo "refusing: '$NAME' contains a 'ui' segment, reserved for <feature>-ui-<toolkit> modules" >&2
    exit 2
  fi
done

DIR="sdk/features/$NAME"
if [ -e "$DIR" ]; then
  echo "refusing: $DIR already exists" >&2
  exit 2
fi

SETTINGS=settings.gradle.kts
TOPO=gradle/module-topology.gradle.kts
if [ -n "$(git status --porcelain -- "$SETTINGS" "$TOPO")" ]; then
  echo "refusing: $SETTINGS or $TOPO has uncommitted changes — commit or stash them first" >&2
  exit 2
fi

# The root namespace is whatever sdk/core publishes under, minus its own ".core" suffix — never
# hardcoded, so this still works after rename-project.sh has rewritten it.
CORE_NAMESPACE="$(sed -n 's/^ *namespace = "\(.*\)\.core"$/\1/p' sdk/core/build.gradle.kts | head -n1)"
if [ -z "$CORE_NAMESPACE" ]; then
  echo "refusing: could not read sdk/core's namespace from sdk/core/build.gradle.kts" >&2
  exit 2
fi
PROJECT_NAME="$(sed -n 's/^rootProject\.name = "\(.*\)"$/\1/p' "$SETTINGS" | head -n1)"
if [ -z "$PROJECT_NAME" ]; then
  echo "refusing: could not read rootProject.name from $SETTINGS" >&2
  exit 2
fi

# Dashes are dropped when segments are joined into one package name (e.g. "face-match" ->
# "facematch"); refuse before writing anything if that joined segment cannot compile as one.
JOINED="${NAME//-/}"
KOTLIN_KEYWORDS="as break class continue do else false for fun if in interface is null object package return super this throw true try typealias typeof val var when while"
for kw in $KOTLIN_KEYWORDS; do
  if [ "$JOINED" = "$kw" ]; then
    echo "refusing: '$NAME' becomes package segment '$JOINED', a Kotlin hard keyword" >&2
    exit 2
  fi
done

# <ns>.<joined> must not already be a namespace some other sdk/ module declares (e.g. "core"
# would collide with sdk/core itself) — checked before any file is written.
NEW_NAMESPACE="$CORE_NAMESPACE.$JOINED"
while IFS= read -r -d '' file; do
  if grep -qF "namespace = \"$NEW_NAMESPACE\"" "$file"; then
    echo "refusing: '$NAME' collides with the namespace $NEW_NAMESPACE already declared in $file" >&2
    exit 2
  fi
done < <(find sdk -name build.gradle.kts -print0)

echo "==> Generating $DIR"
NAME="$NAME" CORE_NAMESPACE="$CORE_NAMESPACE" PROJECT_NAME="$PROJECT_NAME" python3 - <<'EOF'
import os

name = os.environ["NAME"]
ns = os.environ["CORE_NAMESPACE"]
project_name = os.environ["PROJECT_NAME"]

segments = name.split("-")
pascal = "".join(s.capitalize() for s in segments)
pkg = f"{ns}.{''.join(segments)}"
pkg_path = pkg.replace(".", "/")

module_dir = f"sdk/features/{name}"
src_main = f"{module_dir}/src/main/kotlin/{pkg_path}"
src_test = f"{module_dir}/src/test/kotlin/{pkg_path}"

os.makedirs(src_main, exist_ok=True)
os.makedirs(f"{src_main}/config", exist_ok=True)
os.makedirs(f"{src_main}/gateway", exist_ok=True)
os.makedirs(f"{src_main}/session", exist_ok=True)
os.makedirs(f"{src_main}/internal", exist_ok=True)
os.makedirs(src_test, exist_ok=True)
os.makedirs(f"{src_test}/config", exist_ok=True)

def write(path, content):
    with open(path, "w") as f:
        f.write(content)

write(f"{module_dir}/build.gradle.kts", f'''plugins {{
    id("sdkbase.android.library")
    id("sdkbase.abi")
    id("sdkbase.publishing")
}}

description = "{project_name} {name} feature"

android {{
    namespace = "{pkg}"
}}

dependencies {{
    api(project(":sdk:core"))
    testImplementation(project(":sdk:core-testing"))
}}
''')

write(f"{src_main}/{pascal}Sdk.kt", f'''package {pkg}

import {ns}.core.call.Cancellable
import {ns}.core.call.ResultCallback
import {ns}.core.call.launchCallback
import {ns}.core.result.SdkResult
import {ns}.core.session.SessionScope
import {ns}.core.session.StateStore
import {pkg}.config.{pascal}SdkConfig
import {pkg}.internal.{pascal}SdkRuntime
import {pkg}.session.{pascal}Session
import {pkg}.session.{pascal}State
import kotlinx.coroutines.CancellationException

/**
 * The only class a host needs to know about. It validates, wires and delegates — and nothing
 * else. No networking, no storage, no global mutable state, no DI container.
 */
public object {pascal}Sdk {{

    @JvmStatic
    public suspend fun start(config: {pascal}SdkConfig): SdkResult<{pascal}Session> {{
        val environment = config.environment
        val sessionLogger = environment.logger.withSession(environment.idGenerator.newId())
        // The one SessionScope the whole session lives on: every coroutine this feature ever
        // starts runs on it — there is no other CoroutineScope anywhere in this module.
        val scope = SessionScope(environment.dispatchers, sessionLogger.tagged("{pascal}Session"))
        val store = StateStore({pascal}State.initial())
        return try {{
            // TODO: replace with real work — call config.gateway through safeCall, drive
            // `store.withLock {{ ... }}` from its result, and on a failure `scope.close()` and return
            // it. Left as a stub so the module is green from the first commit — see
            // `:sdk:features:otp`'s OtpSdk.start for the pattern.
            SdkResult.Success({pascal}SdkRuntime(scope, store, config, sessionLogger))
        }} catch (e: CancellationException) {{
            // The caller was cancelled mid-start: no session is returned to close it, so close the
            // scope here or whatever it already started outlives the call.
            scope.close()
            throw e
        }}
    }}

    /**
     * The Java-callable twin of [start]: no `Continuation`, delivered on the config's
     * [SdkEnvironment][{ns}.core.environment.SdkEnvironment]'s main dispatcher. A session that
     * finishes starting but never gets delivered — the caller cancelled first — is closed instead
     * of leaked.
     */
    @JvmStatic
    public fun start(config: {pascal}SdkConfig, callback: ResultCallback<{pascal}Session>): Cancellable =
        launchCallback(
            dispatchers = config.environment.dispatchers,
            callback = callback,
            onUndelivered = {{ it.close() }},
        ) {{ start(config) }}
}}
''')

write(f"{src_main}/session/{pascal}Session.kt", f'''package {pkg}.session

import {ns}.core.session.SdkSession

/**
 * A running {pascal} flow. The host holds this, renders [state], and closes it when done. `state`,
 * `observeState` and `close` come from [SdkSession] — every feature session extends it instead of
 * redeclaring the three members every session needs. Add operations here as a suspend
 * `fun x(): SdkResult<T>` plus its Java-callable twin `fun x(callback: ResultCallback<T>):
 * Cancellable` — see `:sdk:features:otp`'s OtpSession for the pattern to follow once this feature
 * has calls to make.
 */
public interface {pascal}Session : SdkSession<{pascal}State>
''')

write(f"{src_main}/session/{pascal}State.kt", f'''package {pkg}.session

import {ns}.core.error.SdkError

/** Immutable snapshot of the {pascal} flow. The UI renders this and nothing else. */
public class {pascal}State(
    public val phase: Phase,
    public val error: SdkError?,
) {{
    public enum class Phase {{ Idle, Active, Completed, Failed }}

    /** A plain class, not a `data class` (ABI: `copy`/`componentN` would freeze the property list
     * for every consumer). `internal`: only engine code inside this module needs to derive a new
     * state; a host only ever reads the properties above. */
    internal fun copy(
        phase: Phase = this.phase,
        error: SdkError? = this.error,
    ): {pascal}State = {pascal}State(phase = phase, error = error)

    override fun equals(other: Any?): Boolean {{
        if (this === other) return true
        if (other !is {pascal}State) return false
        return phase == other.phase && error == other.error
    }}

    override fun hashCode(): Int {{
        var result = phase.hashCode()
        result = 31 * result + (error?.hashCode() ?: 0)
        return result
    }}

    override fun toString(): String = "{pascal}State(phase=$phase, error=$error)"

    public companion object {{
        public fun initial(): {pascal}State = {pascal}State(phase = Phase.Idle, error = null)
    }}
}}
''')

write(f"{src_main}/internal/{pascal}SdkRuntime.kt", f'''package {pkg}.internal

import {ns}.core.logging.SdkLogger
import {ns}.core.session.SdkSessionBase
import {ns}.core.session.SessionScope
import {ns}.core.session.StateStore
import {pkg}.config.{pascal}SdkConfig
import {pkg}.session.{pascal}Session
import {pkg}.session.{pascal}State

/**
 * Wires this feature's own work to the public [{pascal}Session] contract, on top of
 * [SdkSessionBase]. Kept Kotlin `internal`. Add operations as `scope.ifOpen {{ ... }}` (suspend),
 * their Java twins as `scope.call(callback) {{ ... }}`, and fire-and-forget work as
 * `scope.launch {{ ... }}` — every one of those routes `close()` racing an operation through
 * [SessionScope] instead of this class re-implementing the guard — see `:sdk:features:otp`'s
 * OtpSdkRuntime for the pattern.
 */
internal class {pascal}SdkRuntime(
    scope: SessionScope,
    store: StateStore<{pascal}State>,
    private val config: {pascal}SdkConfig,
    logger: SdkLogger,
) : SdkSessionBase<{pascal}State>(scope, store), {pascal}Session {{

    private val log = logger.tagged(TAG)

    /** Runs exactly once, the first time [close] actually closes the session (see
     * [SdkSessionBase.close]'s idempotency guarantee) — a legitimate diagnostic, not a
     * workaround: lets a host (or a test) confirm a session actually got released instead of
     * leaked. */
    override fun onClose() {{
        log.d {{ "session closed" }}
    }}

    private companion object {{
        const val TAG = "{pascal}Session"
    }}
}}
''')

write(f"{src_main}/{pascal}Errors.kt", f'''package {pkg}

/**
 * 3xxx business codes owned by this feature; pick an unused block (OTP uses its own).
 */
public object {pascal}Errors
''')

write(f"{src_main}/gateway/{pascal}Gateway.kt", f'''package {pkg}.gateway

/**
 * The host calls this feature needs: suspend + SdkResult, plus a callback twin for Java hosts —
 * see `:sdk:features:otp`'s OtpGateway/OtpCallbackGateway for the pattern to follow once this
 * feature has calls to make.
 */
public interface {pascal}Gateway
''')

write(f"{src_main}/config/{pascal}SdkConfig.kt", f'''package {pkg}.config

import {ns}.core.config.validateConfig
import {ns}.core.environment.SdkEnvironment
import {ns}.core.result.SdkResult
import {pkg}.gateway.{pascal}Gateway

/**
 * Host-supplied configuration. Validated once, here, at the public boundary — never deeper. A
 * builder rather than default arguments because a Java host cannot use Kotlin default arguments.
 */
public class {pascal}SdkConfig private constructor(
    public val gateway: {pascal}Gateway,
    public val environment: SdkEnvironment,
) {{
    public class Builder(private val gateway: {pascal}Gateway) {{
        private var environment: SdkEnvironment = SdkEnvironment.Default

        public fun environment(value: SdkEnvironment): Builder = apply {{ environment = value }}

        public fun build(): SdkResult<{pascal}SdkConfig> = validateConfig {{
            // TODO: add `ensure(...) {{ "..." }}` checks as this feature grows its own config —
            // see `:sdk:features:otp`'s OtpSdkConfig.Builder.build for the pattern.
            {pascal}SdkConfig(gateway = gateway, environment = environment)
        }}
    }}
}}
''')

write(f"{src_test}/config/{pascal}SdkConfigTest.kt", f'''package {pkg}.config

import {ns}.core.testing.assertSuccess
import {pkg}.gateway.{pascal}Gateway
import org.junit.Assert.assertNotNull
import org.junit.Test

class {pascal}SdkConfigTest {{

    private val gateway = object : {pascal}Gateway {{}}

    @Test
    fun buildSucceedsWithDefaults() {{
        val config = {pascal}SdkConfig.Builder(gateway).build().assertSuccess()

        assertNotNull(config)
    }}
}}
''')

write(f"{src_test}/{pascal}SdkTest.kt", f'''package {pkg}

import {ns}.core.environment.SdkEnvironment
import {ns}.core.logging.LogLevel
import {ns}.core.logging.SdkLogger
import {ns}.core.testing.RecordingLogSink
import {ns}.core.testing.TestDispatcherProvider
import {ns}.core.testing.assertSuccess
import {pkg}.config.{pascal}SdkConfig
import {pkg}.gateway.{pascal}Gateway
import {pkg}.session.{pascal}State
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [{pascal}Sdk.start] is business logic, not UI, so it gets a unit test per the repo's testing
 * rule — see `:sdk:features:otp`'s OtpSdkTest for the fuller pattern once this feature has real
 * gateway calls to drive.
 */
class {pascal}SdkTest {{

    private val gateway = object : {pascal}Gateway {{}}

    private fun configOf(logger: SdkLogger = SdkLogger.NoOp): {pascal}SdkConfig =
        {pascal}SdkConfig.Builder(gateway)
            .environment(
                SdkEnvironment.Builder()
                    .logger(logger)
                    .dispatchers(TestDispatcherProvider(StandardTestDispatcher()))
                    .build(),
            )
            .build()
            .assertSuccess()

    @Test
    fun startReturnsIdleSession() = runTest {{
        val session = {pascal}Sdk.start(configOf()).assertSuccess()

        assertEquals({pascal}State.Phase.Idle, session.state.value.phase)
        session.close()
    }}

    @Test
    fun closeIsIdempotent() = runTest {{
        val sink = RecordingLogSink()
        val logger = SdkLogger.Builder().minLevel(LogLevel.DEBUG).sink(sink).build()
        val session = {pascal}Sdk.start(configOf(logger)).assertSuccess()

        session.close()
        session.close()

        assertEquals(1, sink.messages(LogLevel.DEBUG).count {{ it.contains("session closed") }})
    }}
}}
''')
EOF

echo "==> Registering :sdk:features:$NAME"
NAME="$NAME" python3 - "$SETTINGS" "$TOPO" <<'EOF'
import os
import sys

settings_path, topo_path = sys.argv[1:3]
name = os.environ["NAME"]
module = f":sdk:features:{name}"

# settings.gradle.kts: add the include right after the last existing feature include, so repeated
# runs keep growing the same block instead of scattering entries through the file.
text = open(settings_path).read()
lines = text.split("\n")
last = max(i for i, l in enumerate(lines) if l.startswith("include(\":sdk:features:"))
lines.insert(last + 1, f'include("{module}")')
open(settings_path, "w").write("\n".join(lines))

# module-topology.gradle.kts: register in the "feature" zone and in publishedArtifacts.
text = open(topo_path).read()

start = text.index('"feature" to listOf(') + len('"feature" to listOf(')
end = text.index("\n    ),", start)
text = text[:end] + f'\n        "{module}",' + text[end:]

start = text.index('extra["publishedArtifacts"] = listOf(') + len('extra["publishedArtifacts"] = listOf(')
end = text.index("\n)", start)
text = text[:end] + f'\n    "{module}",' + text[end:]

open(topo_path, "w").write(text)
EOF

echo "==> Recording the initial ABI baseline"
./gradlew ":sdk:features:$NAME:apiDump" -q

cat <<MSG

Done. sdk/features/$NAME is registered and green.

Next steps:
  1. Implement $DIR/src/main/kotlin/.../gateway/*Gateway.kt with the calls this feature needs.
  2. Pick an unused 3xxx block in *Errors.kt for this feature's business errors.
  3. Add fields to session/*State.kt and operations to session/*Session.kt, then drive them from
     internal/*SdkRuntime.kt (scope.ifOpen/scope.call/scope.launch) and *Sdk.kt's start(), replacing
     the TODO stubs — see sdk/features/otp for the pattern.
  4. Add demo usage under apps/demo, then run:
       ./gradlew check -Psdkbase.warningsAsErrors=true
       ./scripts/verify-publication.sh
MSG
