# GarageAAtoESP32

Pull into your garage and it opens itself — triggered from your car's screen, no phone fumbling, no cloud, no subscription.

<a href="https://play.google.com/store/apps/details?id=com.dunnowsoftware.GarageAAtoESP32">
  <img alt="Get it on Google Play" src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" height="60"/>
</a>

### What it looks like in the car

| Idle | Sending | Opened |
|---|---|---|
| ![Idle](docs/screenshots/aa-idle.png) | ![Sending](docs/screenshots/aa-sending.png) | ![Opened](docs/screenshots/aa-opened.png) |

Tap once to send. The screen shows a spinner while the BLE round-trip runs (typically &lt;1s) and a confirmation when the door receives the open command. Failures show a "Try Again" button instead of auto-resetting, so you don't have to scramble to react while driving.

### What it looks like on the phone

| Main (idle) | Sending | Pairing scan | Settings |
|---|---|---|---|
| ![Phone main idle](docs/screenshots/phone-main-idle.jpg) | ![Phone sending](docs/screenshots/phone-main-sending.jpg) | ![Phone scan](docs/screenshots/phone-scan.jpg) | ![Phone settings](docs/screenshots/phone-settings.jpg) |

The phone app mirrors the in-car flow but is the primary surface for setup. The hero open button doubles as a status indicator — concentric pulses while sending, fills green on success, fills pastel red on failure. The pairing screen runs a real BLE scan with a sweeping radar visual; tap the device that appears in the bottom sheet to pair it. Settings is grouped by Security / Testing / (Danger zone, when paired).

### What it looks like on Wear OS

The watch companion app brings the same open button directly to your wrist. Tap once from the watch — it sends the open command to the phone over the Wear OS data layer, the phone handles the BLE round-trip, and the result comes back to the watch with a confirmation animation and haptic feedback. The watch screen turns on and shows the result even from lock screen. A watch tile is also available for one-tap access from the watch face without opening the app.

---

## Who is this for?

This project exists for a specific situation: you use a **shared or communal garage** — a car park in an apartment block, a rented space, a co-owned facility — where you have **no access to the motor or the gate controller**. You can't wire into the existing system, you can't install a proper smart opener, and the management won't let you touch anything. All you have is a key fob.

This gives you a hands-free way to open the door from your car's Android Auto screen without touching your phone. It works entirely over Bluetooth — no internet, no cloud, no subscription, no hub. The ESP32 presses the fob button for you when you tap the screen — either riding in the car itself powered off the car's USB / 12V socket (recommended for a single car / single user), or fixed near the entrance powered by a USB charger or battery (better for shared garages with multiple users or multiple vehicles). See [Two ways to deploy](#two-ways-to-deploy) for the trade-offs.

There are **three ways** to do the actual button-press. The recommended option (relay module) is beginner-friendly and works for most setups. Options B and C require no soldering inside the fob — important if you have to give it back when you move out. See [Choose your trigger mechanism](#choose-your-trigger-mechanism) below.

It is **not** a replacement for a proper smart garage opener — if you own the garage and have access to the motor, there are better, cleaner solutions. This is specifically for the case where you have no choice but to use the fob.

### Two ways to deploy

**In the car** *(recommended for a single car / single user)*: ESP32 + fob both ride in the car, powered off the car's USB or 12V socket via a USB-A adapter. The car's display becomes a permanent "open garage" button — tap once and the fob inside the glovebox triggers the gate. This is the smoothest experience: BLE always works because the ESP32 is in the same vehicle as your phone; the only range that matters is the fob's RF range to the gate motor from inside the car (typically 50–100 m, usually more than enough). No connectivity concerns, no power planning, no outdoor enclosure — the whole unit travels with the car and charges whenever the car is on. The in-car auto-fire (triggers automatically when the app detects the ESP32 in range on the AA screen) is particularly effective here.

**At the garage** *(better for multi-user / multi-vehicle setups)*: ESP32 lives near the gate, powered by a wall outlet / power bank / solar, and is hidden in or near the entrance. The fob stays at the garage. This is the only deployment that supports **multiple users or multiple vehicles** — anyone you share the password with (other family members, housemates, visiting friends with their own car) can install the app on their own phone and open the same gate without you having to be there or hand anything over. The trade-off: you're now relying on BLE range from a phone in the car to a fixed point near the gate entrance (typically 10–30 m with walls; plan your placement accordingly). The firmware is optimized for this mode (low-power deep sleep, wake-on-BLE-advertising).

---

**How it works:** An ESP32 sits at the garage and is connected to a fob trigger mechanism (see the three options below). The Android app sends a BLE command from your phone directly to the ESP32, which then triggers the fob — exactly as if someone had pressed the button. No internet, no cloud, no Wi-Fi required.

## Quick start

### 1. Firmware

There is no pre-built firmware — the PIN must be yours, so you build it yourself. It's one step:

1. Connect your ESP32 via USB.
2. Download `GarageAAtoESP32-flash-tool.zip` from the [Releases](../../releases) page, extract it, and run the script for your OS:
   - **Windows:** double-click `flash.bat`
   - **Linux:** `chmod +x flash.sh && ./flash.sh`

The tool installs PlatformIO if needed, lets you pick your board, detects the port, asks for your PIN, compiles, and flashes. Your PIN is never saved to disk.

See [docs/provisioning.md](docs/provisioning.md) for full flashing instructions and troubleshooting.

### 2. Android app

- **Play Store** *(easiest, includes Android Auto)*: [Get it on Google Play](https://play.google.com/store/apps/details?id=com.dunnowsoftware.GarageAAtoESP32)
- **APK** *(no Play Store)*: download the latest `.apk` from the [Releases](../../releases) page and sideload it. **Note: sideloaded APKs do not work with Android Auto** — AA only loads apps installed through the Play Store.
- **Build from source**: open `android/` in [Android Studio](https://developer.android.com/studio), connect your phone via USB with debugging enabled, and click **Run**. Same limitation as the APK — Android Auto requires a Play Store install.

**Wear OS companion** *(optional)*: if you have a paired Wear OS watch, install the companion from the Play Store — it is delivered automatically when you install the phone app. The watch app lets you open the garage directly from your wrist, with result feedback and haptics. A watch tile is also available for one-tap access from the watch face.

### 3. Configure the app (one-time setup on phone)

Open **Garage Opener** — it will walk you through scanning for your ESP32 and entering your PIN on first launch.

### 4. Use in car

Connect your phone to Android Auto. Open **Garage Opener** and tap **Open Garage**.

**Optional — geofence auto-open:** in the phone app, go to Settings → Auto-open, set your garage location on the map, adjust the trigger radius (15–75 m), and enable the toggle. From then on the garage opens automatically when you approach — no tap, no phone interaction needed. Works with or without Android Auto: if AA is connected it fires via the car screen; otherwise it falls back to speed and activity detection. A dual-geofence system is used — an outer GPS warmup ring (configurable, default 400 m beyond the inner radius) starts requesting location fixes as you approach, so fresh speed data is ready when the inner zone fires. A notification is posted when the open fires so you always know it happened. See [docs/geofence-auto-open.md](docs/geofence-auto-open.md) for the full trigger logic.

**Open history:** the app keeps a persistent log of every open event — timestamp, trigger source (manual tap, Android Auto, geofence, voice), and result. Access it via the history entry on the main screen. Each row expands to show gate detail and suppression reason for geofence events.

**ESP32 web log server** *(optional, enabled during flash)*: when enabled, the ESP32 hosts its own open Wi-Fi AP (`{device-name}_{MAC}`) with a captive portal. Connect any phone or laptop to it and a browser page opens automatically showing the full event log — timestamps, trigger sources, phone model, and any failed auth attempts. Useful for at-garage deployments where multiple people use the same opener. Enable it by answering **y** to the web log prompt in the flash tool.

---

## Choose your trigger mechanism

The ESP32 supports **three ways** to press the fob's button. Pick the one that fits your situation; the firmware handles all three from a single config flag (`TRIGGER_MODE` in `firmware/include/config.h`).

| Option | Soldering required? | Fob modified? | Best for |
|---|---|---|---|
| **A — Relay module** *(recommended)* | Yes (2 wires to fob button pads) | Yes | Anyone who can solder — beginner-friendly, reliable, galvanically isolated |
| **B — Relay + power-rail switching** | Minimal (copper tape only, outside fob) | **No** | Rented fob that must be returned untouched. Fully reversible. |
| **C — Transistor** | Yes (2 wires inside the fob) | Yes | Advanced — lower power draw than relay (~20 mA vs ~70 mA) |

### Option A — Relay module *(recommended)*

Connect a small relay module between the ESP32 and the fob's button pads. The relay shorts the two pads when triggered — identical to pressing the button. No knowledge of transistor biasing needed; the relay module's onboard driver handles everything.

**Use a 3V coil relay** (e.g. Songle SRD-03VDC-SL-C) — works directly with the ESP32's 3.3V GPIO and power rail. A 5V coil relay also works if you power VCC from the board's 5V/VIN pin.

Wire: ESP32 GND → relay GND, ESP32 3.3V → relay VCC, ESP32 GPIO 26 → relay IN. Fob button pad A → relay COM, fob button pad B → relay NO.

This is also the right approach for **power-rail switching** (Option D in older docs): instead of shorting the button pads, connect COM/NO in series with the fob's battery ground rail — the fob has its button held down mechanically and fires every time it gets power. Renter-friendly, no fob modification required.

### Option C — Transistor *(advanced)*

A single NPN transistor (2N2222, BC547, etc.) shorts the fob's two button pads. Lowest power draw (~20 mA vs ~60–80 mA for relay coil), but requires understanding transistor biasing and a 1 kΩ base resistor. Suitable if power budget is critical or you already have transistors on hand.

See [docs/wiring_diagram.md](docs/wiring_diagram.md) for full diagrams, materials lists, and tuning notes for all options.

## Hardware required

- **ESP32-C3 development board** — e.g., ESP32-C3 SuperMini, XIAO ESP32C3, ESP32-C3-DevKitM-1 (default build target)
  _(must be an ESP32 variant with BLE — the ESP8266 has no Bluetooth. Other ESP32 variants such as the classic ESP32-DevKitC also work; see [docs/advanced.md](docs/advanced.md) for instructions.)_
- **Trigger mechanism** — pick one based on the table above:
  - Option A *(recommended)*: relay module with 3V coil, e.g. Songle SRD-03VDC-SL-C (~$1–2) — works directly with ESP32 3.3V GPIO and power rail
  - Option B: relay module (same as A) + copper tape (two ~20 mm discs) for fake battery contacts + thin wire — no soldering inside the fob
  - Option C: NPN transistor (2N2222 / BC547 / 2N3904, ~$0.10) + 1 kΩ resistor
- **Garage key fob** — for Options A and C you'll solder two wires to the button pads inside it. For Option B you don't open the fob at all.
- **Power source** — pick one based on where you're deploying:
  - Wall outlet + any USB phone charger (simplest — if there's a socket nearby at the garage)
  - Solar power bank (self-sustaining, no maintenance — for at-garage deployments without a socket)
  - USB power bank with always-on / low-current mode (~12 months per charge)
  - 18650 LiPo cells + TP4056 charger board (DIY, most flexible)
  - **Car USB / 12V socket** (for the in-car deployment — ESP32 + fob both stay in the car, powered whenever the car is on)

## Project structure

```
firmware/        ESP32 firmware (PlatformIO + Arduino framework)
android/app/     Phone app (Kotlin + Car App Library + Android Auto)
android/wear/    Wear OS companion app (Compose for Wear OS)
docs/            Wiring diagram, power budget, provisioning guide
```

## Security

- The PIN never travels over the air — only an HMAC-SHA256 hash of a fresh random nonce.
- Each connection uses a new nonce, so replay attacks are impossible.
- See [docs/provisioning.md](docs/provisioning.md) for details.

## Wiring

Three options:
- **A — Relay module** *(recommended)* — beginner-friendly, galvanically isolated, requires soldering to fob button pads
- **B — Relay + power-rail switching** — no soldering inside the fob, fully reversible, best for rented fobs
- **C — Transistor** — lowest power draw (~20 mA), requires soldering, advanced

See [docs/wiring_diagram.md](docs/wiring_diagram.md) for full diagrams, materials lists, and component notes.

## Power

See [docs/power_budget.md](docs/power_budget.md) for battery sizing and solar guidance.

## Testing without hardware

**Test the Android Auto UI without an ESP32:**
Enable **Demo mode** in the app's phone Settings. Tapping "Open Garage" on the AA screen will run the full UI flow (connecting → opened) and show a toast on your phone — no BLE or hardware needed. Use this with the [Android Auto Desktop Head Unit](docs/provisioning.md#step-9--test-on-android-auto) to test entirely on your PC.

**Test the firmware without the Android app:**
Use **nRF Connect** (free on Android/iOS) to talk directly to the ESP32 and verify auth works. See [docs/provisioning.md](docs/provisioning.md) for the full verification steps.

---

For building on non-C3 boards and other advanced topics see [docs/advanced.md](docs/advanced.md).

## License

[![CC BY-NC-SA 4.0](https://licensebuttons.net/l/by-nc-sa/4.0/88x31.png)](https://creativecommons.org/licenses/by-nc-sa/4.0/)

This project is licensed under **Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International**.

**You can:** build your own unit, share it, modify it, publish derivatives.
**You cannot:** sell it, use it in a commercial product, or relicense it.
**You must:** credit the original author and link back to this repository.

See [LICENSE](LICENSE) for full terms.
