plugins {
    id("sdkbase.android.library")
    id("sdkbase.abi")
    id("sdkbase.publishing")
}

description = "AndroidSdkBase remote-config feature"

android {
    namespace = "io.github.thanhng224.sdkbase.remoteconfig"
}

dependencies {
    api(project(":sdk:core"))
    testImplementation(project(":sdk:core-testing"))
}
