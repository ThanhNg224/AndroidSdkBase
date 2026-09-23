package io.github.thanhng224.sdkbase.core.result

import io.github.thanhng224.sdkbase.core.error.SdkErrors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SdkResultOperatorsTest {

    private val success: SdkResult<Int> = SdkResult.Success(2)
    private val failure: SdkResult<Int> = SdkResult.Failure(SdkErrors.unknown())

    @Test
    fun `map transforms a success and leaves a failure untouched`() {
        assertEquals(SdkResult.Success(4), success.map { it * 2 })
        assertEquals(failure, failure.map { it * 2 })
    }

    @Test
    fun `flatMap chains a success and short-circuits a failure`() {
        val chained: SdkResult<String> = success.flatMap { SdkResult.Success(it.toString()) }
        assertEquals(SdkResult.Success("2"), chained)
        assertEquals(failure, failure.flatMap { SdkResult.Success(it.toString()) })
    }

    @Test
    fun `fold picks the branch matching the result`() {
        assertEquals("value=2", success.fold(onSuccess = { "value=$it" }, onFailure = { "error" }))
        assertEquals("error", failure.fold(onSuccess = { "value=$it" }, onFailure = { "error" }))
    }

    @Test
    fun `onSuccess runs only for a success and returns the same instance`() {
        var invoked = false
        val returned = success.onSuccess { invoked = true }
        assertTrue(invoked)
        assertEquals(success, returned)

        invoked = false
        val returnedFailure = failure.onSuccess { invoked = true }
        assertFalse(invoked)
        assertEquals(failure, returnedFailure)
    }

    @Test
    fun `onFailure runs only for a failure and returns the same instance`() {
        var invoked = false
        val returned = failure.onFailure { invoked = true }
        assertTrue(invoked)
        assertEquals(failure, returned)

        invoked = false
        val returnedSuccess = success.onFailure { invoked = true }
        assertFalse(invoked)
        assertEquals(success, returnedSuccess)
    }
}
