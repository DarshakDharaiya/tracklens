import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "io.github.tracklens.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePluginApi)
    compileOnly(libs.room.gradlePlugin)
    compileOnly(libs.spotless.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
    compileOnly(libs.dependencyGuard.gradlePlugin)
    implementation(libs.binaryCompatibilityValidator.gradlePlugin)
}

tasks.validatePlugins {
    enableStricterValidation = true
    failOnWarning = true
}

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "tracklens.android.library"
            implementationClass = "io.github.tracklens.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidApplication") {
            id = "tracklens.android.application"
            implementationClass = "io.github.tracklens.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidCompose") {
            id = "tracklens.android.compose"
            implementationClass = "io.github.tracklens.buildlogic.AndroidComposeConventionPlugin"
        }
        register("room") {
            id = "tracklens.room"
            implementationClass = "io.github.tracklens.buildlogic.RoomConventionPlugin"
        }
        register("root") {
            id = "tracklens.root"
            implementationClass = "io.github.tracklens.buildlogic.RootConventionPlugin"
        }
        register("published") {
            id = "tracklens.published"
            implementationClass = "io.github.tracklens.buildlogic.PublishedLibraryConventionPlugin"
        }
        register("noop") {
            id = "tracklens.noop"
            implementationClass = "io.github.tracklens.buildlogic.NoopConventionPlugin"
        }
        register("androidBenchmark") {
            id = "tracklens.android.benchmark"
            implementationClass = "io.github.tracklens.buildlogic.AndroidBenchmarkConventionPlugin"
        }
    }
}
