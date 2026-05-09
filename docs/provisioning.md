# Provisioning Guide

---

## Step 1 — Choose your PIN

Pick any PIN string, e.g. `my-garage-2024`. Avoid spaces and special shell characters.
Keep it secret — share verbally or via secure message, never in the repo or plaintext messages.

---

## Step 2 — Install PlatformIO (one-time)

PlatformIO is the build system used to compile and flash the firmware. You only install it once.

### Option A — VS Code extension (recommended)

1. Install [VS Code](https://code.visualstudio.com/).
2. Open VS Code → Extensions (`Ctrl+Shift+X`) → search **PlatformIO IDE** → Install.
3. Restart VS Code. The `pio` CLI is now available in your terminal automatically.

### Option B — CLI only (no VS Code needed)

Requires Python 3. If you don't have Python:
- Windows: download from [python.org](https://www.python.org/downloads/) — tick **"Add Python to PATH"** during install.
- macOS/Linux: `brew install python` or use your package manager.

Then install PlatformIO:
```bash
pip install platformio
```

Verify it works:
```bash
pio --version
```

---

## Step 3 — Flash the firmware

### Option A — Flash script (easiest, Windows)

A script is provided that asks for your PIN, flashes the firmware, then immediately wipes the PIN from the file so it's never stored on disk.

1. Connect your ESP32 via USB.
2. Double-click `firmware/flash.bat`.
3. Enter your PIN when prompted.
4. Wait for `Leaving... Hard resetting via RTS pin...` — done.

If the script can't find the COM port automatically, run it from PowerShell with the port specified:
```powershell
cd firmware
.\flash.ps1 -Port COM3
```

To find your COM port: Device Manager → Ports (COM & LPT) → look for "USB Serial Device" or "CP210x".

### Option B — Flash via VS Code (PlatformIO extension)

1. Open the `firmware/` folder in VS Code (`File → Open Folder…` → select `firmware/`).
2. Edit `firmware/include/config.h` — set your PIN:
   ```cpp
   #define USER_PIN  "my-garage-2024"
   ```
3. Optionally change `DEVICE_NAME`:
   ```cpp
   #define DEVICE_NAME  "Garage-Main"
   ```
4. Set `TRIGGER_MODE` to match your hardware: `MODE_TRANSISTOR` (Option A), `MODE_RELAY` (Option B), or `MODE_CAP_PULSE` (Option C — fingerbot, no soldering).
5. Wait for PlatformIO to finish indexing (status bar bottom-left).
6. Click the **→ Upload** arrow in the PlatformIO toolbar, or press `Ctrl+Alt+U`.
7. Watch the terminal for `Leaving... Hard resetting via RTS pin...` — that means success.

### Option C — Flash via terminal

```bash
cd firmware
pio run -e esp32 --target upload
```

If you get a port error, specify it manually:
```bash
pio run -e esp32 --target upload --upload-port COM3        # Windows
pio run -e esp32 --target upload --upload-port /dev/ttyUSB0  # Linux/macOS
```

### Verify firmware is running

Open the Serial Monitor (`Ctrl+Alt+S` in VS Code / PlatformIO, or `pio device monitor`) at 115200 baud.
You should see:
```
BLE advertising started: GarageOpener
```
If you see nothing, press the reset button on the ESP32.

---

## Step 4 — Build the Android app

### Prerequisites

- [Android Studio](https://developer.android.com/studio) (Hedgehog 2023.1.1 or later recommended)
- Android SDK 35 installed (Android Studio will prompt to install if missing)
- Java 17 (bundled with Android Studio — no separate install needed)

### Build a debug APK (simplest)

1. Open Android Studio.
2. `File → Open` → select the `android/` folder inside this repo.
3. Wait for Gradle sync to complete (progress bar at the bottom).
4. In the menu: `Build → Build Bundle(s) / APK(s) → Build APK(s)`.
5. When complete, click **locate** in the notification popup, or find the APK at:
   ```
   android/app/build/outputs/apk/debug/app-debug.apk
   ```

### Build via terminal (alternative)

```bash
cd android
./gradlew assembleDebug          # macOS / Linux
.\gradlew.bat assembleDebug      # Windows
```

APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

---

## Step 5 — Enable USB debugging on your phone

1. Go to **Settings → About phone**.
2. Tap **Build number** 7 times until you see "You are now a developer".
3. Go to **Settings → Developer options**.
4. Enable **USB debugging**.
5. Connect phone to PC via USB. Tap **Allow** on the popup that appears on the phone.

---

## Step 6 — Sideload the APK onto your phone

### Option A — via Android Studio (easiest)

With your phone connected and USB debugging enabled:
1. In Android Studio, select your phone from the device dropdown (top toolbar).
2. Click the **▶ Run** button (or `Shift+F10`).
   Android Studio builds and installs the debug APK directly.

### Option B — via adb command line

```bash
adb install android/app/build/outputs/apk/debug/app-debug.apk
```

If you have multiple devices connected:
```bash
adb devices                          # find your device ID
adb -s <device-id> install app-debug.apk
```

### Option C — copy APK to phone manually

1. Copy `app-debug.apk` to your phone (USB file transfer, Google Drive, email, etc.).
2. On the phone, open a file manager and tap the APK.
3. If prompted, go to **Settings → Install unknown apps** and allow your file manager.
4. Tap **Install**.

---

## Step 7 — Enable Android Auto Developer Mode

Sideloaded apps are **blocked from appearing in Android Auto by default**. You must enable developer mode in the Android Auto app to allow unknown sources.

1. Open the **Android Auto** app on your phone.
2. Tap the **≡ hamburger menu** (top-left) → **Settings**.
3. Scroll to the bottom and tap **Version** (the version number text) **10 times** in a row.
4. A dialog appears: tap **OK** to enable developer mode.
5. Tap the **≡ menu** again → you should now see **Developer settings**.
6. In Developer settings, enable **Unknown sources**.

> **Note:** This setting is per-phone and persists until you disable it or clear Android Auto's data. You do not need to repeat it after rebooting.

---

## Step 8 — Configure the app (one-time phone setup)

1. Open **Garage Opener** on your phone (it appears in your app drawer after sideloading).
2. Tap **Settings**.
3. Enter your PIN — must exactly match `USER_PIN` in `config.h`.
4. Tap **Save PIN**.
5. Tap **Scan for Garage** — the app scans for BLE devices advertising the garage service.
   - Make sure the ESP32 is powered on and within ~10 m.
   - Scan runs for 15 seconds.
6. Tap your device in the list (e.g., "Garage-Main") to save it.
   - The main screen now shows the saved device name.

Grant Bluetooth permissions if prompted (required for BLE scan).

---

## Step 9 — Test on Android Auto

### With a real car head unit

1. Connect your phone to the car via USB (or wireless AA if supported).
2. Android Auto launches on the car screen.
3. Find **Garage Opener** in the app launcher on the AA screen.
4. Tap **Open Garage**.

### Without a car — use the Desktop Head Unit (DHU)

The DHU lets you run Android Auto on your PC for testing without a car.

1. In Android Studio: `Tools → SDK Manager → SDK Tools` tab → enable **Android Auto Desktop Head Unit Emulator** → Apply.
2. Connect your phone with USB debugging enabled.
3. On your phone, open **Android Auto** → hamburger menu → **Developer settings** → enable **DHU mode** (may be labelled "Start head unit server").
4. On your PC, open a terminal and run:
   ```bash
   # macOS / Linux
   $ANDROID_HOME/extras/google/auto/desktop-head-unit

   # Windows (PowerShell)
   & "$env:LOCALAPPDATA\Android\Sdk\extras\google\auto\desktop-head-unit.exe"
   ```
5. The DHU window opens — this is your simulated car screen.
6. Find Garage Opener in the DHU launcher and test the Open button.

> The DHU does NOT emulate BLE, so the button will attempt a real BLE connection to the ESP32 via your phone's Bluetooth. Keep the ESP32 powered on and within range.

---

## Step 10 — Verify with nRF Connect (optional but recommended)

Before testing in the car, verify the firmware independently using the free **nRF Connect** app (Play Store / App Store):

1. Open nRF Connect → **Scanner** tab → tap **Scan**.
2. Find your device name (e.g., "GarageOpener") and tap **Connect**.
3. Navigate to the **Garage Service** (`12345678-0000-...`).
4. Tap the Nonce characteristic (`...-0001-...`) → tap the **↓ Read** button → 16 bytes appear.
5. Copy those 16 bytes as hex. Run this Python script to compute the correct HMAC:
   ```python
   import hmac, hashlib, binascii
   pin   = b"my-garage-2024"   # your PIN
   nonce = bytes.fromhex("aabbcc...")  # paste your 16 bytes here
   result = hmac.new(pin, nonce, hashlib.sha256).digest()
   print(binascii.hexlify(result))
   ```
6. In nRF Connect, tap the Command characteristic (`...-0002-...`) → **↑ Write** → paste the 32-byte hex output → **Send**.
7. Read the Status characteristic (`...-0003-...`) → `01` = success (relay clicks), `00` = auth failed.

---

## Sharing the app with others (family / co-owners)

Each person needs:
1. Your PIN — share verbally or via secure message (Signal, WhatsApp, etc.).
2. The APK — build it yourself and send the file, or have them clone the repo and build it.
3. Follow Steps 5–8 above on their phone.

The PIN never appears in the repository or the APK. Each person enters it manually in the app settings.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Serial Monitor shows nothing | Wrong baud rate or wrong port | Set 115200 baud; check port in PlatformIO |
| Relay / transistor never clicks | Wrong `TRIGGER_MODE` | Set `MODE_TRANSISTOR` for transistor, `MODE_RELAY` for relay module |
| Fingerbot never triggers (cap-pulse) | Pad coupling weak | Increase `RELAY_PULSE_MS`, enlarge pad, or add ESP32 GND wire to fingerbot chassis |
| nRF Connect: Status always `00` | PIN mismatch | Verify `USER_PIN` in `config.h` exactly matches app PIN |
| App not appearing in Android Auto | Unknown sources not enabled | Repeat Step 6; confirm "Unknown sources" is on |
| BLE scan finds nothing | ESP32 not advertising | Check power; check Serial Monitor; move phone closer |
| Scan times out immediately | BLE permission denied | Go to phone Settings → Apps → Garage Opener → Permissions → allow Location + Nearby devices |
| `adb: device unauthorized` | USB debugging not accepted | Unlock phone, tap Allow on the dialog |
| Gradle sync fails | SDK not installed | Android Studio → SDK Manager → install Android 15 (API 35) |
