package com.robpelo.companion.telemetry

import org.junit.Assert.assertEquals
import org.junit.Test

class BikeSampleScalingTest {
    @Test
    fun `centiwatts are converted to whole watts`() {
        assertEquals(210, 21_050L.toWholeWatts())
    }

    @Test
    fun `negative power is clamped to zero`() {
        assertEquals(0, (-100L).toWholeWatts())
    }
}
