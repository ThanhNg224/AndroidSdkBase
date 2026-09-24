# AGENTS.md

Android SDK starter. Clone it, run `scripts/rename-project.sh`, replace the OTP example with your
own feature. Published artifacts: `core`, `otp`, `otp-ui-compose`, `bom`.

## Gates — run before saying anything is done
```bash
./gradlew check -Psdkbase.warningsAsErrors=true   # tests, lint, zone guard, apiCheck
./scripts/verify-publication.sh                   # local publish, POM checks, floor-Kotlin consumer under R8
./scripts/verify-guards.sh                        # only when you touch a guard: proves each can still fail
```

## Layout
- `sdk/core` — Android library toolkit shared by every feature: `result/`, `error/`, `call/` (`safeCall`, `RetryPolicy`), `time/` (`Clock`, `IdGenerator`), `concurrency/`, `logging/` (`SdkLogger`, `TaggedLogger`), `telemetry/`, `gateway/`.
- `sdk/features/<name>` — one published artifact per feature. Engine code lives in `internal/` and is Kotlin `internal`.
- `sdk/features/<name>-ui-compose` — optional UI artifact. Compose never enters a non-UI module.
- `sdk/bom` — lists every published module automatically.
- `apps/demo` — manual testing only; never published.
- `verification/consumer` — separate build that uses the SDK only by Maven coordinate.
- Register every new module in `gradle/module-topology.gradle.kts` or the build fails.

## Package rules
- The entry point (e.g. `OtpSdk`) sits at the module's package root; other public types get a named sub-package (`config/`, `gateway/`, `session/`…), never a grab-bag.
- Implementation is Kotlin `internal` under `internal/`; split it by layer (`internal/engine/`, `internal/runtime/`) only once a package holds more than ~12 files.
- No `utils/`, `misc/`, `helpers/` packages — name the concern instead.

## SDK rules (the build enforces the ones marked *)
- * Dependencies flow `core → feature → app`; a published module depends only on published modules.
- * Public API is frozen by `api/<module>.api` (exact match). Changing it means running `./gradlew :<module>:apiDump` and committing the diff with the code. Removing or changing a line, adding an abstract member to a host-implemented interface, or adding a sealed subtype is **breaking**.
- * Consumers may be on Kotlin 2.2: never raise `kotlinStdlibFloor` or add a dependency built with newer Kotlin without asking.
- `explicitApi()` is on: every public declaration is deliberate. Prefer `internal`.
- The host owns networking: features declare a gateway interface; no HTTP client, no DI framework, no `GlobalScope` in `sdk/`. Call host code only through `safeCall` — it maps exceptions to `SdkError` and applies its own timeout.
- Nothing escapes to the host as an exception: return `SdkResult`; map host exceptions to `SdkError`. A host-supplied `LogSink`/`TelemetrySink` is always contained — a throw from one never reaches the caller.
- Error codes are append-only: 1xxx common, 2xxx system, 3xxx feature business, 4xxx lifecycle.
- Java hosts must be able to use every entry point: builders instead of default arguments, callback interfaces next to suspend ones.
- Log only through `SdkLogger`/`TaggedLogger` — never `android.util.Log` in `sdk/` except inside `LogcatSink`. Every record is redacted before a sink sees it; mask an identifier yourself with `DefaultRedactor.mask()`.
- Unit tests for business logic (state machines, engines, config validation). No tests for Compose layouts.

## Do not
- Publish to Maven Central or add Central/Sonatype plugins or credentials. This is a base; there is nothing to release. Only `build/local-repo` is used.
- Regenerate an `.api` baseline just to make `apiCheck` pass without understanding the diff.
- Weaken or skip a gate. If a gate is wrong, fix the gate and prove it with `verify-guards.sh`.
- Commit `local.properties`, keystores, or anything under `docs/superpowers/` (local working notes).
