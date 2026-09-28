package io.github.thanhng224.sdkbase.core.testing

import io.github.thanhng224.sdkbase.core.error.SdkError
import io.github.thanhng224.sdkbase.core.result.SdkResult

/**
 * Assertion helpers for [SdkResult], so a test reads its expectation directly instead of
 * unwrapping the sealed type by hand. Each throws [AssertionError] naming the actual outcome.
 */
public fun <T> SdkResult<T>.assertSuccess(): T = when (this) {
    is SdkResult.Success -> value
    is SdkResult.Failure -> throw AssertionError(
        "expected SdkResult.Success but was Failure(code=${error.code}, reason=${error.reason})"
    )
}

/** Asserts this is a [SdkResult.Failure] and returns its [SdkError]. */
public fun SdkResult<*>.assertFailure(): SdkError = when (this) {
    is SdkResult.Failure -> error
    is SdkResult.Success -> throw AssertionError(
        "expected SdkResult.Failure but was Success(value=$value)"
    )
}

/** Asserts this is a [SdkResult.Failure] whose [SdkError.code] is [expectedCode]. */
public fun SdkResult<*>.assertFailure(expectedCode: Int): SdkError {
    val error = assertFailure()
    if (error.code != expectedCode) {
        throw AssertionError(
            "expected failure code $expectedCode but was ${error.code} (${error.reason})"
        )
    }
    return error
}
