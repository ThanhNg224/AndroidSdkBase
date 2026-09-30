package io.github.thanhng224.sdkbase.logging.file.error

import io.github.thanhng224.sdkbase.core.error.SdkError

public object FileLoggingErrors {
    public const val STORAGE_FAILURE: Int = 2201
    public const val DIRECTORY_IN_USE: Int = 3203
    public const val CRASH_HANDLER_UNAVAILABLE: Int = 3204
    public const val INVALID_CRASH_ID: Int = 3205
    internal fun storage(): SdkError =
        SdkError.System(STORAGE_FAILURE, "File logging storage failed", isRetryable = true)
    internal fun busy(): SdkError =
        SdkError.Business(DIRECTORY_IN_USE, "File logging directory is in use", isRetryable = true)
}
