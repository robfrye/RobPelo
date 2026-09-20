package com.robpelo.companion.ride

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.robpelo.companion.R
import java.util.Locale

class HudController(
    context: Context,
    private val onClose: () -> Unit,
) {
    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(WindowManager::class.java)

    private var rootView: View? = null
    private lateinit var cadenceView: TextView
    private lateinit var resistanceView: TextView
    private lateinit var outputView: TextView
    private lateinit var speedView: TextView
    private lateinit var distanceView: TextView
    private lateinit var elapsedView: TextView

    val isShowing: Boolean
        get() = rootView != null

    fun hasPermission(): Boolean = Settings.canDrawOverlays(appContext)

    fun show(): Boolean {
        if (isShowing) {
            return true
        }
        if (!Settings.canDrawOverlays(appContext)) {
            return false
        }

        val view = buildView()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(12)
        }

        return try {
            windowManager.addView(view, params)
            rootView = view
            true
        } catch (exception: WindowManager.BadTokenException) {
            android.util.Log.e(TAG, "Overlay window token was rejected", exception)
            false
        } catch (exception: SecurityException) {
            android.util.Log.e(TAG, "Overlay permission was rejected", exception)
            false
        }
    }

    fun update(snapshot: RideSnapshot) {
        if (!isShowing) {
            return
        }

        cadenceView.text = metric("CADENCE", snapshot.cadenceRpm?.toString() ?: "--")
        resistanceView.text = metric(
            "RESISTANCE",
            snapshot.resistancePercent?.toString() ?: "--",
        )
        outputView.text = metric(
            "OUTPUT",
            snapshot.outputWatts?.let { "$it W" } ?: "--",
        )
        speedView.text = metric("SPEED", String.format(Locale.US, "%.1f mph", snapshot.speedMph))
        distanceView.text = metric(
            "DISTANCE",
            String.format(Locale.US, "%.2f mi", snapshot.distanceMiles),
        )
        elapsedView.text = metric("TIME", formatElapsed(snapshot.elapsedMs))
    }

    fun hide() {
        val view = rootView ?: return
        try {
            windowManager.removeView(view)
        } catch (exception: IllegalArgumentException) {
            android.util.Log.w(TAG, "Overlay window was already removed", exception)
        } finally {
            rootView = null
        }
    }

    private fun buildView(): View {
        val background = GradientDrawable().apply {
            setColor(Color.argb(232, 16, 18, 22))
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), appContext.getColor(R.color.accent))
        }
        return LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(10), dp(12), dp(10))
            this.background = background

            addView(LinearLayout(appContext).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                cadenceView = hudText()
                resistanceView = hudText()
                outputView = hudText()
                addView(cadenceView)
                addView(resistanceView, spacedParams())
                addView(outputView, spacedParams())
                addView(TextView(appContext).apply {
                    text = appContext.getString(R.string.hud_close)
                    textSize = 15f
                    setTextColor(appContext.getColor(R.color.error))
                    setTypeface(typeface, Typeface.BOLD)
                    setPadding(dp(18), dp(8), dp(8), dp(8))
                    setOnClickListener { onClose() }
                })
            })

            addView(LinearLayout(appContext).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                speedView = hudText()
                distanceView = hudText()
                elapsedView = hudText()
                addView(speedView)
                addView(distanceView, spacedParams())
                addView(elapsedView, spacedParams())
            })
        }
    }

    private fun hudText(): TextView = TextView(appContext).apply {
        textSize = 17f
        setTextColor(appContext.getColor(R.color.primary_text))
        setTypeface(typeface, Typeface.BOLD)
    }

    private fun spacedParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            marginStart = dp(24)
        }

    private fun metric(label: String, value: String): String = "$label  $value"

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

    private fun dp(value: Int): Int =
        (value * appContext.resources.displayMetrics.density).toInt()

    private companion object {
        const val TAG = "RobPeloTelemetry"
    }
}
