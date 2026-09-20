package com.robpelo.companion

import android.content.Context
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import android.util.Log

object ExternalAppLauncher {
    private const val NETFLIX_PACKAGE = "com.netflix.mediaclient"
    private val PELOTON_APP = ComponentName(
        "com.peloton.activity",
        "com.peloton.activation.ActivationActivity",
    )

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

    fun launchPeloton(context: Context): Boolean {
        val launchIntent = Intent(Intent.ACTION_MAIN).apply {
            component = PELOTON_APP
        }
        return try {
            context.startActivity(launchIntent)
            true
        } catch (exception: ActivityNotFoundException) {
            Log.e(TAG, "Peloton activity was not found", exception)
            false
        } catch (exception: SecurityException) {
            Log.e(TAG, "Peloton activity launch was denied", exception)
            false
        }
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
}
