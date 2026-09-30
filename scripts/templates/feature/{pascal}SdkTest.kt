package {{pkg}}

import {{ns}}.core.environment.SdkEnvironment
import {{ns}}.core.logging.LogLevel
import {{ns}}.core.logging.SdkLogger
import {{ns}}.core.testing.RecordingLogSink
import {{ns}}.core.testing.TestDispatcherProvider
import {{ns}}.core.testing.assertSuccess
import {{pkg}}.config.{{pascal}}SdkConfig
import {{pkg}}.gateway.{{pascal}}Gateway
import {{pkg}}.session.{{pascal}}State
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [{{pascal}}Sdk.start] is business logic, not UI, so it gets a unit test per the repo's testing
 * rule — see `:sdk:features:otp`'s OtpSdkTest for the fuller pattern once this feature has real
 * gateway calls to drive.
 */
class {{pascal}}SdkTest {

    private val gateway = object : {{pascal}}Gateway {}

    private fun configOf(logger: SdkLogger = SdkLogger.NoOp): {{pascal}}SdkConfig =
        {{pascal}}SdkConfig.Builder(gateway)
            .environment(
                SdkEnvironment.Builder()
                    .logger(logger)
                    .dispatchers(TestDispatcherProvider(StandardTestDispatcher()))
                    .build(),
            )
            .build()
            .assertSuccess()

    @Test
    fun startReturnsIdleSession() = runTest {
        val session = {{pascal}}Sdk.start(configOf()).assertSuccess()

        assertEquals({{pascal}}State.Phase.Idle, session.state.value.phase)
        session.close()
    }

    @Test
    fun closeIsIdempotent() = runTest {
        val sink = RecordingLogSink()
        val logger = SdkLogger.Builder().minLevel(LogLevel.DEBUG).sink(sink).build()
        val session = {{pascal}}Sdk.start(configOf(logger)).assertSuccess()

        session.close()
        session.close()

        assertEquals(1, sink.messages(LogLevel.DEBUG).count { it.contains("session closed") })
    }
}
