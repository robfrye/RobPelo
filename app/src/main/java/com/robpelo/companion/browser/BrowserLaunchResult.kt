package com.robpelo.companion.browser

sealed interface BrowserLaunchResult {
    data object Success : BrowserLaunchResult

    data class MissingPackage(val packageName: String) : BrowserLaunchResult

    data class DisabledPackage(val packageName: String) : BrowserLaunchResult

    data class SignatureMismatch(val packageName: String) : BrowserLaunchResult

    data class IncompatibleVersion(
        val packageName: String,
        val installedVersion: Long,
        val minimumVersion: Long,
    ) : BrowserLaunchResult

    data class MissingActivity(val packageName: String) : BrowserLaunchResult

    data class LaunchDenied(val packageName: String) : BrowserLaunchResult

    data class LaunchFailure(val packageName: String, val message: String) : BrowserLaunchResult
}
