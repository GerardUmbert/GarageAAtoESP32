#include "ble_garage.h"
#include "auth.h"
#include "config.h"
#include <NimBLEDevice.h>
#include <esp_random.h>
#include <string.h>

static NimBLEServer          *pServer        = nullptr;
static NimBLECharacteristic  *pNonceChar     = nullptr;
static NimBLECharacteristic  *pCommandChar   = nullptr;
static NimBLECharacteristic  *pStatusChar    = nullptr;

static uint8_t s_nonce[16];
static bool    s_connected = false;

// ── Helpers ───────────────────────────────────────────────────────────────────

static void refreshNonce() {
    uint32_t r0 = esp_random();
    uint32_t r1 = esp_random();
    uint32_t r2 = esp_random();
    uint32_t r3 = esp_random();
    memcpy(s_nonce,      &r0, 4);
    memcpy(s_nonce +  4, &r1, 4);
    memcpy(s_nonce +  8, &r2, 4);
    memcpy(s_nonce + 12, &r3, 4);
}

// ── Server callbacks ──────────────────────────────────────────────────────────

class ServerCallbacks : public NimBLEServerCallbacks {
    void onConnect(NimBLEServer *pSrv) override {
        s_connected = true;
        refreshNonce();
        pNonceChar->setValue(s_nonce, sizeof(s_nonce));
        pNonceChar->notify();
    }

    void onDisconnect(NimBLEServer *pSrv) override {
        s_connected = false;
        // Restart advertising so the device is discoverable again.
        NimBLEDevice::startAdvertising();
    }
};

// ── Command characteristic write callback ─────────────────────────────────────

class CommandCallbacks : public NimBLECharacteristicCallbacks {
    void onWrite(NimBLECharacteristic *pChar) override {
        NimBLEAttValue val = pChar->getValue();

        bool ok = Auth::verify(s_nonce, sizeof(s_nonce),
                               val.data(), val.size());

        uint8_t status = ok ? 0x01 : 0x00;
        pStatusChar->setValue(&status, 1);
        pStatusChar->notify();

        if (ok) {
            onGarageOpen();
        }

        // Disconnect after each attempt (success or failure).
        if (pServer->getConnectedCount() > 0) {
            pServer->disconnect(pServer->getPeerInfo(0).getConnHandle());
        }
    }
};

// ── Public API ────────────────────────────────────────────────────────────────

namespace BleGarage {

void init() {
    NimBLEDevice::init(DEVICE_NAME);
    NimBLEDevice::setPower(ESP_PWR_LVL_P9);

    pServer = NimBLEDevice::createServer();
    pServer->setCallbacks(new ServerCallbacks());

    NimBLEService *pService = pServer->createService(SERVICE_UUID);

    pNonceChar = pService->createCharacteristic(
        NONCE_CHAR_UUID,
        NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY);

    pCommandChar = pService->createCharacteristic(
        COMMAND_CHAR_UUID,
        NIMBLE_PROPERTY::WRITE);
    pCommandChar->setCallbacks(new CommandCallbacks());

    pStatusChar = pService->createCharacteristic(
        STATUS_CHAR_UUID,
        NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY);

    pService->start();
}

void startAdvertising() {
    NimBLEAdvertising *pAdv = NimBLEDevice::getAdvertising();
    pAdv->addServiceUUID(SERVICE_UUID);
    pAdv->setMinInterval(160);  // 100 ms in 0.625 ms units
    pAdv->setMaxInterval(320);  // 200 ms
    NimBLEDevice::startAdvertising();
}

void stopAdvertising() {
    NimBLEDevice::stopAdvertising();
}

bool isConnected() {
    return s_connected;
}

} // namespace BleGarage
