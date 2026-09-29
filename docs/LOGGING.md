# Logging

The base provides three optional artifacts. Keep technical logging in core when that is enough;
add only the capabilities your host needs.

| Artifact | Capability | Required transport/runtime |
|---|---|---|
| `event-logging` | Ordered durable business events, bounded telemetry admission, session timing and queue health | Host gateway; no HTTP client/WorkManager/Compose |
| `logging-file` | Bounded asynchronous rolling logs and opt-in SDK crash capture | Core only; app-private files |
| `event-logging-work` | Network-constrained background delivery after process recreation | WorkManager 2.10.5; host Application provider |

Use the BOM for aligned versions. Existing core and OTP dependencies do not bring any of these
artifacts into the host automatically.

## Business events

```kotlin
val config = EventLoggingConfig.Builder("business-events", context.noBackupFilesDir)
    .allowedAttributeKeys(setOf("sdk_version", "app_version", "outcome", "duration_ms"))
    .commonAttributes(mapOf("sdk_version" to sdkVersion, "app_version" to appVersion))
    .maxEvents(500)
    .maxBytes(1_048_576)
    .build().getOrNull() ?: return
val gateway = EventLoggingGateway { record ->
    // Host HTTP implementation. Send record.id as an idempotency key.
    api.deliver(record)
}
val session = EventLoggingSdk.start(config, gateway, environment).getOrNull() ?: return
session.track(EventLoggingEvent.Builder("sdk_started")
    .screenId("entry")
    .attributes(mapOf("outcome" to "success"))
    .build())
session.flush()
session.close()
```

`track` returns `SdkResult<Unit>`. Success means accepted into the durable queue, not acknowledged
by your server. Admission refuses a full queue rather than discarding an already accepted event.
Disk mutations use a separate file lock from the delivery lease, so an enqueue can complete while
a gateway request is in flight. `flush` removes only the ID acknowledged by the gateway and keeps
failed deliveries queued. It returns the first delivery/storage failure.

One session owns its coroutines. Closing cancels live delivery/retry; persisted events remain for
a later session or background worker. `deliverPending` performs a one-shot drain without starting
live retries or scheduling follow-up work. Two foreground/worker/process drainers cannot hold the
same queue's delivery lease together; the competing drainer receives a retryable failure.

Delivery is **at least once**. A process can die after your server accepts an event but before local
acknowledgement; the same ID may be sent again. The host/server must deduplicate by that ID.
Cancellation can happen after a durable enqueue, so a cancelled caller must not assume no event
was persisted. Repeating `track` creates a new event ID.

Attributes from both common metadata and individual events pass through the exact allowlist,
redaction and size limits before persistence or delivery. Known sensitive attribute keys have
their entire value masked, including spaces and separators. Host redactors run before mandatory
redaction and cannot disable it; custom scrubbing runs once at admission rather than changing a
stored value again on every retry. Event attributes override a common
attribute with the same key. IDs, timestamps, elapsed session time, action, level and screen are
structured record fields; use controlled action/screen names. Add app/SDK/device metadata only as
explicit allowlisted common attributes. No account ID, token, image, raw document, endpoint or
automatic device identifier is added. Default redaction is heuristic: mask known sensitive data
at the source and use the custom redactor for host-specific formats.

Queue diagnostics expose counts, byte totals, failure codes and delivery state. They do not
contain event values, gateway responses or exception messages. Both entry points and session
operations have callback twins for Java; `EventLoggingCallbackGateway` adapts a callback HTTP
client without a Java `Continuation`. The external consumer contains working call sites.

To route feature telemetry to the queue, pass `session.telemetrySink` to
`SdkEnvironment.Builder().telemetry(...)` for those features. This bridge uses bounded,
non-blocking admission and can reject events when busy; watch `rejectedEvents`. Use `track`
when the caller needs confirmation that an event reached disk.

Use a stable **opaque** namespace and one consistent config for that namespace across foreground
and background delivery. Retention expires old events. Unsupported/corrupt queue files fail
closed with a non-retryable storage error instead of silently losing accepted events. The host
must investigate/repair these files; repeated immediate delivery retries cannot repair them.
The periodic recovery request remains registered until explicitly cancelled. Queue files are app-private, bounded and
redacted; this artifact does not provide encryption. Store under `noBackupFilesDir` to avoid
backing up SDK logs. A file rename plus file fsync protects process-recreation recovery; no power
loss or filesystem-failure guarantee is claimed.

## Background recovery

Implement `EventLoggingWorkProvider` on your Application. `resolve(namespace)` reconstructs the
same config, host gateway and environment from a fresh process; it must be bounded and must not
perform blocking network work. Unknown namespaces return null. Configure
`EventLoggingWorkScheduler(context)` on the foreground event config. The provider may return a
config with that same scheduler: the one-shot worker bypasses scheduling.

The scheduler awaits WorkManager's durable enqueue. Startup establishes a unique periodic
recovery request before returning a session; this closes the crash window between file commit
and one-time wake-up. Coalesced one-time requests use `KEEP`, so repeated app starts cannot accumulate a work chain.
The periodic recovery request also handles a wake-up racing with a finishing worker. Workers require a connected network and use exponential backoff.
Work Data contains only the opaque namespace; the host keeps credentials and transport objects.

There is one recurring recovery request per namespace (minimum interval 15 minutes). Android
controls execution time, so this is eventual recovery rather than a 15-minute deadline. It
continues after the foreground session closes. When disabling logging, call the scheduler's
`cancel(namespace)` (or callback twin) after closing the foreground session so it cannot
schedule more work. Cancellation preserves the queue files; choose any deletion policy in the host.

A scheduling failure after durable acceptance is recorded in diagnostics; the accepted event
remains queued. Startup scheduling failure returns failure rather than claiming background
recovery is available. Delivery gateways must cooperate with cancellation for timeout/worker-stop
behavior; the SDK cannot stop a blocking host API or undo a server request.

[WorkManager persistent work](https://developer.android.com/topic/libraries/architecture/workmanager)
and [unique-work policies](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/manage-work)
provide the Android scheduling behavior. The adapter keeps its reflectively created worker name
and constructor through R8. Supply a host WorkerFactory that delegates unknown workers if your
app customizes WorkManager.

## Technical files and crash capture

```kotlin
val fileConfig = FileLoggingConfig.Builder(File(context.noBackupFilesDir, "sdk-files"))
    .maxFiles(4)
    .maxFileBytes(262_144)
    .maxRecordBytes(16_384)
    .captureCrashes(false)
    .build()
val files = FileLoggingSdk.start(fileConfig, environment).getOrNull() ?: return
val logger = SdkLogger.Builder().sink(files).build()
logger.tagged("SdkFlow").i { "started" }
files.flush()
files.close()
```

The sink copies/redacts a bounded record synchronously, then uses non-blocking bounded admission.
One writer runs on IO. Overflow increments the dropped count; `flush` fences admitted writes and
reports storage failures observed during this session. **Flush before close** to preserve admitted
technical records; close cancels the writer and can discard queued records. Technical file logging
is best effort. A directory lease permits one writer per dedicated directory. Restart enforces
updated count/byte/retention limits. Only owned log/crash filenames are managed.

Crash capture is disabled by default. Opt in explicitly with `captureCrashes(true)` and specify
`sdkPackagePrefixes` for a renamed SDK. It captures only a bounded Throwable graph with matching
SDK stack frames, redacts before disk, and stores a bounded number of reports synchronously before
passing the original Throwable unchanged to the previous host handler. This terminal persistence
performs synchronous disk IO; it does not launch a coroutine in a dying process. A throwing redactor/storage
operation still delegates. It requires a previous host handler and permits only one SDK capture
owner. Closing restores the previous handler only if the host has not replaced it.

`pendingCrashes` returns redacted reports after restart; the host chooses upload/consent policy.
Call `acknowledgeCrash(report.id)` after host delivery succeeds. Crash reports contain diagnostic
text and remain private until the host exports them. Regular technical logs are rolling files;
crash files are retained independently under the configured byte/count/age limits.

## Verification

Run the repository strict `check` and local publication gates. The logging consumer builds
Java/Kotlin host callbacks, an Application restoration provider and the optional WorkManager
worker under R8 on floor/current compilers. Unit tests cover queue persistence/order, competing
drainers, cancellation, input privacy, storage errors, byte limits, file rotation and crash handler
ownership. Build/consumer tests do not prove Android OS scheduling on a real device.
