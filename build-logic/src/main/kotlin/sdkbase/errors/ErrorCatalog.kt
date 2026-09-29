package sdkbase.errors

/** An error-code constant declared in an `sdk/` module: [module] is its path under `sdk/`, e.g. `features/otp`. */
public data class CodeEntry(public val module: String, public val name: String, public val code: Int)

/** One committed (ledger) or documented (reference table) code; [retired] codes stay reserved forever. */
public data class CatalogEntry(
    public val module: String,
    public val name: String,
    public val code: Int,
    public val retired: Boolean,
)

public const val MIN_ERROR_CODE: Int = 1000
public const val MAX_ERROR_CODE: Int = 4999

// `[public|internal] const val NAME: Int = 1234` on its own line. Anchored to the line start so a
// KDoc or `//` mention never counts.
private val CODE_CONSTANT =
    Regex(
        """^\s*(?:public\s+|internal\s+)?const\s+val\s+([A-Z][A-Z0-9_]*)\s*:\s*Int\s*=\s*(\d+)\s*$""",
        RegexOption.MULTILINE,
    )

// A reference-table row: | `1000` | core | UNKNOWN | disposition | retryable | active |
private val DOC_ROW = Regex(
    """^\|\s*`?(\d{4})`?\s*\|\s*([^|\s]+)\s*\|\s*`?([A-Z][A-Z0-9_]*)`?\s*\|.*\|\s*(active|retired)\s*\|\s*$""",
)

public fun scanCodes(module: String, text: String): List<CodeEntry> =
    CODE_CONSTANT.findAll(text).map { CodeEntry(module, it.groupValues[1], it.groupValues[2].toInt()) }.toList()

/** Parses `module NAME code [retired]` lines; `#` comments and blank lines are ignored. */
public fun parseLedger(text: String): List<CatalogEntry> =
    text.lineSequence().withIndex().mapNotNull { (index, raw) ->
        val line = raw.substringBefore('#').trim()
        if (line.isEmpty()) return@mapNotNull null
        val parts = line.split(Regex("\\s+"))
        val code = parts.getOrNull(2)?.toIntOrNull()
        val retired = parts.getOrNull(3) == "retired"
        require(code != null && (parts.size == 3 || (parts.size == 4 && retired))) {
            "ledger line ${index + 1} is malformed: '$raw' (expected 'module NAME code [retired]')"
        }
        CatalogEntry(parts[0], parts[1], code, retired)
    }.toList()

public fun parseReferenceRows(text: String): List<CatalogEntry> =
    text.lineSequence().mapNotNull { DOC_ROW.matchEntire(it.trim()) }.map {
        CatalogEntry(it.groupValues[2], it.groupValues[3], it.groupValues[1].toInt(), it.groupValues[4] == "retired")
    }.toList()

private fun CatalogEntry.key() = module to name
private fun CatalogEntry.render() = "$module $name $code" + if (retired) " retired" else ""

/**
 * Enforces "error codes are append-only" against a committed ledger. Returns one message per
 * violation; empty means the catalog is consistent. Renumbering the code AND the reference together
 * still fails, because the ledger is the third, independent witness.
 */
public fun checkCatalog(
    declared: List<CodeEntry>,
    ledger: List<CatalogEntry>,
    reference: List<CatalogEntry>,
): List<String> {
    val problems = mutableListOf<String>()
    val ledgerByKey = ledger.groupBy { it.key() }
    val declaredKeys = declared.map { it.module to it.name }.toSet()

    ledgerByKey.filterValues { it.size > 1 }.keys.forEach { (module, name) ->
        problems += "ledger lists $module $name more than once"
    }
    declared.groupBy { it.module to it.name }.filterValues { it.size > 1 }.keys.forEach { (module, name) ->
        problems += "$module declares $name more than once"
    }

    for (entry in declared) {
        val recorded = ledgerByKey[entry.module to entry.name]?.firstOrNull()
        when {
            recorded == null ->
                problems += "${entry.module} ${entry.name} = ${entry.code} is not in the ledger; run " +
                    "./gradlew errorCatalogDump and commit sdk/error-codes.ledger with the code"
            recorded.code != entry.code ->
                problems += "${entry.module} ${entry.name} was ${recorded.code} in the ledger but is now " +
                    "${entry.code}: error codes are never renumbered"
            recorded.retired ->
                problems += "${entry.module} ${entry.name} is marked retired in the ledger but is still declared"
        }
    }

    for (entry in ledger) {
        if (!entry.retired && (entry.module to entry.name) !in declaredKeys) {
            problems += "${entry.module} ${entry.name} = ${entry.code} is in the ledger but no longer declared: " +
                "mark it 'retired' in sdk/error-codes.ledger, never delete or reuse it"
        }
    }

    // A code value is owned by exactly one (module, name), across live and retired entries.
    val owners = (ledger.map { it.code to it.key() } + declared.map { it.code to (it.module to it.name) })
        .distinct()
        .groupBy({ it.first }, { it.second })
    owners.filterValues { it.size > 1 }.forEach { (code, keys) ->
        problems += "code $code is used by more than one error: " +
            keys.joinToString { "${it.first} ${it.second}" }
    }

    (ledger.map { it.code } + declared.map { it.code }).distinct()
        .filter { it !in MIN_ERROR_CODE..MAX_ERROR_CODE }
        .forEach { problems += "code $it is outside $MIN_ERROR_CODE..$MAX_ERROR_CODE" }

    val ledgerSet = ledger.toSet()
    val referenceSet = reference.toSet()
    (ledgerSet - referenceSet).sortedBy { it.code }.forEach {
        problems += "docs/ERROR_CODE_REFERENCE.md is missing or disagrees with the ledger on: ${it.render()}"
    }
    (referenceSet - ledgerSet).sortedBy { it.code }.forEach {
        problems += "docs/ERROR_CODE_REFERENCE.md lists '${it.render()}' which the ledger does not"
    }
    return problems
}

public class DumpResult(
    public val ledger: String,
    public val added: List<CatalogEntry>,
    public val problems: List<String>,
)

/**
 * Appends codes not yet in the ledger. It never edits, reorders or removes an existing line, and it
 * refuses (problems, no new text) when a declared code contradicts the ledger — so a dump can record
 * new codes but can never bless a renumbering.
 */
public fun dumpLedger(declared: List<CodeEntry>, ledgerText: String): DumpResult {
    val ledger = parseLedger(ledgerText)
    val byKey = ledger.associateBy { it.key() }
    val usedCodes = ledger.associate { it.code to it.key() }.toMutableMap()
    val problems = mutableListOf<String>()
    val added = mutableListOf<CatalogEntry>()

    for (entry in declared.sortedWith(compareBy({ it.code }, { it.module }, { it.name }))) {
        val recorded = byKey[entry.module to entry.name]
        if (recorded != null) {
            if (recorded.code != entry.code) {
                problems += "${entry.module} ${entry.name} is ${recorded.code} in the ledger but ${entry.code} in code"
            }
            continue
        }
        val owner = usedCodes[entry.code]
        if (owner != null) {
            problems += "code ${entry.code} (${entry.module} ${entry.name}) is already owned by " +
                "${owner.first} ${owner.second}"
            continue
        }
        usedCodes[entry.code] = entry.module to entry.name
        added += CatalogEntry(entry.module, entry.name, entry.code, retired = false)
    }
    if (problems.isNotEmpty()) return DumpResult(ledgerText, emptyList(), problems)
    val base = if (ledgerText.isEmpty() || ledgerText.endsWith("\n")) ledgerText else ledgerText + "\n"
    return DumpResult(base + added.joinToString("") { it.render() + "\n" }, added, emptyList())
}

/** `sdk/features/otp/src/main/kotlin/...` -> `features/otp`. */
public fun moduleOf(relativePathUnderSdk: String): String =
    relativePathUnderSdk.substringBefore("/src/")
