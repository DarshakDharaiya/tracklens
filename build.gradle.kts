// Plugins are declared here (apply false) so every module shares one classpath;
// modules apply them through the build-logic convention plugins.
plugins {
    id("tracklens.root")
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.androidx.room) apply false
    alias(libs.plugins.androidx.benchmark) apply false
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.dependency.guard) apply false
    alias(libs.plugins.roborazzi) apply false
}
