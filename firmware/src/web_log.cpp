#include "web_log.h"
#include "config.h"
#include <Preferences.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <time.h>

// ── Ring buffer layout ────────────────────────────────────────────────────────
// NVS namespace "glog". Keys:
//   "head"   uint32  – index of the next slot to write (0‥MAX_ENTRIES-1)
//   "count"  uint32  – number of valid entries (0‥MAX_ENTRIES)
//   "eN_t"   uint32  – entry N timestamp (Unix)
//   "eN_r"   uint8   – entry N reason (0=failure, 1=manual, 2=geofence, 3=voice)
//   "eN_m"   string  – entry N model (success only; empty string for failures)

static const uint32_t MAX_ENTRIES = 50;
static const char *NS = "glog";

static Preferences prefs;

static void writeEntry(uint32_t idx, uint32_t timestamp, uint8_t reason, const char *model) {
    char key[8];

    snprintf(key, sizeof(key), "e%lu_t", (unsigned long)idx);
    prefs.putUInt(key, timestamp);

    snprintf(key, sizeof(key), "e%lu_r", (unsigned long)idx);
    prefs.putUChar(key, reason);

    snprintf(key, sizeof(key), "e%lu_m", (unsigned long)idx);
    prefs.putString(key, model ? model : "");
}

static void append(uint32_t timestamp, uint8_t reason, const char *model) {
    prefs.begin(NS, false);
    uint32_t head  = prefs.getUInt("head",  0);
    uint32_t count = prefs.getUInt("count", 0);

    writeEntry(head, timestamp, reason, model);

    head = (head + 1) % MAX_ENTRIES;
    if (count < MAX_ENTRIES) count++;

    prefs.putUInt("head",  head);
    prefs.putUInt("count", count);
    prefs.end();
}

// ── Public API ────────────────────────────────────────────────────────────────

namespace WebLog {

void init() {
    // Nothing to initialise — Preferences is lazy-opened per call.
}

void appendSuccess(uint32_t timestamp, OpenReason reason, const char *model) {
    char trimmed[33] = {};
    if (model) {
        strncpy(trimmed, model, 32);
        trimmed[32] = '\0';
    }
    append(timestamp, static_cast<uint8_t>(reason), trimmed);
}

void appendFailure(uint32_t timestamp) {
    append(timestamp, 0, "");
}

// ── HTML builder ──────────────────────────────────────────────────────────────

static const char *reasonLabel(uint8_t r) {
    switch (r) {
        case 1: return "Manual";
        case 2: return "Geofence";
        case 3: return "Voice";
        default: return "—";
    }
}

static void formatTime(uint32_t ts, char *buf, size_t len) {
    if (ts == 0) {
        snprintf(buf, len, "—");
        return;
    }
    time_t t = (time_t)ts;
    struct tm *tm = gmtime(&t);
    strftime(buf, len, "%Y-%m-%d %H:%M UTC", tm);
}

char *buildHtml() {
    prefs.begin(NS, true);
    uint32_t head  = prefs.getUInt("head",  0);
    uint32_t count = prefs.getUInt("count", 0);

    // Read entries newest-first into parallel arrays
    uint32_t timestamps[MAX_ENTRIES] = {};
    uint8_t  reasons[MAX_ENTRIES]    = {};
    char     models[MAX_ENTRIES][33] = {};

    for (uint32_t i = 0; i < count; i++) {
        // Newest entry is at (head - 1), oldest at (head - count), wrapping
        uint32_t idx = (head + MAX_ENTRIES - 1 - i) % MAX_ENTRIES;
        char key[8];

        snprintf(key, sizeof(key), "e%lu_t", (unsigned long)idx);
        timestamps[i] = prefs.getUInt(key, 0);

        snprintf(key, sizeof(key), "e%lu_r", (unsigned long)idx);
        reasons[i] = prefs.getUChar(key, 0);

        snprintf(key, sizeof(key), "e%lu_m", (unsigned long)idx);
        String m = prefs.getString(key, "");
        strncpy(models[i], m.c_str(), 32);
        models[i][32] = '\0';
    }
    prefs.end();

    // Build rows
    // Each row ~150 chars, plus header ~1200 chars
    size_t bufSize = 1400 + count * 200;
    char *html = (char *)malloc(bufSize);
    if (!html) return nullptr;

    int pos = 0;
    pos += snprintf(html + pos, bufSize - pos,
        "<!DOCTYPE html><html><head>"
        "<meta charset='utf-8'>"
        "<meta name='viewport' content='width=device-width,initial-scale=1'>"
        "<meta http-equiv='refresh' content='30'>"
        "<title>Garage Log</title>"
        "<style>"
        "body{font-family:sans-serif;background:#111;color:#eee;margin:0;padding:16px}"
        "h1{font-size:1.2em;margin:0 0 4px}"
        "p.sub{color:#888;font-size:.8em;margin:0 0 16px}"
        "table{width:100%%;border-collapse:collapse;font-size:.85em}"
        "th{text-align:left;padding:8px 10px;border-bottom:1px solid #333;color:#aaa;font-weight:normal}"
        "td{padding:8px 10px;border-bottom:1px solid #222}"
        "tr.fail td{color:#f87}"
        "tr.ok td{color:#eee}"
        "</style></head><body>"
        "<h1>Garage Log</h1>"
        "<p class='sub'>Device: %s &nbsp;|&nbsp; %lu entries</p>"
        "<table>"
        "<tr><th>Date / Time</th><th>Result</th><th>Reason</th><th>Device</th></tr>",
        DEVICE_NAME, (unsigned long)count);

    for (uint32_t i = 0; i < count; i++) {
        bool isFailure = (reasons[i] == 0);
        char timeBuf[32];
        formatTime(timestamps[i], timeBuf, sizeof(timeBuf));

        pos += snprintf(html + pos, bufSize - pos,
            "<tr class='%s'>"
            "<td>%s</td>"
            "<td>%s</td>"
            "<td>%s</td>"
            "<td>%s</td>"
            "</tr>",
            isFailure ? "fail" : "ok",
            timeBuf,
            isFailure ? "&#x26A0; Auth fail" : "&#x2713; Opened",
            isFailure ? "—" : reasonLabel(reasons[i]),
            isFailure ? "—" : models[i]);
    }

    if (count == 0) {
        pos += snprintf(html + pos, bufSize - pos,
            "<tr><td colspan='4' style='color:#555;text-align:center;padding:24px'>"
            "No events yet</td></tr>");
    }

    snprintf(html + pos, bufSize - pos,
        "</table></body></html>");

    return html;
}

} // namespace WebLog
