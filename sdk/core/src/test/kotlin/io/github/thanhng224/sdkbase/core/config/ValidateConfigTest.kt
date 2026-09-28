package io.github.thanhng224.sdkbase.core.config

import io.github.thanhng224.sdkbase.core.error.SdkErrors
import io.github.thanhng224.sdkbase.core.result.SdkResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [validateConfig] is where every feature's `Builder.build()` validates itself: the first failing
 * [ConfigChecks.ensure] wins and stops the block, its message is evaluated only on failure, and an
 * unrelated [Exception] becomes [SdkErrors.unknown] rather than crashing the host.
 */
class ValidateConfigTest {

    @Test
    fun allChecksPassReturnsValue() {
        val result = validateConfig {
            ensure(true) { "unreachable" }
            ensure(1 + 1 == 2) { "unreachable" }
            "config"
        }

        assertEquals(SdkResult.Success("config"), result)
    }

    @Test
    fun firstFailedEnsureWins() {
        val result = validateConfig<String> {
            ensure(false) { "first failure" }
            ensure(false) { "second failure" }
            "config"
        } as SdkResult.Failure

        assertEquals(SdkErrors.INVALID_CONFIG, result.error.code)
        assertTrue(result.error.reason.contains("first failure"))
        assertTrue(!result.error.reason.contains("second failure"))
    }

    @Test
    fun laterChecksDoNotRunAfterAFailure() {
        var laterMessageEvaluated = false

        validateConfig {
            ensure(false) { "first failure" }
            ensure(false) { laterMessageEvaluated = true; "second failure" }
            "config"
        }

        assertTrue("a later ensure's message lambda must not run once an earlier one failed", !laterMessageEvaluated)
    }

    @Test
    fun passingChecksNeverEvaluateTheirMessage() {
        var passingMessageEvaluated = false

        validateConfig {
            ensure(true) { passingMessageEvaluated = true; "unreachable" }
        }

        assertTrue("a passing ensure's message lambda must only run on failure", !passingMessageEvaluated)
    }

    @Test
    fun unexpectedExceptionIsUnknown() {
        val cause = IllegalStateException("boom")

        val result = validateConfig<String> {
            throw cause
        } as SdkResult.Failure

        assertEquals(SdkErrors.UNKNOWN, result.error.code)
        assertEquals(cause, result.error.cause)
    }
}
