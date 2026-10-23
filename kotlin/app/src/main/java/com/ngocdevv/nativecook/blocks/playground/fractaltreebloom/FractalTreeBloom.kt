package com.ngocdevv.nativecook.blocks.playground.fractaltreebloom

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

internal val TreeBackground = Color(0xFFF6E6DF)
private val Seasons = listOf("Spring", "Summer", "Autumn", "Winter")
// Reanimated's default timing curve (ease in and out, quadratic) for the season's name.
private val Fade = CubicBezierEasing(0.455f, 0.03f, 0.515f, 0.955f)

/** The engine and its drawing, kept between frames. */
private class TreeScene(auto: Boolean, reduced: Boolean, seed: Int, density: Float) {
    val engine = TreeEngine(auto = auto, reduced = reduced, seed = seed)
    val drawing = TreeDrawing(density)
    val frame = mutableIntStateOf(0)
}

/** The finger's speed at release, px/s: its travel over the last 100 ms of samples. */
private class TreeVelocity {
    private val t = LongArray(32)
    private val x = FloatArray(32)
    private var n = 0

    fun add(time: Long, px: Float) {
        val k = n % t.size
        t[k] = time
        x[k] = px
        n += 1
    }

    val vx: Double
        get() {
            if (n < 2) return 0.0
            val last = (n - 1) % t.size
            var first = last
            var k = n - 2
            while (k >= 0 && k >= n - t.size) {
                val j = k % t.size
                if (t[last] - t[j] > 100) break
                first = j
                k -= 1
            }
            val dt = (t[last] - t[first]) / 1000.0
            return if (dt <= 0) 0.0 else (x[last] - x[first]) / dt
        }
}

/**
 * A fractal tree on a small mound. It grows from a seed, level by level, sways in a wind that
 * reaches its twigs last, and blossoms; each tap turns the season (summer leaves, autumn colour and
 * falling leaves, winter snow), dragging across bends the wind, and holding draws it back into its
 * seed to grow again.
 *
 * @param autoPlay grows the tree, blooms it and turns it through the seasons, on a loop.
 * @param seed the seed the tree grows from: the same seed grows the same tree.
 */
@Composable
fun FractalTreeBloom(modifier: Modifier = Modifier, autoPlay: Boolean = false, seed: Int = 27) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val view = LocalView.current
    val screen = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(screen) / screen.density
    val safeBottom = insets.getBottom(screen) / screen.density
    val scene = remember(reducedMotion, seed) { TreeScene(auto, reducedMotion, seed, screen.density) }
    var seasonName by remember { mutableIntStateOf(0) }

    LaunchedEffect(scene, auto) { scene.engine.setAuto(auto) }

    BoxWithConstraints(modifier.fillMaxSize().background(TreeBackground)) {
        val width = maxWidth.value.toDouble()
        val height = maxHeight.value.toDouble()

        LaunchedEffect(scene, width, height, safeBottom) {
            var last = -1L
            while (true) {
                withFrameNanos { now ->
                    // RN's first frame reports no previous frame: 16 ms.
                    val dt = if (last < 0) 0.016 else (now - last) / 1e9
                    last = now
                    val e = scene.engine
                    e.frame(dt, width, height, safeBottom.toDouble())
                    val ev = e.events
                    if (ev != 0) {
                        e.events = 0
                        if (ev and EVENT_TURNED != 0) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        if (ev and EVENT_PLANTED != 0) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (ev and EVENT_SEASON != 0) seasonName = e.shownSeason
                    }
                    scene.frame.intValue += 1
                }
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "A fractal tree. Tap for the next season, drag across to bend the wind, hold to grow it again." }
                .pointerInput(scene) {
                    // Gesture Handler's race of a pan (past 10 dp across), a long press (0.5 s, 10 dp)
                    // and a tap (0.3 s), from one pointer.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val d = density
                        val sx = down.position.x / d
                        val sy = down.position.y / d
                        val e = scene.engine
                        val tracker = TreeVelocity()
                        tracker.add(down.uptimeMillis, sx)
                        var panning = false
                        var pressed = false
                        var wandered = false
                        var upTime = down.uptimeMillis
                        var cancelled = false
                        try {
                            while (true) {
                                val waiting = !panning && !pressed && !wandered
                                val event = if (waiting) {
                                    val held = SystemClock.uptimeMillis() - down.uptimeMillis
                                    withTimeoutOrNull(max(1L, 500L - held)) { awaitPointerEvent() }
                                } else {
                                    awaitPointerEvent()
                                }
                                if (event == null) {
                                    pressed = true
                                    e.longPress()
                                    continue
                                }
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null) {
                                    cancelled = true
                                    break
                                }
                                for (h in change.historical) tracker.add(h.uptimeMillis, h.position.x / d)
                                val x = change.position.x / d
                                val y = change.position.y / d
                                tracker.add(change.uptimeMillis, x)
                                upTime = change.uptimeMillis
                                if (!change.pressed) break
                                val tx = x - sx
                                // A long press gives up once the finger wanders off.
                                if (hypot(tx, y - sy) > 10) wandered = true
                                if (panning) {
                                    e.windMove(tx.toDouble())
                                } else if (!pressed && abs(tx) > 10) {
                                    panning = true
                                    e.windStart()
                                    e.windMove(tx.toDouble())
                                }
                                if (panning) change.consume()
                            }
                        } catch (stop: kotlinx.coroutines.CancellationException) {
                            // The pointer input restarted or left mid-drag: let go of the wind, then
                            // let the cancellation through.
                            if (panning) e.windEnd(0.0, cancelled = true)
                            throw stop
                        }
                        if (panning) {
                            e.windEnd(tracker.vx, cancelled)
                        } else if (!pressed && !cancelled && upTime - down.uptimeMillis <= 300) {
                            e.tap()
                        }
                    }
                },
        ) {
            scene.frame.intValue
            if (scene.engine.tree == null) return@Canvas
            drawIntoCanvas { c ->
                val nc = c.nativeCanvas
                nc.save()
                nc.scale(density, density)
                scene.drawing.draw(nc, scene.engine, width.toFloat(), height.toFloat())
                nc.restore()
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = (safeTop + 22).dp)
                .height(64.dp),
        ) {
            BasicText(
                "SEED NO. $seed",
                style = TextStyle(color = Color(52, 38, 34, (0.62f * 255).toInt()), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp),
            )
            AnimatedContent(
                targetState = seasonName,
                modifier = Modifier.padding(top = 18.dp),
                transitionSpec = {
                    fadeIn(tween(380, delayMillis = 160, easing = Fade)) togetherWith fadeOut(tween(150, easing = Fade))
                },
                label = "season",
            ) { name ->
                BasicText(
                    Seasons.getOrElse(name) { Seasons[0] },
                    style = TextStyle(color = Color(0xFF33241F), fontSize = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, bottom = (safeBottom + 18).dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            BasicText(
                "Drag to bend the wind · tap for the next season",
                style = TextStyle(
                    color = Color(40, 30, 26, (0.7f * 255).toInt()),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.2.sp,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}
