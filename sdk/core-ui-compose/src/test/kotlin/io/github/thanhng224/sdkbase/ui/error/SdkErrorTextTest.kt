package io.github.thanhng224.sdkbase.ui.error

import io.github.thanhng224.sdkbase.core.error.Disposition
import io.github.thanhng224.sdkbase.core.error.SdkError
import io.github.thanhng224.sdkbase.core.error.SdkErrors
import io.github.thanhng224.sdkbase.ui.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SdkErrorTextTest {
    // One instance per code in SdkErrors.all(): adding a core code fails the first test until it is listed here.
    private val coreErrors = listOf(
        SdkErrors.unknown(), SdkErrors.invalidConfig("x"), SdkErrors.cancelledByUser(),
        SdkErrors.networkUnavailable(), SdkErrors.gatewayFailure("x"), SdkErrors.timeout("x"),
        SdkErrors.notStarted(), SdkErrors.alreadyRunning(), SdkErrors.sessionClosed(),
    )

    @Test
    fun theListCoversEveryCoreCode() {
        assertEquals(SdkErrors.all().toSet(), coreErrors.map { it.code }.toSet())
    }

    @Test
    fun everyCoreErrorTheUserCanSeeHasItsOwnString() {
        coreErrors.filter { it.disposition != Disposition.SILENT }.forEach { error ->
            assertTrue("no string for ${error.code}", error.code in coreErrorMessages)
        }
    }

    @Test
    fun distinctCodesGetDistinctStrings() {
        val resources = coreErrorMessages.values.toList()
        assertEquals(resources.size, resources.toSet().size)
    }

    @Test
    fun textIsChosenByCodeNotByReason() {
        val a = SdkError.System(SdkErrors.TIMEOUT, "one reason")
        val b = SdkError.System(SdkErrors.TIMEOUT, "another reason")
        assertEquals(sdkErrorMessageRes(a), sdkErrorMessageRes(b))
        assertEquals(R.string.sdk_core_ui_compose_error_timeout, sdkErrorMessageRes(a))
    }

    @Test
    fun aCodeCoreDoesNotOwnGetsTheGenericString() {
        assertEquals(R.string.sdk_core_ui_compose_error_unknown, sdkErrorMessageRes(SdkError.Business(3999, "x")))
        assertNotEquals(R.string.sdk_core_ui_compose_error_unknown, sdkErrorMessageRes(SdkErrors.networkUnavailable()))
    }
}
