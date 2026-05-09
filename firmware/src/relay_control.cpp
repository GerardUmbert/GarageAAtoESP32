#include "relay_control.h"
#include "config.h"
#include <Arduino.h>

namespace RelayControl {

static constexpr int RELAY_ON  = RELAY_ACTIVE_LOW ? LOW  : HIGH;
static constexpr int RELAY_OFF = RELAY_ACTIVE_LOW ? HIGH : LOW;

void init() {
    pinMode(RELAY_PIN, OUTPUT);
    digitalWrite(RELAY_PIN, RELAY_OFF);
}

void pulse(uint32_t duration_ms) {
    digitalWrite(RELAY_PIN, RELAY_ON);
    delay(duration_ms);
    digitalWrite(RELAY_PIN, RELAY_OFF);
}

} // namespace RelayControl
