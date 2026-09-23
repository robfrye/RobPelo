package com.robpelo.companion

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.robpelo.companion.browser.BrowserLaunchResult
import com.robpelo.companion.browser.StreamingBrowserRouter
import com.robpelo.companion.browser.StreamingDestination
import com.robpelo.companion.browser.toUserMessage
import com.robpelo.companion.telemetry.AffernetTelemetryClient
import com.robpelo.companion.telemetry.ConnectionState
import com.robpelo.companion.telemetry.RawBikeSample

class DiagnosticActivity : Activity(), AffernetTelemetryClient.Listener {
    private lateinit var telemetryClient: AffernetTelemetryClient
    private lateinit var streamingBrowserRouter: StreamingBrowserRouter
    private lateinit var statusView: TextView
    private lateinit var cadenceView: TextView
    private lateinit var resistanceView: TextView
    private lateinit var outputView: TextView
    private lateinit var rawPowerView: TextView
    private lateinit var framesView: TextView
    private lateinit var lastUpdateView: TextView
    private var launchNetflixAfterOverlayGrant = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        telemetryClient = AffernetTelemetryClient(this, this)
        streamingBrowserRouter = StreamingBrowserRouter(this)
        setContentView(buildContentView())
    }

    override fun onStart() {
        super.onStart()
        telemetryClient.start()
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
    }

    override fun onStop() {
        telemetryClient.stop()
        super.onStop()
    }

    override fun onConnectionStateChanged(state: ConnectionState) {
        statusView.text = getString(R.string.state_format, state.displayText())
        statusView.setTextColor(
            getColor(
                if (state is ConnectionState.Failed) {
                    R.color.error
                } else {
                    R.color.accent
                },
            ),
        )
    }

    override fun onSample(sample: RawBikeSample, frameCount: Long) {
        cadenceView.text = getString(R.string.cadence_format, sample.cadenceRpm.toString())
        resistanceView.text = getString(
            R.string.resistance_format,
            sample.resistancePercent.toString(),
        )
        outputView.text = getString(R.string.output_format, sample.outputWatts.toString())
        rawPowerView.text = getString(R.string.raw_power_format, sample.rawPower.toString())
        framesView.text = getString(R.string.frames_format, frameCount)
        lastUpdateView.text = getString(
            R.string.last_update_format,
            "${sample.receivedAtElapsedRealtimeMs} ms monotonic",
        )
    }

    private fun buildContentView(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(40), dp(28), dp(40), dp(28))
            setBackgroundColor(getColor(R.color.background))
        }

        root.addView(textView(getString(R.string.probe_title), 34f, true))
        root.addView(textView(getString(R.string.probe_safety), 17f, false).apply {
            setTextColor(getColor(R.color.secondary_text))
            gravity = Gravity.CENTER
        })

        statusView = textView(getString(R.string.state_format, "DISCONNECTED"), 22f, true)
        statusView.setTextColor(getColor(R.color.accent))
        root.addView(statusView, marginLayoutParams(topDp = 22))

        val metricsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        cadenceView = metricView(getString(R.string.cadence_format, "--"))
        resistanceView = metricView(getString(R.string.resistance_format, "--"))
        outputView = metricView(getString(R.string.output_format, "--"))
        metricsRow.addView(cadenceView, weightedLayoutParams())
        metricsRow.addView(resistanceView, weightedLayoutParams())
        metricsRow.addView(outputView, weightedLayoutParams())
        root.addView(metricsRow, marginLayoutParams(topDp = 24))

        rawPowerView = textView(getString(R.string.raw_power_format, "--"), 18f, false)
        framesView = textView(getString(R.string.frames_format, 0L), 18f, false)
        lastUpdateView = textView(getString(R.string.last_update_format, "--"), 18f, false)
        root.addView(rawPowerView, marginLayoutParams(topDp = 20))
        root.addView(framesView)
        root.addView(lastUpdateView)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        controls.addView(Button(this).apply {
            text = getString(R.string.connect)
            minWidth = dp(180)
            minHeight = dp(64)
            setOnClickListener { telemetryClient.start() }
        })
        controls.addView(Button(this).apply {
            text = getString(R.string.disconnect)
            minWidth = dp(180)
            minHeight = dp(64)
            setOnClickListener { telemetryClient.stop() }
        }, marginLayoutParams(leftDp = 20))
        controls.addView(Button(this).apply {
            text = getString(R.string.open_just_ride)
            minWidth = dp(220)
            minHeight = dp(64)
            setOnClickListener {
                startActivity(android.content.Intent(this@DiagnosticActivity, JustRideActivity::class.java))
            }
        }, marginLayoutParams(leftDp = 20))
        controls.addView(Button(this).apply {
            text = getString(R.string.open_netflix)
            minWidth = dp(220)
            minHeight = dp(64)
            setOnClickListener { requestNetflixWithHud() }
        }, marginLayoutParams(leftDp = 20))
        root.addView(controls, marginLayoutParams(topDp = 24))

        return root
    }

    private fun metricView(initialText: String): TextView =
        textView(initialText, 26f, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(24), dp(16), dp(24))
            setBackgroundColor(getColor(R.color.panel))
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

    private fun weightedLayoutParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(8)
            marginEnd = dp(8)
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
        val result = VideoHudLauncher.launch(
            this,
            streamingBrowserRouter,
            StreamingDestination.NETFLIX,
        )
        if (result != BrowserLaunchResult.Success) {
            Toast.makeText(this, result.toUserMessage(this), Toast.LENGTH_LONG).show()
        }
    }
}

private fun ConnectionState.displayText(): String = when (this) {
    ConnectionState.Disconnected -> "DISCONNECTED"
    ConnectionState.Connecting -> "CONNECTING"
    ConnectionState.BoundWaitingForData -> "BOUND - WAITING FOR DATA"
    ConnectionState.Connected -> "CONNECTED"
    is ConnectionState.Failed -> "FAILED - $message"
}
