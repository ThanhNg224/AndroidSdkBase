package io.github.thanhng224.sdkbase.core.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultRedactorTest {

    private val redactor = DefaultRedactor()

    @Test
    fun `masks a sensitive key-value pair`() {
        val result = redactor.redact("sending token=abcdef1234 to gateway")
        assertFalse(result.contains("abcdef1234"))
        assertTrue(result.contains("token="))
    }

    @Test
    fun `masks a password value`() {
        val result = redactor.redact("password=hunter2")
        assertFalse(result.contains("hunter2"))
        assertTrue(result.contains("password="))
    }

    @Test
    fun `masks a bearer token`() {
        val result = redactor.redact("Authorization: Bearer abc123.def456")
        assertFalse(result.contains("abc123.def456"))
        assertTrue(result.contains("Bearer"))

        val basic = redactor.redact("Authorization: Basic dXNlcjpwYXNz")
        assertFalse(basic.contains("dXNlcjpwYXNz"))
    }

    @Test
    fun `masks a bare JWT`() {
        val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dQw4w9WgXcQ_rAtOZ1nEw7oOUpBn"
        val result = redactor.redact("payload=$jwt end")
        assertFalse(result.contains(jwt))
        assertTrue(result.contains("end"))
    }

    @Test
    fun `masks an email address`() {
        val result = redactor.redact("contact jane.doe@example.com for help")
        assertFalse(result.contains("jane.doe@example.com"))
        assertTrue(result.contains("for help"))
    }

    @Test
    fun `masks a 10 to 19 digit run but leaves shorter runs alone`() {
        val tenDigits = redactor.redact("phone 0912345678 called")
        assertFalse(tenDigits.contains("0912345678"))

        val twelveDigits = redactor.redact("id 091234567890 recorded")
        assertFalse(twelveDigits.contains("091234567890"))

        val cardNumber = redactor.redact("card 4111111111111111 charged")
        assertFalse(cardNumber.contains("4111111111111111"))

        val nineDigits = redactor.redact("code 123456789 stays")
        assertTrue(nineDigits.contains("123456789"))
    }

    @Test
    fun `masks one-time codes and PINs but not SDK error codes`() {
        assertFalse(redactor.redact("verify otp=123456").contains("123456"))
        assertFalse(redactor.redact("pin: 4321 entered").contains("4321"))
        // SdkError.toString prints code=NNNN: an error code is diagnostic, not a secret.
        assertTrue(redactor.redact("SdkError(code=2001, reason=x)").contains("code=2001"))
    }

    @Test
    fun `escapes carriage returns and newlines so a message cannot forge extra log lines`() {
        val result = redactor.redact("line one\r\nFAKE ERROR: line two\n")
        assertFalse(result.contains("\n"))
        assertFalse(result.contains("\r"))
        assertTrue(result.contains("\\r\\n"))
    }

    @Test
    fun `an extra sensitive key is masked when configured`() {
        val withExtra = DefaultRedactor(extraSensitiveKeys = setOf("member_id"))
        val result = withExtra.redact("member_id=778899")
        assertFalse(result.contains("778899"))
        assertTrue(result.contains("member_id="))

        // The default instance has no reason to know about "member_id".
        assertTrue(redactor.redact("member_id=778899").contains("778899"))
    }

    // --- DefaultRedactor.mask, moved from the old top-level `redact` test ---

    @Test
    fun `mask keeps only the requested tail`() {
        assertEquals("*******89", DefaultRedactor.mask("123456789", keepLast = 2))
        assertEquals("*********", DefaultRedactor.mask("123456789", keepLast = 0))
        // Never leak more than the value itself.
        assertEquals("12", DefaultRedactor.mask("12", keepLast = 5))
    }
}
