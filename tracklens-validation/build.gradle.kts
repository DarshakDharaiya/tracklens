plugins {
    id("tracklens.published")
}

android {
    namespace = "io.github.tracklens.validation"
}

dependencies {
    implementation(project(":tracklens-core"))
}
