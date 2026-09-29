package {{SDK_NAMESPACE}}.onboarding

import {{SDK_NAMESPACE}}.core.call.ResultCallback
import {{SDK_NAMESPACE}}.core.concurrency.DispatcherProvider
import {{SDK_NAMESPACE}}.core.environment.SdkEnvironment
import {{SDK_NAMESPACE}}.core.error.SdkError
import {{SDK_NAMESPACE}}.core.error.SdkErrors
import {{SDK_NAMESPACE}}.core.logging.LogLevel
import {{SDK_NAMESPACE}}.core.logging.SdkLogger
import {{SDK_NAMESPACE}}.core.result.SdkResult
import {{SDK_NAMESPACE}}.core.testing.RecordingLogSink
import {{SDK_NAMESPACE}}.core.testing.TestDispatcherProvider
import {{SDK_NAMESPACE}}.onboarding.config.OnboardingSdkConfig
import {{SDK_NAMESPACE}}.otp.config.OtpSdkConfig
import {{SDK_NAMESPACE}}.otp.gateway.OtpChallenge
import {{SDK_NAMESPACE}}.otp.gateway.OtpGateway
import {{SDK_NAMESPACE}}.profile.gateway.ProfileGateway
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingSdkTest {
    private data class Fixture(val config: OnboardingSdkConfig, val sink: RecordingLogSink)

    private fun fixture(
        dispatcher: DispatcherProvider,
        events: MutableList<String>,
        request: suspend () -> SdkResult<OtpChallenge> = {
            events += "request"
            SdkResult.Success(OtpChallenge("challenge", 4, 60, 0))
        },
        verify: suspend () -> SdkResult<Unit> = {
            events += "verify"
            SdkResult.Success(Unit)
        },
        profile: suspend () -> SdkResult<String> = {
            events += "profile"
            SdkResult.Success("Ada")
        },
    ): Fixture {
        val sink = RecordingLogSink()
        val logger = SdkLogger.Builder().minLevel(LogLevel.DEBUG).sink(sink).build()
        val environment = SdkEnvironment.Builder().logger(logger).dispatchers(dispatcher).build()
        val otp = OtpSdkConfig.Builder("5550100", object : OtpGateway {
            override suspend fun requestOtp(destination: String): SdkResult<OtpChallenge> = request()
            override suspend fun verifyOtp(challengeId: String, code: String): SdkResult<Unit> = verify()
        }).environment(environment).build() as SdkResult.Success
        return Fixture(OnboardingSdkConfig(otp.value, ProfileGateway { profile() }), sink)
    }

    private fun assertClosedOnce(sink: RecordingLogSink, tag: String) {
        assertEquals(1, sink.records.count { it.tag == tag && it.message.contains("session closed") })
    }

    @Test fun `runs feature gateways in order and closes both sessions`() = runTest {
        val events = mutableListOf<String>()
        val fixture = fixture(TestDispatcherProvider(StandardTestDispatcher(testScheduler)), events)
        val result = OnboardingSdk.start(fixture.config, "1234")
        assertEquals(listOf("request", "verify", "profile"), events)
        assertEquals(SdkResult.Success("Ada"), result)
        assertClosedOnce(fixture.sink, "OtpSession")
        assertClosedOnce(fixture.sink, "ProfileSession")
    }

    @Test fun `otp request failure stops later stages and closes any created session`() = runTest {
        val events = mutableListOf<String>()
        val failure = SdkResult.Failure(SdkErrors.gatewayFailure("request"))
        val fixture = fixture(
            TestDispatcherProvider(StandardTestDispatcher(testScheduler)), events,
            request = { events += "request"; failure },
        )
        assertEquals(failure, OnboardingSdk.start(fixture.config, "1234"))
        assertEquals(listOf("request"), events)
        assertEquals(0, fixture.sink.records.count { it.tag == "OtpSession" && it.message.contains("session closed") })
        assertEquals(0, fixture.sink.records.count { it.tag == "ProfileSession" && it.message.contains("session closed") })
    }

    @Test fun `verification failure stops before profile and closes otp`() = runTest {
        val events = mutableListOf<String>()
        val failure = SdkResult.Failure(SdkErrors.gatewayFailure("verify"))
        val fixture = fixture(
            TestDispatcherProvider(StandardTestDispatcher(testScheduler)), events,
            verify = { events += "verify"; failure },
        )
        assertEquals(failure, OnboardingSdk.start(fixture.config, "1234"))
        assertEquals(listOf("request", "verify"), events)
        assertClosedOnce(fixture.sink, "OtpSession")
    }

    @Test fun `cancellation during verification propagates and closes otp`() = runTest {
        val events = mutableListOf<String>()
        val enteredVerify = CompletableDeferred<Unit>()
        val verifyCancelled = CompletableDeferred<Unit>()
        val fixture = fixture(
            TestDispatcherProvider(StandardTestDispatcher(testScheduler)), events,
            verify = {
                events += "verify"
                enteredVerify.complete(Unit)
                try { awaitCancellation() } finally { verifyCancelled.complete(Unit) }
            },
        )
        val job = async { OnboardingSdk.start(fixture.config, "1234") }
        runCurrent()
        assertTrue(enteredVerify.isCompleted)
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertTrue(verifyCancelled.isCompleted)
        assertFalse(events.contains("profile"))
        assertClosedOnce(fixture.sink, "OtpSession")
    }

    @Test fun `profile failure is returned and otp session closes`() = runTest {
        val events = mutableListOf<String>()
        val failure = SdkResult.Failure(SdkErrors.gatewayFailure("profile"))
        val fixture = fixture(
            TestDispatcherProvider(StandardTestDispatcher(testScheduler)), events,
            profile = { events += "profile"; failure },
        )
        assertEquals(failure, OnboardingSdk.start(fixture.config, "1234"))
        assertEquals(listOf("request", "verify", "profile"), events)
        assertClosedOnce(fixture.sink, "OtpSession")
        assertEquals(0, fixture.sink.records.count { it.tag == "ProfileSession" && it.message.contains("session closed") })
    }

    @Test fun `profile cancellation propagates and closes otp while stopping host wait`() = runTest {
        val events = mutableListOf<String>()
        val enteredProfile = CompletableDeferred<Unit>()
        val profileCancelled = CompletableDeferred<Unit>()
        val fixture = fixture(
            TestDispatcherProvider(StandardTestDispatcher(testScheduler)), events,
            profile = {
                events += "profile"
                enteredProfile.complete(Unit)
                try { awaitCancellation() } finally { profileCancelled.complete(Unit) }
            },
        )
        val job = async { OnboardingSdk.start(fixture.config, "1234") }
        runCurrent()
        assertTrue(enteredProfile.isCompleted)
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertTrue(profileCancelled.isCompleted)
        assertClosedOnce(fixture.sink, "OtpSession")
    }

    @Test fun `callback twin delivers one result`() = runTest {
        val events = mutableListOf<String>()
        val fixture = fixture(TestDispatcherProvider(StandardTestDispatcher(testScheduler)), events)
        var delivered = 0
        var resultValue: String? = null
        OnboardingSdk.start(fixture.config, "1234", object : ResultCallback<String> {
            override fun onSuccess(value: String) { delivered++; resultValue = value }
            override fun onFailure(error: SdkError) { delivered++ }
        })
        runCurrent()
        assertEquals(1, delivered)
        assertEquals("Ada", resultValue)
        assertClosedOnce(fixture.sink, "OtpSession")
        assertClosedOnce(fixture.sink, "ProfileSession")
    }

    @Test fun `cancelled callback flow suppresses delivery and closes sessions`() = runTest {
        val events = mutableListOf<String>()
        val enteredProfile = CompletableDeferred<Unit>()
        val profileCancelled = CompletableDeferred<Unit>()
        val fixture = fixture(
            TestDispatcherProvider(StandardTestDispatcher(testScheduler)), events,
            profile = {
                events += "profile"
                enteredProfile.complete(Unit)
                try { awaitCancellation() } finally { profileCancelled.complete(Unit) }
            },
        )
        var delivered = 0
        val cancellable = OnboardingSdk.start(fixture.config, "1234", object : ResultCallback<String> {
            override fun onSuccess(value: String) { delivered++ }
            override fun onFailure(error: SdkError) { delivered++ }
        })
        runCurrent()
        assertTrue(enteredProfile.isCompleted)
        cancellable.cancel()
        runCurrent()
        assertTrue(profileCancelled.isCompleted)
        assertEquals(0, delivered)
        assertClosedOnce(fixture.sink, "OtpSession")
    }
}
