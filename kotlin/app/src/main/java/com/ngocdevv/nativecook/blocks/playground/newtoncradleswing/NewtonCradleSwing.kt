package com.ngocdevv.nativecook.blocks.playground.newtoncradleswing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 Newton Cradle Swing — "Desk Toys · Momentum": a chrome Newton's cradle on a walnut desk under a
 lamp. Pull a ball (or two, three…) out and let go: the swing passes through the still balls,
 one collision every half period, a light click each time, 3 % of the swing lost per hit.

 One Canvas; the room, the front of the frame and one ball are bitmaps rendered once, each frame
 draws five balls, ten strings and five contact shadows (NewtonCradleSwingArt.kt). The motion is
 a folded pendulum (NewtonCradleSwingMotion.kt): analytic in autoPlay (LOOP = 8 s), integrated
 under the finger. Compose only recomposes the status text when its words change.

 Modes: 0 loop · 1 finger on a ball · 2 free swing · 3 easing back to rest before the loop
 restarts (2 s after the last touch, 0.9 s) · 4 reduced motion's gentle return.
 */

private val Background = Color(0xFF120E0B)
private val Ink = Color(0xFFF3EBE1)
private val InkSoft = Color(0xFFAB9B8B)
private const val RETURN_AFTER = 2.0
private const val RETURN_FOR = 0.9
private const val CARD_H = 104f
private val StatusWords = listOf("Ready", "Pulling back", "Swinging")
private val Tabular = TextStyle(fontFeatureSettings = "tnum")

private fun nowSeconds() = System.nanoTime() / 1e9

private class CradleState(auto: Boolean) {
    val frame = mutableIntStateOf(0)
    val wake = mutableIntStateOf(0)
    /** kind × 1000 + collisions; state, read only by the status text. */
    val status = mutableIntStateOf(0)
    var now = -1.0
    var loopStart = 0.0
    var mode = if (auto) 0 else 2
    val sim = CradleSim()
    /** The ball held by the finger, or −1. */
    var grabBall = -1
    var grabPsi = 0.0
    var grabVel = 0.0
    var grabTime = 0.0
    var lastTouch = 0.0
    var backStart = 0.0
    var backPsi = 0.0
    var psi = 0.0
    var group = 1
    var finger = 0.0
    var energy = 0.0
    val angles = DoubleArray(CRADLE_BALLS)

    fun setStatus(v: Int) { if (status.intValue != v) status.intValue = v }

    fun moving(auto: Boolean) = mode != 2 || auto || sim.psi != 0.0 || sim.vel != 0.0
}

/** One frame; returns true when a collision happened under the finger's swing. */
private fun cradleStep(s: CradleState, t0: Double, dt: Double, auto: Boolean): Boolean {
    var hit = false
    when (s.mode) {
        0 -> {
            val t = (t0 - s.loopStart).mod(LOOP)
            s.psi = cradleLoopPsi(t)
            s.group = 1
            s.finger = cradleLoopFinger(t)
            s.energy = cradleLoopEnergy(t)
            val kind = if (t < 1.2 || t > 7.05) 0 else if (t < 2) 1 else 2
            s.setStatus(kind * 1000 + cradleLoopHits(t))
        }
        1 -> {
            s.finger = 0.0
            s.psi = s.sim.psi
            s.group = s.sim.n
            s.energy = min(1.0, abs(s.sim.psi) / CRADLE_PULL)
            s.setStatus(1000)
        }
        else -> {
            s.finger = 0.0
            val sim = s.sim
            if (s.mode == 4) {
                sim.psi = s.backPsi * (1 - cradleEaseInOut(cradleSeg(t0 - s.backStart, 0.0, 0.5)))
                sim.vel = 0.0
                if (t0 - s.backStart >= 0.5) { sim.psi = 0.0; s.mode = 2 }
            } else {
                hit = sim.step(dt)
            }
            var shown = sim.psi
            var amp = sim.amplitude()
            if (s.mode == 3) {
                val k = cradleEaseInOut(cradleSeg(t0 - s.backStart, 0.0, RETURN_FOR))
                shown *= 1 - k
                amp *= 1 - k
                if (k >= 1) {
                    s.mode = 0
                    s.loopStart = t0
                    shown = 0.0
                    sim.psi = 0.0
                    sim.vel = 0.0
                }
            } else if (s.mode == 2 && auto && t0 - s.lastTouch > RETURN_AFTER) {
                s.mode = 3
                s.backStart = t0
                s.backPsi = sim.psi
            }
            s.psi = shown
            s.group = sim.n
            s.energy = min(1.0, amp / CRADLE_PULL)
            s.setStatus(if (amp < 0.004) 0 else 2000 + min(999, sim.hits))
        }
    }
    cradleBallAngles(s.psi, s.group, s.angles)
    return hit
}

@Composable
private fun StatusLine(status: MutableIntState) {
    val v = status.intValue
    val kind = v / 1000
    val hits = v % 1000
    val text = if (kind == 2 && hits > 0) "$hits ${if (hits == 1) "collision" else "collisions"}" else StatusWords[kind]
    Text(text, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, style = Tabular)
}

@Composable
fun NewtonCradleSwing(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val state = remember(auto) { CradleState(auto) }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current.density
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val insetTop = insets.calculateTopPadding().value
    val insetBottom = insets.calculateBottomPadding().value

    BoxWithConstraints(modifier.fillMaxSize().background(Background).clipToBounds()) {
        val w = maxWidth.value
        val h = maxHeight.value
        val g = remember(w, h) { CradleGeo(w, h) }
        val back = remember(g, density) { cradleRenderBack(g, density).asImageBitmap() }
        val front = remember(g, density) { cradleRenderFront(g, density) }
        val ball = remember(g, density) { cradleRenderBall(g.r, density) }
        val painter = remember(g) { CradlePainter(g) }

        LaunchedEffect(state, auto, reduced) {
            while (true) {
                withFrameNanos { nanos ->
                    val t0 = nanos / 1e9
                    val dt = if (state.now < 0) 1.0 / 60 else min(t0 - state.now, 1.0 / 30)
                    if (state.now < 0 && state.mode == 0) state.loopStart = t0
                    state.now = t0
                    if (cradleStep(state, t0, dt, auto) && !reduced) haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    state.frame.intValue++
                }
                if (!state.moving(auto)) {
                    // At rest: sleep until a touch wakes the loop.
                    val seen = state.wake.intValue
                    snapshotFlow { state.wake.intValue }.first { it != seen }
                    state.now = -1.0
                }
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Newton's cradle. Pull a sphere sideways and let go." }
                .pointerInput(state, g, reduced) {
                    /** The finger's angle around ball i's pivot, folded onto the side it pulls toward; and the group size. */
                    fun pull(i: Int, x: Float, y: Float): Pair<Double, Int> {
                        val theta = atan2((x - g.xs[i]).toDouble(), (y - g.pivotY).toDouble())
                        val left = x < g.xs[i]
                        // Pulling left takes ball i and every ball left of it; pulling right, i and those right of it.
                        return if (left) min(0.0, cradleRubberBand(theta)) to i + 1 else max(0.0, cradleRubberBand(theta)) to CRADLE_BALLS - i
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val ex = down.position.x / density
                        val ey = down.position.y / density
                        var best = -1
                        var bestD = g.r * 2.4f
                        for (i in 0 until CRADLE_BALLS) {
                            val a = state.angles[i]
                            val d = hypot(ex - (g.xs[i] + g.l * sin(a).toFloat()), ey - (g.pivotY + g.l * cos(a).toFloat()))
                            if (d < bestD) { bestD = d; best = i }
                        }
                        if (best < 0) return@awaitEachGesture
                        val p = pull(best, ex, ey)
                        state.grabBall = best
                        state.grabPsi = p.first
                        state.grabVel = 0.0
                        state.grabTime = nowSeconds()
                        state.sim.psi = p.first; state.sim.vel = 0.0; state.sim.n = p.second; state.sim.hits = 0
                        state.mode = 1
                        state.wake.intValue++
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            val q = pull(state.grabBall, change.position.x / density, change.position.y / density)
                            val t = nowSeconds()
                            val dtt = max(1.0 / 120, t - state.grabTime)
                            val v = 0.65 * state.grabVel + 0.35 * (q.first - state.grabPsi) / dtt
                            state.grabPsi = q.first
                            state.grabVel = v
                            state.grabTime = t
                            state.sim.psi = q.first; state.sim.vel = v; state.sim.n = q.second; state.sim.hits = 0
                        }
                        state.grabBall = -1
                        val t = nowSeconds()
                        state.lastTouch = t
                        if (reduced) {
                            state.backStart = t
                            state.backPsi = state.sim.psi
                            state.mode = 4
                        } else {
                            state.sim.vel = state.grabVel.coerceIn(-6.0, 6.0)
                            state.sim.hits = 0
                            state.mode = 2
                        }
                        state.wake.intValue++
                    }
                },
        ) {
            state.frame.intValue
            drawImage(back)
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                c.save()
                c.scale(density, density)
                painter.draw(c, state.angles, state.finger.toFloat(), ball, front, density)
                c.restore()
            }
        }

        Column(
            Modifier
                .padding(start = 24.dp, end = 24.dp, top = (insetTop + 18f).dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("DESK TOYS", color = Color(0xFFD2B48F), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.4.sp)
            Text("Momentum", color = Ink, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
            Text("Five chrome spheres on a steel frame", color = InkSoft, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }

        val bottom = max(insetBottom, 12f)
        val cardTop = min(g.baseBottom + 28f, h - bottom - 40f - CARD_H)
        val shape = RoundedCornerShape(20.dp)
        Column(
            Modifier
                .offset(y = cardTop.dp)
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .shadow(14.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(shape)
                .background(Color(0xC7160F0B))
                .border(0.5.dp, Color(0x24FFE8CE), shape)
                .drawBehind { drawLine(Color(0x14FFF0DE), Offset(20.dp.toPx(), 1.dp.toPx()), Offset(size.width - 20.dp.toPx(), 1.dp.toPx()), strokeWidth = 1.dp.toPx()) }
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("SESSION", color = InkSoft, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp)
                    StatusLine(state.status)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ENERGY", color = InkSoft, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp)
                    Box(
                        Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0x1AFFF0DE)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    state.frame.intValue
                                    scaleX = max(0.001f, state.energy.toFloat())
                                    transformOrigin = TransformOrigin(0f, 0.5f)
                                }
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFFE7C49B)),
                        )
                    }
                }
            }
            Text("Period 1.02 s · 3 % lost at each collision", color = InkSoft, fontSize = 13.sp, fontWeight = FontWeight.Medium, style = Tabular)
        }

        Text(
            "Pull a sphere and let go · grab two for a double",
            color = Ink.copy(alpha = 0.5f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 24.dp, end = 24.dp, bottom = (bottom + 14f).dp)
                .fillMaxWidth(),
        )
    }
}

