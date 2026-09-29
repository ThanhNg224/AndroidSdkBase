plugins {
    id("sdkbase.android.library")
    id("sdkbase.abi")
    id("sdkbase.publishing")
}

description = "AndroidSdkBase bounded durable business event logging"

android {
    namespace = "io.github.thanhng224.sdkbase.eventlogging"
}

dependencies {
    api(project(":sdk:core"))
    testImplementation(project(":sdk:core-testing"))
}
