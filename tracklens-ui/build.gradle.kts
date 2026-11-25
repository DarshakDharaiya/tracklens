plugins {
    id("tracklens.published")
    id("tracklens.android.compose")
}

android {
    namespace = "io.github.tracklens.ui"
    // Library resources share the host app's namespace; the prefix keeps them from colliding.
    resourcePrefix = "tracklens_"
    defaultConfig.consumerProguardFiles("consumer-rules.pro")
}

dependencies {
    implementation(project(":tracklens-core"))
    implementation(project(":tracklens-storage"))
    implementation(project(":tracklens-validation"))
    implementation(libs.androidx.core)
    implementation(libs.kotlinx.coroutines.android)
}
