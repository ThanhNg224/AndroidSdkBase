package io.github.thanhng224.sdkbase.eventlogging.internal.runtime

import io.github.thanhng224.sdkbase.core.environment.SdkEnvironment
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.eventlogging.config.EventLoggingConfig
import io.github.thanhng224.sdkbase.eventlogging.error.EventLoggingErrors
import io.github.thanhng224.sdkbase.eventlogging.event.EventLoggingEvent
import io.github.thanhng224.sdkbase.eventlogging.event.EventLoggingRecord
import io.github.thanhng224.sdkbase.eventlogging.internal.redaction.EventAttributeRedactor
import kotlinx.coroutines.CancellationException

internal class EventRecordSanitizer(
    private val config: EventLoggingConfig,
    private val environment: SdkEnvironment,
    private val sessionId: String,
    private val sessionStartedAtMillis: Long,
) {
    fun sanitize(event: EventLoggingEvent): SdkResult<EventLoggingRecord> {
        return try {
            if (event.action.isBlank()) {
                return SdkResult.Failure(EventLoggingErrors.invalidEvent("action is blank"))
            }
            val level = event.level.lowercase()
            if (level !in ALLOWED_LEVELS) {
                return SdkResult.Failure(EventLoggingErrors.invalidEvent("level is unsupported"))
            }
            val action = redact(event.action.take(config.maxActionLength)).take(config.maxActionLength)
            if (action.isBlank()) {
                return SdkResult.Failure(EventLoggingErrors.invalidEvent("action is blank after sanitization"))
            }
            val screen = event.screenId?.let { redact(it.take(MAX_SCREEN_LENGTH)).take(MAX_SCREEN_LENGTH) }
            val attrs = LinkedHashMap<String, String>()
            fun addAttributes(source: Map<String, String>) {
                for ((key, value) in source) {
                    if (key !in config.allowedAttributeKeys || key.length > MAX_ATTRIBUTE_KEY_LENGTH) continue
                    if (attrs.size >= config.maxAttributes && key !in attrs) continue
                    val hostRedacted = config.redactor.redact(value.take(config.maxValueLength))
                        .take(config.maxValueLength)
                    attrs[key] = EventAttributeRedactor.redact(
                        key,
                        hostRedacted,
                        EventAttributeRedactor.default,
                    ).take(config.maxValueLength)
                }
            }
            addAttributes(config.commonAttributes)
            addAttributes(event.attributes)
            val id = environment.idGenerator.newId()
            if (!IDENTIFIER_PATTERN.matches(id) || !IDENTIFIER_PATTERN.matches(sessionId)) {
                return SdkResult.Failure(EventLoggingErrors.invalidEvent("identifier is invalid"))
            }
            val now = environment.clock.nowMillis().coerceAtLeast(0L)
            val elapsed = (now - sessionStartedAtMillis).coerceAtLeast(0L)
            SdkResult.Success(EventLoggingRecord(id, sessionId, now, elapsed, action, level, screen, attrs))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SdkResult.Failure(EventLoggingErrors.invalidEvent("event could not be sanitized"))
        }
    }

    private fun redact(value: String): String =
        EventAttributeRedactor.default.redact(config.redactor.redact(value))

    private companion object {
        private const val MAX_SCREEN_LENGTH: Int = 256
        private const val MAX_ATTRIBUTE_KEY_LENGTH: Int = 64
        private val ALLOWED_LEVELS = setOf("debug", "info", "warn", "error")
        private val IDENTIFIER_PATTERN = Regex("[A-Za-z0-9_-]{1,128}")
    }
}
