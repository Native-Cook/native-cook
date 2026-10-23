package com.ngocdevv.nativecook.blocks.gestures.pullcordtheme

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** The phone's roll in radians (Reanimated's ROTATION sensor), 0 when there is no sensor or Reduce Motion is on. */
internal class PullCordThemeTilt {
    @Volatile
    var roll = 0.0
}

/** Listens to the rotation-vector sensor while [enabled] and the composition is alive. */
@Composable
internal fun rememberPullCordThemeTilt(enabled: Boolean): PullCordThemeTilt {
    val context = LocalContext.current
    val tilt = remember { PullCordThemeTilt() }
    DisposableEffect(context, enabled) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (!enabled || manager == null || sensor == null) {
            tilt.roll = 0.0
            return@DisposableEffect onDispose { }
        }
        val rotation = FloatArray(9)
        val orientation = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                SensorManager.getOrientation(rotation, orientation)
                tilt.roll = orientation[2].toDouble()
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { manager.unregisterListener(listener) }
    }
    return tilt
}
