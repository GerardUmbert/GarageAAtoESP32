# Web Log Feature — Implementation Plan

## Overview

Add a captive-portal web server to the ESP32 that logs every garage open event (and failed auth attempts) to NVS flash, and serves them as an HTML page over a self-hosted Wi-Fi AP. No router dependency. Any device that connects to the ESP32's AP and opens any URL gets redirected to the log page.

---

## Architecture

### ESP32 AP + Captive Portal

- **Mode:** Wi-Fi Station AP (`WiFi.softAP`), WPA2-PSK
- **SSID:** `{DEVICE_NAME}_{last3octetsMAC}` — e.g. `Garage-Opener_AABBCC`, **hidden**
- **Password:** `USER_PIN` (already in `config.h`) — minimum 8 characters enforced by app
- **IP:** always `192.168.4.1` (ESP32 AP default gateway)
- **DNS:** `DNSServer` on port 53, wildcard `*` → `192.168.4.1` (captive portal magic)
- **HTTP:** `WebServer` on port 80
  - `GET /` → serves the log HTML page
  - All other routes → `302` redirect to `http://192.168.4.1/`
- BLE and Wi-Fi AP run simultaneously (ESP32 time-slices the 2.4 GHz radio; minor BLE latency increase, acceptable)

### NVS Log Storage

- Library: `Preferences.h` (built into ESP32 Arduino core)
- Namespace: `garagelog`
- Ring buffer: **50 entries**, stored as individual keys
- **Success entry fields:**
  - Unix timestamp (uint32_t, sent from phone)
  - Open reason (1 byte enum)
  - Phone model string (capped at 32 chars, null-terminated)
- **Failure entry fields:**
  - Unix timestamp
  - Type: `"auth_fail"` (wrong PIN — useful for detecting probing/attacks)
- Failures from BLE noise (no write attempt) are **not logged**

### BLE Command Payload — Extended Format (breaking change)

Old: 32 bytes (HMAC-SHA256 only)

New (max ~70 bytes):
```
[0..31]  HMAC-SHA256(USER_PIN, nonce)       — 32 bytes, unchanged
[32..35] Unix timestamp                      — 4 bytes, big-endian uint32
[36]     Open reason enum                    — 1 byte
           0x01 = manual tap
           0x02 = geofence auto-open
           0x03 = voice command
[37..N]  Phone model string                  — up to 32 bytes, null-terminated
           Build.MANUFACTURER + " " + Build.MODEL
```

Firmware verifies HMAC over bytes 0–31 only, then reads trailing fields independently.  
This is a **breaking change** — old app + new firmware = auth failure. Intentional; flash tool ships with each app version.

---

## Files to Change

### Firmware

| File | Change |
|---|---|
| `firmware/include/config.h` | Add `WIFI_AP_CHANNEL` default (1); document 8-char PIN minimum |
| `firmware/src/main.cpp` | Init Wi-Fi AP, DNSServer, WebServer on setup; add DNS/HTTP handle calls to loop |
| `firmware/src/ble_garage.cpp` | Parse extended payload after HMAC verify; extract timestamp, reason, phone model; call logger |
| `firmware/include/web_log.h` | New — logger interface (init, append success, append failure, get HTML) |
| `firmware/src/web_log.cpp` | New — NVS ring buffer implementation + HTML page renderer |

### Android

| File | Change |
|---|---|
| `GarageBleManager.kt` | Build extended payload: HMAC + timestamp + reason byte + phone model |
| `HmacHelper.kt` | No change (HMAC computation unchanged) |
| PIN setup/validation screen | Enforce minimum 8-character PIN with clear error message |
| Post-open success UI | Show hint: "View log → connect to `{SSID}`, password is your PIN" |

---

## Web Page Design

Minimal, no dependencies (no CDN, no JS frameworks — served from ESP32 flash).

- Single HTML file rendered in C++ string
- Table columns: **Date/Time · Reason · Device · Result**
- Newest entries at top
- Failure rows highlighted (e.g. light red background) so attack attempts stand out
- Auto-refresh every 30 seconds (`<meta http-equiv="refresh" content="30">`)
- Footer: firmware version + ESP32 uptime

---

## Open Questions (resolved)

| Question | Decision |
|---|---|
| Time source | Phone sends Unix timestamp in payload |
| NVS or RAM? | NVS (survives reboots), 50-entry ring buffer |
| Auth on web page? | No — AP password is the gate |
| Wi-Fi credentials in repo? | N/A — AP mode, no router credentials needed |
| Breaking payload change? | Yes, intentional — flash tool ships with app |
| AP password | `USER_PIN`, WPA2 minimum 8 chars enforced by app |
| SSID format | `{DEVICE_NAME}_{last3octetsMAC}`, hidden |
| Captive portal | Yes — DNSServer wildcard + catch-all 302 redirect |
| Source tag in log | Yes — reason enum (manual / geofence / voice) |
| Phone model | `Build.MANUFACTURER + " " + Build.MODEL` |

---

## Out of Scope (this feature)

- PIN storage as hash (see `project_pin_hardening.md`)
- Geofence auto-open (already implemented)
- Voice command (see `project_voice_design.md`)
- OTA firmware updates over the AP
- Log export / download
