package sdkbase.sources

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * One `src/main` source-rule violation. [line] is 1-based in the ORIGINAL file — comment/string
 * stripping preserves every line break, so offsets computed against the stripped text still line
 * up with the file [ruleId] and [message] name a caller reads: `message` is already
 * "<rule id> — <fix hint>", so a caller only has to prepend "path:line: ".
 */
public data class Violation(public val ruleId: String, public val line: Int, public val message: String)

// Rule zones (AGENTS.md "SDK rules" / docs/ARCHITECTURE.md "The rules the guard enforces"). Every
// SDK zone owns none of the first three; only `feature`/`composition` own a session's coroutines.
private val ALL_SDK_ZONES = setOf("core", "testing", "ui", "feature", "composition", "adapter")
private val OWN_SCOPE_ZONES = setOf("feature", "composition")

private val GLOBAL_SCOPE = Regex("\\bGlobalScope\\b")
private val ANDROID_LOG = Regex("android\\.util\\.Log")
private val OWN_COROUTINE_SCOPE = Regex("\\bCoroutineScope\\(")
private val DATA_CLASS = Regex("\\bdata\\s+class\\b")

// UI modules draw only with theme tokens. `Color.Transparent`/`Unspecified` carry no hue, so they stay legal.
private val COLOR_LITERAL_KOTLIN = Regex(
    "\\bColor\\s*\\(\\s*(?:0[xX]|\\d)" +
        "|\\bColor\\.(?:Black|White|Red|Green|Blue|Yellow|Cyan|Magenta|Gray|LightGray|DarkGray)\\b" +
        "|\\bparseColor\\b",
)

// A whole-value `#hex` (attribute or element text), so a `#123` inside prose is not a colour.
private val COLOR_LITERAL_XML =
    Regex("[\"'>]\\s*#(?:[0-9a-fA-F]{8}|[0-9a-fA-F]{6}|[0-9a-fA-F]{3,4})\\s*[\"'<]|@android:color/")
private val XML_COMMENT = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)

private val MODIFIER_KEYWORDS = setOf(
    "public", "internal", "private", "protected", "abstract", "open", "final",
    "sealed", "inner", "annotation", "external", "expect", "actual", "value", "enum",
)

/**
 * The pure rule engine behind `checkSourceRules` (`sdkbase.source-rules.gradle.kts`). Runs on one
 * file's `src/main` Kotlin source, after stripping comments and string/char literals, so a mention
 * inside a KDoc or a string (`GlobalScope` appears in `DispatcherProvider.kt`'s own KDoc) is never
 * a false positive. [zone] is the module's zone as registered in `gradle/module-topology.gradle.kts`
 * (e.g. "core", "feature").
 */
public fun findViolations(
    fileName: String,
    text: String,
    zone: String,
    isUiModule: Boolean = false,
): List<Violation> {
    val code = stripCommentsAndStrings(text)
    val violations = mutableListOf<Violation>()

    if (zone in ALL_SDK_ZONES) {
        violations += matches(code, GLOBAL_SCOPE, "global-scope", "own coroutines through SessionScope")
    }

    // LogcatSink.kt is the one place `android.util.Log` is allowed in `sdk/` (docs/ARCHITECTURE.md).
    if (zone in ALL_SDK_ZONES && !(zone == "core" && fileName == "LogcatSink.kt")) {
        violations += matches(code, ANDROID_LOG, "android-log", "log through SdkLogger/TaggedLogger")
    }

    if (zone in OWN_SCOPE_ZONES) {
        violations += matches(
            code, OWN_COROUTINE_SCOPE, "own-coroutine-scope", "use SessionScope.coroutineScope / launch",
        )
    }

    if (zone in ALL_SDK_ZONES) {
        violations += publicDataClassViolations(code)
    }

    if (isUiModule) {
        violations += matches(
            code, COLOR_LITERAL_KOTLIN, "ui-color-literal",
            "take colours from the theme tokens (SdkColors / MaterialTheme), never a literal",
        )
    }

    return violations.sortedBy { it.line }
}

/**
 * The resource half of the UI colour rule: no `#hex` and no `@android:color/` in a UI module's
 * `res/` XML (comments are ignored). Colours belong to the host's theme, not to a resource file.
 */
public fun findResourceViolations(text: String): List<Violation> {
    val code = XML_COMMENT.replace(text) { match -> match.value.filter { it == '\n' } }
    return matches(
        code, COLOR_LITERAL_XML, "ui-color-literal",
        "take colours from the theme tokens (SdkColors / MaterialTheme), never a resource literal",
    ).sortedBy { it.line }
}

private fun matches(code: String, regex: Regex, ruleId: String, fix: String): List<Violation> =
    regex.findAll(code).map { Violation(ruleId, lineOf(code, it.range.first), "$ruleId — $fix") }.toList()

private fun lineOf(text: String, index: Int): Int {
    var line = 1
    for (i in 0 until index) if (text[i] == '\n') line++
    return line
}

private fun publicDataClassViolations(code: String): List<Violation> =
    DATA_CLASS.findAll(code).mapNotNull { match ->
        val modifiers = modifiersBefore(code, match.range.first)
        if ("internal" in modifiers || "private" in modifiers) return@mapNotNull null
        val propertyCount = primaryConstructorPropertyCount(code, match.range.last + 1)
        if (propertyCount <= 1) return@mapNotNull null
        Violation(
            "public-data-class",
            lineOf(code, match.range.first),
            "public-data-class — plain class with equals/hashCode/toString instead of copy/componentN " +
                "for $propertyCount properties",
        )
    }.toList()

/**
 * Scans backward from a `data class` match for the maximal run of recognized modifier keywords
 * immediately preceding it (whitespace-separated). Known limitation (documented, not fixed): this
 * only reads the declaration's OWN modifier, not an enclosing class's — a `data class` nested
 * inside an `internal`/`private` outer class is not detected as internal this way. A top-level
 * check plus the declaration's own modifier is judged enough (see plan Task 4 design notes).
 */
private fun modifiersBefore(text: String, dataKeywordStart: Int): Set<String> {
    val found = mutableSetOf<String>()
    var pos = dataKeywordStart
    while (pos > 0) {
        var p = pos
        while (p > 0 && text[p - 1].isWhitespace()) p--
        if (p == 0) break
        var wordStart = p
        while (wordStart > 0 && text[wordStart - 1].isLetter()) wordStart--
        if (wordStart == p) break
        val word = text.substring(wordStart, p)
        if (word !in MODIFIER_KEYWORDS) break
        found += word
        pos = wordStart
    }
    return found
}

/**
 * Counts `val`/`var` parameters at paren depth 1 of the primary constructor whose `(` is the first
 * one found at or after [searchFrom] (the class name and an optional `<T>` generic list precede
 * it). Nested `(...)` (a lambda-typed parameter) and top-level `<...>` (a generic type argument,
 * best-effort: `->` never closes it) are not split on; a trailing comma before the closing `)` is
 * ignored.
 */
private fun primaryConstructorPropertyCount(text: String, searchFrom: Int): Int {
    var i = searchFrom
    while (i < text.length && text[i] != '(') {
        // No primary constructor at all (malformed/partial snippet) — nothing to count.
        if (text[i] == '{') return 0
        i++
    }
    if (i >= text.length) return 0
    val start = i + 1
    var parenDepth = 1
    var angleDepth = 0
    var j = start
    val paramStarts = mutableListOf(start)
    while (j < text.length && parenDepth > 0) {
        when (text[j]) {
            '(' -> parenDepth++
            ')' -> parenDepth--
            '<' -> angleDepth++
            '>' -> if (text.getOrNull(j - 1) != '-') angleDepth = maxOf(0, angleDepth - 1)
            ',' -> if (parenDepth == 1 && angleDepth == 0) paramStarts += j + 1
            else -> Unit
        }
        j++
    }
    val end = j - 1 // index of the matching ')'
    val chunks = (paramStarts + end).zipWithNext { s, e -> text.substring(s, e) }
    return chunks.count { chunk -> chunk.isNotBlank() && Regex("\\b(val|var)\\b").containsMatchIn(chunk) }
}

private sealed interface Mode {
    data object Code : Mode
    data object LineComment : Mode
    class BlockComment(var depth: Int) : Mode
    class Str(val raw: Boolean) : Mode
    data object CharLit : Mode
    class Template(val raw: Boolean, var braces: Int) : Mode
}

/**
 * Replaces every comment and string/char literal with blanks (newlines kept, so line numbers and
 * character offsets still match the original file), while a `${...}` template expression inside a
 * string is kept as real code — so e.g. `"${GlobalScope}"` is still caught. A small hand-rolled
 * state machine (nested `/* */` included), not a full Kotlin lexer.
 */
private fun stripCommentsAndStrings(text: String): String {
    val out = StringBuilder(text.length)
    val stack = ArrayDeque<Mode>()
    stack.addLast(Mode.Code)
    var i = 0
    val n = text.length

    fun blank(c: Char) = if (c == '\n') '\n' else ' '

    while (i < n) {
        val c = text[i]
        when (val top = stack.last()) {
            is Mode.Code, is Mode.Template -> when {
                c == '/' && i + 1 < n && text[i + 1] == '/' -> {
                    out.append(blank(c)).append(blank(text[i + 1]))
                    i += 2
                    stack.addLast(Mode.LineComment)
                }

                c == '/' && i + 1 < n && text[i + 1] == '*' -> {
                    out.append(blank(c)).append(blank(text[i + 1]))
                    i += 2
                    stack.addLast(Mode.BlockComment(1))
                }

                c == '"' && i + 2 < n && text[i + 1] == '"' && text[i + 2] == '"' -> {
                    out.append(blank(c)).append(blank(text[i + 1])).append(blank(text[i + 2]))
                    i += 3
                    stack.addLast(Mode.Str(raw = true))
                }

                c == '"' -> {
                    out.append(blank(c))
                    i += 1
                    stack.addLast(Mode.Str(raw = false))
                }

                c == '\'' -> {
                    out.append(blank(c))
                    i += 1
                    stack.addLast(Mode.CharLit)
                }

                top is Mode.Template && c == '{' -> {
                    top.braces++
                    out.append(c)
                    i += 1
                }

                top is Mode.Template && c == '}' -> {
                    top.braces--
                    out.append(c)
                    i += 1
                    if (top.braces == 0) {
                        stack.removeLast()
                        stack.addLast(Mode.Str(top.raw))
                    }
                }

                else -> {
                    out.append(c)
                    i += 1
                }
            }

            is Mode.LineComment -> {
                if (c == '\n') {
                    out.append('\n')
                    stack.removeLast()
                } else {
                    out.append(blank(c))
                }
                i += 1
            }

            is Mode.BlockComment -> when {
                c == '/' && i + 1 < n && text[i + 1] == '*' -> {
                    top.depth++
                    out.append(blank(c)).append(blank(text[i + 1]))
                    i += 2
                }

                c == '*' && i + 1 < n && text[i + 1] == '/' -> {
                    top.depth--
                    out.append(blank(c)).append(blank(text[i + 1]))
                    i += 2
                    if (top.depth == 0) stack.removeLast()
                }

                else -> {
                    out.append(blank(c))
                    i += 1
                }
            }

            is Mode.CharLit -> when {
                c == '\\' && i + 1 < n -> {
                    out.append(blank(c)).append(blank(text[i + 1]))
                    i += 2
                }

                c == '\'' -> {
                    out.append(blank(c))
                    i += 1
                    stack.removeLast()
                }

                else -> {
                    out.append(blank(c))
                    i += 1
                }
            }

            is Mode.Str -> when {
                top.raw && c == '"' && i + 2 < n && text[i + 1] == '"' && text[i + 2] == '"' -> {
                    out.append(blank(c)).append(blank(text[i + 1])).append(blank(text[i + 2]))
                    i += 3
                    stack.removeLast()
                }

                !top.raw && c == '"' -> {
                    out.append(blank(c))
                    i += 1
                    stack.removeLast()
                }

                !top.raw && c == '\\' && i + 1 < n -> {
                    out.append(blank(c)).append(blank(text[i + 1]))
                    i += 2
                }

                c == '$' && i + 1 < n && text[i + 1] == '{' -> {
                    out.append(blank(c)).append(text[i + 1])
                    i += 2
                    stack.removeLast()
                    stack.addLast(Mode.Template(top.raw, braces = 1))
                }

                c == '$' && i + 1 < n && (text[i + 1].isLetter() || text[i + 1] == '_') -> {
                    out.append(blank(c))
                    i += 1
                }

                else -> {
                    out.append(blank(c))
                    i += 1
                }
            }
        }
    }
    return out.toString()
}

/** Fails when a module's `src/main` Kotlin sources trip [findViolations] for its zone. */
@CacheableTask
public abstract class CheckSourceRulesTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val sourceFiles: ConfigurableFileCollection

    // Only used to render a relative path in the failure message; it does not affect which
    // violations are found, so it stays out of the cache key (a failing task is never cached).
    @get:Internal
    public abstract val sourceRoot: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val resourceFiles: ConfigurableFileCollection

    @get:Internal
    public abstract val resourceRoot: DirectoryProperty

    @get:Input
    public abstract val zone: Property<String>

    @get:Input
    public abstract val uiModule: Property<Boolean>

    @get:Input
    public abstract val projectPath: Property<String>

    @get:OutputFile
    public abstract val result: RegularFileProperty

    @TaskAction
    public fun check() {
        val root = sourceRoot.get().asFile
        val z = zone.get()
        val ui = uiModule.get()
        val sourceViolations = sourceFiles.files.sortedBy { it.path }.flatMap { file ->
            val relative = file.relativeTo(root).invariantSeparatorsPath
            findViolations(file.name, file.readText(), z, ui).map { v -> "$relative:${v.line}: ${v.message}" }
        }
        val resourceViolations = if (!ui) {
            emptyList()
        } else {
            val resRoot = resourceRoot.get().asFile
            resourceFiles.files.sortedBy { it.path }.flatMap { file ->
                val relative = "res/" + file.relativeTo(resRoot).invariantSeparatorsPath
                findResourceViolations(file.readText()).map { v -> "$relative:${v.line}: ${v.message}" }
            }
        }
        val violations = sourceViolations + resourceViolations
        if (violations.isNotEmpty()) {
            throw GradleException(
                "${projectPath.get()} [$z] violates the source rules " +
                    "(docs/ARCHITECTURE.md \"The rules the guard enforces\"):\n" +
                    violations.joinToString("\n") { "  - $it" },
            )
        }
        result.get().asFile.writeText("ok\n")
    }
}
