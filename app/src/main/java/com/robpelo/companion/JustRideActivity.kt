package com.robpelo.companion

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Typeface
import android.os.Bundle
import android.os.IBinder
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.robpelo.companion.ride.RideSnapshot
import com.robpelo.companion.ride.RideState
import com.robpelo.companion.ride.RideTelemetryService
import com.robpelo.companion.telemetry.ConnectionState
import java.util.Locale

class JustRideActivity : Activity(), RideTelemetryService.Listener {
    private lateinit var connectionView: TextView
    private lateinit var elapsedView: TextView
    private lateinit var cadenceView: TextView
    private lateinit var resistanceView: TextView
    private lateinit var outputView: TextView
    private lateinit var speedView: TextView
    private lateinit var distanceView: TextView
    private lateinit var totalOutputView: TextView
    private lateinit var pauseResumeButton: Button

    private var rideService: RideTelemetryService? = null
    private var serviceBound = false
    private var lastRideState = RideState.IDLE

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? RideTelemetryService.LocalBinder
            if (localBinder == null) {
                connectionView.text = getString(R.string.state_format, "SERVICE BIND FAILED")
                connectionView.setTextColor(getColor(R.color.error))
                return
            }

            rideService = localBinder.getService().also { service ->
                service.addListener(this@JustRideActivity)
            }
            serviceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            rideService = null
            serviceBound = false
            connectionView.text = getString(R.string.state_format, "SERVICE DISCONNECTED")
            connectionView.setTextColor(getColor(R.color.error))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(buildContentView())
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, RideTelemetryService::class.java)
        startForegroundService(intent)
        serviceBound = bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        if (!serviceBound) {
            connectionView.text = getString(R.string.state_format, "SERVICE UNAVAILABLE")
            connectionView.setTextColor(getColor(R.color.error))
        }
    }

    override fun onStop() {
        val service = rideService
        if (service != null) {
            service.removeListener(this)
        }
        if (serviceBound) {
            unbindService(serviceConnection)
        }
        rideService = null
        serviceBound = false
        super.onStop()
    }

    override fun onRideStateChanged(
        connectionState: ConnectionState,
        snapshot: RideSnapshot,
    ) {
        lastRideState = snapshot.state
        connectionView.text = getString(R.string.state_format, connectionState.displayText())
        connectionView.setTextColor(
            getColor(
                if (connectionState is ConnectionState.Failed) {
                    R.color.error
                } else {
                    R.color.accent
                },
            ),
        )
        elapsedView.text = getString(R.string.elapsed_format, formatElapsed(snapshot.elapsedMs))
        cadenceView.text = getString(
            R.string.cadence_format,
            snapshot.cadenceRpm?.toString() ?: "--",
        )
        resistanceView.text = getString(
            R.string.resistance_format,
            snapshot.resistancePercent?.toString() ?: "--",
        )
        outputView.text = getString(
            R.string.output_format,
            snapshot.outputWatts?.toString() ?: "--",
        )
        speedView.text = getString(R.string.speed_format, snapshot.speedMph)
        distanceView.text = getString(R.string.distance_format, snapshot.distanceMiles)
        totalOutputView.text = getString(R.string.total_output_format, snapshot.totalOutputKj)
        pauseResumeButton.text = getString(
            if (snapshot.state == RideState.PAUSED) R.string.resume else R.string.pause,
        )
    }

    private fun buildContentView(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(36), dp(22), dp(36), dp(22))
            setBackgroundColor(getColor(R.color.background))
        }
        root.addView(textView(getString(R.string.just_ride_title), 34f, true))

        connectionView = textView(getString(R.string.state_format, "CONNECTING"), 18f, true)
        connectionView.setTextColor(getColor(R.color.accent))
        root.addView(connectionView, marginLayoutParams(topDp = 8))

        elapsedView = textView(getString(R.string.elapsed_format, "00:00"), 42f, true)
        root.addView(elapsedView, marginLayoutParams(topDp = 8))

        val primaryMetrics = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        cadenceView = metricView(getString(R.string.cadence_format, "--"))
        resistanceView = metricView(getString(R.string.resistance_format, "--"))
        outputView = metricView(getString(R.string.output_format, "--"))
        primaryMetrics.addView(cadenceView, weightedLayoutParams())
        primaryMetrics.addView(resistanceView, weightedLayoutParams())
        primaryMetrics.addView(outputView, weightedLayoutParams())
        root.addView(primaryMetrics, marginLayoutParams(topDp = 14))

        val secondaryMetrics = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        speedView = secondaryMetricView(getString(R.string.speed_format, 0.0))
        distanceView = secondaryMetricView(getString(R.string.distance_format, 0.0))
        totalOutputView = secondaryMetricView(getString(R.string.total_output_format, 0.0))
        secondaryMetrics.addView(speedView, weightedLayoutParams())
        secondaryMetrics.addView(distanceView, weightedLayoutParams())
        secondaryMetrics.addView(totalOutputView, weightedLayoutParams())
        root.addView(secondaryMetrics, marginLayoutParams(topDp = 12))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        pauseResumeButton = Button(this).apply {
            text = getString(R.string.pause)
            minWidth = dp(220)
            minHeight = dp(68)
            setOnClickListener {
                if (lastRideState == RideState.PAUSED) {
                    rideService?.resumeRide()
                } else {
                    rideService?.pauseRide()
                }
            }
        }
        controls.addView(pauseResumeButton)
        controls.addView(Button(this).apply {
            text = getString(R.string.end_ride)
            minWidth = dp(220)
            minHeight = dp(68)
            setOnClickListener {
                rideService?.endRide()
                finish()
            }
        }, marginLayoutParams(leftDp = 24))
        root.addView(controls, marginLayoutParams(topDp = 18))

        return root
    }

    private fun metricView(initialText: String): TextView =
        textView(initialText, 28f, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(22), dp(12), dp(22))
            setBackgroundColor(getColor(R.color.panel))
        }

    private fun secondaryMetricView(initialText: String): TextView =
        textView(initialText, 20f, false).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(14), dp(10), dp(14))
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
}

private fun ConnectionState.displayText(): String = when (this) {
    ConnectionState.Disconnected -> "DISCONNECTED"
    ConnectionState.Connecting -> "CONNECTING"
    ConnectionState.BoundWaitingForData -> "BOUND - WAITING FOR DATA"
    ConnectionState.Connected -> "CONNECTED"
    is ConnectionState.Failed -> "FAILED - $message"
}

private fun formatElapsed(elapsedMs: Long): String {
    val totalSeconds = elapsedMs / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
