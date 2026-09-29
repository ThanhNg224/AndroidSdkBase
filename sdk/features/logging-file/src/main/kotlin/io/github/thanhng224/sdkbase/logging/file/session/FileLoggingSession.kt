package io.github.thanhng224.sdkbase.logging.file.session

import io.github.thanhng224.sdkbase.core.call.Cancellable
import io.github.thanhng224.sdkbase.core.call.ResultCallback
import io.github.thanhng224.sdkbase.core.logging.LogSink
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.core.session.SdkSession
import io.github.thanhng224.sdkbase.logging.file.crash.CrashReport

/** Log writes use bounded non-blocking admission. Flush before close to preserve admitted logs. */
public interface FileLoggingSession : SdkSession<FileLoggingState>, LogSink {
    /** Fences prior writes and reports any storage failure observed so far in this session. */
    public suspend fun flush(): SdkResult<Unit>
    public fun flush(callback: ResultCallback<Unit>): Cancellable
    /** Read/ack explicitly: the host chooses whether/how to upload these reports. */
    public suspend fun pendingCrashes(): SdkResult<List<CrashReport>>
    public fun pendingCrashes(callback: ResultCallback<List<CrashReport>>): Cancellable
    public suspend fun acknowledgeCrash(id: String): SdkResult<Unit>
    public fun acknowledgeCrash(id: String, callback: ResultCallback<Unit>): Cancellable
}
