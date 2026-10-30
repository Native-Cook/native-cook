package com.ngocdevv.nativecook.blocks.onboarding.careerwebonboarding

import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ngocdevv.nativecook.shared.BlockSystemBars
import kotlin.math.hypot
import kotlin.math.min
import com.ngocdevv.nativecook.blocks.onboarding.careerwebonboarding.CareerWebOnboardingMotion.LOOP
import com.ngocdevv.nativecook.blocks.onboarding.careerwebonboarding.CareerWebOnboardingMotion.N
import com.ngocdevv.nativecook.blocks.onboarding.careerwebonboarding.CareerWebOnboardingMotion.frameAt
import com.ngocdevv.nativecook.blocks.onboarding.careerwebonboarding.CareerWebOnboardingMotion.linkOn
import com.ngocdevv.nativecook.blocks.onboarding.careerwebonboarding.CareerWebOnboardingMotion.links
import com.ngocdevv.nativecook.blocks.onboarding.careerwebonboarding.CareerWebOnboardingMotion.makeDriver
import com.ngocdevv.nativecook.blocks.onboarding.careerwebonboarding.CareerWebOnboardingMotion.nodeHome
import com.ngocdevv.nativecook.blocks.onboarding.careerwebonboarding.CareerWebOnboardingMotion.pluck
import com.ngocdevv.nativecook.blocks.onboarding.careerwebonboarding.CareerWebOnboardingMotion.stepDriver
import com.ngocdevv.nativecook.blocks.onboarding.careerwebonboarding.CareerWebOnboardingMotion.touched
import com.ngocdevv.nativecook.blocks.onboarding.careerwebonboarding.CareerWebOnboardingMotion.vibration
import com.ngocdevv.nativecook.blocks.onboarding.careerwebonboarding.CareerWebOnboardingTheme.REF_H
import com.ngocdevv.nativecook.blocks.onboarding.careerwebonboarding.CareerWebOnboardingTheme.REF_W
import com.ngocdevv.nativecook.blocks.onboarding.careerwebonboarding.CareerWebOnboardingTheme.bars
import com.ngocdevv.nativecook.blocks.onboarding.careerwebonboarding.CareerWebOnboardingTheme.button
import com.ngocdevv.nativecook.blocks.onboarding.careerwebonboarding.CareerWebOnboardingTheme.fits
import com.ngocdevv.nativecook.blocks.onboarding.careerwebonboarding.CareerWebOnboardingTheme.nodes

/** Jetpack Compose port of the Career Web Onboarding reference. */
@Composable
fun CareerWebOnboarding(autoPlay: Boolean = false, previewTime: Float? = null) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = context.resources.displayMetrics.density
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val pin = previewTime?.toDouble()?.let { ((it % LOOP) + LOOP) % LOOP }
    val art = remember(context) { CareerWebOnboardingArt(context) }
    val audio = remember(context) { CareerWebOnboardingAudio(context) }
    DisposableEffect(audio) {
        onDispose { audio.close() }
    }
    BlockSystemBars(darkIcons = false)

    val driver = remember(autoPlay, reduced, pin) { makeDriver(pin ?: 0.0) }
    val clock = remember(driver) { doubleArrayOf(pin ?: 0.0) }
    val initialFrame = frameAt(if (reduced) 0.0 else pin ?: 0.0)
    val frameState = remember(driver, reduced, pin) { mutableStateOf(initialFrame) }
    val liveState = remember(driver, reduced, pin) {
        mutableStateOf(
            CareerWebOnboardingLive(
                px = nodes.map { it.x }.toDoubleArray(),
                py = nodes.map { it.y }.toDoubleArray(),
                grow = initialFrame.grow.copyOf(),
                vib = DoubleArray(links.size),
                hold = -1,
                press = 0.0,
                shoot = -1.0,
                pick = DoubleArray(3) { -1.0 },
            ),
        )
    }
    val homeLengths = remember {
        DoubleArray(links.size) { link ->
            val a = links[link][0]
            val b = links[link][1]
            hypot(nodes[b].x - nodes[a].x, nodes[b].y - nodes[a].y)
        }
    }

    LaunchedEffect(driver, lifecycle, reduced, pin, autoPlay) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (true) {
                withFrameNanos { nanos ->
                    val dt = if (previous == 0L) 0.016 else min((nanos - previous) / 1e9, 0.05)
                    previous = nanos
                    val now = clock[0] + dt
                    clock[0] = now
                    if (pin != null) {
                        driver.mode = 0
                        driver.t = pin
                        driver.amb = pin
                    }
                    val loopFrame = frameAt(driver.t)
                    val simulationDt = if (reduced || pin != null) 0.0 else dt
                    val out = stepDriver(driver, simulationDt, now, autoPlay && !reduced && pin == null, loopFrame.grow)
                    val px = DoubleArray(N)
                    val py = DoubleArray(N)
                    for (index in 0 until N) {
                        val home = nodeHome(index, out.t, driver.amb)
                        px[index] = home.x + driver.ox[index]
                        py[index] = home.y + driver.oy[index]
                    }
                    val vib = DoubleArray(links.size) { link ->
                        val loopRing = if (driver.mode == 0) loopFrame.tugVib * (if (link >= bars.size) 7.0 else 3.0) * linkOn(link, out.grow) else 0.0
                        vibration(driver, link, now) + loopRing
                    }
                    val pick = DoubleArray(3) { person ->
                        val tau = now - driver.pickAt[person]
                        if (driver.pickAt[person] >= 0.0 && tau < 0.7) tau / 0.7
                        else if (person == 1 && driver.mode == 0) loopFrame.pick
                        else -1.0
                    }
                    val shootTime = if (driver.shootAt >= 0.0) (now - driver.shootAt) / 0.5 else -1.0
                    frameState.value = frameAt(if (reduced) 0.0 else out.t)
                    liveState.value = CareerWebOnboardingLive(
                        px = px,
                        py = py,
                        grow = out.grow,
                        vib = vib,
                        hold = driver.hold,
                        press = driver.press,
                        shoot = if (shootTime >= 0.0 && shootTime < 1.0) shootTime else -1.0,
                        pick = pick,
                    )
                }
            }
        }
    }

    fun buzz(kind: Int) {
        when (kind) {
            2 -> view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            1 -> view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            else -> view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
    }

    fun accessibilityToggle() {
        touched(driver, clock[0])
        val on = (if (driver.grow[0] >= 0.0) driver.grow[0] else 0.0) > 0.5
        for (person in fits.indices) {
            if (driver.grow[person] < 0.0) driver.grow[person] = if (on) 1.0 else 0.0
            driver.growAim[person] = if (on) 0.0 else 1.0
        }
    }

    Canvas(
        Modifier
            .fillMaxSize()
            .semantics {
                role = Role.Button
                contentDescription = "Build your future, build your dream. Three people linked to the roles that fit them: UX/UI designer, product designer, software engineer, Flutter developer. Get started."
                onClick(label = "Show or hide the role links") {
                    accessibilityToggle()
                    true
                }
            }
            .pointerInput(driver, reduced, pin) {
                if (pin != null) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val scale = size.width.toDouble() / REF_W
                    val dy = size.height.toDouble() - REF_H * scale
                    fun refPosition(position: Offset): Offset = Offset(
                        (position.x / scale).toFloat(),
                        ((position.y - dy) / scale).toFloat(),
                    )
                    val start = down.position
                    var moved = false
                    var target = 0
                    var hitLink = -1
                    val startRef = refPosition(start)
                    val currentLive = liveState.value
                    if (startRef.x > button.x - 6.0 && startRef.x < button.x + button.w + 6.0 && startRef.y > button.y - 6.0 && startRef.y < button.y + button.h + 6.0) {
                        target = 3
                    } else {
                        var best = -1
                        var bestDistance = Double.MAX_VALUE
                        for (index in 0 until N) {
                            val distance = hypot(startRef.x - currentLive.px[index], startRef.y - currentLive.py[index])
                            if (distance < nodes[index].r + 6.0 && distance < bestDistance) {
                                best = index
                                bestDistance = distance
                            }
                        }
                        if (best >= 0) {
                            target = 1
                            if (!reduced) {
                                touched(driver, clock[0])
                                driver.hold = best
                                driver.fx = startRef.x.toDouble()
                                driver.fy = startRef.y.toDouble()
                                driver.gdx = startRef.x - currentLive.px[best]
                                driver.gdy = startRef.y - currentLive.py[best]
                            }
                        } else {
                            for (link in links.indices) {
                                if (linkOn(link, currentLive.grow) < 0.9) continue
                                val a = links[link][0]
                                val b = links[link][1]
                                if (segmentDistance(startRef.x.toDouble(), startRef.y.toDouble(), currentLive.px[a], currentLive.py[a], currentLive.px[b], currentLive.py[b]) < 16.0) {
                                    target = 2
                                    hitLink = link
                                    break
                                }
                            }
                        }
                    }
                    if (!reduced) {
                        if (target == 3) {
                            touched(driver, clock[0])
                            driver.pressAim = 1.0
                        }
                        if (target == 2) touched(driver, clock[0])
                    }
                    val tracker = VelocityTracker().also {
                        it.resetTracking()
                        it.addPointerInputChange(down)
                    }
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        tracker.addPointerInputChange(change)
                        if (!change.pressed) break
                        if (!reduced) {
                            driver.last = clock[0]
                            val translation = change.position - start
                            if (hypot(translation.x.toDouble(), translation.y.toDouble()) / density > 8.0) moved = true
                            if (target == 1) {
                                val ref = refPosition(change.position)
                                driver.fx = ref.x.toDouble()
                                driver.fy = ref.y.toDouble()
                            }
                        }
                        event.changes.forEach { it.consume() }
                    }

                    val now = clock[0]
                    val held = driver.hold
                    driver.pressAim = 0.0
                    driver.last = now
                    driver.hold = -1
                    val tap = !moved
                    if (target == 1 && held >= 0) {
                        if (tap && nodes[held].kind == CareerWebOnboardingTheme.NodeKind.PERSON) {
                            val person = held
                            val on = (if (driver.grow[person] >= 0.0) driver.grow[person] else 0.0) > 0.5
                            if (reduced) {
                                driver.grow[person] = if (on) 0.0 else 1.0
                                driver.growAim[person] = driver.grow[person]
                            } else {
                                if (driver.grow[person] < 0.0) driver.grow[person] = if (on) 1.0 else 0.0
                                driver.growAim[person] = if (on) 0.0 else 1.0
                                driver.pickAt[person] = now
                            }
                            audio.play(if (on) "pluck" else "attach")
                            buzz(2)
                        } else if (!tap && !reduced) {
                            val velocity = tracker.calculateVelocity()
                            driver.vx[held] = velocity.x / scale
                            driver.vy[held] = velocity.y / scale
                            val speed = min(1.0, hypot(driver.vx[held], driver.vy[held]) / 1400.0)
                            for (link in links.indices) {
                                if (links[link][0] == held || links[link][1] == held) pluck(driver, link, 5.0 + 9.0 * speed, now)
                            }
                            if (speed > 0.25) {
                                audio.play("pluck")
                                buzz(0)
                            }
                        }
                    } else if (target == 2 && tap && hitLink >= 0 && !reduced) {
                        pluck(driver, hitLink, 11.0, now)
                        audio.play("pluck")
                        buzz(0)
                    } else if (target == 3 && tap) {
                        driver.shootAt = now
                        buzz(1)
                    }
                }
            },
    ) {
        val frame = frameState.value
        val live = liveState.value
        drawIntoCanvas { canvas -> art.drawScene(canvas.nativeCanvas, size.width, size.height, frame, live, homeLengths) }
    }
}

private fun segmentDistance(x: Double, y: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
    val dx = bx - ax
    val dy = by - ay
    val lengthSquared = dx * dx + dy * dy
    val u = (((x - ax) * dx + (y - ay) * dy) / if (lengthSquared == 0.0) 1.0 else lengthSquared).coerceIn(0.0, 1.0)
    return hypot(x - (ax + dx * u), y - (ay + dy * u))
}
