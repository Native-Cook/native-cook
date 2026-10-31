package com.ngocdevv.nativecook.blocks.cards.tapridepass

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.*
import kotlinx.coroutines.delay

/** Gallery entry that runs the complete 9 s card-read and ticket-print loop. */
@Composable
fun TapRidePassDemo() {
    TapRidePass(autoPlay = true)
}

/**
 * Full-screen host: lifecycle timing, accessibility and touch hit testing around the dp Canvas.
 * Touch interrupts autoplay; the driver resumes after inactivity. Reduced motion retains the
 * readable state.
 */
@Composable
fun TapRidePass(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val top = WindowInsets.statusBars.getTop(density) / density.density.toDouble()
    val bottom = WindowInsets.navigationBars.getBottom(density) / density.density.toDouble()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduceMotion =
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    val art = remember(context) { TapRidePassArt(context) }
    val driver = remember { TapRidePassDriver() }
    val audio =
        remember(context, reduceMotion) { if (reduceMotion) null else TapRidePassAudio(context) }
    val haptic = LocalHapticFeedback.current
    var geometry by remember { mutableStateOf<TapRidePassGeometry?>(null) }
    var frameTick by remember { mutableIntStateOf(0) }
    var ticketOpen by remember { mutableStateOf(false) }
    SideEffect {
        driver.reduceMotion = reduceMotion
        driver.cue = { if (!reduceMotion) audio?.play(it) }
        driver.buzz = { kind ->
            haptic.performHapticFeedback(
                when (kind) {
                    0 -> HapticFeedbackType.TextHandleMove
                    1 -> HapticFeedbackType.LongPress
                    else -> HapticFeedbackType.Confirm
                }
            )
        }
    }
    DisposableEffect(audio) { onDispose { audio?.close() } }
    LaunchedEffect(driver, lifecycle, autoPlay, reduceMotion, geometry) {
        val layout = geometry ?: return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previousFrameNanos = 0L
            while (true) {
                if (reduceMotion && driver.mode == 0 && !driver.held) {
                    delay(50)
                    previousFrameNanos = 0L
                    continue
                }
                withFrameNanos { frameNanos ->
                    val deltaTime =
                        min(
                            if (previousFrameNanos == 0L) 0.016
                            else (frameNanos - previousFrameNanos) / 1e9,
                            0.05,
                        )
                    previousFrameNanos = frameNanos
                    driver.configure(layout)
                    driver.step(deltaTime, layout, autoPlay)
                    val open = driver.ticketProgress > 0.9
                    if (open != ticketOpen) ticketOpen = open
                    frameTick++
                }
            }
        }
    }
    Canvas(
        Modifier.fillMaxSize()
            .onSizeChanged {
                val layout =
                    TapRidePassGeometry(
                        it.width / density.density.toDouble(),
                        it.height / density.density.toDouble(),
                        top,
                        bottom,
                    )
                driver.configure(layout)
                geometry = layout
            }
            .semantics {
                role = Role.Button
                contentDescription =
                    if (ticketOpen)
                        "Single ride ticket, Harbor Gate to Old Mill, fare $2.75, balance " +
                            "${art.money(driver.balanceAfterFare)}. Gate 3 open."
                    else "Riverline commuter card, balance ${art.money(driver.balance)}"
                onClick(
                    label =
                        if (ticketOpen) "Done" else "Touches the card to the reader to pay the fare"
                ) {
                    if (ticketOpen) driver.close() else driver.assist()
                    true
                }
            }
            .pointerInput(driver, density, geometry) {
                val layout = geometry ?: return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = down.position
                    val x = start.x / density.density
                    val y = start.y / density.density
                    val dockProgress =
                        TapRidePassMotion.smooth(
                            0.0,
                            1.0,
                            1 -
                                min(
                                    1.0,
                                    hypot(
                                        driver.cardX - layout.readerX,
                                        driver.cardY - layout.readerY,
                                    ) / layout.homeDistance,
                                ),
                        )
                    val cardScale =
                        (1 + (layout.dockScale - 1) * dockProgress) * (1 + 0.035 * driver.lift)
                    val cardCenterY =
                        driver.cardY +
                            TapRidePassMotion.smooth(0.45, 0.95, driver.ticketProgress) *
                                layout.cardHeight *
                                0.78
                    val hitCard =
                        driver.ticketProgress <= 0.9 &&
                            abs(x - driver.cardX) < layout.cardWidth * cardScale / 2 &&
                            abs(y - cardCenterY) <
                                layout.cardHeight *
                                    cardScale *
                                    cos(acos(105.0 / 166) * dockProgress) / 2
                    val hitTicket =
                        driver.ticketProgress > 0.9 &&
                            ((abs(x - layout.width / 2) < layout.ticketWidth / 2 &&
                                abs(y - layout.ticketY) < layout.ticketHeight / 2) ||
                                (abs(x - layout.width / 2) < 70 &&
                                    y > layout.ticketY + 237 &&
                                    y < layout.ticketY + 285))
                    if (hitCard) driver.begin()
                    var end = start
                    if (hitCard || hitTicket) down.consume()
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        end = change.position
                        if (hitCard && change.pressed)
                            driver.drag(
                                ((end.x - start.x) / density.density).toDouble(),
                                ((end.y - start.y) / density.density).toDouble(),
                            )
                        if (hitCard || hitTicket) change.consume()
                    } while (change.pressed)
                    if (hitCard) driver.end()
                    if (hitTicket && (end - start).getDistance() / density.density < 12)
                        driver.close()
                }
            }
    ) {
        frameTick
        val layout = geometry ?: return@Canvas
        drawIntoCanvas {
            val canvas = it.nativeCanvas
            canvas.save()
            canvas.scale(density.density, density.density)
            art.draw(canvas, layout, driver)
            canvas.restore()
        }
    }
}
