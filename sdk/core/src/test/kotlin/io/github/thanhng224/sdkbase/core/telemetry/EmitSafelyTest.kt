package io.github.thanhng224.sdkbase.core.telemetry

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [emitSafely] is the one place telemetry is emitted from: a throwing [TelemetrySink] must never
 * escape to the caller, since telemetry is ancillary and host-supplied.
 */
class EmitSafelyTest {

    @Test
    fun forwardsEvent() {
        val events = mutableListOf<Pair<String, Map<String, String>>>()
        val sink = TelemetrySink { name, attributes -> events += name to attributes }

        sink.emitSafely("otp.started", mapOf("attempt" to "1"))

        assertEquals(listOf("otp.started" to mapOf("attempt" to "1")), events)
    }

    @Test
    fun throwingSinkIsContained() {
        val sink = TelemetrySink { _, _ -> error("sink boom") }

        // Must not throw.
        sink.emitSafely("otp.started")
    }
}
