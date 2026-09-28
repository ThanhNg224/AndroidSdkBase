package io.github.thanhng224.sdkbase.core.testing

import io.github.thanhng224.sdkbase.core.telemetry.TelemetrySink
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A [TelemetrySink] that keeps every event it receives, so a test can assert on what a feature
 * emitted. Thread-safe.
 */
public class RecordingTelemetrySink : TelemetrySink {

    private val backing = CopyOnWriteArrayList<Pair<String, Map<String, String>>>()

    /** A snapshot of every (name, attributes) event received so far, oldest first. */
    public val events: List<Pair<String, Map<String, String>>>
        get() = backing.toList()

    override fun onEvent(name: String, attributes: Map<String, String>) {
        backing += name to attributes
    }

    /** The name of every event received so far, oldest first. */
    public fun names(): List<String> = backing.map { it.first }

    /** Discards every event received so far. */
    public fun clear() {
        backing.clear()
    }
}
