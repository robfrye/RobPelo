package com.robpelo.companion.ride

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import com.robpelo.companion.JustRideActivity
import com.robpelo.companion.R
import com.robpelo.companion.telemetry.AffernetTelemetryClient
import com.robpelo.companion.telemetry.ConnectionState
import com.robpelo.companion.telemetry.RawBikeSample
import java.util.concurrent.CopyOnWriteArraySet

class RideTelemetryService : Service(), AffernetTelemetryClient.Listener {
    interface Listener {
        fun onRideStateChanged(
            connectionState: ConnectionState,
            snapshot: RideSnapshot,
        )
    }

    inner class LocalBinder : Binder() {
        fun getService(): RideTelemetryService = this@RideTelemetryService
    }

    private val binder = LocalBinder()
    private val listeners = CopyOnWriteArraySet<Listener>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val session = RideSession(SystemClock::elapsedRealtime)
    private lateinit var telemetryClient: AffernetTelemetryClient
    private lateinit var hudController: HudController
    private var connectionState: ConnectionState = ConnectionState.Disconnected

    private val ticker = object : Runnable {
        override fun run() {
            notifyListeners()
            mainHandler.postDelayed(this, UI_UPDATE_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        telemetryClient = AffernetTelemetryClient(this, this)
        hudController = HudController(this, ::endRide)
        session.start()
        telemetryClient.start()
        mainHandler.post(ticker)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_END_RIDE) {
            endRide()
        } else if (intent?.action == ACTION_START_HUD) {
            if (!hudController.show()) {
                connectionState = ConnectionState.Failed("Overlay permission is unavailable")
                notifyListeners()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        mainHandler.removeCallbacks(ticker)
        hudController.hide()
        telemetryClient.stop()
        session.finish()
        listeners.clear()
        super.onDestroy()
    }

    override fun onConnectionStateChanged(state: ConnectionState) {
        connectionState = state
        notifyListeners()
    }

    override fun onSample(sample: RawBikeSample, frameCount: Long) {
        session.accept(sample)
        notifyListeners()
    }

    fun addListener(listener: Listener) {
        listeners += listener
        listener.onRideStateChanged(connectionState, session.snapshot())
    }

    fun removeListener(listener: Listener) {
        listeners -= listener
    }

    fun pauseRide() {
        session.pause()
        notifyListeners()
    }

    fun resumeRide() {
        session.resume()
        notifyListeners()
    }

    fun endRide() {
        session.finish()
        notifyListeners()
        stopSelf()
    }

    private fun notifyListeners() {
        if (hudController.isShowing && !hudController.hasPermission()) {
            hudController.hide()
            connectionState = ConnectionState.Failed("Overlay permission was revoked")
            stopSelf()
            return
        }

        val snapshot = session.snapshot()
        hudController.update(snapshot)
        listeners.forEach { listener ->
            listener.onRideStateChanged(connectionState, snapshot)
        }
    }

    private fun createNotificationChannel() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.ride_notification_title),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val returnIntent = Intent(this, JustRideActivity::class.java)
        val returnPendingIntent = PendingIntent.getActivity(
            this,
            0,
            returnIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = Intent(this, RideTelemetryService::class.java).apply {
            action = ACTION_END_RIDE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getString(R.string.ride_notification_title))
            .setContentText(getString(R.string.ride_notification_active))
            .setContentIntent(returnPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                Notification.Action.Builder(
                    null,
                    getString(R.string.end_ride),
                    stopPendingIntent,
                ).build(),
            )
            .build()
    }

    companion object {
        const val ACTION_END_RIDE = "com.robpelo.companion.action.END_RIDE"
        const val ACTION_START_HUD = "com.robpelo.companion.action.START_HUD"
        private const val NOTIFICATION_CHANNEL_ID = "active_ride_silent_v2"
        private const val NOTIFICATION_ID = 1001
        private const val UI_UPDATE_INTERVAL_MS = 500L
    }
}
