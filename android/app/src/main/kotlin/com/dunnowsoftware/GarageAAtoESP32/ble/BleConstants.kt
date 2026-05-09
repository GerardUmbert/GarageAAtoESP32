package com.dunnowsoftware.GarageAAtoESP32.ble

import java.util.UUID

// Must mirror the UUIDs in firmware/include/config.h
object BleConstants {
    val SERVICE_UUID: UUID      = UUID.fromString("12345678-0000-1000-8000-00805F9B34FB")
    val NONCE_CHAR_UUID: UUID   = UUID.fromString("12345678-0001-1000-8000-00805F9B34FB")
    val COMMAND_CHAR_UUID: UUID = UUID.fromString("12345678-0002-1000-8000-00805F9B34FB")
    val STATUS_CHAR_UUID: UUID  = UUID.fromString("12345678-0003-1000-8000-00805F9B34FB")

    val CLIENT_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
