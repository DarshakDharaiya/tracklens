# Build notes

Findings from the Task 2 toolchain spike, updated in Tasks 3 and 4 (2026-11-25). Update this file whenever a toolchain version changes. Version *choices* follow [dependencies.md](dependencies.md).

## Verified toolchain

| Component | Version | Verified by |
|---|---|---|
| Gradle | 9.9.2 (daemon on Temurin JDK 25, provisioned by the daemon toolchain) | All builds below |
| Android Gradle Plugin | 9.5.2, **built-in Kotlin** (no `org.jetbrains.kotlin.android` plugin) | All builds below |
| Kotlin | 2.4.21 | All builds below |
| compileSdk / targetSdk | 37 / 37 | 37.0 is the highest floor of the runtime deps (Task 4 AAR metadata scan) |
| Compose BOM | 2026.09.00 (Compose 1.12.1, Material3 1.4.0) | Composable compiled in `tracklens-ui` at Kotlin language version 2.2 (Task 4) |
| KSP | 2.3.14 | Room code generated in `tracklens-storage` |
| Room | 2.7.2 (+ `androidx.room` Gradle plugin) | Entity, DAO (suspend + `Flow`), database, exported schema; first proven on 2.8.5 in Task 2, re-verified on 2.7.2 in Task 4 |
| androidx.benchmark | 1.5.0 (+ `androidx.benchmark` Gradle plugin) | Spike benchmark passed on a physical Android 16 device in dry-run mode |

No workarounds were needed for the toolchain itself. Everything below is how to configure it, plus environment quirks.

## How each piece is configured

### Built-in Kotlin
- Modules apply only `com.android.library` (or `com.android.application`). Kotlin compiles through AGP's built-in support; Kotlin compile tasks are still named `compile<Variant>Kotlin`.
- `kotlin { explicitApi() }` works as a top-level block in the module build file. A declaration without a visibility modifier fails with `NO_EXPLICIT_VISIBILITY_IN_API_MODE` ("Visibility must be specified in explicit API mode").
- Kotlin bytecode follows `compileOptions` (Java 11 gives class major version 55). No separate `jvmTarget` is needed and there is no JVM-target mismatch error.
- Explicit API mode is on in `tracklens-core`, `-storage`, `-validation`, `-ui` and `-noop`. It moves into the `tracklens.android.library` convention plugin in Task 3.

### compileSdk minor version
The `android-compileSdkMinor` catalog entry feeds this DSL (currently `0`):
```kotlin
compileSdk {
    version = release(37) { minorApiLevel = 0 }
}
```
Compose 1.13 artifacts need 37.1 and fail `checkAarMetadata` against 37.0. That's why Task 1 used 37.1, until Task 4 moved to Compose 1.12. Platform `android-37.1` stays installed for when the policy line moves.

### minSdk
minSdk follows the highest minSdk of the runtime deps: 23 with Compose 1.12 / Material3 1.4. Material3 1.5.0 declares 24, so debug minSdk moves to 24 when that line becomes the policy line. `tracklens-noop` stays at 21 (no AndroidX dependencies).

### KSP and Room
- KSP versions are no longer tied to the Kotlin version (2.3.x works with Kotlin 2.4.21). Apply `com.google.devtools.ksp` next to the Android plugin; it works with built-in Kotlin.
- Generated code lands in `build/generated/ksp/<variant>/kotlin/`.
- `room { schemaDirectory("$projectDir/schemas") }` writes `schemas/<database FQCN>/<version>.json` through the `copyRoomSchemas` task. Commit that folder from Phase 2 onward.
- `room-runtime` 2.8 brings `kotlinx-coroutines` onto the compile classpath, so DAOs can use `suspend` and `Flow` without declaring coroutines separately. Declare coroutines explicitly anyway once `tracklens-core` uses them, so the version is pinned in the catalog.

### Compose in a library module
Apply `org.jetbrains.kotlin.plugin.compose` and set `buildFeatures { compose = true }`. Verified by the compiled composable taking a `Composer` parameter.

### Benchmark module
- `com.android.library` + `androidx.benchmark`, with `testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"` and `testBuildType = "release"` (benchmarks must not run debuggable).
- Run on a device: `./gradlew :benchmark:connectedAndroidTest`. Add `-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.dryRunMode.enable=true` to check the setup without measuring. **In PowerShell, quote that whole `-P...` argument**, or PowerShell splits it at the first dot and Gradle reports "Task not found".
- Results: `benchmark/build/outputs/androidTest-results/connected/release/`.

## Convention plugins (`build-logic`, Task 3)

`build-logic` is an included build (`pluginManagement { includeBuild("build-logic") }` in `settings.gradle.kts`) that reads the same `gradle/libs.versions.toml`.

| Plugin id | What it sets |
|---|---|
| `tracklens.android.library` | `com.android.library`, the shared Android settings below, Kotlin explicit API mode, `junit` + `androidx.test.ext:junit` test dependencies |
| `tracklens.android.application` | `com.android.application`, the shared settings, targetSdk, version 1 / "1.0", test runner, R8 `optimization` for release |
| `tracklens.android.compose` | Compose compiler, `buildFeatures.compose`, Compose BOM, ui, material3, tooling-preview, activity-compose, debug tooling, UI test deps |
| `tracklens.room` | KSP, the Room Gradle plugin, `schemaDirectory("<module>/schemas")`, room-runtime + room-compiler |
| `tracklens.android.benchmark` | `tracklens.android.library` + `androidx.benchmark`, benchmark runner, `testBuildType = "release"`, benchmark-junit4 |

**Shared Android settings** live in the catalog's `[versions]` table (`android-compileSdk`, `android-compileSdkMinor`, `android-targetSdk`, `android-minSdk`, `jvmTarget`). To bump the SDK, change them there, not in a module.

**Per-module overrides** go in the module's own `android {}` block after the plugins. `tracklens-noop` is the only module with any today.

Implementation notes:
- AGP 9.5's `CommonExtension` is not generic any more, so one `configureAndroidCommon(extension: CommonExtension)` serves libraries and the app.
- Built-in Kotlin's `kotlin` extension is a `KotlinAndroidExtension`, and `explicitApi()` is reachable through `KotlinBaseExtension` (from `kotlin-gradle-plugin-api`).
- Lazy properties such as `optimization.packageScope` (a `SetProperty`) need `.set(...)` in plugin code. The `=` shortcut only works in build scripts.
- The Compose plugin adds `ui-test-manifest` with `androidTestImplementation` for libraries, so its test activity can't leak into host debug builds, and with `debugImplementation` for the app.
- Gradle plugin validation is strict (`validatePlugins` with `failOnWarning`): `./gradlew :build-logic:convention:validatePlugins`.

### AAR metadata floor (release-safety finding)
By default AGP writes the library's own compileSdk (including the minor) into `META-INF/com/android/build/gradle/aar-metadata.properties` as the floor consumers must compile against. The library convention instead sets `defaultConfig.aarMetadata.minCompileSdk` from `android-libraryMinCompileSdk` (37), and `tracklens-noop` overrides it to 21. Verified in the built AARs: core/ui declare `minCompileSdk=37` and no minor, noop declares `21`. `verifyReleaseAarFloors` (Task 6) asserts these values in every published AAR so they can't regress.

### Kotlin consumer floor (Task 4)
The library convention sets `coreLibrariesVersion` (stdlib 2.2.0) and `compilerOptions.languageVersion/apiVersion` (2.2) on the built-in `KotlinAndroidExtension`. Verified: class files carry Kotlin metadata `mv=[2,2,0]`, Compose compiles at language version 2.2, and `tracklens-noop`'s release runtime classpath is exactly `kotlin-stdlib:2.2.0`.

### Dependency policy check (Task 4)
The `tracklens.root` plugin (applied in the root build file) adds `checkDependencyPolicy` to `./gradlew check`. It scans every `*.gradle.kts` and the build-logic sources for inline versions, and the catalog for unmarked pre-releases. Report: `build/reports/dependency-policy.txt`. Task classes in build-logic need `@CacheableTask` (or `@DisableCachingByDefault`) to pass strict plugin validation.

## Quality gates (Task 5)

All run as part of `./gradlew check`. Locally, `./gradlew spotlessApply` fixes formatting.

| Gate | Scope | Config | Reports |
|---|---|---|---|
| Spotless + ktlint 1.8.0 (`ktlint_official` style) | All Kotlin sources, including build-logic and the sample; all `*.gradle.kts`; whitespace in md/toml/yml/xml | `.editorconfig` | Console output |
| detekt 1.23.8 (default rules + overrides) | All Kotlin sources | `config/detekt/detekt.yml` | `build/reports/detekt/detekt.{html,sarif}` |
| Android Lint | Each module; **warnings are errors in library modules** | `config/lint/lint.xml` | `<module>/build/reports/lint-results-debug.{html,sarif}` |
| Dependency policy | All build scripts + the catalog | `docs/dependencies.md` | `build/reports/dependency-policy.txt` |

- **Where it's wired:** Spotless and detekt are applied once, in the root project (`tracklens.root`), and cover every module. That avoids detekt's per-variant hooks, which expect the `kotlin-android` plugin, a plugin AGP 9 built-in Kotlin no longer applies. Lint is configured per module by the Android convention plugins.
- **Line endings:** Spotless forces `LineEnding.UNIX`, so Windows and Linux CI agree. `.gitattributes` normalizes checkouts to LF.
- **Line length:** detekt's `MaxLineLength` matches ktlint's 140.
- **Proven:** a probe file failed all three gates (ktlint formatting; detekt `EmptyCatchBlock` and 4 related findings; lint `NewApi` error plus a `DefaultLocale` *warning* reported as an error).
- **Known deprecations:** detekt 1.23.8 triggers two Gradle deprecation warnings (removal in Gradle 10/11). See dependencies.md.

## API gates (Task 6)

Applied by `tracklens.published` (umbrella, core, storage, validation, ui) and `tracklens.noop`; all run in `./gradlew check`.

| Gate | Task | Fails when | Update after an intended change |
|---|---|---|---|
| Public ABI | `<module>:apiCheck` | The release classes' public API differs from `<module>/api/<module>.api` | `./gradlew apiDump` |
| No-op parity | `:checkNoopApiParity` | `tracklens-noop/api` and `tracklens-core/api` dumps differ | Make both modules match, then `apiDump` |
| Dependencies | `<module>:dependencyGuard` | `releaseRuntimeClasspath` differs from `<module>/dependencies/releaseRuntimeClasspath.txt`; for the noop, any dependency besides kotlin-stdlib (+ its `annotations`) fails even after re-baselining | `./gradlew dependencyGuardBaseline` |
| Manifest | `<module>:verifyReleaseManifest` | Any `<uses-permission>`/`<permission>`; for the noop also any activity, service, receiver or provider | Not overridable: change the code |
| AAR floors | `<module>:verifyReleaseAarFloors` | The built AAR's minSdk or compileSdk floor differs from the policy, or a compileSdk minor floor appears | Change the policy in the catalog / dependencies.md |

The ABI dump excludes `@io.github.tracklens.InternalTrackLensApi` declarations and the `io.github.tracklens.internal` package.

**Proven:** one planted break per gate (a new public function, an AndroidX dependency in the noop, `INTERNET` in core's manifest, an activity in the noop, the noop's compileSdk floor raised to 37). Each failed its gate with a clear message. A function marked `@InternalTrackLensApi` stayed out of the dump. After `apiDump` on core alone, the parity check failed. Everything passed again after revert.

Findings:
- **Kotlin built-in ABI validation doesn't work with AGP 9 built-in Kotlin** (Kotlin 2.4.21). `updateKotlinAbi` fails with "Cannot query the value of this provider" for both `BinariesSource.MAIN_COMPILATION` and `MAVEN_PUBLICATIONS`; the dump step finds no classes. The DSL also changed in 2.4: `enabled` was removed (calling `abiValidation {}` enables it), `excluded` became `exclude`, and the `legacyDump` block moved up a level.
- **The BCV plugin can't hook in either**, because it waits for the `kotlin-android` plugin. Its task types (`KotlinApiBuildTask`, `KotlinApiCompareTask`) work fine when wired by hand to `compileReleaseKotlin`/`compileReleaseJavaWithJavac` outputs. Their worker needs `kotlin-metadata-jvm` + ASM on a resolvable configuration (`trackLensAbiToolsClasspath`).
- **AGP already owns tasks named `check<Variant>Manifest`**, so our tasks are `verifyRelease*`.
- **AGP may call `onVariants` more than once**, so gate tasks are registered once and the callback only wires their inputs.
- **Task ordering:** `checkNoopApiParity` reads files `apiDump` writes, so it declares `mustRunAfter` on both `apiDump` tasks. Without that it could compare stale dumps in a combined invocation.

## Continuous integration (Task 7)

[`.github/workflows/ci.yml`](../.github/workflows/ci.yml) runs on every pull request, on pushes to `main`, and manually.

| Job | Gradle command | Uploads on failure |
|---|---|---|
| Static analysis | `spotlessCheck detekt checkDependencyPolicy :build-logic:convention:validatePlugins lint` | detekt, lint, dependency-policy reports |
| Unit tests | `testDebugUnitTest :tracklens-contract-tests:testCoreDebugUnitTest :tracklens-contract-tests:testNoopDebugUnitTest` | test reports |
| API and release safety | `apiCheck checkNoopApiParity dependencyGuard verifyReleaseManifest verifyReleaseAarFloors` | generated ABI dumps, release-safety reports |
| Build | `assemble :benchmark:assembleAndroidTest` | — |
| CI OK | Succeeds only if all four succeeded | — |

- **Branch protection:** require only **CI OK**. Jobs can then be added or renamed without editing the protection rules.
- **Shared setup:** [`.github/actions/setup-build`](../.github/actions/setup-build/action.yml) installs Temurin JDK 25, the version `gradle/gradle-daemon-jvm.properties` asks for (a mismatch makes Gradle download another JDK on every run). It then runs `gradle/actions/setup-gradle`, which validates the wrapper checksum and caches Gradle state; only `main` writes the cache, and pull requests read it.
- **Pinned actions:** every third-party action is pinned to a full commit SHA with its version in a comment (checkout v7.0.1, setup-java v6.3.0, setup-gradle v6.4.1, upload-artifact v7.0.2). Bump them deliberately.
- **Jobs use `--continue`,** so one run reports every failing gate, not just the first.
- **Android SDK:** GitHub's Ubuntu runners ship an SDK with accepted licenses, and AGP downloads any missing platform (here android-37) on first use.
- **Verified locally:** the workflow and composite action parse (SnakeYAML), every external `uses:` is SHA-pinned, `ci-ok` needs only existing jobs, and each job's exact Gradle command passes. **Verified on GitHub (2026-11-25):** the first run (push of `a3973b2` to `main`, cold cache) passed every job in about 5.5 minutes wall-clock: static 2m47s, unit 2m38s, api 2m11s, build 4m34s, ci-ok 4s. Target was under 15 minutes. Pull-request runs read the cache that `main` writes, so they should be faster.
- **Repository setup (done in Task 7):**
  - Commit `gradlew` as executable (`git add --chmod=+x gradlew`), or every Linux job fails with "Permission denied". Windows checkouts don't record the bit.
  - `.gitignore` now ignores every module's `build/` and `.kotlin/` (the template ignored only the root `build/`).

## Public API (Task 8)

- **Where it lives:** the public API is the `io.github.tracklens` package of tracklens-core; implementation goes in `io.github.tracklens.internal`. `tracklens-core/api/tracklens-core.api` is the authoritative record (54 classes).
- **Shared types:** every file directly in that package, except `TrackLens.kt`, must be plain public types (configuration, events, callbacks, `TrackLensBundles`). The no-op's `copySharedApiSources` task copies them into `tracklens-noop/build/generated/source/sharedApi`, so both artifacts share one source. Only the `TrackLens` facade is written twice. If a shared file references core internals, the no-op stops compiling; that's intended.
- **KDoc:** every public declaration in library modules needs it. detekt's `UndocumentedPublicClass/Function/Property` rules enforce it, so it runs in CI. Dokka is left for the docs site (Task 49) because its Android support, like BCV's, expects the `kotlin-android` plugin.
- **Clean ABI choices:**
  - Config classes use `internal` (not `private`) constructors, so Kotlin emits no synthetic accessor constructors.
  - The library convention sets `jvmDefault = NO_COMPATIBILITY`, so interface default methods are real Java default methods with no `DefaultImpls` classes.
- **`wrap` parameter order:** `wrap(source = default, delegate)`, with the delegate last, so `TrackLens.wrap(source = "firebase") { name, params -> … }` compiles. The first order, delegate first, broke that documented usage; a probe compiling host-style code against both artifacts caught it.
- **Probe for API changes:** to check host-facing usage, add a temporary file to `sample/src/main` and run `:sample:compileDebugKotlin :sample:compileReleaseKotlin`. Debug compiles against tracklens-core and release against tracklens-noop. Task 10's contract tests make this permanent.
- **Task ordering finding:** dependency-guard 0.5.0 declares the repository directory as an output of every `dependencyGuard` task. Any task scanning the repository (Spotless, detekt, `checkDependencyPolicy`) therefore needs a declared order, or Gradle rejects the build. It first showed up when more tasks ran in one build. `tracklens.root` orders those scans after `dependencyGuard`.

## API contract suite (Task 10)

`tracklens-contract-tests` has two product flavors, `core` and `noop`, each depending on one artifact. The same tests in `src/test` therefore run twice: `:tracklens-contract-tests:testCoreDebugUnitTest` and `:tracklens-contract-tests:testNoopDebugUnitTest`. Both are in `check` and in the CI unit job. The flavored task names are why plain `testDebugUnitTest` doesn't run them.

| Test class | Promise |
|---|---|
| `NeverThrowsContractTest` | No public call throws: edge-case names (empty, 10k chars, emoji, newlines), null and cyclic values, 5,000-entry maps, 20-level nested bundles, 8 threads × 1,000 calls, any call order. `clearAllData`/`exportSession` callbacks arrive exactly once. |
| `WrapContractTest` | `wrap()` forwards the caller's *same* map instance, every call exactly once and in order, even while disabled. SDK exceptions propagate as the same instance. In the no-op, `wrap()` returns the delegate itself. |
| `BundlesContractTest` | Map↔Bundle keeps values *and* types (Int stays Int, Long stays Long, …); nested maps, Firebase-style `items` lists, typed lists and arrays survive a round trip; unsupported values become text. |
| `ConfigContractTest` | Builders clamp bad values instead of throwing; section blocks build on earlier ones; redaction defaults cover e-mail, phone and card numbers; built configs are immutable. |
| `JavaInteropTest` (Java) | The API as a Java host uses it: static facade, overloads, builders, SAM lambdas, a delegate implementing only `logEvent` (default methods), `TrackLensBundleExtensions`. |

- **Proven:** with a no-op `wrap()` that swallowed events, 7 of the 30 tests failed with clear messages (for example "expected `[checkout_started]` but was `[]`").
- **Robolectric** runs the Android-dependent tests on the JVM at SDK 35 (`src/test/resources/robolectric.properties`); the newest level this Robolectric version is known to support. It downloads its Android runtime jar on first use (into `~/.m2`, outside the Gradle cache).
- **Contract scope:** these tests describe the promises of the public API, so later tasks must keep them passing as the stubs are replaced. Implementation details belong in each module's own tests.

## Never throw: guards, diagnostics and the kill switch (Task 11)

Code lives in `tracklens-core/src/main/kotlin/io/github/tracklens/internal/`.

| Piece | Behavior |
|---|---|
| `guard(operation, fallback) { … }` (`Failures.kt`) | Wraps every public `TrackLens` call. Catches every `Throwable`, including `LinkageError`s from dependency mismatches, records it, and returns a safe default. `wrap()` falls back to the host's own delegate, so forwarding survives. Restores the interrupt flag after an `InterruptedException`. |
| Fatal VM errors | An `OutOfMemoryError` or `StackOverflowError` anywhere engages the kill switch (`TrackLens.isEnabled = false`). |
| `CircuitBreaker` | Per component (`Component` enum). After 5 consecutive failures the component is turned off and no longer called, while the rest of TrackLens keeps working; a success resets the count. |
| `Diagnostics` | In-memory ring of the latest 200 entries (component, level, message, exception class + message + 12 frames). `report()` never throws, even for a `Throwable` whose getters throw. Logcat (tag `TrackLens`) gets only the first 10 reports per component, so a failing loop can't flood it. Persisted to the database in Phase 2; shown in the diagnostics view from Task 19. |
| Kill switch | `TrackLens.isEnabled` / `TrackLensConfig.enabled`. Checked first in every logging call (two volatile reads: installed-and-active, then enabled). |
| Callbacks exactly once | `OnceCompletionCallback` / `OnceExportCallback`. If the implementation fails before answering, the facade answers `false` / `onFailed`, and a late second answer is dropped. |
| `install()` checks (`InstallGate`) | Inactive below API 23 (`MIN_SUPPORTED_SDK`, must equal `android-minSdk`) and in secondary processes when `mainProcessOnly`. An unknown process name counts as main. A non-debuggable build gets an ERROR diagnostic plus a Logcat error pointing to `tracklens-noop`; TrackLens stays active. |
| `EventSink` | Where accepted logging calls go, behind the pipeline breaker. `NONE` for now; Task 13 installs the pipeline. Tests install failing sinks. |

**Proven** (`FaultInjectionTest` and the other `tracklens-core` tests, 24 in total):
- 24 logging calls against a sink that always throws: nothing reached the caller, and the sink was called exactly 5 times before its breaker opened.
- An `OutOfMemoryError` from the sink turned TrackLens off.
- `wrap()` kept forwarding while the pipeline was broken.

**Testing note:** the `TrackLens` facade is a process-wide singleton, so core tests call `TrackLensImpl.resetForTests()` before and after each test.

## Parameter normalization (Task 12)

`ParamNormalizer` (`tracklens-core/.../internal/ParamNormalizer.kt`) turns the `Map` or `Bundle` passed to `log()` into an immutable `NormalizedParams` snapshot on the caller's thread. The app can mutate or reuse its objects afterwards. It runs inside the pipeline breaker and hands the result to `EventSink.event(...)`.

| Input | Stored as |
|---|---|
| `String`, `Int`, `Long`, `Double`, `Float`, `Boolean`, `Short`, `Byte`, `Char`, `BigDecimal`, `BigInteger`, `null` | Itself; the type is kept because validation needs `Int` vs `Long` |
| Other `CharSequence` (e.g. `StringBuilder`) | `String` |
| `Bundle`, `Map` | `Map<String, *>` (non-string keys become text) |
| Arrays (primitive and object), `Collection`s, `ArrayList`s from `Bundle` | `List` |
| Anything else (enums, `Parcelable`s, `Serializable`s, `Size`, `SparseArray`, `AtomicInteger`, …) | `UnknownValue(className, text)`; text is `toString()`, or a note if it threw |

Limits (any cut sets `truncated = true`): 3 nested container levels below the top level (deeper → `"<nested too deep>"`, which also ends self-referencing structures), 100 top-level params, 250 elements per list or nested map, 1,024 characters per string, key or text. The limits sit above Firebase's (25 params, 200 `items`, 100/1,000 characters) so validation can still flag violations. A map changed by another thread mid-copy is retried up to 3 times, then recorded without params plus a diagnostic.

`ParamNormalizer`, `NormalizedParams` and `UnknownValue` are `@InternalTrackLensApi` (public bytecode, excluded from the ABI dump), so the benchmark module can call them. Every library module compiles with `-opt-in=io.github.tracklens.InternalTrackLensApi`, set by the library convention.

### Benchmark (vivo V2437, Android 16, 2026-11-25)

| Benchmark (`NormalizationBenchmark`) | Median | p99 (approx.) | Allocations |
|---|---|---|---|
| `normalizeTenParamMap` | 0.71 µs | 1.25 µs | 15 |
| `normalizeTenParamBundle` | 1.54 µs | 1.67 µs | 15 |
| `logTenParamsThroughTheFacade` (guard + kill switch + normalize + empty sink) | 0.86 µs | 1.27 µs | 15 |

Targets: normalization ≤ 5 µs (Task 12) and `log()` p50 < 10 µs / p99 < 100 µs (Task 16 re-measures with the pipeline).

**Running benchmarks:** `./gradlew :benchmark:connectedAndroidTest` with a device connected and unlocked. Results: `benchmark/build/outputs/connected_android_test_additional_output/releaseAndroidTest/connected/<device>/*benchmarkData.json`. **Leave the phone alone while it runs:** androidx.benchmark measures only while its `IsolationActivity` is in front. When the phone went back to its home screen mid-run, the run stalled for 20+ minutes without failing; force-stopping `io.github.tracklens.benchmark.test` and re-running fixed it.

## Event pipeline (Task 13)

`EventPipeline` (`tracklens-core/.../internal/EventPipeline.kt`) is the `EventSink` once `install()` accepts the process.

- **Caller thread** (after guard, kill switch, normalization): timestamps (wall clock + monotonic), a soft-cap check (`AtomicInteger` of pending logging calls, cap 10,000), and `trySend` to one unbounded `Channel`. Over the cap the call is dropped and counted; only the first drop is reported in diagnostics. Control messages are never dropped.
- **One ordered queue** carries logging calls, `MarkAllRead`, and `Barrier` (tests: `awaitIdle()`). Sessions and lifecycle signals join it in Task 25, so one consumer sees a single total order.
- **Pipeline thread** ("TrackLens-Pipeline", one background-priority daemon thread), in queue order:
  - per-session sequence number (from 1) and elapsed time;
  - current screen (a `SCREEN` event updates it, later events carry it);
  - the ring buffer (`liveBufferSize`, default 2,000, newest kept);
  - listeners;
  - a `LiveSnapshot` (`events`, `totalEvents`, `droppedEvents`, `unreadCount`, `version`) published on a `StateFlow` at most every 100 ms: immediately, then at most once per interval while events keep coming.
- **Processing failures** go through the pipeline's own circuit breaker. The pending count drops when a message leaves the queue, so a failing consumer can't fake overflow drops.
- **Listeners:** `ListenerRegistry` sits outside the pipeline, so listeners added before `install()` work. Each runs on its executor (default: main thread). A throwing listener or rejecting executor is reported and the others still run.
- **Sessions:** until Task 25 there is one implicit session per pipeline; `TrackLens.currentSessionId` returns its id.
- **Dependency:** `kotlinx-coroutines-core` 1.10.2 (policy line) in tracklens-core, which moved JetBrains annotations from 13.0 to 23.0.0 on dependent modules. All five dependent baselines were re-recorded; the no-op's is unchanged.

**Proven** (`EventPipelineTest`, 16 new tests in core, 54 in total):
- 8 threads × 50,000 events: all 400,000 arrive numbered 1…400,000 with no gaps and each thread's order intact (0.25 s).
- With the consumer held back and a cap of 10,000: exactly 10,000 accepted and 390,000 counted as dropped (0.16 s).
- The publish throttle, tested on virtual time: the first event publishes at once, the rest wait exactly 100 ms.

**Benchmark** (vivo V2437, Android 16): a whole `TrackLens.log(name, 10 params)` call is now 2.05 µs median (target < 10 µs). In this tight loop some calls may take the cheaper over-cap drop path; Task 16 re-measures with drops reported.

**Build fix:** `dependencyGuardBaseline` also declares the repository as its output, so the repository scans now run after both dependency-guard tasks.

## Redaction (Task 14)

`Redactor` (`tracklens-core/.../internal/Redactor.kt`) is built once from `RedactionConfig`. It runs on the pipeline thread before an event reaches the ring buffer, listeners or (later) storage. The host's SDK still gets the original values through `wrap()`. Validation (Task 38) will check the raw values in the marked slot just before it.

| Rule | Behavior |
|---|---|
| Key rules (`keys`) | Case-insensitive; `*` = any characters. A matching parameter is replaced entirely, whatever its type, at any depth (nested maps, `items` lists). Defaults: `*password*`, `*token*`, `*secret*`, `*email*`, `*phone*`. |
| Value patterns (`patterns`) | Matches are replaced inside strings and `UnknownValue` text. `Long`/`Int`/`BigInteger` values with 8+ digits are checked too, so a card number logged as a `Long` is caught. Defaults: `EMAIL`, `PHONE`, `CREDIT_CARD`. |
| `CREDIT_CARD` | Replaced only if the digits (13–19) pass the Luhn checksum, so ids and timestamps survive. |
| `PHONE` | Tightened in Task 14: needs a leading `+` or separators (`(415) 555-0100`, `415.555.0100`), so plain digit runs such as order ids are left alone. |
| Replacement | `[REDACTED]`, or with `hashValues` a `sha256:` + 8 hex prefix (unsalted, so equal values stay comparable across sessions). |
| User id | `HASH` (default), `MASK` (`u*****2`) or `NONE`. |
| User properties | The property name goes through the key rules; the value through the patterns. |
| Invalid custom regex | Reported in diagnostics and skipped; never throws. |

**Proven:** `RedactorTest` covers every rule (13 tests). `RedactionLeakTest` sends six secrets through the real pipeline: an e-mail at several depths, a card number as text and as a `Long`, a phone number, a password, a token and a user id. None of them appears in the ring buffer or in what listeners received.

**Note:** Python string escapes once turned `\b` into backspace characters in a regex. Write regex-bearing code with the file writer, not through Python string literals.

## Environment quirks (local machine)

- **Expected warning:** Gradle on JDK 25 prints `sun.misc.Unsafe` warnings from its bundled protobuf. They are harmless.
- **PowerShell 5.1 log encoding:** `>` redirection writes UTF-16 LE logs. Decode them before grepping, and write the decoded text as UTF-8; printing it to a cp1252 console fails on non-ASCII characters.
- **Android CLI SDK path:** the new `android` CLI (which `sdkmanager` now forwards to) defaults to `%LOCALAPPDATA%\Android\Sdk`, not this project's SDK. Always pass `--sdk=<path from local.properties>`, for example `android --sdk='D:\ProgramFiles\AndroidStudio\Sdk' sdk install platforms/android-37.1`. Package names use slashes (`platforms/android-37.1`), not semicolons.
- **PowerShell execution policy:** running `.ps1` scripts is disabled on this machine, so multi-step commands are typed directly (chained with `;`).
- **Gradle from Claude Code's shell:** in the Claude Code session, Gradle cannot connect to its daemon from the agent's own shell ("Unable to establish loopback connection"). Builds run fine from a normal PowerShell terminal.
