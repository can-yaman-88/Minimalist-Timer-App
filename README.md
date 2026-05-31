# Focus Timer

Offline-first deep-work timer for Android. Pitch-black / sepia UI, multi-session
focus+break workflows, a stopwatch mode, immersive full-screen digits, a
foreground-service timer engine that keeps accurate time with the screen locked,
local Room storage of daily stats, and CSV/JSON export via the Storage Access
Framework. No network, no cloud, no analytics.

## Build & run

### Android Studio (recommended for local dev)
1. Open the project folder. Android Studio will sync and **generate the Gradle
   wrapper automatically** (the wrapper jar is not committed).
2. Run the `app` configuration on a device/emulator (minSdk 26, target 35).

### Command line
The wrapper jar isn't committed, so generate it once, then build:
```bash
gradle wrapper --gradle-version 8.9   # only needed the first time
./gradlew assembleDebug
```
The debug APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

### GitHub Actions
`.github/workflows/build.yml` runs on every push to `main` (and via manual
dispatch). It sets up JDK 17, provisions Gradle 8.9, generates the wrapper,
runs `./gradlew assembleDebug`, and uploads the APK with
`actions/upload-artifact@v4` under the name **focus-timer-debug-apk**. Download
it from the run's *Artifacts* section.

> If a run ever fails on a missing SDK platform, the GitHub-hosted runner image
> didn't preinstall `android-35`. Add a step before the build:
> `uses: android-actions/setup-android@v3` (and let it install
> `platforms;android-35`).

## Architecture (MVVM, clean boundaries)

```
UI (Compose)  ──observes──>  TimerViewModel / StatsViewModel  ──>  StatsRepository  ──>  Room
     │                                   │                                  ▲
     └──user intents──> TimerService (authoritative timer engine) ──persists┘
                          └─ exposes StateFlow<TimerUiState> (process-global)
```

- **UI** only observes state and emits intents; it never touches the DAO or the
  service directly.
- **Timer engine lives in a foreground `Service`** (not the ViewModel) because a
  ViewModel is cleared when the UI process scope ends. A started foreground
  service keeps the process alive and is exempt from background-execution limits,
  so the timer survives screen-lock, app-swipe, and Doze. A `PARTIAL_WAKE_LOCK`
  keeps the CPU ticking. Time is derived from `SystemClock.elapsedRealtime()` on
  every tick, so individual delayed ticks never cause drift.
- **State survival across rotation**: the authoritative `TimerUiState` is a
  process-global `StateFlow` on the service, and the setup form + immersive flag
  live in a retained `ViewModel` — both survive configuration changes.

### Skip-to-break (per spec)
Tapping the active screen opens the quick menu (Pause/Resume, **Skip to Break**,
Full Screen, **Finish**, **Stop**). "Skip to Break" computes the exact elapsed
focus time, writes a `FocusSession(durationMillis = <elapsed>, …)` row for today,
immediately starts the break, and auto-queues the next focus session when the
break ends. Example: skipping a 50m focus at 32m15s stores `1_935_000` ms.

## Theming
A locked-down Material 3 dark scheme maps every color slot to `#000000` /
`#E6C280` (with two muted sepias for borders and dividers), so no white or bright
element can leak through ripples, scrims, or disabled states.
