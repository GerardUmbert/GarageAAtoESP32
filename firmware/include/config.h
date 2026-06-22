#pragma once

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
#define TRIGGER_MODE       MODE_TRANSISTOR
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
#define RELAY_PULSE_MS     500
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

// ── Timing ────────────────────────────────────────────────────────────────────
// ── Easy to tune ──────────────────────────────────────────────────────────────
#define ADV_TIMEOUT_S      30     // Seconds to advertise before sleeping
#define SLEEP_DURATION_S    5     // Seconds to deep-sleep between ad windows (lower = more responsive, more power)
#define AUTH_TIMEOUT_MS    10000  // ms to wait for Command write after connect

// ── Security ──────────────────────────────────────────────────────────────────
// Set this PIN before flashing. Must match the PIN entered in the Android app.
#ifndef USER_PIN
#define USER_PIN           "change-me-before-flashing"
#endif
