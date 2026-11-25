package io.github.tracklens.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * The no-op must expose exactly the public API of tracklens-core, so a host can switch between them per build type.
 * Compares the two committed ABI dumps (kept current by each module's `apiCheck`).
 */
@CacheableTask
abstract class CheckNoopApiParityTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val coreDump: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val noopDump: RegularFileProperty

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val core = coreDump.get().asFile.normalizedLines()
        val noop = noopDump.get().asFile.normalizedLines()
        val onlyInCore = core - noop.toSet()
        val onlyInNoop = noop - core.toSet()

        val reportFile = report.get().asFile
        reportFile.parentFile.mkdirs()
        if (onlyInCore.isEmpty() && onlyInNoop.isEmpty() && core.size == noop.size) {
            reportFile.writeText("Public API parity: OK (${core.size} lines)\n")
            return
        }
        val details =
            buildString {
                onlyInCore.forEach { appendLine("  only in tracklens-core: $it") }
                onlyInNoop.forEach { appendLine("  only in tracklens-noop: $it") }
                if (onlyInCore.isEmpty() && onlyInNoop.isEmpty()) appendLine("  same declarations, different order or count")
            }
        reportFile.writeText(details)
        throw GradleException(
            "tracklens-noop's public API differs from tracklens-core's. Every public declaration must exist in both:\n$details",
        )
    }

    private fun java.io.File.normalizedLines(): List<String> = readLines().map(String::trimEnd).filter(String::isNotEmpty)
}
