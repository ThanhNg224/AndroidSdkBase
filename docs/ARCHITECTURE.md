# Architecture

You are here because `./gradlew projects` (or any build) failed with:

```
Module zone violation(s) — see docs/ARCHITECTURE.md:
 - :some:module [zone] -> :other:module [zone] is not allowed
```

This document explains the rule the guard enforces, why it exists, and what each module may and
must not do. The guard lives in the root `build.gradle.kts`; the module registry it reads lives in
`gradle/module-topology.gradle.kts`. Both are short — read them alongside this doc if anything here
looks stale, because **the code is the source of truth** and this document only explains it.

## The zone model

Every module is registered in exactly one zone in `gradle/module-topology.gradle.kts`:

```kotlin
"core"       to listOf(":sdk:core"),
"platform"   to listOf(":sdk:platform"),
"capability" to listOf(":sdk:capabilities:otp-engine", ":sdk:capabilities:otp-ui-compose"),
"facade"     to listOf(":sdk:facades:otp-sdk"),
"app"        to listOf(":apps:demo"),
```

A module that exists but is not listed there is zone `unregistered`, which is allowed to depend on
nothing and is reached by nothing — adding a module without registering it fails the guard the
first time anything touches it.

The guard's `allowedTargets` map (root `build.gradle.kts`) is what a module in each zone may declare
an `api`/`implementation` project dependency on. This table is copied directly from that map, so if
it ever looks wrong, trust the map, not this table:

| Zone         | May depend on (project deps)                        |
|--------------|-------------------------------------------------------|
| `core`       | *(nothing — it is the bottom)*                        |
| `platform`   | `core`                                                 |
| `capability` | `core`, `platform`, `capability`                       |
| `facade`     | `core`, `platform`, `capability`                       |
| `app`        | `core`, `platform`, `capability`, `facade`, `app`      |

`core` reaching nothing is deliberate, not an oversight: it is the bottom of the graph, so nothing
below it can exist for it to depend on. A dependency that appears to need to "go down" from `core`
is a design smell — the abstraction it needs belongs in `core` itself, not below it.

`capability` and `facade` have the same allowed set today (`core`, `platform`, `capability`) — a
facade is not "above" a capability that another capability can't also see. What actually separates
facade from capability is the `publishedArtifacts` registry and intent (see below), not a stricter
dependency rule.

## The two rules the guard enforces

The guard (`gradle.projectsEvaluated` block in root `build.gradle.kts`) checks two independent
things, every time projects are evaluated, over every `api`/`implementation` project dependency
(`androidTest` is deliberately exempt):

**Rule 1 — Direction.** A module may only declare a project dependency on a module in its own zone's
allowed-target set, above. A `:sdk:core -> :sdk:platform` edge fails as
`:sdk:core [core] -> :sdk:platform [platform] is not allowed`, because `core`'s allowed set is empty.

**Rule 2 — Purity of a published artifact's transitive compile graph.** Every module listed in
`publishedArtifacts` (`gradle/module-topology.gradle.kts`) has its *entire* transitive `api`/
`implementation` project graph walked, and none of it may land in zone `app` or `unregistered`.

Purity exists because of what "published" means: a consumer resolves the artifact from Maven
Central by its GAV coordinate. If that artifact's compile graph contains an edge to
`:apps:demo` — a module that is never published — the consumer's build has no coordinate to resolve
that edge to. It doesn't fail loudly with "module not found" in every case; it can just be a broken
POM. The purity rule catches this at configuration time on the SDK's own machine, before it ever
reaches a consumer.

Purity is checked **transitively**, not just on direct dependencies. If `:sdk:capabilities:otp-engine`
depended on some module `X` in an unpublished zone, that violation is reported not only against
`otp-engine`, but against *every published module whose graph passes through it* — for example
`:sdk:facades:otp-sdk`, which depends on `otp-engine`. This is intentional: the leak doesn't stay
local to where it was introduced, so neither does the failure. Fixing it at the point the edge was
added (not at every module the report names) clears every downstream violation at once.

Direction and purity are independent: an edge can violate direction without violating purity (e.g.
`otp-engine -> otp-ui-compose`, both published but wrong direction) or violate purity without
violating direction in isolation (e.g. `otp-engine -> apps:demo`, direction alone already forbids
this here, but the purity rule is what would still catch it if a future zone's `allowedTargets`
ever permitted reaching upward).

## Per-module ownership

| Module | Zone | May own | Must not own |
|---|---|---|---|
| `:sdk:core` | `core` | Public contracts: `SdkResult`, the `SdkError` sealed tree, the `SdkErrors` catalog, `SdkLogger`, and host gateway interfaces (`OtpGateway`, `TelemetrySink`). Pure Kotlin only. | Any `android.*` import. Any implementation of a gateway. Any coroutine dispatcher choice, Android context, or UI. |
| `:sdk:platform` | `platform` | Android-side utilities that need the platform but not a feature: `AndroidSdkLogger`, `DispatcherProvider`, resource resolution. | Feature logic (OTP or otherwise). A dependency on any `capability` or `facade` module. |
| `:sdk:capabilities:otp-engine` | `capability` | The headless OTP state machine: `OtpEngine`, `OtpState`, `OtpCommand`, and the internal reducer/timer. Exposes state as `StateFlow`. | Compose, any UI toolkit, or any Android `View`. See the headless/optional-UI split below. |
| `:sdk:capabilities:otp-ui-compose` | `capability` | Stateless Compose UI (`OtpScreen`, `OtpTheme`) that renders an `OtpState` and emits `OtpCommand`s. | Any business logic. It must not decide anything the engine didn't already decide — if a UI change requires new logic, that logic belongs in `otp-engine`. |
| `:sdk:facades:otp-sdk` | `facade` | The one published entry point a host actually calls: `OtpSdk` (start/stop/observe), `OtpSdkConfig` and its validation, and the internal wiring (`OtpSdkRuntime`) that owns the runtime's `CoroutineScope`. | Alternative ways to reach the engine. A facade validates, wires, and delegates — nothing else. It must not depend on `otp-ui-compose`: a host that wants the engine without the UI must be able to depend on the facade alone. |
| `:apps:demo` | `app` | A Compose host application that integrates the facade exactly as a real customer would, including providing its own `OtpGateway`/`TelemetrySink` implementations. It is also the shrinker canary (`isMinifyEnabled = true`). | Anything published modules need. Nothing here is ever on a published module's compile graph — Rule 2 guarantees that. |

## The host-gateway rule

The SDK never talks to a network on its own. It declares the contract it needs in `core`, and the
host supplies the implementation. The two real examples in this repo:

```kotlin
// sdk/core/src/main/kotlin/.../core/gateway/OtpGateway.kt
public interface OtpGateway {
    public suspend fun requestOtp(destination: String): SdkResult<OtpChallenge>
    public suspend fun verifyOtp(challengeId: String, code: String): SdkResult<Unit>
}

// sdk/core/src/main/kotlin/.../core/gateway/TelemetrySink.kt
public fun interface TelemetrySink {
    public fun onEvent(name: String, attributes: Map<String, String>)
}
```

`otp-engine` and `otp-sdk` depend on these interfaces, never on a concrete client. `apps/demo`
supplies real (or mock) implementations, backed by whatever HTTP or analytics stack that particular
app already has — Retrofit, Ktor, OkHttp, a test double.

## No HTTP client, no DI framework, in published artifacts

No Retrofit, OkHttp, Ktor, Hilt, Koin, or Dagger appears in the dependency graph of any module in
`publishedArtifacts`. This is a design rule, not (yet) a mechanically enforced one — the zone guard
does not currently inspect *which* libraries a module pulls in, only which *project* edges it has.

The reason is cost, not taste: a published SDK that bundles its own HTTP stack or DI container
forces that dependency, its transitive graph, its version, and its size onto every consumer —
even a consumer who already uses a different HTTP client or already made a different DI choice.
A version conflict between the SDK's bundled client and the host's own is the host's problem to
resolve, not something a library should create in the first place. Declaring an interface in `core`
and letting the host supply the implementation moves that cost to zero.

## The headless-engine / optional-UI split

`otp-engine` and `otp-ui-compose` are separate published artifacts specifically so that a host that
wants only the state machine — for instance a host with its own UI toolkit, or one embedding the
flow into an existing screen — never inherits the Compose runtime. This is checked directly:

```
./gradlew :sdk:capabilities:otp-engine:dependencies --configuration releaseRuntimeClasspath | grep -i compose
```

This must find nothing. If it finds anything, either a dependency was added to `otp-engine` by
mistake, or UI logic leaked into a module that must stay headless.

## This is not app-style Clean Architecture

There is deliberately no UseCase/Repository stack per feature here. A typical app layers by
*responsibility within one deployable*: presentation → domain → data, all shipped together. That
axis doesn't apply to a library — nothing here is "the data layer" because there is no repository
to own; the host owns all I/O by design (see the host-gateway rule above).

The layering axis in this repository is **the publish surface**: each zone boundary is a boundary
between what does or does not ship as its own artifact, and what can or cannot be resolved
independently by a consumer. `core` ships as a pure-Kotlin contract artifact. `platform` ships as an
Android-utilities artifact. Each `capability` ships as its own artifact so it can be adopted (or not)
independently. A `facade` ships as the one thing a host actually adds to their `dependencies { }`
block. Do not introduce a UseCase/Repository layer inside a module expecting it to mirror app
architecture — it will not map onto any of the rules above, and it does not need to.

## `:sdk:core` is pure Kotlin on purpose

`:sdk:core` applies `sdkbase.kotlin.jvm` (plain `org.jetbrains.kotlin.jvm`), not
`sdkbase.android.library`, and contains zero `android.*` imports (Task 4 in the build plan verifies
this with `grep -rn "android" sdk/core/src/main/kotlin`). This is intentional, not incidental: a
module with no Android dependency at all can become a Kotlin Multiplatform module later — targeting
iOS, JVM, or anything else — without rewriting a single one of its contracts (`SdkResult`,
`SdkError`, `SdkErrors`, `SdkLogger`, or the gateway interfaces). Every other zone depends on `core`
directly or transitively, so keeping it platform-free protects that option for the whole SDK, not
just for `core` itself. Do not add an Android import to `:sdk:core` to solve a one-off problem —
move that code to `:sdk:platform` instead.
