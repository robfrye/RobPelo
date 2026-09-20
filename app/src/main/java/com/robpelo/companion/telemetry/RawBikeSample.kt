package com.robpelo.companion.telemetry

data class RawBikeSample(
    val cadenceRpm: Int,
    val resistancePercent: Int,
    val rawPower: Long,
    val outputWatts: Int,
    val receivedAtElapsedRealtimeMs: Long,
)

