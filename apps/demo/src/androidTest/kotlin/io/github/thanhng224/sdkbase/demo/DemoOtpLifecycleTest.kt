package io.github.thanhng224.sdkbase.demo

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.thanhng224.sdkbase.core.error.SdkErrors
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.demo.otp.presentation.DemoActivity
import io.github.thanhng224.sdkbase.demo.otp.presentation.DemoUiState
import io.github.thanhng224.sdkbase.otp.OtpErrors
import io.github.thanhng224.sdkbase.otp.session.OtpCommand
import io.github.thanhng224.sdkbase.otp.session.OtpSession
import io.github.thanhng224.sdkbase.otp.session.OtpState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DemoOtpLifecycleTest {
    @Test
    fun recreationKeepsSessionAndClosingActivityEndsIt() {
        val scenario = launch()
        val session = ready(scenario)
        try {
            session.dispatch(OtpCommand.AppendDigit('1'))
            waitUntil { session.state.value.enteredCode == "1" }
            scenario.recreate()
            assertSame(session, ready(scenario))
            assertEquals("1", session.state.value.enteredCode)
        } finally {
            scenario.close()
        }
        val result = runBlocking { session.submit("123456") }
        assertEquals(SdkErrors.SESSION_CLOSED, (result as SdkResult.Failure).error.code)
        launch().use { reopened -> assertNotSame(session, ready(reopened)) }
    }

    @Test
    fun wrongCodeResendAndCorrectCodeUseTheRealSdkFlow() {
        launch().use { scenario ->
            val session = ready(scenario)
            val wrong = runBlocking { session.submit("000000") }
            assertEquals(OtpErrors.OTP_INVALID, (wrong as SdkResult.Failure).error.code)
            waitUntil(timeoutMillis = 20_000) { session.state.value.canResend }
            assertTrue(runBlocking { session.resend() } is SdkResult.Success)
            waitUntil { session.state.value.phase == OtpState.Phase.AwaitingCode }
            assertTrue(runBlocking { session.submit("123456") } is SdkResult.Success)
            waitUntil { session.state.value.phase == OtpState.Phase.Verified }
        }
    }

    private fun launch(): ActivityScenario<DemoActivity> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return ActivityScenario.launch(Intent(context, DemoActivity::class.java))
    }

    private fun ready(scenario: ActivityScenario<DemoActivity>): OtpSession {
        var session: OtpSession? = null
        waitUntil {
            scenario.onActivity { activity ->
                session = (activity.viewModel.uiState.value as? DemoUiState.Ready)?.session
            }
            session?.state?.value?.phase == OtpState.Phase.AwaitingCode
        }
        return checkNotNull(session)
    }

    private fun waitUntil(timeoutMillis: Long = 5_000, condition: () -> Boolean) {
        val deadline = android.os.SystemClock.uptimeMillis() + timeoutMillis
        while (!condition()) {
            check(android.os.SystemClock.uptimeMillis() < deadline) {
                "OTP state did not arrive within ${timeoutMillis}ms"
            }
            Thread.sleep(50)
        }
    }
}
