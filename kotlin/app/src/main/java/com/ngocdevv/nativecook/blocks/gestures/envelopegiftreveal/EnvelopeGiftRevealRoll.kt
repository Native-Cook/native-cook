package com.ngocdevv.nativecook.blocks.gestures.envelopegiftreveal

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** The device's roll in radians, from the rotation sensor (Reanimated's ROTATION sensor, `roll`). */
internal class EnvelopeRoll {
    @Volatile
    var value = 0f
}

/** Listens to the rotation sensor while [enabled]; a device without one keeps the roll at 0. */
@Composable
internal fun rememberEnvelopeRoll(enabled: Boolean): EnvelopeRoll {
    val context = LocalContext.current
    val roll = remember { EnvelopeRoll() }
    DisposableEffect(context, enabled) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (!enabled || manager == null || sensor == null) {
            roll.value = 0f
            return@DisposableEffect onDispose {}
        }
        val matrix = FloatArray(9)
        val orientation = FloatArray(3)
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                SensorManager.getOrientation(matrix, orientation)
                // Reanimated normalizes Android to iOS, then adjusts to interface orientation.
                val pitch = -orientation[1]
                val sensorRoll = orientation[2]
                @Suppress("DEPRECATION")
                roll.value = when (windowManager?.defaultDisplay?.rotation) {
                    Surface.ROTATION_90 -> -pitch
                    Surface.ROTATION_180 -> -sensorRoll
                    Surface.ROTATION_270 -> pitch
                    else -> sensorRoll
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        // Match useAnimatedSensor(SensorType.ROTATION, { interval: 16 }) exactly (microseconds here).
        manager.registerListener(listener, sensor, 16_000)
        onDispose { manager.unregisterListener(listener) }
    }
    return roll
}
