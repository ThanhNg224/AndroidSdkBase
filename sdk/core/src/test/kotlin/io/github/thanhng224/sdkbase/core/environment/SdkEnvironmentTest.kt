package io.github.thanhng224.sdkbase.core.environment

import io.github.thanhng224.sdkbase.core.concurrency.AndroidDispatchers
import io.github.thanhng224.sdkbase.core.concurrency.DispatcherProvider
import io.github.thanhng224.sdkbase.core.logging.SdkLogger
import io.github.thanhng224.sdkbase.core.telemetry.TelemetrySink
import io.github.thanhng224.sdkbase.core.time.Clock
import io.github.thanhng224.sdkbase.core.time.IdGenerator
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SdkEnvironmentTest {

    @Test
    fun `defaults are no-op and system`() {
        val environment = SdkEnvironment.Default

        assertSame(SdkLogger.NoOp, environment.logger)
        assertSame(TelemetrySink.None, environment.telemetry)
        assertSame(AndroidDispatchers, environment.dispatchers)
        assertSame(Clock.System, environment.clock)
        assertSame(IdGenerator.Uuid, environment.idGenerator)
    }

    @Test
    fun `builder overrides each field`() {
        val logger = SdkLogger.Builder().build()
        val telemetry = TelemetrySink { _, _ -> }
        val dispatchers = object : DispatcherProvider {
            override val main = Dispatchers.Unconfined
            override val default = Dispatchers.Unconfined
            override val io = Dispatchers.Unconfined
        }
        val clock = Clock { 42L }
        val idGenerator = IdGenerator { "custom-id" }

        val environment = SdkEnvironment.Builder()
            .logger(logger)
            .telemetry(telemetry)
            .dispatchers(dispatchers)
            .clock(clock)
            .idGenerator(idGenerator)
            .build()

        assertSame(logger, environment.logger)
        assertSame(telemetry, environment.telemetry)
        assertSame(dispatchers, environment.dispatchers)
        assertSame(clock, environment.clock)
        assertSame(idGenerator, environment.idGenerator)
        assertEquals(42L, environment.clock.nowMillis())
        assertEquals("custom-id", environment.idGenerator.newId())
    }
}
