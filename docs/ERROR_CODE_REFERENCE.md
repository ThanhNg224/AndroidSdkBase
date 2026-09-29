# Error-code reference

Every failure the SDK surfaces is an `SdkError` with a numeric `code`. Hosts branch on the code, never
on `reason` (diagnostic text that may change). This file is the human-readable catalog; the build
keeps it honest. Read it before adding, changing or retiring an error.

## Rules

- **Codes are append-only.** Never renumber a code, never reuse one, never delete its record — not
  even after the feature that owned it is removed. A code that is no longer declared is marked
  `retired` here and in the ledger, and stays reserved.
- **Families:** `1xxx` common, `2xxx` system/transport, `3xxx` business, `4xxx` lifecycle. `SdkErrors`
  (core) owns `1xxx`, `2000–2002` and `4xxx`. A feature owns its own `x1xx`-style block inside
  `2xxx`/`3xxx` (OTP: `3000–3003`; event logging: `21xx`/`31xx`; file logging: `22xx`/`32xx`). Pick a
  block no module uses; the build rejects a duplicate anywhere.
- **`disposition` says how a UI presents the failure** (`INLINE_RETRY`, `DIALOG_RETRY`,
  `DIALOG_TERMINAL`, `SILENT`); it is independent of `isRetryable`, which says whether repeating the
  call may succeed on its own. Unless a catalog sets one, it is derived: lifecycle → `SILENT`,
  retryable → `DIALOG_RETRY`, otherwise `DIALOG_TERMINAL`.
- **The UI chooses text by `code`, never by `reason`.**
- Log and telemetry records are redacted before a sink sees them; never put user data in `reason`.

## The ledger

`sdk/error-codes.ledger` is the committed record of every code, one `module NAME code [retired]` line
each. `./gradlew check` runs `checkErrorCatalog`, which fails when:

- a declared code is missing from the ledger, or its number differs from the ledger's;
- a non-retired ledger entry is no longer declared (mark it `retired` instead of deleting it);
- one code number belongs to two errors, live or retired;
- a code is outside `1000–4999`;
- the table below and the ledger do not list exactly the same codes with the same status.

To add a code: declare the `const val` in the module's `*Errors.kt`, run `./gradlew errorCatalogDump`
(it only appends), add the matching table row, and commit all three together with a `CHANGELOG.md`
line. `errorCatalogDump` refuses to change an existing code, so a renumbering has to be a visible
hand-edit of the ledger, which review must reject. A new feature scaffolded by
`scripts/new-feature.sh` starts with an empty catalog; register codes only when it has real errors.

## Catalog

`Retryable` is the catalog's `isRetryable`. `Disposition` is the catalog's presentation hint.

| Code | Module | Name | Disposition | Retryable | Status |
|---|---|---|---|---|---|
| `1000` | core | `UNKNOWN` | `DIALOG_TERMINAL` | no | active |
| `1001` | core | `INVALID_CONFIG` | `DIALOG_TERMINAL` | no | active |
| `1002` | core | `CANCELLED_BY_USER` | `SILENT` | no | active |
| `2000` | core | `NETWORK_UNAVAILABLE` | `DIALOG_RETRY` | yes | active |
| `2001` | core | `GATEWAY_FAILURE` | `DIALOG_TERMINAL` | no | active |
| `2002` | core | `TIMEOUT` | `DIALOG_RETRY` | yes | active |
| `2101` | features/event-logging | `STORAGE_FAILURE` | `DIALOG_RETRY` | yes | active |
| `2102` | features/event-logging | `DELIVERY_IN_PROGRESS` | `DIALOG_RETRY` | yes | active |
| `2103` | features/event-logging | `DELIVERY_FAILURE` | `DIALOG_RETRY` | yes | active |
| `2104` | features/event-logging | `SCHEDULER_FAILURE` | `DIALOG_RETRY` | yes | active |
| `2201` | features/logging-file | `STORAGE_FAILURE` | `DIALOG_RETRY` | yes | active |
| `3000` | features/otp | `OTP_INVALID` | `INLINE_RETRY` | no | active |
| `3001` | features/otp | `OTP_EXPIRED` | `INLINE_RETRY` | no | active |
| `3002` | features/otp | `OTP_ATTEMPTS_EXCEEDED` | `DIALOG_TERMINAL` | no | active |
| `3003` | features/otp | `OTP_RESEND_TOO_SOON` | `INLINE_RETRY` | no | active |
| `3101` | features/event-logging | `INVALID_EVENT` | `DIALOG_TERMINAL` | no | active |
| `3103` | features/event-logging | `QUEUE_FULL` | `DIALOG_RETRY` | yes | active |
| `3201` | features/logging-file | `INVALID_CONFIG` | `DIALOG_TERMINAL` | no | active |
| `3203` | features/logging-file | `DIRECTORY_IN_USE` | `DIALOG_RETRY` | yes | active |
| `3204` | features/logging-file | `CRASH_HANDLER_UNAVAILABLE` | `DIALOG_TERMINAL` | no | active |
| `3205` | features/logging-file | `INVALID_CRASH_ID` | `DIALOG_TERMINAL` | no | active |
| `4000` | core | `NOT_STARTED` | `SILENT` | no | active |
| `4001` | core | `ALREADY_RUNNING` | `SILENT` | no | active |
| `4002` | core | `SESSION_CLOSED` | `SILENT` | no | active |

The logging features are background infrastructure: their errors normally reach the host or the
logs, not an end-user dialog. Their dispositions are the derived defaults and are informational.
