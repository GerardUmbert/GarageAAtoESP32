package com.garage.opener

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import com.garage.opener.ble.GarageBleManager
import com.garage.opener.ble.OpenResult
import com.garage.opener.data.DevicePreferences

class GarageScreen(carContext: CarContext) : Screen(carContext) {

    private val prefs = DevicePreferences(carContext)
    private val bleManager = GarageBleManager(carContext)

    private enum class UiState { IDLE, CONNECTING, SUCCESS, FAILURE }
    private var uiState = UiState.IDLE
    private var failureReason = ""
    private var connectAttempt = 0

    override fun onGetTemplate(): Template {
        return when (uiState) {
            UiState.CONNECTING -> buildLoading()
            UiState.SUCCESS    -> buildMessage("Opened!", false)
            UiState.FAILURE    -> buildMessage("Failed: $failureReason", true)
            UiState.IDLE       -> buildMain()
        }
    }

    // ── Templates ─────────────────────────────────────────────────────────────

    private fun buildMain(): Template {
        val configured = prefs.isConfigured
        val subtitle = if (configured) prefs.deviceName ?: prefs.deviceAddress ?: ""
                       else "Set up garage in the phone app first"

        val openAction = Action.Builder()
            .setTitle("Open Garage")
            .setOnClickListener { if (configured) triggerOpen() }
            .build()

        val pane = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle("Garage Door")
                    .addText(subtitle)
                    .build()
            )
            .addAction(openAction)
            .build()

        return PaneTemplate.Builder(pane)
            .setHeaderAction(Action.APP_ICON)
            .build()
    }

    private fun buildLoading(): Template {
        val msg = if (connectAttempt <= 1) "Connecting…"
                  else "Connecting… (attempt $connectAttempt / ${GarageBleManager.MAX_ATTEMPTS})"
        return MessageTemplate.Builder(msg)
            .setHeaderAction(Action.APP_ICON)
            .setLoading(true)
            .build()
    }

    private fun buildMessage(text: String, isError: Boolean): Template {
        val template = MessageTemplate.Builder(text)
            .setHeaderAction(Action.APP_ICON)

        if (isError) {
            template.addAction(
                Action.Builder()
                    .setTitle("Try Again")
                    .setOnClickListener { resetToIdle() }
                    .build()
            )
        }
        return template.build()
    }

    // ── BLE trigger ───────────────────────────────────────────────────────────

    private fun triggerOpen() {
        val address = prefs.deviceAddress ?: return
        val pin = prefs.pin

        uiState = UiState.CONNECTING
        invalidate()

        bleManager.connectAndOpen(address, pin,
            onAttempt = { n ->
                carContext.mainExecutor.execute {
                    connectAttempt = n
                    invalidate()
                }
            }
        ) { result ->
            carContext.mainExecutor.execute {
                when (result) {
                    is OpenResult.Success -> {
                        uiState = UiState.SUCCESS
                        invalidate()
                        // Return to idle after 2 s
                        carContext.mainExecutor.execute {
                            android.os.Handler(android.os.Looper.getMainLooper())
                                .postDelayed({ resetToIdle() }, 2000)
                        }
                    }
                    is OpenResult.Failure -> {
                        uiState = UiState.FAILURE
                        failureReason = result.reason
                        invalidate()
                    }
                }
            }
        }
    }

    private fun resetToIdle() {
        uiState = UiState.IDLE
        invalidate()
    }
}
