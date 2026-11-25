package io.github.tracklens.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * `tracklens.noop`: the release artifact. It must never constrain or change the host app's release build:
 * - minSdk 21 and no compileSdk floor beyond 21,
 * - no permissions and no components in its manifest,
 * - no runtime dependency except kotlin-stdlib.
 * Its public API must match tracklens-core exactly (`checkNoopApiParity` in the root project).
 */
class NoopConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("tracklens.android.library")
            extensions.configure<LibraryExtension> {
                defaultConfig.minSdk = NOOP_FLOOR
                defaultConfig.aarMetadata.minCompileSdk = NOOP_FLOOR
            }
            configurePublishedLibrary(
                PublishedLibraryRules(
                    minSdk = NOOP_FLOOR,
                    minCompileSdk = NOOP_FLOOR,
                    forbidComponents = true,
                    allowedRuntimeDependency = { coordinate -> ALLOWED_RUNTIME.any(coordinate::startsWith) },
                ),
            )
        }
    }

    private companion object {
        const val NOOP_FLOOR = 21

        /**
         * kotlin-stdlib plus its own published dependency on JetBrains annotations (annotation-only, removed by R8).
         * Nothing else may ever reach a host's release build through the no-op.
         */
        val ALLOWED_RUNTIME = listOf("org.jetbrains.kotlin:kotlin-stdlib:", "org.jetbrains:annotations:")
    }
}
