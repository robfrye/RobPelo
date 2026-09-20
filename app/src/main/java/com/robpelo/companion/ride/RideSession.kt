package com.robpelo.companion.ride

import com.robpelo.companion.telemetry.RawBikeSample

enum class RideState {
    IDLE,
    ACTIVE,
    PAUSED,
    FINISHED,
}

data class RideSnapshot(
    val state: RideState = RideState.IDLE,
    val elapsedMs: Long = 0,
    val cadenceRpm: Int? = null,
    val resistancePercent: Int? = null,
    val outputWatts: Int? = null,
    val speedMph: Double = 0.0,
    val distanceMiles: Double = 0.0,
    val totalOutputKj: Double = 0.0,
)

class RideSession(
    private val clockMs: () -> Long,
) {
    private var state = RideState.IDLE
    private var activeStartedAtMs = 0L
    private var accumulatedActiveMs = 0L
    private var lastIntegratedSampleMs: Long? = null
    private var latestSample: RawBikeSample? = null
    private var distanceMiles = 0.0
    private var totalOutputKj = 0.0

    @Synchronized
    fun start() {
        if (state != RideState.IDLE) {
            return
        }
        state = RideState.ACTIVE
        activeStartedAtMs = clockMs()
    }

    @Synchronized
    fun pause() {
        if (state != RideState.ACTIVE) {
            return
        }
        val now = clockMs()
        accumulatedActiveMs += (now - activeStartedAtMs).coerceAtLeast(0)
        lastIntegratedSampleMs = null
        state = RideState.PAUSED
    }

    @Synchronized
    fun resume() {
        if (state != RideState.PAUSED) {
            return
        }
        activeStartedAtMs = clockMs()
        lastIntegratedSampleMs = null
        state = RideState.ACTIVE
    }

    @Synchronized
    fun finish() {
        if (state == RideState.ACTIVE) {
            val now = clockMs()
            accumulatedActiveMs += (now - activeStartedAtMs).coerceAtLeast(0)
        }
        if (state != RideState.IDLE) {
            state = RideState.FINISHED
        }
        lastIntegratedSampleMs = null
    }

    @Synchronized
    fun accept(sample: RawBikeSample) {
        latestSample = sample
        if (state != RideState.ACTIVE) {
            lastIntegratedSampleMs = null
            return
        }

        val previousSampleMs = lastIntegratedSampleMs
        lastIntegratedSampleMs = sample.receivedAtElapsedRealtimeMs
        if (previousSampleMs == null) {
            return
        }

        val deltaMs = sample.receivedAtElapsedRealtimeMs - previousSampleMs
        if (deltaMs !in 1..MAX_INTEGRATION_INTERVAL_MS) {
            return
        }

        val hours = deltaMs / MILLIS_PER_HOUR
        val seconds = deltaMs / MILLIS_PER_SECOND
        val speedMph = pelotonSpeedMphFromPower(sample.outputWatts.toDouble())
        distanceMiles += speedMph * hours
        totalOutputKj += sample.outputWatts * seconds / 1000.0
    }

    @Synchronized
    fun snapshot(): RideSnapshot {
        val now = clockMs()
        val elapsedMs = when (state) {
            RideState.ACTIVE -> {
                accumulatedActiveMs + (now - activeStartedAtMs).coerceAtLeast(0)
            }
            else -> accumulatedActiveMs
        }
        val sample = latestSample?.takeIf {
            now - it.receivedAtElapsedRealtimeMs <= STALE_SAMPLE_AFTER_MS
        }
        return RideSnapshot(
            state = state,
            elapsedMs = elapsedMs,
            cadenceRpm = sample?.cadenceRpm,
            resistancePercent = sample?.resistancePercent,
            outputWatts = sample?.outputWatts,
            speedMph = pelotonSpeedMphFromPower(sample?.outputWatts?.toDouble() ?: 0.0),
            distanceMiles = distanceMiles,
            totalOutputKj = totalOutputKj,
        )
    }

    private companion object {
        const val MAX_INTEGRATION_INTERVAL_MS = 5_000L
        const val STALE_SAMPLE_AFTER_MS = 3_000L
        const val MILLIS_PER_HOUR = 3_600_000.0
        const val MILLIS_PER_SECOND = 1_000.0
    }
}
