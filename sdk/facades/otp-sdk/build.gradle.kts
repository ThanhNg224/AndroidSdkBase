plugins {
    id("sdkbase.android.library")
    id("sdkbase.abi")
}

android {
    namespace = "io.github.thanhng224.sdkbase.otpsdk"
}

dependencies {
    api(project(":sdk:core"))
    // `api`, not `implementation`: the public OtpSession contract exposes StateFlow<OtpState> and
    // dispatch(OtpCommand), both types owned by :sdk:capabilities:otp-engine. `implementation` would
    // hide that edge from a Maven consumer's compile classpath, leaving OtpState/OtpCommand
    // unresolved for anyone using OtpSession without also depending on otp-engine directly.
    api(project(":sdk:capabilities:otp-engine"))
    // AndroidDispatchers never appears in a public signature here (OtpSdkConfig, OtpSession, OtpSdk
    // all stay within :core types) — it is only used internally to construct the engine — so
    // `implementation` is correct.
    implementation(project(":sdk:platform"))
    implementation(libs.coroutines.core)
}
