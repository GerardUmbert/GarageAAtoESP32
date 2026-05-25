#include "relay_control.h"
#include "config.h"
#include <Arduino.h>

namespace RelayControl {

#if TRIGGER_MODE == MODE_RELAY
    static constexpr int ON_LEVEL  = LOW;
    static constexpr int OFF_LEVEL = HIGH;
#elif TRIGGER_MODE == MODE_TRANSISTOR || TRIGGER_MODE == MODE_RELAY_HIGH
    static constexpr int ON_LEVEL  = HIGH;
    static constexpr int OFF_LEVEL = LOW;
#endif

void init() {
#if TRIGGER_MODE == MODE_CAP_PULSE
    // Idle as high-Z so we don't influence the capacitive sensor between
    // presses. The pin is briefly switched to OUTPUT during pulse().
    pinMode(TRIGGER_PIN, INPUT);
#else
    pinMode(TRIGGER_PIN, OUTPUT);
    digitalWrite(TRIGGER_PIN, OFF_LEVEL);
#endif

#if defined(LED_PIN) && LED_PIN != TRIGGER_PIN
    pinMode(LED_PIN, OUTPUT);
    digitalWrite(LED_PIN, !LED_ON_LEVEL);
#endif
}

void pulse(uint32_t duration_ms) {
#if defined(LED_PIN) && LED_PIN != TRIGGER_PIN
    digitalWrite(LED_PIN, LED_ON_LEVEL);
#endif

#if TRIGGER_MODE == MODE_CAP_PULSE
    // Drive the pad to a fixed level to inject charge into the capacitive
    // sensor's field, mimicking a finger touch. Then release to high-Z so
    // the sensor sees the touch-release edge it expects.
    pinMode(TRIGGER_PIN, OUTPUT);
    digitalWrite(TRIGGER_PIN, HIGH);
    delay(duration_ms);
    pinMode(TRIGGER_PIN, INPUT);
#else
    digitalWrite(TRIGGER_PIN, ON_LEVEL);
    delay(duration_ms);
    digitalWrite(TRIGGER_PIN, OFF_LEVEL);
#endif

#if defined(LED_PIN) && LED_PIN != TRIGGER_PIN
    digitalWrite(LED_PIN, !LED_ON_LEVEL);
#endif
}

} // namespace RelayControl
