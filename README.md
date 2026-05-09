# GarageAAtoESP32

Open the garage from your car's Android Auto screen with a single button press.

## Who is this for?

This project exists for a specific situation: you use a **shared or communal garage** — a car park in an apartment block, a rented space, a co-owned facility — where you have **no access to the motor or the gate controller**. You can't wire into the existing system, you can't install a proper smart opener, and the management won't let you touch anything. All you have is a key fob.

This gives you a hands-free way to open the door from your car's Android Auto screen without touching your phone. It works entirely over Bluetooth — no internet, no cloud, no subscription, no hub. The ESP32 sits discreetly near the entrance powered by a USB charger or battery, and it presses the fob button for you when you tap the screen.

It is **not** a replacement for a proper smart garage opener — if you own the garage and have access to the motor, there are better, cleaner solutions. This is specifically for the case where you have no choice but to use the fob.

---

**How it works:** An ESP32 sits at the garage with two wires soldered to the button pads of a key fob. The Android app sends a BLE command from your phone directly to the ESP32, which briefly shorts those pads — exactly like pressing the button. No internet, no cloud, no Wi-Fi required.

## Hardware required

- **ESP32 development board** with BLE — e.g., ESP32-DevKitC, ESP32-C3 SuperMini, XIAO ESP32C3
  _(must be ESP32, not ESP8266 — the ESP8266 has no Bluetooth)_
- **Garage key fob** — you'll solder two wires to the button pads inside it
- **One of the following to trigger the fob:**
  - **NPN transistor** — e.g., 2N2222, BC547, 2N3904 (~$0.10, recommended). Just the transistor + a 1 kΩ resistor. No extra modules.
  - **5V relay module** — familiar to beginners, provides galvanic isolation, but draws more power and costs more (~$1)
- **Power source** — pick one:
  - Wall outlet + any USB phone charger (simplest — if there's a socket nearby)
  - Solar power bank (self-sustaining, no maintenance)
  - USB power bank with always-on / low-current mode (~18 months per charge)
  - 18650 LiPo cells + TP4056 charger board (DIY, most flexible)

See [docs/wiring_diagram.md](docs/wiring_diagram.md) for both wiring options with diagrams.

## Project structure

```
firmware/   ESP32 firmware (PlatformIO + Arduino framework)
android/    Android app (Kotlin + Car App Library)
docs/       Wiring diagram, power budget, provisioning guide
```

## Quick start

### 1. Firmware

1. Install [VS Code](https://code.visualstudio.com/) + [PlatformIO extension](https://platformio.org/) — or just `pip install platformio` for CLI only.
2. Connect your ESP32 via USB.
3. **Double-click `firmware/flash.bat`** (Windows) — it will ask for your PIN, compile, and flash. PIN is never saved to disk.
   - Or open `firmware/` in VS Code, edit `USER_PIN` in `config.h`, and click **Upload**.
4. See [docs/provisioning.md](docs/provisioning.md) for full flashing instructions.

### 2. Android app

1. Open `android/` in [Android Studio](https://developer.android.com/studio).
2. Connect your Android phone via USB with USB debugging enabled.
3. Click **Run**.

### 3. Configure the app (one-time setup on phone)

1. Open **Garage Opener** on your phone and tap **Settings**.
2. Enter your PIN (must match `USER_PIN` in `config.h`).
3. Tap **Scan for Garage** and select your ESP32 from the list.

### 4. Use in car

Connect your phone to Android Auto. Open **Garage Opener** and tap **Open Garage**.

## Security

- The PIN never travels over the air — only an HMAC-SHA256 hash of a fresh random nonce.
- Each connection uses a new nonce, so replay attacks are impossible.
- See [docs/provisioning.md](docs/provisioning.md) for details.

## Wiring

Two options — transistor (simpler, cheaper, lower power) or relay module (more familiar to beginners). See [docs/wiring_diagram.md](docs/wiring_diagram.md) for full diagrams and component notes.

## Power

See [docs/power_budget.md](docs/power_budget.md) for battery sizing and solar guidance.

## Testing without hardware

**Test the Android Auto UI without an ESP32:**
Enable **Demo mode** in the app's phone Settings. Tapping "Open Garage" on the AA screen will run the full UI flow (connecting → opened) and show a toast on your phone — no BLE or hardware needed. Use this with the [Android Auto Desktop Head Unit](docs/provisioning.md#step-9--test-on-android-auto) to test entirely on your PC.

**Test the firmware without the Android app:**
Use **nRF Connect** (free on Android/iOS) to talk directly to the ESP32 and verify auth works. See [docs/provisioning.md](docs/provisioning.md) for the full verification steps.

## Replicating for others

Each person builds their own unit with their own PIN. The repository contains no secrets.
Share the PIN privately; share the code publicly.

## License

[![CC BY-NC-SA 4.0](https://licensebuttons.net/l/by-nc-sa/4.0/88x31.png)](https://creativecommons.org/licenses/by-nc-sa/4.0/)

This project is licensed under **Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International**.

**You can:** build your own unit, share it, modify it, publish derivatives.
**You cannot:** sell it, use it in a commercial product, or relicense it.
**You must:** credit the original author and link back to this repository.

See [LICENSE](LICENSE) for full terms.
