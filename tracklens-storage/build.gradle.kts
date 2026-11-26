plugins {
    id("tracklens.published")
    id("tracklens.room")
}

android {
    namespace = "io.github.tracklens.storage"
    defaultConfig.consumerProguardFiles("consumer-rules.pro")
}

dependencies {
    implementation(project(":tracklens-core"))
    implementation(libs.androidx.lifecycle.process)
}
