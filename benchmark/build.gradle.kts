plugins {
    id("tracklens.android.benchmark")
}

android {
    namespace = "io.github.tracklens.benchmark"
}

dependencies {
    implementation(project(":tracklens-core"))
}
