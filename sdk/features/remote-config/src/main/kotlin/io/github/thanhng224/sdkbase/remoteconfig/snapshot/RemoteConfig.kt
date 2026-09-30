package io.github.thanhng224.sdkbase.remoteconfig.snapshot

import java.util.Collections

/** A snapshot from the host gateway. [revision] must be nonblank for a successful fetch. */
public class RemoteConfig(public val revision: String, values: Map<String, String>) {
    public val values: Map<String, String> = Collections.unmodifiableMap(LinkedHashMap(values))

    override fun equals(other: Any?): Boolean =
        other is RemoteConfig && revision == other.revision && values == other.values

    override fun hashCode(): Int = 31 * revision.hashCode() + values.hashCode()

    override fun toString(): String = "RemoteConfig(revision=$revision, valueCount=${values.size})"
}
