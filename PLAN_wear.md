# Wear OS companion plan

Primary use case: motorbike rider, phone in pocket, gloves on. Swipe left on watch face → tap tile → door opens. No look required after muscle memory kicks in.

## Architecture

Watch never talks to ESP32 directly. All BLE lives on the phone.

```
Watch tile tap
  → MessageClient /garage/open
    → Phone WearMessageListenerService
      → GarageBleManager (existing, unchanged)
        → ESP32
      → MessageClient /garage/result SUCCESS|FAIL
        → Watch haptic (success) or notification (fail)

Phone GeofenceForegroundService SUCCESS
  → MessageClient /garage/autofired
    → Watch WearMessageListenerService
      → local notification "Opened automatically"
```

## Phases

### Phase 1 — Project scaffolding

- Add `:wear` to `android/settings.gradle.kts`
- Create `android/wear/build.gradle.kts`
  - `com.android.application` plugin
  - Same `namespace`, `compileSdk`, `minSdk` as `:app` except `minSdk = 26` (Wear OS 2+)
  - Wear Compose, Tiles, Wearable deps (see Dependencies section)
- Add new version + library entries to `libs.versions.toml`
- Add `play-services-wearable` to `:app` `build.gradle.kts`
- Create `wear/src/main/AndroidManifest.xml`
  - `uses-feature android.hardware.type.watch`
  - `com.google.android.wearable.standalone = false` (requires paired phone app)
  - `com.google.android.wearable.watchface.preview` not needed (not a watchface)

### Phase 2 — Phone message listener

New file: `android/app/src/main/.../wear/WearMessageListenerService.kt`

- Extends `WearableListenerService`
- Listens for `/garage/open`
- Reuses `GarageBleManager` + `DevicePreferences` — identical path to manual tap
- On result sends `/garage/result` with payload `SUCCESS` or `FAIL`
- Register in phone `AndroidManifest.xml` with `BIND_LISTENER` + `com.google.android.gms.wearable.MESSAGE_RECEIVED` intent filter

### Phase 3 — Watch full app

`WearActivity` + `WatchMainScreen` composable.

**UI:** Port from phone `MainScreen` verbatim for the core shapes:
- `GMark` — unchanged, pure Canvas
- `ExpandingPulses` — unchanged
- `CheckGlyph` / `CrossGlyph` — unchanged
- Gyro tilt (`rememberYaw`) — dropped, irrelevant on a watch
- Round-aware layout via `ScalingLazyColumn` or simple `Box` centered in the round canvas
- Same colors: `#0A0C0E` bg, `#2AD4A3` accent

**States:** `Idle → Sending → Opened | Failed → Idle`
- Sending: pulses animate, button not re-tappable
- Opened: check glyph + success haptic (VibratorManager `EFFECT_HEAVY_CLICK`), 2s then Idle
- Failed: cross glyph + error haptic (three short pulses), 2s then Idle
- Timeout: if no `/garage/result` within 10s → Failed

**No pairing UI.** If phone has no paired device, result is FAIL.

### Phase 4 — Tile (fire-and-forget with passive result feedback)

`GarageOpenerTileService` in `:wear`.

- Extends `TileService`
- No app launch — ever. MessageClient call fires directly from the tile action.

**Tile states (layout-based, no animation):**

| State | Layout |
|-------|--------|
| `Idle` | GMark icon + "OPEN" label in accent, button tappable |
| `Sending` | Spinner or dimmed GMark + "OPENING…" label, button disabled (prevents double-tap) |
| `Opened` | Checkmark + "OPENED" in accent, auto-resets to Idle after 3s |
| `Failed` | Cross + "FAILED" in danger color, auto-resets to Idle after 3s |

**State persistence:** tile state stored in `SharedPreferences` in `:wear` — `tileState` enum + `tileStateSetAt` timestamp. `onTileRequest` reads this to decide which layout to render. The 3s auto-reset is handled by checking `tileStateSetAt` age in `onTileRequest` — if Opened/Failed and age > 3s, render Idle instead and clear state.

**Flow:**
1. Tap → OS haptic immediately (free, no code needed)
2. Tile action fires `/garage/open` via `MessageClient`
3. Tile writes `Sending` to prefs + calls `requestUpdate()` → tile redraws (~300ms)
4. Phone responds `/garage/result SUCCESS|FAIL`
5. `WearMessageListenerService` writes result state to prefs + calls `TileService.getUpdater(context).requestUpdate(GarageOpenerTileService::class.java)`
6. Tile redraws to Opened/Failed
7. Next `onTileRequest` call (3s later via `TileService` freshness or explicit `postDelayed` requestUpdate) redraws to Idle

**Why no app launch:** launching an app from a tile adds ~1-2s and a visual context switch. For the motorbike use case that defeats the purpose. Tile layout swap is ~300ms — fast enough for a quick glance confirmation without breaking focus.

### Phase 5 — Auto-open notification on watch

Phone `GeofenceForegroundService`, on `OpenResult.Success`, after writing `lastAutoFiredAt`:

```kotlin
Wearable.getMessageClient(this).sendMessage(nodeId, "/garage/autofired", ByteArray(0))
```

Watch `WearMessageListenerService`:
- Listens for `/garage/autofired`
- Posts local notification: title from string res, body from string res
- Haptic: single `EFFECT_HEAVY_CLICK`
- Auto-dismiss after 5s (same as phone result notification)

Node discovery: use `Wearable.getNodeClient(context).connectedNodes` — send to all connected nodes (there will only ever be one watch).

### Phase 6 — Shared canvas components

`GMark`, `CheckGlyph`, `CrossGlyph`, `ExpandingPulses` are duplicated into `:wear` rather than extracted to a `:shared` module. They are ~100 lines total, pure Compose Canvas, zero dependencies. A `:shared` module adds Gradle complexity for no real gain at this scale.

Color constants duplicated in a `WearColors` object in `:wear` — same hex values as `GarageColors`.

## Dependencies to add

### `libs.versions.toml`

```toml
[versions]
wearCompose = "1.4.1"
wearTiles = "1.4.1"
playServicesWearable = "18.2.0"
wearInput = "1.1.0"

[libraries]
wear-compose-material = { group = "androidx.wear.compose", name = "compose-material", version.ref = "wearCompose" }
wear-compose-foundation = { group = "androidx.wear.compose", name = "compose-foundation", version.ref = "wearCompose" }
wear-tiles = { group = "androidx.wear.tiles", name = "tiles", version.ref = "wearTiles" }
wear-tiles-material = { group = "androidx.wear.tiles", name = "tiles-material", version.ref = "wearTiles" }
play-services-wearable = { group = "com.google.android.gms", name = "play-services-wearable", version.ref = "playServicesWearable" }
wear-input = { group = "androidx.wear", name = "wear-input", version.ref = "wearInput" }

[plugins]
android-library = { id = "com.android.library", version.ref = "agp" }
```

### `:app` `build.gradle.kts` addition
```kotlin
implementation(libs.play.services.wearable)
```

### `:wear` `build.gradle.kts`
```kotlin
implementation(libs.wear.compose.material)
implementation(libs.wear.compose.foundation)
implementation(libs.wear.tiles)
implementation(libs.wear.tiles.material)
implementation(libs.play.services.wearable)
implementation(libs.wear.input)
implementation(libs.core.ktx)
implementation(libs.activity.compose)
implementation(platform(libs.compose.bom))
implementation(libs.compose.ui)
implementation(libs.compose.ui.graphics)
```

## Files to create

```
android/
  settings.gradle.kts                          ← add :wear
  gradle/libs.versions.toml                    ← add wear versions/libraries/plugin
  app/
    build.gradle.kts                           ← add play-services-wearable
    src/main/
      AndroidManifest.xml                      ← add WearMessageListenerService
      kotlin/.../wear/
        WearMessageListenerService.kt          ← phone side listener
  wear/
    build.gradle.kts
    src/main/
      AndroidManifest.xml
      kotlin/.../wear/
        WearActivity.kt
        WatchMainScreen.kt                     ← ported HeroButton + states
        GarageOpenerTileService.kt
        WearMessageListenerService.kt          ← watch side listener
        WearColors.kt
```

## Open questions

- **Haptic API:** Wear OS 3+ (API 30+) has `VibratorManager` with predefined effects. Wear OS 2 (API 26-29) only has `Vibrator.vibrate(ms)`. Since `minSdk = 26` for `:wear`, need a compat wrapper. In practice almost all active Wear OS watches are on Wear OS 3+ but the build must not crash on 2.
- **Node discovery timing:** `getConnectedNodes()` is async. For the tile fire-and-forget path, if the watch-phone link isn't established yet the message silently drops. Acceptable — same as phone being out of range.
- **Watch app store listing:** Wear OS apps bundled in the phone APK are delivered automatically by the Play Store to a paired watch if the watch app's `applicationId` matches. No separate listing needed. The `:wear` module `applicationId` must match `:app` exactly: `com.dunnowsoftware.GarageAAtoESP32`.
