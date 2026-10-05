# Engineering Guidelines

An Android SDK template with a manual demo host. The module map, enforced boundaries, and SDK contracts are described below and in [Architecture](docs/ARCHITECTURE.md).

## Workflow

- Read only the owning documents relevant to the task, then inspect current implementation, callers, and existing tests.
- Make the smallest complete change. Add infrastructure or abstractions only for a concrete requirement or failure mode.
- Work in the current branch and checkout. Do not create a branch or worktree unless explicitly requested; preserve other contributors' edits.
- Ask when unresolved intent or a tradeoff changes the work. Continue independent work while waiting.
- Handle small and tightly coupled changes directly. Delegate only when independent tracks reduce total effort; use a reviewer for high-risk changes or when requested.
- Use the smallest level in [Verification](docs/VERIFICATION.md). Test behavior, state, persistence, security, and concurrency when affected; do not add tests that merely mirror layout or styling.
- Report exact commands and results. Keep local checks, archive checks, builds, remote CI, and device evidence distinct.
- Keep rules in their owning documents and link elsewhere. Preserve active plans and decision records; keep handoff plans local under `docs/plans/` and delete them when completed.
- Follow [Git workflow](docs/GIT_FLOW.md). Do not commit, push, tag, publish, or deploy unless explicitly requested.

## Documents

- [Architecture](docs/ARCHITECTURE.md): ownership, dependencies, and data flow.
- [Verification](docs/VERIFICATION.md): risk levels, commands, side effects, and proof boundaries.
- [Git workflow](docs/GIT_FLOW.md): existing branch, commit, and release conventions.

## Layout
- `sdk/core` — shared Android toolkit: result/error, calls, time, concurrency, logging, telemetry, gateway, environment, session, and config.
- `sdk/core-testing` — published fakes and assertions; use via `testImplementation`; only `bom` and `app` may target it outside tests.
- `sdk/core-ui-compose` — shared Compose tokens, spacing, contrast, error text and locale; only named feature UI modules plus `bom`/`app` may depend on it; see `docs/THEMING.md`.
- `sdk/features/remote-config` — one-shot example: a host gateway returns an immutable snapshot, with no session.
- `sdk/features/<name>` — one headless feature artifact; scaffold with `./scripts/new-feature.sh <name>`; implementation in `internal/`.
- `sdk/features/<name>-ui-compose` — optional UI artifact; Compose never enters a non-UI module.
- `sdk/composition/<flow>` — the only place multiple features meet.
- `sdk/adapters/<feature>-<lib>` — optional host bridge; only SDK zone allowed HTTP clients/DI, and nothing depends on it.
- `sdk/vendor/<name>` — local binary wrapper, never published; only adapters may depend on it. `fake-sms-vendor`/`otp-fake-sms` are examples.
- `apps/demo` is manual-only. `verification/consumer` is a separate coordinate-only build.
- Register modules in `gradle/module-topology.gradle.kts`; Settings derives includes. Scaffold other zones with `./scripts/new-module.sh --zone ui|adapter|composition|vendor <name>` (UI takes its feature name; adapter supports `--unpublished`; vendor is always unpublished).

## Package rules
- The entry point (e.g. `OtpSdk`) sits at the module's package root; other public types get a named sub-package (`config/`, `gateway/`, `session/`…), never a grab-bag.
- Implementation is Kotlin `internal` under `internal/`; split it by layer (`internal/engine/`, `internal/runtime/`) only once a package holds more than ~12 files.
- No `utils/`, `misc/`, `helpers/` packages — name the concern instead.

## SDK rules (the build enforces the ones marked *)
- * Dependencies flow `core → feature → composition → app` (`adapter` sits beside composition); a published module depends only on published modules.
- * A feature depends on another feature only as its UI module (`<name>-ui-<toolkit> → <name>`); two different features meet only in a composition module.
- * Public API is frozen by `api/<module>.api` (exact match). Changing it means running `./gradlew :<module>:apiDump` and committing the diff with the code. Removing or changing a line, adding an abstract member to a host-implemented interface, or adding a sealed subtype is **breaking**.
- * Consumers may be on Kotlin 2.2: never raise `kotlinStdlibFloor` or add a dependency built with newer Kotlin without asking.
- * A public `data class` in `sdk/` main sources may have at most one constructor property — `copy`/`componentN` freeze the property list for every consumer (`checkSourceRules`); use a plain class with `equals`/`hashCode`/`toString` for more than one.
- UI modules use no colour literal (`checkSourceRules`).
- `explicitApi()` is on: every public declaration is deliberate. Prefer `internal`.
- * The host owns networking: features declare a gateway interface; no HTTP client or DI framework in a core/feature/composition classpath, transitively (`checkDependencyPolicy`). Call host code only through `safeCall` — it maps exceptions to `SdkError` and applies its own timeout.
- * No `GlobalScope` in `sdk/`, and a feature/composition module owns no `CoroutineScope` of its own — one `SessionScope` per session owns every coroutine (`checkSourceRules`).
- Nothing escapes to the host as an exception: return `SdkResult`; map host exceptions to `SdkError`. A host-supplied `LogSink`/`TelemetrySink` is always contained — a throw from one never reaches the caller.
- Error codes are append-only once the API is frozen (see Pre-release below): 1xxx common, 2xxx system, 3xxx feature business, 4xxx lifecycle. A new code is recorded with `./gradlew errorCatalogDump` and a row in `docs/ERROR_CODE_REFERENCE.md`; never renumber or delete one — mark it `retired` (`checkErrorCatalog`). Every `SdkError` has a `Disposition`; the UI chooses text by `code`, never by `reason`.
- A public change (an `.api` diff, a new error code, a UI string resource) adds a line to `CHANGELOG.md` in the same commit; the version bump it implies is in `docs/COMPATIBILITY.md` "Versioning".
- Java hosts must be able to use every entry point: builders instead of default arguments, callback interfaces next to suspend ones. For an outbound call (the SDK calling the host back), build the callback-based twin on core's `launchCallback`/`observe` (`core/call/`, `core/session/`) rather than hand-rolling threading/cancellation — see `OtpSdk.start(config, callback)`.
- One config, one entry point: a feature's `*Config` carries the host gateway and its `SdkEnvironment`, and its `Builder.build()` validates once and returns `SdkResult` (`validateConfig`). The entry point exposes `start(config)` plus its `start(config, callback)` twin (likewise any other entry, e.g. `deliverPending`). A Java callback gateway goes through a `Builder` constructor overload (`asGateway()`), never a second entry-point overload. A session a host may fake is a public interface extending `SdkSession`, implemented under `internal/`.
- A public class with hand-written `equals`/`hashCode` is tested with `assertValueSemantics` (`core-testing`): one differing instance per property.
- * Log only through `SdkLogger`/`TaggedLogger` — never `android.util.Log` in `sdk/` except inside `LogcatSink` (`checkSourceRules`). Every record is redacted before a sink sees it; mask an identifier yourself with `DefaultRedactor.mask()`.
- Unit tests for business logic (state machines, engines, config validation). No tests for Compose layouts.

## Pre-release: breaking changes are allowed
No release has been tagged, so no consumer exists. Until the maintainer freezes the API (a line here
saying "API frozen from <tag>"), a breaking change is the default way to fix a bad design — do not
keep the old shape alive.
- Change or delete a public API directly. No compatibility overloads, aliases, `@Deprecated` shims, or
  legacy resource names "so existing hosts keep working" — there are none.
- Still run `./gradlew :<module>:apiDump` and commit the `.api` diff with the code, and add the
  `CHANGELOG.md` line. Batch `apiDump` at the end of a change instead of after every edit.
- Error codes too: renumber or delete one by editing the `*Errors.kt` constant, its
  `sdk/error-codes.ledger` line and its `docs/ERROR_CODE_REFERENCE.md` row together, in one commit. No
  `retired` marker for a code nobody has seen. `checkErrorCatalog` still runs unchanged.
- Every other gate is unchanged.
- `@JvmOverloads` and callback twins are Java usability, not compatibility — keep them.

## Do not
- Publish to Maven Central or add Central/Sonatype plugins or credentials. This is a base; there is nothing to release. Only `build/local-repo` is used.
- Regenerate an `.api` baseline just to make `apiCheck` pass without understanding the diff.
- Weaken or skip a gate. If a gate is wrong, fix the gate and prove it with `verify-guards.sh`.
- Commit `local.properties`, keystores, or anything under `docs/plans/` (local, git-ignored plans). Delete a plan once its work is done.
