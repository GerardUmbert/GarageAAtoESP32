package com.dunnowsoftware.GarageAAtoESP32

import android.os.Handler
import android.os.Looper
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import com.dunnowsoftware.GarageAAtoESP32.ble.GarageBleManager
import com.dunnowsoftware.GarageAAtoESP32.ble.OpenResult
import com.dunnowsoftware.GarageAAtoESP32.data.DevicePreferences

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
        val subtitle = when {
            prefs.demoMode -> "Demo mode — no ESP32 needed"
            configured     -> prefs.deviceName ?: prefs.deviceAddress ?: ""
            else           -> "Set up garage in the phone app first"
        }

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
        if (prefs.demoMode) {
            triggerDemo()
            return
        }
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
                        Handler(Looper.getMainLooper()).postDelayed({
                            carContext.mainExecutor.execute { resetToIdle() }
                        }, 2000)
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

    private fun triggerDemo() {
        uiState = UiState.CONNECTING
        connectAttempt = 1
        invalidate()
        Handler(Looper.getMainLooper()).postDelayed({
            carContext.mainExecutor.execute {
                uiState = UiState.SUCCESS
                invalidate()
                Handler(Looper.getMainLooper()).postDelayed({
                    carContext.mainExecutor.execute { resetToIdle() }
                }, 2000)
            }
        }, 1500)
    }

    private fun resetToIdle() {
        uiState = UiState.IDLE
        invalidate()
    }
}
