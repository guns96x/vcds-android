package com.vag.vcdsandroid.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

class PhoneBarometerProvider(
    context: Context,
    val filter: BarometerMedianFilter = BarometerMedianFilter()
) : SensorEventListener {

    companion object {
        private const val TAG = "PhoneBaro"
    }

    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    val pressureSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)
    val isSensorAvailable: Boolean = (pressureSensor != null)

    private var isListening = false
    private val mainHandler = Handler(Looper.getMainLooper())

    var onReadingChanged: ((PhoneBaroReading) -> Unit)? = null

    @Synchronized
    fun start(): Boolean {
        if (!isSensorAvailable || isListening) return false
        val s = pressureSensor ?: return false
        val sm = sensorManager ?: return false

        filter.clear()
        val ok = sm.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL, mainHandler)
        isListening = ok
        Log.i(TAG, "PhoneBarometerProvider registered: $ok (sensor: ${s.name})")
        return ok
    }

    @Synchronized
    fun stop() {
        if (!isListening) return
        try {
            sensorManager?.unregisterListener(this)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering pressure sensor: ${e.message}")
        }
        isListening = false
        filter.clear()
        Log.i(TAG, "PhoneBarometerProvider stopped and cleared.")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.values.isEmpty()) return
        val rawHpa = event.values[0].toDouble()
        val monoNs = if (event.timestamp > 0L) event.timestamp else SystemClock.elapsedRealtimeNanos()
        filter.addSample(rawHpa, monoNs)
        val reading = filter.getMedianReading(SystemClock.elapsedRealtimeNanos())
        onReadingChanged?.invoke(reading)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun getReading(nowNs: Long = SystemClock.elapsedRealtimeNanos()): PhoneBaroReading {
        if (!isSensorAvailable) {
            return PhoneBaroReading(valueMbar = null, available = false, fresh = false, ageMs = 0L)
        }
        return filter.getMedianReading(nowNs)
    }
}
