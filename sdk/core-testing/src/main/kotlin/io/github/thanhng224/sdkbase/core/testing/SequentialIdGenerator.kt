package io.github.thanhng224.sdkbase.core.testing

import io.github.thanhng224.sdkbase.core.time.IdGenerator
import java.util.concurrent.atomic.AtomicLong

/**
 * An [IdGenerator] that yields deterministic, sequential ids (`"id-1"`, `"id-2"`, ...) instead of
 * random UUIDs, so assertions on session/challenge ids are stable. Thread-safe.
 */
public class SequentialIdGenerator(private val prefix: String) : IdGenerator {

    public constructor() : this("id-")

    private val counter = AtomicLong(0)

    override fun newId(): String = "$prefix${counter.incrementAndGet()}"
}
