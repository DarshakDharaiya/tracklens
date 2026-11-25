package io.github.tracklens.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Enforces the rules in docs/dependencies.md that can be checked mechanically:
 * 1. No build script or convention plugin declares a version inline (all versions live in the catalog).
 * 2. The catalog contains no pre-release versions unless the line is marked `# prerelease-allowed: <reason>`.
 */
@CacheableTask
abstract class CheckDependencyPolicyTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val buildFiles: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val catalog: RegularFileProperty

    /** Only used to print readable relative paths. */
    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val root = rootDirectory.get().asFile
        val violations = mutableListOf<String>()

        buildFiles.files.sortedBy { it.path }.forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                val trimmed = line.trimStart()
                // Comments (including KDoc) may show example coordinates; only code counts.
                if (trimmed.startsWith("*") || trimmed.startsWith("/*")) return@forEachIndexed
                val code = line.substringBefore("//")
                if (ALLOWED_INLINE.any { it in code }) return@forEachIndexed
                val where = "${file.relativeTo(root).invariantSeparatorsPath}:${index + 1}"
                INLINE_COORDINATE.find(code)?.let { violations += "$where inline dependency version: ${it.value}" }
                INLINE_PLUGIN_VERSION.find(code)?.let { violations += "$where inline plugin version: ${it.value}" }
            }
        }

        var inVersions = false
        catalog.get().asFile.readLines().forEachIndexed { index, line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("[")) inVersions = trimmed == "[versions]"
            if (inVersions && PRERELEASE.containsMatchIn(trimmed.substringBefore("#")) &&
                "# prerelease-allowed:" !in trimmed
            ) {
                violations += "gradle/libs.versions.toml:${index + 1} pre-release version without justification: $trimmed"
            }
        }

        val reportFile = report.get().asFile
        reportFile.parentFile.mkdirs()
        if (violations.isEmpty()) {
            reportFile.writeText("Dependency policy: OK (${buildFiles.files.size} build files checked)\n")
        } else {
            reportFile.writeText(violations.joinToString("\n", postfix = "\n"))
            throw GradleException(
                "Dependency policy violations (see docs/dependencies.md):\n" +
                    violations.joinToString("\n") { "  - $it" },
            )
        }
    }

    private companion object {
        /** "group:artifact:1.2.3" inside a string literal. Project paths (":module") never match. */
        val INLINE_COORDINATE = Regex(""""[A-Za-z0-9_.\-]+:[A-Za-z0-9_.\-]+:[0-9][^"]*"""")

        /** `id("x") version "1.0"` or `version = "1.0"`. Does not match versionName/versionCode. */
        val INLINE_PLUGIN_VERSION = Regex("""\bversion\s*(=\s*)?"[0-9][^"]*"""")

        val PRERELEASE = Regex("""(?i)"[^"]*(alpha|beta|-rc|\.rc|snapshot|-dev|eap)[^"]*"""")

        /**
         * Settings-level plugins cannot read the version catalog, so the toolchain resolver
         * in settings.gradle.kts is the one documented exception.
         */
        val ALLOWED_INLINE = listOf("org.gradle.toolchains.foojay-resolver-convention")
    }
}
