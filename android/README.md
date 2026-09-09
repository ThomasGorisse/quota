# Quota for Android

Compose app + Glance widget for the `/v1/status` contract in the [root README](../README.md).
Package `com.gorisse.thomas.quota`, minSdk 26, Material 3 with dynamic colour **off** — the
palette is the one in [`docs/DESIGN.md`](../docs/DESIGN.md) and severity has to mean the same
green, amber and red on every device.

## Build and run

```bash
./gradlew :app:assembleDebug
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
```

Versions are pinned in `gradle/libs.versions.toml`: Gradle 8.14.5, AGP 8.13.2, Kotlin 2.4.10,
Compose BOM 2026.06.01, compileSdk 36 — the set already known to work on this machine.

## Seeing it without a backend

The pairing screen carries a **Demo data** switch that only exists in debug builds
(`BuildConfig.DEMO_DATA_AVAILABLE`). It fills the repository — and therefore the widget — with
`DemoData`, whose numbers cover the three severities and whose reset times are relative to now.

The widget itself can be captured without dragging anything onto a launcher:

```bash
adb shell appwidget grantbind --package com.gorisse.thomas.quota --user 0
adb shell am start -n com.gorisse.thomas.quota/.debug.WidgetPreviewActivity
```

That debug activity binds the real provider into an `AppWidgetHost` at 2×1 and 4×2, so what it
shows is the actual RemoteViews Glance produces.

## Screenshots

| | |
|---|---|
| ![home light](docs/screenshots/home-light.png) | ![home dark](docs/screenshots/home-dark.png) |
| ![2x1](docs/screenshots/widget-2x1-dark.png) | ![4x2](docs/screenshots/widget-4x2-dark.png) |

## Shape of the code

| File | Why it exists |
|---|---|
| `data/Status.kt` | The wire contract, plus `remaining = 100 - percent` and the severity rule |
| `data/Pairing.kt` | Parses `https://<host>/#t=<token>`; stores it encrypted |
| `data/StatusRepository.kt` | The single source of truth for app **and** widget, cache included |
| `ui/RingGauge.kt` | The phone ring: Compose `drawArc`, 400 ms ease-out on value change only |
| `widget/RingBitmap.kt` | The widget ring: Glance has no arc primitive, so it is a Canvas bitmap |
| `widget/RefreshWorker.kt` | WorkManager, every 15 min while a widget is placed, plus on app open |

Signing and Play upload: see [`docs/release.md`](docs/release.md).
