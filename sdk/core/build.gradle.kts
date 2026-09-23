import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.SourcesJar

plugins {
    id("sdkbase.kotlin.jvm")
    id("sdkbase.abi")
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.dokka)
}

dependencies {
    // `api`, not `implementation`: CoroutineDispatcher appears in DispatcherProvider's public surface.
    api(libs.coroutines.core)
}

// :sdk:core is a pure `org.jetbrains.kotlin.jvm` module, so it cannot use the
// `sdkbase.android.publishing` convention plugin (that one requires com.android.library and
// AndroidSingleVariantLibrary). Configured explicitly here, same reasoning as
// sdkbase.android.publishing.gradle.kts: Dokka HTML output stands in for a Java javadoc jar
// because there is no Java source for the `javadoc` tool to process.
mavenPublishing {
    configure(
        KotlinJvm(
            javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml"),
            sourcesJar = SourcesJar.Sources(),
        )
    )

    coordinates(project.group.toString(), project.name, project.version.toString())

    val hasSigningKey = providers.gradleProperty("signingInMemoryKey").isPresent ||
        providers.environmentVariable("ORG_GRADLE_PROJECT_signingInMemoryKey").isPresent
    if (hasSigningKey) {
        signAllPublications()
    }

    pom {
        name.set(project.name)
        description.set("AndroidSdkBase contracts: results, errors, gateways")
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

publishing {
    repositories {
        maven {
            name = "localTest"
            url = rootProject.layout.buildDirectory.dir("local-repo").get().asFile.toURI()
        }
    }
}
