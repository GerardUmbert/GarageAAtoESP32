package com.garage.opener.ble

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed class OpenResult {
    object Success : OpenResult()
    data class Failure(val reason: String, val isAuthFailure: Boolean = false) : OpenResult()
}

class GarageBleManager(private val context: Context) {

    companion object {
        const val MAX_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 2000L
    }

    private val _state = MutableStateFlow<OpenResult?>(null)
    val state: StateFlow<OpenResult?> = _state

    private val handler = Handler(Looper.getMainLooper())

    private var gatt: BluetoothGatt? = null
    private var nonce: ByteArray? = null
    private var pin: String = ""
    private var deviceAddress: String = ""
    private var resultCallback: ((OpenResult) -> Unit)? = null
    private var attemptCallback: ((Int) -> Unit)? = null
    private var attempt = 0

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices()
            } else if (resultCallback != null) {
                // Connection dropped or failed — eligible for retry
                closeGatt()
                scheduleRetryOrFail("Connection failed (status $status)")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                closeGatt()
                scheduleRetryOrFail("Service discovery failed")
                return
            }
            val statusChar = gatt.getService(BleConstants.SERVICE_UUID)
                ?.getCharacteristic(BleConstants.STATUS_CHAR_UUID)
            if (statusChar == null) {
                closeGatt()
                scheduleRetryOrFail("Garage service not found")
                return
            }
            enableNotify(gatt, statusChar)
            // Nonce read is triggered from onDescriptorWrite after notification is enabled
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (characteristic.uuid != BleConstants.NONCE_CHAR_UUID) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                closeGatt()
                scheduleRetryOrFail("Failed to read nonce")
                return
            }
            nonce = characteristic.value
            sendCommand(gatt)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                closeGatt()
                scheduleRetryOrFail("Command write failed")
            }
            // Wait for Status notification; result delivered in onCharacteristicChanged.
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid != BleConstants.STATUS_CHAR_UUID) return
            val ok = characteristic.value?.firstOrNull()?.toInt() == 0x01
            if (ok) {
                deliver(OpenResult.Success)
            } else {
                // Auth failure — wrong PIN. Do NOT retry; retrying won't fix a bad PIN.
                deliver(OpenResult.Failure("Auth failed — check PIN", isAuthFailure = true))
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            val nonceChar = gatt.getService(BleConstants.SERVICE_UUID)
                ?.getCharacteristic(BleConstants.NONCE_CHAR_UUID)
            nonceChar?.let { gatt.readCharacteristic(it) }
        }
    }

    // ── Public ────────────────────────────────────────────────────────────────

    /**
     * @param onAttempt called each time a connection attempt starts, with the attempt number (1-based).
     *                  Use this to update UI ("Connecting… attempt 2/3").
     */
    fun connectAndOpen(
        deviceAddress: String,
        userPin: String,
        onAttempt: (attempt: Int) -> Unit = {},
        onResult: (OpenResult) -> Unit
    ) {
        cleanup()
        this.deviceAddress = deviceAddress
        pin = userPin
        resultCallback = onResult
        attemptCallback = onAttempt
        attempt = 0
        startAttempt()
    }

    fun cleanup() {
        handler.removeCallbacksAndMessages(null)
        closeGatt()
        nonce = null
        attempt = 0
        resultCallback = null
        attemptCallback = null
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun startAttempt() {
        attempt++
        attemptCallback?.invoke(attempt)
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val device = adapter.getRemoteDevice(deviceAddress)
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private fun scheduleRetryOrFail(reason: String) {
        val cb = resultCallback ?: return
        if (attempt >= MAX_ATTEMPTS) {
            resultCallback = null
            val result = OpenResult.Failure("$reason (tried $MAX_ATTEMPTS times)")
            _state.value = result
            cb(result)
            return
        }
        handler.postDelayed({ startAttempt() }, RETRY_DELAY_MS)
    }

    private fun sendCommand(gatt: BluetoothGatt) {
        val currentNonce = nonce ?: run {
            closeGatt()
            scheduleRetryOrFail("No nonce")
            return
        }
        val hmac = HmacHelper.compute(pin, currentNonce)
        val commandChar = gatt.getService(BleConstants.SERVICE_UUID)
            ?.getCharacteristic(BleConstants.COMMAND_CHAR_UUID) ?: run {
            closeGatt()
            scheduleRetryOrFail("Command characteristic not found")
            return
        }
        commandChar.value = hmac
        commandChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        gatt.writeCharacteristic(commandChar)
    }

    private fun enableNotify(gatt: BluetoothGatt, char: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(char, true)
        val descriptor = char.getDescriptor(BleConstants.CLIENT_CONFIG_UUID)
        descriptor?.let {
            it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(it)
        }
    }

    private fun deliver(result: OpenResult) {
        val cb = resultCallback ?: return
        resultCallback = null
        _state.value = result
        closeGatt()
        cb(result)
    }

    private fun closeGatt() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        nonce = null
    }
}
