package io.github.thanhng224.sdkbase.logging.file.internal

import io.github.thanhng224.sdkbase.logging.file.config.FileLoggingConfig

/** Only one SDK capture is installed; always delegates the original Throwable unchanged. */
internal class CrashCapture(
    private val config: FileLoggingConfig,
    private val storage: FileStorage,
    private val now: () -> Long,
) : Thread.UncaughtExceptionHandler {
    private var previous: Thread.UncaughtExceptionHandler? = null

    fun install(): Boolean = synchronized(guard) {
        if (owner != null) return false
        val host = Thread.getDefaultUncaughtExceptionHandler() ?: return false
        previous = host
        Thread.setDefaultUncaughtExceptionHandler(this)
        owner = this
        true
    }

    fun uninstall() = synchronized(guard) {
        if (owner === this) {
            if (Thread.getDefaultUncaughtExceptionHandler() ===
                this
            ) {
                Thread.setDefaultUncaughtExceptionHandler(previous)
            }
            owner = null
        }
    }

    override fun uncaughtException(thread: Thread, error: Throwable) {
        try {
            synchronized(guard) {
                if (owner !== this) return@synchronized
                val captured = renderThrowable(error, config.maxRecordBytes, config.sdkPackagePrefixes)
                if (captured.hasSdkFrame) {
                    val sanitized = redactForStorage(config, captured.text)
                    storage.saveCrash(boundedBytes(sanitized, config.maxRecordBytes), now())
                }
            }
        } catch (_: Throwable) {
            // A terminal crash boundary must delegate even when capture itself fails (including OOM).
        } finally {
            previous?.uncaughtException(thread, error)
        }
    }

    companion object {
        private val guard = Any()
        private var owner: CrashCapture? = null
    }
}
