plugins {
    id("sdkbase.android.library")
    id("sdkbase.android.compose")
    id("sdkbase.abi")
}

android {
    namespace = "io.github.thanhng224.sdkbase.otp.ui"
}

dependencies {
    api(project(":sdk:capabilities:otp-engine"))
}
