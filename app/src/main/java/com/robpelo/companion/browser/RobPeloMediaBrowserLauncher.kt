package com.robpelo.companion.browser

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Browser
import android.util.Log

class RobPeloMediaBrowserLauncher(
    private val context: Context,
) : StreamingBrowserLauncher {
    override fun launch(destination: StreamingDestination): BrowserLaunchResult {
        val unavailable = BrowserAvailability.validate(
            snapshot = packageSnapshot(),
            packageName = PACKAGE_NAME,
            minimumVersion = MINIMUM_VERSION_CODE,
            requireMatchingSignature = true,
        )
        if (unavailable != null) {
            return unavailable
        }

        val intent = Intent(ACTION_OPEN_MEDIA).apply {
            component = MEDIA_VIEWER_COMPONENT
            putExtra(EXTRA_SERVICE, destination.serviceId)
            putExtra(Browser.EXTRA_APPLICATION_ID, context.packageName)
        }
        return try {
            context.startActivity(intent)
            BrowserLaunchResult.Success
        } catch (exception: ActivityNotFoundException) {
            Log.e(TAG, "Media viewer activity was not found", exception)
            BrowserLaunchResult.MissingActivity(PACKAGE_NAME)
        } catch (exception: SecurityException) {
            Log.e(TAG, "Media viewer launch was denied", exception)
            BrowserLaunchResult.LaunchDenied(PACKAGE_NAME)
        } catch (exception: RuntimeException) {
            Log.e(TAG, "Media viewer launch failed", exception)
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
            packageManager.getActivityInfo(MEDIA_VIEWER_COMPONENT, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        return BrowserPackageSnapshot(
            packageName = PACKAGE_NAME,
            enabled = applicationInfo.enabled,
            versionCode = versionCode,
            signatureMatches = packageManager.checkSignatures(
                context.packageName,
                PACKAGE_NAME,
            ) == PackageManager.SIGNATURE_MATCH,
            activityPresent = activityInfo != null,
            activityEnabled = activityInfo?.enabled == true,
        )
    }

    companion object {
        const val PACKAGE_NAME = "com.robpelo.browser"
        const val ACTION_OPEN_MEDIA = "com.robpelo.browser.action.OPEN_MEDIA"
        const val EXTRA_SERVICE = "service"
        const val MINIMUM_VERSION_CODE = 429510404L

        val MEDIA_VIEWER_COMPONENT = ComponentName(
            PACKAGE_NAME,
            "$PACKAGE_NAME.MediaViewerActivity",
        )

        private const val TAG = "RobPeloTelemetry"
    }
}
