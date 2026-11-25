package io.github.tracklens.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * `tracklens.android.compose`: Compose compiler plus the Compose BOM and core Material 3 dependencies.
 * Works with either `tracklens.android.library` or `tracklens.android.application`.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            pluginManager.withPlugin("com.android.library") {
                extensions.configure<LibraryExtension> { buildFeatures.compose = true }
                // A library must not merge the test manifest's activity into host debug builds,
                // so it goes on the instrumented-test classpath only.
                dependencies { "androidTestImplementation"(libs.library("androidx-compose-ui-test-manifest")) }
                // Screenshot tests of library UI on Robolectric: recordRoborazziDebug / verifyRoborazziDebug.
                pluginManager.apply("io.github.takahirom.roborazzi")
                dependencies {
                    "testImplementation"(libs.library("roborazzi"))
                    "testImplementation"(libs.library("robolectric"))
                    "testImplementation"(libs.library("androidx-test-core"))
                }
            }
            pluginManager.withPlugin("com.android.application") {
                extensions.configure<ApplicationExtension> { buildFeatures.compose = true }
                dependencies { "debugImplementation"(libs.library("androidx-compose-ui-test-manifest")) }
            }

            dependencies {
                val bom = platform(libs.library("androidx-compose-bom"))
                "implementation"(bom)
                "androidTestImplementation"(bom)
                "implementation"(libs.library("androidx-compose-ui"))
                "implementation"(libs.library("androidx-compose-material3"))
                "implementation"(libs.library("androidx-compose-ui-tooling-preview"))
                "implementation"(libs.library("androidx-activity-compose"))
                "debugImplementation"(libs.library("androidx-compose-ui-tooling"))
                "androidTestImplementation"(libs.library("androidx-compose-ui-test-junit4"))
            }
        }
    }
}
