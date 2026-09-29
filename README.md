# AndroidSdkBase

A base for a publishable Android SDK where module boundaries, the public ABI, the Kotlin floor, and
the external consumer build are enforced by gates, not by review. One worked example — OTP
verification — exercises every layer end to end so you have something real to delete.

## Gates

| Gate | What fails it | Command |
|---|---|---|
| `check` | A module crossing a zone boundary, a feature depending on another feature, an HTTP client or DI framework on an SDK classpath, a changed/removed public signature, a failing test, lint | `./gradlew check -Psdkbase.warningsAsErrors=true` |
| `verify-publication.sh` | A broken POM, an unpinned `kotlin-stdlib`, the Kotlin-2.2.10 consumer failing to build, R8 stripping SDK code | `./scripts/verify-publication.sh` |
| `verify-guards.sh` | A guard above that can no longer fail on a real violation | `./scripts/verify-guards.sh` |
| `verify-integration.sh` | A multi-feature flow leaking sessions, optional adapter becoming required, or fixture artifacts failing Maven/R8 consumption | `./scripts/verify-integration.sh` |

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

```text
sdk/core                          Android library toolkit shared by every feature: result/, error/,
                                   call/ (safeCall, RetryPolicy), time/ (Clock, IdGenerator),
                                   concurrency/, logging/, telemetry/, gateway/.
sdk/core-testing                  published test kit: fakes, recording sinks and SdkResult
                                   assertions; use via testImplementation.
sdk/features/<name>                one published artifact per feature; entry point at the package
                                    root, public sub-packages (config/, gateway/, session/...),
                                    engine code under internal/ and Kotlin `internal`.
sdk/features/<name>-ui-compose     optional UI artifact; public surface at ui/, implementation
                                    under ui/internal/. Compose never enters a non-UI module.
sdk/composition/<flow>             (zone reserved) wires several features into one flow; the only
                                    place two features meet.
sdk/adapters/<feature>-<lib>       optional host bridge, e.g. event-logging-work or a gateway on OkHttp;
                                    the only SDK zone allowed an HTTP client or DI framework.
sdk/bom                            lists every published module automatically.
apps/demo                          manual testing only; never published.
verification/consumer              separate build that uses the SDK only by Maven coordinate.
```

`gradle/module-topology.gradle.kts` is the module registry; every included module must be listed
there or the zone guard in root `build.gradle.kts` fails the build. See `docs/ARCHITECTURE.md` and
AGENTS.md "Package rules" for the layout conventions inside each module.

## Optional logging

Core technical logging remains lightweight. Add `event-logging` for durable structured events,
`logging-file` for bounded rolling files and opt-in SDK crash capture, and `event-logging-work`
for network-constrained background recovery. The host supplies the transport; no HTTP client
enters core or either logging feature. See [logging contracts and setup](docs/LOGGING.md).

The publication gate also builds a separate Java/Kotlin logging consumer under R8. The core/OTP
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

Scaffolds `sdk/features/<name>` built on the core session kit — entry point, config builder
(validated via `validateConfig`), gateway, business-error catalog, a session interface extending
`SdkSession`, a plain state class, and a runtime on `SdkSessionBase`, plus two tests — registers it
in both `settings.gradle.kts` and `gradle/module-topology.gradle.kts`, and records its initial ABI
baseline — the module is green from the first commit. The name must be lowercase, dash-case (e.g.
`face-match`), not already used, not contain a `ui` segment (that naming is reserved for
`<feature>-ui-<toolkit>` modules), not become a Kotlin hard keyword once its dashes are joined
(e.g. `in`), and not collide with a namespace another `sdk/` module already declares (e.g. `core`);
it refuses with exit code 2 and leaves the tree untouched otherwise. Then:

1. Implement the gateway with the calls this feature needs, and pick an unused 3xxx block in its
   `*Errors.kt` for its business errors — see AGENTS.md "Package rules" for the layout conventions
   (entry point at the package root, other public types in named sub-packages, engine code under
   `internal/`) and `sdk/features/otp` as a worked example.
2. Add fields to `session/*State.kt` and operations to `session/*Session.kt`, then drive them from
   `internal/*SdkRuntime.kt` (`scope.ifOpen`/`scope.call`/`scope.launch`) and `*Sdk.kt`'s `start()`,
   replacing the stubs.
3. Run `./gradlew :sdk:features:<name>:apiDump` again and commit the baseline diff with the code.

## Remove the example

Delete `sdk/features/otp*`, their entries in `settings.gradle.kts` and the topology, and the
demo/consumer code that uses them. Keep in `sdk/core` only the gateway-agnostic parts you still
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
