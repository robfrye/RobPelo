package com.robpelo.companion

import android.content.Context
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.robpelo.companion.browser.TvBroLauncher

object ExternalAppLauncher {
    private val PELOTON_APP = ComponentName(
        "com.peloton.activity",
        "com.peloton.activation.ActivationActivity",
    )

    fun launchPeloton(context: Context): Boolean =
        launchIntent(
            context,
            Intent(Intent.ACTION_MAIN).setComponent(PELOTON_APP),
            "Peloton",
        )

    fun launchTvBroSetup(context: Context): Boolean =
        launchIntent(
            context = context,
            intent = Intent(Intent.ACTION_MAIN).setComponent(TvBroLauncher.HOME_COMPONENT),
            destination = "TV Bro setup",
        )

    fun launchSettings(context: Context): Boolean =
        launchIntent(context, Intent(Settings.ACTION_SETTINGS), "Android Settings")

    private fun launchIntent(
        context: Context,
        intent: Intent,
        destination: String,
    ): Boolean = try {
        context.startActivity(intent)
        true
    } catch (exception: ActivityNotFoundException) {
        Log.e(TAG, "$destination activity was not found", exception)
        false
    } catch (exception: SecurityException) {
        Log.e(TAG, "$destination launch was denied", exception)
        false
    }

    private const val TAG = "RobPeloTelemetry"
}
