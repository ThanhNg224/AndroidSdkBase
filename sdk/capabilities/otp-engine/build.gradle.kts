plugins {
    id("sdkbase.android.library")
    id("sdkbase.abi")
}

android {
    namespace = "io.github.thanhng224.sdkbase.otp"
}

dependencies {
    api(project(":sdk:core"))
    // OtpEngine's public constructor takes a DispatcherProvider (see OtpEngine.kt): that type is
    // part of this module's public ABI, so the dependency providing it must be `api`, not
    // `implementation` — otherwise a host consuming :otp-engine alone (the whole point of the
    // headless/optional-UI split, see docs/ARCHITECTURE.md) would not have DispatcherProvider on
    // its compile classpath and could not compile a call to this constructor, even using its
    // default value, because Kotlin resolves every declared parameter type of a called overload.
    api(project(":sdk:platform"))
    implementation(libs.coroutines.core)
}
