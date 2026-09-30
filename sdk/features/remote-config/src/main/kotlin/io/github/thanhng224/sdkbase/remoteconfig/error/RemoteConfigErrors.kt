package io.github.thanhng224.sdkbase.remoteconfig.error

import io.github.thanhng224.sdkbase.core.error.SdkError

/** Business errors of the one-shot remote config example. */
public object RemoteConfigErrors {
    public const val INVALID_RESPONSE: Int = 3301

    public fun invalidResponse(): SdkError =
        SdkError.Business(INVALID_RESPONSE, "Remote config revision is blank")

    public fun all(): List<Int> = listOf(INVALID_RESPONSE)
}
