package com.dunnowsoftware.GarageAAtoESP32.geofence

import com.dunnowsoftware.GarageAAtoESP32.data.DevicePreferences
import com.dunnowsoftware.GarageAAtoESP32.data.GarageDevice

/**
 * What a manual-tap surface (phone dropdown, AA, watch, tile) should
 * pre-select/fire right now, based on real geofence presence only — being
 * physically inside a device's geofence is a positive "you are here"
 * signal, safe to drive a one-tap screen or (on the tile) fire without a
 * human confirming which device. See PLAN_multiple_garages.md Phase 3.
 *
 * Deliberately does **not** cover "no geofence configured" BLE devices —
 * unlike a geofence match, "no geofence, might be in RF range" is not a
 * positive presence signal (RF range alone can't distinguish "in range"
 * from "actually connected"), so pre-building a one-tap button for it would
 * duplicate and weaken the job the existing per-surface presence-scan/
 * connect logic already does correctly (e.g. GarageScreen's BLE-presence
 * auto-fire, which only fires once a device actually connects). Those
 * devices stay reachable exactly as before — via the ordinary picker plus
 * their own connect-triggered auto-fire — never via a pre-built button.
 */
sealed interface GeofenceResolution {
    data class Resolved(val devices: List<GarageDevice>) : GeofenceResolution

    /** No geofence currently entered — caller should fall back to selectedDeviceId or show a picker. */
    data object Empty : GeofenceResolution
}

fun resolveGeofenceTargets(prefs: DevicePreferences): GeofenceResolution {
    val devices = prefs.geofenceMatchedTargets()
    return if (devices.isEmpty()) GeofenceResolution.Empty else GeofenceResolution.Resolved(devices)
}
