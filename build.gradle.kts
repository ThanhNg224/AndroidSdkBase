// AGP 9.4.1 ships KGP 2.2.10. This classpath entry is the documented, supported way to move to a
// newer Kotlin: Gradle resolves the conflict upward (verified: 2.2.10 -> 2.4.20).
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    }
}

plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.dependency.analysis)
}

apply(from = "gradle/module-topology.gradle.kts")

subprojects {
    group = providers.gradleProperty("sdkbase.group").get()
    version = providers.gradleProperty("sdkbase.version").get()
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

// ---------------------------------------------------------------------------
// Zone guard — docs/ARCHITECTURE.md is enforced here, not by code review.
//
// Two independent rules:
//   1. Direction: a module may only depend on modules in zones below it.
//   2. Purity:    a published artifact's transitive compile graph may not reach an unpublished zone,
//                 because a consumer resolving it from Maven Central could not resolve that edge.
// Only `api` and `implementation` project edges are inspected; androidTest is deliberately exempt.
// ---------------------------------------------------------------------------

@Suppress("UNCHECKED_CAST")
val zones = rootProject.extra["zones"] as Map<String, List<String>>

@Suppress("UNCHECKED_CAST")
val publishedArtifacts = rootProject.extra["publishedArtifacts"] as List<String>

val zoneByPath: Map<String, String> =
    zones.flatMap { (zone, paths) -> paths.map { it to zone } }.toMap()

// What each zone is allowed to reach. `core` reaches nothing: it is the bottom.
val allowedTargets: Map<String, Set<String>> = mapOf(
    "core" to emptySet(),
    "platform" to setOf("core"),
    "capability" to setOf("core", "platform", "capability"),
    "facade" to setOf("core", "platform", "capability"),
    "app" to setOf("core", "platform", "capability", "facade", "app"),
)

// A published AAR must never reach one of these.
val unpublishableZones = setOf("app", "unregistered")

fun directProjectDeps(project: Project): Set<String> =
    listOf("api", "implementation")
        .mapNotNull { project.configurations.findByName(it) }
        .flatMap { configuration ->
            configuration.dependencies
                .filterIsInstance<ProjectDependency>()
                .map { it.path }
        }
        .toSet()

fun transitiveProjectDeps(project: Project, visited: MutableSet<String>): Set<String> {
    directProjectDeps(project).forEach { path ->
        if (visited.add(path)) {
            project.rootProject.findProject(path)?.let { transitiveProjectDeps(it, visited) }
        }
    }
    return visited
}

gradle.projectsEvaluated {
    val violations = mutableListOf<String>()

    // Rule 1: direction.
    rootProject.subprojects.forEach { source ->
        val sourceZone = zoneByPath[source.path] ?: "unregistered"
        val allowed = allowedTargets[sourceZone]
        directProjectDeps(source).forEach { targetPath ->
            val targetZone = zoneByPath[targetPath] ?: "unregistered"
            if (allowed == null || targetZone !in allowed) {
                violations += "${source.path} [$sourceZone] -> $targetPath [$targetZone] is not allowed"
            }
        }
    }

    // Rule 2: purity of every published graph.
    publishedArtifacts.forEach { modulePath ->
        val module = rootProject.findProject(modulePath) ?: return@forEach
        transitiveProjectDeps(module, mutableSetOf()).forEach { depPath ->
            val depZone = zoneByPath[depPath] ?: "unregistered"
            if (depZone in unpublishableZones) {
                violations += "$modulePath reaches $depPath [$depZone] in its published compile graph"
            }
        }
    }

    // Rule 3: every published artifact must actually have an ABI contract and a publication.
    // Registration alone is not enough — an artifact with no baseline has no contract.
    publishedArtifacts.forEach { modulePath ->
        val module = rootProject.findProject(modulePath) ?: return@forEach
        if (module.tasks.findByName("apiCheck") == null) {
            violations += "$modulePath is registered as published but applies no ABI plugin " +
                "(sdkbase.abi for Android modules, sdkbase.abi.jvm for Kotlin JVM modules)"
        }
        if (module.tasks.findByName("publishToMavenLocal") == null) {
            violations += "$modulePath is registered as published but applies no publishing plugin"
        }
    }

    if (violations.isNotEmpty()) {
        throw GradleException(
            "Module zone violation(s) — see docs/ARCHITECTURE.md:\n" +
                violations.joinToString("\n") { " - $it" }
        )
    }
}
