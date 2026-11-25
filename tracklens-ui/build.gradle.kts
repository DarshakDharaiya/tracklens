plugins {
    id("tracklens.published")
    id("tracklens.android.compose")
}

android {
    namespace = "io.github.tracklens.ui"
}

dependencies {
    implementation(project(":tracklens-core"))
    implementation(project(":tracklens-storage"))
    implementation(project(":tracklens-validation"))
}
