plugins {
    id("sdkbase.android.library")
}

android {
    namespace = "io.github.thanhng224.sdkbase.otp.ui"
}

dependencies {
    api(project(":sdk:capabilities:otp-engine"))
}
