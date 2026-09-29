// Override AGP's built-in Kotlin through its buildscript classpath, as the producer does.
// This changes the compiler; the runtime stdlib remains pinned to the SDK floor.
buildscript {
    repositories { google(); mavenCentral() }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${providers.gradleProperty("consumerKotlin").getOrElse("2.2.10")}")
    }
}

plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") apply false
}

val consumerKotlin = providers.gradleProperty("consumerKotlin").getOrElse("2.2.10")

val verifyCompilerProfile = tasks.register("verifyCompilerProfile") {
    val classpath = buildscript.configurations.named("classpath")
    doLast {
        val selected = classpath.get().resolvedConfiguration.resolvedArtifacts
            .single { it.moduleVersion.id.group == "org.jetbrains.kotlin" && it.name == "kotlin-gradle-plugin" }
            .moduleVersion.id.version
        check(selected == consumerKotlin) { "Requested Kotlin $consumerKotlin but resolved $selected" }
        println("Consumer Kotlin compiler: $selected (AGP 9.4.1)")
    }
}

subprojects {
    tasks.register("verifyRuntimeContracts") {
        dependsOn(verifyCompilerProfile)
        val runtime = configurations.named("releaseRuntimeClasspath")
        val floor = providers.gradleProperty("sdkStdlibFloor").getOrElse("2.2.21")
        doLast {
            val modules = runtime.get().incoming.resolutionResult.allComponents
                .mapNotNull { it.id as? org.gradle.api.artifacts.component.ModuleComponentIdentifier }
            val stdlib = modules.single { it.group == "org.jetbrains.kotlin" && it.module == "kotlin-stdlib" }
            check(stdlib.version == floor) { "${project.path}: stdlib ${stdlib.version}, expected $floor" }
            if (project.name == "headless") {
                check(modules.none { it.group.startsWith("androidx.compose") }) {
                    "Headless consumer unexpectedly resolves Compose"
                }
            }
            println("${project.path}: stdlib ${stdlib.version}; Compose ${modules.any { it.group.startsWith("androidx.compose") }}")
        }
    }
}
