plugins {
    id("sdkbase.android.library")
    id("sdkbase.android.compose")
    id("sdkbase.abi")
    id("sdkbase.android.publishing")
}

android {
    namespace = "io.github.thanhng224.sdkbase.otp.ui"
}

dependencies {
    api(project(":sdk:features:otp"))
}
