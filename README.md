# AndroidSdkBase

A base for a publishable Android SDK where module boundaries, the public ABI, the Kotlin floor, and
the external consumer build are enforced by gates, not by review. One worked example — OTP
verification — exercises every layer end to end so you have something real to delete.

## Gates

| Gate | What fails it | Command |
|---|---|---|
| `check` | A module crossing a zone boundary, a changed/removed public signature, a failing test, lint | `./gradlew check -Psdkbase.warningsAsErrors=true` |
| `verify-publication.sh` | A broken POM, an unpinned `kotlin-stdlib`, the Kotlin-2.2.10 consumer failing to build, R8 stripping SDK code | `./scripts/verify-publication.sh` |
| `verify-guards.sh` | A guard above that can no longer fail on a real violation | `./scripts/verify-guards.sh` |

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
sdk/core                         Android library contracts shared by every feature: results, errors, logging.
sdk/features/<name>               one published artifact per feature; engine code is internal/.
sdk/features/<name>-ui-compose    optional UI artifact; Compose never enters a non-UI module.
sdk/bom                           lists every published module automatically.
apps/demo                         manual testing only; never published.
verification/consumer             separate build that uses the SDK only by Maven coordinate.
```

`gradle/module-topology.gradle.kts` is the module registry; every included module must be listed
there or the zone guard in root `build.gradle.kts` fails the build. See `docs/ARCHITECTURE.md`.

## Start a new SDK

```bash
./scripts/rename-project.sh --group com.acme --namespace com.acme.paykit --name PayKit \
  --developer-id acme --developer-name "Acme Inc." --developer-url https://acme.com \
  --repo-url https://github.com/acme/paykit
./gradlew check -Psdkbase.warningsAsErrors=true
./scripts/verify-publication.sh
```

## Add a feature

1. Create `sdk/features/<name>` applying `sdkbase.android.library`, `sdkbase.abi`, and
   `sdkbase.publishing`.
2. Register it in both lists in `gradle/module-topology.gradle.kts` — `zones` and, if it ships,
   `publishedArtifacts`.
3. Run `./gradlew :sdk:features:<name>:apiDump` and commit the baseline with the code.

## Remove the example

Delete `sdk/features/otp*`, their entries in `settings.gradle.kts` and the topology, and the
demo/consumer code that uses them. Keep in `sdk/core` only the gateway-agnostic parts you still
need — `SdkResult`, `SdkError`/`SdkErrors`, `SdkLogger`.

## Publishing

Local file repository only (`build/local-repo`). **Maven Central is intentionally not set up while
this is a base** — there is nothing to publish yet. A derived SDK adds a Central Portal account,
signing keys in CI secrets, and vanniktech's `publishToMavenCentral`.

## License

Apache 2.0.
