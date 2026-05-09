# Changelog

All notable changes to this project will be documented in this file.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
Versions follow [Semantic Versioning](https://semver.org/).

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
