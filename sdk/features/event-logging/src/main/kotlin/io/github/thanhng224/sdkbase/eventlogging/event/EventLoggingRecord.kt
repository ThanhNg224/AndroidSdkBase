package io.github.thanhng224.sdkbase.eventlogging.event

import java.util.Collections

/** Immutable, enriched event delivered to the host supplied gateway. */
public class EventLoggingRecord internal constructor(
    public val id: String,
    public val sessionId: String,
    public val createdAtMillis: Long,
    public val elapsedSinceSessionStartMillis: Long,
    public val action: String,
    public val level: String,
    public val screenId: String?,
    attributes: Map<String, String>,
) {
    public val attributes: Map<String, String> = Collections.unmodifiableMap(LinkedHashMap(attributes))
}
