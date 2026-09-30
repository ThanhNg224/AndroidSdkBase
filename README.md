# AndroidSdkBase

A base for a publishable Android SDK where module boundaries, the public ABI, the Kotlin floor, and
the external consumer build are enforced by gates, not by review. One worked example — OTP
verification — exercises every layer end to end so you have something real to delete.

## Gates

| Gate | What fails it | Command |
|---|---|---|
| `check` | A module crossing a zone boundary, an HTTP client or DI framework on a forbidden SDK classpath, a changed public signature, an unrecorded error code, a colour literal in a UI module, a failing test, lint, or a formatting violation | `./gradlew check -Psdkbase.warningsAsErrors=true` |
| `verify-publication.sh` | A broken POM, an unpinned `kotlin-stdlib`, the Kotlin-2.2.10 consumer failing to build, R8 stripping SDK code | `./scripts/verify-publication.sh` |
| `verify-guards.sh` | A guard above that can no longer fail on a real violation | `./scripts/verify-guards.sh` |
| `verify-integration.sh` | A multi-feature flow leaking sessions, optional adapter becoming required, or fixture artifacts failing Maven/R8 consumption | `./scripts/verify-integration.sh` |

For local work, follow the tiered gates in [AGENTS.md](AGENTS.md). CI runs every gate on each PR and
is the merge gate. `spotlessCheck` runs as part of `check`; use `./gradlew spotlessApply` to format.

The publication gate builds both headless and Compose UI hosts on Kotlin compiler 2.2.10;
`./scripts/verify-publication.sh --current` repeats them on 2.4.20. Both keep runtime stdlib at
2.2.21. See [the consumer matrix](docs/COMPATIBILITY.md#verified-consumer-profiles).

Integration fixtures live under `verification/integration-fixtures` and are overlaid only into
`build/integration-proof.*`. They exercise OTP → profile composition and an optional callback
adapter, including failure/cancellation cleanup and coordinate-only Java/Kotlin consumers. They
add no artifacts to the base's default publication. The callback fixture cancels waiting and
ignores late callbacks; it cannot cancel a backend task without a host cancellation handle.

## Toolchain

| | Version |
|---|---|
| Gradle | 9.7.1 |
| AGP | 9.4.1 (built-in Kotlin) |
| KGP | 2.4.20 |
| JDK | 17 |
| `minSdk` | 24 |
| Consumer Kotlin floor | 2.2 (`verification/consumer` builds on AGP's bundled 2.2.10) |

## Layout

The canonical module map and package rules live in [AGENTS.md](AGENTS.md). Zone dependency semantics
and enforced boundaries are in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md). The module registry is
`gradle/module-topology.gradle.kts`; an unregistered included module fails the zone guard.

## Optional logging

Core technical logging remains lightweight. Add `event-logging` for durable structured events,
`logging-file` for bounded rolling files and opt-in SDK crash capture, and `event-logging-work`
for network-constrained background recovery. The host supplies the transport; no HTTP client
enters core or either logging feature. See [logging contracts and setup](docs/LOGGING.md).

The publication gate also builds a separate Java/Kotlin logging consumer under R8. The core/OTP/remote-config
headless consumer verifies that neither Compose nor WorkManager becomes a mandatory dependency.

## Start a new SDK

```bash
./scripts/rename-project.sh --group com.acme --namespace com.acme.paykit --name PayKit \
  --developer-id acme --developer-name "Acme Inc." --developer-url https://acme.com \
  --repo-url https://github.com/acme/paykit
./gradlew check -Psdkbase.warningsAsErrors=true
./scripts/verify-publication.sh
```

## Add a feature

```bash
./scripts/new-feature.sh face-match
```

The script creates a registered, green feature scaffold and its initial ABI baseline. Follow the
[feature recipe](docs/RECIPES.md#add-a-feature) to replace its stubs and verify the implementation.

OTP demonstrates a session; `remote-config` demonstrates a one-shot fetch without a session.
See [the one-shot recipe](docs/RECIPES.md#add-a-one-shot-feature-no-session).

## Remove the examples

Delete `sdk/features/otp*`, `sdk/features/remote-config` and the OTP-dependent
`sdk/adapters/otp-fake-sms`, their entries in `gradle/module-topology.gradle.kts`, and the
demo/consumer code and coordinate dependencies that use them. Remove their error ledger/reference rows and API baselines
as permitted by the pre-release policy. Keep in `sdk/core` only the gateway-agnostic parts you still
need — `SdkResult`, `SdkError`/`SdkErrors`, `SdkLogger`.

## Versioning

SemVer, decided by the `api/*.api` diff — see `docs/COMPATIBILITY.md` "Versioning". Every
public change is recorded in [CHANGELOG.md](CHANGELOG.md).

## Publishing

Local file repository only (`build/local-repo`). **Maven Central is intentionally not set up while
this is a base** — there is nothing to publish yet. A derived SDK adds a Central Portal account,
signing keys in CI secrets, and vanniktech's `publishToMavenCentral`.

## License

Apache 2.0.
