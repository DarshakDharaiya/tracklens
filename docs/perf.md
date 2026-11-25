# Performance

What TrackLens costs the app that calls it. Every logging call runs on the caller's thread, often the main thread, so this is the cost that matters (plan section 4.2).

## Targets

| What | Target | Source |
|---|---|---|
| `TrackLens.log(...)` per call, median | < 10 µs | Plan section 4.2, Task 16 |
| `TrackLens.log(...)` per call, p99 | < 100 µs | Task 16 |
| Normalizing 10 parameters | ≤ 5 µs | Task 12 |

## Current baseline

**Device:** vivo V2437, Android 16 (API 36), 8 cores at up to 2.5 GHz; androidx.benchmark reported clocks as locked. **Date:** 2026-11-25. **Code:** after Task 14 (pipeline + redaction). Raw files: [`benchmark/baselines/vivo-V2437-android16/`](../benchmark/baselines/vivo-V2437-android16/).

### Per call, median (`LogOverheadBenchmark`, androidx.benchmark)

| Call | Median | Allocations |
|---|---|---|
| `log(name, 10 params)` while disabled (`TrackLens.isEnabled = false`) | **8 ns** | 0 |
| `log(name)` | 0.43 µs | 3 |
| `log(name, 10-param Map)` | 1.62 µs | 18 |
| `log(name, 10-param Bundle)` | 1.62 µs | 18 |
| `wrap(...).logEvent(name, 10 params)` (no-op SDK) | 1.54 µs | 18 |
| Normalization only, 10-param Map (`NormalizationBenchmark`) | 0.75 µs | 15 |
| Normalization only, 10-param Bundle | 1.55 µs | 15 |

### Per-call distribution (`LogLatencyDistributionTest`)

200,000 calls of `log(name, 10-param Map)`, each timed individually after 50,000 warm-up calls:

| p50 | p90 | p99 | p99.9 | Max |
|---|---|---|---|---|
| 0.92 µs | 1.46 µs | **4.6 µs** | 35 µs | 2.4 ms |

Both targets are met with a wide margin. The single 2.4 ms call (1 in 200,000) is consistent with a garbage-collection pause landing in a measured call; the p99.9 shows such stalls are rare.

**Why two methods:** androidx.benchmark reports averages over many iterations per run, which hides tails. The distribution test times every call, so stalls show up. Its median is a little lower because it has no per-iteration bookkeeping.

## How the measurements avoid false results

- **No dropped events:** after every 1,000 calls, with timing paused, a listener confirms the pipeline delivered every logged event. If the queue had ever been full, calls would take the cheaper drop path and the numbers would look better than reality. The wait would then never finish, and the benchmark fails instead.
- **Release build, real device:** benchmarks run against a non-debuggable build (`testBuildType = "release"`) on hardware, never an emulator.
- **Full pipeline:** measurements go through the public API with TrackLens installed: guard, kill switch, normalization, queue. The consumer thread (redaction, ring buffer, listeners) runs concurrently, as in an app.

## Running

```
./gradlew :benchmark:connectedAndroidTest
```

- A physical device must be connected and unlocked.
- **Leave it alone while it runs.** androidx.benchmark measures only while its `IsolationActivity` is in front, and a run stalled for 20+ minutes when the phone went to its home screen. If that happens, run `adb shell am force-stop io.github.tracklens.benchmark.test` and start again.
- Results: `benchmark/build/outputs/connected_android_test_additional_output/releaseAndroidTest/connected/<device>/`. It holds `*benchmarkData.json` (androidx.benchmark), `tracklens-log-latency.json` (distribution) and Perfetto/method traces for investigating regressions.
- `LogLatencyDistributionTest` fails if p50 ≥ 10 µs or p99 ≥ 100 µs; the androidx benchmarks fail if any event was dropped.

## Updating the baseline

Re-run after changes on the caller's path (facade, guard, normalization, pipeline `offer`), and before each release.

1. Copy the two JSON files into `benchmark/baselines/<device>/`.
2. Update the tables above.
3. Note in the CHANGELOG any change over 20% on the same device.

The plan's nightly comparison job (`benchmark.yml`, §9.3) automates this later; CI has no device, so it runs the benchmarks in dry-run mode only.
