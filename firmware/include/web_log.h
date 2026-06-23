#pragma once
#include <stdint.h>
#include <stddef.h>
#include "config.h"


namespace WebLog {
    // Call once in setup() after Wi-Fi AP is up.
    void init();

    // Append a successful open event. model is null-terminated, capped internally.
    void appendSuccess(uint32_t timestamp, OpenReason reason, const char *model);

    // Append a failed auth attempt (wrong PIN).
    void appendFailure(uint32_t timestamp);

    // Returns a heap-allocated HTML string for the log page.
    // Caller must free() it.
    char *buildHtml();
}
