package com.ngocdevv.nativecook.blocks.cards.pineappleliftproduct

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.hypot

/**
 * The product page of an invented body oil (Isla Bloom, Golden Pineapple) on a sunny yellow
 * backdrop. At rest a whole pineapple stands closed; its crowned top half lifts away on a spring,
 * the amber bottle rises out of the fruit and hovers between the halves while a ring of juice drops
 * bursts off the cut rim and the hibiscus flowers drift; then everything settles back.
 *
 * Loop (LOOP = 7 s): rest → press and lift (1.0–2.4 s) → the bottle rises (1.3 s) in a burst of
 * drops (1.45 s) → hovers → sinks back (4.6–5.5 s) → the top closes and lands (5.75 s) → rest. Drag
 * up to lift it yourself, release to let it spring shut; tap for a hop and a splash. Reduced
 * motion: the open product shot, still.
 */
@Composable
fun PineappleLiftProduct(autoPlay: Boolean = false) {
    val m = PineappleLiftProductMotion
    val art = PineappleLiftProductArt
    val context = LocalContext.current
    val view = LocalView.current
    val owner = LocalLifecycleOwner.current
    val reduced = rememberLiveReducedMotion()
    var active by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ -> active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    BlockSystemBars(darkIcons = true)

    val res = remember { art.load(context) }
    val clock = remember { mutableDoubleStateOf(0.0) }
    val touch = remember { PineappleLiftProductTouch() }
    val awake = remember { mutableStateOf(false) }
    val running = !reduced && active && (autoPlay || awake.value)
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var startNanos = -1L
        val base = clock.doubleValue
        while (true) {
            withFrameNanos { now ->
                if (startNanos < 0) startNanos = now
                val c = base + (now - startNanos) / 1e9
                clock.doubleValue = c
                if (touch.takeHaptic(c)) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
            if (!autoPlay && !touch.touching && clock.doubleValue > touch.restsAfter + 0.1) {
                awake.value = false
                break
            }
        }
    }

    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(art.argb(art.SKY_MID)))) {
        val k = maxWidth.value / 393f
        val height = (maxHeight.value / k).toDouble()
        val top = (WindowInsets.safeDrawing.getTop(density) / density.density / k).toDouble()
        val bottom = (WindowInsets.safeDrawing.getBottom(density) / density.density / k).toDouble()
        val layout = remember(height, top, bottom) { PineappleLiftProductArt.Layout(height, top, bottom) }

        val tap = {
            if (!reduced) {
                val c = clock.doubleValue
                touch.begin(c, autoPlay)
                touch.end(c, tapped = true)
                awake.value = true
                clock.doubleValue += 1e-6
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "Isla Bloom. Golden Pineapple body oil, 100 ml, 28 dollars. " +
                        "A pineapple cut in half, the top lifting away as an amber bottle of body oil rises out of it. " +
                        "Drag up to lift the bottle out, tap for a splash."
                    role = Role.Button
                    onClick { tap(); true }
                }
                .pointerInput(reduced, layout) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val scale = size.width / 393f
                        val y0 = down.position.y / scale
                        if (y0 > layout.ctaTop - 8) {
                            // The Add to bag pill.
                            val x0 = down.position.x / scale
                            if (x0 in 169f..369f && y0 < layout.ctaTop + 56) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            return@awaitEachGesture
                        }
                        if (reduced) return@awaitEachGesture
                        touch.begin(clock.doubleValue, autoPlay)
                        awake.value = true
                        clock.doubleValue += 1e-6
                        var moved = 0f
                        var lastUptime = down.uptimeMillis
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            lastUptime = change.uptimeMillis
                            val dx = (change.position.x - down.position.x) / scale
                            val dy = (change.position.y - down.position.y) / scale
                            moved = maxOf(moved, hypot(dx, dy))
                            touch.drag(clock.doubleValue, dy.toDouble())
                            change.consume()
                        } while (event.changes.any { it.pressed })
                        touch.end(clock.doubleValue, tapped = lastUptime - down.uptimeMillis < 250 && moved < 10)
                    }
                },
        ) {
            val c = clock.doubleValue
            val frame = if (reduced) PineappleLiftProductMotion.Frame(open = 1.0, rise = 1.0, glow = 1.0) else touch.frame(c, autoPlay)
            val ambient = if (reduced) 0.0 else m.mod(c, m.LOOP)

            val canvas = drawContext.canvas.nativeCanvas
            val scale = size.width / 393f
            canvas.save()
            canvas.scale(scale, scale)
            art.drawBackground(canvas, layout, res)
            art.drawFlowers(canvas, layout, res, frame.drift, ambient)
            art.drawHero(canvas, layout, res, frame, ambient)
            art.drawDrops(canvas, layout, frame)
            art.drawUi(canvas, layout, res)
            canvas.restore()
        }
    }
}
