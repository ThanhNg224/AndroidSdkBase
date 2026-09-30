# Architecture

You are here because a build failed with `Module zone violation(s) — see docs/ARCHITECTURE.md`. The
guard lives in `build-logic/src/main/kotlin/sdkbase.zone-guard.gradle.kts`, with pure rules in
`sdkbase/zones/ZoneRules.kt`. Its registry is `gradle/module-topology.gradle.kts`; Settings derives
module includes from registered paths that contain a build file. A registered path without a build
file still fails the guard. If this document and the code disagree, trust the code.

Root Spotless formats Kotlin in `sdk/`, `apps/`, `build-logic/src/`, and recursive `*.gradle.kts`
files; it excludes the separate `verification/` build. Rules come from `.editorconfig`, including
ktlint settings; there is no second style definition. Structural ktlint rules (signature and
argument wrapping, import ordering) are disabled there to keep the baseline small, so the gate enforces
whitespace, braces, trailing commas, explicit imports and the 120-column limit.

## Zones

Every included module is registered in exactly one zone:

| Zone | Modules | May depend on |
|---|---|---|
| `core` | `:sdk:core` | *(nothing)* |
| `testing` | `:sdk:core-testing` | `core` |
| `ui` | `:sdk:core-ui-compose` | `core` |
| `feature` | `otp`, `otp-ui-compose`, `event-logging`, `logging-file` | `core`; another `feature` only as its UI module; `ui` only from a `<name>-ui-<toolkit>` module |
| `composition` | *(none yet)* | `core`, `feature` |
| `adapter` | `:sdk:adapters:event-logging-work`, `:sdk:adapters:otp-fake-sms` | `core`, `feature`, `vendor` |
| `vendor` | `:sdk:vendor:fake-sms-vendor` | *(no project)* |
| `bom` | `:sdk:bom` | `core`, `feature`, `composition`, `adapter`, `testing`, `ui` |
| `app` | `:apps:demo` | everything |

- **testing** — the published test kit (`:sdk:core-testing`): fakes and assertions for SDK and
  host tests. Depends on `core` only. Only `bom` and `app` may target it in a non-test
  configuration; a feature or composition consumes it via `testImplementation`, which the zone
  guard ignores (see rule 2), so it never appears in `allowedTargets` for those zones.
- **ui** — the shared Compose toolkit (`:sdk:core-ui-compose`): theme tokens, spacing and touch-target
  constants, contrast maths, error text by code, and a per-screen locale. Depends on `core` only.
  Only a `<name>-ui-<toolkit>` feature module (plus `bom`/`app`) may depend on it, so Compose never
  reaches a headless feature. See [THEMING.md](THEMING.md).
- **feature** — one capability, headless, plus its optional `<name>-ui-<toolkit>` module.
- **composition** — wires several features into one flow (e.g. `:sdk:composition:onboarding` =
  OTP + KYC). The only SDK zone that sees more than one feature, so cross-feature orchestration has
  exactly one home instead of growing a mesh of feature → feature edges.
- **adapter** — optional bridge a host opts into: a gateway backed by a concrete HTTP client, a
  vendor device SDK. The only SDK zone allowed an HTTP client or DI framework; no SDK zone may depend
  on it, so it never rides along transitively.
- **vendor** — wraps a binary a vendor hands you that has no Maven coordinate (a local `.jar`/`.aar`):
  `:sdk:vendor:fake-sms-vendor` is the worked example. It depends on no project and is never
  published; only an `adapter` may depend on it (`:sdk:adapters:otp-fake-sms` implements `OtpGateway`
  on it). Rule 4 below makes it impossible for anything published to reach it, so a published
  artifact can never require a file no consumer can resolve. A host that wants such an adapter copies
  the adapter and the binary, or writes its own gateway.

The empty composition zone is deliberate: the slot and its rules exist before the first module needs them.

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
6. Every `core`/`testing`/`ui`/`feature`/`composition` module (`dependencyPolicedZones`) runs
   `checkDependencyPolicy`.
7. Every `core`/`testing`/`ui`/`feature`/`composition`/`adapter` module (`sourceRuledZones`) runs
   `checkSourceRules` (see "Source rules" below).
8. The `ui` zone is used only by `<name>-ui-<toolkit>` modules: `otp-ui-compose -> core-ui-compose`
   passes, `otp -> core-ui-compose` fails.

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

The SDK relies on more of a gateway than its signature says: a suspend gateway must return rather
than throw, and must stop when cancelled (the SDK's timeouts cancel it); a callback gateway must
call back exactly once. `GatewayContract` in `:sdk:core-testing` checks this from the host's own
unit tests, in real time, throwing `AssertionError` (no test framework required):

```kotlin
@Test fun `gateway honours the contract`() {
    val gateway = MyOtpGateway(fakeApi)
    GatewayContract.assertReturns { gateway.requestOtp("+84901234567") }
    GatewayContract.assertCancellable { gateway.requestOtp("+84901234567") }
}
```

`assertCallsBackOnce`/`assertCompletesOnce` do the same for `GatewayCallback`/`CompletionCallback`
based gateways. `apps/demo` has a worked example (`DemoOtpGatewayContractTest`).

## Source rules

`checkSourceRules` (part of `check`) scans a module's `src/main` Kotlin sources — after stripping
comments and string/char literals, so a mention inside a KDoc or a string is never a false positive
— for:

| Rule | Zones | Fails on |
|---|---|---|
| `global-scope` | every SDK zone | `GlobalScope` |
| `android-log` | every SDK zone | `android.util.Log` (except `LogcatSink.kt` in `core`) |
| `own-coroutine-scope` | `feature`, `composition` | `CoroutineScope(` — own coroutines through `SessionScope` instead |
| `public-data-class` | every SDK zone | a non-`internal`/non-`private` `data class` with more than one constructor property |
| `ui-color-literal` | `ui` and every `<name>-ui-<toolkit>` module | `Color(0x…)`, `Color.Red` and the other named colours, `parseColor`, and a `#hex` or `@android:color/` in `res/` XML (`Color.Transparent`/`Unspecified` are fine) |

The pure rule engine is `findViolations(fileName, text, zone, isUiModule)` (plus `findResourceViolations` for `res/` XML) in
`build-logic/src/main/kotlin/sdkbase/sources/SourceRules.kt` (unit-tested in
`build-logic/src/test/kotlin/sdkbase/sources/SourceRulesTest.kt`, wired into the root `check`).
Known limitation: `public-data-class` only reads a `data class` declaration's own modifier, not an
enclosing class's — one nested inside an `internal`/`private` outer class is not detected this way.

## Headless feature, optional UI

`:sdk:features:otp` is the headless state machine; `:sdk:features:otp-ui-compose` is a separate
artifact so a host with its own UI toolkit never inherits the Compose runtime. Verify with:

```bash
./gradlew :sdk:features:otp:dependencies --configuration releaseRuntimeClasspath | grep -i compose
```

This must find nothing.

## Core toolkit

`core` is an Android-only library; Kotlin Multiplatform is out of scope. Its package map is:

| Package | Purpose | Key symbols |
|---|---|---|
| `result/`, `error/` | Typed outcomes and error policy | `SdkResult`, `SdkError`, `SdkErrors`, `Disposition` |
| `call/` | Host-call containment, retry, Java callbacks | `safeCall`, `RetryPolicy`, `launchCallback`, `ResultCallback` |
| `time/`, `concurrency/` | Injectable time, IDs, and dispatchers | `Clock`, `IdGenerator`, `DispatcherProvider` |
| `session/` | Session lifetime, observation, serialized state | `SdkSession`, `SessionScope`, `StateStore`, `SdkSessionBase` |
| `gateway/` | Java-friendly callback contracts and suspend bridges | `GatewayCallback`, `CompletionCallback`, `awaitCallback` |
| `logging/`, `telemetry/` | Structured host sinks with containment/redaction | `SdkLogger`, `TaggedLogger`, `DefaultRedactor`, `emitSafely` |
| `environment/`, `config/` | Shared injected runtime services and validation | `SdkEnvironment`, `validateConfig` |
| `annotation/` | Opt-in marker for SDK-internal entry points | `SdkInternalApi` |

Two implementation guarantees matter when extending sessions: `StateStore.withLock` serializes
mutations and fails fast on re-entry into the same store; `SdkSessionBase.close()` invokes `onClose()`
once, only for the call that closes the scope. Callback entry points suppress delivery after
cancellation and pass undelivered values to their `onUndelivered` handler.

Package and file-placement rules are in [AGENTS.md](../AGENTS.md#layout). For adding a feature or
operation, follow [the change recipes](RECIPES.md).

## Error codes

`SdkErrors` (`:sdk:core`) owns the shared common/system/lifecycle codes; each feature owns its own
catalog in a block no other module uses (the logging features hold 2101–2104, 2201, 3101/3103 and
3201/3203–3205). Families: 1xxx common, 2xxx system, 3xxx business, 4xxx lifecycle. Hosts branch on
`SdkError.code`, never on `.reason`. Every error also carries a `Disposition` (`INLINE_RETRY`,
`DIALOG_RETRY`, `DIALOG_TERMINAL`, `SILENT`) that tells a UI how to present it, independent of
`isRetryable`.

Codes are append-only, and the build enforces it: `sdk/error-codes.ledger` records every code,
`docs/ERROR_CODE_REFERENCE.md` lists them with their disposition, and `checkErrorCatalog` (part of
`./gradlew check`) fails on a renumbered, dropped, reused or unrecorded code. Add one with
`./gradlew errorCatalogDump`; see the reference for the full procedure.

## Threading

`OtpEngine` serializes every state transition — a UI-driven `dispatch()`, a host-driven
`submitCode()`/`resend()`, and its own timer ticks alike — through `StateStore.withLock`, so no two
transitions can ever interleave. `SessionScope` is the only owner of the session's coroutines: the
engine's ticker runs on `scope.coroutineScope`, `dispatch()` goes through `scope.launch`, and
`submitCode()`/`resend()` (and their Java twins) go through `scope.ifOpen`/`scope.call`. `close()`
cancels every one of them, in flight or not, and every call afterwards — suspend or Java — gets
`Failure(SdkErrors.sessionClosed())` instead of reaching the engine. Every call into the host's
gateway goes through core's `safeCall`, which maps a timeout, `IOException`, or any other exception
to an `SdkError` — nothing the host throws or hangs on ever reaches the engine's caller.

## Building a feature

`./scripts/new-feature.sh <name>` scaffolds the session interface, plain state, config, gateway,
error catalog, runtime, tests, registration, and initial ABI. Replace the stubs with the feature's
behavior; use [the feature and operation recipes](RECIPES.md#add-a-feature) and `sdk/features/otp`
as the worked example. Public signatures and errors follow the compatibility policy in
[AGENTS.md](../AGENTS.md#pre-release-breaking-changes-are-allowed).

## Optional logging pipelines

`event-logging` implements the core `TelemetrySink` contract through a session-owned bounded
admission queue. Explicit `track` acknowledges durable persistence; `flush` drains the same
ordered disk queue through a host gateway under `safeCall`. File locks serialize queue mutations
and prevent concurrent foreground/background drainers. IDs remain stable across delivery retries.

`event-logging-work` adds WorkManager only when explicitly consumed. A host Application provider
restores the same namespace/config/gateway after process recreation. The worker performs a
one-shot drain; it does not schedule itself. A durable periodic recovery request closes the gap
between file persistence and a one-time wake-up. Work Data contains only the opaque namespace.

`logging-file` implements `LogSink` with bounded non-blocking admission, one writer on IO and
bounded rolling files. The single SessionScope owns the writer. Optional crash capture persists
bounded redacted SDK-attributed reports synchronously at the terminal crash boundary, then always
delegates the original exception to the previous host handler. Setup, delivery semantics and
retention limits are specified in [LOGGING.md](LOGGING.md).
