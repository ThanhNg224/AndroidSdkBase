package io.github.thanhng224.sdkbase.logging.file.crash

/** Bounded, redacted SDK crash text; ID is an opaque local filename, never a user identifier. */
public class CrashReport(public val id: String, public val text: String)
