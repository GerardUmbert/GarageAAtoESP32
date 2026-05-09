# Wiring Diagram

## Option A — Transistor (recommended, simpler)

A single NPN transistor (e.g., 2N2222, BC547, or 2N3904) shorts the fob button pads when triggered by the ESP32. No relay module needed.

```
ESP32 GPIO 26 ──[1 kΩ]──▶ Base (B)
                           Collector (C) ──▶ Fob button pad A
ESP32 GND     ─────────▶ Emitter (E)
                           Emitter (E)  ──▶ Fob button pad B
```

When GPIO 26 goes HIGH, the transistor saturates and shorts the two fob pads — identical to pressing the button.

**Component notes:**
- Any small-signal NPN BJT works. 2N2222 and BC547 are common and cheap (~$0.10).
- 1 kΩ base resistor limits base current to ~3 mA from the 3.3 V GPIO pin.
- Fob button pads carry only microamps at ~3 V — well within any small-signal transistor's ratings.
- In `config.h`, set `TRIGGER_MODE MODE_TRANSISTOR` and `TRIGGER_PIN 26`.

## Option B — Relay module

Provides galvanic isolation between the ESP32 and fob circuits. Overkill for a 3 V fob but familiar to beginners.

### Relay to ESP32

```
ESP32 GPIO 26  ──────▶  Relay module IN
ESP32 GND      ──────▶  Relay module GND
ESP32 3.3V/5V  ──────▶  Relay module VCC
```

### Relay to Garage Fob

Open the fob and find the two pads connected to the button.
Solder wires to both pads, then connect to the relay:

```
Fob button pad A  ──────▶  Relay COM
Fob button pad B  ──────▶  Relay NO  (Normally Open)
```

When the relay closes (triggered by ESP32), it shorts the two fob pads exactly as a physical button press would.

In `config.h`, set `TRIGGER_MODE MODE_RELAY`.

## Option C — Capacitive pulse on a fingerbot (no soldering, renter-friendly)

If you can't or don't want to open the fob — for instance, the fob is rented from the parking management and has to be returned untouched — pair the ESP32 with an **Adaprox / Tuya Fingerbot Plus** (model `ADFBB531` or equivalent with a capacitive top button).

The ESP32 doesn't talk to the fingerbot over BLE. Instead it fakes a finger touch on the fingerbot's capacitive top button by briefly driving a small conductive pad pressed against the button. The fingerbot then mechanically presses the fob.

```
ESP32 GPIO 26  ──────▶  Conductive pad (1 cm² copper tape or foil)
                                │
                                │  pressed firmly against
                                ▼
                       Fingerbot's top capacitive button

Fingerbot arm ──▶  physically presses the fob button
```

**How it works:** capacitive sensors detect a change in the local electric field caused by a grounded object (your finger) approaching. Briefly switching the GPIO from high-Z input to OUTPUT HIGH injects charge into the field, mimicking that change. After the pulse the firmware returns the pin to high-Z so the sensor sees the touch *and release* edges it expects.

**Materials:**
- 1 × Adaprox Fingerbot Plus (or compatible Tuya capacitive-top-button fingerbot)
- ~1 cm² of copper tape (or aluminum foil + tape)
- 1 × thin wire (any gauge)
- Double-sided tape to mount everything

**Assembly:**
1. Stick the fingerbot to the wall over the fob's button using the included double-sided tape, exactly as the fingerbot's own manual describes.
2. Solder or twist a thin wire to a ~1 cm² piece of copper tape. The pad is the electrode; the wire is just the lead — no transistor or resistor needed.
3. Stick the copper pad **directly on top of the fingerbot's capacitive top button**, pressed flat with no air gap. A thin layer of tape on top is fine; an air gap kills the coupling.
4. Run the wire from the pad to ESP32 GPIO 26.
5. In `config.h`, set `TRIGGER_MODE MODE_CAP_PULSE`.

**Tuning:**
- Default `RELAY_PULSE_MS 500` is conservative. The fingerbot's capacitive button typically registers in 100–300 ms; 500 ms always works.
- If a press isn't being registered: increase `RELAY_PULSE_MS`, or enlarge the copper pad, or add a short wire from ESP32 GND to the fingerbot's chassis (improves the ground reference the sensor uses).
- If the fingerbot triggers spuriously between presses: shrink the pad slightly, or move it so it covers only the capacitive area and not the surrounding casing.

**Trade-offs vs Options A/B:**
- ✅ No soldering on the fob — the fob is unmodified and can be returned.
- ✅ Reversible — peel everything off when you move out.
- ✅ Same firmware, same auth, same AA flow.
- ⚠️ Adds the fingerbot's battery to maintain (USB-C, recharge every few months depending on use).
- ⚠️ Latency adds ~0.5–1 s vs the direct wired path because the fingerbot has its own internal trigger-to-arm cycle.

## Power Options

### Option 0 — Wall outlet + USB charger (easiest of all)
```
Wall outlet  ──▶  Any USB phone charger (5V ≥ 500mA)  ──▶  ESP32 USB port
```
If your communal garage has a wall outlet nearby, this is the simplest possible setup — just a phone charger and a USB cable. No batteries, no solar, no maintenance. The ESP32 draws so little power (~17 mAh/day) that any basic charger handles it with ease.

### Option 1 — Solar + USB battery pack (no outlet, low maintenance)
```
Solar panel  ──▶  Battery pack solar input
Battery pack USB ──▶  ESP32 USB port
```
Many off-the-shelf solar battery packs (e.g., Hiluckey 25000mAh solar) accept solar in and output USB.
Self-sustaining — the solar panel keeps the battery topped up indefinitely. Good choice for outdoor or poorly lit garages with no socket.

### Option 2 — USB Power Bank (no outlet, no solar)
```
Power bank USB-A  ──▶  ESP32 dev board Micro-USB / USB-C
```
Choose a power bank with always-on / low-current mode (e.g., Anker A1263 or similar).
Avoid banks that auto-shut off — they will cut power when ESP32 is in deep sleep (very low current draw).
At ~17 mAh/day a 10 000 mAh bank lasts ~18 months before needing a recharge.

### Option 3 — 18650 LiPo + TP4056 + solar (DIY, most flexible)
```
Solar panel +/−  ──▶  TP4056 IN+/IN−
TP4056 BAT+/BAT− ──▶  18650 cells in parallel
TP4056 OUT+/OUT− ──▶  5V step-up boost converter IN
Boost converter  ──▶  ESP32 5V pin + GND
```
Most control over capacity and charging. Good for custom enclosures or when you want to size the battery precisely.

## Notes
- **Transistor option**: peak current draw drops to ~20 mA (no relay coil) — better for battery life.
- **Relay option**: coil draws ~60–80 mA; ensure your power source can supply ≥ 300 mA peak. Use a flyback diode if using a bare coil (most relay modules have one built in).
- **Cap-pulse option**: ESP32 peak draw is unchanged (~20 mA during the brief pulse). The fingerbot has its own battery and isn't powered by the ESP32.
- Mount in an IP65 waterproof junction box for outdoor use. Route wires through cable glands.
