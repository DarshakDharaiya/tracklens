package io.github.tracklens.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * `tracklens.published`: a library that ships to host apps' debug builds
 * (tracklens, tracklens-core, -storage, -validation, -ui). Applies `tracklens.android.library`
 * plus the API and release-safety gates, with the floors from the version catalog.
 */
class PublishedLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("tracklens.android.library")
            configurePublishedLibrary(
                PublishedLibraryRules(
                    minSdk = libs.intVersionOf("android-minSdk"),
                    minCompileSdk = libs.intVersionOf("android-libraryMinCompileSdk"),
                    forbidComponents = false, // the UI module will declare TrackLensActivity
                    allowedRuntimeDependency = null,
                ),
            )
        }
    }
}
