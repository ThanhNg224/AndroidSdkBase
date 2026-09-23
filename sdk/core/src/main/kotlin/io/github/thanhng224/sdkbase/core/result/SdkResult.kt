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

/** Transforms a [SdkResult.Success] value; a [SdkResult.Failure] passes through unchanged. */
public inline fun <T, R> SdkResult<T>.map(transform: (T) -> R): SdkResult<R> = when (this) {
    is SdkResult.Success -> SdkResult.Success(transform(value))
    is SdkResult.Failure -> this
}

/** Chains a [SdkResult]-returning [transform]; a [SdkResult.Failure] short-circuits. */
public inline fun <T, R> SdkResult<T>.flatMap(transform: (T) -> SdkResult<R>): SdkResult<R> =
    when (this) {
        is SdkResult.Success -> transform(value)
        is SdkResult.Failure -> this
    }

/** Reduces either branch to a single value of [R]. */
public inline fun <T, R> SdkResult<T>.fold(onSuccess: (T) -> R, onFailure: (SdkError) -> R): R =
    when (this) {
        is SdkResult.Success -> onSuccess(value)
        is SdkResult.Failure -> onFailure(error)
    }

/** Runs [action] for its side effect when this is a [SdkResult.Success]; returns this unchanged. */
public inline fun <T> SdkResult<T>.onSuccess(action: (T) -> Unit): SdkResult<T> {
    if (this is SdkResult.Success) action(value)
    return this
}

/** Runs [action] for its side effect when this is a [SdkResult.Failure]; returns this unchanged. */
public inline fun <T> SdkResult<T>.onFailure(action: (SdkError) -> Unit): SdkResult<T> {
    if (this is SdkResult.Failure) action(error)
    return this
}
