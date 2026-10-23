// The block is built for Android 12 (API 31) and newer; the demo checks before showing it.
@file:androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.S)

package com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.console

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.anim.AnimationEngine
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.anim.Shared
import kotlin.math.max
import kotlin.math.min

/** Reanimated `SensorType.ROTATION` sample (pitch/roll used here). */
data class Rotation(val pitch: Double, val roll: Double, val yaw: Double)

/**
 * Reanimated's Android rotation sensor (ReanimatedSensor + Listener):
 * TYPE_ROTATION_VECTOR at `interval` ms (registered with interval·1000 µs and
 * throttled by wall clock), rotation matrix → getOrientation, reported as
 * yaw = −o[0], pitch = −o[1], roll = o[2] to match iOS. The app is portrait
 * only, so the interface-orientation adjustment is the identity.
 */
private class RotationSensor(context: Context, private val interval: Double, private val onSample: (Rotation) -> Unit) :
    SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private var lastRead = System.currentTimeMillis().toDouble()
    private val rotation = FloatArray(9)
    private val orientation = FloatArray(3)

    fun start() {
        sensor?.let { manager.registerListener(this, it, (interval * 1000).toInt()) }
    }

    fun stop() = manager.unregisterListener(this)

    override fun onSensorChanged(event: SensorEvent) {
        val current = System.currentTimeMillis().toDouble()
        if (current - lastRead < interval) return
        lastRead = current
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        SensorManager.getOrientation(rotation, orientation)
        onSample(Rotation(pitch = -orientation[1].toDouble(), roll = orientation[2].toDouble(), yaw = -orientation[0].toDouble()))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

/**
 * tilt.tsx: mounted only while the tilt effect is on and the screen is
 * focused (here: resumed), so the rotation sensor is not running otherwise.
 * Writes a normalized tilt (-1…1) relative to a slowly following baseline:
 * highlights answer a movement of the phone, then drift back to rest for
 * however the phone is being held.
 */
@Composable
fun TiltSource(tilt: Shared<Tilt>) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(context, lifecycle) {
        val sensorValue = Shared(Rotation(0.0, 0.0, 0.0))
        var base = Triple(0.0, 0.0, false) // roll, pitch, ready
        val reaction = AnimationEngine.reaction({ sensorValue.get() }) { r, previous ->
            // A Reanimated reaction runs when its input changes, not every frame.
            if (r === previous) return@reaction
            val (bRoll, bPitch, ready) = base
            val roll = if (ready) bRoll + (r.roll - bRoll) * 0.02 else r.roll
            val pitch = if (ready) bPitch + (r.pitch - bPitch) * 0.02 else r.pitch
            base = Triple(roll, pitch, true)
            val next = Tilt(
                x = max(-1.0, min(1.0, (r.roll - roll) / 0.35)),
                y = max(-1.0, min(1.0, (r.pitch - pitch) / 0.35)),
            )
            // An unchanged tilt draws the same pixels: skip the redraw.
            if (next != tilt.get()) tilt.set(next)
        }
        val sensor = RotationSensor(context, 16.0) { sensorValue.set(it) }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> sensor.start()
                Lifecycle.Event.ON_PAUSE -> sensor.stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            sensor.stop()
            AnimationEngine.removeReaction(reaction)
        }
    }
}
