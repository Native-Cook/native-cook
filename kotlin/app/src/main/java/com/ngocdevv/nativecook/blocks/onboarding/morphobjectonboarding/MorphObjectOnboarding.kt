package com.ngocdevv.nativecook.blocks.onboarding.morphobjectonboarding

import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.provider.Settings
import android.view.HapticFeedbackConstants
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
import com.ngocdevv.nativecook.blocks.onboarding.morphobjectonboarding.MorphObjectOnboardingMotion.Live
import com.ngocdevv.nativecook.blocks.onboarding.morphobjectonboarding.MorphObjectOnboardingMotion.arrivalAt
import com.ngocdevv.nativecook.blocks.onboarding.morphobjectonboarding.MorphObjectOnboardingMotion.band
import com.ngocdevv.nativecook.blocks.onboarding.morphobjectonboarding.MorphObjectOnboardingMotion.pageRound
import com.ngocdevv.nativecook.blocks.onboarding.morphobjectonboarding.MorphObjectOnboardingTheme as U

@Composable
fun MorphObjectOnboarding(autoPlay: Boolean = false, previewTime: Float? = null) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    val debug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    val pin = if (debug) previewTime?.toDouble() else null
    val art = remember(context) {
        MorphObjectOnboardingArt(context)
    }

    val audio = remember(context, reduced) {
        if (reduced) null else MorphObjectOnboardingAudio(context)
    }

    DisposableEffect(audio) {
        onDispose {
            audio?.close()
        }
    }

    DisposableEffect(view) {
        val activity = generateSequence(view.context) {
            (it as? ContextWrapper)?.baseContext
        }.filterIsInstance<Activity>().firstOrNull()
        val controller = activity?.let {
            WindowCompat.getInsetsController(it.window, view)
        }

        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose {
            if (previous != null) controller.isAppearanceLightStatusBars = previous
        }
    }

    val driver = remember(autoPlay, reduced, pin) {
        MorphObjectOnboardingDriver(pin ?: 0.0)
    }

    val clock = remember(driver) {
        doubleArrayOf(0.0)
    }

    val live = remember(driver) {
        mutableStateOf(
            Live(
                amb = pin ?: 0.0,
                p = driver.p,
                arrive = pin?.let { arrivalAt(it) } ?: -1.0,
            ),
        )
    }

    val fadePage = remember(reduced) {
        Animatable(0f)
    }

    val scope = rememberCoroutineScope()
    fun reducedGo(page: Double) {
        scope.launch {
            // RN explicitly ignores the system duration scale for this gentle 200 ms cross-fade.
            withContextDurationScaleOne {
                val easing = Easing { p ->
                    if (p < 0.5f) 2 * p * p else 1 - (-2 * p + 2).pow(2) / 2
                }
                fadePage.animateTo(
                    page.coerceIn(0.0, 2.0).toFloat(),
                    tween(200, easing = easing),
                )
            }
        }
    }

    fun buzz(selection: Boolean = false) {
        view.performHapticFeedback(if (selection) HapticFeedbackConstants.CLOCK_TICK else HapticFeedbackConstants.CONTEXT_CLICK)
    }

    LaunchedEffect(driver, lifecycle) {
        if (reduced || pin != null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (true) withFrameNanos { nanos ->
                val dt = min(if (previous == 0L) 0.016 else (nanos - previous) / 1e9, 0.05)
                previous = nanos
                clock[0] += dt
                val now = clock[0]
                val before = driver.p
                val arrived = driver.step(dt, now, autoPlay)
                if (arrived >= 0) {
                    driver.arriveAt = now
                    audio?.play("pop")
                    buzz(true)
                }

                if (driver.mode == 2 && driver.aim < 0 && floor(before + 0.5) != floor(driver.p + 0.5)) buzz(true)
                val loopArrival = if (driver.mode == 0) arrivalAt(driver.t) else -1.0
                val arrival = if (driver.arriveAt >= 0 && now - driver.arriveAt < 1) now - driver.arriveAt else loopArrival
                val hop = if (driver.hopAt >= 0 && now - driver.hopAt < 1.2) now - driver.hopAt else -1.0
                live.value = Live(driver.amb, driver.p, arrival, hop, driver.press)
            }
        }
    }

    Canvas(Modifier.fillMaxSize().semantics {
        role = Role.Button
        contentDescription = "Onboarding, three pages: We really value your time. You will learn online. You will learn correctly."
        onClick(label = "Goes to the next page") {
            if (reduced) {
                val cur = pageRound(fadePage.value.toDouble())
                reducedGo(if (cur + 1 > 2) 0.0 else cur + 1)
            } else {
                val cur = pageRound(driver.p)
                driver.goTo(if (cur >= 2) 0.0 else cur + 1, clock[0])
            }

            true
        }
    }.pointerInput(driver, reduced, pin) {
        if (pin != null) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val s = size.width / U.REF_W
            val dy = size.height - U.REF_H * s
            val startX = down.position.x / s
            val startY = (down.position.y - dy) / s
            fun inButton(x: Double, y: Double, pad: Double): Boolean {
                return x >= U.buttonX - pad && x <= U.buttonX + U.buttonW + pad &&
                    y >= U.buttonY - pad && y <= U.buttonY + U.buttonH + pad
            }
            val target = when {
                inButton(startX, startY, 6.0) -> 2
                abs(startX - U.skipX + 16) < 34 && abs(startY - U.TOP_Y) < 24 -> 3
                startX > U.OBJECT_X + 40 && startX < U.OBJECT_X + U.OBJECT_SIZE - 40 &&
                    startY > U.OBJECT_Y + 30 && startY < U.OBJECT_Y + U.OBJECT_SIZE - 30 -> 4
                else -> 1
            }

            if (!reduced) {
                if (target == 2) {
                    driver.pressAim = 1.0
                    driver.last = clock[0]
                } else if (target == 1 || target == 4) driver.grab(clock[0])
            }

            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var end = down.position
            var moved = false
            down.consume()
            try {
                do {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    end = change.position
                    tracker.addPosition(change.uptimeMillis, end)
                    val translationX = end.x - down.position.x
                    if (!reduced && (target == 1 || target == 4)) {
                        driver.last = clock[0]
                        if (abs(translationX) > 8 * density) moved = true
                        driver.p = band(driver.dragFrom - translationX / s / (U.REF_W * 0.85))
                        driver.v = 0.0
                    }

                    change.consume()
                } while (change.pressed)
            } finally {
                val x = end.x / s
                val y = (end.y - dy) / s
                val tap = hypot(x - startX, y - startY) < 10 && !moved
                val now = clock[0]
                driver.pressAim = 0.0
                driver.last = now
                if (reduced) {
                    val cur = pageRound(fadePage.value.toDouble())
                    if (tap && target == 2) reducedGo(if (cur + 1 > 2) 0.0 else cur + 1)
                    else if (tap && target == 3) reducedGo(2.0)
                    else if (!tap && (target == 1 || target == 4)) reducedGo(cur + if (end.x - down.position.x < 0) 1 else -1)
                } else if (target == 2 && tap && inButton(x, y, 12.0)) {
                    val cur = pageRound(if (driver.mode == 0) driver.p else if (driver.aim >= 0) driver.aim else driver.p)
                    driver.goTo(if (cur >= 2) 0.0 else cur + 1, now)
                    audio?.play("morph")
                    buzz()
                } else if (target == 3 && tap) {
                    driver.goTo(2.0, now, true)
                    audio?.play("morph")
                    buzz()
                } else if ((target == 1 || target == 4) && !tap) {
                    val velocity = -tracker.calculateVelocity().x / s / (U.REF_W * 0.85)
                    val from = pageRound(driver.dragFrom)
                    driver.release(velocity)
                    if (driver.aim != from) audio?.play("morph")
                } else if (target == 4 && tap) {
                    driver.hopAt = now
                    driver.aim = pageRound(driver.p)
                    buzz()
                } else if (target == 1) driver.aim = pageRound(driver.p)
            }
        }
    }) {
        drawIntoCanvas { canvas ->
            val c = canvas.nativeCanvas
            if (pin != null) {
                art.drawScene(c, size.width, size.height, live.value)
            } else if (reduced) {
                val p = fadePage.value.toDouble()
                val lo = floor(p)
                val k = p - lo
                art.drawScene(c, size.width, size.height, Live(p = lo))
                if (k > 0.001) {
                    val count = c.saveLayerAlpha(0f, 0f, size.width, size.height, (k * 255).roundToInt())
                    art.drawScene(c, size.width, size.height, Live(p = min(2.0, lo + 1)))
                    c.restoreToCount(count)
                }
            } else art.drawScene(c, size.width, size.height, live.value)
        }
    }
}
