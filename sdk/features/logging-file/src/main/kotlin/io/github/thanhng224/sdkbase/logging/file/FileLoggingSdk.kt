package io.github.thanhng224.sdkbase.logging.file

import io.github.thanhng224.sdkbase.core.call.Cancellable
import io.github.thanhng224.sdkbase.core.call.ResultCallback
import io.github.thanhng224.sdkbase.core.call.launchCallback
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.logging.file.config.FileLoggingConfig
import io.github.thanhng224.sdkbase.logging.file.error.FileLoggingErrors
import io.github.thanhng224.sdkbase.logging.file.internal.FileLoggingRuntime
import io.github.thanhng224.sdkbase.logging.file.session.FileLoggingSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

public object FileLoggingSdk {
    /** Explicit start/stop owns the writer and, only when enabled, the process crash handler. */
    @JvmStatic
    public suspend fun start(config: FileLoggingConfig): SdkResult<FileLoggingSession> {
        val environment = config.environment
        var runtime: FileLoggingRuntime? = null
        return try {
            val created = withContext(environment.dispatchers.io) {
                FileLoggingRuntime.create(config, environment).also { runtime = it }
            }
            val failure = created.start()
            if (failure != null) {
                created.close()
                SdkResult.Failure(failure)
            } else {
                currentCoroutineContext().ensureActive()
                SdkResult.Success(created)
            }
        } catch (e: CancellationException) {
            runtime?.close()
            throw e
        } catch (_: java.nio.channels.OverlappingFileLockException) {
            runtime?.close()
            SdkResult.Failure(FileLoggingErrors.busy())
        } catch (_: Exception) {
            runtime?.close()
            SdkResult.Failure(FileLoggingErrors.storage())
        }
    }

    /** Java-callable form of [start]; a session that cannot be delivered is closed, not leaked. */
    @JvmStatic
    public fun start(config: FileLoggingConfig, callback: ResultCallback<FileLoggingSession>): Cancellable =
        launchCallback(config.environment.dispatchers, callback, onUndelivered = { it.close() }) { start(config) }
}
