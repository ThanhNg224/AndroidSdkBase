plugins {
    id("sdkbase.android.library")
}

description = "OtpGateway on the fake SMS vendor's SDK; a host-owned adapter, never published"

android {
    namespace = "io.github.thanhng224.sdkbase.otp.fakesms"
}

dependencies {
    api(project(":sdk:features:otp"))
    implementation(project(":sdk:vendor:fake-sms-vendor"))
    testImplementation(project(":sdk:core-testing"))
}
