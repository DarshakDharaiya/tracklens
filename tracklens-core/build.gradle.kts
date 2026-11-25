plugins {
    id("tracklens.published")
}

android {
    namespace = "io.github.tracklens.core"
}

dependencies {
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
