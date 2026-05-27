# Changelog

All notable changes to this project will be documented in this file.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
Versions follow [Semantic Versioning](https://semver.org/).

## [1.4.2]

### Changed
- Geofence auto-open now requires Android Auto to be actively connected (via `CarConnection`) instead of checking a 60-second grace window from the last AA session stamp — correctly handles long drives where AA was connected from the start
- Speed gate (> 3 m/s) removed — AA connection is the authoritative signal that the user is in a vehicle
- Geofence EXIT events are now requested from the OS and logged with speed, AA connection state, and time since last auto-fire
- ENTER log now includes a full context summary (speed, AA connected, last auto-fire) before gate evaluation, so all information is visible even when Gate 1 fails
- Geofence result notification now appears as a heads-up banner on the Android Auto head unit display and auto-dismisses after 5 seconds

### Fixed
- Geofence auto-open was broken for drives longer than 60 seconds — the old timestamp-based AA check would always fail once the grace window elapsed

## [1.4.1]

### Fixed
- Geofence transition log now includes trigger location coordinates, accuracy, and speed for easier field debugging
- Settings screen scrolls correctly (LazyColumn conversion completed)
- Map picker: zoom-to-circle on open; user location zoom tightened when no pin set
- Map picker: zoom/pan buttons removed; pinch-to-zoom clipped correctly via `clipToOutline`
- Removed duplicate chevron from "location set" string in all 8 languages

## [1.4.0]

### Added
- Geofence auto-open: the garage opens automatically when the phone crosses the configured geofence radius while Android Auto is connected and the vehicle is moving (> 3 m/s)
- Settings → Auto-open section: tap "Garage location" to set a pin on an OSM dark map and drag a radius slider (50–200 m); toggle enables/disables auto-open independently of the saved location
- `GeofenceBroadcastReceiver` gates four conditions before firing: AA connected within 60 s, ENTER transition, speed > 3 m/s, 10 s debounce since last successful auto-open
- `GeofenceForegroundService` handles the BLE work with an elevated 8-attempt retry budget and posts a result notification ("Garage opened" / "Couldn't open garage")
- Geofences re-register after reboot via `BOOT_COMPLETED` receiver
- Cross-process debounce: `lastAutoFiredAt` in `EncryptedSharedPreferences` is written by both the foreground service and `GarageScreen`, so a geofence fire and a BLE-in-range auto-fire cannot double-trigger
- Background location permission request flow (coarse → fine → background, three separate prompts as required by Android 10+)
- New strings in all 8 languages (en, es, ca, de, fr, fi, it, pt-PT)

## [1.3.7] - 2026-05-25

### Added
- Linux flash tool (`firmware/flash.sh`) — mirrors `flash.ps1` step for step: PlatformIO auto-install via official installer script, port detection via `/sys/bus/usb` VID/PID matching with `/dev/ttyUSB*`/`/dev/ttyACM*` fallback, same board picker, trigger picker, PIN prompt (silent input, never written to disk), advanced options, and Play Store QR on success
- LED indicator on trigger — the board's built-in LED flashes once per garage open and is off the rest of the time, eliminating continuous LED drain on active-low boards (Lolin32 Lite, Lolin32)

### Changed
- Lolin32 Lite default trigger pin changed from GPIO 22 (shared with built-in LED) to GPIO 26; GPIO 22 is now driven HIGH at boot to keep the LED off between triggers

### Fixed
- Deep sleep crash on wake — redundant `stopAdvertising()` call before `NimBLEDevice::deinit()` left a stale NimBLE host task that caused a crash on the next wake cycle

## [1.3.6] - 2026-05-23

### Added
- Flash tool now asks which board you have and picks the right PlatformIO env automatically — supports ESP32-C3-DevKitM-1, Wemos Lolin32 Lite, ESP32-DevKitC, Wemos Lolin32, ESP32-S3-DevKitC-1, and NodeMCU ESP32-S
- Flash tool advanced options now include trigger GPIO — shown with the correct default for the selected board
- Pre-configured build environments for all supported boards in `platformio.ini` — `pio run -e <env>` just works
- `config.h` overridable defines (`TRIGGER_MODE`, `TRIGGER_PIN`, `DEVICE_NAME`, `USER_PIN`) now use `#ifndef` guards so per-board values can be injected via build flags without editing the file
- Flash tool no longer patches `config.h` — settings are injected via compiler flags, so the file is never modified and there is nothing to restore if the tool is killed mid-run

### Changed
- Default BLE device name changed from `GarageOpener` to `Garage-Opener`
- No pre-built firmware binary in releases — the PIN must be yours, so the flash tool is the only path. Download `GarageAAtoESP32-flash-tool.zip` from the release and run `flash.bat`

## [1.3.5] - 2026-05-19

### Added
- Flash tool now prompts for sleep duration, pulse duration, and BLE device name as optional advanced step — press Enter to keep defaults
- Sleep duration: 1–10 s range with guidance on responsiveness trade-off
- Pulse duration: 100–2000 ms range with guidance for snappy vs. slow fobs
- BLE device name: up to 20 characters, useful for multi-unit installs

### Fixed
- Power budget corrected to match actual `SLEEP_DURATION_S = 5` (was calculated with 10 s): daily draw updated from ~17 mAh to ~19 mAh, battery runtimes and solar margin updated throughout docs

## [1.3.4] - 2026-05-17

### Added
- Flash tool (`firmware/flash.bat`) now installs PlatformIO automatically via winget if not present — no manual toolchain setup required
- Flash tool auto-detects the ESP32 COM port by USB VID/PID — no need to know which port the board is on
- Flash tool prompts for trigger mechanism (transistor / relay / capacitive pad) and bakes the choice into the firmware at flash time
- Flash tool asks for PIN confirmation to catch typos before flashing
- Flash tool shows a QR code on success linking to the Play Store
- Flash tool patches `config.h` defensively on startup to recover from any previous hard-killed run
- GitHub releases now include a `GarageAAtoESP32-flash-tool.zip` with the flash scripts — no repo clone needed to flash

### Changed
- README reordered: leads with the experience and screenshots before technical details
- Builder-level docs (alternative boards, replication) moved to `docs/advanced.md`
- Quick start updated to reflect the one-step flash tool and onboarding flow

## [1.3.3] - 2026-05-15

### Added
- Launcher long-press shortcut: long-press the app icon on the home screen to get an "Open garage" action that launches the app and triggers the opener immediately, without navigating any screen

## [1.3.2] - 2026-05-15

### Added
- Haptic feedback on all interactive elements throughout the app: device list rows, skip link, settings gear icon, language picker rows, demo mode toggle, repair/unpair/pair buttons, and password show/hide toggles

## [1.3.1] - 2026-05-15

### Fixed
- In-app language list now correctly highlights the language set via Android's system App Info → Language screen, instead of always showing "System default" when the language was changed outside the app

## [1.3.0] - 2026-05-15

### Added
- Voice command support via Google Assistant / Gemini: say "Open the garage" through the car mic or AA screen mic button to trigger the opener without touching the screen. Implemented as an App Action (`OPEN_APP_FEATURE` BII) — Google routes the matched intent directly to the running AA service, which fires the BLE open immediately. No cloud dependency beyond the voice trigger itself; the actual open command is fully local BLE as always. Phrase matching works in all 8 supported languages.

### Changed
- CI firmware build target updated to `esp32c3` to match the default board (was `esp32`, which no longer exists as a named environment in `platformio.ini`)

## [1.2.3] - 2026-05-15

### Added
- Quick Settings tile — add the app to the notification shade for one-swipe access to open the garage without unlocking the phone or opening the app. Add it via long-press on the shade → Edit tiles. The tile subtitle shows the current state: Sending… → Opened / Failed, then clears after 2 seconds
- Haptic feedback on the phone open button: short tick when the command is sent, solid bump on success, double tap on failure

### Fixed
- Android Auto "Try Again" button on the failure screen now immediately retries the BLE connection instead of returning to the idle screen
- Edge swipe (predictive back gesture) now navigates back within the app instead of exiting — works from Settings, Change Password, and all other secondary screens

## [1.2.2] - 2026-05-11

### Added
- Android Auto screen auto-fires the open command the first time the paired device comes into BLE range while the AA screen is active — no tap needed on arrival. Fires once per AA session; resets when the screen goes to background so the next session is fresh
- Settings paired-device card shows presence inline: dot and badge turn green and read "PAIRED · In range" when the opener is detected nearby, grey otherwise

### Changed
- Presence scan callback no longer sets `inRange` directly — the 1.5s runnable is the sole authority on the `inRange` state transition, which is what gates the auto-fire decision

### Fixed
- Presence scan on the main screen and settings now uses `SCAN_MODE_LOW_LATENCY` so the in-range indicator appears within ~1s of opening the screen instead of the 5–6s worst-case under `LOW_POWER`; acceptable battery cost given the scan only runs while the screen is foreground
- Radar dot in the scan screen now updates RSSI on every scan callback instead of freezing at the first reading — the dot moves inward/outward as you walk toward or away from the device

## [1.2.1] - 2026-05-11

### Fixed
- Android Auto car screen now respects the per-app language selected in Settings. The AA service runs in a separate process (`gearhead:car`) unaffected by `AppCompatDelegate.setApplicationLocales`; fixed by applying the saved locale directly to the `CarContext` resources configuration in `GarageSession.onCreateScreen` before `GarageScreen` is constructed
- AA screen strings translated into all 8 supported languages (was hardcoded English)

## [1.2.0] - 2026-05-11

### Added
- Full i18n: all user-visible strings extracted to `strings.xml` and translated into 8 languages — Spanish, French, German, Catalan, Finnish, Portuguese (Portugal), and Italian — in addition to English
- Language selector in Settings: tappable row shows the active language and opens a dedicated Language screen listing all supported languages plus "System default"
- Per-app language switching via `AppCompatDelegate.setApplicationLocales` (API 29+), independent of the system locale. Selected language persists across app restarts
- `res/xml/locales_config.xml` declared and referenced in the manifest so Android 13+ shows the language picker in App Info → Language
- "Pair another device" button below the paired-device card in Settings — opens a BLE scan that excludes the currently paired device, so replacing an opener doesn't show the old one in the list
- Paired-device card in Settings now contains the "Re-pair" and "Password" actions inline; unpair moved to a small `×` icon button in the top-right corner of the card
- App version displayed at the bottom of the Settings screen

### Changed
- Language section moved above the Testing (demo mode) section in Settings
- Version info moved from its own card to a subtle footer at the bottom of the Settings screen
- Scan screen accepts an `excludeAddress` parameter so "Pair another" correctly hides the already-paired device
- "Last opened" timestamp on the main screen is now fully localised — "Today" is translated and the time format uses the active app locale via `DateFormat.getTimeFormat`
- Paired device icon on the pairing screen replaced with a custom hairline chip SVG drawable (thinner strokes than the Material `Outlined.Memory` icon)

### Removed
- Standalone "About" section card — version is now shown as a plain footer

## [1.1.3] - 2026-05-10

### Added
- Presence indicator on both the phone main screen and the Android Auto car screen. A low-power BLE scan filtered to the paired device's MAC runs while the screen is foreground; the dot shows `● In range` when the opener has been heard within the last 15s, `○ Out of range` otherwise. Tells the user whether they're actually near their garage before they tap Open instead of always optimistically saying "Ready"
- AA car screen renders the presence indicator on the same line as the device name (`● In range - ESP32-Garage`) so it reads at a glance from the driver's seat

### Changed
- BLE presence scan uses `CALLBACK_TYPE_ALL_MATCHES` and a 15s staleness window so the indicator stays steady on a stably-advertising peer instead of flickering between scan callbacks
- `GarageScreen` (AA) now reads `DevicePreferences` fresh on every access and watches for re-pair events on its 1.5s presence ticker, restarting the scan with the new MAC. Previously, re-pairing on the phone left AA scanning for the old MAC until the user backed out and re-entered the AA screen

## [1.1.2] - 2026-05-10

### Fixed
- BLE manager now enforces a 5-second per-attempt deadline covering the entire connect → discover → read-nonce → write-command → status-notify chain. If the peer accepts the connection but never responds (or any of the GATT callbacks silently never fires), the attempt is treated as failed and falls into the existing 3-attempt retry path. After three timeouts, the user sees `"Timed out — no response from opener (tried 3 times)"` instead of the UI hanging on `Sending…` forever. Surfaces identically on the phone main screen and the Android Auto car screen
- Auth-failure message renamed from `"Auth failed — check PIN"` to `"Auth failed — check password"` to match the rest of the UI (the underlying credential has always been a free-form string, not a numeric PIN)

### Added
- `docs/testing-with-nrf-connect.md`: step-by-step guide for testing the full BLE flow end-to-end without an ESP32, using nRF Connect for Android on a second phone as a virtual peripheral. Covers UUIDs, GATT-server config, advertiser setup, and walking through the success / auth-failure / connection-failure / timeout paths

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
