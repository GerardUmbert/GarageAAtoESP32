#pragma once
#include <NimBLEDevice.h>

// Callback invoked when a valid auth command is received.
// Implemented in main.cpp.
void onGarageOpen();

namespace BleGarage {
    void init();
    void startAdvertising();
    void stopAdvertising();

    // Returns true if a client is currently connected.
    bool isConnected();
}
