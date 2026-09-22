plugins {
    id("sdkbase.android.library")
    id("sdkbase.abi")
    id("sdkbase.android.publishing")
}

android {
    namespace = "io.github.thanhng224.sdkbase.otp"
}

dependencies {
    // DispatcherProvider (OtpEngine's public constructor parameter) now lives in :core, so the
    // engine needs no dependency on :sdk:platform at all — see docs/ARCHITECTURE.md and Task 6c.
    api(project(":sdk:core"))
    implementation(libs.coroutines.core)
}
