package com.robpelo.companion.telemetry

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data object BoundWaitingForData : ConnectionState
    data object Connected : ConnectionState
    data class Failed(val message: String) : ConnectionState
}

