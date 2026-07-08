#ifdef ENABLE_HA_WEBHOOK

#include "ha_webhook.h"
#include "config.h"
#ifdef ENABLE_WEBLOG
#include "web_log.h"
#endif
#include <Arduino.h>
#include <WiFi.h>
#include <WebServer.h>
#include <ESPmDNS.h>
#include <string.h>
#include "mbedtls/md.h"

extern void onGarageOpen();

static WebServer server(HA_SERVER_PORT);

// SHA-256(USER_PIN) as lowercase hex, computed once at init.
static char s_tokenHex[65] = {};

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
// Expected token is SHA-256(USER_PIN) as lowercase hex (64 chars).
// Constant-time comparison prevents timing attacks.
// Using the hash means a captured Bearer token cannot be used to authenticate
// over BLE, where the raw PIN is required.

static void computeTokenHex() {
    uint8_t hash[32];
    const uint8_t *pin = reinterpret_cast<const uint8_t *>(USER_PIN);
    size_t pin_len = strlen(USER_PIN);

    mbedtls_md_context_t ctx;
    const mbedtls_md_info_t *info = mbedtls_md_info_from_type(MBEDTLS_MD_SHA256);
    mbedtls_md_init(&ctx);
    mbedtls_md_setup(&ctx, info, 0);
    mbedtls_md_starts(&ctx);
    mbedtls_md_update(&ctx, pin, pin_len);
    mbedtls_md_finish(&ctx, hash);
    mbedtls_md_free(&ctx);

    for (int i = 0; i < 32; i++) {
        snprintf(s_tokenHex + i * 2, 3, "%02x", hash[i]);
    }
}

static bool tokenValid(const String &header) {
    // Expect: "Bearer <sha256hex>"
    if (!header.startsWith("Bearer ")) return false;
    const char *provided = header.c_str() + 7; // skip "Bearer "
    const char *expected = s_tokenHex;          // 64-char hex, always same length

    size_t pLen = strlen(provided);
    // Always compare 64 chars to avoid length timing leak.
    uint8_t diff = (uint8_t)(pLen != 64);
    for (size_t i = 0; i < 64; i++) {
        diff |= (uint8_t)(provided[i] ^ expected[i]);
    }
    return diff == 0;
}

// ── Routes ────────────────────────────────────────────────────────────────────

#ifdef ENABLE_WEBLOG
// NTP sync completes a few seconds after boot; before that, time(nullptr)
// returns a small/garbage epoch. Clamp to 0 so it renders as "—" (same as
// BLE auth failures with no trusted timestamp) instead of a bogus 1970 date.
static uint32_t currentTimeOrUnknown() {
    time_t now = time(nullptr);
    return (now < 1700000000) ? 0 : (uint32_t)now;
}
#endif

static void handleOpen() {
    if (checkRateLimit()) {
        server.send(429, "text/plain", "Too Many Requests");
        return;
    }

    String auth = server.header("Authorization");
    if (!tokenValid(auth)) {
        recordFailure();
#ifdef ENABLE_WEBLOG
        WebLog::appendFailure(currentTimeOrUnknown());
#endif
        server.send(401, "text/plain", "Unauthorized");
        return;
    }

    // Request the relay fire before anything else (HTTP response, NVS log
    // write) — those are network/flash I/O and must not delay the trigger.
    onGarageOpen();
    server.send(200, "text/plain", "OK");
#ifdef ENABLE_WEBLOG
    WebLog::appendSuccess(currentTimeOrUnknown(), OpenReason::MANUAL, "Webhook");
#endif
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
    computeTokenHex();

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

#ifdef ENABLE_WEBLOG
        // Needed so webhook-triggered opens (no client-supplied timestamp,
        // unlike BLE) get a real time in the log instead of epoch 0. Three
        // servers on different operators/anycast networks so an IoT-only
        // VLAN or a single blocked/down provider still has a chance to sync.
        // Fully non-blocking either way — if all three are unreachable (e.g.
        // no internet egress), sync just never completes and
        // currentTimeOrUnknown() keeps returning 0 ("—" in the log) forever.
        configTime(0, 0, "pool.ntp.org", "time.google.com", "time.cloudflare.com");
        WebLog::init();
#endif
        static const char *headerKeys[] = { "Authorization" };
        server.collectHeaders(headerKeys, 1);
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
