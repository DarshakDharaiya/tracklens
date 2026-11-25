package io.github.tracklens.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Copies tracklens-core's shared public types (every file directly in the public package except the `TrackLens`
 * facade) into tracklens-noop's generated sources. One source of truth for configuration and model types; only
 * the facade differs between the two artifacts. If a shared file ever depends on core internals, the no-op stops
 * compiling, which keeps the public package free of implementation details.
 */
@CacheableTask
abstract class CopySharedApiSourcesTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val sources: ConfigurableFileCollection

    /** Package directory the files are written to, for example `io/github/tracklens`. */
    @get:Input
    abstract val packagePath: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun copy() {
        val root = outputDirectory.get().asFile
        root.deleteRecursively()
        val target = root.resolve(packagePath.get()).apply { mkdirs() }
        sources.files.forEach { file -> file.copyTo(target.resolve(file.name), overwrite = true) }
    }
}
