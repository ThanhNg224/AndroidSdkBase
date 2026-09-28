package io.github.thanhng224.sdkbase.core.testing

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingTelemetrySinkTest {

    @Test
    fun `records names in order`() {
        val sink = RecordingTelemetrySink()

        sink.onEvent("otp_started", emptyMap())
        sink.onEvent("otp_verified", mapOf("result" to "success"))

        assertEquals(listOf("otp_started", "otp_verified"), sink.names())
        assertEquals(
            listOf(
                "otp_started" to emptyMap(),
                "otp_verified" to mapOf("result" to "success"),
            ),
            sink.events,
        )
    }

    @Test
    fun `clear empties`() {
        val sink = RecordingTelemetrySink()
        sink.onEvent("otp_started", emptyMap())

        sink.clear()

        assertEquals(emptyList<String>(), sink.names())
        assertEquals(emptyList<Pair<String, Map<String, String>>>(), sink.events)
    }
}
