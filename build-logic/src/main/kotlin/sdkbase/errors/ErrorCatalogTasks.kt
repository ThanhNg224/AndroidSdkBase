package sdkbase.errors

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

private fun declaredCodes(files: Set<java.io.File>, sdkRoot: java.io.File): List<CodeEntry> =
    files.sortedBy { it.path }.flatMap { file ->
        scanCodes(moduleOf(file.relativeTo(sdkRoot).invariantSeparatorsPath), file.readText())
    }

/** checkErrorCatalog: code, `sdk/error-codes.ledger` and `docs/ERROR_CODE_REFERENCE.md` must agree. */
@CacheableTask
public abstract class CheckErrorCatalogTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val errorSources: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    public abstract val ledger: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    public abstract val reference: RegularFileProperty

    @get:Internal
    public abstract val sdkRoot: DirectoryProperty

    @get:OutputFile
    public abstract val result: RegularFileProperty

    @TaskAction
    public fun check() {
        val problems = checkCatalog(
            declared = declaredCodes(errorSources.files, sdkRoot.get().asFile),
            ledger = parseLedger(ledger.get().asFile.readText()),
            reference = parseReferenceRows(reference.get().asFile.readText()),
        )
        if (problems.isNotEmpty()) {
            throw GradleException(
                "Error catalog is inconsistent (docs/ERROR_CODE_REFERENCE.md \"Rules\"):\n" +
                    problems.joinToString("\n") { "  - $it" },
            )
        }
        result.get().asFile.writeText("ok\n")
    }
}

/** errorCatalogDump: appends newly declared codes to the ledger; never edits or renumbers one. */
public abstract class ErrorCatalogDumpTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val errorSources: ConfigurableFileCollection

    @get:Internal
    public abstract val ledger: RegularFileProperty

    @get:Internal
    public abstract val sdkRoot: DirectoryProperty

    @TaskAction
    public fun dump() {
        val file = ledger.get().asFile
        val current = if (file.exists()) file.readText() else ""
        val outcome = dumpLedger(declaredCodes(errorSources.files, sdkRoot.get().asFile), current)
        if (outcome.problems.isNotEmpty()) {
            throw GradleException(
                "Refusing to update the ledger:\n" + outcome.problems.joinToString("\n") { "  - $it" },
            )
        }
        file.writeText(outcome.ledger)
        if (outcome.added.isEmpty()) {
            logger.lifecycle("sdk/error-codes.ledger is up to date.")
        } else {
            logger.lifecycle(
                "Added to sdk/error-codes.ledger:\n" +
                    outcome.added.joinToString("\n") { "  ${it.module} ${it.name} ${it.code}" } +
                    "\nNow add a matching row to docs/ERROR_CODE_REFERENCE.md.",
            )
        }
    }
}
