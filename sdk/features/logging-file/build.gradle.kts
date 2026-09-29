plugins {
    id("sdkbase.android.library")
    id("sdkbase.abi")
    id("sdkbase.publishing")
}
description = "Bounded asynchronous file logging and opt-in SDK crash capture"
android { namespace = "io.github.thanhng224.sdkbase.logging.file" }
dependencies {
    api(project(":sdk:core"))
    implementation(libs.coroutines.core)
    testImplementation(project(":sdk:core-testing"))
}
