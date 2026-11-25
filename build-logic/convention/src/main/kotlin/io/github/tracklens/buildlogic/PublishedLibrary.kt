package io.github.tracklens.buildlogic

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.dropbox.gradle.plugins.dependencyguard.DependencyGuardPluginExtension
import kotlinx.validation.KotlinApiBuildTask
import kotlinx.validation.KotlinApiCompareTask
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register

/** Declarations carrying this annotation are public only for cross-module use, and stay out of the ABI dump. */
internal const val INTERNAL_API_ANNOTATION = "io.github.tracklens.InternalTrackLensApi"

/** Everything under this package is implementation detail, and stays out of the ABI dump. */
internal const val INTERNAL_PACKAGE = "io.github.tracklens.internal"

/** What a published library promises to the apps that depend on it. Each item is enforced by `./gradlew check`. */
internal class PublishedLibraryRules(
    /** minSdk declared in the AAR manifest. */
    val minSdk: Int,
    /** compileSdk the AAR metadata requires of consumers (and no minor-version floor). */
    val minCompileSdk: Int,
    /** Fail if the manifest declares activities, services, receivers or providers. */
    val forbidComponents: Boolean,
    /** When set, every resolved release runtime dependency must match; otherwise only the baseline is checked. */
    val allowedRuntimeDependency: ((String) -> Boolean)?,
)

/**
 * The API and release-safety gates shared by every published library (docs/build-notes.md, "API gates"):
 * - public ABI dump `api/<module>.api`: `apiCheck` compares, `apiDump` rewrites it;
 * - dependency-guard baseline of the release runtime classpath (`dependencies/releaseRuntimeClasspath.txt`);
 * - no permissions (and optionally no components) in the library manifest;
 * - the minSdk / compileSdk floors actually written into the release AAR.
 */
internal fun Project.configurePublishedLibrary(rules: PublishedLibraryRules) {
    val apiCheck = configureApiDump()

    pluginManager.apply("com.dropbox.dependency-guard")
    extensions.configure<DependencyGuardPluginExtension> {
        configuration("releaseRuntimeClasspath") {
            rules.allowedRuntimeDependency?.let { allowed -> allowedFilter = allowed }
        }
    }

    // Named verify* because AGP already owns check<Variant>Manifest. Registered once; onVariants only wires inputs.
    val manifestCheck =
        tasks.register<CheckLibraryManifestTask>("verifyReleaseManifest") {
            group = "verification"
            description = "Fails if the release manifest declares permissions${if (rules.forbidComponents) " or components" else ""}."
            forbidComponents.set(rules.forbidComponents)
            report.set(layout.buildDirectory.file("reports/release-safety/manifest.txt"))
        }
    val floorsCheck =
        tasks.register<CheckAarFloorsTask>("verifyReleaseAarFloors") {
            group = "verification"
            description = "Fails if the release AAR's minSdk or compileSdk floor differs from the declared policy."
            expectedMinSdk.set(rules.minSdk)
            expectedMinCompileSdk.set(rules.minCompileSdk)
            report.set(layout.buildDirectory.file("reports/release-safety/aar-floors.txt"))
        }
    val androidComponents = extensions.getByType<LibraryAndroidComponentsExtension>()
    androidComponents.onVariants(androidComponents.selector().withBuildType("release")) { variant ->
        manifestCheck.configure { manifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)) }
        floorsCheck.configure { aar.set(variant.artifacts.get(SingleArtifact.AAR)) }
    }
    tasks.named("check") {
        dependsOn(apiCheck, manifestCheck, floorsCheck, "dependencyGuard")
    }
}

/**
 * Public ABI dump in the binary-compatibility-validator format, built from the release classes.
 *
 * Neither the BCV plugin nor Kotlin's built-in ABI validation can see AGP 9 built-in Kotlin compilations
 * (Task 6 findings in docs/build-notes.md), so the BCV task types are wired to the release compile outputs here.
 */
private fun Project.configureApiDump(): Any {
    val abiTools = configurations.dependencyScope("trackLensAbiTools")
    val abiToolsClasspath = configurations.resolvable("trackLensAbiToolsClasspath") { extendsFrom(abiTools.get()) }
    dependencies {
        "trackLensAbiTools"(libs.library("kotlin-metadata-jvm"))
        "trackLensAbiTools"(libs.library("asm"))
        "trackLensAbiTools"(libs.library("asm-tree"))
    }

    val dumpFileName = "${project.name}.api"
    val apiBuild =
        tasks.register<KotlinApiBuildTask>("apiBuild") {
            group = "verification"
            description = "Writes the public ABI of the release classes to build/api."
            runtimeClasspath.from(abiToolsClasspath)
            inputClassesDirs.from(
                tasks.named("compileReleaseKotlin").map { it.outputs.files },
                tasks.named("compileReleaseJavaWithJavac").map { it.outputs.files },
            )
            nonPublicMarkers.add(INTERNAL_API_ANNOTATION)
            ignoredPackages.add(INTERNAL_PACKAGE)
            outputApiFile.set(layout.buildDirectory.file("api/$dumpFileName"))
            // The Compose compiler emits a public `ComposableSingletons$<File>Kt` holder per file, whose getters
            // are module-internal and named after a hash of the lambda. It is not API, and would churn the dump.
            doLast {
                val dump = outputApiFile.get().asFile
                dump.writeText(stripComposableSingletons(dump.readText()))
            }
        }
    val apiCheck =
        tasks.register<KotlinApiCompareTask>("apiCheck") {
            group = "verification"
            description = "Fails if the public ABI differs from the committed api/$dumpFileName. Run apiDump after intended changes."
            projectApiFile.set(layout.projectDirectory.file("api/$dumpFileName"))
            generatedApiFile.set(apiBuild.flatMap { it.outputApiFile })
        }
    tasks.register<Copy>("apiDump") {
        group = "verification"
        description = "Rewrites the committed api/$dumpFileName from the current release classes."
        from(apiBuild.flatMap { it.outputApiFile })
        into(layout.projectDirectory.dir("api"))
    }
    return apiCheck
}

private val COMPOSABLE_SINGLETONS_BLOCK =
    Regex("""(?m)^public final class \S+/ComposableSingletons\$\S+ \{\r?\n(?:.*\r?\n)*?\}\r?\n(?:\r?\n)?""")

/** Removes the Compose compiler's `ComposableSingletons` classes from an ABI dump. */
internal fun stripComposableSingletons(dump: String): String = dump.replace(COMPOSABLE_SINGLETONS_BLOCK, "")
