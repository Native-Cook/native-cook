package com.ngocdevv.nativecook.blocks.navigation.deligraintabs

import android.graphics.Paint
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.abs

/**
 * An invented Italian deli's category screen on cream, after the pin's layout: two rows of six
 * small food silhouettes as tabs and one large silhouette in the middle with the category's name
 * and item count. When the tab changes, the big silhouette crumbles into 1,400 flour grains that
 * drain from the bottom, swirl, and rain into the next shape bottom-first while the tab and its
 * indicator move along the strip.
 *
 * Loop (LOOP = 9.6 s): Balsamico rests (0.9 s) → sifts into Flour & Pizza (1.5 s) → rests → Pasta
 * & Risotto (bottom row) → Sweets & Coffee → back to Balsamico. Tap a tab to sift to it; drag
 * across the big shape to stir the grains; the loop resumes 2 s after the last touch. Reduced
 * motion: Balsamico, still; a tab tap cross-fades.
 */
@Composable
fun DeliGrainTabs(autoPlay: Boolean = false) {
    val art = DeliGrainTabsArt
    val m = DeliGrainTabsMotion
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

    val epoch = remember { System.nanoTime() }
    fun now() = (System.nanoTime() - epoch) / 1e9
    val clock = remember { mutableDoubleStateOf(0.0) }
    val touch = remember { DeliGrainTabsTouch() }
    val paints = remember { DeliGrainTabsArt.Paints() }
    val awake = remember { mutableStateOf(false) }
    val looping = autoPlay && !reduced
    val running = active && (looping || awake.value)
    LaunchedEffect(running, looping) {
        if (!running) return@LaunchedEffect
        while (true) {
            withFrameNanos { frame -> clock.doubleValue = (frame - epoch) / 1e9 }
            val c = clock.doubleValue
            if (awake.value && !touch.touching) {
                val settle = if (looping) touch.restsAfter + m.RESUME_AFTER else touch.restsAfter
                if (c > settle + 0.1) {
                    awake.value = false
                    if (!looping) break
                }
            }
        }
    }

    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(art.CREAM))) {
        val k = maxWidth.value / 393f
        val height = (maxHeight.value / k).toDouble()
        val top = (WindowInsets.safeDrawing.getTop(density) / density.density / k).toDouble()
        val bottom = (WindowInsets.safeDrawing.getBottom(density) / density.density / k).toDouble()
        val layout = remember(height, top, bottom) { DeliGrainTabsArt.Layout(height, top, bottom) }
        val speckle = remember { Paint(Paint.ANTI_ALIAS_FLAG) }

        // The paper: drawn once, it reads no state that changes.
        Canvas(Modifier.fillMaxSize()) {
            art.drawPaper(drawContext.canvas.nativeCanvas, size.width, size.height, speckle)
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "Deli categories. " + art.categories.joinToString(", ") { "${it.name}, ${it.count} products" } +
                        ". Tap a category to show it; drag across the big shape to stir it."
                }
                .pointerInput(reduced, layout, autoPlay) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val scale = size.width / 393f
                        val x0 = down.position.x / scale
                        val y0 = down.position.y / scale
                        // Which tab, if any: a 54 × 60 pt box around each icon.
                        val tab = art.categories.indices.firstOrNull { i ->
                            abs(x0 - layout.tabX(i)) <= 27 && y0 - layout.tabY(i) in -26.0..34.0
                        }
                        val inHero = y0 > layout.topRow + 40 && y0 < layout.bottomRow - 40
                        if (tab != null) {
                            var moved = false
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if ((change.position - down.position).getDistance() > 12 * scale) moved = true
                            } while (event.changes.any { it.pressed })
                            if (!moved) {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                touch.tap(tab, now(), looping, reduced, layout)
                                awake.value = true
                                clock.doubleValue = now()
                            }
                            return@awaitEachGesture
                        }
                        if (!inHero || reduced) return@awaitEachGesture
                        touch.begin(x0.toDouble(), y0.toDouble(), now(), looping)
                        awake.value = true
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            touch.move((change.position.x / scale).toDouble(), (change.position.y / scale).toDouble(), now())
                            change.consume()
                        } while (event.changes.any { it.pressed })
                        touch.end(now())
                    }
                },
        ) {
            val c = clock.doubleValue
            val frame = touch.frame(c, looping)
            val canvas = drawContext.canvas.nativeCanvas
            val scale = size.width / 393f
            canvas.save()
            canvas.scale(scale, scale)
            art.draw(canvas, layout, frame, reduced, paints)
            canvas.restore()
        }
    }
}
