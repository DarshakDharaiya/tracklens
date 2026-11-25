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
import java.util.Properties
import java.util.zip.ZipFile

/**
 * Reads the floors a release AAR actually imposes on its consumers and compares them with the policy
 * in docs/dependencies.md. Checking the built artifact (not the DSL) catches regressions from any source,
 * for example AGP defaulting the compileSdk floor to our own compileSdk.
 */
@CacheableTask
abstract class CheckAarFloorsTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val aar: RegularFileProperty

    @get:Input
    abstract val expectedMinSdk: Property<Int>

    @get:Input
    abstract val expectedMinCompileSdk: Property<Int>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val (manifest, metadata) =
            ZipFile(aar.get().asFile).use { zip ->
                val manifestText = zip.getEntry("AndroidManifest.xml")?.let { zip.getInputStream(it).reader().readText() }.orEmpty()
                val properties = Properties()
                zip.getEntry(AAR_METADATA)?.let { entry -> zip.getInputStream(entry).use(properties::load) }
                manifestText to properties
            }

        val minSdk =
            MIN_SDK
                .find(manifest)
                ?.groupValues
                ?.get(1)
                ?.toInt()
        val minCompileSdk = metadata.getProperty("minCompileSdk")?.toInt()
        val minCompileMinor = metadata.getProperty("minCompileMinorSdk")?.toInt() ?: 0

        val violations =
            buildList {
                if (minSdk != expectedMinSdk.get()) add("minSdk is $minSdk, expected ${expectedMinSdk.get()}")
                if (minCompileSdk != expectedMinCompileSdk.get()) {
                    add("AAR metadata minCompileSdk is $minCompileSdk, expected ${expectedMinCompileSdk.get()}")
                }
                if (minCompileMinor != 0) {
                    add("AAR metadata declares a compileSdk minor floor; consumers would need $minCompileSdk.$minCompileMinor")
                }
            }

        val reportFile = report.get().asFile
        reportFile.parentFile.mkdirs()
        reportFile.writeText("minSdk=$minSdk minCompileSdk=$minCompileSdk minCompileMinorSdk=$minCompileMinor\n")
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Release AAR floors differ from the policy in docs/dependencies.md:\n" + violations.joinToString("\n") { "  - $it" },
            )
        }
    }

    private companion object {
        const val AAR_METADATA = "META-INF/com/android/build/gradle/aar-metadata.properties"
        val MIN_SDK = Regex("""minSdkVersion="(\d+)"""")
    }
}
