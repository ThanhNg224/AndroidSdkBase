plugins {
    id("sdkbase.android.library")
    id("sdkbase.android.compose")
    id("sdkbase.abi")
    id("sdkbase.publishing")
}

description = "AndroidSdkBase Compose UI for the OTP feature"

android {
    namespace = "io.github.thanhng224.sdkbase.otp.ui"
}

dependencies {
    api(project(":sdk:features:otp"))
}
