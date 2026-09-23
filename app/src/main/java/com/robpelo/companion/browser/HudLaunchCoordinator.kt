package com.robpelo.companion.browser

object HudLaunchCoordinator {
    fun launch(
        startHud: () -> Unit,
        launchBrowser: () -> BrowserLaunchResult,
        stopHud: () -> Unit,
    ): BrowserLaunchResult {
        startHud()
        val result = launchBrowser()
        if (result != BrowserLaunchResult.Success) {
            stopHud()
        }
        return result
    }
}
