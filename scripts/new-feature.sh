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
import {pkg}.config.{pascal}SdkConfig

/**
 * The only class a host needs to know about. It validates, wires and delegates — and nothing
 * else. No networking, no storage, no global mutable state, no DI container.
 */
public object {pascal}Sdk {{

    @JvmStatic
    public suspend fun start(config: {pascal}SdkConfig): SdkResult<Unit> {{
        // TODO: replace with real work — call config.gateway, build a session, wire it to
        // config.environment's logger/telemetry. Left as a stub so the module is green from the
        // first commit.
        config.environment.logger.tagged("{pascal}Sdk").d {{ "start() called; feature not yet implemented" }}
        return SdkResult.Success(Unit)
    }}

    /**
     * The Java-callable twin of [start]: no `Continuation`, delivered on the config's
     * [SdkEnvironment][{ns}.core.environment.SdkEnvironment]'s main dispatcher.
     */
    @JvmStatic
    public fun start(config: {pascal}SdkConfig, callback: ResultCallback<Unit>): Cancellable =
        launchCallback(config.environment.dispatchers, callback) {{ start(config) }}
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

import {ns}.core.environment.SdkEnvironment
import {ns}.core.result.SdkResult
import {pkg}.gateway.{pascal}Gateway

/**
 * Host-supplied configuration. A builder rather than default arguments because a Java host cannot
 * use Kotlin default arguments.
 */
public class {pascal}SdkConfig private constructor(
    public val gateway: {pascal}Gateway,
    public val environment: SdkEnvironment,
) {{
    public class Builder(private val gateway: {pascal}Gateway) {{
        private var environment: SdkEnvironment = SdkEnvironment.Default

        public fun environment(value: SdkEnvironment): Builder = apply {{ environment = value }}

        public fun build(): SdkResult<{pascal}SdkConfig> =
            SdkResult.Success({pascal}SdkConfig(gateway = gateway, environment = environment))
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
    fun `build succeeds with defaults`() {{
        val config = {pascal}SdkConfig.Builder(gateway).build().assertSuccess()

        assertNotNull(config)
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
  3. Wire the gateway into *Sdk.kt, replacing the TODO stub.
  4. Add demo usage under apps/demo, then run:
       ./gradlew check -Psdkbase.warningsAsErrors=true
       ./scripts/verify-publication.sh
MSG
