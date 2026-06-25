package com.dunnowsoftware.GarageAAtoESP32.wear

import android.content.Context

private const val PREFS = "tile_state"
private const val KEY_STATE = "state"
private const val KEY_SET_AT = "set_at"

private const val STATE_IDLE    = "IDLE"
private const val STATE_SENDING = "SENDING"
private const val STATE_OPENED  = "OPENED"
private const val STATE_FAILED  = "FAILED"

private const val RESULT_LINGER_MS = 3_000L

enum class TileState { Idle, Sending, Opened, Failed }

object TileStateStore {

    fun get(context: Context): TileState {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_STATE, STATE_IDLE)
        val setAt = prefs.getLong(KEY_SET_AT, 0L)
        // Auto-expire Opened/Failed after linger period
        if ((raw == STATE_OPENED || raw == STATE_FAILED) &&
            System.currentTimeMillis() - setAt > RESULT_LINGER_MS) {
            set(context, TileState.Idle)
            return TileState.Idle
        }
        return when (raw) {
            STATE_SENDING -> TileState.Sending
            STATE_OPENED  -> TileState.Opened
            STATE_FAILED  -> TileState.Failed
            else          -> TileState.Idle
        }
    }

    fun set(context: Context, state: TileState) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_STATE, state.name)
            .putLong(KEY_SET_AT, System.currentTimeMillis())
            .apply()
    }

    fun setSending(context: Context) = set(context, TileState.Sending)

    fun setResult(context: Context, success: Boolean) =
        set(context, if (success) TileState.Opened else TileState.Failed)
}
