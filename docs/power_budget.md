# Power Budget

## Current draw estimates

| State | Current | Notes |
|---|---|---|
| Deep sleep | ~10 µA | Timer wakeup every `SLEEP_DURATION_S` seconds |
| BLE advertising | ~20 mA avg | 100–200 ms interval; radio on ~5–10% of time |
| BLE connected + auth | ~100 mA | ~2 s per open event |
| Relay energized | ~60–80 mA (coil) | 500 ms pulse |
| Relay + ESP32 active peak | ~150 mA | During pulse only |

## Daily consumption

Assuming 30 s advertise / 5 s sleep cycle (`SLEEP_DURATION_S = 5`), 20 garage opens per day:

```
Per cycle (no open):
  Advertising:  20 mA × 30 s = 600 mAs
  Sleep:        0.01 mA × 5 s = 0.05 mAs
  Total/cycle:  ~600 mAs

Cycles per hour: 3600 / 35 = ~103
Daily baseline: 103 × 600 / 3600 = ~17.1 mAh

Per open event:
  Connected + auth: 100 mA × 2 s = 200 mAs
  Relay pulse:      150 mA × 0.5 s = 75 mAs
  Per open: ~275 mAs ≈ 0.076 mAh

20 opens/day: 20 × 0.076 = 1.5 mAh

Total daily: ~18.6 mAh ≈ 19 mAh/day
```

## Battery sizing

| Battery | Capacity | Runtime (no solar) |
|---|---|---|
| Single 18650 (3000 mAh) | 3000 mAh | ~158 days |
| 2× 18650 in parallel (6000 mAh) | 6000 mAh | ~316 days |
| 10 000 mAh power bank | ~7000 mAh usable | ~368 days (~12 months) |
| 25 000 mAh power bank | ~17 500 mAh usable | ~921 days (~30 months) |

## Solar sizing

A 1 W panel at 5 V in 3 peak sun hours per day generates:
```
1 W / 5 V = 200 mA × 3 h = 600 mAh/day
```
That is **32× the daily draw** — even accounting for charging inefficiency (~70%), you have >22× margin.
A 0.5 W panel is still sufficient. Use any panel rated ≥ 0.5 W for outdoor installs.

## Notes on power banks and auto-shutoff

Many power banks shut off when current drops below ~100 mA for a few seconds.
During deep sleep the ESP32 draws only ~10 µA — far below this threshold.

**If using a power bank**, choose one that supports:
- "Always-on" / "trickle charge" mode (designed for small devices like GPS trackers)
- Or test by connecting the ESP32 and leaving it overnight — if it's still running next morning, the bank doesn't auto-shutoff at low current.

Known-good options: Anker A1263 (PowerCore Slim 10000), Ravpower RP-PB201.
