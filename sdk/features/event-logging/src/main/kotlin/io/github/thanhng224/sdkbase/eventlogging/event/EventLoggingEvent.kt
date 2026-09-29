package io.github.thanhng224.sdkbase.eventlogging.event

import java.util.Collections

/** Caller supplied business event. The SDK filters and redacts its attributes before persistence. */
public class EventLoggingEvent private constructor(
    public val action: String,
    public val level: String,
    public val screenId: String?,
    public val attributes: Map<String, String>,
) {
    public class Builder(private val action: String) {
        private var level: String = "info"
        private var screenId: String? = null
        private var attributes: Map<String, String> = emptyMap()

        public fun level(value: String): Builder = apply { level = value }

        public fun screenId(value: String?): Builder = apply { screenId = value }

        public fun attributes(value: Map<String, String>): Builder = apply { attributes = value }

        public fun build(): EventLoggingEvent {
            val bounded = LinkedHashMap<String, String>()
            for ((key, value) in attributes.entries.take(MAX_INPUT_ATTRIBUTES)) {
                if (key.length <= MAX_INPUT_KEY_LENGTH) bounded[key] = value.take(MAX_INPUT_VALUE_LENGTH)
            }
            return EventLoggingEvent(
                action.take(MAX_INPUT_ACTION_LENGTH),
                level.take(16),
                screenId?.take(MAX_INPUT_SCREEN_LENGTH),
                Collections.unmodifiableMap(bounded),
            )
        }

        private companion object {
            private const val MAX_INPUT_ATTRIBUTES = 64
            private const val MAX_INPUT_KEY_LENGTH = 64
            private const val MAX_INPUT_VALUE_LENGTH = 4_096
            private const val MAX_INPUT_ACTION_LENGTH = 512
            private const val MAX_INPUT_SCREEN_LENGTH = 256
        }
    }
}
