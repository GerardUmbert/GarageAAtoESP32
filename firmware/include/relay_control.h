#pragma once
#include <stdint.h>

namespace RelayControl {
    void init();
    void pulse(uint32_t duration_ms);
}
