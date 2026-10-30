package com.ngocdevv.nativecook.blocks.sheets.goldticketreward

import android.app.Activity
import android.content.ContextWrapper
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.launch
import kotlin.math.*
import com.ngocdevv.nativecook.blocks.sheets.goldticketreward.GoldTicketRewardTheme.A
import com.ngocdevv.nativecook.blocks.sheets.goldticketreward.GoldTicketRewardTheme.B
import com.ngocdevv.nativecook.blocks.sheets.goldticketreward.GoldTicketRewardTheme.Box
import com.ngocdevv.nativecook.blocks.sheets.goldticketreward.GoldTicketRewardMotion.T


@Composable

fun GoldTicketReward(autoPlay: Boolean = false, previewTime: Double? = null) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    val resources = remember(context) {
        GoldTicketRewardArt(context)
    }
    val driver = remember(autoPlay, reduced, previewTime) {
        GoldTicketRewardDriver(previewTime?: if (reduced)GoldTicketRewardMotion.PEAK else 0.0)
    }
    val frame = remember(driver) {
        mutableStateOf(GoldTicketRewardMotion.frameAt(driver.t))
    }
    val tilt = remember(context) {
        GoldTicketRewardTilt(context)
    }
    val live = remember(driver) {
        doubleArrayOf(0.0, 0.0, 0.0)
    }
    val clock = remember(driver) {
        doubleArrayOf(0.0)
    }
    val fade = remember {
        Animatable(1f)
    }
    val scope = rememberCoroutineScope()
    val audio = remember(context, reduced) {
        if (reduced)null else GoldTicketRewardAudio(context)
    }
    val vibrator = remember(context) {
        context.getSystemService(Vibrator::class.java)
    }

    fun buzz(kind: Int = 0) {
        if (Build.VERSION.SDK_INT >= 29) vibrator?.vibrate(VibrationEffect.createPredefined(if (kind == 1)VibrationEffect.EFFECT_HEAVY_CLICK else if (kind == 2)VibrationEffect.EFFECT_TICK else VibrationEffect.EFFECT_CLICK))
        else vibrator?.vibrate(VibrationEffect.createOneShot(if (kind == 1)18 else 12, if (kind == 1)80 else 45))
    }

    fun toggleReduced(toB: Boolean) {
        scope.launch {
            fade.animateTo(if (toB)1f else 0f, tween(220, easing = Easing {
                p -> if (p < 0.5f)2 * p * p else 1 - (-2 * p + 2).pow(2) / 2
            }
            ))
        }
    }
    DisposableEffect(audio) {
        onDispose {
            audio?.close()
        }
    }
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
            if (previous != null)controller.isAppearanceLightStatusBars = previous
        }
    }
    LaunchedEffect(driver, lifecycle) {
        if (reduced || previewTime != null)return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            tilt.start()
            try {
                var previous = 0L
                while (true) withFrameNanos {
                    nanos ->
                    val dt = min(if (previous == 0L)0.016 else (nanos - previous) / 1e9, 0.05)
                    previous = nanos
                    val now = clock[0] + dt
                    clock[0] = now
                    val before = driver.t
                    val t = driver.step(dt, now, autoPlay)
                    tilt.step(dt)
                    val ba = GoldTicketRewardMotion.bAmountAt(t) * 0.6
                    live[0] = driver.tiltX + tilt.x * ba
                    live[1] = driver.tiltY + tilt.y * ba
                    live[2] = driver.ctaP
                    frame.value = GoldTicketRewardMotion.frameAt(t, driver.spinPos, driver.hop(now), driver.twinkle(now))
                    if (driver.mode == 1 && before < T.land && t >= T.land && t < T.hold[0]) {
                        audio?.play("coin")
                        buzz(1)
                    }
                }
            } finally {
                tilt.stop()
            }
        }
    }
    Canvas(Modifier.fillMaxSize().semantics {
        role = Role.Button
        contentDescription = "Thanks for your interest. Book a test drive to win a 1 gram gold coin."
        onClick(label = "Reveals the gold reward, or closes it when it is open.") {
            if (reduced)toggleReduced(fade.value < 0.5f)
            else {
                driver.last = clock[0]
                if (GoldTicketRewardMotion.inRestA(driver.t)) {
                    driver.t = T.commit
                    driver.mode = 1
                    driver.rate = 1.0
                    driver.stop = GoldTicketRewardMotion.PEAK
                } else if (!driver.closeB()) {
                    driver.mode = 1
                    driver.rate = 1.0
                    driver.stop = if (driver.t < GoldTicketRewardMotion.PEAK)GoldTicketRewardMotion.PEAK else GoldTicketRewardMotion.LOOP
                }
            }
            true
        }
    }
        .pointerInput(driver, reduced, previewTime) {
        if (previewTime != null)return@pointerInput

        fun inBox(x: Double,
            y: Double,
            b: Box,
            pad: Double) = x >= b.x - pad && x <= b.x + b.w + pad && y >= b.y - pad && y <= b.y + b.h + pad

        fun near(x: Double, y: Double, cx: Double, cy: Double, r: Double) = (x - cx).pow(2) + (y - cy).pow(2) <= r * r
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val s = size.width / 402.0
            val dy = size.height - 874 * s
            val start = down.position
            val x = start.x / s
            val y = (start.y - dy) / s
            val showingA = if (reduced)fade.value < 0.5f else GoldTicketRewardMotion.inRestA(driver.t)
            val showingB = if (reduced)fade.value >= 0.5f else GoldTicketRewardMotion.inRewardB(driver.t)
            val target = if (showingA) {
                if (inBox(x,
                    y,
                    A.button,
                    6.0))6 else if (inBox(x,
                    y,
                    A.ticket,
                    10.0) || near(x,
                    y,
                    A.coin.x,
                    A.coin.y,
                    A.halo + 6))1 else 0
            } else if (showingB) {
                if (near(x,
                    y,
                    B.close.x,
                    B.close.y,
                    24.0))2 else if (near(x,
                    y,
                    B.coin.x,
                    B.coin.y,
                    B.coin.r + 10))3 else if (inBox(x,
                    y,
                    B.button,
                    6.0))4 else if (inBox(x,
                    y,
                    B.ticket,
                    12.0))5 else 0
            } else 0
            if (!reduced) {
                driver.touched(clock[0])
                if (target == 1 || target == 6)driver.pressA()
                if (target == 4 || target == 6)driver.ctaAim = 1.0
            }
            var scrubbed = 0.0
            var end = start
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, start)
            down.consume()
            try {
                do {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull {
                        it.id == down.id
                    } ?: break
                    end = change.position
                    tracker.addPosition(change.uptimeMillis, end)
                    if (!reduced && change.pressed) {
                        driver.last = clock[0]
                        // RN translations and velocities are raw screen points, not reference-space units.
                        val translationY = (end.y - start.y) / density
                        if (target == 1 && (translationY < -6 || scrubbed > 0)) {
                            val before = scrubbed
                            val p = driver.scrubTo(translationY.toDouble())
                            scrubbed = max(p, 0.0001)
                            if ((before < GoldTicketRewardMotion.COMMIT) != (p < GoldTicketRewardMotion.COMMIT) && before > 0)buzz(2)
                        } else if (target == 5) {
                            driver.aimX = ((end.x / s - (B.ticket.x + B.ticket.w / 2)) / (B.ticket.w / 2)).coerceIn(-1.0, 1.0)
                            driver.aimY = (((end.y - dy) / s - (B.ticket.y + B.ticket.h / 2)) / (B.ticket.h * 1.2)).coerceIn(-1.0, 1.0)
                        }
                    }
                    change.consume()
                } while (change.pressed && event.changes.any { it.pressed })
            } finally {
                val ex = end.x / s
                val ey = (end.y - dy) / s
                val tap = hypot(ex - x, ey - y) < 12
                val velocity = tracker.calculateVelocity()
                driver.ctaAim = 0.0
                if (reduced) {
                    if (tap && (target == 1 || target == 6))toggleReduced(true) else if (tap && target == 2)toggleReduced(false)
                } else {
                    if (target == 1 && scrubbed > 0) {
                        if (driver.scrubEnd(((end.y - start.y) / density).toDouble(),
                            (velocity.y / density).toDouble()))audio?.play("reveal")
                    } else if (target == 1 || target == 6) {
                        val onTarget = tap && (inBox(ex,
                            ey,
                            A.ticket,
                            16.0) || near(ex,
                            ey,
                            A.coin.x,
                            A.coin.y,
                            A.halo + 10) || inBox(ex,
                            ey,
                            A.button,
                            10.0))
                        driver.releaseA(onTarget)
                        if (onTarget)audio?.play("reveal")
                    } else if (target == 2 && tap) {
                        if (driver.closeB())buzz()
                    } else if (target == 3) {
                        driver.flickCoin(if (tap)0.0 else (velocity.x / density).toDouble(), clock[0])
                        audio?.play("coin")
                        buzz()
                    } else if (target == 4 && tap) {
                        driver.ctaAt = clock[0]
                        audio?.play("sparkle")
                        buzz()
                    }
                    driver.aimX = 0.0
                    driver.aimY = 0.0
                    driver.last = clock[0]
                }
            }
        }
    }
    ) {
        drawIntoCanvas {
            val canvas = it.nativeCanvas
            if (reduced) {
                val k = fade.value
                if (k < 0.999f)resources.drawScene(canvas, size.width, size.height, GoldTicketRewardMotion.frameAt(0.0))
                if (k > 0.001f) {
                    canvas.saveLayerAlpha(null, (k * 255).roundToInt())
                    resources.drawScene(canvas, size.width, size.height, GoldTicketRewardMotion.frameAt(GoldTicketRewardMotion.PEAK))
                    canvas.restore()
                }
            } else resources.drawScene(canvas, size.width, size.height, frame.value, live[0], live[1], live[2])
        }
    }
}
