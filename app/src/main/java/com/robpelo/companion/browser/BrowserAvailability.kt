package com.robpelo.companion.browser

data class BrowserPackageSnapshot(
    val packageName: String,
    val enabled: Boolean,
    val versionCode: Long,
    val signatureMatches: Boolean,
    val activityPresent: Boolean,
    val activityEnabled: Boolean,
)

object BrowserAvailability {
    fun validate(
        snapshot: BrowserPackageSnapshot?,
        packageName: String,
        minimumVersion: Long,
        requireMatchingSignature: Boolean,
    ): BrowserLaunchResult? {
        if (snapshot == null) {
            return BrowserLaunchResult.MissingPackage(packageName)
        }
        if (!snapshot.enabled) {
            return BrowserLaunchResult.DisabledPackage(packageName)
        }
        if (requireMatchingSignature && !snapshot.signatureMatches) {
            return BrowserLaunchResult.SignatureMismatch(packageName)
        }
        if (snapshot.versionCode < minimumVersion) {
            return BrowserLaunchResult.IncompatibleVersion(
                packageName,
                snapshot.versionCode,
                minimumVersion,
            )
        }
        if (!snapshot.activityPresent) {
            return BrowserLaunchResult.MissingActivity(packageName)
        }
        if (!snapshot.activityEnabled) {
            return BrowserLaunchResult.DisabledPackage(packageName)
        }
        return null
    }
}
