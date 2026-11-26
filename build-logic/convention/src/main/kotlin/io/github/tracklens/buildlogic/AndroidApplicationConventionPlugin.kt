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
                    // Full-scope R8, as a host app's release build runs it. The project template limited R8 to
                    // androidx/kotlin/kotlinx (`packageScope`); that partial mode moved a subclass away from its
                    // package-private kotlin.collections superclass and the release sample crashed at startup.
                    optimization {
                        enable = true
                    }
                }
            }

            dependencies {
                "androidTestImplementation"(libs.library("androidx-espresso-core"))
            }
        }
    }
}
