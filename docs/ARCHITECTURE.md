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

`:sdk:core` gives every feature the same building blocks instead of each reinventing them:

- `result/` — `SdkResult` (`Success`/`Failure`) plus `map`/`flatMap`/`fold`/`onSuccess`/
  `onFailure`/`getOrNull`/`errorOrNull`; the only type that crosses the public boundary.
- `call/` — `safeCall(operation, timeoutMillis, mapper, block)` contains host code via its own
  `withTimeoutOrNull` (an enclosing cancellation still propagates). `RetryPolicy` retries with
  capped exponential backoff, stopping on success, `maxAttempts`, or when `retryOn` (default
  `RetryPolicy.TransientErrors`) rejects. `launchCallback(dispatchers, callback, parent, onUndelivered,
  block)` is the one primitive every Java-callable outbound entry point (`OtpSdk.start`,
  `OtpSession.submit`/`resend`) is built from: it runs `block` on `dispatchers.default`, delivers to
  a `ResultCallback` on `dispatchers.main`, and — via one `AtomicBoolean` deciding cancel-vs-deliver
  — guarantees the callback never fires once cancelled and, if `block` still succeeds after that,
  hands the value to `onUndelivered` instead of leaking it (e.g. `OtpSdk.start`'s `onUndelivered`
  closes the session). It is `@SdkInternalApi` (see `annotation/`), so only `sdk/` modules call it
  directly; a host only ever sees the public callback entry points built on it.
- `session/` — `StateFlow<S>.observe(dispatchers, listener, parent)` is `launchCallback`'s sibling
  for a `StateFlow`: delivers the current value then every change to a `StateListener` on
  `dispatchers.main`, contained the same way, until cancelled. `SdkSession<S>` is the public
  contract every feature session extends (`state`, `observeState`, `close`). `SessionScope` is the
  only owner of a session's coroutines: `launch` (fire-and-forget, no-op once closed), `ifOpen`
  (suspend, `Failure(SdkErrors.sessionClosed())` once closed) and `call` (its Java-callable twin,
  built on `launchCallback`) all route through it, and `close()` — atomic, idempotent, `true` only
  for the first caller — cancels every one of them, in flight or not. `StateStore<S>` serializes
  every state mutation through `withLock`, including timer ticks, so no two transitions can
  interleave; a nested `withLock` on the same store is detected (via a coroutine-context element
  keyed to that store) and fails fast with `IllegalStateException` instead of deadlocking, and its
  `Mutation<S>.update` has no public implementation outside a held lock. `SdkSessionBase<S>`
  implements `SdkSession`'s three members once on top of a `SessionScope`/`StateStore` pair; `close()`
  is `final` and calls `onClose()` exactly once, on the call that actually closes the scope.
- `annotation/` — `@SdkInternalApi` (`@RequiresOptIn`) marks a declaration (`launchCallback`,
  `observe`) meant only for SDK modules; `sdkbase.android.library` opts every SDK module in via
  `compilerOptions.optIn`, while `apps/demo` and `verification/consumer` are not opted in, so a host
  reaching for one gets a normal opt-in compile error.
- `time/` — `Clock`/`IdGenerator`: injected "now"/id sources so backoff and log timestamps are
  testable.
- `logging/` — `SdkLogger` (built via `SdkLogger.Builder`, `NoOp` by default) hands out a
  `TaggedLogger` (`v`/`d`/`i`/`w`/`e`/`trace`) per tag. A record is redacted by the logger's
  `Redactor` (`DefaultRedactor` + `mask()`) once, before any `LogSink` sees it; a throwing sink is
  contained. `LogcatSink` is the one place `android.util.Log` is allowed in `sdk/`.
- `telemetry/`, `gateway/` — `TelemetrySink` (`None` by default) and the Java-friendly
  `GatewayCallback`/`CompletionCallback`; host-supplied and always called inside a try/catch via
  `TelemetrySink.emitSafely(name, attributes)` (`@SdkInternalApi`, catches `Exception`, never
  `Error`). `gateway/` also has `awaitCallback`/`awaitCompletion` (`@SdkInternalApi`,
  `suspendCancellableCoroutine`): the suspend side of a `GatewayCallback`/`CompletionCallback`
  bridge — only the first terminal call resumes the coroutine, from any thread, and a callback that
  arrives after the awaiting coroutine was cancelled is silently ignored rather than crash.
- `config/` — `validateConfig(block)` (`@SdkInternalApi`) is the style every feature's
  `Builder.build()` validates itself in: `block` runs against a `ConfigChecks` receiver whose
  `ensure(condition, message)` stops validation at the first failing check (`message` is evaluated
  only on failure, later checks never run) and becomes `Failure(SdkErrors.invalidConfig(message))`;
  any other `Exception` out of `block` becomes `Failure(SdkErrors.unknown(cause))` instead of
  crashing the host, and an `Error` is never caught.
- `environment/` — `SdkEnvironment` (built via `SdkEnvironment.Builder`, `Default` when every field
  is left at its default) bundles `logger`, `telemetry`, `dispatchers`, `clock` and `idGenerator`
  into the one instance every feature's config takes (e.g. `OtpSdkConfig.Builder.environment(...)`),
  instead of each feature config growing its own copies of these builder methods.

Package layout rules — entry point at the package root, named sub-packages, `internal/` for
implementation, no `utils/misc/helpers` — live in AGENTS.md "Package rules".

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

`./scripts/new-feature.sh <name>` scaffolds a feature on this kit instead of a bare stub: a session
interface extending `SdkSession<S>`, a plain state class, and a runtime extending `SdkSessionBase`
— see "Core toolkit" `session/` above. To add an operation:

1. Add whatever fields it needs to `session/*State.kt` (a plain class, not a `data class` —
   `public-data-class` in "Source rules" above) and a `Phase`/case for it if the flow gains a new
   state.
2. Declare it on `session/*Session.kt`: a suspend `fun x(): SdkResult<T>` plus its Java-callable
   twin `fun x(callback: ResultCallback<T>): Cancellable`.
3. Implement both on `internal/*SdkRuntime.kt`, routed through the inherited `scope`: a suspend
   operation through `scope.ifOpen { ... }`, its Java twin through `scope.call(callback) { ... }`,
   fire-and-forget UI-driven work through `scope.launch { ... }`. Mutate state only inside
   `store.withLock { update { ... } }` — never by holding a reference to the store's value and
   writing to it directly.
4. Wire `*Sdk.kt`'s `start()` to call the gateway, close the `SessionScope` on failure (see
   `OtpSdk.start`'s try/catch around a mid-start cancellation), and hand back the runtime.

`sdk/features/otp` is the worked example for all four steps.

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
