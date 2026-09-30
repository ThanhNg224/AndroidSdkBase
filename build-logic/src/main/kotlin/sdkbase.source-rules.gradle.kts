import sdkbase.sources.CheckSourceRulesTask
import sdkbase.topology.isUiToolkitModule

// checkSourceRules: bans GlobalScope, android.util.Log (outside LogcatSink.kt in core), a
// feature/composition module creating its own CoroutineScope, a public multi-property data
// class in src/main Kotlin sources, and — in a UI module (zone `ui`, or a `<name>-ui-<toolkit>`
// module) — a colour literal in Kotlin or in `res/` XML (sdkbase/sources/SourceRules.kt). Applies
// to every module in a sourceRuledZones zone (module-topology.gradle.kts); the root zone guard
// fails any such module that does not run this check.
@Suppress("UNCHECKED_CAST")
val zones = rootProject.extra["zones"] as Map<String, List<String>>

@Suppress("UNCHECKED_CAST")
val sourceRuledZones = (rootProject.extra["sourceRuledZones"] as List<String>).toSet()

val moduleZone = zones.entries.firstOrNull { project.path in it.value }?.key

val moduleIsUi = moduleZone == "ui" || isUiToolkitModule(project.name)

if (moduleZone in sourceRuledZones) {
    plugins.withId("com.android.library") {
        val checkSourceRules = tasks.register<CheckSourceRulesTask>("checkSourceRules") {
            group = "verification"
            description = "Fails on GlobalScope, android.util.Log, an owned CoroutineScope, a " +
                "public multi-property data class, or (UI modules) a colour literal in src/main."
            sourceFiles.from(fileTree("src/main/kotlin") { include("**/*.kt") })
            sourceRoot.set(layout.projectDirectory.dir("src/main/kotlin"))
            resourceFiles.from(fileTree("src/main/res") { include("**/*.xml") })
            resourceRoot.set(layout.projectDirectory.dir("src/main/res"))
            zone.set(moduleZone)
            uiModule.set(moduleIsUi)
            projectPath.set(project.path)
            result.set(layout.buildDirectory.file("source-rules/ok"))
        }
        tasks.named("check") { dependsOn(checkSourceRules) }
    }
}
