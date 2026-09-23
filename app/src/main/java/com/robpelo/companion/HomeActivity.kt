package com.robpelo.companion

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import com.robpelo.companion.browser.BrowserLaunchResult
import com.robpelo.companion.browser.BrowserRoute
import com.robpelo.companion.browser.StreamingBrowserRouter
import com.robpelo.companion.browser.StreamingDestination
import com.robpelo.companion.browser.toUserMessage
import com.robpelo.companion.update.TvBroRelease
import com.robpelo.companion.update.TvBroUpdateState
import com.robpelo.companion.update.TvBroUpdater

class HomeActivity : Activity() {
    private var destinationAfterOverlayGrant: StreamingDestination? = null
    private var pendingTvBroInstall: TvBroRelease? = null
    private var awaitingTvBroInstallResult = false
    private var tvBroUpdateState: TvBroUpdateState? = null
    private lateinit var tvBroUpdater: TvBroUpdater
    private lateinit var streamingBrowserRouter: StreamingBrowserRouter
    private lateinit var tvBroUpdateNotice: TextView
    private lateinit var tvBroUpdateButton: Button
    private lateinit var browserRouteButton: Button
    private lateinit var settingsPopup: PopupWindow

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tvBroUpdater = TvBroUpdater(this)
        streamingBrowserRouter = StreamingBrowserRouter(this)
        setContentView(buildContentView())
    }

    override fun onResume() {
        super.onResume()
        if (awaitingTvBroInstallResult) {
            awaitingTvBroInstallResult = false
            if (isTvBroInstalled()) {
                Toast.makeText(
                    this,
                    R.string.tvbro_setup_required,
                    Toast.LENGTH_LONG,
                ).show()
                ExternalAppLauncher.launchTvBroSetup(this)
                return
            }
        }
        val pendingDestination = destinationAfterOverlayGrant
        if (
            pendingDestination != null &&
            VideoHudLauncher.hasOverlayPermission(this)
        ) {
            destinationAfterOverlayGrant = null
            launchStreamingWithHud(pendingDestination)
        }
        val pendingRelease = pendingTvBroInstall
        if (pendingRelease != null && packageManager.canRequestPackageInstalls()) {
            pendingTvBroInstall = null
            downloadTvBro(pendingRelease)
        } else {
            tvBroUpdater.checkIfDue(::renderTvBroUpdateState)
        }
    }

    override fun onDestroy() {
        if (::settingsPopup.isInitialized) {
            settingsPopup.dismiss()
        }
        tvBroUpdater.shutdown()
        super.onDestroy()
    }

    private fun buildContentView(): View {
        val root = FrameLayout(this).apply {
            setBackgroundColor(getColor(R.color.background))
        }
        root.addView(
            ImageView(this).apply {
                setImageResource(R.drawable.home_background)
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = null
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(40), dp(24), dp(40), dp(24))
        }
        val tiles = GridLayout(this).apply {
            columnCount = 3
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
        }
        tiles.addView(tile(getString(R.string.just_ride_title)) {
            startActivity(Intent(this, JustRideActivity::class.java))
        }, tileLayoutParams())
        tiles.addView(tile(getString(R.string.open_netflix)) {
            requestStreamingWithHud(StreamingDestination.NETFLIX)
        }, tileLayoutParams())
        tiles.addView(tile(getString(R.string.open_youtube)) {
            requestStreamingWithHud(StreamingDestination.YOUTUBE)
        }, tileLayoutParams())
        tiles.addView(tile(getString(R.string.open_hbo_max)) {
            requestStreamingWithHud(StreamingDestination.HBO_MAX)
        }, tileLayoutParams())
        tiles.addView(tile(getString(R.string.open_prime_video)) {
            requestStreamingWithHud(StreamingDestination.PRIME_VIDEO)
        }, tileLayoutParams())
        tiles.addView(tile(getString(R.string.open_apple_tv)) {
            requestStreamingWithHud(StreamingDestination.APPLE_TV)
        }, tileLayoutParams())
        content.addView(tiles, marginLayoutParams(topDp = 16))

        content.addView(Button(this).apply {
            text = getString(R.string.open_peloton)
            minWidth = dp(220)
            minHeight = dp(58)
            setOnClickListener {
                if (!ExternalAppLauncher.launchPeloton(this@HomeActivity)) {
                    Toast.makeText(
                        this@HomeActivity,
                        R.string.peloton_launcher_unavailable,
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }, marginLayoutParams(topDp = 20))

        root.addView(
            content,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        settingsPopup = buildSettingsPopup()
        root.addView(ImageButton(this).apply {
            contentDescription = getString(R.string.open_settings_menu)
            setImageResource(R.drawable.ic_settings)
            imageTintList = ColorStateList.valueOf(getColor(R.color.primary_text))
            setPadding(dp(15), dp(15), dp(15), dp(15))
            background = roundedBackground(getColor(R.color.panel), dp(14), dp(1))
            setOnClickListener {
                if (settingsPopup.isShowing) {
                    settingsPopup.dismiss()
                } else {
                    settingsPopup.showAtLocation(
                        root,
                        Gravity.TOP or Gravity.END,
                        dp(32),
                        dp(92),
                    )
                }
            }
        }, FrameLayout.LayoutParams(dp(64), dp(64), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(24)
            marginEnd = dp(32)
        })
        return root
    }

    private fun buildSettingsPopup(): PopupWindow {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = roundedBackground(getColor(R.color.panel), dp(16), dp(1))
            elevation = dp(12).toFloat()
        }
        panel.addView(settingsActionButton(getString(R.string.open_settings)) {
            settingsPopup.dismiss()
            if (!ExternalAppLauncher.launchSettings(this@HomeActivity)) {
                Toast.makeText(
                    this@HomeActivity,
                    R.string.settings_unavailable,
                    Toast.LENGTH_LONG,
                ).show()
            }
        })
        panel.addView(settingsActionButton(getString(R.string.diagnostics)) {
            settingsPopup.dismiss()
            startActivity(Intent(this@HomeActivity, DiagnosticActivity::class.java))
        }, marginLayoutParams(topDp = 12))
        browserRouteButton = settingsActionButton("", ::toggleBrowserRoute)
        updateBrowserRouteButton()
        panel.addView(browserRouteButton, marginLayoutParams(topDp = 12))
        panel.addView(settingsActionButton(getString(R.string.open_media_browser_setup)) {
            settingsPopup.dismiss()
            val result = streamingBrowserRouter.launchMediaBrowserSetup()
            if (result != BrowserLaunchResult.Success) {
                Toast.makeText(
                    this@HomeActivity,
                    result.toUserMessage(this@HomeActivity),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }, marginLayoutParams(topDp = 12))
        tvBroUpdateButton = Button(this).apply {
            text = getString(R.string.check_firefox_updates)
            minWidth = dp(320)
            minHeight = dp(64)
            textSize = 18f
            setOnClickListener { onTvBroUpdateClicked() }
        }
        panel.addView(tvBroUpdateButton, marginLayoutParams(topDp = 12))

        tvBroUpdateNotice = textView("", 17f, true).apply {
            visibility = View.GONE
            gravity = Gravity.CENTER
        }
        panel.addView(tvBroUpdateNotice, marginLayoutParams(topDp = 12))

        return PopupWindow(
            panel,
            dp(360),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true,
        ).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = dp(12).toFloat()
        }
    }

    private fun settingsActionButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            minWidth = dp(320)
            minHeight = dp(64)
            textSize = 18f
            setOnClickListener { action() }
        }

    private fun roundedBackground(color: Int, radius: Int, strokeWidth: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
            setStroke(strokeWidth, getColor(R.color.accent))
        }

    private fun requestStreamingWithHud(destination: StreamingDestination) {
        if (!VideoHudLauncher.hasOverlayPermission(this)) {
            destinationAfterOverlayGrant = destination
            Toast.makeText(
                this,
                R.string.overlay_permission_required,
                Toast.LENGTH_LONG,
            ).show()
            VideoHudLauncher.openOverlaySettings(this)
            return
        }
        launchStreamingWithHud(destination)
    }

    private fun launchStreamingWithHud(destination: StreamingDestination) {
        val result = VideoHudLauncher.launch(this, streamingBrowserRouter, destination)
        if (result != BrowserLaunchResult.Success) {
            Toast.makeText(
                this,
                result.toUserMessage(this),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun toggleBrowserRoute() {
        val newRoute = when (streamingBrowserRouter.route) {
            BrowserRoute.MEDIA_BROWSER -> BrowserRoute.TV_BRO
            BrowserRoute.TV_BRO -> BrowserRoute.MEDIA_BROWSER
        }
        streamingBrowserRouter.setRoute(newRoute)
        updateBrowserRouteButton()
        settingsPopup.dismiss()
        Toast.makeText(
            this,
            if (newRoute == BrowserRoute.MEDIA_BROWSER) {
                R.string.media_browser_route_active
            } else {
                R.string.tvbro_route_active
            },
            Toast.LENGTH_LONG,
        ).show()
    }

    private fun updateBrowserRouteButton() {
        browserRouteButton.text = getString(
            if (streamingBrowserRouter.route == BrowserRoute.MEDIA_BROWSER) {
                R.string.use_tvbro_rollback
            } else {
                R.string.use_media_browser
            },
        )
    }

    private fun onTvBroUpdateClicked() {
        val available = tvBroUpdateState as? TvBroUpdateState.Available
        if (available == null) {
            tvBroUpdater.check(::renderTvBroUpdateState)
            return
        }

        if (!packageManager.canRequestPackageInstalls()) {
            pendingTvBroInstall = available.release
            Toast.makeText(
                this,
                R.string.firefox_install_permission,
                Toast.LENGTH_LONG,
            ).show()
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName"),
                ),
            )
            return
        }
        downloadTvBro(available.release)
    }

    private fun downloadTvBro(release: TvBroRelease) {
        tvBroUpdater.downloadAndVerify(release, ::renderTvBroUpdateState)
    }

    private fun renderTvBroUpdateState(state: TvBroUpdateState) {
        if (isDestroyed) {
            return
        }
        tvBroUpdateState = state
        when (state) {
            TvBroUpdateState.Checking -> {
                tvBroUpdateButton.isEnabled = false
                tvBroUpdateButton.text = getString(R.string.checking_firefox)
                tvBroUpdateNotice.visibility = View.GONE
            }
            is TvBroUpdateState.Current -> {
                tvBroUpdateButton.isEnabled = true
                tvBroUpdateButton.text = getString(R.string.check_firefox_updates)
                tvBroUpdateNotice.text =
                    getString(R.string.firefox_current, state.installedVersion)
                tvBroUpdateNotice.setTextColor(getColor(R.color.secondary_text))
                tvBroUpdateNotice.visibility = View.VISIBLE
            }
            is TvBroUpdateState.Available -> {
                tvBroUpdateButton.isEnabled = true
                tvBroUpdateButton.text = getString(
                    if (state.installedVersion == null) {
                        R.string.install_firefox
                    } else {
                        R.string.update_firefox
                    },
                    state.release.versionName,
                )
                tvBroUpdateNotice.text =
                    getString(R.string.firefox_update_available, state.release.versionName)
                tvBroUpdateNotice.setTextColor(getColor(R.color.accent))
                tvBroUpdateNotice.visibility = View.VISIBLE
            }
            is TvBroUpdateState.Downloading -> {
                tvBroUpdateButton.isEnabled = false
                tvBroUpdateButton.text =
                    getString(R.string.downloading_firefox, state.versionName)
                tvBroUpdateNotice.visibility = View.GONE
            }
            is TvBroUpdateState.ReadyToInstall -> {
                tvBroUpdateButton.isEnabled = true
                tvBroUpdateButton.text = getString(R.string.check_firefox_updates)
                openTvBroInstaller(state)
            }
            is TvBroUpdateState.Failed -> {
                tvBroUpdateButton.isEnabled = true
                tvBroUpdateButton.text = getString(R.string.check_firefox_updates)
                tvBroUpdateNotice.text =
                    getString(R.string.firefox_update_failed, state.message)
                tvBroUpdateNotice.setTextColor(getColor(R.color.error))
                tvBroUpdateNotice.visibility = View.VISIBLE
            }
        }
    }

    private fun openTvBroInstaller(state: TvBroUpdateState.ReadyToInstall) {
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(state.contentUri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            awaitingTvBroInstallResult = true
            startActivity(installIntent)
        } catch (exception: android.content.ActivityNotFoundException) {
            renderTvBroUpdateState(
                TvBroUpdateState.Failed("Android package installer is unavailable"),
            )
            awaitingTvBroInstallResult = false
        } catch (exception: SecurityException) {
            renderTvBroUpdateState(
                TvBroUpdateState.Failed(exception.message ?: "installer launch denied"),
            )
            awaitingTvBroInstallResult = false
        }
    }

    private fun isTvBroInstalled(): Boolean = try {
        packageManager.getPackageInfo(TV_BRO_PACKAGE, 0)
        true
    } catch (exception: android.content.pm.PackageManager.NameNotFoundException) {
        false
    }

    private fun tile(label: String, action: () -> Unit): TextView {
        val background = GradientDrawable().apply {
            setColor(getColor(R.color.panel))
            cornerRadius = dp(16).toFloat()
            setStroke(dp(2), getColor(R.color.accent))
        }
        return TextView(this).apply {
            text = label
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(getColor(R.color.primary_text))
            setTypeface(typeface, Typeface.BOLD)
            this.background = background
            setOnClickListener { action() }
        }
    }

    private fun tileLayoutParams(): GridLayout.LayoutParams =
        GridLayout.LayoutParams().apply {
            width = dp(280)
            height = dp(140)
            setMargins(dp(12), dp(12), dp(12), dp(12))
        }

    private fun textView(text: String, sizeSp: Float, bold: Boolean): TextView =
        TextView(this).apply {
            this.text = text
            textSize = sizeSp
            setTextColor(getColor(R.color.primary_text))
            if (bold) {
                setTypeface(typeface, Typeface.BOLD)
            }
        }

    private fun marginLayoutParams(
        topDp: Int = 0,
        leftDp: Int = 0,
    ): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = dp(topDp)
            marginStart = dp(leftDp)
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val TV_BRO_PACKAGE = "com.phlox.tvwebbrowser"
    }
}
