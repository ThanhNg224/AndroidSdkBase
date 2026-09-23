package io.github.thanhng224.sdkbase.core.time

import java.util.UUID

/** Injected id source, so anything that mints identifiers (sessions, challenges) is testable. */
public fun interface IdGenerator {

    public fun newId(): String

    public companion object {
        public val Uuid: IdGenerator = IdGenerator { UUID.randomUUID().toString() }
    }
}
