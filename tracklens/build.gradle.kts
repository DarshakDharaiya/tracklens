plugins {
    id("tracklens.published")
}

android {
    namespace = "io.github.tracklens"
}

dependencies {
    api(project(":tracklens-core"))
    api(project(":tracklens-storage"))
    api(project(":tracklens-validation"))
    api(project(":tracklens-ui"))
}
