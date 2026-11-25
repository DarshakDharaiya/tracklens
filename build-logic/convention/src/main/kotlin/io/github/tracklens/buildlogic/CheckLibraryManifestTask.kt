package io.github.tracklens.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

/**
 * TrackLens promises it needs no permissions (plan section 3.1), so no library manifest may declare any.
 * The no-op additionally must not add any component to the host's release build.
 */
@CacheableTask
abstract class CheckLibraryManifestTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifest: RegularFileProperty

    @get:Input
    abstract val forbidComponents: Property<Boolean>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifest.get().asFile)
        val forbiddenTags = PERMISSION_TAGS + if (forbidComponents.get()) COMPONENT_TAGS else emptyList()
        val violations =
            forbiddenTags.flatMap { tag ->
                val nodes = document.getElementsByTagName(tag)
                (0 until nodes.length).map { index ->
                    val name = (nodes.item(index) as Element).getAttribute("android:name").ifEmpty { "(unnamed)" }
                    "<$tag> $name"
                }
            }

        val reportFile = report.get().asFile
        reportFile.parentFile.mkdirs()
        reportFile.writeText(if (violations.isEmpty()) "OK\n" else violations.joinToString("\n", postfix = "\n"))
        if (violations.isNotEmpty()) {
            throw GradleException(
                "The release manifest declares entries a TrackLens library must not ship:\n" +
                    violations.joinToString("\n") { "  - $it" },
            )
        }
    }

    private companion object {
        val PERMISSION_TAGS = listOf("uses-permission", "uses-permission-sdk-23", "permission", "permission-group", "permission-tree")
        val COMPONENT_TAGS = listOf("activity", "activity-alias", "service", "receiver", "provider")
    }
}
