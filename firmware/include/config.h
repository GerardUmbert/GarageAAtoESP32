#pragma once

// ── Hardware ──────────────────────────────────────────────────────────────────
#define RELAY_PIN          26
#define RELAY_ACTIVE_LOW   false  // false = transistor (GPIO HIGH triggers); true = relay module (GPIO LOW triggers)
#define RELAY_PULSE_MS     500    // Duration of simulated fob button press

// ── BLE ───────────────────────────────────────────────────────────────────────
// Change DEVICE_NAME per install so multiple units are distinguishable
#define DEVICE_NAME        "GarageOpener"

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
#define USER_PIN           "change-me-before-flashing"
