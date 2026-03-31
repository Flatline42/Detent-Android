# DETENT

A native Android shot logger for film photographers.

**Core goal:** Log a frame one-handed, without unlocking your phone, in under 5 seconds from the Quick Screen or under 3 seconds from the home screen widget.

---

## What it is

DETENT is a field shot log for 35mm film photography. It tracks exposure data — aperture, shutter speed, lens, filters, exposure compensation, GPS coordinates, and notes — per frame, per roll. It is not a light meter, not a development log, not a social app.

The dominant usage pattern is **continuation with small delta**: most frames share settings with the previous frame, and only one or two values change per shot. The app is built around this pattern. All other functionality is subordinate to it.

**Offline-first.** No accounts, no cloud sync, no analytics, no ads. All data lives on your device in a local SQLite database.

---

## Features

- Home screen widget (3×5) with horizontal aperture and shutter speed scroll wheels and one-tap frame logging
- Quick Screen with horizontal scroll wheels for aperture, shutter speed, and exposure compensation
- Per-roll gear configuration — camera body, lenses, filters pulled from your gear library
- Kit system — save named gear setups for fast roll creation
- Push/pull actuator with live ISO calculation, custom ISO override
- GPS coordinate capture on frame log (optional, per-roll toggle)
- Speech-to-text note entry
- Roll journal with full frame editing and retroactive correction
- Export to CSV, JSON, and plain text via Android share sheet
- Full database backup and restore (.DETENT file)
- Two themes: Alpine (black + Safety Gold) and Golden Hour (dark + Amber)
- Custom OFL-licensed fonts: JetBrains Mono, Space Grotesk, Share Tech Mono
- Haptic feedback on all stepper and scroll wheel interactions
- Shutter click sound on frame log (respects silent/vibrate mode)

---

## Screenshots

<!-- TODO: add screenshots -->

---

## Installation

### Release APK

Download the latest APK from the [Releases](https://github.com/Flatline42/FrameLog-Android/releases) page. Enable "Install from unknown sources" on your device and install directly.

### Build from source

Requirements:
- Android Studio Hedgehog or later
- JDK 11
- Android SDK — Min SDK 26, Target SDK 36

```bash
git clone https://github.com/Flatline42/FrameLog-Android.git
cd FrameLog-Android
./gradlew assembleDebug
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

For a release build you will need to supply your own signing keystore.

---

## Tech stack

| Concern | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Database | Room / SQLite |
| Navigation | Navigation Compose 2.8.0 (type-safe routes) |
| Widget | Glance AppWidget 1.1.0 |
| GPS | FusedLocationProviderClient |
| Export | Android Share Intent + FileProvider |
| Backup | SQLite file copy as `.DETENT` |
| Preferences | SharedPreferences |
| Haptics | VibrationEffect.createWaveform |
| Speech | Android SpeechRecognizer |

**Package:** `com.southsouthwest.framelog`
**Min SDK:** 26 / **Target SDK:** 36 / **Kotlin:** 2.2.10

---

## Data model

12 entities: CameraBody, Lens, Filter, FilmStock, Kit, KitLens, KitFilter, Roll, RollLens, RollFilter, Frame, FrameFilter.

Key constraints:
- `frameNumber` is immutable after roll creation
- `totalExposures` is locked at roll creation
- Mount type vocabulary is user-defined — Lens is the source of truth
- Push/pull is stored as an integer (-3 to +3); ratedISO is derived
- Half-frame bodies double totalExposures automatically

---

## Widget notes

The home screen widget uses Android's RemoteViews/Glance system. This means:
- **No swipe gestures** — Android launchers intercept swipe input before it reaches widgets. Aperture and shutter use tap +/− buttons on the widget.
- **No GPS logging from the widget** — Glance's ActionCallback context does not have reliable location access. Log GPS-tagged frames from the Quick Screen.
- **No haptics on the widget** — same context limitation.

Add the 4×2 or larger widget from your launcher's widget picker. A setup guide is in the app under Settings → Widget.

---

## Limitations and known scope

**v1.0 scope — not implemented:**
- Medium format (enum value reserved)
- Zoom lenses (single focal length only)
- Stepless aperture / cine lenses
- Google Sheets sync
- ExifTool command file export
- Per-frame ISO override
- In-app map rendering (tapping GPS coordinates opens your maps app via geo: intent)

**Widget limitations** are platform-level Android constraints, not implementation choices.

---

## License

MIT License. See [LICENSE](LICENSE).

### Font licenses

This app bundles three fonts licensed under the SIL Open Font License 1.1. Full license text and credits are in `assets/licenses/`. See Settings → About → Font licenses in the app.

- **JetBrains Mono** — Copyright © 2020 JetBrains s.r.o.
- **Space Grotesk** — Copyright © 2020 Florian Karsten
- **Share Tech Mono** — Copyright © 2012 Carrois Corporate Design

### Open source dependencies

See Settings → About → Open source licenses in the app (release builds only — requires the OSS Licenses Gradle plugin which only generates data on release builds with AGP 9+).

### Shutter sound

`res/raw/shutter_click.ogg` from Pixabay, used under the [Pixabay Content License](https://pixabay.com/service/license-summary/).

---

## Author

Built by [Flatline42](https://github.com/Flatline42) — a film photographer who wanted a faster way to log frames in the field.
