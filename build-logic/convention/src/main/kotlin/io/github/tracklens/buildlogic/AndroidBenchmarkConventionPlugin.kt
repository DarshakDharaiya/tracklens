package io.github.tracklens.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** `tracklens.android.benchmark`: a library module that runs androidx.benchmark microbenchmarks. */
class AndroidBenchmarkConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("tracklens.android.library")
            pluginManager.apply("androidx.benchmark")

            extensions.configure<LibraryExtension> {
                defaultConfig.testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
                // Benchmarks must run against a non-debuggable build.
                testBuildType = "release"
            }

            dependencies {
                "androidTestImplementation"(libs.library("androidx-benchmark-junit4"))
            }
        }
    }
}
