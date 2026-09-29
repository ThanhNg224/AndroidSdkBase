package sdkbase.errors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorCatalogTest {

    private val declared = listOf(CodeEntry("core", "UNKNOWN", 1000), CodeEntry("features/otp", "OTP_INVALID", 3000))
    private val ledger = listOf(
        CatalogEntry("core", "UNKNOWN", 1000, false),
        CatalogEntry("features/otp", "OTP_INVALID", 3000, false),
    )

    @Test
    fun scansOnlyRealConstantDeclarations() {
        val text = """
            |/** const val IN_KDOC: Int = 1234 */
            |public object E {
            |    // const val IN_COMMENT: Int = 1235
            |    public const val A_CODE: Int = 3001
            |    internal const val B_CODE: Int = 3002
            |    private const val TAG: String = "x"
            |    public const val NOT_A_CODE_YET: Int = SOMETHING
            |}
        """.trimMargin()

        assertEquals(
            listOf(CodeEntry("m", "A_CODE", 3001), CodeEntry("m", "B_CODE", 3002)),
            scanCodes("m", text),
        )
    }

    @Test
    fun aConsistentCatalogHasNoProblems() {
        assertEquals(emptyList<String>(), checkCatalog(declared, ledger, ledger))
    }

    @Test
    fun renumberingIsCaughtEvenWhenTheReferenceIsRenumberedToo() {
        val renumbered = listOf(CodeEntry("core", "UNKNOWN", 1009), declared[1])
        val referenceFollowsCode = listOf(CatalogEntry("core", "UNKNOWN", 1009, false), ledger[1])

        val problems = checkCatalog(renumbered, ledger, referenceFollowsCode)

        assertTrue(problems.any { "never renumbered" in it })
    }

    @Test
    fun aNewCodeMustBeInTheLedger() {
        val problems = checkCatalog(declared + CodeEntry("core", "NEW_ONE", 1001), ledger, ledger)

        assertTrue(problems.any { "NEW_ONE" in it && "errorCatalogDump" in it })
    }

    @Test
    fun aRemovedCodeMustBeRetiredNotDeleted() {
        val problems = checkCatalog(declared.take(1), ledger, ledger)
        assertTrue(problems.any { "OTP_INVALID" in it && "retired" in it })

        val retiredLedger = listOf(ledger[0], ledger[1].copy(retired = true))
        assertEquals(emptyList<String>(), checkCatalog(declared.take(1), retiredLedger, retiredLedger))
    }

    @Test
    fun aRetiredCodeStaysReservedAgainstReuse() {
        val retiredLedger = listOf(ledger[0], ledger[1].copy(retired = true))
        val reused = declared.take(1) + CodeEntry("features/other", "OTHER", 3000)

        val withOther = retiredLedger + CatalogEntry("features/other", "OTHER", 3000, false)

        val problems = checkCatalog(reused, withOther, withOther)

        assertTrue(problems.any { "code 3000 is used by more than one error" in it })
    }

    @Test
    fun aDuplicateCodeAcrossModulesIsCaught() {
        val clash = declared + CodeEntry("features/other", "OTHER", 3000)
        val problems = checkCatalog(clash, ledger + CatalogEntry("features/other", "OTHER", 3000, false), ledger)

        assertTrue(problems.any { "code 3000 is used by more than one error" in it })
    }

    @Test
    fun aCodeOutsideTheFamilyRangesIsCaught() {
        val out = listOf(CodeEntry("core", "TOO_SMALL", 999))
        val l = listOf(CatalogEntry("core", "TOO_SMALL", 999, false))

        assertTrue(checkCatalog(out, l, l).any { "outside" in it })
    }

    @Test
    fun theReferenceMustMatchTheLedger() {
        val problems = checkCatalog(declared, ledger, ledger.take(1))
        assertTrue(problems.any { "OTP_INVALID" in it && "ERROR_CODE_REFERENCE" in it })

        val extra = ledger + CatalogEntry("core", "GHOST", 1500, false)
        assertTrue(checkCatalog(declared, ledger, extra).any { "GHOST" in it })
    }

    @Test
    fun parsesLedgerAndReferenceRows() {
        val parsed = parseLedger("# c\ncore UNKNOWN 1000\nfeatures/otp OTP_INVALID 3000 retired # note\n\n")
        assertEquals(
            listOf(
                CatalogEntry("core", "UNKNOWN", 1000, false),
                CatalogEntry("features/otp", "OTP_INVALID", 3000, true),
            ),
            parsed,
        )
        val rows = parseReferenceRows(
            "| Code | Module | Name | D | R | Status |\n|---|---|---|---|---|---|\n" +
                "| `1000` | core | `UNKNOWN` | DIALOG_TERMINAL | no | active |\n",
        )
        assertEquals(listOf(CatalogEntry("core", "UNKNOWN", 1000, false)), rows)
    }

    @Test
    fun dumpAppendsNewCodesAndNeverRewritesExistingLines() {
        val text = "# header\ncore UNKNOWN 1000\n"

        val result = dumpLedger(declared + CodeEntry("core", "NEW_ONE", 1001), text)

        assertEquals(emptyList<String>(), result.problems)
        assertTrue(result.ledger.startsWith(text))
        assertEquals(
            listOf("core NEW_ONE 1001", "features/otp OTP_INVALID 3000"),
            result.added.map { "${it.module} ${it.name} ${it.code}" },
        )
        assertTrue(result.ledger.endsWith("core NEW_ONE 1001\nfeatures/otp OTP_INVALID 3000\n"))
    }

    @Test
    fun dumpRefusesToBlessARenumbering() {
        val result = dumpLedger(listOf(CodeEntry("core", "UNKNOWN", 1009)), "core UNKNOWN 1000\n")

        assertTrue(result.problems.isNotEmpty())
        assertEquals("core UNKNOWN 1000\n", result.ledger)
    }

    @Test
    fun dumpRefusesACodeOwnedByAnotherError() {
        val result = dumpLedger(listOf(CodeEntry("core", "OTHER", 1000)), "core UNKNOWN 1000\n")

        assertTrue(result.problems.single().contains("already owned"))
    }

    @Test
    fun moduleIsThePathBeforeSrc() {
        assertEquals("features/otp", moduleOf("features/otp/src/main/kotlin/x/OtpErrors.kt"))
        assertEquals("core", moduleOf("core/src/main/kotlin/x/SdkErrors.kt"))
    }
}
