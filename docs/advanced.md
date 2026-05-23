# Advanced

## Supported boards

The following boards have pre-configured environments in `firmware/platformio.ini`:

| Env name        | Board                  | Default trigger pin |
|-----------------|------------------------|---------------------|
| `esp32c3`       | ESP32-C3-DevKitM-1     | GPIO 8              |
| `lolin32lite`   | Wemos Lolin32 Lite     | GPIO 22             |
| `esp32dev`      | ESP32-DevKitC          | GPIO 26             |
| `lolin32`       | Wemos Lolin32          | GPIO 26             |
| `esp32s3`       | ESP32-S3-DevKitC-1     | GPIO 4              |
| `nodemcu32s`    | NodeMCU ESP32-S        | GPIO 26             |

Each env also has a `_debug` variant (e.g. `esp32c3_debug`) that enables verbose serial logging.

To build and flash for your board:

```
python -m platformio run -e <env_name> --target upload
```

The pre-built `.bin` attached to each release is ESP32-C3 only. For any other board you must build from source.

## Adding a board not in the list

1. Look up the board ID: `python -m platformio boards espressif32`
2. Add an env block to `firmware/platformio.ini`:
   ```ini
   [env:myboard]
   board = <board_id>
   build_flags =
       -DCORE_DEBUG_LEVEL=0
       -DTRIGGER_PIN=<gpio>
   ```
3. Flash with `python -m platformio run -e myboard --target upload`

Pick a GPIO that has no strapping function and is not shared with USB serial on your specific board.

## Replicating for others

Each person builds their own unit with their own PIN. The repository contains no secrets.
Share the PIN privately; share the code publicly.
