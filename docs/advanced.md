# Advanced

## Building for a different board

The default build target is `esp32c3` (ESP32-C3-DevKitM-1 pinout). To build for a different ESP32 variant:

1. Open `firmware/platformio.ini` and add a new env block, e.g. for a classic ESP32-DevKitC:
   ```ini
   [env:esp32devkit]
   platform = espressif32
   board = esp32dev
   framework = arduino
   lib_deps =
       h2zero/NimBLE-Arduino @ ^1.4.2
   monitor_speed = 115200
   build_flags = -DCORE_DEBUG_LEVEL=0
   ```
2. Open `firmware/include/config.h` and set `TRIGGER_PIN` to a valid GPIO for your board (e.g. `26` for ESP32-DevKitC).
3. Flash with `pio run -e esp32devkit --target upload` (or select the env in VS Code's PlatformIO sidebar).

The pre-built `.bin` attached to each release is ESP32-C3 only. For any other board you must build from source.

## Replicating for others

Each person builds their own unit with their own PIN. The repository contains no secrets.
Share the PIN privately; share the code publicly.
