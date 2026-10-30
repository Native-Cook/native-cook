package com.ngocdevv.nativecook.blocks.cards.blacklightflightpassport

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.exp
import kotlin.math.min


/** Rotation-vector pitch/roll use the RN gains and its drifting baseline, rather than absolute tilt. */
internal class BlacklightFlightPassportGyro(context: Context) : SensorEventListener {
    private val manager = requireNotNull(context.getSystemService(SensorManager::class.java))
    private val sensor = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val matrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private var pitch = 0.0
    private var roll = 0.0
    private var baselinePitch = 0.0
    private var baselineRoll = 0.0
    private var initialized = false
    var x = 0.0
        private set
    var y = 0.0
        private set

    fun start() { sensor?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) } }

    fun stop() { manager.unregisterListener(this) }
    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(matrix, event.values)
        SensorManager.getOrientation(matrix, orientation)
        pitch = orientation[1].toDouble()
        roll = orientation[2].toDouble()
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    fun step(dt: Double) {
        if (!initialized && (pitch != 0.0 || roll != 0.0)) {
            initialized = true
            baselinePitch = pitch
            baselineRoll = roll
        }
        val baselineGain = min(1.0, dt * 0.6)
        baselinePitch += (pitch - baselinePitch) * baselineGain
        baselineRoll += (roll - baselineRoll) * baselineGain
        val smoothing = 1 - exp(-dt * 8)
        x += (((roll - baselineRoll) / 0.35).coerceIn(-1.0, 1.0) - x) * smoothing
        y += (((pitch - baselinePitch) / 0.35).coerceIn(-1.0, 1.0) - y) * smoothing
    }
}
