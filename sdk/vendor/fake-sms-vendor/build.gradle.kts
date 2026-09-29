plugins {
    id("sdkbase.android.library")
}

description = "Wraps the fake SMS vendor's local jar; unpublished, reachable only from an adapter"

android {
    namespace = "io.github.thanhng224.sdkbase.vendor.fakesms"
}

dependencies {
    // A binary with no Maven coordinate: it cannot be published, so nothing published may reach it.
    api(files("libs/fake-sms-sdk.jar"))
}
