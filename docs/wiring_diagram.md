# Wiring Diagram

## Option A — Transistor (recommended, simpler)

A single NPN transistor (e.g., 2N2222, BC547, or 2N3904) shorts the fob button pads when triggered by the ESP32. No relay module needed.

```
ESP32 GPIO 8 ──[1 kΩ]──▶ Base (B)
                           Collector (C) ──▶ Fob button pad A
ESP32 GND     ─────────▶ Emitter (E)
                           Emitter (E)  ──▶ Fob button pad B
```

When GPIO 8 goes HIGH the transistor saturates and shorts the two fob pads — identical to pressing the button.

**Component notes:**
- Any small-signal NPN BJT works. 2N2222 and BC547 are common and cheap (~$0.10).
- 1 kΩ base resistor limits base current to ~3 mA from the 3.3 V GPIO pin.
- Fob button pads carry only microamps at ~3 V — well within any small-signal transistor's ratings.
- In `config.h`, set `TRIGGER_MODE MODE_TRANSISTOR` and `TRIGGER_PIN 8`.

## Option B — Relay module

Provides galvanic isolation between the ESP32 and fob circuits. Overkill for a 3 V fob but familiar to beginners.

### Relay to ESP32

```
ESP32 GPIO 8  ──────▶  Relay module IN
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
ESP32 GPIO 8  ──────▶  Conductive pad (1 cm² copper tape or foil)
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
4. Run the wire from the pad to ESP32 GPIO 8.
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

## Option D — Fake battery + power-rail switching (no soldering, no fob modification, recommended for rented fobs)

This is the cleanest no-solder option when you cannot modify the fob at all. Instead of simulating a button press, the ESP32 controls the fob's entire power rail. The fob button is held down mechanically with a 3D-printed clip, so the fob fires every time it gets power.

**Concept:**
- A fake CR2032 "battery" made from copper tape slides into the fob's battery slot. The real CR2032 sits outside the fob connected by wires — easy to replace without opening anything.
- The ESP32 switches the fob's ground line via a relay or NPN transistor. When the GPIO fires, the fob powers on with the button already pressed, transmits, and powers off again.
- Zero PCB contact. Zero soldering inside the fob. Fully reversible — remove the fake battery insert and the 3D-printed clip and the fob is exactly as you found it.

```
ESP32 GPIO ──[1kΩ]──▶ Base (NPN)   or   ESP32 GPIO ──▶ Relay IN
                        Collector ──▶ Fob battery − wire             ESP32 GND ──▶ Relay GND
ESP32 GND ──────────▶ Emitter                                        Relay NO  ──▶ Fob battery − wire
                                                                      Relay COM ──▶ CR2032 − (external)

CR2032 + (external) ──▶ Fob battery + contact (via copper tape insert)
CR2032 − (external) ──▶ switched by transistor/relay to Fob battery − contact
```

Low-side switching: the transistor/relay interrupts the negative rail. The positive rail is always connected. Standard, safe pattern.

**Materials:**
- 1 × NPN transistor (2N2222 / BC547) + 1 kΩ resistor, **or** 1 × 5V relay module
- Thin copper tape or copper foil cut to the CR2032 footprint (~20 mm diameter) × 2 pieces (for + and − contacts inside the fob slot)
- 2 × thin wires soldered to the copper tape pads before insertion
- 1 × CR2032 held externally (e.g., taped to the outside of the fob body, or in a small CR2032 holder)
- 1 × 3D-printed button press clip — a small bracket that physically depresses the fob's button and holds it down permanently. Model to suit your fob.

**Assembly:**
1. Cut two copper tape discs to the CR2032 size. Solder a short wire to each before sticking them in — the flat inside the fob slot makes post-insertion soldering very hard.
2. Slide the two copper discs into the fob's battery slot, one on each contact face (+ and −). They replace the battery.
3. Run both wires out of the fob (through the seam or a tiny routed notch if needed). The fob body does not need to be opened further.
4. Connect the + wire directly to the CR2032 + terminal (external). Connect the − wire to the Collector of the NPN (or relay NO contact). Connect the CR2032 − terminal to the Emitter (or relay COM).
5. Print and fit the button press clip so the fob's button is held depressed at all times.
6. Wire the transistor base (or relay IN) to an ESP32 GPIO through a 1 kΩ resistor.
7. In `config.h`, set `TRIGGER_MODE MODE_TRANSISTOR` (same low-side pulse behavior) and set `TRIGGER_PIN` to your chosen GPIO.

**Verifying it works:**
Before wiring to the ESP32, briefly touch the CR2032 − wire to GND by hand — the fob should transmit (LED flash, gate responds). If not, check the copper disc contacts are making firm contact with both battery slot faces.

**One thing to test:**
Some fobs require a clean power-on to register a button press — if the fob's MCU boots and immediately sees the button held, it may ignore it. Test by cycling power a few times rapidly: if it fires reliably every cycle, you're fine. If it occasionally misses, add a small capacitor (100 µF) across the fob's power rails to give the MCU a slightly softer power ramp.

**Trade-offs:**
- ✅ No soldering on the fob PCB — fob is returned exactly as received.
- ✅ No fingerbot battery to maintain.
- ✅ Lower latency than Option C (~same as Options A/B).
- ✅ Battery replacement is trivial — CR2032 is external and accessible.
- ✅ Works on any fob regardless of internal button layout.
- ⚠️ Requires a 3D-printed button clip specific to your fob model (or improvise with foam/tape to hold the button down).
- ⚠️ Slightly more assembly than Option C, but no fingerbot dependency.

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
- **Transistor option**: peak current draw drops to ~20 mA (no relay coil) — better for battery life.
- **Relay option**: coil draws ~60–80 mA; ensure your power source can supply ≥ 300 mA peak. Use a flyback diode if using a bare coil (most relay modules have one built in).
- **Cap-pulse option**: ESP32 peak draw is unchanged (~20 mA during the brief pulse). The fingerbot has its own battery and isn't powered by the ESP32.
- Mount in an IP65 waterproof junction box for outdoor use. Route wires through cable glands.
