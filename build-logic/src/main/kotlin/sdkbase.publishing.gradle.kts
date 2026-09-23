import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.JavaPlatform
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SourcesJar

// ---------------------------------------------------------------------------------------------
// Publishing for every published module: Android library, Kotlin JVM, or java-platform (BOM).
// Local file repo only — Maven Central is deliberately not configured while this is a base.
//
// Verified against the real vanniktech maven-publish 0.37.0 jar (javap + the -sources jar in
// ~/.gradle/caches), not transcribed from the plan: `AndroidSingleVariantLibrary(javadocJar,
// sourcesJar, variant)`, `KotlinJvm(javadocJar, sourcesJar)` and `JavaPlatform()` are the
// non-deprecated constructors — the Boolean-taking overloads of the first two are
// `@Deprecated`, which would fail `-Psdkbase.warningsAsErrors=true`.
// ---------------------------------------------------------------------------------------------

plugins {
    id("com.vanniktech.maven.publish")
}

// A module that applies this plugin but is not in the registry would publish a coordinate nobody
// declared. Fail loudly rather than shipping a surprise artifact.
@Suppress("UNCHECKED_CAST")
val registered = (rootProject.extra["publishedArtifacts"] as List<String>).toSet()
if (project.path !in registered) {
    throw GradleException(
        "${project.path} applies sdkbase.publishing but is not in publishedArtifacts " +
            "(gradle/module-topology.gradle.kts)"
    )
}

fun pomProperty(key: String): String = providers.gradleProperty("sdkbase.pom.$key").get()

val publishing = extensions.getByType(MavenPublishBaseExtension::class.java)
val dokkaJavadoc = JavadocJar.Dokka("dokkaGeneratePublicationHtml")

plugins.withId("com.android.library") {
    pluginManager.apply("org.jetbrains.dokka")
    publishing.configure(AndroidSingleVariantLibrary(dokkaJavadoc, SourcesJar.Sources(), "release"))
}
plugins.withId("org.jetbrains.kotlin.jvm") {
    pluginManager.apply("org.jetbrains.dokka")
    publishing.configure(KotlinJvm(dokkaJavadoc, SourcesJar.Sources()))
}
plugins.withId("java-platform") {
    publishing.configure(JavaPlatform())
}

publishing.apply {
    coordinates(project.group.toString(), project.name, project.version.toString())

    // Sign only when a key is present, so publishToMavenLocal works on a machine without keys.
    val hasSigningKey = providers.gradleProperty("signingInMemoryKey").isPresent ||
        providers.environmentVariable("ORG_GRADLE_PROJECT_signingInMemoryKey").isPresent
    if (hasSigningKey) signAllPublications()

    pom {
        val url = pomProperty("url")
        name.set(project.name)
        // Lazy, not `project.description ?: project.name` eagerly: for a Kotlin JVM or
        // java-platform module this `pom {}` action runs (as part of `configure(Platform)`,
        // which creates the publication synchronously) before the module's own `description =
        // "..."` line executes, so an eager read would always see null and fall back to the
        // project name. An Android module defers publication creation, so it never showed this.
        description.set(project.provider { project.description ?: project.name })
        inceptionYear.set(pomProperty("inceptionYear"))
        this.url.set(url)
        licenses {
            license {
                name.set("The Apache Software License, Version 2.0")
                this.url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set(pomProperty("developerId"))
                name.set(pomProperty("developerName"))
                this.url.set(pomProperty("developerUrl"))
            }
        }
        scm {
            this.url.set(url)
            connection.set("scm:git:$url.git")
            developerConnection.set("scm:git:$url.git")
        }
    }
}

extensions.configure<PublishingExtension> {
    repositories {
        maven {
            name = "localTest"
            url = rootProject.layout.buildDirectory.dir("local-repo").get().asFile.toURI()
        }
    }
}
