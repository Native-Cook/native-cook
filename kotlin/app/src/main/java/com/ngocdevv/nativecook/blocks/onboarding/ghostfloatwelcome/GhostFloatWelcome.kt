package com.ngocdevv.nativecook.blocks.onboarding.ghostfloatwelcome

import android.app.Activity
import android.content.ContextWrapper
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import com.ngocdevv.nativecook.blocks.onboarding.ghostfloatwelcome.GhostFloatWelcomeMotion.frameAt
import com.ngocdevv.nativecook.blocks.onboarding.ghostfloatwelcome.GhostFloatWelcomeMotion.liveHome
import com.ngocdevv.nativecook.blocks.onboarding.ghostfloatwelcome.GhostFloatWelcomeTheme.UI


/** The analytic loop is paused in place by touch; ambient drift and spring offsets keep moving. */
@Composable

fun GhostFloatWelcome(autoPlay: Boolean = false, previewTime: Double? = null) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    val art = remember(context) { GhostFloatWelcomeArt(context) }
    val driver = remember(autoPlay, reduced, previewTime) { GhostFloatWelcomeDriver(previewTime ?: 0.0) }
    val clock = remember(driver) { doubleArrayOf(0.0) }
    val redraw = remember(driver) { mutableIntStateOf(0) }
    val gyro = remember(context) { GhostFloatWelcomeGyro(context) }
    val feedback = remember(context, view) { GhostFloatWelcomeFeedback(context, view) }
    val fade = remember(driver) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    DisposableEffect(view, feedback) {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().firstOrNull()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose {
            if (previous != null) controller.isAppearanceLightStatusBars = previous
            feedback.release()
        }
    }

    fun toggleReduced() {
        val aim = if (fade.value > 0.5f) 0f else 1f
        scope.launch(object : MotionDurationScale { override val scaleFactor = 1f }) {
            fade.animateTo(aim, tween(200, easing = Easing { p ->
                if (p < 0.5f) 2 * p * p else 1 - (-2 * p + 2) * (-2 * p + 2) / 2
            }))
        }
    }
    LaunchedEffect(driver, lifecycle, reduced, previewTime) {
        if (reduced || previewTime != null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            gyro.start()
            try {
                var previous = 0L
                while (true) {
                    withFrameNanos { nanos ->
                        val dt = min(if (previous == 0L) 0.016 else (nanos - previous) / 1e9, 0.05)
                        previous = nanos
                        clock[0] += dt
                        driver.step(dt, clock[0], autoPlay)
                        gyro.step(dt)
                        redraw.intValue++
                    }
                }
            } finally { gyro.stop() }
        }
    }
    Canvas(Modifier.fillMaxSize()
        .semantics {
            role = Role.Checkbox
            contentDescription = "Welcome to Wisp. To get started, create a new wallet or import an existing one. I agree to the Terms of Service."
            onClick(label = "Ticks or unticks the agreement") {
                if (reduced) toggleReduced() else {
                    driver.touched(clock[0])
                    driver.toggleAgree()
                }
                true
            }
        }
        .pointerInput(driver, reduced, previewTime) {
            if (previewTime != null) return@pointerInput
            awaitEachGesture {
                val first = awaitFirstDown(requireUnconsumed = false)
                val scale = size.width / 402.0
                val dy = (size.height - 874 * scale) / 2
                val startX = first.position.x / scale
                val startY = (first.position.y - dy) / scale
                var endX = startX
                var endY = startY
                var moved = false
                var swished = false
                var target = 0
                val tracker = VelocityTracker()
                tracker.addPosition(first.uptimeMillis, first.position)
                if (abs(startY - UI.checkY) < 24 && startX > UI.checkX - 26 && startX < UI.checkX + 250) target = 3
                else if (inButton(startX, startY, 6.0)) target = 4
                else if (startY < GhostFloatWelcomeTheme.fieldBottom) {
                    target = 1
                    var best = -1
                    var bestDistance = 1e9
                    for (j in 0 until 7) {
                        val home = liveHome(j, driver.t, driver.amb)
                        val distance = hypot(startX - home.x - driver.ox[j], startY - home.y - driver.oy[j])
                        if (distance < home.r + 8 && distance < bestDistance) {
                            best = j
                            bestDistance = distance
                        }
                    }
                    if (best >= 0) {
                        target = 2
                        val home = liveHome(best, driver.t, driver.amb)
                        driver.grab = best
                        driver.gdx = startX - home.x - driver.ox[best]
                        driver.gdy = startY - home.y - driver.oy[best]
                    }
                }
                if (!reduced) {
                    driver.touched(clock[0])
                    driver.down = if (target == 1 || target == 2) 1 else 0
                    driver.fx = startX
                    driver.fy = startY
                    driver.fvx = 0.0
                    driver.fvy = 0.0
                    if (target == 4) driver.btnAim = 1.0
                }
                first.consume()
                try {
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == first.id } ?: break
                        endX = change.position.x / scale
                        endY = (change.position.y - dy) / scale
                        tracker.addPosition(change.uptimeMillis, change.position)
                        if (!reduced && change.pressed) {
                            val velocity = tracker.calculateVelocity()
                            driver.last = clock[0]
                            driver.fx = endX
                            driver.fy = endY
                            driver.fvx = velocity.x / scale
                            driver.fvy = velocity.y / scale
                            if (hypot(endX - startX, endY - startY) > 10) moved = true
                            if (target == 1 && !swished && hypot(driver.fvx, driver.fvy) > 700) {
                                swished = true
                                feedback.cue("swish")
                            }
                        }
                        change.consume()
                    } while (event.changes.any { it.id == first.id && it.pressed })
                } finally {
                    val now = clock[0]
                    driver.down = 0
                    driver.btnAim = 0.0
                    driver.last = now
                    if (reduced) {
                        if (!moved && target == 3) toggleReduced()
                        driver.grab = -1
                    } else if (target == 2) {
                        val body = driver.grab
                        driver.grab = -1
                        if (!moved && body == 0) {
                            driver.boo(now)
                            feedback.cue("boo")
                            feedback.buzz(1)
                        } else if (body >= 0) {
                            val velocity = tracker.calculateVelocity()
                            driver.vx[body] = velocity.x / scale
                            driver.vy[body] = velocity.y / scale
                            if (moved) feedback.buzz(0)
                        }
                    } else if (target == 3 && !moved) {
                        driver.toggleAgree()
                        feedback.cue("tick")
                        feedback.buzz(2)
                    } else if (target == 4 && !moved && inButton(endX, endY, 12.0)) {
                        val agreed = (if (driver.agree >= 0) driver.agree else frameAt(driver.t).tick) > 0.5
                        if (agreed) {
                            driver.rollAt = now
                            feedback.buzz(1)
                        } else {
                            driver.noAt = now
                            feedback.buzz(3)
                        }
                    }
                }
            }
        }
    ) {
        // Read the invalidation state here only: the screen does not recompose each frame.
        redraw.intValue
        drawIntoCanvas {
            if (reduced) {
                val still = GhostFloatWelcomeDriver()
                still.agree = fade.value.toDouble()
                art.drawScene(it.nativeCanvas, size.width, size.height, frameAt(0.0), still, GhostFloatWelcomeDriver.Extra(), 0.0, 0.0)
            } else {
                art.drawScene(it.nativeCanvas, size.width, size.height, frameAt(driver.t), driver, driver.extra(clock[0]), gyro.x, gyro.y)
            }
        }
    }
}

private fun inButton(x: Double, y: Double, pad: Double): Boolean {
    return x >= UI.buttonX - pad && x <= UI.buttonX + UI.buttonW + pad &&
        y >= UI.buttonY - pad && y <= UI.buttonY + UI.buttonH + pad
}
