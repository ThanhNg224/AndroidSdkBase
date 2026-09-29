package io.github.thanhng224.sdkbase.eventlogging.session

/** Payload-free view of queue health; it never contains event fields or gateway response text. */
public class EventLoggingDiagnostics internal constructor(
    public val pendingEvents: Int,
    public val pendingBytes: Int,
    public val enqueuedEvents: Long,
    public val deliveredEvents: Long,
    public val rejectedEvents: Long,
    public val deliveryFailures: Long,
    public val lastFailureCode: Int?,
    public val isDelivering: Boolean,
)
