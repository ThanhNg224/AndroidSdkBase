# Compatibility

You are here because `apiCheck` failed with `Public ABI of :<module> differs from api/<name>.api` or
`Missing ABI baseline`. The gate is `sdkbase.abi` (`build-logic/src/main/kotlin/sdkbase.abi.gradle.kts`,
`sdkbase/abi/AbiTasks.kt`). If this document and the code disagree, trust the code.

## ABI policy: exact match

Every published module carries a committed baseline at `api/<module>.api`. `apiCheck` fails on
**any** difference from the current dump — additions included, not just removals. Breaking changes
include:

- Removing or changing a public signature.
- Adding an abstract member to an interface a host implements (e.g. `SdkLogger`) — every existing
  implementation stops compiling.
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
`verification/consumer` is the proof: it builds on Kotlin 2.2.10 (AGP 9.4.1's bundled default, no
KGP override) and resolves the SDK only by Maven coordinate. Raising `kotlinStdlibFloor` is a
compatibility decision, not a routine bump — it drops support for any consumer still below it.

## Host floor

No library convention sets `aarMetadata.minCompileSdk` or `minAgpVersion`. The floor a consumer
actually needs is whatever its own dependencies already require — adding a redundant, hand-picked
floor on top only risks being wrong in one direction or the other.

## Java interop

Published modules set `-jvm-default=enable`, keeping the `DefaultImpls` bridge Java callers link
against. Every public entry point is Java-usable: `OtpSdkConfig` is a builder (Java cannot use a
Kotlin default argument), and `OtpCallbackGateway`/`CompletionCallback` give a callback-based host a
form of every gateway that would otherwise require a `suspend fun`.
