package io.github.thanhng224.sdkbase.core.testing

import io.github.thanhng224.sdkbase.core.time.Clock
import java.util.concurrent.atomic.AtomicLong

/**
 * A [Clock] a test moves by hand instead of depending on wall-clock time. Thread-safe: backed by
 * an [AtomicLong].
 */
public class FakeClock(startMillis: Long) : Clock {

    public constructor() : this(0L)

    private val currentMillis = AtomicLong(startMillis)

    override fun nowMillis(): Long = currentMillis.get()

    /** Moves the clock forward by [millis] (a negative value moves it back). */
    public fun advanceBy(millis: Long) {
        currentMillis.addAndGet(millis)
    }

    /** Sets the clock to an absolute value. */
    public fun set(millis: Long) {
        currentMillis.set(millis)
    }
}
