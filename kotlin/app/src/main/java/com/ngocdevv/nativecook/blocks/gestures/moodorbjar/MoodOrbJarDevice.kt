package com.ngocdevv.nativecook.blocks.gestures.moodorbjar

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The phone's roll in radians (Reanimated's ROTATION sensor), read only while [enabled]. It stays 0 on a device without
 * a rotation sensor and under Reduce Motion, so the glass and the pile simply do not drift.
 */
@Composable
internal fun rememberMoodOrbJarRoll(enabled: Boolean): State<Float> {
    val context = LocalContext.current
    val roll = remember { mutableFloatStateOf(0f) }
    DisposableEffect(context, enabled) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (!enabled || manager == null || sensor == null) {
            roll.floatValue = 0f
            return@DisposableEffect onDispose { }
        }
        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientation)
                roll.floatValue = orientation[2]
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { manager.unregisterListener(listener) }
    }
    return roll
}

/** Maps the source's selection, light, medium, heavy impacts and warning to Android's haptic constants. */
internal fun View.performMoodOrbJarHaptic(kind: Int) {
    val feedback = when (kind) {
        MoodOrbJarHaptic.GRAB -> HapticFeedbackConstants.CLOCK_TICK
        MoodOrbJarHaptic.THROW, MoodOrbJarHaptic.LAND_SOFT -> HapticFeedbackConstants.VIRTUAL_KEY
        MoodOrbJarHaptic.LAND -> HapticFeedbackConstants.KEYBOARD_TAP
        MoodOrbJarHaptic.LAND_HARD -> HapticFeedbackConstants.LONG_PRESS
        MoodOrbJarHaptic.UNDO -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
        else -> return
    }
    performHapticFeedback(feedback)
}
