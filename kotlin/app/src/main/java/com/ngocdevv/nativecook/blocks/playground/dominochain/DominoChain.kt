package com.ngocdevv.nativecook.blocks.playground.dominochain

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
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
import kotlin.math.hypot

internal val DominoRoom = Color(0xFF07090A)

/**
 * The finger's speed at release, px/s: its travel over the last VELOCITY_WINDOW ms of samples.
 * (Compose's VelocityTracker gave unreliable readings for short flicks on the emulator.)
 */
private class DominoVelocity {
    private val t = LongArray(32)
    private val x = FloatArray(32)
    private val y = FloatArray(32)
    private var n = 0

    fun add(time: Long, px: Float, py: Float) {
        val k = n % t.size
        t[k] = time
        x[k] = px
        y[k] = py
        n += 1
    }

    private fun velocity(axis: FloatArray): Double {
        if (n < 2) return 0.0
        val last = (n - 1) % t.size
        var first = last
        var k = n - 2
        while (k >= 0 && k >= n - t.size) {
            val j = k % t.size
            if (t[last] - t[j] > VELOCITY_WINDOW) break
            first = j
            k -= 1
        }
        val dt = (t[last] - t[first]) / 1000.0
        return if (dt <= 0) 0.0 else (axis[last] - axis[first]) / dt
    }

    val vx: Double get() = velocity(x)
    val vy: Double get() = velocity(y)
}

private const val VELOCITY_WINDOW = 100L

/** The engine and its drawing, kept between frames. */
private class DominoScene(auto: Boolean, reduced: Boolean) {
    val engine = DominoEngine(auto = auto, reduced = reduced)
    val drawing = DominoDrawing()
    val frame = mutableIntStateOf(0)
}

/**
 * A run of ivory dominoes on a green felt table seen from above at an angle. Tap the first (or
 * flick any) and the run topples, each tile turning about its leading edge and striking the next;
 * drag across the table to lay a new run along your finger; sweep across a fallen run to stand it
 * back up, last tile first.
 *
 * @param autoPlay lays a spiral, tips it over, stands it back up and clears the table, on a loop.
 */
@Composable
fun DominoChain(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val view = LocalView.current
    val screen = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(screen) / screen.density
    val safeBottom = insets.getBottom(screen) / screen.density
    val scene = remember(reducedMotion) { DominoScene(auto, reducedMotion) }
    var fallen by remember { mutableStateOf(false) }

    LaunchedEffect(scene, auto) { scene.engine.setAuto(auto) }

    BoxWithConstraints(modifier.fillMaxSize().background(DominoRoom)) {
        val width = maxWidth.value.toDouble()
        val height = maxHeight.value.toDouble()
        val railY = safeTop + RAIL_AT

        LaunchedEffect(scene, width, height, railY) {
            scene.engine.layout = DominoLayout(width, height, railY)
            var last = -1L
            while (true) {
                withFrameNanos { now ->
                    // RN's first frame reports no previous frame: 16 ms.
                    val dt = if (last < 0) 0.016 else (now - last) / 1e9
                    last = now
                    val e = scene.engine
                    e.frame(dt)
                    val ev = e.events
                    if (ev != 0) {
                        e.events = 0
                        if (ev and EVENT_CLICK != 0) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (ev and EVENT_TIPPED != 0) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        if (ev and EVENT_RAISED != 0) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        if (ev and EVENT_FALLEN != 0) fallen = e.wasFallen
                    }
                    scene.frame.intValue += 1
                }
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "A run of dominoes on a felt table. Tap the first tile to topple the run, drag to lay a new run, sweep across a fallen run to stand it up."
                }
                .pointerInput(scene) {
                    // Gesture Handler's race of a pan (6 dp) and a tap (0.3 s, 10 dp), from one pointer.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val d = density
                        val sx = down.position.x / d
                        val sy = down.position.y / d
                        val e = scene.engine
                        val tracker = DominoVelocity()
                        tracker.add(down.uptimeMillis, down.position.x, down.position.y)
                        var panning = false
                        var lastX = sx
                        var lastY = sy
                        var upTime = down.uptimeMillis
                        var cancelled = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null) {
                                cancelled = true
                                break
                            }
                            for (h in change.historical) tracker.add(h.uptimeMillis, h.position.x, h.position.y)
                            tracker.add(change.uptimeMillis, change.position.x, change.position.y)
                            lastX = change.position.x / d
                            lastY = change.position.y / d
                            upTime = change.uptimeMillis
                            if (!change.pressed) break
                            if (!panning) {
                                if (hypot(lastX - sx, lastY - sy) >= 6) {
                                    panning = true
                                    e.panStart(sx.toDouble(), sy.toDouble(), lastX.toDouble(), lastY.toDouble())
                                }
                            } else {
                                e.panMove(lastX.toDouble(), lastY.toDouble(), (lastX - sx).toDouble(), (lastY - sy).toDouble())
                            }
                            if (panning) change.consume()
                        }
                        if (panning) {
                            e.panEnd(tracker.vx / d, tracker.vy / d, cancelled)
                        } else if (!cancelled && upTime - down.uptimeMillis <= 300 && hypot(lastX - sx, lastY - sy) <= 10) {
                            e.tap(lastX.toDouble(), lastY.toDouble())
                        }
                    }
                },
        ) {
            scene.frame.intValue
            if (!scene.engine.started) return@Canvas
            drawIntoCanvas { c ->
                val nc = c.nativeCanvas
                nc.save()
                nc.scale(density, density)
                scene.drawing.draw(nc, scene.engine)
                nc.restore()
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = (safeTop + 22).dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText(
                "TABLE NO. 4",
                style = TextStyle(color = Color(0xFFC9A978), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp),
            )
            BasicText(
                "Domino Run",
                style = TextStyle(color = Color(0xFFF3EAD8), fontSize = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, bottom = (safeBottom + 18).dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            BasicText(
                if (fallen) "Sweep across to stand them back up" else "Tap the first tile · drag to lay a new run",
                style = TextStyle(
                    color = Color(243, 234, 216, (0.82f * 255).toInt()),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.2.sp,
                    textAlign = TextAlign.Center,
                    shadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 1f * screen.density), 4f * screen.density),
                ),
            )
        }
    }
}
