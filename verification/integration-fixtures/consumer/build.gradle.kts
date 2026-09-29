buildscript {
    repositories { google(); mavenCentral() }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${providers.gradleProperty("fixtureKotlin").get()}")
    }
}

plugins { id("com.android.application") version "{{AGP_VERSION}}" apply false }

val compilerClasspath = buildscript.configurations.getByName("classpath")
val expectedCompiler = providers.gradleProperty("fixtureKotlin").get()
val expectedStdlib = providers.gradleProperty("fixtureStdlib").get()
val verifyFixtureCompiler = tasks.register("verifyFixtureCompiler") {
    doLast {
        val selected = compilerClasspath.resolvedConfiguration.resolvedArtifacts
            .single { it.moduleVersion.id.group == "org.jetbrains.kotlin" && it.name == "kotlin-gradle-plugin" }
            .moduleVersion.id.version
        check(selected == expectedCompiler) { "Requested Kotlin compiler $expectedCompiler but resolved $selected" }
        println("Integration consumer Kotlin compiler: $selected")
    }
}

subprojects {
    tasks.register("verifyFixtureRuntime") {
        dependsOn(verifyFixtureCompiler)
        val runtime = configurations.named("releaseRuntimeClasspath")
        doLast {
            val modules = runtime.get().incoming.resolutionResult.allComponents
                .mapNotNull { it.id as? org.gradle.api.artifacts.component.ModuleComponentIdentifier }
            val stdlib = modules.single { it.group == "org.jetbrains.kotlin" && it.module == "kotlin-stdlib" }
            check(stdlib.version == expectedStdlib) {
                "${project.path}: stdlib ${stdlib.version}, expected $expectedStdlib"
            }
            check(modules.none { it.group.startsWith("androidx.compose") }) {
                "Headless integration consumer resolved Compose: ${project.path}"
            }
            val networkGroups = setOf("com.squareup.okhttp3", "com.squareup.retrofit2", "io.ktor", "com.android.volley")
            check(modules.none { it.group in networkGroups || it.group.startsWith("org.apache.httpcomponents") }) {
                "Integration fixture unexpectedly resolved a network client: ${project.path}"
            }
            val adapterPresent = modules.any { it.group == "{{SDK_GROUP}}" && it.module == "profile-callback" }
            check(adapterPresent == (project.name == "adapter")) {
                "Optional adapter graph mismatch for ${project.path}: adapter=$adapterPresent"
            }
            println("${project.path}: stdlib=${stdlib.version}; adapter=$adapterPresent; Compose=false; HTTP-client=false")
        }
    }
}
