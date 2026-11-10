package com.ngocdevv.nativecook.blocks.sheets.messagebottleshare

import android.app.Activity
import android.content.ContextWrapper
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import com.ngocdevv.nativecook.blocks.sheets.messagebottleshare.MessageBottleShareMotion as M
import kotlin.math.*

@Composable
fun MessageBottleShare(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    val art = remember(context) { MessageBottleShareArt(context) }
    val vibrator = remember(context) { context.getSystemService(Vibrator::class.java) }
    val driver = remember(autoPlay, reduced) { MessageBottleShareDriver(autoPlay, reduced) }
    val tick = remember(driver) { mutableIntStateOf(0) }
    var revision by remember(driver) { mutableIntStateOf(0) }
    var sent by remember(driver) { mutableStateOf(false) }
    var pressedTarget by remember { mutableIntStateOf(0) }
    val press = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val top = WindowInsets.statusBars.getTop(density)/density.density.toDouble()
    val bottom = WindowInsets.navigationBars.getBottom(density)/density.density.toDouble()
    DisposableEffect(view) {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }
    DisposableEffect(driver, vibrator) {
        driver.cue = { kind ->
            if (Build.VERSION.SDK_INT >= 29 && kind != 2) vibrator?.vibrate(VibrationEffect.createPredefined(
                if (kind == 1) VibrationEffect.EFFECT_HEAVY_CLICK else VibrationEffect.EFFECT_TICK))
            else if (kind == 2) vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 12, 50, 18), intArrayOf(0, 70, 0, 110), -1))
            else vibrator?.vibrate(VibrationEffect.createOneShot(if (kind == 1) 18L else 12L, if (kind == 1) 90 else 45))
        }
        onDispose { driver.cue = null }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val l = remember(maxWidth, maxHeight, top, bottom) { M.Layout(maxWidth.value.toDouble(), maxHeight.value.toDouble(), top, bottom) }
        fun refresh() {
            driver.step(0.0, l, autoPlay)
            sent = driver.frame.sent
            tick.intValue++
            revision++
        }
        LaunchedEffect(driver, l, revision, lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                var previous = 0L
                do {
                    withFrameNanos { nanos ->
                        val dt = if (previous == 0L) 0.0 else min(0.5, max(0.0, (nanos-previous)/1e9))
                        previous = nanos
                        val f = driver.step(dt, l, autoPlay)
                        if (sent != f.sent) sent = f.sent
                        tick.intValue++
                    }
                } while (driver.animating)
            }
        }
        Canvas(Modifier.fillMaxSize().pointerInput(driver, l) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val start = down.position
                val x = start.x/density.density; val y = start.y/density.density
                val target = when {
                    x >= 24 && x <= l.w-24 && y >= l.ctaTop && y <= l.ctaTop+56 -> 2
                    x >= 4 && x <= 90 && y >= l.top-4 && y <= l.top+48 -> 1
                    !driver.frame.sent && x >= l.homeX-l.scale*1.3 && x <= l.homeX+l.scale*1.3 && y >= l.homeY-l.scale*0.8 && y <= l.homeY+l.scale*0.8 -> 3
                    else -> 0
                }
                if (target == 0) return@awaitEachGesture
                down.consume()
                val tracker = VelocityTracker()
                tracker.addPosition(down.uptimeMillis, start)
                var end = start
                if (target == 3) { driver.begin(l); refresh() }
                else {
                    pressedTarget = target
                    scope.launch { press.animateTo(1f, tween(150, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f))) }
                }
                try {
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        end = change.position
                        tracker.addPosition(change.uptimeMillis, end)
                        if (target == 3 && change.pressed) {
                            driver.drag(((end.x-start.x)/density.density).toDouble(), ((end.y-start.y)/density.density).toDouble())
                        }
                        change.consume()
                    } while (change.pressed && event.changes.any { it.pressed })
                } finally {
                    if (target == 3) {
                        driver.end(((end.y-start.y)/density.density).toDouble(), (tracker.calculateVelocity().y/density.density).toDouble())
                    } else {
                        scope.launch {
                            press.animateTo(0f, tween(150, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)))
                            pressedTarget = 0
                        }
                        val ex = end.x/density.density; val ey = end.y/density.density
                        if (target == 2 && ex >= 8 && ex <= l.w-8 && ey >= l.ctaTop-16 && ey <= l.ctaTop+72) driver.press(l)
                    }
                    refresh()
                }
            }
        }) {
            tick.intValue
            drawIntoCanvas {
                val canvas = it.nativeCanvas
                canvas.save()
                canvas.scale(density.density, density.density)
                art.draw(canvas, l, driver.frame, driver.t, driver.sentChanged, driver.now, pressedTarget, press.value.toDouble(), reduced)
                canvas.restore()
            }
        }
        Box(Modifier.offset(8.dp, l.top.dp).size(80.dp, 44.dp).semantics {
            role = Role.Button; contentDescription = "Cancel"
            onClick { true }
        })
        Box(Modifier.offset(24.dp, l.titleTop.dp).size((l.w-48).dp, 48.dp).semantics {
            heading(); contentDescription = if (sent) "On its way" else "Cast your letter"
        })
        Box(Modifier.offset(24.dp, (l.titleTop+50).dp).size((l.w-48).dp, 20.dp).semantics {
            contentDescription = if (sent) "Maya will find it at dawn." else "To Maya · 214 words · written at the lake"
        })
        if (!sent) Box(Modifier.offset((l.homeX-l.scale*1.3).dp, (l.homeY-l.scale*0.8).dp)
            .size((l.scale*2.6).dp, (l.scale*1.6).dp).semantics {
                role = Role.Button; contentDescription = "Bottle with your letter"
                onClick(label = "Throws the bottle out to sea and sends the letter") { driver.activate(l); refresh(); true }
            })
        Box(Modifier.offset(24.dp, l.ctaTop.dp).size((l.w-48).dp, 56.dp).semantics {
            role = Role.Button; contentDescription = if (sent) "Write Another" else "Cast Off"
            onClick { driver.press(l); refresh(); true }
        })
        if (!sent) Box(Modifier.offset(0.dp, l.hintTop.dp).size(l.w.dp, 20.dp).semantics { contentDescription = "or throw the bottle" })
    }
}
