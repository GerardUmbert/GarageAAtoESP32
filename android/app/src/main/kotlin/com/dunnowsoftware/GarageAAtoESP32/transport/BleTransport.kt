package com.dunnowsoftware.GarageAAtoESP32.transport

import android.content.Context
import com.dunnowsoftware.GarageAAtoESP32.ble.GarageBleManager
import com.dunnowsoftware.GarageAAtoESP32.data.PairedDevice
import com.dunnowsoftware.GarageAAtoESP32.data.TriggerSource
import kotlinx.coroutines.flow.StateFlow

/**
 * Thin [OpenTransport] adapter over [GarageBleManager], closing over the
 * paired device's address/password so callers don't need to know them.
 */
class BleTransport(context: Context, private val pairedDevice: PairedDevice) : OpenTransport {

    private val bleManager = GarageBleManager(context)

    override val state: StateFlow<OpenResult?> = bleManager.state

    override fun open(
        trigger: TriggerSource,
        onAttempt: (attempt: Int) -> Unit,
        onResult: (OpenResult) -> Unit,
    ) {
        bleManager.connectAndOpen(
            deviceAddress = pairedDevice.address,
            userPin = pairedDevice.password,
            trigger = trigger,
            onAttempt = onAttempt,
            onResult = onResult,
        )
    }

    override fun cleanup() {
        bleManager.cleanup()
    }
}
