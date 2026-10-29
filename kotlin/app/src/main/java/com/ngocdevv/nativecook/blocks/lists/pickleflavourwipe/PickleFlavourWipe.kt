package com.ngocdevv.nativecook.blocks.lists.pickleflavourwipe

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
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.abs

/**
 * A pantry shop's flavour browser for an invented pickle brand, jarful. Each flavour is a page in
 * its own colour with a jar and its ingredients floating around it; a diagonal slash wipes the next
 * flavour in (Mango → Lime → Garlic), the jar springs up from below, the ingredients pop out from
 * behind it and the big title slides in with a smear. The last page lines the three jars up.
 *
 * Loop (LOOP = 10 s): rest on Mango → wipes at 1.0, 3.25, 5.5 s → the Trio line-up → Mango wipes
 * back in at 7.75 s and settles → rest. Swipe left or right to wipe to the next or previous
 * flavour: the edge follows the finger, release completes the wipe or springs it back. Reduced
 * motion: the Mango page, still; swipes cross-fade.
 */
@Composable
fun PickleFlavourWipe(autoPlay: Boolean = false) {
    val m = PickleFlavourWipeMotion
    val art = PickleFlavourWipeArt
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
    BlockSystemBars(darkIcons = false)

    val res = remember { art.load(context) }
    val clock = remember { mutableDoubleStateOf(0.0) }
    val touch = remember { PickleFlavourWipeTouch() }
    val awake = remember { mutableStateOf(false) }
    val running = active && ((autoPlay && !reduced) || awake.value)
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
            val c = clock.doubleValue
            if ((!autoPlay || reduced) && !touch.touching && c > touch.restsAfter + 0.1 && !touch.fading(c)) {
                awake.value = false
                break
            }
        }
    }

    val step = { dx: Double ->
        val c = clock.doubleValue
        if (reduced) {
            touch.fadeSwipe(c, dx * 100)
        } else {
            touch.begin(c, autoPlay)
            touch.drag(c, dx * 120)
            touch.end(c, dx * 1000)
        }
        awake.value = true
        clock.doubleValue += 1e-6
    }

    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(art.argb(art.pages[0].mid)))) {
        val k = maxWidth.value / 393f
        val height = (maxHeight.value / k).toDouble()
        val top = (WindowInsets.safeDrawing.getTop(density) / density.density / k).toDouble()
        val bottom = (WindowInsets.safeDrawing.getBottom(density) / density.density / k).toDouble()
        val layout = remember(height, top, bottom) { PickleFlavourWipeArt.Layout(height, top, bottom) }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "jarful pickles. Mango Mixed Pickle, Lime Chilli Pickle, Garlic Whole Clove Pickle " +
                        "and the Trio tasting box. Swipe to wipe to the next flavour."
                    customActions = listOf(
                        CustomAccessibilityAction("Next flavour") { step(-1.0); true },
                        CustomAccessibilityAction("Previous flavour") { step(1.0); true },
                    )
                }
                .pointerInput(reduced, layout) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val scale = size.width / 393f
                        val x0 = down.position.x / scale
                        val y0 = down.position.y / scale
                        if (y0 > layout.ctaTop - 8) {
                            // The Add to basket pill.
                            if (x0 in 179f..369f && y0 < layout.ctaTop + 56) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            return@awaitEachGesture
                        }
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var dragging = false
                        var dx = 0.0
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            dx = ((change.position.x - down.position.x) / scale).toDouble()
                            if (!reduced) {
                                if (!dragging && abs(dx) > 6) {
                                    dragging = true
                                    touch.begin(clock.doubleValue, autoPlay)
                                    awake.value = true
                                    clock.doubleValue += 1e-6
                                }
                                if (dragging) {
                                    touch.drag(clock.doubleValue, dx)
                                    change.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })
                        if (reduced) {
                            touch.fadeSwipe(clock.doubleValue, dx)
                            awake.value = true
                            clock.doubleValue += 1e-6
                        } else if (dragging) {
                            touch.end(clock.doubleValue, (tracker.calculateVelocity().x / scale).toDouble())
                        }
                    }
                },
        ) {
            val c = clock.doubleValue
            val scene = if (reduced) touch.fadeScene(c) else touch.scene(c, autoPlay)
            val ambient = if (reduced) 0.0 else m.mod(c, m.LOOP)

            val canvas = drawContext.canvas.nativeCanvas
            val scale = size.width / 393f
            canvas.save()
            canvas.scale(scale, scale)
            art.drawScene(canvas, layout, res, scene, ambient)
            art.drawChrome(canvas, layout, res, scene)
            canvas.restore()
        }
    }
}
