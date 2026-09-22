import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SourcesJar

// ---------------------------------------------------------------------------------------------
// Publishing convention for the four Android library modules. `:sdk:core` is a pure-Kotlin JVM
// module and gets the vanniktech plugin applied directly in its own build.gradle.kts instead —
// `AndroidSingleVariantLibrary` requires `com.android.library`.
//
// Verified against the real vanniktech maven-publish 0.37.0 jar (javap +
// gradle-maven-publish-plugin-0.37.0-sources.jar), not transcribed from the plan:
//   - `AndroidSingleVariantLibrary(variant, sourcesJar: Boolean, publishJavadocJar: Boolean)` DOES
//     exist as a real constructor, but it is `@Deprecated("Use constructor with JavadocJar and
//     SourcesJar instead of Boolean")`. Using it here would put a Kotlin deprecation warning into
//     every build, which fails `sdkbase.warningsAsErrors=true`. The non-deprecated constructor is
//     `AndroidSingleVariantLibrary(javadocJar: JavadocJar, sourcesJar: SourcesJar, variant: String)`.
//   - `JavadocJar.Javadoc()` (Gradle's own `javadoc` task) is meant for Java sources; these modules
//     are Kotlin-only. `MavenPublishBaseExtension.configureBasedOnAppliedPlugins()` — vanniktech's
//     own auto-detection, read from MavenPublishBaseExtension.kt — picks
//     `JavadocJar.Dokka("dokkaGeneratePublicationHtml")` whenever `org.jetbrains.dokka` is applied,
//     which is the same choice made explicit below.
//   - Dokka 2.2.0's `org.jetbrains.dokka` plugin id maps to `DokkaPlugin`, which in V2 mode (the
//     2.x default) applies `DokkaHtmlPlugin`. Its `TaskNames` registers
//     `"dokkaGeneratePublication" + formatName.uppercaseFirstChar()`, i.e. literally
//     `dokkaGeneratePublicationHtml` for the "html" format — confirmed by reading
//     dokka-gradle-plugin-2.2.0-sources.jar's tasks/TaskNames.kt and formats/DokkaHtmlPlugin.kt.
//     `./gradlew :sdk:platform:tasks --all | grep -i dokka` confirms the task is actually
//     registered (see the Task 9 execution report).
// ---------------------------------------------------------------------------------------------

plugins {
    id("com.vanniktech.maven.publish")
    id("org.jetbrains.dokka")
}

// A module that applies this plugin but is not in the registry would publish a coordinate nobody
// declared. Fail loudly rather than shipping a surprise artifact.
@Suppress("UNCHECKED_CAST")
val registered = (rootProject.extra["publishedArtifacts"] as List<String>).toSet()
if (project.path !in registered) {
    throw GradleException(
        "${project.path} applies sdkbase.android.publishing but is not listed in " +
            "publishedArtifacts (gradle/module-topology.gradle.kts)"
    )
}

extensions.configure<MavenPublishBaseExtension> {
    configure(
        AndroidSingleVariantLibrary(
            javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml"),
            sourcesJar = SourcesJar.Sources(),
            variant = "release",
        )
    )

    coordinates(project.group.toString(), project.name, project.version.toString())

    // Signing keys are never in the repo. Sign only when a key is actually present, so a local
    // `publishToMavenLocal` works on a laptop with no keys configured.
    val hasSigningKey = providers.gradleProperty("signingInMemoryKey").isPresent ||
        providers.environmentVariable("ORG_GRADLE_PROJECT_signingInMemoryKey").isPresent
    if (hasSigningKey) {
        signAllPublications()
    }

    pom {
        name.set(project.name)
        description.set("AndroidSdkBase ${project.name} module")
        inceptionYear.set("2026")
        url.set("https://github.com/ThanhNg224/AndroidSdkBase")

        licenses {
            license {
                name.set("The Apache Software License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("thanhng224")
                name.set("Nguyen Phuc Thanh")
                url.set("https://github.com/ThanhNg224")
            }
        }
        scm {
            url.set("https://github.com/ThanhNg224/AndroidSdkBase")
            connection.set("scm:git:https://github.com/ThanhNg224/AndroidSdkBase.git")
            developerConnection.set("scm:git:ssh://git@github.com/ThanhNg224/AndroidSdkBase.git")
        }
    }
}

// A file-backed repo used by scripts/verify-publication.sh: the consumer build resolves from here,
// which is the only honest way to test what a real consumer gets.
publishing {
    repositories {
        maven {
            name = "localTest"
            url = rootProject.layout.buildDirectory.dir("local-repo").get().asFile.toURI()
        }
    }
}
