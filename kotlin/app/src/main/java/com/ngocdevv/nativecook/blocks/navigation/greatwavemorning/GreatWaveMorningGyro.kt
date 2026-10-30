package com.ngocdevv.nativecook.blocks.navigation.greatwavemorning

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/** Rotation-vector source matching Reanimated's ROTATION sensor pitch/roll values. */
internal class GreatWaveMorningGyro(context: Context) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val rotation = FloatArray(9)
    private val orientation = FloatArray(3)

    @Volatile var pitch = 0.0
        private set
    @Volatile var roll = 0.0
        private set

    fun start() {
        val source = sensor ?: return
        manager?.registerListener(this, source, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        manager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        SensorManager.getOrientation(rotation, orientation)
        pitch = -orientation[1].toDouble()
        roll = orientation[2].toDouble()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
