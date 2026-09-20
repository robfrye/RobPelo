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
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.robpelo.companion.update.FirefoxRelease
import com.robpelo.companion.update.FirefoxUpdateState
import com.robpelo.companion.update.FirefoxUpdater

class HomeActivity : Activity() {
    private var launchNetflixAfterOverlayGrant = false
    private var launchYouTubeAfterOverlayGrant = false
    private var pendingFirefoxInstall: FirefoxRelease? = null
    private var firefoxUpdateState: FirefoxUpdateState? = null
    private lateinit var firefoxUpdater: FirefoxUpdater
    private lateinit var firefoxUpdateNotice: TextView
    private lateinit var firefoxUpdateButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firefoxUpdater = FirefoxUpdater(this)
        setContentView(buildContentView())
    }

    override fun onResume() {
        super.onResume()
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
        val pendingRelease = pendingFirefoxInstall
        if (pendingRelease != null && packageManager.canRequestPackageInstalls()) {
            pendingFirefoxInstall = null
            downloadFirefox(pendingRelease)
        } else {
            firefoxUpdater.checkIfDue(::renderFirefoxUpdateState)
        }
    }

    override fun onDestroy() {
        firefoxUpdater.shutdown()
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

        val tiles = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        tiles.addView(tile(getString(R.string.just_ride_title)) {
            startActivity(Intent(this, JustRideActivity::class.java))
        })
        tiles.addView(tile(getString(R.string.open_netflix)) {
            requestNetflixWithHud()
        }, marginLayoutParams(leftDp = 28))
        tiles.addView(tile(getString(R.string.open_youtube)) {
            requestYouTubeWithHud()
        }, marginLayoutParams(leftDp = 28))
        root.addView(tiles, marginLayoutParams(topDp = 48))

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
        firefoxUpdateButton = Button(this).apply {
            text = getString(R.string.check_firefox_updates)
            minWidth = dp(250)
            minHeight = dp(58)
            setOnClickListener { onFirefoxUpdateClicked() }
        }
        utilities.addView(firefoxUpdateButton, marginLayoutParams(leftDp = 24))
        root.addView(utilities, marginLayoutParams(topDp = 40))

        firefoxUpdateNotice = textView("", 17f, true).apply {
            visibility = View.GONE
            gravity = Gravity.CENTER
        }
        root.addView(firefoxUpdateNotice, marginLayoutParams(topDp = 12))
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

    private fun onFirefoxUpdateClicked() {
        val available = firefoxUpdateState as? FirefoxUpdateState.Available
        if (available == null) {
            firefoxUpdater.check(::renderFirefoxUpdateState)
            return
        }

        if (!packageManager.canRequestPackageInstalls()) {
            pendingFirefoxInstall = available.release
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
        downloadFirefox(available.release)
    }

    private fun downloadFirefox(release: FirefoxRelease) {
        firefoxUpdater.downloadAndVerify(release, ::renderFirefoxUpdateState)
    }

    private fun renderFirefoxUpdateState(state: FirefoxUpdateState) {
        if (isDestroyed) {
            return
        }
        firefoxUpdateState = state
        when (state) {
            FirefoxUpdateState.Checking -> {
                firefoxUpdateButton.isEnabled = false
                firefoxUpdateButton.text = getString(R.string.checking_firefox)
                firefoxUpdateNotice.visibility = View.GONE
            }
            is FirefoxUpdateState.Current -> {
                firefoxUpdateButton.isEnabled = true
                firefoxUpdateButton.text = getString(R.string.check_firefox_updates)
                firefoxUpdateNotice.text =
                    getString(R.string.firefox_current, state.installedVersion)
                firefoxUpdateNotice.setTextColor(getColor(R.color.secondary_text))
                firefoxUpdateNotice.visibility = View.VISIBLE
            }
            is FirefoxUpdateState.Available -> {
                firefoxUpdateButton.isEnabled = true
                firefoxUpdateButton.text = getString(
                    if (state.installedVersion == null) {
                        R.string.install_firefox
                    } else {
                        R.string.update_firefox
                    },
                    state.release.versionName,
                )
                firefoxUpdateNotice.text =
                    getString(R.string.firefox_update_available, state.release.versionName)
                firefoxUpdateNotice.setTextColor(getColor(R.color.accent))
                firefoxUpdateNotice.visibility = View.VISIBLE
            }
            is FirefoxUpdateState.Downloading -> {
                firefoxUpdateButton.isEnabled = false
                firefoxUpdateButton.text =
                    getString(R.string.downloading_firefox, state.versionName)
                firefoxUpdateNotice.visibility = View.GONE
            }
            is FirefoxUpdateState.ReadyToInstall -> {
                firefoxUpdateButton.isEnabled = true
                firefoxUpdateButton.text = getString(R.string.check_firefox_updates)
                openFirefoxInstaller(state)
            }
            is FirefoxUpdateState.Failed -> {
                firefoxUpdateButton.isEnabled = true
                firefoxUpdateButton.text = getString(R.string.check_firefox_updates)
                firefoxUpdateNotice.text =
                    getString(R.string.firefox_update_failed, state.message)
                firefoxUpdateNotice.setTextColor(getColor(R.color.error))
                firefoxUpdateNotice.visibility = View.VISIBLE
            }
        }
    }

    private fun openFirefoxInstaller(state: FirefoxUpdateState.ReadyToInstall) {
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(state.contentUri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(installIntent)
        } catch (exception: android.content.ActivityNotFoundException) {
            renderFirefoxUpdateState(
                FirefoxUpdateState.Failed("Android package installer is unavailable"),
            )
        } catch (exception: SecurityException) {
            renderFirefoxUpdateState(
                FirefoxUpdateState.Failed(exception.message ?: "installer launch denied"),
            )
        }
    }

    private fun tile(label: String, action: () -> Unit): TextView {
        val background = GradientDrawable().apply {
            setColor(getColor(R.color.panel))
            cornerRadius = dp(16).toFloat()
            setStroke(dp(2), getColor(R.color.accent))
        }
        return TextView(this).apply {
            text = label
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(getColor(R.color.primary_text))
            setTypeface(typeface, Typeface.BOLD)
            this.background = background
            minWidth = dp(350)
            minHeight = dp(220)
            setOnClickListener { action() }
        }
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
    }
}
