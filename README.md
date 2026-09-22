# AndroidSdkBase

A starter repository for building a **publishable Android SDK** — one whose architecture is enforced
by the build rather than described in a document.

It contains **no business logic carried over from any other project.** What it carries is a set of
guard rails that are hard to get right once and pointless to get right twice, plus one small worked
example that exercises all of them end to end.

> **Cloning this to start a real SDK?** Read [docs/EXAMPLE_VS_INFRASTRUCTURE.md](docs/EXAMPLE_VS_INFRASTRUCTURE.md)
> first. It lists, file by file, what to keep and what to delete.

---

## What it actually gives you

Four things in this repository fail the build when you break them. Each one has been broken on
purpose to prove it fails — a guard that cannot fail is decoration.

| Guard | What it stops | Command |
|---|---|---|
| **Zone guard** | A module depending upward, or a published artifact reaching a module that never ships — which a consumer resolving from Maven Central could not resolve | `./gradlew projects` |
| **Additive-only ABI** | Removing or changing a published signature. Additions pass with no re-dump; that asymmetry *is* the policy | `./gradlew apiCheck` |
| **Metadata floor** | Silently shipping Kotlin metadata newer than your consumers' compiler can read | `./scripts/verify-kotlin-metadata.sh` |
| **External consumer** | A broken POM, a missing transitive dependency, an API unusable from Java, or R8 stripping something your consumer rules forgot | `./scripts/verify-publication.sh` |

The last one is the one most projects never build, and the only one that tests what a consumer
actually receives: it publishes to a local Maven repository, then compiles a **separate Gradle build**
against those coordinates from **both Java and Kotlin**, with `minifyEnabled true`.

Beyond the guards: a numbered error catalog, a host-owned gateway contract (the SDK ships no HTTP
client and no DI framework), a headless engine with its Compose UI in a **separate optional artifact**,
strict `explicitApi()`, resource prefixing, and convention plugins that keep every module's setup in
one place.

## Toolchain

| | Version |
|---|---|
| Gradle | 9.7.1 |
| AGP | 9.4.1 (requires Gradle ≥ 9.6.0) |
| Kotlin | 2.4.20, via AGP 9's built-in Kotlin with the KGP classpath override |
| compileSdk / targetSdk | 37 |
| minSdk | 24 |
| JVM toolchain | 17 |
| Compose | BOM 2026.09.00, in its own artifact |

AGP 9 forbids applying `org.jetbrains.kotlin.android`, `-Xjvm-default` is gone, and the emitted
Kotlin metadata version is a consumer-facing decision. All three are explained, with the measurements
behind them, in [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md).

## Layout

```text
sdk/core/                     pure Kotlin, zero android.* — contracts, errors, host gateways
sdk/platform/                 Android utilities (Logcat sink, real dispatchers)
sdk/capabilities/otp-engine/      headless state machine — no UI, no Compose
sdk/capabilities/otp-ui-compose/  optional Compose UI, separate artifact
sdk/facades/otp-sdk/          the published entry point: validates, wires, delegates
apps/demo/                    a host app that integrates the SDK like a customer
verification/consumer/        a SEPARATE build that consumes the published artifacts
build-logic/                  convention plugins + the ABI tooling
```

`:sdk:core` is pure Kotlin on purpose: it can become a Kotlin Multiplatform module later without
rewriting a single contract. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the zone table, which
is generated from the same rules the guard enforces.

## Start a new SDK from this

```bash
./scripts/rename-project.sh --group com.acme --namespace com.acme.paykit --name PayKit
./gradlew clean check
./gradlew apiDump          # record YOUR project's first baseline
```

Then delete the example, following [docs/EXAMPLE_VS_INFRASTRUCTURE.md](docs/EXAMPLE_VS_INFRASTRUCTURE.md).
If you delete a module but forget to unregister it, `./gradlew projects` tells you so — the guard
covers cleanup, not just addition.

## Adding a second feature

The walkthrough a template lives or dies by. Every step below is what the OTP example actually did.

1. **Create the engine.** `sdk/capabilities/<feature>-engine/` with
   `plugins { id("sdkbase.android.library"); id("sdkbase.abi"); id("sdkbase.android.publishing") }`
   and `android { namespace = "<your.root>.<feature>" }`. Keep it headless — no Compose, no Android views.
2. **Declare the gateway in `:sdk:core`.** The host owns the network, always. Add both a `suspend`
   interface and, if Java hosts matter to you, a `GatewayCallback`-based one — Java cannot implement a
   `suspend fun` with a genuinely async client.
3. **Add error codes** to `SdkErrors` in their own family range, append-only, and regenerate
   [docs/ERROR_CODES.md](docs/ERROR_CODES.md). Never renumber a released code.
4. **Register the module** in *both* registries in `gradle/module-topology.gradle.kts` — `zones`, and
   `publishedArtifacts` if it ships. **Forgetting this is the single most common way to break this base**,
   and the guard's error message tells you exactly that: an unregistered module is zone `unregistered`,
   allowed to depend on nothing.
5. **If it needs UI**, put it in a separate `<feature>-ui-compose` module applying
   `sdkbase.android.compose`, and keep the engine Compose-free. Prove it:
   `./gradlew :sdk:capabilities:<feature>-engine:dependencies --configuration releaseRuntimeClasspath | grep -i compose`
   must find nothing.
6. **Create the facade** in `sdk/facades/<feature>-sdk/` — validate config at the boundary and return a
   result, never throw out of a public entry point into someone else's app.
7. **Record the first baseline:** `./gradlew :<module>:apiDump`, and commit it with the change.
8. **Exercise it from the external consumer** in `verification/consumer/`, from both Java and Kotlin.
9. **Run the full gate:** `./gradlew check && ./scripts/verify-publication.sh`.

## The example

One feature — OTP verification — chosen because it is real enough to exercise every layer
(`contract → headless engine → optional UI → published facade`) and trivial enough that nobody will
mistake it for something to keep. It has a state machine, a countdown, retry limits and a lockout,
covered by **43 unit tests**. There are deliberately no unit tests for Compose layout: the UI is
stateless, so everything worth testing lives in the engine.

Its accepted code in the demo app is `123456`.

## Verification

```bash
./gradlew check                      # tests, lint, and every ABI baseline
./scripts/verify-publication.sh      # publish locally, then build the external Java+Kotlin consumer
```

CI runs both on every push and pull request. See [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md) for the
gates, the tracked-vs-local docs policy, and what is deliberately *not* set up here — notably Maven
Central publishing, which is left to the derived project rather than shipped untested.

## License

Apache 2.0.
