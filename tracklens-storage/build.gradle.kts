plugins {
    id("tracklens.published")
    id("tracklens.room")
}

android {
    namespace = "io.github.tracklens.storage"
}

dependencies {
    implementation(project(":tracklens-core"))
}
