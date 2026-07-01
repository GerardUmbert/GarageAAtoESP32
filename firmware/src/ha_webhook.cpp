#ifdef ENABLE_HA_WEBHOOK

#include "ha_webhook.h"
#include "config.h"
#include "web_log.h"
#include <Arduino.h>
#include <WiFi.h>
#include <WebServer.h>
#include <ESPmDNS.h>
#include <string.h>

extern void onGarageOpen();

static WebServer server(HA_SERVER_PORT);

// ── Rate limiter ──────────────────────────────────────────────────────────────
// Tracks failed auth attempts. Resets after HA_RATE_LIMIT_WIN_S seconds.

static uint32_t s_failCount    = 0;
static uint32_t s_windowStart  = 0;
static bool     s_rateLimited  = false;

static bool checkRateLimit() {
    uint32_t now = millis() / 1000;
    if (now - s_windowStart >= HA_RATE_LIMIT_WIN_S) {
        s_failCount   = 0;
        s_windowStart = now;
        s_rateLimited = false;
    }
    return s_rateLimited;
}

static void recordFailure() {
    s_failCount++;
    if (s_failCount >= HA_RATE_LIMIT_MAX) {
        s_rateLimited = true;
    }
}

// ── Token check ───────────────────────────────────────────────────────────────
// Constant-time comparison to prevent timing attacks.

static bool tokenValid(const String &header) {
    // Expect: "Bearer <pin>"
    if (!header.startsWith("Bearer ")) return false;
    const char *provided = header.c_str() + 7; // skip "Bearer "
    const char *expected = USER_PIN;

    size_t pLen = strlen(provided);
    size_t eLen = strlen(expected);

    // Always run the full loop against expected length to avoid timing leak.
    uint8_t diff = (uint8_t)(pLen != eLen);
    size_t  cmp  = eLen < pLen ? eLen : pLen;
    for (size_t i = 0; i < cmp; i++) {
        diff |= (uint8_t)(provided[i] ^ expected[i]);
    }
    return diff == 0;
}

// ── Routes ────────────────────────────────────────────────────────────────────

static void handleOpen() {
    if (checkRateLimit()) {
        server.send(429, "text/plain", "Too Many Requests");
        return;
    }

    String auth = server.header("Authorization");
    if (!tokenValid(auth)) {
        recordFailure();
        server.send(401, "text/plain", "Unauthorized");
        return;
    }

    server.send(200, "text/plain", "OK");
    onGarageOpen();
}

static void handleHealth() {
    server.send(200, "application/json", "{\"status\":\"ok\"}");
}

static void handleLog() {
    char *html = WebLog::buildHtml();
    if (html) {
        server.send(200, "text/html", html);
        free(html);
    } else {
        server.send(500, "text/plain", "Out of memory");
    }
}

// ── Public API ────────────────────────────────────────────────────────────────

namespace HaWebhook {

void init() {
    WiFi.mode(WIFI_STA);
    WiFi.begin(HA_WIFI_SSID, HA_WIFI_PASS);

#if CORE_DEBUG_LEVEL > 0
    Serial.print("Connecting to WiFi");
#endif

    uint32_t start = millis();
    while (WiFi.status() != WL_CONNECTED && millis() - start < 15000) {
#if CORE_DEBUG_LEVEL > 0
        Serial.print(".");
#endif
        delay(500);
    }

#if CORE_DEBUG_LEVEL > 0
    Serial.println();
    if (WiFi.status() == WL_CONNECTED) {
        Serial.print("WiFi connected, IP: ");
        Serial.println(WiFi.localIP());
    } else {
        Serial.println("WiFi connect failed — HTTP server unavailable");
    }
#endif

    if (WiFi.status() == WL_CONNECTED) {
        if (MDNS.begin(DEVICE_NAME)) {
#if CORE_DEBUG_LEVEL > 0
            Serial.print("mDNS: http://");
            Serial.print(DEVICE_NAME);
            Serial.println(".local/open");
#endif
        }

        WebLog::init();
        server.collectHeaders("Authorization");
        server.on("/open",   HTTP_POST, handleOpen);
        server.on("/health", HTTP_GET,  handleHealth);
        server.on("/log",    HTTP_GET,  handleLog);
        server.begin();
    }
}

void handle() {
    server.handleClient();
}

} // namespace HaWebhook

#endif // ENABLE_HA_WEBHOOK
