package com.robpelo.companion.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HudLaunchCoordinatorTest {
    @Test
    fun `successful browser launch keeps HUD running`() {
        var started = false
        var stopped = false

        val result = HudLaunchCoordinator.launch(
            startHud = { started = true },
            launchBrowser = { BrowserLaunchResult.Success },
            stopHud = { stopped = true },
        )

        assertEquals(BrowserLaunchResult.Success, result)
        assertTrue(started)
        assertFalse(stopped)
    }

    @Test
    fun `failed browser launch stops HUD immediately`() {
        var started = false
        var stopped = false
        val failure = BrowserLaunchResult.MissingPackage("com.robpelo.browser")

        val result = HudLaunchCoordinator.launch(
            startHud = { started = true },
            launchBrowser = { failure },
            stopHud = { stopped = true },
        )

        assertEquals(failure, result)
        assertTrue(started)
        assertTrue(stopped)
    }
}
