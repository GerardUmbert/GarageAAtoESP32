#pragma once
#include <stdint.h>

// ── Trigger mode ──────────────────────────────────────────────────────────────
// Choose how the ESP32 fires the garage. Pick exactly one:
//
//   MODE_TRANSISTOR  – Wired into the fob. GPIO HIGH for RELAY_PULSE_MS turns on
//                      a small NPN transistor that shorts the fob's button pads.
//                      (Requires opening the fob and soldering 3 wires.)
//
//   MODE_RELAY       – Wired into the fob through a relay module instead of a
//                      transistor. GPIO LOW pulls the relay coil (most modules
//                      are active-low). Same wiring complexity as transistor.
//
//   MODE_RELAY_HIGH  – Same as MODE_RELAY but for active-high relay modules
//                      (trigger on HIGH, rest on LOW). Use this if your relay
//                      module energizes when the GPIO is HIGH. Common on
//                      modules with a direct transistor driver (e.g. JQC-3FF).
//
//   MODE_CAP_PULSE   – No soldering. ESP32 drives a small conductive pad
//                      (copper tape) physically pressed against the capacitive
//                      top button of an Adaprox/Tuya-style Fingerbot, faking a
//                      finger touch. The Fingerbot then mechanically presses
//                      the fob button. Renter-friendly, fully reversible.
//
#define MODE_TRANSISTOR    1
#define MODE_RELAY         2
#define MODE_CAP_PULSE     3
#define MODE_RELAY_HIGH    4

#ifndef TRIGGER_MODE
#define TRIGGER_MODE       MODE_RELAY_HIGH
#endif

// ── Hardware ──────────────────────────────────────────────────────────────────
// Pin used to drive the load (transistor base, relay coil, or capacitive pad).
// GPIO 8 is a safe general-purpose output on ESP32-C3 (no strapping function,
// not shared with USB serial, available on all common dev boards).
#ifndef TRIGGER_PIN
#define TRIGGER_PIN        8
#endif

// Pulse duration. For MODE_TRANSISTOR / MODE_RELAY this is how long the fob
// button is held "pressed". For MODE_CAP_PULSE this is how long the conductive
// pad is driven before being released to high-Z.
#ifndef RELAY_PULSE_MS
#define RELAY_PULSE_MS     1500
#endif

// MODE_CAP_PULSE only: capacitive sensors look for a touch *and* release edge,
// so after the pulse the pin is set to INPUT (high-Z) instead of being driven
// LOW. Some sensors also need a short minimum touch time (typ. 100–300 ms);
// adjust RELAY_PULSE_MS upward if a single press isn't being registered.

// ── LED indicator ─────────────────────────────────────────────────────────────
// Optional. If defined, the LED flashes once per garage trigger.
// LED_ON_LEVEL: 1 = active-high (LED lights on HIGH), 0 = active-low (LED lights on LOW).
// If LED_PIN == TRIGGER_PIN and the trigger is active-high, the flash is free —
// no extra code runs. Define them separately only when the LED needs independent control.
#ifdef LED_PIN
#ifndef LED_ON_LEVEL
#define LED_ON_LEVEL       1
#endif
#endif

// ── Legacy aliases (kept so the existing pin name still works in case anyone
// is referencing it externally; new code should use TRIGGER_PIN). ─────────────
#define RELAY_PIN          TRIGGER_PIN

// ── BLE ───────────────────────────────────────────────────────────────────────
// Change DEVICE_NAME per install so multiple units are distinguishable
#ifndef DEVICE_NAME
#define DEVICE_NAME     "Garage-Opener"
#endif

// 128-bit service / characteristic UUIDs
#define SERVICE_UUID       "12345678-0000-1000-8000-00805F9B34FB"
#define NONCE_CHAR_UUID    "12345678-0001-1000-8000-00805F9B34FB"
#define COMMAND_CHAR_UUID  "12345678-0002-1000-8000-00805F9B34FB"
#define STATUS_CHAR_UUID   "12345678-0003-1000-8000-00805F9B34FB"
#define CAPS_CHAR_UUID     "12345678-0004-1000-8000-00805F9B34FB"

// Capability flags (CAPS_CHAR_UUID, 1 byte, read-only)
#define CAP_WEBLOG         0x01

// Open reason byte sent by the Android app in the extended BLE payload.
enum class OpenReason : uint8_t {
    MANUAL   = 0x01,
    GEOFENCE = 0x02,
    VOICE    = 0x03,
    WATCH    = 0x04,
};

// ── Timing ────────────────────────────────────────────────────────────────────
// ── Easy to tune ──────────────────────────────────────────────────────────────
#define ADV_TIMEOUT_S      30     // Seconds to advertise before sleeping
#define SLEEP_DURATION_S    5     // Seconds to deep-sleep between ad windows (lower = more responsive, more power)
#define AUTH_TIMEOUT_MS    10000  // ms to wait for Command write after connect

// ── Wi-Fi STA (Home Assistant webhook server) ─────────────────────────────────
// Enabled via -DENABLE_HA_WEBHOOK=1 at build time (set by flash.ps1 / flash.sh).
// The ESP32 joins your home WiFi and exposes POST /open authenticated with the
// same USER_PIN as the BLE stack. BLE continues to run in parallel. No sleep.
#ifdef ENABLE_HA_WEBHOOK
#ifndef HA_WIFI_SSID
#define HA_WIFI_SSID        "your-wifi-ssid"
#endif
#ifndef HA_WIFI_PASS
#define HA_WIFI_PASS        "your-wifi-password"
#endif
#define HA_SERVER_PORT      80
#define HA_RATE_LIMIT_MAX   3
#define HA_RATE_LIMIT_WIN_S 60
#endif

// ── Wi-Fi AP (captive portal log server) ──────────────────────────────────────
// The ESP32 broadcasts a hidden WPA2 AP so the log page is accessible without
// a router. SSID is derived at runtime: {DEVICE_NAME}_{last3octetsMAC}.
// Password is USER_PIN — WPA2 requires a minimum of 8 characters.
#ifndef WIFI_AP_CHANNEL
#define WIFI_AP_CHANNEL    1
#endif

// ── Security ──────────────────────────────────────────────────────────────────
// Set this PIN before flashing. Must match the PIN entered in the Android app.
// MINIMUM 8 CHARACTERS — required for WPA2 AP password.
#ifndef USER_PIN
#define USER_PIN           "change-me-before-flashing"
#endif
