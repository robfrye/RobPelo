package com.robpelo.companion.browser

import android.content.Context
import com.robpelo.companion.R

fun BrowserLaunchResult.toUserMessage(context: Context): String =
    when (this) {
        BrowserLaunchResult.Success -> ""
        is BrowserLaunchResult.MissingPackage ->
            context.getString(R.string.streaming_browser_missing)
        is BrowserLaunchResult.DisabledPackage ->
            context.getString(R.string.streaming_browser_disabled)
        is BrowserLaunchResult.SignatureMismatch ->
            context.getString(R.string.streaming_browser_signature_mismatch)
        is BrowserLaunchResult.IncompatibleVersion ->
            context.getString(R.string.streaming_browser_incompatible)
        is BrowserLaunchResult.MissingActivity ->
            context.getString(R.string.streaming_browser_activity_missing)
        is BrowserLaunchResult.LaunchDenied ->
            context.getString(R.string.streaming_browser_launch_denied)
        is BrowserLaunchResult.LaunchFailure ->
            context.getString(R.string.streaming_browser_launch_failed, message)
    }
