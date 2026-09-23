package com.robpelo.companion

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.robpelo.companion.browser.BrowserLaunchResult
import com.robpelo.companion.browser.HudLaunchCoordinator
import com.robpelo.companion.browser.StreamingBrowserLauncher
import com.robpelo.companion.browser.StreamingDestination
import com.robpelo.companion.ride.RideTelemetryService

object VideoHudLauncher {
    fun hasOverlayPermission(activity: Activity): Boolean =
        Settings.canDrawOverlays(activity)

    fun openOverlaySettings(activity: Activity) {
        activity.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${activity.packageName}"),
            ),
        )
    }

    fun launch(
        activity: Activity,
        browserLauncher: StreamingBrowserLauncher,
        destination: StreamingDestination,
    ): BrowserLaunchResult =
        HudLaunchCoordinator.launch(
            startHud = {
                activity.startForegroundService(
                    Intent(activity, RideTelemetryService::class.java).apply {
                        action = RideTelemetryService.ACTION_START_HUD
                    },
                )
            },
            launchBrowser = { browserLauncher.launch(destination) },
            stopHud = {
                activity.startService(
                    Intent(activity, RideTelemetryService::class.java).apply {
                        action = RideTelemetryService.ACTION_END_RIDE
                    },
                )
            },
        )
}
