package com.robpelo.companion

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.robpelo.companion.update.TvBroRelease
import com.robpelo.companion.update.TvBroUpdateState
import com.robpelo.companion.update.TvBroUpdater

class HomeActivity : Activity() {
    private var launchNetflixAfterOverlayGrant = false
    private var launchYouTubeAfterOverlayGrant = false
    private var pendingTvBroInstall: TvBroRelease? = null
    private var awaitingTvBroInstallResult = false
    private var tvBroUpdateState: TvBroUpdateState? = null
    private lateinit var tvBroUpdater: TvBroUpdater
    private lateinit var tvBroUpdateNotice: TextView
    private lateinit var tvBroUpdateButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tvBroUpdater = TvBroUpdater(this)
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
        if (
            launchNetflixAfterOverlayGrant &&
            VideoHudLauncher.hasOverlayPermission(this)
        ) {
            launchNetflixAfterOverlayGrant = false
            launchNetflixWithHud()
        }
        if (
            launchYouTubeAfterOverlayGrant &&
            VideoHudLauncher.hasOverlayPermission(this)
        ) {
            launchYouTubeAfterOverlayGrant = false
            launchYouTubeWithHud()
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
        tvBroUpdater.shutdown()
        super.onDestroy()
    }

    private fun buildContentView(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(56), dp(44), dp(56), dp(44))
            setBackgroundColor(getColor(R.color.background))
        }
        root.addView(textView(getString(R.string.home_title), 48f, true))
        root.addView(textView(getString(R.string.home_subtitle), 21f, false).apply {
            setTextColor(getColor(R.color.secondary_text))
        }, marginLayoutParams(topDp = 8))

        val tiles = GridLayout(this).apply {
            columnCount = 4
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
        }
        tiles.addView(tile(getString(R.string.just_ride_title)) {
            startActivity(Intent(this, JustRideActivity::class.java))
        }, tileLayoutParams())
        tiles.addView(tile(getString(R.string.open_netflix)) {
            requestNetflixWithHud()
        }, tileLayoutParams())
        tiles.addView(tile(getString(R.string.open_youtube)) {
            requestYouTubeWithHud()
        }, tileLayoutParams())
        root.addView(tiles, marginLayoutParams(topDp = 32))

        val utilities = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        utilities.addView(Button(this).apply {
            text = getString(R.string.diagnostics)
            minWidth = dp(220)
            minHeight = dp(58)
            setOnClickListener {
                startActivity(Intent(this@HomeActivity, DiagnosticActivity::class.java))
            }
        })
        utilities.addView(Button(this).apply {
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
        }, marginLayoutParams(leftDp = 24))
        utilities.addView(Button(this).apply {
            text = getString(R.string.open_settings)
            minWidth = dp(220)
            minHeight = dp(58)
            setOnClickListener {
                if (!ExternalAppLauncher.launchSettings(this@HomeActivity)) {
                    Toast.makeText(
                        this@HomeActivity,
                        R.string.settings_unavailable,
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }, marginLayoutParams(leftDp = 24))
        tvBroUpdateButton = Button(this).apply {
            text = getString(R.string.check_firefox_updates)
            minWidth = dp(250)
            minHeight = dp(58)
            setOnClickListener { onTvBroUpdateClicked() }
        }
        utilities.addView(tvBroUpdateButton, marginLayoutParams(leftDp = 24))
        root.addView(utilities, marginLayoutParams(topDp = 40))

        tvBroUpdateNotice = textView("", 17f, true).apply {
            visibility = View.GONE
            gravity = Gravity.CENTER
        }
        root.addView(tvBroUpdateNotice, marginLayoutParams(topDp = 12))
        return root
    }

    private fun requestNetflixWithHud() {
        if (!VideoHudLauncher.hasOverlayPermission(this)) {
            launchNetflixAfterOverlayGrant = true
            Toast.makeText(
                this,
                R.string.overlay_permission_required,
                Toast.LENGTH_LONG,
            ).show()
            VideoHudLauncher.openOverlaySettings(this)
            return
        }
        launchNetflixWithHud()
    }

    private fun launchNetflixWithHud() {
        if (!VideoHudLauncher.launchNetflix(this)) {
            Toast.makeText(this, R.string.netflix_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun requestYouTubeWithHud() {
        if (!VideoHudLauncher.hasOverlayPermission(this)) {
            launchYouTubeAfterOverlayGrant = true
            Toast.makeText(
                this,
                R.string.overlay_permission_required,
                Toast.LENGTH_LONG,
            ).show()
            VideoHudLauncher.openOverlaySettings(this)
            return
        }
        launchYouTubeWithHud()
    }

    private fun launchYouTubeWithHud() {
        if (!VideoHudLauncher.launchYouTube(this)) {
            Toast.makeText(this, R.string.youtube_unavailable, Toast.LENGTH_LONG).show()
        }
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
            width = dp(300)
            height = dp(170)
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
