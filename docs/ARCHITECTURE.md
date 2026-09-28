# Architecture

You are here because a build failed with `Module zone violation(s) — see docs/ARCHITECTURE.md`. The
guard lives in the `gradle.projectsEvaluated` block of root `build.gradle.kts`; the module registry
it reads is `gradle/module-topology.gradle.kts`. If this document and the code disagree, trust the
code.

## Zones

Every included module is registered in exactly one zone:

| Zone | Modules | May depend on |
|---|---|---|
| `core` | `:sdk:core` | *(nothing)* |
| `feature` | `:sdk:features:otp`, `:sdk:features:otp-ui-compose` | `core`; another `feature` only as its UI module |
| `composition` | *(none yet)* | `core`, `feature` |
| `adapter` | *(none yet)* | `core`, `feature` |
| `bom` | `:sdk:bom` | `core`, `feature`, `composition`, `adapter` |
| `app` | `:apps:demo` | everything |

- **feature** — one capability, headless, plus its optional `<name>-ui-<toolkit>` module.
- **composition** — wires several features into one flow (e.g. `:sdk:composition:onboarding` =
  OTP + KYC). The only SDK zone that sees more than one feature, so cross-feature orchestration has
  exactly one home instead of growing a mesh of feature → feature edges.
- **adapter** — optional bridge a host opts into: a gateway backed by a concrete HTTP client, a
  vendor device SDK. The only SDK zone allowed an HTTP client or DI framework; no SDK zone may depend
  on it, so it never rides along transitively.

Empty zones are deliberate: the slot and its rules exist before the first module needs them.

## The rules the guard enforces

1. Every included module is registered in a zone.
2. Edges only go to allowed zones, across every non-test configuration (`compileOnly`,
   `runtimeOnly`, and build-type-specific ones included — not just `implementation`/`api`).
3. A feature depends on another feature only when it is that feature's UI module:
   `otp-ui-compose -> otp` passes, `otp-extra -> otp` and `otp -> otp-ui-compose` fail. Two different
   features meet only inside a composition module.
4. A published module (`publishedArtifacts` in the topology file) depends only on other published
   modules — a consumer resolving by Maven coordinate must be able to resolve every edge.
5. A published module has an `apiCheck` task (applies `sdkbase.abi`) and a `localTest` publication.
6. Every `core`/`feature`/`composition` module (`dependencyPolicedZones`) runs `checkDependencyPolicy`.

## The host-gateway rule

The SDK never talks to a network on its own. It declares the contract it needs and the host supplies
the implementation:

```kotlin
public interface OtpGateway {
    public suspend fun requestOtp(destination: String): SdkResult<OtpChallenge>
    public suspend fun verifyOtp(challengeId: String, code: String): SdkResult<Unit>
}
```

`:sdk:features:otp` depends on this interface, never on a concrete client; `apps/demo` supplies an
implementation. No Retrofit, OkHttp, Ktor, Hilt, Koin, or Dagger appears in a `core`, `feature` or
`composition` module: a bundled HTTP client or DI container forces its transitive graph, version, and
size onto every consumer, and a version conflict with the host's own choice becomes the host's
problem to resolve.

`checkDependencyPolicy` (part of `check`) enforces this. It walks the whole resolved
`releaseCompileClasspath` and `releaseRuntimeClasspath` — transitive edges included — and fails with
the shortest path to the offender. The forbidden groups live in
`build-logic/src/main/kotlin/sdkbase/dependencies/DependencyPolicy.kt`. A ready-made gateway on a
specific client belongs in an `adapter` module (e.g. `:sdk:adapters:otp-okhttp`), which is exempt.

## Headless feature, optional UI

`:sdk:features:otp` is the headless state machine; `:sdk:features:otp-ui-compose` is a separate
artifact so a host with its own UI toolkit never inherits the Compose runtime. Verify with:

```bash
./gradlew :sdk:features:otp:dependencies --configuration releaseRuntimeClasspath | grep -i compose
```

This must find nothing.

## Core toolkit

`:sdk:core` gives every feature the same building blocks instead of each reinventing them:

- `result/` — `SdkResult` (`Success`/`Failure`) plus `map`/`flatMap`/`fold`/`onSuccess`/
  `onFailure`/`getOrNull`/`errorOrNull`; the only type that crosses the public boundary.
- `call/` — `safeCall(operation, timeoutMillis, mapper, block)` contains host code via its own
  `withTimeoutOrNull` (an enclosing cancellation still propagates). `RetryPolicy` retries with
  capped exponential backoff, stopping on success, `maxAttempts`, or when `retryOn` (default
  `RetryPolicy.TransientErrors`) rejects.
- `time/` — `Clock`/`IdGenerator`: injected "now"/id sources so backoff and log timestamps are
  testable.
- `logging/` — `SdkLogger` (built via `SdkLogger.Builder`, `NoOp` by default) hands out a
  `TaggedLogger` (`v`/`d`/`i`/`w`/`e`/`trace`) per tag. A record is redacted by the logger's
  `Redactor` (`DefaultRedactor` + `mask()`) once, before any `LogSink` sees it; a throwing sink is
  contained. `LogcatSink` is the one place `android.util.Log` is allowed in `sdk/`.
- `telemetry/`, `gateway/` — `TelemetrySink` and the Java-friendly `GatewayCallback`/
  `CompletionCallback`; host-supplied and always called inside a try/catch.

Package layout rules — entry point at the package root, named sub-packages, `internal/` for
implementation, no `utils/misc/helpers` — live in AGENTS.md "Package rules".

## Error codes

`SdkErrors` (`:sdk:core`) owns 1xxx common, 2xxx system/transport, and 4xxx lifecycle codes. Each
feature owns its own 3xxx business range in its own catalog — `OtpErrors` for OTP. Codes are
append-only: never renumber a released code. Hosts branch on `SdkError.code`, never on `.reason`.

## Threading

`OtpEngine` serializes every state transition through one `Mutex`, so a UI-driven `dispatch()` and a
host-driven `submitCode()`/`resend()` can never interleave. Every call into the host's gateway goes
through core's `safeCall`, which maps a timeout, `IOException`, or any other exception to an
`SdkError` — nothing the host throws or hangs on ever reaches the engine's caller.
