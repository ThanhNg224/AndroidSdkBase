import sdkbase.zones.ZoneModule
import sdkbase.zones.findZoneViolations

// Capture configuration-time facts after every module has applied its conventions.
// Parent projects without build files remain outside the leaf-module rules.
gradle.projectsEvaluated {
    @Suppress("UNCHECKED_CAST")
    val zones = rootProject.extra["zones"] as Map<String, List<String>>

    @Suppress("UNCHECKED_CAST")
    val published = (rootProject.extra["publishedArtifacts"] as List<String>).toSet()

    @Suppress("UNCHECKED_CAST")
    val policed = (rootProject.extra["dependencyPolicedZones"] as List<String>).toSet()

    @Suppress("UNCHECKED_CAST")
    val sourceRuled = (rootProject.extra["sourceRuledZones"] as List<String>).toSet()
    val projectsByCoordinates = rootProject.subprojects.associateBy { it.group.toString() to it.name }

    val modules = rootProject.subprojects.filter { it.buildFile.exists() }.map { source ->
        val edges = source.configurations.filterNot { it.name.contains("test", ignoreCase = true) }
            .flatMap { configuration ->
                val dependencies = configuration.dependencies.withType(ProjectDependency::class.java).map { it.path }
                val constraints = configuration.dependencyConstraints.mapNotNull { constraint ->
                    projectsByCoordinates[constraint.group to constraint.name]?.path
                }
                dependencies + constraints
            }.toSet()
        ZoneModule(source.path, source.name, edges, source.tasks.names)
    }
    val violations = findZoneViolations(
        zones,
        modules,
        rootProject.allprojects.map { it.path }.toSet(),
        published,
        policed,
        sourceRuled,
    )
    if (violations.isNotEmpty()) {
        throw GradleException(
            "Module zone violation(s) — see docs/ARCHITECTURE.md:\n" +
                violations.joinToString("\n") { " - $it" },
        )
    }
}
