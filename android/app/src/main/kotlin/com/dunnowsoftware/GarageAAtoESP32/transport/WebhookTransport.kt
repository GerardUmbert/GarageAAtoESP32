package com.dunnowsoftware.GarageAAtoESP32.transport

import android.content.Context
import android.os.Build
import com.dunnowsoftware.GarageAAtoESP32.data.TriggerSource
import com.dunnowsoftware.GarageAAtoESP32.data.WebhookConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * [OpenTransport] that "opens" by POSTing to a user-configured webhook URL
 * (e.g. a Home Assistant native webhook trigger or rest_command endpoint)
 * instead of connecting to an ESP32 over BLE. Retry shape mirrors
 * [com.dunnowsoftware.GarageAAtoESP32.ble.GarageBleManager] exactly, so
 * downstream UI/history behavior stays consistent between transports.
 */
class WebhookTransport(
    private val context: Context,
    private val config: WebhookConfig,
) : OpenTransport {

    companion object {
        const val MAX_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 2000L
        private const val ATTEMPT_TIMEOUT_MS = 5000
    }

    private val _state = MutableStateFlow<OpenResult?>(null)
    override val state: StateFlow<OpenResult?> = _state

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun open(
        trigger: TriggerSource,
        onAttempt: (attempt: Int) -> Unit,
        onResult: (OpenResult) -> Unit,
    ) {
        scope.launch {
            runAttempts(trigger, 0, onAttempt, onResult)
        }
    }

    private suspend fun runAttempts(
        trigger: TriggerSource,
        attemptsSoFar: Int,
        onAttempt: (attempt: Int) -> Unit,
        onResult: (OpenResult) -> Unit,
    ) {
        val attempt = attemptsSoFar + 1
        withContext(Dispatchers.Main) { onAttempt(attempt) }

        val result = postOnce(trigger)

        if (result is OpenResult.Success) {
            deliver(result, onResult)
            return
        }

        val failure = result as OpenResult.Failure
        if (failure.isAuthFailure) {
            // Bad/missing auth won't fix itself on retry — same rule as a bad BLE PIN.
            deliver(failure, onResult)
            return
        }

        if (attempt >= MAX_ATTEMPTS) {
            deliver(
                OpenResult.Failure("${failure.reason} (tried $MAX_ATTEMPTS times)"),
                onResult,
            )
            return
        }

        delay(RETRY_DELAY_MS)
        runAttempts(trigger, attempt, onAttempt, onResult)
    }

    private fun postOnce(trigger: TriggerSource): OpenResult {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(config.url)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = ATTEMPT_TIMEOUT_MS
                readTimeout = ATTEMPT_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                if (!config.authToken.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer ${config.authToken}")
                }
            }

            val body = buildPayload(trigger)
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body) }

            val code = connection.responseCode
            when {
                code in 200..299 -> OpenResult.Success(caps = 0)
                code == 401 || code == 403 ->
                    OpenResult.Failure("Auth failed — check webhook token", isAuthFailure = true)
                else -> OpenResult.Failure("Webhook returned HTTP $code")
            }
        } catch (e: IOException) {
            OpenResult.Failure(e.message ?: "Network error")
        } catch (e: Exception) {
            OpenResult.Failure(e.message ?: "Webhook request failed")
        } finally {
            connection?.disconnect()
        }
    }

    private fun buildPayload(trigger: TriggerSource): String {
        val reason = when (trigger) {
            TriggerSource.MANUAL_PHONE,
            TriggerSource.MANUAL_AA     -> "manual"
            TriggerSource.WEAR          -> "watch"
            TriggerSource.AUTO_GEOFENCE -> "geofence"
            TriggerSource.VOICE         -> "voice"
        }
        return JSONObject().apply {
            put("reason", reason)
            put("timestamp", System.currentTimeMillis() / 1000L)
            put("model", "${Build.MANUFACTURER} ${Build.MODEL}")
        }.toString()
    }

    private suspend fun deliver(result: OpenResult, onResult: (OpenResult) -> Unit) {
        _state.value = result
        withContext(Dispatchers.Main) { onResult(result) }
    }

    override fun cleanup() {
        scope.cancel()
    }
}
