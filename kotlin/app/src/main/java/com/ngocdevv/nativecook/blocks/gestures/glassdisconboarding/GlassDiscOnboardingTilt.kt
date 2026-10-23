package com.ngocdevv.nativecook.blocks.gestures.glassdisconboarding

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** The device tilt that moves the lens highlight; it stays still on emulators and under Reduce Motion. */
internal class GlassDiscOnboardingTilt {
    @Volatile var roll: Float = 0f
    @Volatile var pitch: Float = 0f
}

@Composable
internal fun rememberGlassDiscOnboardingTilt(enabled: Boolean): GlassDiscOnboardingTilt {
    val context = LocalContext.current
    val tilt = remember { GlassDiscOnboardingTilt() }
    DisposableEffect(context, enabled) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (!enabled || manager == null || sensor == null) {
            tilt.roll = 0f
            tilt.pitch = 0f
            return@DisposableEffect onDispose { }
        }
        val matrix = FloatArray(9)
        val orientation = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                SensorManager.getOrientation(matrix, orientation)
                tilt.roll = orientation[2]
                tilt.pitch = orientation[1]
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { manager.unregisterListener(listener) }
    }
    return tilt
}
