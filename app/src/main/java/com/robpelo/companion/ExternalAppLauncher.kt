package com.robpelo.companion

import android.content.Context
import android.content.ActivityNotFoundException
import android.util.Log

object ExternalAppLauncher {
    private const val NETFLIX_PACKAGE = "com.netflix.mediaclient"

    fun launchNetflix(context: Context): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(NETFLIX_PACKAGE)
            ?: return false
        return try {
            context.startActivity(launchIntent)
            true
        } catch (exception: ActivityNotFoundException) {
            Log.e(TAG, "Netflix launch activity was not found", exception)
            false
        } catch (exception: SecurityException) {
            Log.e(TAG, "Netflix launch was denied", exception)
            false
        }
    }

    private const val TAG = "RobPeloTelemetry"
}
