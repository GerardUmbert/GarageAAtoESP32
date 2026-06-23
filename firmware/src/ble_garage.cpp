#include "ble_garage.h"
#include "auth.h"
#include "config.h"
#ifdef ENABLE_WEBLOG
#include "web_log.h"
#endif
#include <NimBLEDevice.h>
#include <esp_random.h>
#include <string.h>

static NimBLEServer          *pServer        = nullptr;
static NimBLECharacteristic  *pNonceChar     = nullptr;
static NimBLECharacteristic  *pCommandChar   = nullptr;
static NimBLECharacteristic  *pStatusChar    = nullptr;
static NimBLECharacteristic  *pCapsChar      = nullptr;

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
            // Parse extended payload fields after the 32-byte HMAC:
            //   [32..35] Unix timestamp (big-endian uint32)
            //   [36]     open reason byte
            //   [37..N]  phone model string (null-terminated, max 32 chars)
            uint32_t timestamp = 0;
            OpenReason reason = OpenReason::MANUAL;
            char model[33] = {};

            const uint8_t *p = val.data();
            size_t len = val.size();

            if (len >= 36) {
                timestamp = ((uint32_t)p[32] << 24) |
                            ((uint32_t)p[33] << 16) |
                            ((uint32_t)p[34] <<  8) |
                             (uint32_t)p[35];
            }
            if (len >= 37) {
                uint8_t r = p[36];
                if (r >= 1 && r <= 3) reason = static_cast<OpenReason>(r);
            }
            if (len >= 38) {
                size_t modelLen = len - 37;
                if (modelLen > 32) modelLen = 32;
                memcpy(model, p + 37, modelLen);
                model[modelLen] = '\0';
            }

#ifdef ENABLE_WEBLOG
            WebLog::appendSuccess(timestamp, reason, model);
#endif
            onGarageOpen();
        } else {
#ifdef ENABLE_WEBLOG
            // Wrong PIN — log as auth failure. No trusted timestamp from
            // an unauthenticated sender, so we use 0 (renders as "—").
            WebLog::appendFailure(0);
#endif
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

    pCapsChar = pService->createCharacteristic(
        CAPS_CHAR_UUID,
        NIMBLE_PROPERTY::READ);
    uint8_t caps = 0;
#ifdef ENABLE_WEBLOG
    caps |= CAP_WEBLOG;
#endif
    pCapsChar->setValue(&caps, 1);

    pService->start();
}

void startAdvertising() {
    NimBLEAdvertising *pAdv = NimBLEDevice::getAdvertising();
    pAdv->addServiceUUID(SERVICE_UUID);
    pAdv->setMinInterval(160);  // 100 ms in 0.625 ms units
    pAdv->setMaxInterval(320);  // 200 ms

    // Encode capability flags in manufacturer-specific data:
    //   [0..1] company ID 0xFFFF (reserved/test)
    //   [2]    caps byte (CAP_WEBLOG = 0x01)
    uint8_t caps = 0;
#ifdef ENABLE_WEBLOG
    caps |= CAP_WEBLOG;
#endif
    uint8_t mfr[3] = { 0xFF, 0xFF, caps };
    pAdv->setManufacturerData(std::string(reinterpret_cast<char*>(mfr), sizeof(mfr)));

    NimBLEDevice::startAdvertising();
}

void stopAdvertising() {
    NimBLEDevice::stopAdvertising();
}

bool isConnected() {
    return s_connected;
}

} // namespace BleGarage
