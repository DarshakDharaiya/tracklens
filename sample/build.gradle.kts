plugins {
    id("tracklens.android.application")
    id("tracklens.android.compose")
}

android {
    namespace = "io.github.tracklens.sample"
    defaultConfig.applicationId = "io.github.tracklens.sample"
}

dependencies {
    debugImplementation(project(":tracklens"))
    releaseImplementation(project(":tracklens-noop"))
}
