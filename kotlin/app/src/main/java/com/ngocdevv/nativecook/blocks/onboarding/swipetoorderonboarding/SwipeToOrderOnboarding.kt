package com.ngocdevv.nativecook.blocks.onboarding.swipetoorderonboarding

import android.app.Activity
import android.content.ContextWrapper
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
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
import kotlinx.coroutines.flow.first
import kotlin.math.min

@Composable
fun SwipeToOrderOnboarding(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current.density
    val reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    val resources = remember(context) { SwipeToOrderOnboardingArt(context) }
    val audio = remember(context, reduced) { if (reduced) null else SwipeToOrderOnboardingAudio(context) }
    val vibrator = remember(context) { context.getSystemService(Vibrator::class.java) }
    val driver = remember(autoPlay, reduced) {
        SwipeToOrderOnboardingDriver({ audio?.play(it) }) { kind ->
            if (Build.VERSION.SDK_INT >= 29) vibrator?.vibrate(VibrationEffect.createPredefined(if (kind == 0) VibrationEffect.EFFECT_TICK else VibrationEffect.EFFECT_CLICK))
            else vibrator?.vibrate(VibrationEffect.createOneShot(if (kind == 2) 24 else 12, 45))
        }
    }
    val tick = remember(driver) { mutableIntStateOf(0) }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
    DisposableEffect(audio) { onDispose { audio?.close() } }
    DisposableEffect(view) {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (previous != null) controller?.isAppearanceLightStatusBars = previous }
    }
    LaunchedEffect(driver, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (true) {
                if (reduced && !driver.awake) { snapshotFlow { driver.awake }.first { it }; previous = 0 }
                withFrameNanos { now ->
                    val dt = min(if (previous == 0L) 0.016 else (now-previous)/1e9, 0.05)
                    previous = now
                    driver.step(dt, autoPlay && !reduced, reduced)
                    tick.intValue++
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth.value
        Canvas(Modifier.fillMaxSize().semantics {
            contentDescription = "Taskloop. Client Call ABC Corp., 12 Hours Needed. Focus Block · Code Review. Leisure Reading, 2 Hours Needed. Take control of your schedule"
        }) {
            tick.intValue
            drawIntoCanvas {
                val c = it.nativeCanvas
                c.save(); c.scale(density,density)
                resources.draw(c,size.width/density,size.height/density,top,bottom,driver.frame,reduced)
                c.restore()
            }
        }
        Box(Modifier.offset(x = (width-92).dp, y = (top+8).dp).size(70.dp,36.dp).semantics { role = Role.Button; contentDescription = "Skip →" })
        Box(Modifier.offset(x = 20.dp, y = (maxHeight.value-bottom-84).dp).size((width-40).dp,62.dp)
            .semantics {
                role = Role.Button
                contentDescription = if (driver.done) "You're all set" else "Get started"
                onClick(label = "Swipe right, or double-tap, to get started") { driver.activate(); true }
            }
            .pointerInput(driver, width) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = down.position
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, start)
                    driver.begin(); down.consume()
                    try {
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(change.uptimeMillis,change.position)
                            driver.drag(((change.position.x-start.x)/density).toDouble(),(width-102).toDouble())
                            change.consume()
                        } while (change.pressed && event.changes.any { it.pressed })
                    } finally {
                        driver.end((tracker.calculateVelocity().x/density).toDouble(),(width-102).toDouble())
                    }
                }
            })
    }
}
