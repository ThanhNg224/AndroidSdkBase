package io.github.thanhng224.sdkbase.core.error

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SdkErrorsTest {

    @Test
    fun `every catalog code is unique`() {
        val codes = SdkErrors.all()
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun `codes are grouped by family range`() {
        // 1xxx common, 2xxx system, 3xxx business, 4xxx lifecycle.
        assertTrue(SdkErrors.invalidConfig("x") is SdkError.Common)
        assertTrue(SdkErrors.networkUnavailable() is SdkError.System)
        assertTrue(SdkErrors.notStarted() is SdkError.Lifecycle)

        assertEquals(1, SdkErrors.invalidConfig("x").code / 1000)
        assertEquals(2, SdkErrors.networkUnavailable().code / 1000)
        assertEquals(4, SdkErrors.notStarted().code / 1000)
    }

    @Test
    fun `core owns no business codes`() {
        assertTrue(SdkErrors.all().none { it / 1000 == 3 })
    }

    @Test
    fun `sessionClosed is 4002 lifecycle`() {
        val error = SdkErrors.sessionClosed()
        assertTrue(error is SdkError.Lifecycle)
        assertEquals(SdkErrors.SESSION_CLOSED, error.code)
        assertEquals(4002, error.code)
    }

    @Test
    fun `all contains 4002`() {
        assertTrue(SdkErrors.all().contains(SdkErrors.SESSION_CLOSED))
    }

    @Test
    fun `only network and timeout failures are retryable`() {
        val retryable = listOf(
            SdkErrors.unknown(), SdkErrors.invalidConfig("x"), SdkErrors.cancelledByUser(),
            SdkErrors.networkUnavailable(), SdkErrors.gatewayFailure("x"), SdkErrors.timeout("x"),
            SdkErrors.notStarted(), SdkErrors.alreadyRunning(), SdkErrors.sessionClosed(),
        ).filter { it.isRetryable }.map { it.code }
        assertEquals(listOf(SdkErrors.NETWORK_UNAVAILABLE, SdkErrors.TIMEOUT), retryable)
    }

    @Test
    fun `an error is not retryable unless it says so`() {
        assertEquals(false, SdkError.Business(3999, "x").isRetryable)
        assertEquals(true, SdkError.Business(3999, "x", null, true).isRetryable)
    }

    @Test
    fun `disposition is derived from family and retryability`() {
        assertEquals(Disposition.SILENT, SdkError.Lifecycle(4999, "x").disposition)
        assertEquals(Disposition.SILENT, SdkError.Lifecycle(4999, "x", null, true).disposition)
        assertEquals(Disposition.DIALOG_RETRY, SdkError.System(2999, "x", null, true).disposition)
        assertEquals(Disposition.DIALOG_TERMINAL, SdkError.Business(3999, "x").disposition)
        assertEquals(Disposition.DIALOG_TERMINAL, SdkError.Common(1999, "x").disposition)
    }

    @Test
    fun `a catalog can override the derived disposition`() {
        val error = SdkError.Business(3999, "x", disposition = Disposition.INLINE_RETRY)
        assertEquals(Disposition.INLINE_RETRY, error.disposition)
        assertEquals(false, error.isRetryable)
    }

    @Test
    fun `catalog dispositions`() {
        assertEquals(Disposition.SILENT, SdkErrors.cancelledByUser().disposition)
        assertEquals(Disposition.DIALOG_RETRY, SdkErrors.networkUnavailable().disposition)
        assertEquals(Disposition.DIALOG_RETRY, SdkErrors.timeout("x").disposition)
        assertEquals(Disposition.DIALOG_TERMINAL, SdkErrors.invalidConfig("x").disposition)
        assertEquals(Disposition.SILENT, SdkErrors.sessionClosed().disposition)
    }
}
