# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versioning follows the policy in
[docs/COMPATIBILITY.md](docs/COMPATIBILITY.md#versioning).

## [Unreleased]

### Added
- Build tooling: extracted feature templates, registry-derived inclusion and UI/adapter/composition scaffolds; tested
  zone-guard convention and independent CI jobs for check, publication modes, integration and guards.
- Tooling: root `spotlessCheck` enforces Kotlin and Gradle Kotlin formatting in `check`;
  `spotlessApply` fixes formatting; `rename-project.sh` formats the renamed tree, and `verify-guards.sh`
  proves the formatter gate and every scaffold stay format-clean.
- Zone `vendor` for a local binary with no Maven coordinate: never published, only an adapter may
  depend on it, and `verify-publication.sh` proves neither reaches `build/local-repo`. Worked example:
  `sdk/vendor/fake-sms-vendor` and the unpublished adapter `sdk/adapters/otp-fake-sms`.
- `:sdk:core-ui-compose` (new zone `ui`): the shared Compose toolkit — `SdkColors`,
  `SdkSpacing`/`SdkDimens`, `contrastRatio`/`meetsContrast`, `sdkErrorMessage`/`sdkErrorMessageRes`
  (text chosen by error code, English and Vietnamese, host-overridable by resource name) and
  `ProvideSdkLocale`. Only `<name>-ui-<toolkit>` modules may depend on it. `otp-ui-compose` uses it
  for every non-OTP error; its `sdk_otp_ui_compose_error_network/timeout/generic` strings are removed.
- `checkSourceRules` fails a colour literal (Kotlin `Color(0x…)`, named colours, `parseColor`, or
  `#hex`/`@android:color/` in `res/`) in a UI module; new build guards for the `ui` zone.
- `docs/ERROR_CODE_REFERENCE.md`, `sdk/error-codes.ledger` and the `checkErrorCatalog` gate (part of
  `check`): error codes are recorded once and can only be appended; a renumbered, deleted, reused or
  undocumented code fails the build. `./gradlew errorCatalogDump` appends new codes to the ledger.
- `Disposition` and `SdkError.disposition`: how a UI presents a failure (`INLINE_RETRY`,
  `DIALOG_RETRY`, `DIALOG_TERMINAL`, `SILENT`). Derived from the family and `isRetryable` unless a
  catalog overrides it; the existing `SdkError` constructors keep working. `OtpErrors` marks a wrong or
  expired code and a too-early resend as `INLINE_RETRY`, exhausted attempts as `DIALOG_TERMINAL`.
- **Logging (additive minor):** optional `event-logging` with an ordered bounded durable queue,
  stable delivery IDs, allowlisted/redacted attributes, session timing, metadata-only diagnostics,
  Java callbacks and host-owned transport; optional `event-logging-work` adds network-constrained
  WorkManager retry and periodic recovery after process recreation.
- Optional `logging-file`: bounded asynchronous rolling-file sink, explicit flush/close,
  retention and SDK-filtered opt-in crash capture that preserves the host crash handler.
  Event system errors 2101–2104, file storage 2201, event logging 3101/3103, and file logging
  3201/3203–3205 are append-only; existing core and OTP API remains unchanged.
- A separate Maven-only Java/Kotlin logging consumer checks the optional artifacts under R8 on
  floor/current compilers while core/OTP still resolves neither Compose nor WorkManager.
- Verification-only profile/onboarding/callback-adapter fixtures prove composition cleanup and
  optional adapter wiring, then Java/Kotlin consumption through Maven coordinates under R8.
- Headless and Compose UI consumer profiles verify Kotlin compilers 2.2.10 and 2.4.20 on AGP
  9.4.1, with runtime stdlib kept at 2.2.21 and no Compose in the headless graph.
- Module zones `composition` (the only place two features meet), `adapter` (optional host bridges on
  a concrete client) and `testing`; a feature may depend on another feature only as its UI module.
- Build gates: `checkDependencyPolicy` (no HTTP client or DI framework on an SDK classpath,
  transitively) and `checkSourceRules` (no `GlobalScope`, no `android.util.Log` outside
  `LogcatSink`, no feature-owned `CoroutineScope`, no public multi-property `data class`); each is
  proven by `scripts/verify-guards.sh`.
- `:sdk:core-testing`, a published test kit: `FakeClock`, `SequentialIdGenerator`,
  `TestDispatcherProvider`, `RecordingLogSink`, `RecordingTelemetrySink`, `SdkResult` assertions and
  `GatewayContract`, which lets a host check its own gateway against the SDK's contract.
- `SdkEnvironment`: one logger, telemetry sink, dispatcher provider, clock and id generator shared
  by every feature.
- A Java-callable outbound API: `ResultCallback`, `Cancellable`, `StateListener`, with core's
  `launchCallback`/`observe` behind every callback twin.
- A session kit: `SdkSession`, `SessionScope`, `StateStore`, `SdkSessionBase`; plus
  `awaitCallback`/`awaitCompletion`, `emitSafely` and `validateConfig`.
- `SdkErrors.SESSION_CLOSED` (4002) and `SdkError.isRetryable`.
- `scripts/new-feature.sh`, which scaffolds a registered feature built on the session kit that
  passes `check` as generated.
- OTP UI strings in English and Vietnamese, overridable by the host through resources.

### Changed
- **Logging behavior (patch; public ABI unchanged):** `LogRecord.throwable` is now a detached,
  redacted snapshot, including cause/suppressed messages and stack-frame text. Sinks must not
  compare it with the source exception or cast it to that exception's subclass. A snapshot-read
  failure retains the redacted message without a throwable; a redactor failure drops the record.
- OTP runs on the session kit: one `SessionScope` per session; timer ticks go through the state
  lock; `close()` cancels in-flight work and later calls return `SESSION_CLOSED`.
- `RetryPolicy.TransientErrors` retries exactly the errors that declare `isRetryable`.
- The OTP screen shows text chosen by error code, never `SdkError.reason`, and the backspace key has
  an accessibility label.
- OTP rejects an invalid `OtpChallenge` from the host (blank id, `codeLength` outside 4..10,
  non-positive expiry, negative resend delay) with `GATEWAY_FAILURE` instead of entering a broken
  state.

### Breaking
- `OtpSdkConfig.Builder.logger()`/`telemetry()` are replaced by `environment(SdkEnvironment)`.
- `OtpSession` extends `SdkSession<OtpState>`; `state`/`observeState`/`close` are inherited.
- `OtpState`, `OtpChallenge` and `OtpColors` are plain classes: `copy` and `componentN` are gone.

### Fixed
- Completed callback calls and cancelled state observers detach their owning jobs, so a
  long-lived session does not accumulate empty child jobs and its parent can complete normally.
- A callback no longer fires after `cancel()` or `close()` issued on the main thread, and a result
  that was produced but not delivered is released instead of leaked.
- Cancelling `OtpSdk.start` mid-flight no longer leaves the session's ticker running.
