package com.robpelo.companion

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.robpelo.companion.ride.RideTelemetryService

object NetflixHudLauncher {
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

    fun launch(activity: Activity): Boolean {
        activity.startForegroundService(
            Intent(activity, RideTelemetryService::class.java).apply {
                action = RideTelemetryService.ACTION_START_HUD
            },
        )
        if (ExternalAppLauncher.launchNetflix(activity)) {
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

