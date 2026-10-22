# PacingLab

Training analysis for runners who record with a Fitbit watch. PacingLab reads workouts, heart rate,
HRV, sleep and related data from Health Connect on the phone and turns them into the kind of analysis
you get from Garmin Connect, intervals.icu or Runalyze: your own HRmax and LTHR, intensity zones,
training load, fitness and fatigue, interval breakdowns, route maps and recovery trends.

All data stays on the device. There is no account, no server and no analytics.

## Status

Early development, used daily by its author. What works today:

- Sync of the full Health Connect history into a local database, with change tracking
- Workouts grouped into training sessions (a GPS warm-up plus an interval workout become one),
  with duplicates from other apps removed
- Interval sessions split into reps and recoveries from the watch's speed data, with pace, distance
  and heart rate for each
- Heart rate zones from your own maximum, threshold and resting heart rate, kept with the dates they
  apply from, and time in zones per session; left empty, the maximum is estimated from your workouts
  and the resting heart rate measured during sleep
- Route maps from OpenStreetMap data (OpenFreeMap tiles, no account needed)
- Training load (Banister TRIMP, a stress score where an hour at threshold scores 100, Edwards
  TRIMP), fitness, fatigue and form, and weekly training with intensity distribution
- Nightly HRV, resting heart rate, breathing rate and sleep, and a readiness score that compares
  them with your own baselines
- A sleep score built on the dimensions of sleep health (duration, continuity, regularity, timing)
  and a sleep screen with stages, overnight values, schedule and trends

Planned: VO2max trend, heart rate charts, threshold heart rate from your data.

The [project site](https://andremacedo88.github.io/PacingLab/) has the
[privacy policy](https://andremacedo88.github.io/PacingLab/privacy.html).

## Requirements

- Linux (the toolchain scripts target x86_64 Linux)
- About 8 GB of disk for the JDK, Android SDK and emulator image
- An Android 14+ phone with Health Connect, or the bundled emulator

## Getting started

The toolchain (JDK 21, Android SDK, emulator) lives in `.toolchain/` inside the repository and is
ignored by git. Install it once:

```sh
tools/bootstrap.sh --with-emulator   # omit the flag to skip the emulator (about 3 GB)
source tools/env.sh                  # puts java, adb and the emulator on PATH
./gradlew assembleDebug
```

Gradle keeps its download cache in `~/.gradle`, and adb stores its small pairing key in
`~/.android`. Deleting `.toolchain/`, `~/.gradle/` and `~/.android/` removes the whole setup.

### VS Code

Open the folder in VS Code and install the recommended extensions. The integrated terminal and the
tasks (Terminal > Run Task) pick up the toolchain automatically, so `source tools/env.sh` is not
needed there.

### Running on a phone

Enable developer options and USB or wireless debugging on the phone, connect it, then:

```sh
./gradlew installDebug
```

Debug builds install as "PacingLab Dev" (`app.pacinglab.debug`) and can live next to release builds.

## Common commands

| Command | What it does |
| --- | --- |
| `./gradlew test` | Unit, Robolectric and screenshot tests |
| `./gradlew verifyRoborazziDebug` | Compare screens against the committed reference screenshots |
| `./gradlew recordRoborazziDebug` | Re-record reference screenshots after an intended UI change |
| `./gradlew spotlessApply` | Format Kotlin, Gradle and text files |
| `./gradlew check` | Formatting check, tests and Android Lint |
| `./gradlew installDebug` | Build and install the debug app on a connected device |

## Project structure

```
app/                  Application, navigation, app-level wiring
build-logic/          Gradle convention plugins shared by all modules
core/model/           Domain types (plain Kotlin, no Android)
core/analytics/       Training science: zones, load, intervals, HRV (plain Kotlin, no Android)
core/designsystem/    Theme, icons and shared UI components
core/healthconnect/   Health Connect access and the data probe
core/testing/         Shared test utilities
feature/settings/     Settings and data source screens
tools/                Toolchain bootstrap and environment scripts
```

The analytics module has no Android dependencies, so every calculation is tested on the plain JVM in
seconds. Screens are tested with Robolectric, and their screenshots are rendered on the JVM with
Roborazzi and compared on every run.

## Contributing

Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/).
Run `./gradlew spotlessApply check` before committing.

## Licence

PacingLab is released under the [Apache License 2.0](LICENSE). The bundled Material Symbols are
also under Apache 2.0; map data comes from OpenStreetMap via OpenFreeMap and is credited on every map.
