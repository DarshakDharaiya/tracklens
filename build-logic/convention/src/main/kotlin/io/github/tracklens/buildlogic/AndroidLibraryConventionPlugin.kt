package io.github.tracklens.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

/**
 * `tracklens.android.library`: an Android library with the shared settings, Kotlin explicit API mode,
 * and the consumer floors from docs/dependencies.md:
 * - the compileSdk floor written into the AAR metadata (`android-libraryMinCompileSdk`),
 * - the Kotlin language/API version and stdlib version consumers need (`kotlinConsumer*`).
 * A module can still override values afterwards (tracklens-noop lowers minSdk and the compileSdk floor).
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")

            extensions.configure<LibraryExtension> {
                configureAndroidCommon(this)
                // Without this, AGP writes our own compileSdk as the floor every consumer must meet.
                defaultConfig.aarMetadata.minCompileSdk = libs.intVersionOf("android-libraryMinCompileSdk")
                // Library code ships to other apps: no lint warning is acceptable.
                lint.warningsAsErrors = true
                // Instrumented library tests (androidTest) run with the AndroidX runner.
                defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            dependencies { "androidTestImplementation"(libs.library("androidx-test-runner")) }

            // AGP 9 built-in Kotlin registers the `kotlin` extension when the Android plugin is applied.
            val kotlin = extensions.getByName("kotlin") as KotlinAndroidExtension
            kotlin.explicitApi()
            kotlin.coreLibrariesVersion = libs.versionOf("kotlinConsumerStdlib")
            val consumerFloor = KotlinVersion.fromVersion(libs.versionOf("kotlinConsumerLanguage"))
            kotlin.compilerOptions {
                languageVersion.set(consumerFloor)
                apiVersion.set(consumerFloor)
                // Interface default methods compile to real Java default methods, without DefaultImpls shims.
                jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)
                // TrackLens modules share @InternalTrackLensApi declarations (Kotlin requires the opt-in even in the declaring module).
                optIn.add(INTERNAL_API_ANNOTATION)
            }
        }
    }
}
