package com.robpelo.companion.ride

import com.robpelo.companion.telemetry.RawBikeSample
import org.junit.Assert.assertEquals
import org.junit.Test

class RideSessionTest {
    private var nowMs = 1_000L
    private val session = RideSession { nowMs }

    @Test
    fun `elapsed time excludes paused duration`() {
        session.start()
        nowMs += 5_000
        session.pause()
        nowMs += 10_000
        session.resume()
        nowMs += 3_000

        assertEquals(8_000L, session.snapshot().elapsedMs)
    }

    @Test
    fun `samples integrate distance and total output`() {
        session.start()
        session.accept(sample(atMs = 1_000, watts = 100))
        session.accept(sample(atMs = 2_000, watts = 100))

        val snapshot = session.snapshot()
        assertEquals(0.1, snapshot.totalOutputKj, 0.0001)
        assertEquals(
            pelotonSpeedMphFromPower(100.0) / 3_600.0,
            snapshot.distanceMiles,
            0.000001,
        )
    }

    @Test
    fun `stale intervals are not integrated`() {
        session.start()
        session.accept(sample(atMs = 1_000, watts = 200))
        session.accept(sample(atMs = 10_000, watts = 200))

        val snapshot = session.snapshot()
        assertEquals(0.0, snapshot.totalOutputKj, 0.0)
        assertEquals(0.0, snapshot.distanceMiles, 0.0)
    }

    @Test
    fun `paused samples update display but do not accumulate`() {
        session.start()
        session.accept(sample(atMs = 1_000, watts = 100))
        session.pause()
        session.accept(sample(atMs = 2_000, watts = 250))

        val snapshot = session.snapshot()
        assertEquals(250, snapshot.outputWatts)
        assertEquals(0.0, snapshot.totalOutputKj, 0.0)
    }

    @Test
    fun `stale live values become unavailable`() {
        session.start()
        session.accept(sample(atMs = 1_000, watts = 100))
        nowMs = 4_001

        val snapshot = session.snapshot()
        assertEquals(null, snapshot.cadenceRpm)
        assertEquals(null, snapshot.resistancePercent)
        assertEquals(null, snapshot.outputWatts)
        assertEquals(0.0, snapshot.speedMph, 0.0)
    }

    private fun sample(atMs: Long, watts: Int) = RawBikeSample(
        cadenceRpm = 80,
        resistancePercent = 40,
        rawPower = watts * 100L,
        outputWatts = watts,
        receivedAtElapsedRealtimeMs = atMs,
    )
}
