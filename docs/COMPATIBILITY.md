# Compatibility

You are here because `apiCheck` failed with `Public ABI of :<module> differs from api/<name>.api` or
`Missing ABI baseline`. The gate is `sdkbase.abi` (`build-logic/src/main/kotlin/sdkbase.abi.gradle.kts`,
`sdkbase/abi/AbiTasks.kt`). If this document and the code disagree, trust the code.

## ABI policy: exact match

Every published module carries a committed baseline at `api/<module>.api`. `apiCheck` fails on
**any** difference from the current dump — additions included, not just removals. Breaking changes
include:

- Removing or changing a public signature.
- Adding an abstract member to an interface a host implements (e.g. `LogSink`, `Redactor`,
  `OtpGateway`) — every existing implementation stops compiling.
- Adding a subtype to a sealed type (e.g. a new `SdkError` subclass) — an exhaustive `when` on it in
  host code stops compiling.

To update deliberately: run `./gradlew :<module>:apiDump` and commit the new `api/<module>.api` in
the **same commit** as the code change, never as a follow-up "fix build" commit.

`apiDump`/`apiCheck` run `org.jetbrains.kotlin:abi-tools` — the engine KGP itself uses — directly
against the release AAR or JVM jar, because KGP's own `abiValidation` silently checks nothing on an
Android module built with AGP 9's built-in Kotlin.

## The Kotlin floor

Every published module publishes `kotlin-stdlib` pinned to `kotlinStdlibFloor` (`gradle/libs.versions.toml`)
and compiles with `languageVersion`/`apiVersion` set to that same floor — `kotlin.stdlib.default.dependency=false`
in `gradle.properties` stops the toolchain's own newer stdlib from riding along instead.
`verification/consumer` is the proof: it builds on Kotlin 2.2.10 and 2.4.20, selecting the compiler
through AGP's buildscript classpath, and resolves the SDK only by Maven coordinate. Raising `kotlinStdlibFloor` is a
compatibility decision, not a routine bump — it drops support for any consumer still below it.

## Host floor

No library convention sets `aarMetadata.minCompileSdk` or `minAgpVersion`. The floor a consumer
actually needs is whatever its own dependencies already require — adding a redundant, hand-picked
floor on top only risks being wrong in one direction or the other.

### Verified consumer profiles

The headless core/OTP, OTP + Compose UI, and optional logging consumers build a minified release
with Java and Kotlin call sites on this matrix:

| Compiler profile | AGP | Kotlin compiler | Runtime stdlib | compileSdk / minSdk |
|---|---|---|---|---|
| Floor | 9.4.1 | 2.2.10 | 2.2.21 | 37 / 24 |
| Current | 9.4.1 | 2.4.20 | 2.2.21 | 37 / 24 |

`./scripts/verify-publication.sh` runs Floor; `--current` runs Current. Each checks the selected
Kotlin Gradle plugin, resolved runtime stdlib, fresh R8 mapping and retained SDK classes. The
headless core/OTP runtime graph must contain neither Compose nor WorkManager; the logging
consumer explicitly opts into WorkManager and remains Compose-free. CI runs both profiles on every PR.
These are build/consumer guarantees, not proof for older AGP/Kotlin hosts or runtime devices.

## Logging API and R8

`SdkLogger.Builder`, `TaggedLogger`'s `v`/`d`/`i`/`w`/`e`/`trace`, and the `LogSink`/`Redactor`
`fun interface`s are public ABI, tracked by `api/core.api` like everything else in `:sdk:core`.
`sdk/core/consumer-rules.pro` ships an `-assumenosideeffects` rule so a consumer's own minified
release build can strip `TaggedLogger.v`/`d` calls; `i`/`w`/`e` and every sink still run.

Every exception handed to a sink by `SdkLogger` is a detached snapshot. The original type name is
retained as redacted text in `toString()`, together with redacted message, cause/suppressed graph
and stack-frame text; line numbers remain intact. Sinks must not compare exception identity or
cast the snapshot to the source exception's subtype. The source exception and `SdkError.cause`
are untouched. Snapshot-read failures emit the redacted message with `throwable = null`;
redactor exceptions drop the record. `Redactor.None` deliberately disables text redaction but
still produces a detached snapshot. `LogcatSink` renders only logger-created snapshots and ignores
raw throwables supplied through manually constructed records.

This hardening is classified as a patch under Versioning (no public ABI change). Hosts that used
the old sink identity/subtype behavior must update their sink to consume the snapshot contract.

## Java interop

Published modules set `-jvm-default=enable`, keeping the `DefaultImpls` bridge Java callers link
against. Every public entry point is Java-usable: `OtpSdkConfig` is a builder (Java cannot use a
Kotlin default argument), and `OtpCallbackGateway`/`CompletionCallback` give a callback-based host a
form of every gateway that would otherwise require a `suspend fun`.

The same is true in the other direction, for a `suspend` entry point the SDK calls the host *back*
through: `OtpSdk.start(config, callback: ResultCallback<OtpSession>): Cancellable` and
`OtpSession.submit`/`resend`/`observeState` each have a `Continuation`-free, callback-based twin,
built on core's `launchCallback`/`observe`. Every one of these is `@JvmStatic` where it sits on an
`object`, delivers on the environment's main dispatcher, and returns a `Cancellable` the host can
call from any thread. `JavaConsumer.startFromJava` in `verification/consumer` is the proof: it never
names `Continuation` anywhere, including inside the callbacks it passes back to `submit`/
`observeState`.

## UI resources

A UI module's string resources (`sdk_otp_ui_compose_*`) are a contract with the host: a host
customises the bundled screen's text by declaring a string with the same name in its own app, which
wins over the library's at merge time. Renaming or removing one silently reverts every host that
overrode it, so it counts as a breaking change (see Versioning). Adding one is not.

## Host-owned adapters

An adapter built on a vendor binary with no Maven coordinate cannot be published: a consumer could not
resolve the file. Such a binary lives in the `vendor` zone and its adapter in the `adapter` zone, both
unpublished (`sdk/vendor/fake-sms-vendor` and `sdk/adapters/otp-fake-sms` are the worked example), and
the build proves nothing published reaches them (zone guard rule 4; `verify-publication.sh` checks
`build/local-repo`). A host that wants one copies the adapter and the vendor's binary into its own
project, or implements the feature's gateway itself.

## Versioning

Every artifact shares one version (`sdkbase.version`), aligned for hosts by the BOM, and follows
SemVer. The `api/*.api` diff decides the bump:

| Change | Bump |
|---|---|
| An `.api` line removed or changed; an abstract member added to an interface a host implements; a subtype added to a sealed type; `kotlinStdlibFloor` or `minSdk` raised; a UI string resource renamed or removed | **major** |
| `.api` lines only added; a new error code; a new UI string resource | **minor** |
| No `.api` change | **patch** |

While the version is `0.x`, a minor release may break — but only if the changelog says so under a
`### Breaking` heading of that release.

**Deprecation** (applies only once the API is frozen; see "Pre-release" in `AGENTS.md` — before that,
change or delete directly): mark the old API `@Deprecated(level = DeprecationLevel.WARNING)` with a
`ReplaceWith` for at least one minor release, then `ERROR` for one more, and remove it only in a
major. **Error codes** are never reused or renumbered, even after the feature that owned them is
removed.

Every public change adds a line to `CHANGELOG.md` in the same commit as its `.api` diff.

## Optional logging artifacts

`event-logging`, `logging-file` and `event-logging-work` add public API and error codes without
changing existing core/OTP signatures or raising the Kotlin/minSdk floor. This is an additive
**minor** change under Versioning. Each has its own committed ABI baseline and publishes sources,
Javadoc and a floor-pinned POM. WorkManager is pinned to 2.10.5 in the optional adapter to preserve
the current host floor; it is absent from core and both logging feature graphs.

The adapter keeps its reflectively constructed worker name and constructor in consumer R8 rules,
so queued WorkManager requests remain resolvable after minification. Renaming that worker or
changing the persisted queue format requires an explicit migration. These checks prove build and
consumer compatibility; real-device background execution still requires device verification.
