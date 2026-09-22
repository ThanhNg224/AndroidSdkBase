# Error Codes

Every error the SDK can emit is created in one place: `sdk/core/src/main/kotlin/io/github/thanhng224/sdkbase/core/SdkErrors.kt`.
This table is generated from that file's `SdkErrors.all()` catalog — do not hand-maintain a second
list. If this table and `SdkErrors.kt` ever disagree, regenerate the table from the source, don't
edit the source to match the table.

**Codes are append-only.** A released code is never renumbered or reused for a different meaning.
Adding a new failure means adding a new constant in the appropriate family's range; removing a
constant that a host may already be branching on is a breaking change.

| Code | Family | Constant | Meaning | What a host should do |
|-----:|--------|----------|---------|------------------------|
| 1000 | Common | `UNKNOWN` | An unclassified failure, usually wrapping a caught `Throwable`. | Log the cause and show a generic failure state; treat as retryable only if the surrounding flow allows it. |
| 1001 | Common | `INVALID_CONFIG` | The `Builder` for a facade (e.g. `OtpSdkConfig`) was given an invalid value. | Fix the host's configuration; this is a programmer error, not a runtime condition to recover from. |
| 1002 | Common | `CANCELLED_BY_USER` | The in-progress operation was cancelled by the user. | Return to the prior screen/state; not an error to report to telemetry as a failure. |
| 2000 | System | `NETWORK_UNAVAILABLE` | The host's gateway could not reach the network. | Show a retry affordance; this is a transport condition, not a business rejection. |
| 2001 | System | `GATEWAY_FAILURE` | The host-implemented gateway threw or returned a failure. | Inspect `SdkError.cause`/`reason` for the host's own diagnostic; typically retryable. |
| 2002 | System | `TIMEOUT` | An operation exceeded its allotted time. | Offer retry; consider whether the host's gateway implementation itself is slow. |
| 3000 | Business | `OTP_INVALID` | The submitted OTP code did not match. | Prompt the user to re-enter the code; decrement any client-side attempt counter the host keeps. |
| 3001 | Business | `OTP_EXPIRED` | The OTP challenge expired before it was verified. | Prompt the user to request a new code. |
| 3002 | Business | `OTP_ATTEMPTS_EXCEEDED` | Too many incorrect verification attempts. | Block further attempts and require a fresh challenge (e.g. resend). |
| 3003 | Business | `OTP_RESEND_TOO_SOON` | A resend was requested before the cool-down elapsed. | Disable the resend action until `retryAfterSeconds` (carried in the error's `reason`) has passed. |
| 4000 | Lifecycle | `NOT_STARTED` | An operation was attempted before the SDK/session was started. | Fix the call order in the host; start the session first. |
| 4001 | Lifecycle | `ALREADY_RUNNING` | A new session was started while one was already running. | Either await/observe the existing session or explicitly stop it before starting another. |

## Families

Defined in `SdkError.kt`: `Common` (1xxx), `System` (2xxx), `Business` (3xxx), `Lifecycle` (4xxx).
Hosts should branch on `SdkError.code` (the stable contract), never on `SdkError.reason` (diagnostic
text that may change without notice).

Business codes 3000–3003 belong to the OTP worked example specifically. If you are stripping the
example per `docs/EXAMPLE_VS_INFRASTRUCTURE.md`, this is the range you delete and replace with your
own feature's codes — starting a fresh 3xxx range is the template, not a requirement to reuse these
exact numbers.
