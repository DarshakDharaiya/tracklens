plugins {
    id("tracklens.published")
}

android {
    namespace = "io.github.tracklens.core"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
