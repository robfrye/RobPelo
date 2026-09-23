package com.robpelo.companion.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingDestinationTest {
    @Test
    fun `service identifiers match the browser contract`() {
        assertEquals(
            listOf("netflix", "youtube", "hbo_max", "prime_video", "apple_tv"),
            StreamingDestination.entries.map { it.serviceId },
        )
    }
}
