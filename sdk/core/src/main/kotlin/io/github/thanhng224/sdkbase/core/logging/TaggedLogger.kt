package io.github.thanhng224.sdkbase.core.logging

/**
 * A [SdkLogger] bound to one [tag]. Obtained via [SdkLogger.tagged]; never constructed directly.
 */
public class TaggedLogger internal constructor(
    private val logger: SdkLogger,
    private val tag: String,
) {

    public fun v(message: () -> String): Unit = logger.log(LogLevel.VERBOSE, tag, null, message)

    public fun d(message: () -> String): Unit = logger.log(LogLevel.DEBUG, tag, null, message)

    public fun i(message: () -> String): Unit = logger.log(LogLevel.INFO, tag, null, message)

    public fun w(throwable: Throwable? = null, message: () -> String): Unit =
        logger.log(LogLevel.WARN, tag, throwable, message)

    public fun e(throwable: Throwable? = null, message: () -> String): Unit =
        logger.log(LogLevel.ERROR, tag, throwable, message)

    /**
     * Runs [block] unconditionally - even when nothing would actually be logged - then logs its
     * duration at VERBOSE on success, or at ERROR (with the throwable) if it throws, before
     * rethrowing. `inline` so a suspend [block] or a non-local `return` inside it works exactly as
     * if it were written at the call site.
     *
     * [traceStart]/[traceEnd] are `@PublishedApi internal`: a public inline function compiled into
     * a host cannot otherwise reach [logger]. Not meant to be called directly, but they are JVM-public
     * and therefore part of the ABI dump: keep their signatures stable.
     */
    public inline fun <T> trace(operation: String, block: () -> T): T {
        val start = traceStart()
        try {
            val result = block()
            traceEnd(operation, start, null)
            return result
        } catch (t: Throwable) {
            traceEnd(operation, start, t)
            throw t
        }
    }

    @PublishedApi
    internal fun traceStart(): Long = logger.currentTimeMillis()

    @PublishedApi
    internal fun traceEnd(operation: String, start: Long, throwable: Throwable?) {
        val durationMillis = logger.currentTimeMillis() - start
        val level = if (throwable == null) LogLevel.VERBOSE else LogLevel.ERROR
        logger.log(level, tag, throwable) { "op=$operation duration_ms=$durationMillis" }
    }
}
