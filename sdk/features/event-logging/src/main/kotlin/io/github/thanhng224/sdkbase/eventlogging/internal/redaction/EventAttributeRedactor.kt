package io.github.thanhng224.sdkbase.eventlogging.internal.redaction

import io.github.thanhng224.sdkbase.core.logging.DefaultRedactor
import io.github.thanhng224.sdkbase.core.logging.Redactor

/** Mandatory allowlisted attribute scrub applied both before persistence and again when loading. */
internal object EventAttributeRedactor {
    private val wholeValueKeys = setOf(
        "token", "password", "secret", "apikey", "accesstoken", "refreshtoken", "clientsecret",
        "cookie", "otp", "pin", "passcode", "email", "phone", "mobile", "cccd", "citizen",
        "nationalid", "identity", "passport", "uid", "cif",
    )

    fun redact(key: String, value: String, redactor: Redactor): String {
        val normalizedKey = key.lowercase().filter(Char::isLetterOrDigit)
        if (normalizedKey in wholeValueKeys) return "***"
        return redactor.redact(value)
    }

    val default: Redactor = DefaultRedactor()
}
