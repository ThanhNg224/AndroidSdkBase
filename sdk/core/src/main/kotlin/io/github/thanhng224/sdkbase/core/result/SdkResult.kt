package io.github.thanhng224.sdkbase.core.result

import io.github.thanhng224.sdkbase.core.error.SdkError

/**
 * The only type the SDK returns across its public boundary.
 *
 * Deliberately not `kotlin.Result`: that type cannot be used as a public return type without
 * inline-class boxing surprises for Java hosts, and it cannot carry a stable numeric error code.
 */
public sealed interface SdkResult<out T> {

    public data class Success<out T>(public val value: T) : SdkResult<T>

    public data class Failure(public val error: SdkError) : SdkResult<Nothing>
}

public fun <T> SdkResult<T>.getOrNull(): T? = (this as? SdkResult.Success<T>)?.value

public fun <T> SdkResult<T>.errorOrNull(): SdkError? = (this as? SdkResult.Failure)?.error
