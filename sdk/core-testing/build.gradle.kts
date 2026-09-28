plugins {
    id("sdkbase.android.library")
    id("sdkbase.abi")
    id("sdkbase.publishing")
}

description = "AndroidSdkBase test kit: fakes and assertions for SDK and host tests"

android {
    namespace = "io.github.thanhng224.sdkbase.core.testing"
}

dependencies {
    api(project(":sdk:core"))
}
