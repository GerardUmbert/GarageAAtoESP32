#include <Arduino.h>
#include "config.h"
#include "ble_garage.h"
#include "relay_control.h"

// Tracks whether the relay should fire after the current BLE operation.
static volatile bool g_openRequested = false;

// Called from ble_garage.cpp on successful auth.
void onGarageOpen() {
    g_openRequested = true;
}

// ── Deep sleep helpers ────────────────────────────────────────────────────────

static void goToSleep() {
    NimBLEDevice::deinit(true);
    esp_sleep_enable_timer_wakeup((uint64_t)SLEEP_DURATION_S * 1000000ULL);
    esp_deep_sleep_start();
}

// ── Arduino entry points ──────────────────────────────────────────────────────

void setup() {
#if CORE_DEBUG_LEVEL > 0
    Serial.begin(115200);
    Serial.println("GarageOpener starting");
#endif

    RelayControl::init();
    BleGarage::init();
    BleGarage::startAdvertising();
}

void loop() {
    if (g_openRequested) {
        g_openRequested = false;
        RelayControl::pulse(RELAY_PULSE_MS);
        // Short delay so the status notify has time to reach the phone
        // before BLE tears down.
        delay(200);
        goToSleep();
    }

    // If nobody connects within the advertising window, sleep and retry.
    static uint32_t advStart = millis();
    if (!BleGarage::isConnected()) {
        if (millis() - advStart > (uint32_t)ADV_TIMEOUT_S * 1000UL) {
            goToSleep();
        }
    } else {
        // Reset the timeout while connected so a slow auth doesn't cut off.
        advStart = millis();
    }

    delay(50);
}
