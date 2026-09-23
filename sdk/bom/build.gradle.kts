plugins {
    `java-platform`
    id("sdkbase.publishing")
}

description = "Aligns the versions of every AndroidSdkBase artifact"

// The BOM lists every other published module, read from the registry so it cannot drift.
@Suppress("UNCHECKED_CAST")
val published = rootProject.extra["publishedArtifacts"] as List<String>

dependencies {
    constraints {
        published.filter { it != project.path }.forEach { api(project(it)) }
    }
}
