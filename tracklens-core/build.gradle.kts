plugins {
    id("tracklens.published")
}

android {
    namespace = "io.github.tracklens.core"
}

dependencies {
    // StateFlow appears in @InternalTrackLensApi signatures used by the other TrackLens modules.
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
