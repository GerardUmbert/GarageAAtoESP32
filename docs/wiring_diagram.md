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
- No changes needed in `config.h` — `RELAY_PIN 26` and `RELAY_ACTIVE_LOW true` still apply (GPIO goes HIGH to trigger, transistor inverts nothing; set `RELAY_ACTIVE_LOW false` if your transistor logic is inverted).

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
- Mount in an IP65 waterproof junction box for outdoor use. Route wires through cable glands.
