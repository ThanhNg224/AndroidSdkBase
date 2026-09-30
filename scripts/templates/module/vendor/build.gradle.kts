plugins {
    id("sdkbase.android.library")
}

description = "{{project_name}} unpublished {{name}} vendor binary wrapper"

android {
    namespace = "{{namespace}}"
}

dependencies {
    // Vendor binaries have no Maven coordinate and must stay outside published dependency graphs.
    api(fileTree("libs") { include("*.jar", "*.aar") })
}
