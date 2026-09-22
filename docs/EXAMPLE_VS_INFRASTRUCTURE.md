# Example vs. Infrastructure

This base contains zero business logic carried over from any other project. But its one worked
example — OTP verification — is deliberately interleaved with the reusable infrastructure, because
the architecture puts every host gateway in `:sdk:core` and every error code in one catalog
(`SdkErrors.kt`). If you are cloning this base to start a real SDK, you must be able to tell, file by
file, what to keep and rename versus what to delete outright — otherwise a stray `OtpGateway` lives
on in your published contract forever.

This document is that file-by-file list. It is derived from the actual repository tree; if it drifts
from reality, trust `git ls-files` and fix this doc, not the other way around.

## Infrastructure — keep and rename

These have no OTP-specific meaning. Run `scripts/rename-project.sh` and keep everything it touches.

- `build-logic/**` — all convention plugins (`sdkbase.android.library`, `sdkbase.android.compose`,
  `sdkbase.android.publishing`, `sdkbase.abi`, `sdkbase.abi.jvm`, `sdkbase.kotlin.jvm`) and the ABI
  task implementations under `build-logic/src/main/kotlin/sdkbase/abi/`.
- `gradle/libs.versions.toml` — the version catalog.
- `gradle/module-topology.gradle.kts` — the zone/publishing registries (remove the four OTP module
  entries per below; keep the mechanism).
- The root `build.gradle.kts` zone guard (the `allowedTargets` map, `directProjectDeps`,
  `transitiveProjectDeps`, and `gradle.projectsEvaluated` block).
- `gradle.properties`, `settings.gradle.kts` (keep the include list minus the OTP modules),
  `gradle/wrapper/**`, `gradlew`/`gradlew.bat`.
- `scripts/**` — `verify-publication.sh`, `verify-kotlin-metadata.sh`, `rename-project.sh`.
- `.github/workflows/ci.yml`, `renovate.json`.
- `.gitignore`, `.gitattributes`, `.editorconfig`.
- `verification/consumer/**` — the external Java+Kotlin consumer harness. Its own imports and
  dependency coordinates change to point at your new artifacts, but the harness itself (two
  consumers, one Java and one Kotlin, built with `-PsdkLocalRepo=...`) is infrastructure.
- In `:sdk:core`
  (`sdk/core/src/main/kotlin/io/github/thanhng224/sdkbase/core/`): `SdkResult.kt`, `SdkError.kt`,
  `SdkLogger.kt`, `DispatcherProvider.kt`, `gateway/TelemetrySink.kt`, and `gateway/GatewayCallback.kt`
  — a generic `GatewayCallback<T>` (`onSuccess`/`onFailure`) with no OTP-specific shape, used to give
  any suspending gateway a non-suspending, Java-implementable form.
- In `:sdk:platform` (`sdk/platform/src/main/kotlin/io/github/thanhng224/sdkbase/platform/`):
  `AndroidDispatchers.kt`, `AndroidSdkLogger.kt`.
- `SdkErrors.kt` itself is infrastructure as a *mechanism* — see the hybrid section below for its
  contents.

## Example — delete outright

Everything OTP-shaped. Deleting these is the point of cloning this base.

- `sdk/capabilities/otp-engine/` (module, sources, tests, `api/otp-engine.api` baseline).
- `sdk/capabilities/otp-ui-compose/` (module, sources, `api/otp-ui-compose.api` baseline).
- `sdk/facades/otp-sdk/` (module, sources, tests, `api/otp-sdk.api` baseline).
- `sdk/core/src/main/kotlin/io/github/thanhng224/sdkbase/core/gateway/OtpGateway.kt` — the
  suspending OTP gateway interface.
- `sdk/core/src/main/kotlin/io/github/thanhng224/sdkbase/core/gateway/OtpCallbackGateway.kt` — the
  non-suspending, Java-implementable form of `OtpGateway` plus its `asGateway()` bridge to the
  suspending form. It is example code (its shape is entirely OTP's `send`/`verify`/`resend`), even
  though it exists to demonstrate the same `GatewayCallback` infrastructure a real feature's own
  callback-style gateway would reuse.
- `apps/demo/src/main/kotlin/io/github/thanhng224/sdkbase/demo/FakeOtpGateway.kt`,
  `DemoActivity.kt`, `DemoOtpViewModel.kt` — the OTP screen wiring in the demo app. Keep the
  `apps/demo` module itself (it is your integration harness and shrinker canary); replace this
  wiring with your own feature's.
- Then remove the four OTP module paths (`:sdk:capabilities:otp-engine`,
  `:sdk:capabilities:otp-ui-compose`, `:sdk:facades:otp-sdk`, and the demo's dependency on them) from
  **both** registries in `gradle/module-topology.gradle.kts` (`zones` and `publishedArtifacts`), from
  `settings.gradle.kts`'s `include(...)` list, and from `apps/demo/build.gradle.kts`'s dependencies.
  Deleting a module's directory without deregistering it does not pass the zone guard — see below.

## The one hybrid: `SdkErrors.kt`

`sdk/core/src/main/kotlin/io/github/thanhng224/sdkbase/core/SdkErrors.kt` is one file with two kinds
of content: the catalog *mechanism* (infrastructure — keep) and the OTP business codes it currently
holds (example — delete). State plainly which is which, and that the example range is the template
for your own feature's range, not something to preserve:

| Range | Codes | Keep? |
|---|---|---|
| 1xxx common | `UNKNOWN`, `INVALID_CONFIG`, `CANCELLED_BY_USER` | keep |
| 2xxx system | `NETWORK_UNAVAILABLE`, `GATEWAY_FAILURE`, `TIMEOUT` | keep |
| 3xxx business | `OTP_INVALID`, `OTP_EXPIRED`, `OTP_ATTEMPTS_EXCEEDED`, `OTP_RESEND_TOO_SOON` | **delete — replace with your feature's codes** |
| 4xxx lifecycle | `NOT_STARTED`, `ALREADY_RUNNING` | keep |

Keep the object, its KDoc, `all()`, and the 1xxx/2xxx/4xxx entries and factory functions. Remove the
3xxx entries, their factory functions (`otpInvalid()`, `otpExpired()`, `otpAttemptsExceeded()`,
`otpResendTooSoon(...)`), and their `all()` references. Start your own feature's business codes at
`3000` in a fresh, append-only range — see `docs/ERROR_CODES.md`. Regenerate that doc from the new
`SdkErrors.kt` once you've made the change; don't hand-edit its table to match.

## Proving the strip worked

```bash
./gradlew projects        # the zone guard rejects a module you deleted but left registered
./gradlew check           # every remaining baseline still matches
./scripts/verify-publication.sh
```

If `./gradlew projects` fails with a zone violation right after a delete, that is the guard doing its
job — an unregistered or dangling reference to a module you removed. The fix is to finish updating
`gradle/module-topology.gradle.kts` and `settings.gradle.kts`, never to weaken or bypass the guard.
