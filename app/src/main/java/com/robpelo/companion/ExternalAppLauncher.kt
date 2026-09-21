package com.robpelo.companion

import android.content.Context
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log

object ExternalAppLauncher {
    private const val TV_BRO_PACKAGE = "com.phlox.tvwebbrowser"
    private val PELOTON_APP = ComponentName(
        "com.peloton.activity",
        "com.peloton.activation.ActivationActivity",
    )
    private val TV_BRO_HOME = ComponentName(
        TV_BRO_PACKAGE,
        "com.phlox.tvwebbrowser.activity.main.MainActivity",
    )

    fun launchNetflix(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.netflix.com/browse")).apply {
            component = TV_BRO_HOME
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        return launchIntent(context, intent, "Netflix in TV Bro")
    }

    fun launchPeloton(context: Context): Boolean =
        launchIntent(
            context,
            Intent(Intent.ACTION_MAIN).setComponent(PELOTON_APP),
            "Peloton",
        )

    fun launchYouTubeInTvBro(context: Context): Boolean {
        val youtubeUri = Uri.parse("https://m.youtube.com")
        val intent = Intent(Intent.ACTION_VIEW, youtubeUri).apply {
            component = TV_BRO_HOME
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        return launchIntent(context, intent, "YouTube in TV Bro")
    }

    fun launchTvBroSetup(context: Context): Boolean =
        launchIntent(
            context,
            Intent(Intent.ACTION_MAIN).setComponent(TV_BRO_HOME),
            "TV Bro setup",
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
