// One test suite, run against each artifact: testCoreDebugUnitTest and testNoopDebugUnitTest.
plugins {
    id("tracklens.android.library")
}

android {
    namespace = "io.github.tracklens.contract"
    flavorDimensions += "artifact"
    productFlavors {
        create("core")
        create("noop")
    }
}

dependencies {
    "coreImplementation"(project(":tracklens-core"))
    "noopImplementation"(project(":tracklens-noop"))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
