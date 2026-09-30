package io.github.thanhng224.sdkbase.eventlogging.internal.runtime

import io.github.thanhng224.sdkbase.core.environment.SdkEnvironment
import io.github.thanhng224.sdkbase.core.logging.Redactor
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.core.result.getOrNull
import io.github.thanhng224.sdkbase.core.testing.FakeClock
import io.github.thanhng224.sdkbase.core.testing.SequentialIdGenerator
import io.github.thanhng224.sdkbase.eventlogging.config.EventLoggingConfig
import io.github.thanhng224.sdkbase.eventlogging.error.EventLoggingErrors
import io.github.thanhng224.sdkbase.eventlogging.event.EventLoggingEvent
import io.github.thanhng224.sdkbase.eventlogging.gateway.EventLoggingGateway
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventRecordSanitizerTest {

    @Test
    fun `sanitizer allowlists attributes truncates values and masks key named secrets`() {
        val directory = Files.createTempDirectory("event-record-sanitizer").toFile()
        val config = config(
            directory,
            allowedKeys = setOf("outcome", "password"),
            maxActionLength = 5,
            maxValueLength = 4,
            redactor = Redactor { it },
        )
        val sanitizer = EventRecordSanitizer(config, environment(), "session-1", 100L)

        val result = sanitizer.sanitize(
            EventLoggingEvent.Builder("long-action")
                .attributes(mapOf("outcome" to "123456", "password" to "top secret", "ignored" to "value"))
                .build(),
        )

        assertTrue(result is SdkResult.Success)
        val record = (result as SdkResult.Success).value
        assertEquals("1234", record.attributes["outcome"])
        assertEquals("***", record.attributes["password"])
        assertFalse("ignored" in record.attributes)
        assertEquals("long-", record.action)
        directory.deleteRecursively()
    }

    @Test
    fun `event attributes override common attributes within the configured limit`() {
        val directory = Files.createTempDirectory("event-record-precedence").toFile()
        val config = EventLoggingConfig.Builder(
            "sanitizer",
            directory,
            EventLoggingGateway { SdkResult.Success(Unit) },
        )
            .allowedAttributeKeys(setOf("outcome", "password", "extra"))
            .commonAttributes(mapOf("outcome" to "common", "password" to "common-secret"))
            .maxAttributes(2)
            .redactor(Redactor { it })
            .build()
        val sanitizer = EventRecordSanitizer(config.getOrNull()!!, environment(), "session-1", 100L)

        val result = sanitizer.sanitize(
            EventLoggingEvent.Builder("login")
                .attributes(mapOf("outcome" to "event", "extra" to "omitted"))
                .build(),
        )

        assertTrue(result is SdkResult.Success)
        val attributes = (result as SdkResult.Success).value.attributes
        assertEquals("event", attributes["outcome"])
        assertEquals("***", attributes["password"])
        assertFalse("extra" in attributes)
        directory.deleteRecursively()
    }

    @Test
    fun `host redactor exceptions are contained without exposing exception text`() {
        val directory = Files.createTempDirectory("event-record-redactor").toFile()
        val config = config(
            directory,
            allowedKeys = setOf("outcome"),
            redactor = Redactor { throw IllegalStateException("private-redactor-detail") },
        )
        val sanitizer = EventRecordSanitizer(config, environment(), "session-1", 100L)

        val result = sanitizer.sanitize(
            EventLoggingEvent.Builder("login").attributes(mapOf("outcome" to "success")).build(),
        )

        assertTrue(result is SdkResult.Failure)
        val failure = result as SdkResult.Failure
        assertEquals(EventLoggingErrors.INVALID_EVENT, failure.error.code)
        assertFalse(failure.error.reason.contains("private-redactor-detail"))
        directory.deleteRecursively()
    }

    private fun config(
        directory: java.io.File,
        allowedKeys: Set<String>,
        maxActionLength: Int = 128,
        maxValueLength: Int = 2_048,
        redactor: Redactor,
    ): EventLoggingConfig = EventLoggingConfig.Builder(
        "sanitizer",
        directory,
        EventLoggingGateway { SdkResult.Success(Unit) },
    )
        .allowedAttributeKeys(allowedKeys)
        .maxActionLength(maxActionLength)
        .maxValueLength(maxValueLength)
        .redactor(redactor)
        .build()
        .getOrNull()!!

    private fun environment(): SdkEnvironment = SdkEnvironment.Builder()
        .clock(FakeClock(123L))
        .idGenerator(SequentialIdGenerator("event-"))
        .build()
}
