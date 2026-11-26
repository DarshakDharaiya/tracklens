# Build notes

Findings from the Task 2 toolchain spike, updated in Tasks 3 and 4 (2026-11-25). Update this file whenever a toolchain version changes. Version *choices* follow [dependencies.md](dependencies.md).

## Verified toolchain

| Component | Version | Verified by |
|---|---|---|
| Gradle | 9.9.2 (daemon on Temurin JDK 25, provisioned by the daemon toolchain) | All builds below |
| Android Gradle Plugin | 9.5.3, **built-in Kotlin** (no `org.jetbrains.kotlin.android` plugin) | All builds below |
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
| `tracklens.android.application` | `com.android.application`, the shared settings, targetSdk, version 1 / "1.0", test runner, full-scope R8 `optimization` for release (Task 21 removed the template's `packageScope`) |
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

### Benchmark (vivo V2437, Android 16, 2026-11-25; current numbers in [perf.md](perf.md))

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

**Benchmark:** superseded by Task 16's drop-checked measurements in [perf.md](perf.md) (p50 0.92 µs, p99 4.6 µs per call).

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

## Floating bubble (Task 17)

`BubbleView` (`tracklens-ui/.../ui/overlay/`) is a plain custom `View`, so it can sit in any host activity's window, Compose or not. Placement rules live in `BubblePositioner`, which uses no Android types.

- **Look:** teal circle (`#00695C`) with a white lens icon, about 6:1 contrast. The red badge (`#C62828`) with white text is about 5.6:1 and shows the unread count, "99+" above 99, hidden at 0. Size is 56 dp (touch target ≥ 48 dp), with an 8 dp margin and 6 dp elevation.
- **Drag:** starts after the system touch slop; anything shorter is a tap. On release it snaps to the nearer edge with a 200 ms animation, skipped when reduced motion or the system animation scale is 0. The stored position is `(edge, yFraction)`, so it survives rotation and resizes. `START`/`END` follow layout direction.
- **Insets:** stays inside system bars and display cutouts. **Device finding:** an overlay added to the decor view *after* the window dispatched its insets never receives them. Robolectric didn't show this; on the phone the bubble sat under the status bar. The bubble now re-requests insets on attach and falls back to the window's root insets until a dispatch arrives. Before Android 11 it takes the larger of the dispatched and root insets, because a sibling can consume them first.
- **Accessibility:** announced as a button with its count ("TrackLens, 12 unread events"). Custom actions: Open TrackLens, Move bubble to the other side, Hide TrackLens bubble.
- **Resources:** `tracklens-ui` uses `resourcePrefix = "tracklens_"` so library resources can't collide with the host's.
- **Left for Task 18:** attaching to activities, persisting the position, and drag-to-dismiss, which needs a visible drop target owned by the overlay.

**Tests:**
- 9 positioner tests (JVM).
- 8 Robolectric `BubbleView` tests: badge, spoken text, button role and size, tap, TalkBack move and hide, margins, insets.
- 3 Roborazzi screenshots: `bubble_unread_0/7/99_plus`, stored in `tracklens-ui/src/test/screenshots/`, verified by `verifyRoborazziDebug` in CI.
- 4 instrumented tests on the phone with real touch injection (`Instrumentation.sendPointerSync`): drag across snaps to the other edge; a sub-slop move is a tap; the bubble never overlaps the device's real bars even when dragged past the top or bottom; arbitrary insets are respected.

**Build additions:**
- Library modules now set `AndroidJUnitRunner` and depend on `androidx.test:runner` for `androidTest`.
- The Compose convention applies Roborazzi (with Robolectric and `androidx.test:core`) to library modules.
- `tracklens-ui` declares `androidx.core` (1.18.0, the policy line). Its baseline and the umbrella's moved from the 1.16.0 that Compose pulled in transitively.

**Screenshot baselines** were recorded on Windows. If Linux CI renders the badge text differently, re-record them from CI's actual images (uploaded with `unit-test-reports` on failure). The plan prefers Linux-recorded baselines.

**Instrumented tests:** `./gradlew :tracklens-ui:connectedDebugAndroidTest` with a device connected. The test activity briefly appears on screen.

## Overlay attacher (Task 18)

**How tracklens-ui plugs in:** core never depends on the UI. `TrackLens.install` loads every `TrackLensModule` with `ServiceLoader`. tracklens-ui registers `UiModule` in `META-INF/services/io.github.tracklens.internal.spi.TrackLensModule`, and its consumer R8 rule keeps the class and its constructor. Each module gets a `TrackLensRuntime`: the config, the live snapshot, bubble visibility, mark-as-read, open-panel and failure reporting. A module that throws is reported in diagnostics and skipped. `TrackedEvents.create` (`@InternalTrackLensApi`) builds events for previews and tests in other modules.

**`OverlayAttacher`** (`Application.ActivityLifecycleCallbacks`, main thread only):
- **Attach and detach:** adds the bubble to the resumed activity's decor view and removes it on pause, so exactly one bubble exists at a time.
- **Where it doesn't show:**
  - activities in `BubbleConfig.excludedActivities`;
  - TrackLens' own screens, marked with `NoBubbleScreen` (a package-prefix check would also have hidden it in the sample app);
  - `OverlayMode.NONE`;
  - while hidden with `TrackLens.hideBubble()`.
- **Position:** saved in the `tracklens_overlay` SharedPreferences as `(edge, yFraction)`, so it carries across activities, rotation and restarts.
- **Badge:** follows `LiveSnapshot.unreadCount`. Opening the mini panel marks everything read.
- **Drag to dismiss:** while dragging, a 64 dp "×" target appears at bottom center, above the navigation bar. It turns red when the bubble is over it, and a drop there hides the bubble.
- **Mini panel** (Compose): the last 5 events (newest first, ⚠ on events with issues), the total, Pause/Resume (freezes the list), and "Open TrackLens". That button is a no-op until Task 19 adds `TrackLensActivity`. The panel sits beside the bubble: below it in the upper half of the screen, above it in the lower half.
- **Failures:** every callback is guarded. After 5 failures the overlay removes itself and reports why; the host app and logging carry on.

**Finding: a `ComposeView` in a plain `Activity`.** Compose's default recomposer belongs to the window. It is created from the lifecycle owner of the window's root view, here the decor view, not from the `ComposeView`. A plain `Activity` has no owner there, so the first mini panel crashed with "ViewTreeLifecycleOwner not found". `OverlayViewTreeOwner` now gives each overlay view its own lifecycle, saved-state registry and `Recomposer` (`setParentCompositionContext`), and cancels them when the panel closes. This also keeps TrackLens' UI independent of the host's lifecycle in a `ComponentActivity`.

**Deferred to Task 19:** the launcher shortcut (it needs `TrackLensActivity`) and a working "Open TrackLens".

**Tests:**
- 10 Robolectric attacher tests:
  - the bubble moves to the resumed activity;
  - excluded activities, own screens and `NONE` mode get no bubble;
  - the badge follows unread events;
  - a tap opens and closes the mini panel and marks events read;
  - hide and show;
  - a drop on the target hides the bubble, a drop elsewhere doesn't;
  - the position carries across activities;
  - 5 failures turn the overlay off;
  - `ServiceLoader` finds `UiModule`.
- 3 Roborazzi screenshots: `mini_panel_events/paused/empty`. The panel is hosted in a plain `Activity` with `WRAP_CONTENT`, as in production.
- 2 instrumented tests on the phone through the real `TrackLens.install` (so ServiceLoader runs on the device too):
  - the bubble follows across three activities and a rotation to landscape, keeping the position it was dragged to;
  - hide and show.
  - The test activities are declared in `src/androidTest/AndroidManifest.xml`, which ships only in the test APK.

**Build additions:** tracklens-ui depends on `kotlinx-coroutines-android` (1.10.2, the policy line) and ships `consumer-rules.pro`. tracklens-core exposes `kotlinx-coroutines-core` as `api`, because `TrackLensRuntime` uses `StateFlow`. The ABI dump (`apiBuild`) now drops the Compose compiler's `ComposableSingletons$<File>Kt` classes. They are public in bytecode but not API: their getters are module-internal and named after lambda hashes, so the dump would change on every UI edit.

## TrackLens screen shell (Task 19)

**`TrackLensActivity`** (`tracklens-ui/.../ui/`):
- **Declared in tracklens-ui's manifest:**
  - `exported="false"`;
  - `taskAffinity="${applicationId}.tracklens"` with `singleTask`, so it is its own entry in Recents and Back returns to the app;
  - the platform `Theme.Material.NoActionBar`, so there is no MDC XML dependency;
  - `enableOnBackInvokedCallback`, for predictive back in hosts that target below Android 16.
- **Edge-to-edge**, and it implements `NoBubbleScreen`, so the bubble never shows on it.
- **The runtime** comes from `UiRuntime`, which `UiModule` sets on install. If Android restores the screen into a process where TrackLens isn't installed, it explains what to do instead of crashing.

**Navigation:** `BackStack` is immutable, never empty and always rooted at Live.
- Switching tabs puts that tab's root above Live, so Back from any tab returns to Live, and Back from Live closes TrackLens.
- Tapping the current tab again returns to its root.
- Back goes through `BackHandler`, so predictive back works.
- The stack is saved with `rememberSaveable` as route strings (`live`, `plan`, `event/42`) instead of `@Parcelize`, which avoids adding the parcelize plugin. Routes it doesn't recognize are dropped on restore.
- Tab screens are placeholders until Tasks 20 onwards.

**`TrackLensTheme`** (`ui.design`):
- **Palette:** hand-tuned teal light and dark schemes (the bubble's teal, a slate secondary, an amber tertiary).
- **Seed color:** `ThemeConfig.seedColor` derives only the primary group, keeping the seed's hue and shifting its lightness until every pair passes.
- **Dynamic color:** on Android 12+ when `dynamicColor` is on.
- **Dark mode:** follows `DarkMode`.
- The mini panel now uses it too, replacing the interim overlay theme; its screenshots were re-recorded.
- **Icons:** the tab icons are small `ImageVector`s in `TrackLensIcons`, so there is no dependency on the Material icons artifact.

**Opening TrackLens:**
- **`TrackLens.openPanel`:** core asks each installed module (`TrackLensModule.openPanel`, default `false`). `UiModule` starts the activity in its own task. With no UI module, it logs a diagnostics warning.
- **The mini panel's "Open TrackLens"** goes through the same path.
- **Launcher shortcut:** a dynamic shortcut ("TrackLens", teal lens icon), pushed by `UiModule` on a background thread because it is a system call. It is removed when `BubbleConfig.showAppShortcut` is `false`.

**Finding:** the first seeded dark palette passed against the surface (4.5:1) but not against the surface containers where text buttons sit (4.36:1). Seeded primaries are now checked against `surfaceContainerHighest`, the worst case in both modes.

**Device finding:** Android attaches dynamic shortcuts to the app's launcher activity, and `pushDynamicShortcut` throws (`ShortcutService.fillInDefaultActivity`) when there is none, as in a library's test APK. The guard caught and reported it. `LauncherShortcut` now skips apps without a launcher activity, and the androidTest host activity is a launcher activity, as in a real app.

**Tests:**
- 7 `BackStack` tests (JVM).
- 4 palette contrast tests. Every text pair is ≥ 4.5:1 and outlines ≥ 3:1, for the default palette and 9 seeds (pale yellow, cyan, pink, blue, grey, near-black, near-white, translucent), in light and dark.
- 6 Robolectric activity tests: opens on Live; tab then Back to Live then Back closes; the stack survives recreation; the not-installed state; two screenshots (`tracklens_shell_live_light`, `tracklens_shell_sessions_dark`).
- 5 `UiModule` tests: runtime handoff, `openPanel` starts the activity with `NEW_TASK`, the shortcut is published, an app without a launcher activity gets none (and no error), and turning it off removes it.
- 3 core `ModuleLoader` tests: a failing module doesn't stop the others, a broken discovery is reported, modules without a screen don't claim `openPanel`.
- 2 instrumented tests on the phone through the real install: `openPanel` opens TrackLens in a different task with no bubble, and Back returns to the same host activity; the launcher shortcut is published.

**Build additions:** tracklens-ui uses `ui-test-junit4` for its Robolectric Compose tests, with the v2 `createEmptyComposeRule`.

## Live screen and event detail (Task 20)

**Live** (`ui/live/LiveScreen.kt`):
- **The list:** every event of this run, newest first, keyed by sequence number. Each row shows the number and time, the name, the screen and parameter keys, a kind label (unless it's a plain event) and an icon for its worst issue. The icon's shape differs by severity, and it is spoken as "2 issues, worst: Error". Seeing the screen marks events read.
- **Search:** case-insensitive, over the name, screen, parameter keys and parameter values, including nested maps and lists.
- **Filter chips:** Kind, Screen and Source (multi-select menus built from the events shown), Severity ("Warning or worse") and "Has issues". There's no Category chip yet: events get categories from the tracking plan in Task 38.
- **Pause** freezes the list at the newest sequence number and shows "Paused · N new events held back" with Resume. It stores only that number, so it survives rotation. Events older than the buffer still roll out while paused.
- **Follow newest** (a toggle in the top bar) keeps the list at the top. Dragging the list turns it off, and "Jump to newest" turns it back on.
- **Clear** empties the live buffer through a new pipeline message (`ClearLive`, in queue order). Sequence numbers and the session continue.
- **Dropped-calls banner:** shown when the pipeline dropped logging calls because the app logged faster than it could keep up.
- **State:** the filter (`LiveFilter`, a plain class), pause and follow live in `rememberSaveable`, not in a ViewModel with `SavedStateHandle` as the plan said. That survives rotation and process death without the lifecycle-viewmodel dependency.

**Event detail** (`EventDetailScreen.kt`):
- **Overview:** sequence, kind, time, time since session start, screen, source, session.
- **Parameters:** each with a type badge (text, integer, decimal, boolean, map, list, or the class of an unknown value) and a "redacted" badge. TrackLens keeps only the redacted value, so the badge recognizes the configured replacement text or `sha256:` hash.
- **Issues:** sorted worst first, each with severity, code, parameter and message.
- **JSON:** highlighted, selectable, with **Copy JSON**. `EventJson` is a small hand-written encoder: no serialization dependency, valid JSON for every normalized type, non-finite numbers as strings.
- **Navigation:** Previous/Next replace the screen rather than push it, so one Back returns to the list. An event that has left the live buffer gets an explanation instead of an empty page.
- **Not yet:** "Show in timeline" waits for the session dashboard.

**Other:**
- More hand-drawn icons in `TrackLensIcons`. Back, Previous and Next mirror in right-to-left layouts.
- Screens draw their own top bars, and the scaffold no longer pads for the status bar.
- **Time zone:** `EventFormat.time` reads the default time zone on each call. The Live tests pin UTC, so screenshots look the same on every machine and on CI (they first rendered in this machine's IST and would have failed on Linux).

**Tests:**
- 5 `LiveFilter` tests (JVM): search, each chip, severity "or worse", save and restore.
- 4 `EventJson`/`EventFormat` tests (JVM): exact JSON of an event, escaping and number edge cases, type and value formatting, redaction recognition.
- 1 more pipeline test: Clear empties the buffer, but sequence numbers and totals continue.
- 13 Robolectric Compose tests through the real activity:
  - the list is newest first;
  - search narrows the list and can be cleared;
  - the Kind menu and "Has issues" filter;
  - "no matches" offers to clear the filters;
  - pause holds back 2 new events until Resume;
  - Clear goes through the runtime;
  - the dropped banner shows and events get marked read;
  - TalkBack descriptions on issue icons;
  - detail shows parameters, badges and issues, and Copy JSON puts exactly the encoded JSON on the clipboard;
  - Previous/Next, then one Back to the list;
  - a cleared event is explained;
  - 3 screenshots (`tracklens_live_list_light`, `tracklens_live_filtered_dark`, `tracklens_event_detail_light`).
- **1 instrumented burst test on the phone** (`LiveBurstDeviceTest`), through the real install and pipeline. 1,000 events logged from a background thread (about 30 ms) all reach the list, newest first and in order. After one warm-up round, 8 real flings are measured with `FrameMetrics` (vivo V2437, debuggable build):

  | Run | Frames | p50 | p90 | Max | Slower than 33 ms |
  |---|---|---|---|---|---|
  | Without warm-up, before the row slimming | 728–734 | 11–12 ms | 27–36 ms | 76–91 ms | 6–13% |
  | With warm-up (final, two runs) | 652 / 784 | 11 / 10 ms | 26 / 22 ms | 55 / 45 ms | 6.7% / 1.0% |

  - **Limits:** the test requires p50 ≤ 17 ms (one 60 Hz frame), p90 ≤ 50 ms and at most 15% of frames over 33 ms. They are set for a debuggable build, because that's the only kind TrackLens runs in. They still catch a real regression (the broken measurement below produced 191 ms median frames).
  - **Warm-up:** a debuggable app runs without ahead-of-time compiled code, so the first flings after a fresh install also measure the JIT warming up. Real users scroll more than once, so the test does one unmeasured round first.
  - **Row slimming:** rows draw their divider instead of composing one, and the kind label uses a background instead of a `Surface`.
  - **Arrival time:** the time until the newest row is on screen (0.4–1.2 s across runs) is inflated by the measurement itself, because every UiAutomation poll walks the whole accessibility tree. The MVP's "within 200 ms" check belongs to the sample app (Task 21+) and needs a lighter probe.

**Code organization:** detekt's function-count and complexity limits split the Live screen into `LiveScreen.kt`, `FilterChips.kt` and `EventRow.kt`. `Banner` and `EmptyState` moved to `ui.design/Messages.kt` for the screens to come.

**Lint:** "keeps the newest N events" became a plural (`PluralsCandidate`, an error under warningsAsErrors).

**Device findings (test harness, not TrackLens):**
- **Compose test rules distort frame timing.** While a Compose test rule is active, Compose runs frames and animations on the test's clock. `performTouchInput` delivered each swipe in one main-thread task (p50 191 ms frames), and real touch input then didn't move the list at all. The burst test therefore uses no Compose rule: it injects real touch events about every 8 ms and finds rows through UiAutomation.
- **Compose's accessibility provider doesn't implement `findAccessibilityNodeInfosByText`,** so the test walks the node tree itself.

## Sample app v1 (Task 21)

**Setup** (`:sample`):
- `debugImplementation(:tracklens)` and `releaseImplementation(:tracklens-noop)`.
- `SampleApplication` installs TrackLens with one line in `onCreate`. The same code compiles against both artifacts.
- The release build is signed with the debug key, so the R8 build can be installed and checked on a device. The sample is never published.

**Fake SDKs** (`sample/.../sdk/`):
- **`FakeFirebaseAnalytics`:** `logEvent(name, Bundle)`, user properties and a user id.
- **`FakeAmplitude`:** `track(eventType, properties)`, `identify` and a user id.
- Both write to logcat (tags `FakeFirebase`, `FakeAmplitude`) and to `SdkCallLog`, which the sample shows on screen. That way a release build visibly still logs even on phones that hide logcat output from non-debuggable apps.
- **Integration:** each SDK sits behind an `AnalyticsDelegate` (`FirebaseDelegate`, `AmplitudeDelegate`) wrapped with `TrackLens.wrap("firebase" / "amplitude", …)`, the pattern the README will show. Debug builds record every call tagged with its SDK and then forward it. The no-op's `wrap` returns the delegate itself.

**Demo flows** (`DemoFlows`), each a button and runnable by intent (`adb shell am start -n io.github.tracklens.sample/.MainActivity --es flow <id>`):

| Id | What it logs |
|---|---|
| `browse` | Home and Product screens, `view_item_list` and `view_item` with item lists |
| `add_to_cart` | `add_to_cart` |
| `checkout` | Cart, Checkout and Confirmation screens; `begin_checkout`, `add_shipping_info`, `add_payment_info`, `purchase` |
| `sign_in` | `login`, plus user id and `plan` property for both SDKs |
| `sign_out` | `logout`, user ids cleared |
| `play_song` | Amplitude's "Song Played" |
| `sensitive` | e-mail, phone, card number in text, password, for redaction |
| `invalid` | reserved prefix, a name that's too long, a hyphen, 30 parameters (validation arrives in Task 38) |
| `burst` | 1,000 events from a background thread |

The screen also has Open panel, Show bubble and Hide bubble (debug only), and the SDK calls list.

**Device finding: the R8 release sample crashed at startup**, with `IllegalAccessError: Class kotlin.collections.h extended by class ec is inaccessible`, inside `androidx.startup`, before any app code ran.
- **Cause:** the project template's `optimization { packageScope = androidx.**, kotlin.**, kotlinx.** }`. That partial-scope R8 mode moved a subclass out of the package of its package-private kotlin.collections superclass.
- **Not the AGP bump:** AGP 9.5.2 crashes the same way.
- **Why it went unnoticed:** CI only *builds* the release sample, and it had never been launched.
- **Fix:** the application convention now runs full-scope R8, as a host's release build does. Task 47's release check should also *launch* the release sample, not only inspect it.

**Verified on the phone (vivo V2437, Android 16):**
- **Release (R8, tracklens-noop):**
  - It starts and shows "Release build: no TrackLens", with no bubble in the window (UI Automator dump).
  - The checkout flow reached the fake SDK: 4 calls on screen and 4 `FakeFirebase` lines in logcat.
  - Of 1,862 classes in the dex, the only `io.github.tracklens` ones are the sample's `MainActivity` and `SampleApplication`: R8 removed the whole no-op.
  - The manifest has no TrackLens components.
- **Debug:** the bubble shows "TrackLens, 7 unread events" after checkout (4 events, 3 screens), and the "TrackLens" launcher shortcut is published.
- **6 instrumented tests** (`SampleFlowsTest`, debug):
  - checkout reaches TrackLens (tagged `firebase`, with screens) and the SDK in the same order, with items as bundles;
  - Amplitude events are tagged `amplitude`;
  - sign-in records user ids and properties for both SDKs;
  - redaction masks e-mail, card and password in what TrackLens keeps while the SDK receives the real values;
  - a 1,000-event burst arrives complete and in order;
  - the debug build reports TrackLens installed.

**Version:** the library still reports `0.1.0-SNAPSHOT` (`LIBRARY_VERSION`); Task 50 generates it from the published version. The `v0.1.0-alpha01` git tag marks the MVP snapshot; nothing is published yet.

## Room schema v1 (Task 22)

**Schema** (`tracklens-storage/.../internal/storage/`, exported to `tracklens-storage/schemas/.../1.json`):

| Table | Columns | Indices | Foreign keys |
|---|---|---|---|
| `sessions` | 29 | `uuid` (unique), `started_at` | — |
| `events` | 14 | `(session_id, seq)` unique, `(session_id, name)`, `(session_id, elapsed_ms)`, `(session_id, max_severity)`, `screen_visit_id` | session CASCADE, visit SET NULL |
| `event_params` | 8 | `event_id`, `(session_id, key)`, `(session_id, key, value_text)` | event CASCADE |
| `screen_visits` | 9 | `(session_id, order_index)` unique | session CASCADE |
| `warnings` | 7 | `(session_id, code)`, `event_id` | session CASCADE, event CASCADE |
| `diagnostics` | 6 | — | — |

- **Columns:** exactly plan section 3.2. Enums (`EventKind`, `SessionEndReason`, `ParamValueType`, `ScreenVisitSource`) are stored by name. Severities are 0–3 (`SeverityLevel`). The extra `events.screen_visit_id` index is required by Room for the foreign key.
- **Package:** `io.github.tracklens.internal.storage`, so it stays out of the ABI dump. Types tracklens-ui's dashboards will use are public `@InternalTrackLensApi`.

**DAOs:**
- **`SessionDao`:** list (active first, then newest), end, counters, pin, label, delete.
- **`WriteDao`:** batch inserts, plus `insertEvent` with its params and warnings in one transaction, screen visits and diagnostics trimming.
- **`DashboardDao`** (charts): summary, timeline, events per bucket, top events, name counts, invalid names, screen flow.
- **`FindingsDao`:** warning groups and rows, funnel events, comparison counts and order.
- **`EventListDao`:** keyset-paged event list with name/severity filters, event detail, search by parameter.
- These three DAOs together cover every query in plan section 3.4. They are split by what they serve because one interface had more functions than detekt allows.
- Dashboard queries are `Flow`s that re-emit on writes. One-shot queries (funnels, comparison, paging) are `suspend`.

**Database:**
- `TrackLensDatabase.open(context)` creates `noBackupFilesDir/tracklens.db`, so it's never in Auto Backup or device transfer.
- Before 1.0, a schema change drops the data (`fallbackToDestructiveMigration(dropAllTables = true)`).
- Foreign keys are turned on, and `auto_vacuum` is set to INCREMENTAL for retention.

**Device finding: reading SQLite settings in WAL mode.**
- Android's SQLite defaults to `auto_vacuum = FULL`, and Room opens the database in WAL mode with a connection pool. Reads outside a transaction go to a pooled read connection, which kept reporting the old mode (1) after the switch, even though the file already said INCREMENTAL (2).
- My first `onOpen` checked the mode that way, so on every open it would have seen 1 and run a full `VACUUM`, rewriting the whole database.
- `TrackLensDatabase.autoVacuumMode` now reads inside a transaction (the primary connection). The VACUUM runs once, on first open, while the database is still empty. `foreign_keys` is per connection too, and matters on the primary connection, where deletes run.

**Tests: 17 instrumented, on the phone (vivo V2437).**
- **`DashboardFixture`:** three sessions with hand-computed numbers, written up as a table in its KDoc. Session A is active with 14 records across 5 screen visits (one record outside any visit) and 6 warnings, one of them session-level. Session B has 5 events for comparison. Session C is empty.
- **`DashboardDaoTest`** (16): asserts the fixture numbers for every widget query, for example:
  - summary 10 events / 7 names / 2 invalid / 4 screens / 6 warnings;
  - 60-second buckets `(4, 1 warning)` and `(6, 2 warnings)`;
  - warning groups worst first;
  - funnel sequence 4, 5, 6, 10, 11, 14;
  - keyset pages and `LIKE` filters (`add%` matches `add_to_cart` and `add_payment_info`);
  - search by `item_id`.
  - Further tests cover: counter and lifecycle updates; deleting a session cascades to everything in it and nothing else; deleting a visit keeps its events without a visit; dashboard Flows follow new writes; diagnostics trimming.
- **`DatabaseFileTest`** (1): the file is in `noBackupFilesDir`, `auto_vacuum` is INCREMENTAL on the primary connection and in the file itself (checked through a separate plain connection), `foreign_keys` is on, and `user_version` is 1.

## Batched writer (Task 23)

**How events reach storage:**
- Core still never depends on storage. tracklens-storage registers `StorageModule` (ServiceLoader, kept by its consumer R8 rule), which adds a `RecordObserver` through the runtime.
- The pipeline calls observers synchronously on its own thread, in recording order, after redaction, with the keys redaction changed. A failing observer is reported and never stops recording.
- The runtime also gained `awaitPipelineIdle` (for the crash flush) and `reportStorageProblem`.

**`BatchedWriter`** (plan section 4.4):
- **Buffering:** `enqueue` on the pipeline thread only buffers. Writes happen on one "TrackLens-Writer" thread, one Room transaction per batch, so a batch is stored completely or not at all. The database opens lazily on that thread.
- **Flush triggers:**
  - 64 buffered records (immediately);
  - 200 ms after the first unflushed record;
  - `ProcessLifecycleOwner` ON_STOP;
  - `onTrimMemory` at `TRIM_MEMORY_UI_HIDDEN` or higher;
  - session end (Task 25);
  - the crash handler.
- **Why 200 ms, not 250:** the plan says 250 ms *and* that a sudden death loses at most 250 ms. A record that arrives while a batch is being written also waits for that write, so a 250 ms timer can't keep that promise. The 50 ms margin covers the write.
- **Crash flush:** `CrashFlushHandler` is installed in front of the existing default handler. Within an 800 ms budget it drains the pipeline (so the events logged just before the crash are included), flushes the writer, then always passes the same thread and exception to the previous handler. A crash on the pipeline or writer thread skips the step that would wait for itself.
- **Backpressure:** more than 5,000 buffered records and the oldest are dropped from storage only (the live view keeps them). They're counted in `sessions.dropped_count`, with an `SE_EVENTS_DROPPED` session warning.
- **Truncation:** at `RetentionConfig.maxEventsPerSession`, a session stores nothing more, `truncated = 1`, and it gets one `SE_SESSION_TRUNCATED` warning.
- **Storage breaker:** a failed batch is put back and retried. After 5 consecutive failures storage turns itself off and reports it; the live view keeps working.
- **Rows:** `RecordMapper` turns a record into its rows:
  - `params_json` is written with core's `JsonEncoder`, so it matches Copy JSON;
  - each top-level parameter gets a typed `event_params` row (STRING, LONG, DOUBLE, BOOLEAN, NULL, ARRAY, BUNDLE, OTHER) with a text and numeric form and the `redacted` flag;
  - the event's issues become warnings, and `max_severity` and the session counters are updated in the same transaction.
- **Sessions:** a session row is created lazily from the first record of a pipeline session. Task 25 replaces this with explicit starts, ends, interrupted recovery and device metadata.

**Shared JSON:** the Task 20 encoder moved from tracklens-ui to core (`JsonEncoder`, compact or indented). The UI's `EventJson` now uses it; its exact-output tests pass unchanged.

**Bug caught by the tests:** at first, a full batch of 64 waited for the timer. Both triggers shared one "flush posted" flag, so a waiting timer suppressed the immediate write. They're now separate flags.

**Tests:**
- 2 core `RecordObserverTest` tests: every record arrives in order with the redacted keys (e-mail, a phone number as a user property), and a failing observer doesn't stop the others.
- 11 instrumented `BatchedWriterTest` tests (in-memory Room, a test-driven writer thread):
  - one timer for many records;
  - a full batch is written immediately;
  - `flush()` doesn't wait;
  - **a batch with a duplicate sequence number stores nothing of that batch** and is put back;
  - 5 failures turn storage off;
  - backpressure keeps the newest 100 of 110 and records 10 dropped;
  - truncation at 10, warned once, with other sessions unaffected;
  - every value type, the redacted flag, warnings and counters;
  - the crash handler flushes and then calls the previous handler with the same thread and exception;
  - the crash handler gives up after its budget when the writer is stuck;
  - background and trim-memory signals (not `TRIM_MEMORY_RUNNING_LOW`, when the UI is still visible).
- **Process death on the phone:** `sample/qa/measure_kill_loss.py` runs the sample's new `ticker` flow (a numbered event every 10 ms, also written to logcat), kills the process with `kill -9`, copies the database out (WAL included) and compares the newest tick in logcat with the newest stored, using device timestamps. This is the worst case: no ON_STOP, no crash handler.

  | Runs | Lost (min) | Lost (median) | Lost (max) |
  |---|---|---|---|
  | 10 | 53 ms | 171 ms | **217 ms** |

  At most 250 ms, as required.

## Environment quirks (local machine)

- **Expected warning:** Gradle on JDK 25 prints `sun.misc.Unsafe` warnings from its bundled protobuf. They are harmless.
- **PowerShell 5.1 log encoding:** `>` redirection writes UTF-16 LE logs. Decode them before grepping, and write the decoded text as UTF-8; printing it to a cp1252 console fails on non-ASCII characters.
- **Android CLI SDK path:** the new `android` CLI (which `sdkmanager` now forwards to) defaults to `%LOCALAPPDATA%\Android\Sdk`, not this project's SDK. Always pass `--sdk=<path from local.properties>`, for example `android --sdk='D:\ProgramFiles\AndroidStudio\Sdk' sdk install platforms/android-37.1`. Package names use slashes (`platforms/android-37.1`), not semicolons.
- **PowerShell execution policy:** running `.ps1` scripts is disabled on this machine, so multi-step commands are typed directly (chained with `;`).
- **Gradle from Claude Code's shell:** in the Claude Code session, Gradle cannot connect to its daemon from the agent's own shell ("Unable to establish loopback connection"). Builds run fine from a normal PowerShell terminal.
