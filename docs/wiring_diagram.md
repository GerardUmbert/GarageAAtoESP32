# Wiring Diagram

## Option A — Relay module *(recommended)*

A relay module between the ESP32 and the fob's button pads. When triggered, the relay closes and shorts the two pads — identical to pressing the button. Beginner-friendly: the module's onboard transistor driver handles everything, no biasing knowledge needed.

**Recommended relay:** Songle SRD-03VDC-SL-C (3V coil). Works directly with the ESP32's 3.3V GPIO and power rail — no transistor driver or 5V supply needed.

### Relay to ESP32

```
ESP32 GPIO 26  ──────▶  Relay module IN
ESP32 GND      ──────▶  Relay module GND
ESP32 3.3V     ──────▶  Relay module VCC
```

### Relay to fob button pads

Open the fob and find the two pads connected to the button. Solder wires to both pads, then connect to the relay:

```
Fob button pad A  ──────▶  Relay COM
Fob button pad B  ──────▶  Relay NO  (Normally Open)
```

When the relay closes, it shorts the two fob pads exactly as a physical button press would.

In `config.h`, set `TRIGGER_MODE MODE_RELAY_HIGH` (most modules) or `MODE_RELAY` (older opto-isolated modules that trigger on LOW).

### Power-rail switching variant *(renter-friendly, no fob modification)*

Instead of shorting the button pads, use the relay to switch the fob's battery ground rail. A 3D-printed clip (or foam wedge) holds the fob button depressed permanently — the fob fires every time it gets power.

```
ESP32 GPIO 26  ──────▶  Relay IN
ESP32 GND      ──────▶  Relay GND
ESP32 3.3V     ──────▶  Relay VCC

Relay COM  ──────▶  CR2032 − (external battery, negative terminal)
Relay NO   ──────▶  Fob battery − contact (via copper tape insert)
CR2032 +   ──────▶  Fob battery + contact (via copper tape insert)
```

Both battery contacts are copper tape discs slid into the fob's battery slot — the real CR2032 sits outside the fob. No soldering inside the fob. Fully reversible — remove the inserts and the clip, return the fob exactly as received.

Same firmware config as above (`MODE_RELAY_HIGH`), same wiring to ESP32. Only the load side changes.

## Option B — Transistor *(advanced)*

A single NPN transistor shorts the fob button pads directly. Lower power draw than a relay (~20 mA vs ~60–80 mA) but requires understanding transistor biasing.

```
ESP32 GPIO 26 ──[1 kΩ]──▶ Base (B)
                            Collector (C) ──▶ Fob button pad A
ESP32 GND     ──────────▶ Emitter (E)
                            Emitter (E)  ──▶ Fob button pad B
```

When GPIO 26 goes HIGH the transistor saturates and shorts the two fob pads.

**Component notes:**
- Any small-signal NPN BJT works. 2N2222 and BC547 are common (~$0.10).
- 1 kΩ base resistor limits base current to ~3 mA from the 3.3V GPIO pin.
- In `config.h`, set `TRIGGER_MODE MODE_TRANSISTOR` and `TRIGGER_PIN 26`.


---

## Power Options

### Option 0 — Wall outlet + USB charger (easiest of all)
```
Wall outlet  ──▶  Any USB phone charger (5V ≥ 500mA)  ──▶  ESP32 USB port
```
If your communal garage has a wall outlet nearby, this is the simplest possible setup — just a phone charger and a USB cable. No batteries, no solar, no maintenance. The ESP32 draws so little power (~19 mAh/day) that any basic charger handles it with ease.

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
At ~19 mAh/day a 10 000 mAh bank lasts ~12 months before needing a recharge.

### Option 3 — 18650 LiPo + TP4056 + solar (DIY, most flexible)
```
Solar panel +/−  ──▶  TP4056 IN+/IN−
TP4056 BAT+/BAT− ──▶  18650 cells in parallel
TP4056 OUT+/OUT− ──▶  5V step-up boost converter IN
Boost converter  ──▶  ESP32 5V pin + GND
```
Most control over capacity and charging. Good for custom enclosures or when you want to size the battery precisely.

## Notes
- **Relay option (recommended)**: SRD-03VDC-SL-C coil draws ~60–80 mA; ensure your power source can supply ≥ 300 mA peak. Use a flyback diode if using a bare coil (most relay modules have one built in).
- **Transistor option**: peak current draw drops to ~20 mA (no relay coil) — better for battery life if power budget is critical.
- Mount in an IP65 waterproof junction box for outdoor use. Route wires through cable glands.
