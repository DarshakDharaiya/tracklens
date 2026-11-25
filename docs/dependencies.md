# Dependency policy

TrackLens is added to *other people's* apps. Every dependency, version and SDK level we choose is imposed on those apps, so the debug artifact on their debug classpath, and the no-op on their release build. These rules keep that imposition as small as possible.

`./gradlew checkDependencyPolicy` (part of `./gradlew check`) enforces rules 1 and 5. Rule 6 and the floors in section 4 are enforced by the API gates (`tracklens.published` / `tracklens.noop`, also part of `check`); the rest is code review.

## 1. One place for versions

Every version lives in [`gradle/libs.versions.toml`](../gradle/libs.versions.toml). Build scripts and convention plugins reference catalog aliases only.

- No `"group:artifact:1.2.3"` strings and no `id("x") version "1.2"` in any `*.gradle.kts` or in `build-logic` sources.
- Compose libraries take their versions from the BOM and have no `version.ref`.
- **The only exception:** the toolchain-resolver plugin in `settings.gradle.kts`. Settings plugins load before the catalog exists.

## 2. Runtime libraries that published modules expose: **stable minus one minor**

Use the **newest patch of the previous minor line**, for example Compose 1.12.x while 1.13 is current. This applies to Compose (via the BOM), Material3, Activity, Lifecycle, Fragment, Core, Room, Coroutines, serialization and kaml.

Why:
- **Debug/release divergence:** Gradle resolves the highest requested version, so a newer TrackLens dependency silently upgrades the host's own copy in debug builds only. The host's debug and release builds then run different library versions. Staying one minor behind makes that much less likely.
- **SDK floors:** the newest AndroidX lines are usually the ones that raise minSdk and compileSdk (for example Material3 1.5 needs minSdk 24 and Compose 1.13 needs compileSdk 37.1).

Exceptions need a reason in a catalog comment. Valid reasons: a bug fix or an API TrackLens actually needs, or tooling incompatibility, such as an old line whose Gradle plugin doesn't work with the current AGP. Re-test the toolchain when moving a line; Task 4 re-verified Room 2.7.2 with KSP 2.3.14 and Kotlin 2.4.21.

**When to move:** when a new minor line becomes stable, move the policy line in the next TrackLens minor release, and record it in the CHANGELOG under "Changed". Moving it may raise minSdk or compileSdk, which is a breaking change for some hosts.

## 3. Build tooling, tests and the sample app: **latest stable**

AGP, Kotlin (the compiler), KSP, Gradle, lint and formatting tools, test libraries and benchmark tools are not shipped to hosts. Use the latest stable release so we get fixes and support for new SDKs. The sample app uses the same runtime catalog entries as the library, so it behaves like a typical host.

## 4. Floors we declare to hosts

| Floor | Debug artifacts | `tracklens-noop` | Where it's set |
|---|---|---|---|
| minSdk | Highest minSdk among runtime deps: **23** (Compose 1.12 / Material3 1.4) | **21** | `android-minSdk`; noop overrides in its build file |
| compileSdk required of the host (AAR metadata) | Highest floor among runtime deps: **37** | **21** | `android-libraryMinCompileSdk`; noop overrides |
| Kotlin language/API version | **2.2** | **2.2** | `kotlinConsumerLanguage` |
| kotlin-stdlib version | **2.2.0** | **2.2.0** | `kotlinConsumerStdlib` |

Notes:
- **compileSdk:** we compile against the highest level our runtime deps require (today 37.0). AGP would otherwise write our own compileSdk into every AAR as a floor, so the library convention sets the floor explicitly.
- **Kotlin:** we compile with the latest Kotlin (2.4.x) but at language/API version 2.2, declaring stdlib 2.2.0. Host apps on Kotlin 2.2 or newer can consume the artifacts, and the no-op never forces a newer stdlib onto a release build.
- **Raising a floor** is a breaking change for some hosts: call it out in the CHANGELOG.

## 5. No pre-releases

No alpha, beta, RC, dev, EAP or snapshot versions anywhere in the catalog. That includes tooling, because pre-release tooling has produced broken artifacts in other projects. An exception needs `# prerelease-allowed: <reason>` on the catalog line, and the check accepts only lines marked that way.

## 6. The no-op depends on nothing but kotlin-stdlib

`tracklens-noop`'s release runtime classpath is exactly `kotlin-stdlib` (at the floor version) plus stdlib's own dependency `org.jetbrains:annotations`, an annotation-only jar that R8 removes. dependency-guard enforces it with an allow-list (`NoopConventionPlugin`); any other dependency fails `check`, even after re-baselining.

## 7. Adding a dependency

1. Check it isn't already covered by something on the classpath.
2. Add it to the catalog in the right section (runtime / tooling / tests) with a version picked by the rules above.
3. If a published module exposes it at runtime, check its AAR metadata doesn't raise our minSdk or compileSdk floor. If it does, either pick an older line or raise the floor deliberately, updating section 4 and the CHANGELOG.
4. Run `./gradlew check`.

## Current choices and open items

| Item | Status |
|---|---|
| detekt | **Decided in Task 5: 1.23.8.** It analyses this project's Kotlin 2.4 sources correctly on the JDK 25 daemon. It uses two Gradle APIs deprecated for removal in Gradle 10/11 (`ReportingExtension.file`, `Configuration.setVisible`), so **move to detekt 2.0 once it is stable, before upgrading to Gradle 10.** |
| Lifecycle lint checks | Lifecycle 2.10.x's bundled lint checks can't load in AGP 9.5's lint (`ObsoleteLintCustomCheck`). Ignored for that registry only in `config/lint/lint.xml`; remove the entry when the Lifecycle policy line moves to 2.11. |
| dependency-guard | **Decided in Task 6: 0.5.0.** Works on Gradle 9.9 with the configuration cache, despite its last release being Feb 2024. Baselines live in `<module>/dependencies/`. |
| ABI validation | **Decided in Task 6: BCV 0.18.2 task types, wired by hand.** Kotlin 2.4's built-in ABI validation and the BCV plugin both fail to see AGP 9 built-in Kotlin compilations (details in build-notes.md). Revisit when Kotlin's ABI validation supports AGP built-in Kotlin. |
