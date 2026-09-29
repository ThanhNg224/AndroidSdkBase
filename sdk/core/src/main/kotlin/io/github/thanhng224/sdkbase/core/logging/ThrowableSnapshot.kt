package io.github.thanhng224.sdkbase.core.logging

import java.util.IdentityHashMap

/** Builds a detached Throwable graph containing only redacted text and stack frames. */
internal fun snapshotThrowable(
    throwable: Throwable,
    redact: (String) -> String,
): Throwable = snapshotThrowable(throwable, redact, IdentityHashMap(), depth = 0)

internal fun isThrowableSnapshot(throwable: Throwable): Boolean = throwable is RedactedThrowableSnapshot

private fun snapshotThrowable(
    throwable: Throwable,
    redact: (String) -> String,
    inPath: IdentityHashMap<Throwable, Boolean>,
    depth: Int,
): Throwable {
    // `inPath` holds only the ancestors of this node, so the same exception reached through two
    // sibling branches is snapshotted twice rather than mislabelled as a cycle.
    if (inPath.put(throwable, true) != null) return marker("<cycle>")
    // A chain this deep is not diagnostic and would overflow the stack of a recursive walk.
    if (depth >= MAX_DEPTH) {
        inPath.remove(throwable)
        return marker("<truncated>")
    }

    val typeName = redact(throwable.javaClass.name)
    val message = throwable.message?.let(redact)
    val cause = throwable.cause?.let { snapshotThrowable(it, redact, inPath, depth + 1) }
    val snapshot = RedactedThrowableSnapshot(typeName, message, cause)

    val frames = throwable.stackTrace.map { frame ->
        StackTraceElement(
            redact(frame.className),
            redact(frame.methodName),
            frame.fileName?.let(redact),
            frame.lineNumber,
        )
    }
    snapshot.stackTrace = frames.toTypedArray()

    throwable.suppressed.forEach { suppressed ->
        snapshot.addSuppressed(snapshotThrowable(suppressed, redact, inPath, depth + 1))
    }
    inPath.remove(throwable)
    return snapshot
}

private const val MAX_DEPTH = 32

private fun marker(name: String): Throwable =
    RedactedThrowableSnapshot(typeName = name, message = null).apply { stackTrace = emptyArray() }

/** The visible exception type is retained as text without retaining the source Throwable. */
private class RedactedThrowableSnapshot(
    private val typeName: String,
    message: String?,
    cause: Throwable? = null,
) : Exception(message, cause) {
    override fun toString(): String = message?.let { "$typeName: $it" } ?: typeName
}
