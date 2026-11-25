package io.github.tracklens.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** `tracklens.android.application`: the sample app. Shared settings plus targetSdk and R8 for release. */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")

            extensions.configure<ApplicationExtension> {
                configureAndroidCommon(this)

                defaultConfig {
                    targetSdk = libs.intVersionOf("android-targetSdk")
                    versionCode = 1
                    versionName = "1.0"
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }

                buildTypes.getByName("release") {
                    optimization {
                        enable = true
                        packageScope.set(setOf("androidx.**", "kotlin.**", "kotlinx.**"))
                    }
                }
            }

            dependencies {
                "androidTestImplementation"(libs.library("androidx-espresso-core"))
            }
        }
    }
}
