package io.github.thanhng224.sdkbase.core.testing

import io.github.thanhng224.sdkbase.core.concurrency.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher

/**
 * A [DispatcherProvider] backed by whatever dispatcher(s) a test controls - a
 * `StandardTestDispatcher`, a single-thread executor dispatcher - instead of the real,
 * Android-backed ones. Deliberately does not depend on kotlinx-coroutines-test, so it works with
 * any [CoroutineDispatcher] the caller passes in.
 */
public class TestDispatcherProvider private constructor(
    override val main: CoroutineDispatcher,
    override val default: CoroutineDispatcher,
    override val io: CoroutineDispatcher,
) : DispatcherProvider {

    /** [main], [default] and [io] are all [dispatcher]. */
    public constructor(dispatcher: CoroutineDispatcher) : this(dispatcher, dispatcher, dispatcher)

    /** [main] is [main]; [default] and [io] are both [background]. */
    public constructor(main: CoroutineDispatcher, background: CoroutineDispatcher) :
        this(main, background, background)
}
