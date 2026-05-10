# Testing the app end-to-end without an ESP32

You can test the full BLE round-trip — scan, pair, open, success, failure —
using **nRF Connect for Android** on a second phone as a fake ESP32.
Zero code changes required, the GarageAA app has no idea it's not talking
to real hardware.

The only thing this *can't* test is whether the HMAC the app sends is
cryptographically correct (nRF Connect can't run HMAC-SHA256). For that
you need the real firmware. But you can verify every other part of the
flow: scan visibility, pairing, BLE connect, characteristic reads + writes,
notifications, success/failure UI states.

## What you need

- A second Android phone or tablet (any modern Android with BLE)
- **nRF Connect for Mobile** by Nordic Semiconductor — free on the Play Store
- The GarageAA app installed on your primary phone

## UUIDs you'll need (copy these — must match exactly)

| Item | UUID |
|---|---|
| Service | `12345678-0000-1000-8000-00805F9B34FB` |
| Nonce characteristic (READ) | `12345678-0001-1000-8000-00805F9B34FB` |
| Command characteristic (WRITE) | `12345678-0002-1000-8000-00805F9B34FB` |
| Status characteristic (NOTIFY) | `12345678-0003-1000-8000-00805F9B34FB` |

## Setup on the second phone (the fake ESP32)

### 1. Install nRF Connect

Search "nRF Connect for Mobile" on the Play Store, install it.

### 2. Open the GATT-server editor

In nRF Connect:
1. Tap the menu (☰) in the top-left
2. Tap **Configure GATT Server**

### 3. Add the GarageAA service

1. Tap **Add Service**
2. Choose **Custom service** at the bottom of the list (not any of the SIG-Adopted ones)
3. Paste the **Service** UUID from the table above
4. Save

### 4. Add the three characteristics

For each row, tap **Add characteristic** under the service you just made:

#### Nonce (READ)
- UUID: `12345678-0001-1000-8000-00805F9B34FB`
- Properties: **Read** ✓ (everything else off)
- Permissions: **Read**
- Initial value: 16 bytes of anything. Easy choice: `00 11 22 33 44 55 66 77 88 99 AA BB CC DD EE FF` (paste into the hex value field; nRF Connect's value editor accepts hex). It doesn't matter what value — the app just HMACs whatever it reads.

#### Command (WRITE)
- UUID: `12345678-0002-1000-8000-00805F9B34FB`
- Properties: **Write** ✓ (only this)
- Permissions: **Write**
- No initial value needed

#### Status (NOTIFY)
- UUID: `12345678-0003-1000-8000-00805F9B34FB`
- Properties: **Notify** ✓ (only this)
- Permissions: leave default
- Add a **descriptor**: tap **Add descriptor** under this characteristic, choose **Client Characteristic Configuration** from the list (UUID `2902`). This is what lets the app subscribe to notifications. nRF Connect will set its permissions automatically.
- No initial value needed

### 5. Save the GATT server config

Back out of the editor — nRF Connect saves automatically.

### 6. Start advertising

1. In nRF Connect, tap the **Advertiser** tab at the bottom
2. Tap **+** (or "New Advertising Packet" / similar wording)
3. Give it a name like `GarageAA-Fake`
4. **Important:** in the advertising-data section, add a **Service UUID** field and put the Service UUID (`12345678-0000-1000-8000-00805F9B34FB`) — without this the GarageAA app's scan filter won't match
5. Optionally set the device name (e.g. `ESP32-Garage`) so it shows readable in the scan list
6. Tap **Start** / the play icon

The phone is now advertising as a fake ESP32.

## Setup on the primary phone (GarageAA)

If you've already paired to a real or different fake device, **unpair first**: open the app → Settings → "Unpair this opener" in the Danger zone.

Then:

1. From the Settings screen tap **Pair an opener** (or restart the app after unpairing — it'll route you to the scan screen automatically)
2. Wait a moment — the radar sweeps and the bottom card switches from "Scanning for openers…" to a row showing your fake device (whatever name you set in nRF Connect, with its RSSI on the right)
3. Tap the row → app saves the address + name, jumps to the main screen

If the device doesn't appear: confirm the advertising packet on the second phone includes the Service UUID (step 6 above) — that's the most common miss.

## Walking through an open

With both phones in front of you:

### Successful open

1. **GarageAA phone:** make sure you've set a password (Settings → Change password). Any value works since nRF Connect won't verify it.
2. **GarageAA phone:** tap the hero button. Ring fades to green, three concentric pulses start radiating out. The "SENDING…" label appears.
3. **nRF Connect phone:** the connection event shows in the **Server** tab. You'll see:
   - A connect event
   - A read of the Nonce characteristic
   - A descriptor write to the Status CCCD (the app subscribing to notifications)
   - A write to the Command characteristic with 32 bytes (the HMAC)
4. **nRF Connect phone:** tap the Status characteristic, then **Notify** / **Send notification**. In the value editor, send a single byte `01`. Tap send.
5. **GarageAA phone:** ring fills green, check glyph appears, "Last opened" timestamp updates. After 2s it settles back to idle.

### Failed open (auth path)

Same as above but in step 4, send `00` (or anything other than `01`) instead.

**GarageAA phone:** ring fills pastel red, X glyph appears, toast at the bottom shows "Auth failed — check PIN" (the failure-reason message from `GarageBleManager`). After 2s it settles back to idle.

### Connection-failed path

To exercise the connection-retry / scheduled-fail path:
1. Pair as above
2. **nRF Connect phone:** stop advertising and disconnect any active connections
3. **GarageAA phone:** tap the hero button
4. The app retries 3× internally (each attempt waits ~2s between), then shows the failed state with a "Connection failed" reason

## Troubleshooting

- **Device doesn't appear in scan:** advertising packet missing the Service UUID. Step 6 of "Start advertising".
- **Connect succeeds but no notification ever fires:** the Status characteristic doesn't have a CCCD descriptor. Re-add the `2902` descriptor under it.
- **App shows "Garage service not found":** UUIDs typo'd. Double-check by copy-pasting from this file.
- **App shows auth failure even when sending `01`:** the Status notification went out but it wasn't `0x01`. Check the byte you sent — nRF Connect sometimes interprets the value as text; switch the editor to **hex** mode and send the literal byte `01`.

## What this test proves

- Scan filter works (Service UUID match)
- Pairing flow saves the right address + name to encrypted prefs
- Connect → service discovery → enable-notify → read-nonce → compute-HMAC → write-command → wait-for-status sequence runs
- Both success (`0x01`) and failure (any other byte) paths render the matching UI state on both surfaces (phone + AA car screen)
- The 3× connection-retry path triggers when the peer is unreachable

## What this test doesn't prove

- HMAC correctness — nRF Connect doesn't verify the bytes the app writes. Only the real ESP32 firmware (which knows the password) can confirm the HMAC matches.
- Power consumption / range — those need real hardware.
