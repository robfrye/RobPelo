package com.robpelo.companion

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class HomeActivity : Activity() {
    private var launchNetflixAfterOverlayGrant = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContentView())
    }

    override fun onResume() {
        super.onResume()
        if (
            launchNetflixAfterOverlayGrant &&
            NetflixHudLauncher.hasOverlayPermission(this)
        ) {
            launchNetflixAfterOverlayGrant = false
            launchNetflixWithHud()
        }
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
        root.addView(utilities, marginLayoutParams(topDp = 40))
        return root
    }

    private fun requestNetflixWithHud() {
        if (!NetflixHudLauncher.hasOverlayPermission(this)) {
            launchNetflixAfterOverlayGrant = true
            Toast.makeText(
                this,
                R.string.overlay_permission_required,
                Toast.LENGTH_LONG,
            ).show()
            NetflixHudLauncher.openOverlaySettings(this)
            return
        }
        launchNetflixWithHud()
    }

    private fun launchNetflixWithHud() {
        if (!NetflixHudLauncher.launch(this)) {
            Toast.makeText(this, R.string.netflix_unavailable, Toast.LENGTH_LONG).show()
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
            minWidth = dp(390)
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
}
