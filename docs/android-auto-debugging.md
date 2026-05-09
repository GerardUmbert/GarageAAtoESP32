# Debugging the Android Auto app on a PC

The Android Auto **Desktop Head Unit (DHU)** lets you project the AA UI from the phone to a window on your PC, instead of needing a real car or a head unit. Combined with `adb logcat`, it's the only sane way to iterate on Car App Library bugs.

## One-time setup

1. Install the DHU via Android Studio:
   `SDK Manager` → `SDK Tools` tab → check `Android Auto Desktop Head Unit Emulator` → Apply.
   It lands at `%LOCALAPPDATA%\Android\Sdk\extras\google\auto\desktop-head-unit.exe`.

2. On the phone, enable AA developer mode (one time):
   Open the Android Auto app → Settings → tap `Version` 10 times → developer settings unlock.

## Each debug session

Order matters — start the head unit server **before** launching DHU.

1. **On the phone:** AA app → ⋮ menu (top right) → Developer settings → tap **Start head unit server**.
   You should see a notification confirming it's running.

2. **On the PC**, with the phone connected over ADB (USB or wireless):

   ```powershell
   adb forward tcp:5277 tcp:5277
   & "$env:LOCALAPPDATA\Android\Sdk\extras\google\auto\desktop-head-unit.exe"
   ```

   The DHU window should populate with the AA launcher within a few seconds.

## Pulling crash logs

While DHU is connected, the phone is doing the actual rendering and the AA UI is just being projected — so logcat on the phone has everything. To capture an exception when the app shows "unexpected error":

```powershell
adb logcat -c                                              # clear buffer
# (reproduce the error in DHU)
adb logcat -d | Select-String "CarApp|<your-package>|FATAL"
```

The Car App Library logs under tags `CarApp.H`, `CarApp.H.Tem`, and `GH.*` (gearhead). Real exceptions surface as `E/CarApp.H.Tem` lines with full stack traces — that's the signal you want.

## Troubleshooting

- **DHU exits immediately on launch** → port 5277 isn't forwarded, or the head unit server isn't running on the phone. Re-do step 1, then re-run `adb forward`.
- **DHU says "waiting for phone" forever** → toggle the head unit server off and back on in AA developer settings. The listener sometimes gets into a stuck state.
- **DHU disconnects when you force-stop AA on the phone** → expected. Restart the head unit server and relaunch DHU.
- **Wireless ADB drops mid-session** → DHU only runs while the ADB transport is alive. If your phone roams between Wi-Fi networks (or onto Tailscale), the port reconnects on a new port number; redo `adb forward` against the new port.
