package com.robpelo.companion.browser

interface StreamingBrowserLauncher {
    fun launch(destination: StreamingDestination): BrowserLaunchResult
}
