package com.ruizurraca.carapp.benchmark

/** `E4-03` (`D-252`): the `:androidApp` `benchmark` variant's application ID (its `.debug` suffix). */
internal const val TARGET_PACKAGE = "com.ruizurraca.carapp.debug"

/** Mirrors `BENCHMARK_VEHICLE_NAME` in `:shared`; `PerformanceHarnessContractTest` pins the two. */
internal const val BENCHMARK_VEHICLE_NAME = "Benchmark vehicle"

/** `docs/versions-matrix.md`: the cold-start figure is the median of 10 runs. */
internal const val COLD_START_ITERATIONS = 10

/** Five scripted flings across the whole list. */
internal const val SCROLL_ITERATIONS = 5

/** Upper bound for content to appear; a timeout fails the iteration instead of measuring a spinner. */
internal const val CONTENT_TIMEOUT_MS = 30_000L

/** Safety bound for one top-to-end fling sequence over 1,000 rows. */
internal const val MAX_FLINGS_PER_ITERATION = 100

/** Keeps flings out of the system gesture areas. */
internal const val GESTURE_MARGIN_DIVISOR = 5
