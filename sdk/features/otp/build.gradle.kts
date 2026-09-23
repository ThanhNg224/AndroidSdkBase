plugins {
    id("sdkbase.android.library")
    id("sdkbase.abi")
    id("sdkbase.publishing")
}

description = "AndroidSdkBase OTP feature: request, verify, resend"

android {
    namespace = "io.github.thanhng224.sdkbase.otp"
}

dependencies {
    api(project(":sdk:core"))
    implementation(libs.coroutines.android)
}
