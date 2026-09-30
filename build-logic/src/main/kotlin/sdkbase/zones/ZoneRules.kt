package sdkbase.zones

import sdkbase.topology.isOwnUiModule
import sdkbase.topology.isUiToolkitModule

/** Configuration-time facts supplied by the Gradle adapter; the rule engine uses no Gradle objects. */
public data class ZoneModule(
    public val path: String,
    public val name: String,
    public val dependencies: Set<String>,
    public val tasks: Set<String>,
)

private val ALLOWED_TARGETS = mapOf(
    "core" to emptySet(),
    "testing" to setOf("core"),
    "ui" to setOf("core"),
    "feature" to setOf("core", "feature", "ui"),
    "composition" to setOf("core", "feature"),
    "adapter" to setOf("core", "feature", "vendor"),
    "vendor" to emptySet(),
    "bom" to setOf("core", "feature", "composition", "adapter", "testing", "ui"),
    "app" to setOf("core", "feature", "composition", "adapter", "app", "testing", "ui"),
)

/** Returns the existing zone guard's messages in registry/module/edge order. */
public fun findZoneViolations(
    zones: Map<String, List<String>>,
    modules: List<ZoneModule>,
    includedProjects: Set<String>,
    publishedArtifacts: Set<String>,
    dependencyPolicedZones: Set<String>,
    sourceRuledZones: Set<String>,
): List<String> {
    val zoneByPath = zones.flatMap { (zone, paths) -> paths.map { it to zone } }.toMap()
    val violations = mutableListOf<String>()
    (zones.keys - ALLOWED_TARGETS.keys).forEach { violations += "unknown zone '$it' in the registry" }
    zoneByPath.keys.filter { it !in includedProjects }
        .forEach { violations += "$it is registered but not included (no build.gradle.kts at its path)" }

    modules.forEach { source ->
        val sourceZone = zoneByPath[source.path]
        if (sourceZone == null) {
            violations += "${source.path} is not registered in gradle/module-topology.gradle.kts"
            return@forEach
        }
        val allowed = ALLOWED_TARGETS[sourceZone].orEmpty()
        val published = source.path in publishedArtifacts
        source.dependencies.forEach { target ->
            val targetZone = zoneByPath[target] ?: "unregistered"
            if (targetZone !in allowed) {
                violations += "${source.path} [$sourceZone] -> $target [$targetZone] is not allowed"
            } else if (sourceZone == "feature" && targetZone == "feature" &&
                !isOwnUiModule(source.name, target.substringAfterLast(':'))
            ) {
                violations += "${source.path} [feature] -> $target [feature] is not allowed: a feature " +
                    "depends on another feature only as its UI module (<name>-ui-<toolkit> -> <name>); " +
                    "wire different features together in a composition module"
            }
            if (sourceZone == "feature" && targetZone == "ui" && !isUiToolkitModule(source.name)) {
                violations += "${source.path} [feature] -> $target [ui] is not allowed: only " +
                    "<name>-ui-<toolkit> modules may use the shared UI toolkit"
            }
            if (published && target !in publishedArtifacts) {
                violations += "${source.path} is published but depends on unpublished $target"
            }
        }
        if (sourceZone in dependencyPolicedZones && "checkDependencyPolicy" !in source.tasks) {
            violations += "${source.path} [$sourceZone] has no checkDependencyPolicy (apply sdkbase.android.library)"
        }
        if (sourceZone in sourceRuledZones && "checkSourceRules" !in source.tasks) {
            violations += "${source.path} [$sourceZone] has no checkSourceRules (apply sdkbase.android.library)"
        }
        if (published) {
            if (sourceZone != "bom" && "apiCheck" !in source.tasks) {
                violations += "${source.path} is published but has no apiCheck (apply sdkbase.abi)"
            }
            if ("publishAllPublicationsToLocalTestRepository" !in source.tasks) {
                violations += "${source.path} is published but has no localTest publication"
            }
        }
    }
    return violations
}
