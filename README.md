# GarageAAtoESP32

Open the garage from your car's Android Auto screen with a single button press.

**How it works:** An ESP32 sits at the garage with a relay wired in parallel to a physical key fob button. The Android app sends a BLE command from your phone directly to the ESP32 — no internet, no cloud, no Wi-Fi required.

## Hardware required

- ESP32 development board (e.g., ESP32-DevKitC)
- 5V relay module (single channel)
- Garage key fob (you'll solder two wires to the button pads)
- Power source — pick one:
  - USB power bank with always-on / low-current mode
  - 18650 LiPo cells + TP4056 charger board (+ optional solar panel)
  - Solar power bank

## Project structure

```
firmware/   ESP32 firmware (PlatformIO + Arduino framework)
android/    Android app (Kotlin + Car App Library)
docs/       Wiring diagram, power budget, provisioning guide
```

## Quick start

### 1. Firmware

1. Install [VS Code](https://code.visualstudio.com/) + [PlatformIO extension](https://platformio.org/).
2. Open the `firmware/` folder in VS Code.
3. Edit `firmware/include/config.h`:
   - Set `USER_PIN` to your chosen passphrase.
   - Optionally change `DEVICE_NAME` to distinguish multiple units.
4. Connect ESP32 via USB and click **Upload** in PlatformIO.

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

See [docs/wiring_diagram.md](docs/wiring_diagram.md).

## Power

See [docs/power_budget.md](docs/power_budget.md) for battery sizing and solar guidance.

## Testing without a car

Use **nRF Connect** (free on Android/iOS) to talk directly to the ESP32 and verify auth works before testing in the car. See [docs/provisioning.md](docs/provisioning.md) Step 5.

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
