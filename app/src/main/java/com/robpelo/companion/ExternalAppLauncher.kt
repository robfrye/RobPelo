package com.robpelo.companion

import android.content.Context
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Browser
import android.provider.Settings
import android.util.Log

object ExternalAppLauncher {
    private const val NETFLIX_PACKAGE = "com.netflix.mediaclient"
    private const val FIREFOX_PACKAGE = "org.mozilla.firefox"
    private val PELOTON_APP = ComponentName(
        "com.peloton.activity",
        "com.peloton.activation.ActivationActivity",
    )
    private val FIREFOX_HOME = ComponentName(
        FIREFOX_PACKAGE,
        "org.mozilla.fenix.HomeActivity",
    )

    fun launchNetflix(context: Context): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(NETFLIX_PACKAGE)
            ?: return false
        return launchIntent(context, launchIntent, "Netflix")
    }

    fun launchPeloton(context: Context): Boolean =
        launchIntent(
            context,
            Intent(Intent.ACTION_MAIN).setComponent(PELOTON_APP),
            "Peloton",
        )

    fun launchYouTubeInFirefox(context: Context): Boolean {
        val youtubeUri = Uri.parse("https://m.youtube.com")
        val reuseIntent = Intent(Intent.ACTION_VIEW, youtubeUri).apply {
            component = FIREFOX_HOME
            addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
            )
            putExtra(Browser.EXTRA_APPLICATION_ID, YOUTUBE_APPLICATION_ID)
            putExtra(Browser.EXTRA_CREATE_NEW_TAB, false)
        }
        if (launchIntent(context, reuseIntent, "YouTube in Firefox")) {
            return true
        }

        val fallbackIntent = Intent(Intent.ACTION_VIEW, youtubeUri).apply {
            setPackage(FIREFOX_PACKAGE)
            putExtra(Browser.EXTRA_APPLICATION_ID, YOUTUBE_APPLICATION_ID)
            putExtra(Browser.EXTRA_CREATE_NEW_TAB, false)
        }
        return launchIntent(context, fallbackIntent, "YouTube in Firefox")
    }

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
    private const val YOUTUBE_APPLICATION_ID = "com.robpelo.companion.youtube"
}
