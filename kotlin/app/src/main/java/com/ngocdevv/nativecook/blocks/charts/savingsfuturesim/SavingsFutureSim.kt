package com.ngocdevv.nativecook.blocks.charts.savingsfuturesim

import android.app.Activity
import android.content.ContextWrapper
import android.provider.Settings
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.min

@Composable
fun SavingsFutureSim(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val haptic = LocalHapticFeedback.current
    val vibrator = remember(context) { context.getSystemService(Vibrator::class.java) }
    val reduced = Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f) == 0f
    val art = remember(context) { SavingsFutureSimArt(context) }
    val audio = remember(context) { SavingsFutureSimAudio(context) }
    val driver = remember(autoPlay,reduced) { SavingsFutureSimDriver() }
    val frame = remember(driver) { mutableStateOf(driver.frame(reduced)) }
    DisposableEffect(audio) { onDispose { audio.release() } }
    DisposableEffect(view) {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window,view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }
    fun cue(value: String) { if (!reduced) audio.play(value) }
    fun tick() { cue("tick"); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    LaunchedEffect(driver,lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (true) {
                withFrameNanos { time ->
                    val dt = min(if (previous == 0L) 0.016 else (time-previous)/1e9,0.05)
                    previous = time
                    driver.now += dt
                    driver.state.step(dt,driver.now,autoPlay && !reduced)
                    frame.value = driver.frame(reduced)
                }
            }
        }
    }
    Canvas(Modifier.fillMaxSize().semantics {
        role = Role.Button
        contentDescription = "Joined waitlist. Your savings rate: 6 percent. See your savings grow. Simulate my future."
        onClick(label = "Opens the forecast.") {
            driver.state.takeOver(driver.now)
            driver.state.setOpen(!driver.state.open,driver.now)
            frame.value = driver.frame(reduced)
            true
        }
    }.pointerInput(driver,reduced) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val scale = size.width/736.0
            var point = down.position
            driver.begin(point.x/scale,point.y/scale)
            down.consume()
            try {
                do {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    point = change.position
                    if (change.pressed && change.position != change.previousPosition) {
                        if (driver.move(point.x/scale,point.y/scale)) tick()
                    }
                    change.consume()
                } while (event.changes.any { it.id == down.id && it.pressed })
            } finally {
                val sound = driver.end(point.x/scale,point.y/scale,reduced)
                if (sound != null) { cue(sound); vibrator?.vibrate(VibrationEffect.createOneShot(12,45)) }
                frame.value = driver.frame(reduced)
            }
        }
    }) {
        // Only drawing reads frame state; composition stays idle.
        drawIntoCanvas { art.draw(it.nativeCanvas,size.width,size.height,frame.value) }
    }
}
