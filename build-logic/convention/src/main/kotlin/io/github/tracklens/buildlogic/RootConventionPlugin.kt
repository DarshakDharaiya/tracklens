package io.github.tracklens.buildlogic

import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.spotless.LineEnding
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

/**
 * `tracklens.root`: applied once, to the root project. Adds the repository-wide checks to `./gradlew check`:
 * - `checkDependencyPolicy` (docs/dependencies.md),
 * - `checkNoopApiParity`: tracklens-noop exposes exactly tracklens-core's public API,
 * - `spotlessCheck`: ktlint formatting for all Kotlin sources and build scripts, plus whitespace hygiene,
 * - `detekt`: static analysis for all Kotlin sources (config/detekt/detekt.yml).
 * Android Lint runs per module and is configured by the Android convention plugins.
 */
class RootConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            check(this == rootProject) { "tracklens.root must only be applied to the root project" }
            pluginManager.apply("base")

            registerDependencyPolicyCheck()
            registerNoopApiParityCheck()
            orderRepositoryScansAfterDependencyGuard()
            configureSpotless()
            configureDetekt()
        }
    }

    private fun Project.registerDependencyPolicyCheck() {
        val checkDependencyPolicy =
            tasks.register<CheckDependencyPolicyTask>("checkDependencyPolicy") {
                group = "verification"
                description = "Fails if any version is declared outside gradle/libs.versions.toml, or a pre-release slips in."
                buildFiles.from(
                    fileTree(rootDir) {
                        include("**/*.gradle.kts", "build-logic/convention/src/**/*.kt")
                        exclude(GENERATED_DIRS)
                    },
                )
                catalog.set(layout.projectDirectory.file("gradle/libs.versions.toml"))
                rootDirectory.set(layout.projectDirectory)
                report.set(layout.buildDirectory.file("reports/dependency-policy.txt"))
            }
        tasks.named("check") { dependsOn(checkDependencyPolicy) }
    }

    private fun Project.registerNoopApiParityCheck() {
        val checkNoopApiParity =
            tasks.register<CheckNoopApiParityTask>("checkNoopApiParity") {
                group = "verification"
                description = "Fails if tracklens-noop's public API differs from tracklens-core's."
                coreDump.set(layout.projectDirectory.file("tracklens-core/api/tracklens-core.api"))
                noopDump.set(layout.projectDirectory.file("tracklens-noop/api/tracklens-noop.api"))
                report.set(layout.buildDirectory.file("reports/noop-api-parity.txt"))
                // Compare the dumps only after any apiDump in the same build has rewritten them.
                mustRunAfter(":tracklens-core:apiDump", ":tracklens-noop:apiDump")
            }
        tasks.named("check") { dependsOn(checkNoopApiParity) }
    }

    /**
     * dependency-guard 0.5.0 declares the repository directory as an output of its `dependencyGuard` and
     * `dependencyGuardBaseline` tasks, so Gradle
     * rejects any task that scans the repository (Spotless, detekt, the policy check) unless it is ordered after them.
     * It only surfaced once enough tasks ran in one build (Task 8); declaring the order keeps runs deterministic.
     */
    private fun Project.orderRepositoryScansAfterDependencyGuard() {
        val scansRepository = { name: String -> name.startsWith("spotless") || name == "detekt" || name == "checkDependencyPolicy" }
        tasks.matching { scansRepository(it.name) }.configureEach {
            mustRunAfter(subprojects.map { module -> module.tasks.matching { it.name in DEPENDENCY_GUARD_TASKS } })
        }
    }

    private fun Project.configureSpotless() {
        pluginManager.apply("com.diffplug.spotless")
        val ktlintVersion = libs.versionOf("ktlint")
        extensions.configure<SpotlessExtension> {
            // Without this, Spotless uses the platform line ending and Windows/CI results differ.
            lineEndings = LineEnding.UNIX
            kotlin {
                target("**/src/**/*.kt")
                targetExclude(GENERATED_DIRS)
                ktlint(ktlintVersion)
            }
            kotlinGradle {
                target("**/*.gradle.kts")
                targetExclude(GENERATED_DIRS)
                ktlint(ktlintVersion)
            }
            format("misc") {
                target("**/*.md", "**/*.toml", "**/*.yml", "**/*.xml", ".editorconfig", ".gitattributes", ".gitignore")
                targetExclude(GENERATED_DIRS + listOf("**/src/**/res/**", "**/schemas/**"))
                trimTrailingWhitespace()
                endWithNewline()
            }
        }
    }

    private fun Project.configureDetekt() {
        pluginManager.apply("io.gitlab.arturbosch.detekt")
        extensions.configure<DetektExtension> {
            buildUponDefaultConfig = true
            parallel = true
            config.setFrom(layout.projectDirectory.file("config/detekt/detekt.yml"))
            basePath = rootDir.absolutePath
            source.setFrom(
                fileTree(rootDir) {
                    include("**/src/**/*.kt")
                    exclude(GENERATED_DIRS)
                },
            )
        }
        tasks.withType<Detekt>().configureEach {
            reports {
                html.required.set(true)
                sarif.required.set(true) // for GitHub code scanning in Task 7
                xml.required.set(false)
                txt.required.set(false)
                md.required.set(false)
            }
        }
    }

    private companion object {
        val GENERATED_DIRS = listOf("**/build/**", "**/.gradle/**", ".idea/**")
        val DEPENDENCY_GUARD_TASKS = setOf("dependencyGuard", "dependencyGuardBaseline")
    }
}
