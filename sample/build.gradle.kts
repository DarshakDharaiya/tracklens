plugins {
    id("tracklens.android.application")
    id("tracklens.android.compose")
}

android {
    namespace = "io.github.tracklens.sample"
    defaultConfig.applicationId = "io.github.tracklens.sample"
    // The sample is never published: sign its release build with the debug key so the R8 release build (with
    // tracklens-noop) can be installed and checked on a device.
    buildTypes.getByName("release").signingConfig = signingConfigs.getByName("debug")
}

dependencies {
    debugImplementation(project(":tracklens"))
    releaseImplementation(project(":tracklens-noop"))
}
