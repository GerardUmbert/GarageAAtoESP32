package com.dunnowsoftware.GarageAAtoESP32.geofence

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.dunnowsoftware.GarageAAtoESP32.data.DevicePreferences
import com.dunnowsoftware.GarageAAtoESP32.data.PairedDevice
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

private const val TAG = "GeofenceManager"
const val GEOFENCE_ID_PREFIX = "garage_"
const val GEOFENCE_OUTER_ID_PREFIX = "garage_outer_"
const val EXTRA_DEVICE_ADDRESS = "device_address"
// Outer geofence offset for GPS warmup — added on top of the user-configured radius
const val OUTER_GEOFENCE_OFFSET_M = 150f

class GeofenceManager(private val context: Context) {

    private val client = LocationServices.getGeofencingClient(context)

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    private fun activityPendingIntent(): PendingIntent {
        val intent = Intent(context, ActivityUpdateReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    fun startActivityUpdates() {
        try {
            ActivityRecognition.getClient(context)
                .requestActivityUpdates(30_000L, activityPendingIntent())
                .addOnSuccessListener { GeofenceLogger.i(context, TAG, "Activity updates registered (30s interval)") }
                .addOnFailureListener { GeofenceLogger.w(context, TAG, "Failed to register activity updates: $it") }
        } catch (e: SecurityException) {
            GeofenceLogger.w(context, TAG, "Missing ACTIVITY_RECOGNITION permission: $e")
        }
    }

    fun stopActivityUpdates() {
        try {
            ActivityRecognition.getClient(context)
                .removeActivityUpdates(activityPendingIntent())
                .addOnSuccessListener { GeofenceLogger.i(context, TAG, "Activity updates removed") }
                .addOnFailureListener { GeofenceLogger.w(context, TAG, "Failed to remove activity updates: $it") }
        } catch (e: SecurityException) {
            GeofenceLogger.w(context, TAG, "Missing ACTIVITY_RECOGNITION permission on stop: $e")
        }
    }

    fun register(device: PairedDevice) {
        val lat = device.geofenceLat ?: return
        val lng = device.geofenceLng ?: return
        val radius = device.geofenceRadiusM ?: return

        GeofenceLogger.i(context, TAG, "Registering geofence for ${device.address} — lat=$lat lng=$lng radius=${radius}m")

        val innerGeofence = Geofence.Builder()
            .setRequestId(GEOFENCE_ID_PREFIX + device.address)
            .setCircularRegion(lat, lng, radius)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setNotificationResponsiveness(5_000)
            .build()

        val outerGeofence = Geofence.Builder()
            .setRequestId(GEOFENCE_OUTER_ID_PREFIX + device.address)
            .setCircularRegion(lat, lng, radius + OUTER_GEOFENCE_OFFSET_M)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setNotificationResponsiveness(5_000)
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0) // do not fire immediately on register
            .addGeofence(innerGeofence)
            .addGeofence(outerGeofence)
            .build()

        try {
            client.addGeofences(request, pendingIntent())
                .addOnSuccessListener { GeofenceLogger.i(context, TAG, "Geofence registered OK for ${device.address}") }
                .addOnFailureListener { GeofenceLogger.e(context, TAG, "Failed to register geofence for ${device.address}: $it") }
        } catch (e: SecurityException) {
            GeofenceLogger.e(context, TAG, "Missing location permission for geofence registration: $e")
        }
        startActivityUpdates()
    }

    fun unregister(deviceAddress: String) {
        GeofenceLogger.i(context, TAG, "Removing geofence for $deviceAddress")
        client.removeGeofences(listOf(GEOFENCE_ID_PREFIX + deviceAddress, GEOFENCE_OUTER_ID_PREFIX + deviceAddress))
            .addOnSuccessListener { GeofenceLogger.i(context, TAG, "Geofence removed OK for $deviceAddress") }
            .addOnFailureListener { GeofenceLogger.e(context, TAG, "Failed to remove geofence for $deviceAddress: $it") }
        stopActivityUpdates()
    }

    fun reregisterAll() {
        val device = DevicePreferences(context).pairedDevice
        if (device == null) {
            GeofenceLogger.d(context, TAG, "reregisterAll: no paired device, nothing to register")
            return
        }
        if (!device.isGeofenceActive) {
            GeofenceLogger.d(context, TAG, "reregisterAll: device ${device.address} has no active geofence, skipping")
            return
        }
        GeofenceLogger.i(context, TAG, "reregisterAll: re-registering geofence for ${device.address}")
        register(device) // also calls startActivityUpdates()
    }
}
