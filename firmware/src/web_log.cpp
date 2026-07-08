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

static const uint32_t MAX_ENTRIES = 30;
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
        case 4: return "Watch";
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

    uint32_t timestamps[MAX_ENTRIES] = {};
    uint8_t  reasons[MAX_ENTRIES]    = {};
    char     models[MAX_ENTRIES][33] = {};

    for (uint32_t i = 0; i < count; i++) {
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

    size_t bufSize = 6144 + count * 256;
    char *html = (char *)malloc(bufSize);
    if (!html) return nullptr;

    int pos = 0;

    // ── Head ──────────────────────────────────────────────────────────────────
    pos += snprintf(html + pos, bufSize - pos,
        "<!DOCTYPE html><html lang='en'><head>"
        "<meta charset='utf-8'>"
        "<meta name='viewport' content='width=device-width,initial-scale=1'>"
        "<meta http-equiv='refresh' content='30'>"
        "<title>%s — Log</title>"
        "<style>"
        "*,*::before,*::after{box-sizing:border-box;margin:0;padding:0}"
        "html,body{height:100%%}"
        "body{"
          "background:#0A0C0E;color:#F3F5F7;"
          "font-family:ui-sans-serif,system-ui,-apple-system,sans-serif;"
          "font-size:15px;line-height:1.5;"
          "-webkit-font-smoothing:antialiased"
        "}"
        "::-webkit-scrollbar{width:4px}"
        "::-webkit-scrollbar-track{background:#0A0C0E}"
        "::-webkit-scrollbar-thumb{background:#2AD4A3;border-radius:2px}"

        /* layout */
        ".layout{display:flex;min-height:100vh}"
        ".sidebar{"
          "width:200px;flex-shrink:0;"
          "background:#14181C;"
          "border-right:1px solid rgba(255,255,255,0.06);"
          "padding:24px 0;"
          "position:sticky;top:0;height:100vh;overflow-y:auto"
        "}"
        ".main{flex:1;padding:32px 24px;min-width:0}"

        /* sidebar */
        ".sidebar-title{"
          "font-size:11px;font-weight:600;letter-spacing:.1em;"
          "color:#5A6169;text-transform:uppercase;"
          "padding:0 20px 12px"
        "}"
        ".sidebar a{"
          "display:block;padding:9px 20px;"
          "color:#8A939C;text-decoration:none;font-size:13px;"
          "border-left:2px solid transparent;transition:all .15s"
        "}"
        ".sidebar a:hover{color:#F3F5F7;background:rgba(255,255,255,0.04)}"
        ".sidebar a.active{color:#2AD4A3;border-left-color:#2AD4A3;background:rgba(42,212,163,0.07)}"

        /* header */
        ".page-title{font-size:24px;font-weight:700;letter-spacing:-.5px;margin-bottom:4px}"
        ".page-sub{color:#8A939C;font-size:13px;margin-bottom:28px}"

        /* stats row */
        ".stats{display:flex;gap:12px;margin-bottom:28px;flex-wrap:wrap}"
        ".stat{"
          "background:#14181C;border:1px solid rgba(255,255,255,0.06);"
          "border-radius:12px;padding:14px 18px;min-width:100px"
        "}"
        ".stat-val{font-size:22px;font-weight:700;color:#2AD4A3}"
        ".stat-lbl{font-size:11px;color:#5A6169;text-transform:uppercase;letter-spacing:.08em;margin-top:2px}"

        /* table */
        ".card{"
          "background:#14181C;border:1px solid rgba(255,255,255,0.06);"
          "border-radius:16px;overflow:hidden"
        "}"
        "table{width:100%%;border-collapse:collapse}"
        "thead th{"
          "padding:12px 16px;text-align:left;"
          "font-size:11px;font-weight:600;letter-spacing:.08em;text-transform:uppercase;"
          "color:#5A6169;border-bottom:1px solid rgba(255,255,255,0.06)"
        "}"
        "tbody tr{border-bottom:1px solid rgba(255,255,255,0.04);transition:background .1s}"
        "tbody tr:last-child{border-bottom:none}"
        "tbody tr:hover{background:rgba(255,255,255,0.03)}"
        "td{padding:13px 16px;font-size:13px;color:#F3F5F7}"
        "td.dim{color:#8A939C}"
        "td.mono{font-family:ui-monospace,monospace;font-size:12px;color:#8A939C}"
        ".badge{"
          "display:inline-flex;align-items:center;gap:5px;"
          "padding:3px 9px;border-radius:999px;font-size:12px;font-weight:500"
        "}"
        ".badge-ok{background:rgba(42,212,163,0.12);color:#2AD4A3}"
        ".badge-fail{background:rgba(255,100,80,0.12);color:#ff7060}"
        ".empty{text-align:center;padding:48px 16px;color:#5A6169;font-size:13px}"

        /* mobile */
        "@media(max-width:600px){"
          ".layout{flex-direction:column}"
          ".sidebar{width:100%%;height:auto;position:static;border-right:none;"
            "border-bottom:1px solid rgba(255,255,255,0.06);padding:16px 0;"
            "display:flex;flex-wrap:wrap;gap:0}"
          ".sidebar-title{width:100%%;padding:0 16px 8px}"
          ".sidebar a{padding:8px 14px;border-left:none;border-bottom:2px solid transparent}"
          ".sidebar a.active{border-left-color:transparent;border-bottom-color:#2AD4A3}"
          ".main{padding:20px 16px}"
          "thead th:nth-child(4){display:none}"
          "td:nth-child(4){display:none}"
        "}"
        "</style>"
        "<script>"
        "function filter(dev,el){"
          "document.querySelectorAll('.sidebar a').forEach(a=>a.classList.remove('active'));"
          "el.classList.add('active');"
          "var rows=document.querySelectorAll('tbody tr[data-dev]');"
          "var vis=0;"
          "rows.forEach(function(r){"
            "var show=dev===''||r.dataset.dev===dev;"
            "r.style.display=show?'':'none';"
            "if(show)vis++;"
          "});"
          "document.getElementById('vis').textContent=vis;"
        "}"
        "</script>"
        "</head><body>",
        DEVICE_NAME);

    // ── Count successes, failures, unique models ──────────────────────────────
    uint32_t nOk = 0, nFail = 0;
    // Collect unique non-empty models (max 10 unique devices)
    char uniqueModels[10][33] = {};
    uint32_t uniqueCounts[10] = {};
    uint8_t  nUnique = 0;

    for (uint32_t i = 0; i < count; i++) {
        if (reasons[i] == 0) { nFail++; continue; }
        nOk++;
        if (models[i][0] == '\0') continue;
        bool found = false;
        for (uint8_t u = 0; u < nUnique; u++) {
            if (strncmp(uniqueModels[u], models[i], 32) == 0) {
                uniqueCounts[u]++;
                found = true;
                break;
            }
        }
        if (!found && nUnique < 10) {
            strncpy(uniqueModels[nUnique], models[i], 32);
            uniqueModels[nUnique][32] = '\0';
            uniqueCounts[nUnique] = 1;
            nUnique++;
        }
    }

    // ── Sidebar ───────────────────────────────────────────────────────────────
    pos += snprintf(html + pos, bufSize - pos,
        "<div class='layout'>"
        "<nav class='sidebar'>"
        "<div class='sidebar-title'>Devices</div>"
        "<a href='#' class='active' onclick='filter(\"\",this);return false;'>"
          "All &nbsp;<span style='color:#5A6169'>%lu</span>"
        "</a>",
        (unsigned long)count);

    for (uint8_t u = 0; u < nUnique; u++) {
        pos += snprintf(html + pos, bufSize - pos,
            "<a href='#' onclick='filter(\"%s\",this);return false;'>"
              "%s &nbsp;<span style='color:#5A6169'>%lu</span>"
            "</a>",
            uniqueModels[u], uniqueModels[u], (unsigned long)uniqueCounts[u]);
    }

    pos += snprintf(html + pos, bufSize - pos, "</nav>");

    // ── Main ──────────────────────────────────────────────────────────────────
    pos += snprintf(html + pos, bufSize - pos,
        "<main class='main'>"
        "<div class='page-title'>%s</div>"
        "<div class='page-sub'>Event log &nbsp;&#8231;&nbsp; auto-refreshes every 30 s &nbsp;&#8231;&nbsp; times shown in UTC (webhook opens) &mdash; the ESP32 has no timezone of its own</div>"
        "<div class='stats'>"
          "<div class='stat'><div class='stat-val' id='vis'>%lu</div><div class='stat-lbl'>Showing</div></div>"
          "<div class='stat'><div class='stat-val'>%lu</div><div class='stat-lbl'>Opened</div></div>"
          "<div class='stat'><div class='stat-val'>%lu</div><div class='stat-lbl'>Auth fails</div></div>"
        "</div>"
        "<div class='card'>"
        "<table>"
        "<thead><tr>"
          "<th>Date / Time</th><th>Result</th><th>Reason</th><th>Device</th>"
        "</tr></thead>"
        "<tbody>",
        DEVICE_NAME,
        (unsigned long)count,
        (unsigned long)nOk,
        (unsigned long)nFail);

    if (count == 0) {
        pos += snprintf(html + pos, bufSize - pos,
            "<tr><td colspan='4' class='empty'>No events yet</td></tr>");
    }

    for (uint32_t i = 0; i < count; i++) {
        bool isFailure = (reasons[i] == 0);
        char timeBuf[32];
        formatTime(timestamps[i], timeBuf, sizeof(timeBuf));

        pos += snprintf(html + pos, bufSize - pos,
            "<tr data-dev='%s'>"
            "<td class='mono'>%s</td>"
            "<td><span class='badge %s'>%s</span></td>"
            "<td class='dim'>%s</td>"
            "<td class='dim'>%s</td>"
            "</tr>",
            isFailure ? "" : models[i],
            timeBuf,
            isFailure ? "badge-fail" : "badge-ok",
            isFailure ? "&#x26A0; Auth fail" : "&#x2713; Opened",
            isFailure ? "—" : reasonLabel(reasons[i]),
            isFailure ? "—" : models[i]);
    }

    snprintf(html + pos, bufSize - pos,
        "</tbody></table></div>"
        "</main></div>"
        "</body></html>");

    return html;
}

} // namespace WebLog
