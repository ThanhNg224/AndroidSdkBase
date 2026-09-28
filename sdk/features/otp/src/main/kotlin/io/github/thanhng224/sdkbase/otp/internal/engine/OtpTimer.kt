package io.github.thanhng224.sdkbase.otp.internal.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * A one-second heartbeat scoped to the engine. `delay` is used rather than a system clock so
 * `kotlinx-coroutines-test` can drive it virtually — which is why the counters are testable at all.
 */
internal class OtpTimer(
    private val scope: CoroutineScope,
    private val tickMillis: Long = 1_000L,
) {
    private var job: Job? = null

    /** [onTick] is `suspend` so it can go through `StateStore.withLock`, waiting for the lock like
     * any other caller rather than racing a transition already in flight. */
    fun start(onTick: suspend () -> Unit) {
        stop()
        job = scope.launch {
            while (isActive) {
                delay(tickMillis)
                onTick()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
