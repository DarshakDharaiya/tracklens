package io.github.tracklens.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Settings shared by every Android module (libraries and the sample app):
 * compileSdk, minSdk, Java/Kotlin bytecode level, shared lint config and the baseline test dependencies.
 * Values come from the `android-*` and `jvmTarget` entries of the version catalog.
 */
internal fun Project.configureAndroidCommon(extension: CommonExtension) {
    val catalog = libs
    extension.apply {
        compileSdk {
            version =
                release(catalog.intVersionOf("android-compileSdk")) {
                    minorApiLevel = catalog.intVersionOf("android-compileSdkMinor")
                }
        }
        defaultConfig.minSdk = catalog.intVersionOf("android-minSdk")

        // With AGP 9 built-in Kotlin, Kotlin bytecode follows these settings.
        val javaVersion = JavaVersion.toVersion(catalog.versionOf("jvmTarget"))
        compileOptions.sourceCompatibility = javaVersion
        compileOptions.targetCompatibility = javaVersion

        testOptions.unitTests.isIncludeAndroidResources = true

        lint.abortOnError = true
        lint.lintConfig = rootDir.resolve("config/lint/lint.xml")
    }

    dependencies {
        "testImplementation"(catalog.library("junit"))
        "androidTestImplementation"(catalog.library("androidx-junit"))
    }
}
