# Android Auto UX — future ideas

Notes for a possible future redesign of the AA car-app screen
([`GarageScreen.kt`](../android/app/src/main/kotlin/com/dunnowsoftware/GarageAAtoESP32/GarageScreen.kt)).
**Not implemented today** — this file is a parking lot for ideas, not a spec.

## What's possible in AA

The AA car-app surface is **not free-form**. The Car App Library
(`androidx.car.app`) only lets you build screens from a fixed set of
`Template` classes:

- `PaneTemplate` — title + body rows + up to 2 actions
- `MessageTemplate` — title + message + 1–2 actions
- `GridTemplate` — grid of icon tiles
- `ListTemplate` — vertical list with icons + text
- `NavigationTemplate` — full-screen map (for navigation apps only)
- `LongMessageTemplate`, `SearchTemplate`, `SignInTemplate`, etc.

What you **cannot** do:

- Custom Compose / `View` rendering
- `Canvas` drawing of your own shapes
- Continuous animations (no expanding rings, no pulsing glows, no morphing
  states). Google's distraction-safety rules for in-car UI explicitly forbid
  animated visuals because drivers shouldn't be looking at moving things
- Custom typography / spacing — everything is themed by the AA host

So the phone app's hero-button + concentric pulses + green celebration
animation **cannot** be ported to AA. But the *intent* (telegraph that a
thing is happening, then show success/failure unmistakably) can be expressed
in AA-native idioms.

## Proposed redesign

Three layers, all using stock AA primitives:

### 1. Auto-send on screen entry

Today, the AA screen shows a button the user has to tap. Drop the tap. The
act of launching the app from the AA app drawer is itself the user's intent
to open the garage — fire the BLE open immediately on `onCreateScreen` /
`onResume` of `GarageScreen`. Zero taps from the driver.

Open question: **auto-send every time, or only first launch this session?**
Auto-send every time is friendliest from the driver's seat (re-tap the
launcher icon = re-open the door); first-time-only avoids accidental
re-opens if the user backs into the AA launcher and accidentally re-taps
GarageAA.

### 2. Loading state via `Pane.Builder().setLoading(true)`

While the BLE round-trip is in flight, render a `PaneTemplate` with
`setLoading(true)`. AA shows its standard system spinner — the closest
thing to "animation" AA permits, and the *intended* idiom for "waiting on
something." Title: `"Sending…"` or similar.

Triggered by calling `screen.invalidate()` on the AA screen instance to
re-render the template after state changes.

### 3. Discrete result panes

After the BLE call returns, swap to one of two final templates:

- **Success:** `MessageTemplate` with title `"Garage opened"`, body subtitle
  with timestamp ("Today, 8:14 AM"), single icon (a stock check / green
  indicator from `CarIcon`), and a `"Done"` action that closes the screen.
- **Failure:** `MessageTemplate` with title `"Couldn't open"`, body with the
  failure reason (matching the toast on the phone app), and a `"Retry"`
  action that re-runs the BLE open.

Note: `GarageBleManager` already retries 3× internally on connection
failures, so the `"Retry"` button is a *user-initiated fourth attempt*
after the internal 3 have failed. This matches the phone app's behavior.

## State diagram

```
launcher tap → onCreateScreen
                 │
                 ▼
            [Sending…]   ← Pane.setLoading(true)
                 │
        ┌────────┴────────┐
        ▼                 ▼
   [Opened ✓]        [Failed ✗]
   ("Done" action)   ("Retry" action → loops back to [Sending…])
```

Each transition is a `screen.invalidate()` followed by a fresh template
build in `onGetTemplate()`. Discrete state swaps, no animation between
them — but the spinner + result-state combo is the AA-native equivalent
of the phone app's hero-button celebration.

## Implementation scope

- Single file change: [`GarageScreen.kt`](../android/app/src/main/kotlin/com/dunnowsoftware/GarageAAtoESP32/GarageScreen.kt)
- No new dependencies, no new permissions
- No changes to `GarageBleManager`, `MainCarAppService`, or the phone UI
- Worth confirming the user really wants auto-trigger before building it
  (some users prefer the "intentional tap" pattern even in-car)

## Why we're not doing this now (2026-05-10)

Current screen works, has the standard "tap a big button" pattern that's
familiar from other AA apps, and the failure-feedback path is already
wired through toasts. The proposed redesign is a UX upgrade, not a bug
fix. Park it; revisit if real-world use surfaces driver-distraction issues
or if the manual-tap step proves annoying in practice.
