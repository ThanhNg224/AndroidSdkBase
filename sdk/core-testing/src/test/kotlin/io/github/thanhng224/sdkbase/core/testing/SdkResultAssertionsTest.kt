package io.github.thanhng224.sdkbase.core.testing

import io.github.thanhng224.sdkbase.core.error.SdkErrors
import io.github.thanhng224.sdkbase.core.result.SdkResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SdkResultAssertionsTest {

    @Test
    fun `assertSuccess returns the value`() {
        val result: SdkResult<String> = SdkResult.Success("value")

        assertEquals("value", result.assertSuccess())
    }

    @Test
    fun `assertSuccess throws naming the error code on failure`() {
        val result: SdkResult<String> = SdkResult.Failure(SdkErrors.invalidConfig("bad"))

        try {
            result.assertSuccess()
            fail("expected AssertionError")
        } catch (e: AssertionError) {
            assertTrue(e.message.orEmpty().contains("1001"))
        }
    }

    @Test
    fun `assertFailure returns the error`() {
        val error = SdkErrors.invalidConfig("bad")
        val result: SdkResult<String> = SdkResult.Failure(error)

        assertEquals(error, result.assertFailure())
    }

    @Test
    fun `assertFailure throws on success`() {
        val result: SdkResult<String> = SdkResult.Success("value")

        try {
            result.assertFailure()
            fail("expected AssertionError")
        } catch (e: AssertionError) {
            // expected
        }
    }

    @Test
    fun `assertFailure with expected code passes when it matches`() {
        val result: SdkResult<String> = SdkResult.Failure(SdkErrors.invalidConfig("bad"))

        assertEquals(SdkErrors.INVALID_CONFIG, result.assertFailure(SdkErrors.INVALID_CONFIG).code)
    }

    @Test
    fun `assertFailure with expected code throws when it differs`() {
        val result: SdkResult<String> = SdkResult.Failure(SdkErrors.invalidConfig("bad"))

        try {
            result.assertFailure(SdkErrors.NETWORK_UNAVAILABLE)
            fail("expected AssertionError")
        } catch (e: AssertionError) {
            // expected
        }
    }
}
