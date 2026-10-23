package com.ngocdevv.nativecook.blocks.gestures.tiltposterparallax

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun TiltPosterParallaxSensor(engine: TiltPosterParallaxEngine, enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(engine, enabled, context) {
        if (!enabled) {
            return@DisposableEffect onDispose { }
        }
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val matrix = FloatArray(9)
        val orientation = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                SensorManager.getOrientation(matrix, orientation)
                engine.updateSensor(
                    roll = orientation[2].toDouble(),
                    pitch = orientation[1].toDouble(),
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (manager != null && sensor != null) {
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose {
            manager?.unregisterListener(listener)
        }
    }
}
