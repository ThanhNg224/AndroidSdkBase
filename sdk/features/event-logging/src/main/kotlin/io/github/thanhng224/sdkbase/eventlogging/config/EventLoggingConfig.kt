package io.github.thanhng224.sdkbase.eventlogging.config

import io.github.thanhng224.sdkbase.core.config.validateConfig
import io.github.thanhng224.sdkbase.core.logging.DefaultRedactor
import io.github.thanhng224.sdkbase.core.logging.Redactor
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.eventlogging.delivery.EventDeliveryScheduler
import java.io.File
import java.util.Collections

/** Storage, privacy, queue, and retry limits for a named durable event stream. */
public class EventLoggingConfig private constructor(
    public val namespace: String,
    public val storageDirectory: File,
    allowedAttributeKeys: Set<String>,
    public val commonAttributes: Map<String, String>,
    public val redactor: Redactor,
    public val maxEvents: Int,
    public val maxBytes: Int,
    public val maxAttributes: Int,
    public val maxActionLength: Int,
    public val maxValueLength: Int,
    public val retentionMillis: Long,
    public val gatewayTimeoutMillis: Long,
    public val retryInitialDelayMillis: Long,
    public val retryMaxDelayMillis: Long,
    public val scheduler: EventDeliveryScheduler,
) {
    public val allowedAttributeKeys: Set<String> = Collections.unmodifiableSet(LinkedHashSet(allowedAttributeKeys))

    /** Builder keeps Java hosts independent of Kotlin default arguments. */
    public class Builder(
        private val namespace: String,
        private val storageDirectory: File,
    ) {
        private var allowedAttributeKeys: Set<String> = emptySet()
        private var invalidAllowlist: Boolean = false
        private var commonAttributes: Map<String, String> = emptyMap()
        private var invalidCommonAttributes: Boolean = false
        private var redactor: Redactor = DefaultRedactor()
        private var maxEvents: Int = DEFAULT_MAX_EVENTS
        private var maxBytes: Int = DEFAULT_MAX_BYTES
        private var maxAttributes: Int = DEFAULT_MAX_ATTRIBUTES
        private var maxActionLength: Int = DEFAULT_MAX_ACTION_LENGTH
        private var maxValueLength: Int = DEFAULT_MAX_VALUE_LENGTH
        private var retentionMillis: Long = DEFAULT_RETENTION_MILLIS
        private var gatewayTimeoutMillis: Long = DEFAULT_GATEWAY_TIMEOUT_MILLIS
        private var retryInitialDelayMillis: Long = DEFAULT_RETRY_INITIAL_DELAY_MILLIS
        private var retryMaxDelayMillis: Long = DEFAULT_RETRY_MAX_DELAY_MILLIS
        private var scheduler: EventDeliveryScheduler = EventDeliveryScheduler.None

        public fun allowedAttributeKeys(value: Set<String>): Builder = apply {
            invalidAllowlist = value.size > MAX_ALLOWLIST_KEYS ||
                value.any { it.isBlank() || it.length > MAX_ATTRIBUTE_KEY_LENGTH }
            allowedAttributeKeys = if (invalidAllowlist) emptySet() else value.toSet()
        }

        /** Adds bounded static app or SDK metadata; event attributes override matching keys. */
        public fun commonAttributes(value: Map<String, String>): Builder = apply {
            invalidCommonAttributes = value.size > MAX_ALLOWLIST_KEYS ||
                value.keys.any { !ATTRIBUTE_KEY_PATTERN.matches(it) }
            commonAttributes = Collections.unmodifiableMap(LinkedHashMap<String, String>().apply {
                for ((key, item) in value.entries.take(MAX_ALLOWLIST_KEYS)) {
                    if (ATTRIBUTE_KEY_PATTERN.matches(key)) put(key, item.take(MAX_VALUE_LENGTH))
                }
            })
        }

        /** Applies host scrubbing before the mandatory default PII/token redactor. */
        public fun redactor(value: Redactor): Builder = apply { redactor = value }

        public fun maxEvents(value: Int): Builder = apply { maxEvents = value }

        public fun maxBytes(value: Int): Builder = apply { maxBytes = value }

        public fun maxAttributes(value: Int): Builder = apply { maxAttributes = value }

        public fun maxActionLength(value: Int): Builder = apply { maxActionLength = value }

        public fun maxValueLength(value: Int): Builder = apply { maxValueLength = value }

        public fun retentionMillis(value: Long): Builder = apply { retentionMillis = value }

        public fun gatewayTimeoutMillis(value: Long): Builder = apply { gatewayTimeoutMillis = value }

        public fun retryInitialDelayMillis(value: Long): Builder = apply { retryInitialDelayMillis = value }

        public fun retryMaxDelayMillis(value: Long): Builder = apply { retryMaxDelayMillis = value }

        public fun scheduler(value: EventDeliveryScheduler): Builder = apply { scheduler = value }

        public fun build(): SdkResult<EventLoggingConfig> = validateConfig {
            ensure(NAMESPACE_PATTERN.matches(namespace)) { "namespace must be a short URL-safe opaque identifier" }
            ensure(storageDirectory.path.isNotBlank()) { "storageDirectory must not be blank" }
            ensure(!invalidAllowlist) { "allowedAttributeKeys must contain at most $MAX_ALLOWLIST_KEYS short non-blank keys" }
            ensure(allowedAttributeKeys.size <= MAX_ALLOWLIST_KEYS) { "allowedAttributeKeys has too many entries" }
            ensure(allowedAttributeKeys.all { ATTRIBUTE_KEY_PATTERN.matches(it) }) {
                "allowedAttributeKeys must contain short identifier keys"
            }
            ensure(commonAttributes.keys.all { it in allowedAttributeKeys }) {
                "commonAttributes keys must be present in allowedAttributeKeys"
            }
            ensure(!invalidCommonAttributes && commonAttributes.size <= maxAttributes) {
                "commonAttributes must fit maxAttributes and use short attribute keys"
            }
            ensure(maxEvents in 1..MAX_MAX_EVENTS) { "maxEvents must be in 1..$MAX_MAX_EVENTS" }
            ensure(maxBytes in MIN_BYTES..MAX_BYTES) { "maxBytes must be in $MIN_BYTES..$MAX_BYTES" }
            ensure(maxAttributes in 0..MAX_ALLOWLIST_KEYS) { "maxAttributes must be in 0..$MAX_ALLOWLIST_KEYS" }
            ensure(maxActionLength in 1..MAX_ACTION_LENGTH) { "maxActionLength must be in 1..$MAX_ACTION_LENGTH" }
            ensure(maxValueLength in 1..MAX_VALUE_LENGTH) { "maxValueLength must be in 1..$MAX_VALUE_LENGTH" }
            ensure(retentionMillis > 0L) { "retentionMillis must be positive" }
            ensure(gatewayTimeoutMillis in 1L..MAX_TIMEOUT_MILLIS) { "gatewayTimeoutMillis is out of range" }
            ensure(retryInitialDelayMillis in 1L..MAX_RETRY_DELAY_MILLIS) { "retryInitialDelayMillis is out of range" }
            ensure(retryMaxDelayMillis in retryInitialDelayMillis..MAX_RETRY_DELAY_MILLIS) {
                "retryMaxDelayMillis must be at least retryInitialDelayMillis and at most $MAX_RETRY_DELAY_MILLIS"
            }
            EventLoggingConfig(
                namespace, storageDirectory, Collections.unmodifiableSet(allowedAttributeKeys.toSet()), commonAttributes, redactor,
                maxEvents, maxBytes, maxAttributes,
                maxActionLength, maxValueLength, retentionMillis, gatewayTimeoutMillis,
                retryInitialDelayMillis, retryMaxDelayMillis, scheduler,
            )
        }

        private companion object {
            private const val DEFAULT_MAX_EVENTS = 500
            private const val MAX_MAX_EVENTS = 10_000
            private const val DEFAULT_MAX_BYTES = 1_048_576
            private const val MIN_BYTES = 1_024
            private const val MAX_BYTES = 16 * 1_048_576
            private const val DEFAULT_MAX_ATTRIBUTES = 32
            private const val DEFAULT_MAX_ACTION_LENGTH = 128
            private const val DEFAULT_MAX_VALUE_LENGTH = 2_048
            private const val MAX_ACTION_LENGTH = 512
            private const val MAX_VALUE_LENGTH = 4_096
            private const val DEFAULT_RETENTION_MILLIS = 7L * 24L * 60L * 60L * 1_000L
            private const val DEFAULT_GATEWAY_TIMEOUT_MILLIS = 30_000L
            private const val MAX_TIMEOUT_MILLIS = 300_000L
            private const val DEFAULT_RETRY_INITIAL_DELAY_MILLIS = 5_000L
            private const val DEFAULT_RETRY_MAX_DELAY_MILLIS = 15L * 60L * 1_000L
            private const val MAX_RETRY_DELAY_MILLIS = 24L * 60L * 60L * 1_000L
            private const val MAX_ALLOWLIST_KEYS = 64
            private const val MAX_ATTRIBUTE_KEY_LENGTH = 64
            private val NAMESPACE_PATTERN = Regex("[A-Za-z0-9_-]{1,64}")
            private val ATTRIBUTE_KEY_PATTERN = Regex("[A-Za-z][A-Za-z0-9_.-]{0,63}")
        }
    }
}
