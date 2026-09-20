package com.robpelo.companion

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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

    fun launchNetflix(activity: Activity): Boolean =
        launchWithHud(activity) { ExternalAppLauncher.launchNetflix(activity) }

    fun launchYouTube(activity: Activity): Boolean =
        launchWithHud(activity) { ExternalAppLauncher.launchYouTubeInFirefox(activity) }

    private fun launchWithHud(
        activity: Activity,
        launchDestination: () -> Boolean,
    ): Boolean {
        activity.startForegroundService(
            Intent(activity, RideTelemetryService::class.java).apply {
                action = RideTelemetryService.ACTION_START_HUD
            },
        )
        if (launchDestination()) {
            return true
        }

        activity.startService(
            Intent(activity, RideTelemetryService::class.java).apply {
                action = RideTelemetryService.ACTION_END_RIDE
            },
        )
        return false
    }
}
