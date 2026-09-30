plugins {
    id("sdkbase.android.library")
}

description = "{{project_name}} unpublished {{name}} adapter"

android {
    namespace = "{{namespace}}"
}

dependencies {
    api(project(":sdk:core"))
}
