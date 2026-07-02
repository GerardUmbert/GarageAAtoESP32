package com.dunnowsoftware.GarageAAtoESP32.transport

sealed class OpenResult {
    data class Success(val caps: Int = 0) : OpenResult()
    data class Failure(val reason: String, val isAuthFailure: Boolean = false) : OpenResult()
}
