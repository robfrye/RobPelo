package com.robpelo.companion.browser

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log

class TvBroLauncher(
    private val context: Context,
) : StreamingBrowserLauncher {
    override fun launch(destination: StreamingDestination): BrowserLaunchResult {
        val unavailable = BrowserAvailability.validate(
            snapshot = packageSnapshot(),
            packageName = PACKAGE_NAME,
            minimumVersion = 1,
            requireMatchingSignature = false,
        )
        if (unavailable != null) {
            return unavailable
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(destination.startUrl)).apply {
            component = HOME_COMPONENT
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        return try {
            context.startActivity(intent)
            BrowserLaunchResult.Success
        } catch (exception: ActivityNotFoundException) {
            Log.e(TAG, "TV Bro activity was not found", exception)
            BrowserLaunchResult.MissingActivity(PACKAGE_NAME)
        } catch (exception: SecurityException) {
            Log.e(TAG, "TV Bro launch was denied", exception)
            BrowserLaunchResult.LaunchDenied(PACKAGE_NAME)
        } catch (exception: RuntimeException) {
            Log.e(TAG, "TV Bro launch failed", exception)
            BrowserLaunchResult.LaunchFailure(
                PACKAGE_NAME,
                exception.message ?: exception.javaClass.simpleName,
            )
        }
    }

    private fun packageSnapshot(): BrowserPackageSnapshot? {
        val packageManager = context.packageManager
        val applicationInfo = try {
            packageManager.getApplicationInfo(PACKAGE_NAME, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }
        val packageInfo = try {
            packageManager.getPackageInfo(PACKAGE_NAME, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }
        val activityInfo = try {
            packageManager.getActivityInfo(HOME_COMPONENT, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
        @Suppress("DEPRECATION")
        return BrowserPackageSnapshot(
            packageName = PACKAGE_NAME,
            enabled = applicationInfo.enabled,
            versionCode = packageInfo.versionCode.toLong(),
            signatureMatches = true,
            activityPresent = activityInfo != null,
            activityEnabled = activityInfo?.enabled == true,
        )
    }

    private val StreamingDestination.startUrl: String
        get() = when (this) {
            StreamingDestination.NETFLIX -> "https://www.netflix.com/browse"
            StreamingDestination.YOUTUBE -> "https://m.youtube.com/"
            StreamingDestination.HBO_MAX -> "https://play.hbomax.com/"
            StreamingDestination.PRIME_VIDEO -> "https://www.primevideo.com/region/na/"
            StreamingDestination.APPLE_TV -> "https://tv.apple.com/"
        }

    companion object {
        const val PACKAGE_NAME = "com.phlox.tvwebbrowser"
        val HOME_COMPONENT = ComponentName(
            PACKAGE_NAME,
            "com.phlox.tvwebbrowser.activity.main.MainActivity",
        )

        private const val TAG = "RobPeloTelemetry"
    }
}
