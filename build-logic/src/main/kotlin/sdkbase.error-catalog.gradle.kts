import sdkbase.errors.CheckErrorCatalogTask
import sdkbase.errors.ErrorCatalogDumpTask

// checkErrorCatalog / errorCatalogDump: error codes are append-only. `sdk/error-codes.ledger` is the
// committed record; docs/ERROR_CODE_REFERENCE.md must list the same codes. Applied to the root
// project only (sdkbase/errors/ErrorCatalog.kt holds the rules).
val sdkDir = layout.projectDirectory.dir("sdk")
val errorFiles = fileTree(sdkDir) {
    include("**/src/main/**/*Errors.kt")
    exclude("**/build/**")
}

tasks.register<CheckErrorCatalogTask>("checkErrorCatalog") {
    group = "verification"
    description = "Fails if an error code was renumbered, dropped, reused, or is missing from the ledger/docs."
    errorSources.from(errorFiles)
    ledger.set(layout.projectDirectory.file("sdk/error-codes.ledger"))
    reference.set(layout.projectDirectory.file("docs/ERROR_CODE_REFERENCE.md"))
    sdkRoot.set(sdkDir)
    result.set(layout.buildDirectory.file("error-catalog/ok"))
}

tasks.register<ErrorCatalogDumpTask>("errorCatalogDump") {
    group = "verification"
    description = "Appends newly declared error codes to sdk/error-codes.ledger (never edits an existing one)."
    errorSources.from(errorFiles)
    ledger.set(layout.projectDirectory.file("sdk/error-codes.ledger"))
    sdkRoot.set(sdkDir)
}
