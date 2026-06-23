#include <Arduino.h>
#include "config.h"
#include "ble_garage.h"
#include "relay_control.h"

#ifdef ENABLE_WEBLOG
#include <WiFi.h>
#include <DNSServer.h>
#include <WebServer.h>
#include "web_log.h"
#endif

static volatile bool g_openRequested = false;

void onGarageOpen() {
    g_openRequested = true;
}

// ── AP helpers ────────────────────────────────────────────────────────────────

#ifdef ENABLE_WEBLOG

static DNSServer  dnsServer;
static WebServer  webServer(80);

static void buildApSsid(char *buf, size_t len) {
    uint8_t mac[6];
    WiFi.macAddress(mac);
    snprintf(buf, len, "%s_%02X%02X%02X",
             DEVICE_NAME, mac[3], mac[4], mac[5]);
}

static void startAP() {
    char ssid[64];
    buildApSsid(ssid, sizeof(ssid));

    WiFi.softAP(ssid, nullptr, WIFI_AP_CHANNEL);

#if CORE_DEBUG_LEVEL > 0
    Serial.print("AP started: ");
    Serial.println(ssid);
    Serial.print("AP IP: ");
    Serial.println(WiFi.softAPIP());
#endif

    dnsServer.start(53, "*", WiFi.softAPIP());

    webServer.on("/", HTTP_GET, []() {
        char *html = WebLog::buildHtml();
        if (html) {
            webServer.send(200, "text/html", html);
            free(html);
        } else {
            webServer.send(500, "text/plain", "Out of memory");
        }
    });

    webServer.onNotFound([]() {
        webServer.sendHeader("Location", "http://192.168.4.1/", true);
        webServer.send(302, "text/plain", "");
    });

    webServer.begin();
    WebLog::init();
}

#endif // ENABLE_WEBLOG

// ── Deep sleep helpers ────────────────────────────────────────────────────────

static void goToSleep() {
#ifdef ENABLE_WEBLOG
    webServer.stop();
    dnsServer.stop();
    WiFi.softAPdisconnect(true);
#endif
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
#ifdef ENABLE_WEBLOG
    startAP();
#endif
    BleGarage::init();
    BleGarage::startAdvertising();
}

void loop() {
#ifdef ENABLE_WEBLOG
    dnsServer.processNextRequest();
    webServer.handleClient();
#endif

    if (g_openRequested) {
        g_openRequested = false;
        RelayControl::pulse(RELAY_PULSE_MS);
        delay(200);
        goToSleep();
    }

    static uint32_t advStart = millis();
    if (!BleGarage::isConnected()) {
#ifdef ENABLE_WEBLOG
        if (WiFi.softAPgetStationNum() == 0 &&
            millis() - advStart > (uint32_t)ADV_TIMEOUT_S * 1000UL) {
#else
        if (millis() - advStart > (uint32_t)ADV_TIMEOUT_S * 1000UL) {
#endif
            goToSleep();
        }
    } else {
        advStart = millis();
    }

    delay(10);
}
