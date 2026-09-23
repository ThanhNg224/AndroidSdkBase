package io.github.thanhng224.sdkbase.core.logging

/**
 * Best-effort scrub of the shapes of secret that most often end up in a log line by accident: a
 * `key=value`/`key: value` pair whose key looks sensitive, a bearer token, a bare JWT, an email
 * address, and a run of 10-12 digits (a phone number or similar identifier). Also escapes CR/LF so
 * a message a host passes in cannot forge extra log lines.
 *
 * This is a heuristic, not a guarantee: callers that know a value is sensitive should still mask
 * it themselves with [mask] before it ever reaches a log call.
 */
public class DefaultRedactor(extraSensitiveKeys: Set<String> = emptySet()) : Redactor {

    private val keyValuePattern: Regex = run {
        val keys = (BASE_SENSITIVE_KEYS + extraSensitiveKeys.map { it.lowercase() })
            .joinToString("|") { Regex.escape(it) }
        Regex("(?i)\\b($keys)(\\s*[:=]\\s*)(\"?)([^\"\\s,;&]+)(\"?)")
    }

    override fun redact(message: String): String {
        var result = escapeControlChars(message)
        result = keyValuePattern.replace(result) { match ->
            val (key, separator, openQuote, value, closeQuote) = match.destructured
            "$key$separator$openQuote${mask(value)}$closeQuote"
        }
        result = BEARER_PATTERN.replace(result) { match ->
            "${match.groupValues[1]}${mask(match.groupValues[2])}"
        }
        result = JWT_PATTERN.replace(result) { match -> mask(match.value) }
        result = EMAIL_PATTERN.replace(result) { match -> mask(match.value) }
        result = DIGIT_RUN_PATTERN.replace(result) { match -> mask(match.value) }
        return result
    }

    private fun escapeControlChars(message: String): String =
        message.replace("\r\n", "\\r\\n").replace("\r", "\\r").replace("\n", "\\n")

    public companion object {

        /** Masks all but the last [keepLast] characters. Never reveals more than [value] has. */
        public fun mask(value: String, keepLast: Int = 2): String {
            if (value.isEmpty()) return value
            val keep = keepLast.coerceIn(0, value.length)
            if (keep == value.length) return value
            return "*".repeat(value.length - keep) + value.takeLast(keep)
        }

        private val BASE_SENSITIVE_KEYS = setOf(
            "token", "password", "secret", "apikey", "api_key",
            "access_token", "refresh_token", "client_secret",
            "cookie", "otp", "pin", "passcode",
        )

        private val BEARER_PATTERN = Regex("(?i)((?:Bearer|Basic)\\s+)([\\w.\\-+/=]+)")

        // Three dot-separated base64url segments: header.payload.signature.
        private val JWT_PATTERN = Regex("\\b[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\b")

        private val EMAIL_PATTERN = Regex("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b")

        // 10-19 digits: phone numbers, national ids and card numbers. Shorter runs (error codes,
        // counts) stay readable; a one-time code is caught by its key instead.
        private val DIGIT_RUN_PATTERN = Regex("\\b\\d{10,19}\\b")
    }
}
