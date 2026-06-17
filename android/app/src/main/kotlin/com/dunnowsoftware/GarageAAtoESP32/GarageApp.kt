package com.dunnowsoftware.GarageAAtoESP32

import android.app.Application
import androidx.car.app.connection.CarConnection
import com.dunnowsoftware.GarageAAtoESP32.geofence.GeofenceLogger

class GarageApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CarConnection(this).type.observeForever { connectionType ->
            val connected = connectionType == CarConnection.CONNECTION_TYPE_PROJECTION
            AndroidAutoState.isConnected = connected
            GeofenceLogger.i(this, "GarageApp", "AA connection type=$connectionType connected=$connected")
        }
    }
}
