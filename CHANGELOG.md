# Changelog

All notable changes to this project will be documented in this file.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
Versions follow [Semantic Versioning](https://semver.org/).

## [1.1.1] - 2026-05-10

### Added
- Android Auto car screen now also exercises the failure path in demo mode (~30% of opens, same demo error reasons as the phone) — both branches are visible in the car UI without needing a real ESP32
- README: "What it looks like in the car" section with the three AA states (idle / sending / opened) captured from the Desktop Head Unit
- README: "What it looks like on the phone" section with four phone screenshots — main idle, main sending (with pulses), pairing scan (with radar), settings
- `docs/android-auto-ux-ideas.md`: parking-lot doc for a possible future AA UX redesign (auto-trigger on launch, spinner pane, result pane). Noted as not-implemented and the why
- `scripts/`: Python generators + outputs for the Play Store launcher icon (512×512) and feature graphic (1024×500) — white G mark on dark bg, supersampled and downsampled with LANCZOS for crisp edges

### Changed
- Demo-mode open simulation consolidated into a single `DemoOpener` helper used by both the phone activity and the AA car screen, so failure rate / timing / error messages stay in sync across surfaces
- AA car screen demo timing aligned with the phone (1.2s instead of 1.5s)

### Fixed
- Status bar icons on the phone forced to light style so they stay visible against the dark app background regardless of the system's light/dark theme. Previously dark icons rendered on dark bg and were invisible

## [1.1.0] - 2026-05-10

### Added
- Phone app: full UI rewrite in Jetpack Compose. New onboarding (Welcome → Set password → Scan/pair), redesigned main screen with hero open button, redesigned settings screen with paired-device hero card and grouped rows
- Main screen `Sending` state animates with three concentric expanding pulses radiating from the hero button
- Main screen `Opened` state: ring fills green with a check glyph, settles back to idle after 2s
- Main screen `Failed` state: ring fills pastel red with an X glyph, settles back to idle after 2s — same celebration cadence as `Opened`, so failures are unmistakable in a one-second peripheral glance
- Demo mode now randomly fails on ~30% of opens with one of three demo error reasons, so both success and failure animations are exercised without an ESP32
- "Last opened" timestamp persisted on each successful open and shown on the main screen
- Pairing flow: real BLE scan with a five-ring radar visual, scrollable list of found devices in the bottom sheet, tap-to-pair (no 6-digit code — the ESP32 doesn't have a display)
- Release signing: `app/build.gradle.kts` now reads keystore credentials from a gitignored `android/keystore.properties` (in addition to env vars and `~/.gradle/gradle.properties`), so `./gradlew assembleRelease` works in any fresh shell without exporting env vars

### Changed
- Phone app theme is now dark with a single warm-green accent, matching the launcher icon's color story
- Launcher icon: redesigned as a white "G" donut + tongue mark on the app's near-black background, replacing the previous teal-on-green-with-orange-badge icon. Adaptive icon foreground sized for the safe zone so it isn't clipped on circle/squircle masks
- "PIN" terminology in the UI is now "password" everywhere (the underlying storage was always a free-form string; this just aligns the wording with reality)
- Settings: "Demo mode" moved into a Testing section with a clear toggle and description; "Unpair" moved into a Danger zone section

### Removed
- Standalone `SettingsActivity` and its XML layout — settings are now an in-app route inside the Compose host activity
- AppCompat dependency (the rewrite uses `ComponentActivity` + Compose Material3)

## [1.0.11] - 2026-05-09

### Added
- Firmware: new `MODE_CAP_PULSE` trigger mode, alongside the existing `MODE_TRANSISTOR` and `MODE_RELAY`. Drives a small conductive pad (copper tape) taped over the capacitive top button of an Adaprox / Tuya Fingerbot Plus to fake a finger touch — the fingerbot then mechanically presses the fob. Renter-friendly: no soldering, no fob modification, fully reversible.
- Wiring guide: full Option C section with diagram, materials list, assembly steps, tuning notes, and trade-offs vs. wired options.
- README: rewritten "Choose your trigger mechanism" section with a comparison table and detailed explanation of why the capacitive-pulse trick works.

### Changed
- Firmware: `RELAY_ACTIVE_LOW` config flag replaced by `TRIGGER_MODE` (`MODE_TRANSISTOR` / `MODE_RELAY` / `MODE_CAP_PULSE`). `RELAY_PIN` kept as a legacy alias for the new `TRIGGER_PIN`. Existing transistor / relay setups behave identically.

## [1.0.10] - 2026-05-09

### Changed
- Launcher icon is now an adaptive icon (foreground + background layers), so the launcher and Android Auto can mask it into a circle / squircle properly instead of showing a square inside a circle frame

### Added
- Documentation for testing the AA flow on a PC via the Desktop Head Unit emulator (`docs/android-auto-debugging.md`)

## [1.0.9] - 2026-05-09

### Fixed
- Android Auto "unexpected error" on launch: `androidx.car.app.minCarApiLevel` meta-data must be on the `<application>` tag, not inside the `<service>` tag. The Car App Library reads it from the application context, and its absence caused `IllegalArgumentException` in `AppInfo.retrieveMinCarAppApiLevel`

## [1.0.8] - 2026-05-09

### Fixed
- Android Auto "unexpected error" on launch: removed `Toast` calls from the Car App context (not permitted by the AA framework) and fixed thread dispatch in demo mode

## [1.0.7] - 2026-05-09

### Fixed
- Android Auto launcher now lists the app: added the `<queries>` block (`androidx.car.app.CarAppService` intent + `androidx.car.app.connection` provider authority) required on Android 11+ for the Car App Library to communicate with the AA host
- Phone UI no longer draws underneath the camera punch-hole / status bar on Android 15 edge-to-edge displays (added `fitsSystemWindows` to phone and settings layouts)

## [1.0.6] - 2026-05-09

### Changed
- Package renamed: `com.garage.opener` → `com.dunnowsoftware.GarageAAtoESP32`
- CI now builds and signs a **release** APK with a proper keystore (no longer debuggable)
- Required for the app to appear in real Android Auto head units (production cars filter out debuggable APKs)

### Fixed
- Phone UI no longer draws underneath the camera punch-hole / status bar on Android 15 edge-to-edge displays (added `fitsSystemWindows` to phone and settings layouts)

### Notes
- Existing installs will need to be uninstalled before installing 1.0.6 (different package name and signature)

## [1.0.5] - 2026-05-09

### Changed
- CI now builds with JDK 21 (Temurin) to match AGP 8.13.1 requirements

## [1.0.4] - 2026-05-09

### Fixed
- Android Auto app visibility: restore `com.google.android.gms.car.application` meta-data on application tag (required alongside service declaration)
- Bump Car App Library to 1.7.0-rc01 (fixes AA launcher discovery and Android 14+ permission dialogs)

### Notes
- Sideloaded APKs must be installed via `adb install -i com.android.vending app.apk` to appear in the Android Auto launcher

## [1.0.3] - 2026-05-09

### Fixed
- Android Auto: app now appears in the AA launcher
  - Removed `android:exported="false"` from `<application>` tag which was blocking AA discovery
  - Removed incorrect `com.google.android.gms.car.application` meta-data
  - Added `android:label` and `androidx.car.app.minCarApiLevel` meta-data to the service (correct Car App Library pattern)

## [1.0.2] - 2026-05-09

### Added
- Open Garage button on the phone screen — no longer just a settings launcher. Works in both real mode (triggers BLE) and demo mode (simulated flow with toasts). Button is disabled until the app is configured or demo mode is on.

## [1.0.1] - 2026-05-09

### Added
- Demo mode: toggle in phone Settings to simulate a garage open without any ESP32 or BLE — runs the full AA UI flow (connecting → opened) and fires a toast on the phone. Useful for testing the Android Auto screen with the Desktop Head Unit.
- Flash script (`firmware/flash.bat` / `flash.ps1`): prompts for PIN, compiles and flashes via PlatformIO, then immediately restores the placeholder PIN so it is never stored on disk.
- Launcher icon (vector drawable).

### Changed
- Connection retry: app now silently retries up to 3 times (2 s apart) before showing failure — handles the ESP32 deep sleep window transparently.
- Deep sleep reduced from 10 s to 5 s for faster worst-case response.
- Power options updated: wall outlet + USB charger added as simplest option; solar power bank promoted above plain power bank.
- Wiring diagram: transistor option documented as Option A (recommended); relay as Option B.
- Provisioning guide expanded: PlatformIO install instructions, flash script usage, COM port detection.

### Fixed
- `gradle.properties`: added `android.useAndroidX=true` and `android.suppressUnsupportedCompileSdk=35` required for successful Gradle build.
- Added missing `gradle-wrapper.jar` and wrapper scripts so CI can build without a pre-installed Gradle.
- Added missing launcher icon that was causing `processDebugResources` to fail in CI.

## [1.0.0] - 2026-05-09

### Added
- ESP32 firmware: BLE service with nonce/command/status characteristics
- HMAC-SHA256 challenge-response authentication (PIN never sent over air)
- Relay and transistor wiring support (configurable via `RELAY_ACTIVE_LOW` in `config.h`)
- Deep sleep between advertising windows for power efficiency
- Android Auto app: single "Open Garage" button on car screen
- One-time phone setup via SettingsActivity (PIN entry + BLE device scan)
- Automatic retry on connection failure (up to 3 attempts, 2 s apart)
- Wiring diagram, power budget, and provisioning guide
- CC BY-NC-SA 4.0 license
