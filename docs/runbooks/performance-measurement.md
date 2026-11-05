# Performance Measurement Runbook

> Owner-run (`E4-03`, decisions `D-251` to `D-256`). It produces the cold-start, smoothness and `D-80`
> figures of `docs/versions-matrix.md` "Performance measurement baselines". The measured configuration is
> `staging` / `Staging` (`D-259`), through its `benchmark` / `Benchmark` derivatives (`D-252`, `D-253`):
> release code and settings against the development project. Release builds fail closed until `E4-10`
> (`D-238`) and are never used here. Nothing here handles a secret: the outputs contain only timings,
> counts and device names. iOS signing uses your untracked `iosApp/Local.xcconfig`
> (`docs/runbooks/ios-device-signing.md`).

## 0. What you will measure

| Figure | Android | iOS | Gate |
|--------|---------|-----|------|
| Cold start, returning user, to the populated vehicle list | §2 A6 | §3 I6 | median of at least 10 runs < 2 s (`D-265`) |
| Cold start, signed-out install, to the welcome screen (second figure) | §2 A3 | §3 I3 | recorded; median of at least 10 runs < 2 s (`D-265`) |
| Fling across 1,000 entries, frames over 32 ms | §2 A7 | §3 I7 | 0 |
| Consumption of 1,000 entries, median of 20 after 5 warm-ups (`D-80`) | CI (JVM) | §4 | < 100,000,000 ns |

A frame is over 32 ms when its lateness `L` satisfies `L > (floor(32 / T) - 1) * T`, with
`T = 1000 / refreshHz` ms. At 60 Hz (Pixel 6a, iPhone 12) that is any late frame.

## 1. Before you start

1. Use the reference devices: Pixel 6a on Android 16 (API 36) and iPhone 12 on iOS 26. If you use another
   device, run everything anyway and record its model, OS and refresh rate (`docs/versions-matrix.md`).
   An Android device that is not rooted needs Android 14 (API 34) or later. Below API 34 Macrobenchmark
   resets compilation by reinstalling the app, which deletes the seeded history before A6 and A7; below
   API 31 it reports no `frameOverrunMs`; and on API 28 it reported no `timeToFullDisplayMs` (dry run on a
   OnePlus 3 with Android 9 on 2026-11-03, O17 in `docs/handoffs/E4-B2.md`).
2. Open a terminal in a checkout of `story/E4-B2-release-readiness` at its head (the agent's worktree
   `.worktrees/carApp-E4-B2` is one; run `git pull` there first). Run every command below from that
   checkout's root. Create the output folder (ignored by git):
   `mkdir -p build/perf/<YYYY-MM-DD>/android build/perf/<YYYY-MM-DD>/ios`.
3. Charge each device above 50%, close every other app, fix the screen brightness, disable battery saver,
   and leave the device idle for 2 minutes before each measured block. On Android, keep the screen on for
   the whole session (Developer options, "Stay awake", with the device charging), because A5 waits up to 5
   minutes without a touch; turn it off again after A8.
4. **The benchmark builds reuse the development identity** (`com.ruizurraca.carapp.debug`). Installing one
   replaces the debug or staging app and its data on that device. If that app is signed in, open it, sign
   out or delete its local data first: on iOS a Firebase session survives an app reinstall.
5. **Run this after the `staging` device session (O15 and O16 of `docs/handoffs/E4-B2.md`) has passed.**
   The adoption steps A5 and I5 need the development App Check to accept the build on a real device, which
   that session proves first.
6. Keep the iPhone on a USB cable for every `xcodebuild` step (airplane mode disconnects wireless debugging).
   Record `xcodebuild -version` once; it is part of the iOS evidence.
7. Order matters: the signed-out figure (A3, I3) needs a fresh install; the returning-user figures (A6, I6)
   and the fling (A7, I7) need the seeded and adopted history (A4-A5, I4-I5).

## 2. Android

**A0 — Identify the device.**
```bash
adb devices -l
export ANDROID_SERIAL=<serial from the list>
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
adb shell dumpsys display | grep -m 1 -o 'fps=[0-9.]*'
```
Record model, Android version, API level and refresh rate.

**A1 — Fresh install of the benchmark build and the benchmark tests.**
```bash
adb uninstall com.ruizurraca.carapp.debug || true
adb uninstall com.ruizurraca.carapp.benchmark || true
./gradlew :androidApp:installBenchmark :benchmark:installBenchmark
adb shell pm list instrumentation | grep com.ruizurraca.carapp.benchmark
```
Expected: one line ending in `androidx.test.runner.AndroidJUnitRunner (target=com.ruizurraca.carapp.benchmark)`.

**A2 — Go offline.**
```bash
adb shell cmd connectivity airplane-mode enable
adb shell settings get global airplane_mode_on
```
Expected: `1`.

**A3 — Signed-out install to the welcome screen (second figure).** Run this before A4.
```bash
D=build/perf/<YYYY-MM-DD>/android
adb shell rm -rf /sdcard/Android/media/com.ruizurraca.carapp.benchmark
adb shell am instrument -w -r \
  -e class 'com.ruizurraca.carapp.benchmark.ColdStartBenchmark#signedOutColdStartToWelcomeScreen' \
  -e androidx.benchmark.output.enable true \
  com.ruizurraca.carapp.benchmark/androidx.test.runner.AndroidJUnitRunner | tee "$D/welcome.txt"
adb pull /sdcard/Android/media/com.ruizurraca.carapp.benchmark/ "$D/welcome/"
F=$(find "$D/welcome" -name '*benchmarkData.json' | head -n 1)
jq '.benchmarks[] | {name, ttfdMedianMs: .metrics.timeToFullDisplayMs.median, ttfdRunsMs: .metrics.timeToFullDisplayMs.runs, ttidMedianMs: .metrics.timeToInitialDisplayMs.median}' "$F"
```
Expected: `OK (1 test)` in `welcome.txt`, and a `jq` object with ten `ttfdRunsMs`. If the pulled folder is
empty, run `adb shell find /sdcard/Android/media -name '*benchmarkData.json'`, pull that path and report it.

**A4 — Seed 1,000 entries (still offline).**
```bash
adb logcat -c
adb shell am start -W -n com.ruizurraca.carapp.debug/com.ruizurraca.carapp.BenchmarkSeedActivity
adb logcat -s CarAppBenchmark:I -m 1
```
Expected: `I CarAppBenchmark: seed=ok vehicles=1 fuelEntries=1000`. `seed=refused reason=ONLINE`: airplane mode
is off, repeat A2. `seed=refused reason=NOT_LOCAL_OWNER`: repeat from A1.

**A5 — Adopt the history once (online).**
```bash
adb shell cmd connectivity airplane-mode disable
adb shell am start -W -n com.ruizurraca.carapp.debug/com.ruizurraca.carapp.MainActivity
```
On the phone, wait (up to 5 minutes, touching nothing) until the vehicle list shows "Benchmark vehicle" and
the backup status shows its idle state (the `backup_status_idle` string: "Synced locally" in English,
"Sincronizado localmente" in Spanish on 2026-10-15; use the current value of that string). If the welcome screen is still shown after 60 seconds online, stop and report: the adoption failed.
Then:
```bash
adb shell am force-stop com.ruizurraca.carapp.debug
adb shell cmd connectivity airplane-mode enable
```

**A6 — Returning user to the populated vehicle list (gate).**
```bash
adb shell rm -rf /sdcard/Android/media/com.ruizurraca.carapp.benchmark
adb shell am instrument -w -r \
  -e class 'com.ruizurraca.carapp.benchmark.ColdStartBenchmark#returningUserColdStartToPopulatedVehicleList' \
  -e androidx.benchmark.output.enable true \
  com.ruizurraca.carapp.benchmark/androidx.test.runner.AndroidJUnitRunner | tee "$D/returning.txt"
adb pull /sdcard/Android/media/com.ruizurraca.carapp.benchmark/ "$D/returning/"
F=$(find "$D/returning" -name '*benchmarkData.json' | head -n 1)
jq '.benchmarks[] | {name, ttfdMedianMs: .metrics.timeToFullDisplayMs.median, ttfdRunsMs: .metrics.timeToFullDisplayMs.runs, ttidMedianMs: .metrics.timeToInitialDisplayMs.median}' "$F"
```

**A7 — Fling across 1,000 entries.** Replace `60` by the refresh rate of A0.
```bash
adb shell rm -rf /sdcard/Android/media/com.ruizurraca.carapp.benchmark
adb shell am instrument -w -r \
  -e class 'com.ruizurraca.carapp.benchmark.ListScrollBenchmark#flingAcrossOneThousandFuelEntries' \
  -e androidx.benchmark.output.enable true \
  com.ruizurraca.carapp.benchmark/androidx.test.runner.AndroidJUnitRunner | tee "$D/fling.txt"
adb pull /sdcard/Android/media/com.ruizurraca.carapp.benchmark/ "$D/fling/"
F=$(find "$D/fling" -name '*benchmarkData.json' | head -n 1)
jq --argjson hz 60 '.benchmarks[]
  | (1000 / $hz) as $t
  | ((((32 / $t) | floor) - 1) * $t) as $threshold
  | {name,
     thresholdMs: $threshold,
     framesOver32Ms: ([.sampledMetrics.frameOverrunMs.runs | flatten | .[] | select(. > $threshold)] | length),
     framesMeasured: ([.sampledMetrics.frameOverrunMs.runs | flatten | .[]] | length),
     cpuFramesOver32Ms: ([.sampledMetrics.frameDurationCpuMs.runs | flatten | .[] | select(. > 32)] | length)}' "$F"
```
If `jq` prints `null` or an error, run `jq '.benchmarks[0] | keys, (.sampledMetrics | keys)' "$F"` and send
the output instead.

**A8 — Clean up.**
```bash
adb shell cmd connectivity airplane-mode disable
```
Open the app, Settings, "Delete local data", confirm, and wait for the welcome screen (this deletes the
anonymous account and its backup in the development project). Then:
```bash
adb uninstall com.ruizurraca.carapp.debug
adb uninstall com.ruizurraca.carapp.benchmark
```

Send back: the A0 values, the three `jq` outputs, the A4 log line, the A5 and A8 confirmations and the three
`*.txt` files.

## 3. iOS

**I0 — Identify the device.** `xcrun devicectl list devices`. Record the UDID, model, iOS version and the
refresh rate (60 Hz for iPhone 12; 120 Hz for ProMotion models). With the iPhone connected, open Device
Hub (Xcode 27: Xcode > Open Developer Tool > Device Hub, the successor of "Devices and Simulators"),
select the iPhone and keep the app open for the whole session. Then `xcrun xctrace list devices` must list
the iPhone under `== Devices ==`, not under `== Devices Offline ==`: while it is offline the launch metric
records no value and the launch tests still pass (`D-265`).

**I1 — Fresh install.** On the iPhone, if `carApp` (debug) is installed and signed in, open it, sign out or
delete local data. Then remove it: `xcrun devicectl device uninstall app --device <UDID> com.ruizurraca.carapp.debug`.

**I2 — Start every step online and let the test take it offline (`D-264`).** iOS 27 verifies every
developer-signed install online at its first launch, and each `xcodebuild … test` below reinstalls the
test runner and `carApp`, so a step started in airplane mode fails with `The application could not be
launched because the Developer App Certificate is not trusted`. Start each of I3, I4, I6 and I7 with
airplane mode off and Wi-Fi on, and keep the cable connected. In I3, I4 and I6 the test launches `carApp`
once, closes it, prints `PERF: turn airplane mode on and Wi-Fi off now` and waits up to 5 minutes. When
that line appears, or `carApp` has opened and closed once on the iPhone, turn airplane mode on and Wi-Fi
off in Control Center and leave the iPhone untouched: the test then waits the 2 idle minutes of §1 by
itself (I3, I6) and measures offline. After each step, turn airplane mode off and Wi-Fi on again. I7 never
asks for airplane mode: it stays online from start to end (`D-266`). If "Unable to Verify
App" appears, the iPhone was offline too early: tap Cancel, go online and repeat the step, because every
launch is refused while that alert is on screen.

**I3 — Signed-out install to the welcome screen (second figure).**
```bash
I=build/perf/<YYYY-MM-DD>/ios
xcodebuild -project iosApp/carApp.xcodeproj -scheme carAppPerformance -configuration Benchmark \
  -destination "id=<UDID>" -derivedDataPath iosApp/build/perf \
  -resultBundlePath "$I/welcome.xcresult" \
  -only-testing:carAppPerformanceUITests/LaunchPerformanceUITests/testSignedOutLaunchToWelcomeScreen \
  test | tee "$I/welcome.log"
grep -E 'measured \[' "$I/welcome.log"
grep -E 'measured \[' "$I/welcome.log" | sed -E 's/.*values: \[([^]]*)\].*/\1/' | tr ',' '\n' \
  | sort -n | awk '{v[NR]=$1} END {print "n=" NR, "median=" (v[int((NR+1)/2)] + v[int(NR/2)+1]) / 2}'
```
Expected: `** TEST SUCCEEDED **` and a `measured [Duration (AppLaunch), s]` line; the `awk` line prints `n=`,
the number of values the run recorded out of 25 iterations, and their median. The figure needs `n` of at
least 10 (`D-265`); with fewer, repeat the step and never pool runs. XCTest notes each lost iteration as
`Received unexpected number of metrics: 0 in iteration with index <n>` and still passes. No `measured`
line at all: check `xcrun xctrace list devices` (I0).
`The device never went offline (D-264)`: airplane mode was not turned on within 5 minutes; repeat the step.

**I4 — Seed 1,000 entries (online at the start, offline once the test asks, as in I2).**
```bash
xcodebuild -project iosApp/carApp.xcodeproj -scheme carAppPerformance -configuration Benchmark \
  -destination "id=<UDID>" -derivedDataPath iosApp/build/perf \
  -only-testing:carAppPerformanceUITests/BenchmarkSeedingUITests/testSeedsTheBenchmarkFuelHistoryWhileOffline \
  test | tee "$I/seed.log"
```
Expected: `** TEST SUCCEEDED **`. The verification launch passes no seed argument, so only the launch after
the iPhone is offline seeds. A failure naming `benchmark_seed_refused` with `ONLINE`: Wi-Fi or mobile data
was still on; repeat the step. With `NOT_LOCAL_OWNER`: repeat from I1.

**I5 — Adopt the history once (online).** Airplane mode off, Wi-Fi on. Open `carApp` from the home screen
and wait (up to 5 minutes) until the list shows "Benchmark vehicle" and the backup status shows its idle
state. If the welcome screen stays after 60 seconds, stop and report. Then force-quit the app (swipe it
away) and stay online: I6 starts online (I2).

**I6 — Returning user to the populated vehicle list (gate).** Same command as I3 with
`-only-testing:carAppPerformanceUITests/LaunchPerformanceUITests/testReturningUserLaunchToPopulatedVehicleList`,
result bundle `"$I/returning.xcresult"`, log `"$I/returning.log"`, and the same `grep`/`awk` lines on that log;
the figure needs `n` of at least 10, as in I3.
Start it online and go offline when the test asks (I2). Its verification launch runs online, so the app may
run one sync cycle before the iPhone goes offline; that launch is not measured.

**I7 — Fling across 1,000 entries (five runs, online from start to end, `D-266`).** Before the loop,
turn on Do Not Disturb in Control Center (Focus), leaving airplane mode off and Wi-Fi on: online, other
apps' notification banners otherwise cover the list during the measured flings. Turn it off after I7.
```bash
for n in 1 2 3 4 5; do
  xcodebuild -project iosApp/carApp.xcodeproj -scheme carAppPerformance -configuration Benchmark \
    -destination "id=<UDID>" -derivedDataPath iosApp/build/perf \
    -resultBundlePath "$I/fling-$n.xcresult" \
    -only-testing:carAppPerformanceUITests/ListScrollPerformanceUITests/testFlingAcrossOneThousandFuelEntries \
    test | tee "$I/fling-$n.log"
  grep -E 'measured \[(Number of Hitches|Clock Monotonic Time)' "$I/fling-$n.log"
  grep -q 'measured \[Number of Hitches' "$I/fling-$n.log" || break
done
```
On iOS 27 the hitch metric stops recording once about 60 full-list flings have accumulated in one run, so
each run measures one block of 30 flings toward the end of the list, after an unmeasured first invocation
that does not fling, and the loop repeats the run five times (`D-266`). Each run takes about 5 minutes: it
reinstalls, verifies the install, waits the 2 idle minutes and measures. Keep airplane mode off and the
iPhone unlocked and untouched for the whole loop; a run fails with `Stay online: the fling is measured
online (D-266)` if the iPhone went offline. Each run prints a `Number of Hitches (debug)` line and a
`Clock Monotonic Time` line, each with one value. The clock value is about 85 s; a value near 0 means the
recorded invocation did not fling: stop and report. The loop stops at a run with no hitch line, which the
runner explains with `Code=4097` for `MetricMeasurementHelper`: report it and run I7b. Send every printed
line verbatim. At 60 Hz the gate holds when each of the five hitch counts is `0`; a non-zero count is
checked for overlapping network work before it is reported as a failure. If the test was skipped (iOS
older than 26) or the device is not 60 Hz and a count is not zero, run I7b.

**I7b — Instruments "Animation Hitches" (only when I7 sends you here).** In Xcode (the version
`xcodebuild -version` printed) open `iosApp/carApp.xcodeproj`, choose the scheme `carAppPerformance` and the
iPhone, then Product > Profile
(it builds the `Benchmark` configuration). In the template chooser pick "Animation Hitches". On iOS 27 the
install needs one launch online first (`D-264`): press Record while online, press Stop as soon as the app
shows, turn airplane mode on and Wi-Fi off, wait 2 minutes, and press Record again.
On the iPhone open "Benchmark vehicle" and flick upward fast 30 times, until the end of the list. Press Stop.
Select the "Hitches" track; in the detail pane at the bottom show the list of hitches and sort it by
duration. Count the hitches whose duration is greater than `(floor(32 / T) - 1) * T` ms. Repeat 5 recordings.
Save each trace (File > Save As) to `build/perf/<YYYY-MM-DD>/ios/hitches-<n>.trace`. Send the five counts and
the exact name of the duration column.

**I8 — Clean up.** Airplane mode off. Open the app, Settings, "Delete local data", confirm, wait for the
welcome screen. Then `xcrun devicectl device uninstall app --device <UDID> com.ruizurraca.carapp.debug`
and `xcrun devicectl device uninstall app --device <UDID> com.ruizurraca.carapp.perfuitests.xctrunner`.

## 4. `D-80` consumption on the iPhone

**D1 — Build.** This builds only the `ConsumptionBenchmarkApp` target (no Firebase, no `carApp`), so the
fail-closed Release check of `D-238` does not apply to it.
```bash
xcodebuild -project iosApp/carApp.xcodeproj -scheme ConsumptionBenchmarkApp -configuration Release \
  -destination "id=<UDID>" -derivedDataPath iosApp/build/d80 build
```
**D2 — Install.**
`xcrun devicectl device install app --device <UDID> iosApp/build/d80/Build/Products/Release-iphoneos/ConsumptionBenchmarkApp.app`

**D3 — Run without a debugger.** `D-80` needs no network, but on iOS 27 the first launch after D2 does, to
verify the install (`D-264`): if the iPhone is in airplane mode, open the app once online first and close it.
Leave the iPhone idle for 2 minutes, then open `ConsumptionBenchmarkApp`
from the home screen. A spinner shows while the benchmark runs; then a number appears: the median in
nanoseconds of 20 measured runs after 5 warm-ups. Take a screenshot. Wait 30 seconds, tap the play button,
record the second number; repeat once more. The first number is the `D-80` figure; the other two show
repeatability.

**D4 — Record.** Model, iOS version, date, the three numbers. The figure passes when the first number is
below `100000000`.

**D5 — Clean up.** `xcrun devicectl device uninstall app --device <UDID> com.ruizurraca.carapp.consumptionbenchmark`.

## 5. What to send back

Everything listed at the end of §2, §3 and §4, pasted as text, plus `xcodebuild -version` and the
screenshot of §4. The agent records
the figures in `docs/handoffs/E4-03.md` and the `D-80` figure in a dated closure update of
`docs/handoffs/E1-05.md`; it never records a figure you did not send.
