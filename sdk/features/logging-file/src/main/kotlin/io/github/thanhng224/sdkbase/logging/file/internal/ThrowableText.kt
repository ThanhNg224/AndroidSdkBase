package io.github.thanhng224.sdkbase.logging.file.internal

import java.util.Collections
import java.util.IdentityHashMap

internal class ThrowableText(val text: String, val hasSdkFrame: Boolean)

/** Bounded traversal including causes/suppressed; cycles and hostile Throwables are contained by callers. */
internal fun renderThrowable(error: Throwable, limit: Int, prefixes: Set<String>): ThrowableText {
    val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    val pending = ArrayDeque<Throwable>()
    pending.add(error)
    val text = StringBuilder()
    var sdkFrame = false
    while (pending.isNotEmpty() && seen.size < 16 && text.length < limit) {
        val current = pending.removeFirst()
        if (!seen.add(current)) continue
        text.append(current.javaClass.name).append(": ")
            .append(current.message?.take(limit) ?: "").append('\n')
        for (frame in current.stackTrace.take(64)) {
            if (prefixes.any { frame.className.startsWith("$it.") }) sdkFrame = true
            if (text.length < limit) text.append("at ").append(frame.toString().take(512)).append('\n')
        }
        current.cause?.let { pending.add(it) }
        current.suppressed.take(8).forEach { pending.add(it) }
    }
    return ThrowableText(text.toString().take(limit), sdkFrame)
}
