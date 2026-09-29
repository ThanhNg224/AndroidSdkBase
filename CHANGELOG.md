# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versioning follows the policy in
[docs/COMPATIBILITY.md](docs/COMPATIBILITY.md#versioning).

## [Unreleased]

### Added
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
- A callback no longer fires after `cancel()` or `close()` issued on the main thread, and a result
  that was produced but not delivered is released instead of leaked.
- Cancelling `OtpSdk.start` mid-flight no longer leaves the session's ticker running.
