package com.robpelo.companion.browser

import android.content.Context

enum class BrowserRoute {
    MEDIA_BROWSER,
    TV_BRO,
}

class StreamingBrowserRouter(
    context: Context,
) : StreamingBrowserLauncher {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val mediaBrowser = RobPeloMediaBrowserLauncher(context)
    private val tvBro = TvBroLauncher(context)

    val route: BrowserRoute
        get() = BrowserRoute.entries.firstOrNull {
            it.name == preferences.getString(KEY_ROUTE, null)
        } ?: BrowserRoute.TV_BRO

    fun setRoute(route: BrowserRoute) {
        preferences.edit().putString(KEY_ROUTE, route.name).apply()
    }

    override fun launch(destination: StreamingDestination): BrowserLaunchResult =
        when (route) {
            BrowserRoute.MEDIA_BROWSER -> mediaBrowser.launch(destination)
            BrowserRoute.TV_BRO -> tvBro.launch(destination)
        }

    fun launchMediaBrowserSetup(): BrowserLaunchResult =
        mediaBrowser.launch(StreamingDestination.PRIME_VIDEO)

    private companion object {
        const val PREFERENCES = "streaming_browser"
        const val KEY_ROUTE = "route"
    }
}
