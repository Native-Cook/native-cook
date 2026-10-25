package com.ngocdevv.nativecook.blocks.playground.slinkystairs

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/*
 Slinky Stairs — "Desk Toys", a toy-physics playground: a steel slinky on a three-step maple
 staircase on a walnut desk, under a warm lamp. A port of SlinkyStairs.tsx (the reference); the
 numbers are the same.

 It walks down by itself: the top end tips over and lands on the next step, the coils pour through
 the arch, the last end flips over onto the new stack (SlinkyStairsMotion.kt has the timeline).
 Grab the top coil and pull: the slinky stretches from its stack after the finger, rubber-banding
 past 300 dp; let go and it collapses back on an underdamped spring (16 rad/s, damping 0.32),
 squashing below its height and ringing. A touch while it walks lets the current step finish at
 4x first. 2 s after the last touch it is lifted back to the top step (0.9 s) and the loop starts
 again. Reduced motion: no loop; the slinky rests on the top step, and a drag still stretches it.
*/

private const val BACK_FOR = 0.9
private const val MAX_PULL = 300.0
private const val ROLL = 26f
private const val FADE_IN = 0.25
private val LABELS = arrayOf("Ready", "Step 1 of 3", "Step 2 of 3", "Step 3 of 3", "Landed", "Ready")

private val INK = Color(0xFFF1E9DE)
private val MUTE = Color(241 / 255f, 233 / 255f, 222 / 255f, 0.62f)
private val FAINT = Color(241 / 255f, 233 / 255f, 222 / 255f, 0.42f)
private val AMBER = Color(0xFFF0BE7E)

private fun nowSeconds() = System.nanoTime() / 1e9

private class SlinkySim {
    var k = 0
    var px = 0.0
    var py = 0.0
    var vx = 0.0
    var vy = 0.0
    var grab = false
    var offX = 0.0
    var offY = 0.0
    var fingerX = 0.0
    var fingerY = 0.0
    var lastTouch = 0.0
    var ff = -1.0
    var ffTo = 0.0
    var backAt = -1.0
    var snapped = true
    var tick = 0
}

private class SlinkyArtSet(val layout: SlinkyLayout, val scene: SlinkyBaked)

private class SlinkyController(val auto: Boolean) {
    enum class Mode { Auto, Touch, Back }

    var now by mutableDoubleStateOf(0.0)
    var awake by mutableStateOf(true)
    var mode = Mode.Auto
    var loopStart = -1.0
    var artAt = -1.0
    var lastFrame = -1.0
    var sim = SlinkySim()
    var snapped = false
    private val pose = SlinkyPose()
    private var poseAt = Double.NaN

    fun loopTime(at: Double) = if (!auto || loopStart < 0) 0.0 else (((at - loopStart) % LOOP) + LOOP) % LOOP

    private fun restTopY(g: SlinkyGeo, k: Int) = g.baseY[k] - (SLINKY_N - 1) * g.pitch

    fun pose(g: SlinkyGeo, at: Double = now): SlinkyPose {
        if (at == poseAt) return pose
        poseAt = at
        val s = sim
        when {
            mode == Mode.Auto -> SlinkyMotion.loopPose(loopTime(at), g, pose)
            s.ff >= 0 -> SlinkyMotion.loopPose(s.ff, g, pose)
            mode == Mode.Back -> {
                pose.clear()
                val v = min(1.0, (at - s.backAt) / BACK_FOR)
                SlinkyMotion.liftRings(g, s.k, v, pose)
                pose.shadow(g.baseX[s.k], g.baseY[s.k], g.R, 1 - min(1.0, v * 4))
                pose.shadow(g.baseX[0], g.baseY[0], g.R, max(0.0, (v - 0.7) / 0.3))
                pose.label = 4 + SlinkyMotion.easeInOutCubic(v)
            }
            else -> {
                pose.clear()
                SlinkyMotion.stretchRings(g, s.k, s.px, s.py, pose)
                pose.shadow(g.baseX[s.k], g.baseY[s.k], g.R, 1.0)
                pose.label = if (s.k == 3) 4.0 else s.k.toDouble()
            }
        }
        return pose
    }

    fun frame(g: SlinkyGeo, at: Double) {
        if (loopStart < 0) loopStart = at
        if (artAt < 0) artAt = at
        val dt = if (lastFrame < 0) 1.0 / 60 else min(at - lastFrame, 1.0 / 20)
        lastFrame = at
        snapped = false
        if (mode != Mode.Auto) {
            val s = sim
            val restX = g.baseX[s.k]
            val restY = restTopY(g, s.k)
            if (s.ff >= 0) {
                // The current step finishes at 4x before the finger takes the top coil.
                s.ff += dt * 4
                if (s.ff >= s.ffTo) {
                    s.ff = -1.0
                    s.px = restX
                    s.py = restY
                    s.vx = 0.0
                    s.vy = 0.0
                    s.offX = restX - s.fingerX
                    s.offY = restY - s.fingerY
                }
            } else if (!s.grab) {
                // Underdamped spring back onto the stack, in small steps.
                val w = 16.0
                val z = 0.32
                val n = 4
                val h = dt / n
                val before = s.py - restY
                for (i in 0 until n) {
                    s.vx += (-w * w * (s.px - restX) - 2 * z * w * s.vx) * h
                    s.vy += (-w * w * (s.py - restY) - 2 * z * w * s.vy) * h
                    s.px += s.vx * h
                    s.py += s.vy * h
                }
                if (!s.snapped && before < 0 && s.py - restY >= 0) {
                    s.snapped = true
                    snapped = true
                }
            }
            if (mode == Mode.Touch && !s.grab && s.ff < 0 && at - s.lastTouch > 2) {
                if (s.k == 0) {
                    restart(at)
                } else {
                    s.backAt = at
                    mode = Mode.Back
                }
            } else if (mode == Mode.Back && at - s.backAt >= BACK_FOR) {
                restart(at)
            }
        }
        now = at
    }

    private fun restart(at: Double) {
        sim = SlinkySim()
        mode = Mode.Auto
        loopStart = at
    }

    fun idle(at: Double) = at - artAt >= FADE_IN && !auto && mode == Mode.Auto

    /** Freezes the loop where it is (finishing a step at 4x if one is under way). */
    fun takeOver(g: SlinkyGeo, at: Double) {
        val s = SlinkySim()
        s.lastTouch = at
        val t = loopTime(at)
        if (t >= SLINKY_WALK_FROM && t < 6.4) {
            val step = min(2, floor((t - SLINKY_WALK_FROM) / SLINKY_STEP_FOR).toInt())
            s.ff = t
            s.ffTo = SLINKY_WALK_FROM + (step + 1) * SLINKY_STEP_FOR
            s.k = step + 1
        } else if (t >= SLINKY_LIFT_FROM && t < SLINKY_LIFT_FROM + SLINKY_LIFT_FOR) {
            s.ff = t
            s.ffTo = SLINKY_LIFT_FROM + SLINKY_LIFT_FOR
            s.k = 0
        } else {
            s.k = if (t >= 6.4 && t < SLINKY_LIFT_FROM) 3 else 0
        }
        s.px = g.baseX[s.k]
        s.py = restTopY(g, s.k)
        sim = s
        mode = Mode.Touch
        poseAt = Double.NaN
    }
}

/**
 * "Desk Toys": a steel slinky walking down three maple steps on a desk. Drag its top coil to
 * stretch it and let go to watch it ring back.
 *
 * @param autoPlay plays the 9 s walk-down-and-lift loop until touched.
 */
@Composable
fun SlinkyStairs(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val view = LocalView.current
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val c = remember(auto) { SlinkyController(auto) }

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFF212B2A)).clipToBounds()) {
        val screen = LocalDensity.current
        val density = screen.density
        val insets = WindowInsets.safeDrawing
        val top = insets.getTop(screen) / density
        val bottom = insets.getBottom(screen) / density
        val w = maxWidth.value
        val h = maxHeight.value
        val g = remember(w, h, top, bottom) { SlinkyLayout(w, h, top, bottom) }
        val painter = remember { SlinkyPainter() }
        var art by remember { mutableStateOf<SlinkyArtSet?>(null) }

        LaunchedEffect(w, h, top, bottom, density) {
            // Wait for the insets to settle (the demo hides the status bar) before baking.
            delay(120)
            val layout = SlinkyLayout(w, h, top, bottom)
            val scene = withContext(Dispatchers.Default) { SlinkyStairsArt.bakeScene(layout, top, min(2f, density)) }
            // A rebake (the insets settled late) swaps the art in place; only the first one fades in and starts the loop.
            val first = art == null
            art = SlinkyArtSet(layout, scene)
            if (first) {
                c.artAt = -1.0
                c.loopStart = -1.0
            }
            c.awake = true
        }

        val current = art
        LaunchedEffect(current, c, c.awake) {
            if (current == null || !c.awake) return@LaunchedEffect
            while (true) {
                withFrameNanos { n -> c.frame(current.layout.geo, n / 1e9) }
                if (c.snapped) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                if (c.idle(c.now)) {
                    c.awake = false
                    break
                }
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "A steel slinky walking down three wooden steps. Drag its top coil to stretch it." }
                .pointerInput(current, c) {
                    val a = current ?: return@pointerInput
                    val lay = a.layout
                    val geo = lay.geo
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val ex = down.position.x / density.toDouble()
                        val ey = down.position.y / density.toDouble()
                        if (c.mode == SlinkyController.Mode.Back || ey > lay.panelY || ey < top + 96) return@awaitEachGesture
                        val at = nowSeconds()
                        c.awake = true
                        if (c.mode == SlinkyController.Mode.Auto) c.takeOver(geo, at)
                        val s = c.sim
                        val restY = geo.baseY[s.k] - (SLINKY_N - 1) * geo.pitch
                        val nearX = abs(ex - geo.baseX[s.k]) < geo.R + 40
                        val nearY = ey > min(restY, s.py) - 60 && ey < geo.baseY[s.k] + 20
                        if (s.ff < 0 && !(nearX && nearY)) {
                            s.lastTouch = at
                            return@awaitEachGesture
                        }
                        s.grab = true
                        s.snapped = false
                        s.tick = 0
                        s.lastTouch = at
                        s.fingerX = ex
                        s.fingerY = ey
                        s.offX = s.px - ex
                        s.offY = s.py - ey
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                                ch.consume()
                                tracker.addPosition(ch.uptimeMillis, ch.position)
                                val x = ch.position.x / density.toDouble()
                                val y = ch.position.y / density.toDouble()
                                s.lastTouch = nowSeconds()
                                s.fingerX = x
                                s.fingerY = y
                                if (s.ff >= 0) continue
                                val bx = geo.baseX[s.k]
                                val by = geo.baseY[s.k]
                                var dx = x + s.offX - bx
                                // It cannot be pushed into the step, nor squashed below half its height.
                                var dy = min(y + s.offY - by, -(SLINKY_N - 1) * geo.pitch * 0.5)
                                val l = hypot(dx, dy)
                                if (l > MAX_PULL) {
                                    val over = l - MAX_PULL
                                    val l2 = MAX_PULL + over / (1 + over / 100)
                                    dx *= l2 / l
                                    dy *= l2 / l
                                }
                                s.px = bx + dx
                                s.py = by + dy
                                val v = tracker.calculateVelocity()
                                s.vx = v.x / density.toDouble()
                                s.vy = v.y / density.toDouble()
                                val step = floor(hypot(dx, dy) / 60).toInt()
                                if (step != s.tick) {
                                    s.tick = step
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            }
                        } finally {
                            s.grab = false
                            s.lastTouch = nowSeconds()
                            s.vx = max(-1500.0, min(1500.0, s.vx))
                            s.vy = max(-1500.0, min(1500.0, s.vy))
                        }
                    }
                },
        ) {
            val a = current
            scale(density, density, Offset.Zero) {
                val canvas = drawContext.canvas.nativeCanvas
                if (a == null) {
                    painter.placeholder(canvas, g)
                    return@scale
                }
                val pose = c.pose(a.layout.geo)
                val s = c.sim
                val touch = if (c.mode == SlinkyController.Mode.Touch && s.grab) 1f else 0f
                val fade = SlinkyMotion.clamp01((c.now - c.artAt) / FADE_IN)
                if (c.artAt < 0 || fade < 1) {
                    painter.placeholder(canvas, a.layout)
                    val save = canvas.saveLayerAlpha(0f, 0f, a.layout.w, a.layout.h, (255 * (if (c.artAt < 0) 0.0 else fade)).toInt())
                    painter.frame(canvas, a.layout, a.scene, pose, s.fingerX.toFloat(), s.fingerY.toFloat(), touch)
                    canvas.restoreToCount(save)
                } else {
                    painter.frame(canvas, a.layout, a.scene, pose, s.fingerX.toFloat(), s.fingerY.toFloat(), touch)
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = (top + 14).dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText("DESK TOYS", style = TextStyle(color = AMBER, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp))
            BasicText("Slinky", style = TextStyle(color = INK, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp))
            BasicText("A walk down three maple steps", style = TextStyle(color = MUTE, fontSize = 15.sp, fontWeight = FontWeight.Medium))
        }

        // The panel: the rolling step label, a chip, the hint and the stats.
        Column(
            Modifier
                .offset(x = 16.dp, y = g.panelY.dp)
                .width((w - 32).dp)
                .height(g.panelH.dp)
                .padding(start = 20.dp, end = 20.dp, top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(30.dp).clipToBounds()) {
                    val roll = if (reduced) 0f else ROLL
                    for (i in LABELS.indices) {
                        BasicText(
                            LABELS[i],
                            Modifier.fillMaxWidth().offset(y = 1.dp).graphicsLayer {
                                val l = art?.let { c.pose(it.layout.geo).label.toFloat() } ?: 0f
                                val d = i - l
                                alpha = max(0f, 1 - abs(d))
                                translationY = d * roll * density
                            },
                            style = TextStyle(color = INK, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                            maxLines = 1,
                        )
                    }
                }
                Box(
                    Modifier.height(26.dp).background(Color(240 / 255f, 190 / 255f, 126 / 255f, 0.14f), RoundedCornerShape(13.dp)).padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText("Spring steel", style = TextStyle(color = AMBER, fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
                }
            }
            BasicText("Drag the top coil to stretch it, then let go", style = TextStyle(color = MUTE, fontSize = 14.sp, fontWeight = FontWeight.Medium))
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                SlinkyStat("40", "coils")
                SlinkyStat("1.7 s", "per step")
                SlinkyStat("92 g", "weight")
            }
        }
    }
}

@Composable
private fun SlinkyStat(value: String, label: String) {
    BasicText(
        buildAnnotatedString {
            withStyle(SpanStyle(color = INK, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")) { append(value) }
            append(" ")
            append(label)
        },
        style = TextStyle(color = FAINT, fontSize = 13.sp, fontWeight = FontWeight.Medium),
    )
}
