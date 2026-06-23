package com.dunnowsoftware.GarageAAtoESP32

import com.dunnowsoftware.GarageAAtoESP32.ble.OpenResult

/**
 * Single source of truth for the demo-mode "open the garage" simulation.
 * Used by both the phone activity and the AA car screen so failure rate,
 * delay timing, and error messages stay identical across surfaces.
 */
object DemoOpener {
    const val DELAY_MS: Long = 1200L
    const val FAILURE_RATE: Float = 0.30f

    private val reasons = listOf(
        "Demo: connection timed out",
        "Demo: auth failed — check password",
        "Demo: garage didn't respond",
    )

    /** Returns the simulated open result for a single demo attempt. */
    fun nextResult(): OpenResult =
        if (kotlin.random.Random.nextFloat() < FAILURE_RATE) {
            OpenResult.Failure(reasons.random())
        } else {
            OpenResult.Success()
        }
}
