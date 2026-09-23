package io.github.thanhng224.sdkbase.core.time

/**
 * Injected time source, so anything that reads "now" (retry backoff, session timestamps, log
 * records) is controllable from a test instead of calling `System.currentTimeMillis()` directly.
 */
public fun interface Clock {

    public fun nowMillis(): Long

    public companion object {
        public val System: Clock = Clock { java.lang.System.currentTimeMillis() }
    }
}
