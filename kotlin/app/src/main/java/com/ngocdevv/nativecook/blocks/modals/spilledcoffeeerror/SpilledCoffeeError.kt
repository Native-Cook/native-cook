package com.ngocdevv.nativecook.blocks.modals.spilledcoffeeerror

import android.app.Activity
import android.content.ContextWrapper
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.*

/** Resting error screen and reversible retry, scaled by width from the 402×874 reference. */
@Composable
fun SpilledCoffeeError(autoPlay: Boolean = false, previewTime: Double? = null) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(view) {
        val activity = generateSequence(view.context) {
            (it as? ContextWrapper)?.baseContext
        }

        .filterIsInstance<Activity>().firstOrNull()
        val controller = activity?.let {
            WindowCompat.getInsetsController(it.window, view)
        }

        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose {
            if (previous != null) controller?.isAppearanceLightStatusBars = previous
        }
    }

    val reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    val auto = autoPlay && !reduced
    val art = remember(context) {
        SpilledCoffeeErrorArt(context)
    }

    val audio = remember(context, reduced) {
        SpilledCoffeeErrorAudio(context, !reduced)
    }

    DisposableEffect(audio) {
        onDispose {
            audio.close()
        }
    }

    val driver = remember(auto, reduced, previewTime) {
        SpilledCoffeeErrorDriver(previewTime ?: 0.0)
    }

    val clock = remember(driver) {
        doubleArrayOf(0.0)
    }

    val frame = remember(driver) {
        mutableStateOf(SpilledCoffeeErrorMotion.poseAt(previewTime ?: 0.0))
    }

    LaunchedEffect(driver, lifecycle, audio) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            var previousTheta = 90.0
            while (true) withFrameNanos {
                nanos ->
                try {
                    val dt = min(if (previous == 0L) 0.016 else (nanos - previous) / 1e9, 0.05)
                    previous = nanos
                    clock[0] += dt
                    if (previewTime == null) driver.step(dt, clock[0], auto)
                    val pose = driver.pose(clock[0])
                    if (reduced) {
                        pose.rewind = 0.0
                        pose.steam = 0.0
                    }
    
                    if (driver.mode == 2 && driver.retryAt < 0) {
                        if (previousTheta < 86 && pose.theta >= 86) {
                            audio.play("tip")
                            audio.buzz(1)
                        }
                        else if (previousTheta > 3 && pose.theta <= 3 && driver.dragging == 0) {
                            audio.play("clack")
                            audio.buzz(0)
                        }
                    }
    
                    previousTheta = pose.theta
                    // State is read in drawing only, avoiding per-frame composition.
                    frame.value = pose
                } catch (failure: RuntimeException) {
                    // Like RN's guarded frame callback, a bad frame must not stop future frames.
                    android.util.Log.e("SpilledCoffeeError", "Frame failed", failure)
                }
            }
        }
    }

    Canvas(Modifier.fillMaxSize().semantics {
        role = Role.Button
        contentDescription = "Error! Something went wrong, please try again later. Retry."
        onClick(label = "Tries again.") {
            driver.takeOver(clock[0])
            driver.retry(clock[0])
            true
        }
    }

    .pointerInput(driver, reduced, previewTime, audio) {
        if (previewTime != null) return@pointerInput
        fun toPin(x: Float, y: Float): SpilledCoffeeErrorArt.P2 {
            val scale = size.width / 402.0
            return SpilledCoffeeErrorArt.P2(x / scale / SpilledCoffeeErrorTheme.S, (y / scale - 64) / SpilledCoffeeErrorTheme.S)
        }

        fun onCup(p: SpilledCoffeeErrorArt.P2, theta: Double): Boolean {
            val t = theta * Math.PI / 180
            val fx = -cos(SpilledCoffeeErrorTheme.YAW)
            val bx = 503 + 55 * (1 - cos(t)) * fx
            val by = 545 - 55 * sin(t)
            val mx = bx + sin(t) * fx * 215
            val my = by - cos(t) * 215 * cos(SpilledCoffeeErrorTheme.ELEV)
            val vx = mx - bx
            val vy = my - by
            val u = (((p.x - bx) * vx + (p.y - by) * vy) / (vx * vx + vy * vy)).coerceIn(0.0, 1.0)
            return hypot(p.x - (bx + vx * u), p.y - (by + vy * u)) < 99
        }

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val now = clock[0]
            driver.takeOver(now)
            val point = toPin(down.position.x, down.position.y)
            val pose = driver.pose(now)
            val zone = when {
                point.x > 212 && point.x < 446 && point.y > 915 && point.y < 1011 -> {
                    driver.pressing = 1
                    driver.pressAt = now
                    1
                }

                onCup(point, pose.theta) -> {
                    driver.grabCup(now)
                    audio.cancelScript()
                    2
                }

                pose.lid > 0.95 && hypot(point.x - 502, (point.y - 620) * 2.5) < 82 -> 3
                else -> 0
            }

            val startTheta = driver.theta
            var end = down.position
            down.consume()
            try {
                do {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull {
                        it.id == down.id
                    }

                    if (change != null) {
                        end = change.position
                        driver.last = clock[0]
                        if (zone == 2 && change.pressed) {
                            val p = toPin(end.x, end.y)
                            driver.dragTheta = (startTheta - (p.x - point.x) * 0.5).coerceIn(0.0, 95.0)
                        }

                        change.consume()
                    }
                }

                while (event.changes.any {
                    it.id == down.id && it.pressed
                }

                )
            }

            finally {
                val ended = clock[0]
                driver.last = ended
                when (zone) {
                    2 -> driver.dragging = 0
                    1 -> {
                        driver.pressing = 0
                        val p = toPin(end.x, end.y)
                        if (p.x > 202 && p.x < 456 && p.y > 905 && p.y < 1021) {
                            driver.retry(ended)
                            audio.play("tap")
                            audio.buzz(0)
                            if (!reduced) audio.playScript()
                        }
                    }

                    3 -> {
                        driver.hopAt = ended
                        audio.play("tap")
                        audio.buzz(2)
                    }
                }
            }
        }
    }

    ) {
        drawIntoCanvas {
            art.drawScene(it.nativeCanvas, size.width, size.height, frame.value)
        }
    }
}
