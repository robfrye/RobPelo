package com.robpelo.companion.telemetry

import android.os.SystemClock
import com.onepeloton.affernetservice.BikeData

internal object BikeSampleMapper {
    fun from(data: BikeData): RawBikeSample = RawBikeSample(
        cadenceRpm = data.rpm.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
        resistancePercent = data.currentResistance.coerceIn(0, 100),
        rawPower = data.power,
        outputWatts = data.power.toWholeWatts(),
        receivedAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
    )
}

internal fun Long.toWholeWatts(): Int =
    (this / 100L).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
