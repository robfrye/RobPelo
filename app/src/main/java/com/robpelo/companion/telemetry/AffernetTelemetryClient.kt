package com.robpelo.companion.telemetry

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import android.util.Log
import com.onepeloton.affernetservice.BikeData
import com.onepeloton.affernetservice.IV1Callback
import com.onepeloton.affernetservice.IV1Interface
import java.util.concurrent.atomic.AtomicLong

class AffernetTelemetryClient(
    context: Context,
    private val listener: Listener,
) {
    interface Listener {
        fun onConnectionStateChanged(state: ConnectionState)
        fun onSample(sample: RawBikeSample, frameCount: Long)
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private var service: IV1Interface? = null
    private var bindRequested = false
    private val frameCount = AtomicLong()
    @Volatile private var hasReceivedFrame = false

    private val callback = object : IV1Callback.Stub() {
        override fun onSensorDataChange(bikeData: BikeData?) {
            if (bikeData == null) {
                reportFailure("Affernet returned an empty telemetry frame")
                return
            }

            val sample = BikeSampleMapper.from(bikeData)
            hasReceivedFrame = true
            val currentFrameCount = frameCount.incrementAndGet()
            Log.i(
                TAG,
                "frame=$currentFrameCount cadence=${sample.cadenceRpm} " +
                    "resistance=${sample.resistancePercent} " +
                    "rawPower=${sample.rawPower} watts=${sample.outputWatts}",
            )
            mainHandler.post {
                listener.onConnectionStateChanged(ConnectionState.Connected)
                listener.onSample(sample, currentFrameCount)
            }
        }

        override fun onSensorError(errorCode: Long) {
            reportFailure("Affernet sensor error $errorCode")
        }

        override fun onCalibrationStatus(status: Int, success: Boolean, timestamp: Long) {
            Log.i(
                TAG,
                "Ignoring unsolicited calibration status: status=$status " +
                    "success=$success timestamp=$timestamp",
            )
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.i(TAG, "Affernet service connected: $name")
            val connectedService = IV1Interface.Stub.asInterface(binder)
            if (connectedService == null) {
                reportFailure("Affernet returned a null Binder interface")
                return
            }

            service = connectedService
            try {
                connectedService.registerCallback(callback, CLIENT_ID)
                if (!hasReceivedFrame) {
                    reportState(ConnectionState.BoundWaitingForData)
                }

                try {
                    val appliedRate = connectedService.setCallbackReportRate(REPORT_RATE_MS)
                    Log.i(TAG, "Requested ${REPORT_RATE_MS}ms report rate; applied=$appliedRate")
                } catch (exception: RemoteException) {
                    Log.w(TAG, "Affernet rejected the optional report-rate request", exception)
                }
            } catch (exception: RemoteException) {
                reportFailure("Callback registration failed: ${exception.message}")
            } catch (exception: SecurityException) {
                reportFailure("Callback registration denied: ${exception.message}")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            reportState(ConnectionState.Disconnected)
            Log.w(TAG, "Affernet service disconnected: $name")
        }

        override fun onBindingDied(name: ComponentName?) {
            service = null
            reportFailure("Affernet binding died: $name")
        }

        override fun onNullBinding(name: ComponentName?) {
            service = null
            reportFailure("Affernet returned a null binding: $name")
        }
    }

    fun start() {
        if (bindRequested) {
            return
        }

        frameCount.set(0)
        hasReceivedFrame = false
        reportState(ConnectionState.Connecting)

        val intent = Intent(SERVICE_ACTION).setPackage(SERVICE_PACKAGE)
        try {
            bindRequested = appContext.bindService(
                intent,
                connection,
                Context.BIND_AUTO_CREATE,
            )
            if (!bindRequested) {
                reportFailure("Affernet service was not available")
            }
        } catch (exception: SecurityException) {
            bindRequested = false
            reportFailure("Affernet bind denied: ${exception.message}")
        } catch (exception: IllegalArgumentException) {
            bindRequested = false
            reportFailure("Affernet bind intent was rejected: ${exception.message}")
        }
    }

    fun stop() {
        val connectedService = service
        if (connectedService != null) {
            try {
                connectedService.unregisterCallback(callback, CLIENT_ID)
            } catch (exception: RemoteException) {
                Log.w(TAG, "Failed to unregister Affernet callback", exception)
            } catch (exception: SecurityException) {
                Log.w(TAG, "Affernet denied callback unregister", exception)
            }
        }

        if (bindRequested) {
            appContext.unbindService(connection)
        }

        service = null
        bindRequested = false
        hasReceivedFrame = false
        reportState(ConnectionState.Disconnected)
        Log.i(TAG, "Affernet telemetry stopped")
    }

    private fun reportFailure(message: String) {
        Log.e(TAG, message)
        reportState(ConnectionState.Failed(message))
    }

    private fun reportState(state: ConnectionState) {
        mainHandler.post { listener.onConnectionStateChanged(state) }
    }

    private companion object {
        const val TAG = "RobPeloTelemetry"
        const val SERVICE_PACKAGE = "com.onepeloton.affernetservice"
        const val SERVICE_ACTION = "com.onepeloton.affernetservice.IV1Interface"
        const val CLIENT_ID = "RobPeloTelemetryProbe"
        const val REPORT_RATE_MS = 1000
    }
}
