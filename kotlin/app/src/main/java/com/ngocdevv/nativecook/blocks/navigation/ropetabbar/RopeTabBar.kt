package com.ngocdevv.nativecook.blocks.navigation.ropetabbar

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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Rope Tab Bar — "Basecamp", an outdoor app whose tab indicator is a knot on a climbing rope tied
 across the top of the tab bar. Change tab and the knot slides along: the rope sags under it,
 lifts where it left, whips and rings down; the icons it passes are tugged down a few dp.
 The pages cross-slide with a parallax and the large title cross-fades as it slides.

 One Canvas over the bar: the bar rendered once into a bitmap, the rope (48 samples, a braid in
 two paths), the knot and the ripple drawn per frame from plain fields, invalidated by one frame
 counter. The rope is closed form in every mode (RopeTabBarMotion.kt). Only the draw and
 graphicsLayer lambdas read the per-frame values; the screen recomposes when nothing moves.

 Modes: 0 loop · 1 touch (knot on a spring toward its tab) · 2 easing back to Explore 2 s after
 the last touch (0.9 s), then the loop restarts.
 */

private val Background = Color(0xFF13201A)
private const val BAR_CONTENT = 78f
private val TabNames = listOf("Explore", "Routes", "Gear", "Profile")
private val TabIcons = listOf(Icons.Filled.Landscape, Icons.Filled.Map, Icons.Filled.Backpack, Icons.Filled.AccountCircle)
private val Active = Color(0xFFF4EAD8)
private val Idle = Color(0xFF7F9387)
private const val RETURN_AFTER = 2.0
private const val RETURN_FOR = 0.9
private const val MAX_KICKS = 8

private fun nowSeconds() = System.nanoTime() / 1e9

/** Everything the frame loop and the gestures share. Plain fields; `frame` invalidates the drawing. */
private class RopeState(auto: Boolean) {
    val frame = mutableIntStateOf(0)
    /** The knot's place 0…3 (a tab index); state so the active tab can be derived from it. */
    val p = mutableDoubleStateOf(0.0)
    val wake = mutableIntStateOf(0)
    var now = -1.0
    var loopStart = 0.0
    var mode = if (auto) 0 else 1
    var pVel = 0.0
    var target = 0.0
    var dragging = false
    val touchKicks = DoubleArray(MAX_KICKS * 3)
    var touchCount = 0
    var kicks: DoubleArray = RopeLoopKicks
    var kickCount = RopeLoopKicks.size
    var kickClock = 0.0
    var gain = 1.0
    var rippleStart = -10.0
    var rippleTab = 0.0
    var lastTouch = 0.0
    var backStart = 0.0
    var backFrom = 0.0
    val tug = FloatArray(ROPE_TABS)

    fun addKick(at: Double, u: Double, size: Double) {
        if (touchCount >= MAX_KICKS * 3) {
            System.arraycopy(touchKicks, 3, touchKicks, 0, touchCount - 3)
            touchCount -= 3
        }
        touchKicks[touchCount] = at
        touchKicks[touchCount + 1] = u
        touchKicks[touchCount + 2] = size
        touchCount += 3
    }

    /** Freezes the loop's place and ringing into the touch timeline. */
    fun takeOver(t0: Double) {
        if (mode != 0) return
        val t = (t0 - loopStart).mod(LOOP)
        val shift = t0 - t
        touchCount = 0
        var i = 0
        while (i < RopeLoopKicks.size && touchCount < MAX_KICKS * 3) {
            addKick(RopeLoopKicks[i] + shift, RopeLoopKicks[i + 1], RopeLoopKicks[i + 2])
            i += 3
        }
        pVel = 0.0
        target = p.doubleValue.roundToInt().toDouble()
        mode = 1
    }

    /** True while anything on screen can still move. */
    fun moving(t0: Double, auto: Boolean): Boolean {
        if (mode != 1 || dragging) return true
        if (abs(p.doubleValue - target) > 0.0005 || abs(pVel) > 0.001) return true
        if (t0 - rippleStart < 0.6) return true
        var k = 0
        while (k < touchCount) { if (t0 - touchKicks[k] < 1.9) return true; k += 3 }
        return auto
    }

    fun ropeY(u: Double) = ropeAt(u, ropeTabU(p.doubleValue), kicks, kickCount, kickClock, gain)
}

@Composable
fun RopeTabBar(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val state = remember(auto) { RopeState(auto) }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val insetTop = insets.calculateTopPadding().value
    val insetBottom = insets.calculateBottomPadding().value
    val barH = BAR_CONTENT + insetBottom

    LaunchedEffect(state, auto, reduced) {
        while (true) {
            withFrameNanos { nanos ->
                val t0 = nanos / 1e9
                val dt = if (state.now < 0) 1.0 / 60 else min(t0 - state.now, 1.0 / 30)
                if (state.now < 0) state.loopStart = t0
                state.now = t0
                step(state, t0, dt, auto, reduced)
                state.frame.intValue++
            }
            if (!state.moving(state.now, auto)) {
                // At rest: sleep until a touch wakes the loop.
                val seen = state.wake.intValue
                snapshotFlow { state.wake.intValue }.first { it != seen }
                state.now = -1.0
            }
        }
    }

    val activeTab by remember(state) { derivedStateOf { ropeClampTab(state.p.doubleValue).roundToInt() } }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Background)
            .clipToBounds(),
    ) {
        val w = maxWidth.value
        val h = maxHeight.value
        val barTop = h - barH
        val pagesTop = insetTop + 108f

        // Header: the app's name, the weather, and the four titles cross-faded by the knot's place.
        Column(
            Modifier
                .padding(start = 20.dp, end = 20.dp, top = (insetTop + 10f).dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("BASECAMP", color = Color(0xFFE8743B), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.6.sp)
                Row(
                    Modifier
                        .height(30.dp)
                        .background(Color(0x12F2EBDD), RoundedCornerShape(15.dp))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .drawBehind {
                                drawCircle(Brush.radialGradient(listOf(Color(0xB3F2B766), Color(0x00F2B766)), radius = size.minDimension * 1.3f), radius = size.minDimension * 1.3f)
                                drawCircle(Color(0xFFF2B766))
                            },
                    )
                    Text("14° Clear", color = RopeInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, style = ropeTabular)
                }
            }
            Box(Modifier.fillMaxWidth().height(44.dp)) {
                for (i in TabNames.indices) {
                    Text(
                        TabNames[i],
                        color = RopeInk,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.6).sp,
                        maxLines = 1,
                        modifier = Modifier.graphicsLayer {
                            val d = (state.p.doubleValue - i).toFloat()
                            val a = abs(d)
                            alpha = max(0f, 1f - a * 1.6f)
                            translationX = -d * 26f * density.density
                            val s = 1f - 0.06f * min(1f, a)
                            scaleX = s
                            scaleY = s
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        },
                    )
                }
            }
        }

        // Pages: each translated by its distance from the knot, so they cross-slide.
        Box(
            Modifier
                .offset(y = pagesTop.dp)
                .fillMaxWidth()
                .height(max(0f, barTop - pagesTop).dp)
                .clipToBounds(),
        ) {
            for (i in 0 until ROPE_TABS) {
                Box(
                    Modifier
                        .width(w.dp)
                        .graphicsLayer { translationX = (i - state.p.doubleValue).toFloat() * w * density.density }
                        .padding(top = 2.dp),
                ) {
                    RopeTabBarPage(i, state.p, w)
                }
            }
        }

        // The bar: one canvas for the bar, rope, knot and ripple, the tabs on top, and the touches.
        val bar = remember(w, barH, density.density) { ropeRenderBar(w, barH, density.density).asImageBitmap() }
        val painter = remember { RopePainter() }
        val ys = remember { FloatArray(ROPE_SAMPLES + 1) }
        Box(
            Modifier
                .offset(y = (barTop - ROPE_TOP_PAD).dp)
                .fillMaxWidth()
                .height((barH + ROPE_TOP_PAD).dp)
                .semantics { contentDescription = "Tabs, ${TabNames[activeTab]} selected" }
                .pointerInput(state, w, reduced) {
                    ropeGestures(state, w, reduced, { haptics.performHapticFeedback(it) }) { state.wake.intValue++ }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                state.frame.intValue
                drawImage(bar)
                drawIntoCanvas { canvas ->
                    val c = canvas.nativeCanvas
                    c.save()
                    c.scale(density.density, density.density)
                    val base = (ROPE_TOP_PAD + ROPE_LINE).toDouble()
                    val uk = ropeTabU(state.p.doubleValue)
                    for (k in 0..ROPE_SAMPLES) ys[k] = (base + state.ropeY(k.toDouble() / ROPE_SAMPLES)).toFloat()
                    val ky = base + state.ropeY(uk)
                    val slope = (state.ropeY(uk + 0.01) - state.ropeY(uk - 0.01)) / (0.02 * w)
                    val life = ropeRippleAt(state.kickClock, state.rippleStart)
                    painter.draw(c, w, ys, (uk * w).toFloat(), (ky + 1).toFloat(), slope.toFloat(), life.toFloat(), (ropeTabU(state.rippleTab) * w).toFloat(), ROPE_TOP_PAD + 40f)
                    c.restore()
                }
            }
            val cell = w / ROPE_TABS
            for (i in 0 until ROPE_TABS) {
                Column(
                    Modifier
                        .offset(x = (i * cell).dp, y = (ROPE_TOP_PAD + 26f).dp)
                        .width(cell.dp)
                        .height(52.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(30.dp, 28.dp)
                            .graphicsLayer {
                                state.frame.intValue
                                translationY = state.tug[i] * density.density
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(TabIcons[i], null, tint = Idle, modifier = Modifier.size(24.dp).graphicsLayer { alpha = min(1f, abs(state.p.doubleValue - i).toFloat()) })
                        Icon(TabIcons[i], null, tint = Active, modifier = Modifier.size(24.dp).graphicsLayer { alpha = max(0f, 1f - abs(state.p.doubleValue - i).toFloat()) })
                    }
                    Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(16.dp)) {
                        Text(TabNames[i], color = Idle, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = min(1f, abs(state.p.doubleValue - i).toFloat()) })
                        Text(TabNames[i], color = Active, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = max(0f, 1f - abs(state.p.doubleValue - i).toFloat()) })
                    }
                }
            }
        }
    }
}

/** One frame of the knot, the kicks the rope reads, and the icons' tug. */
private fun step(s: RopeState, t0: Double, dt: Double, auto: Boolean, reduced: Boolean) {
    if (s.mode == 0) {
        val t = (t0 - s.loopStart).mod(LOOP)
        s.p.doubleValue = ropeLoopKnot(t)
        s.kicks = RopeLoopKicks
        s.kickCount = RopeLoopKicks.size
        s.kickClock = t
        s.gain = 1.0
        var rs = -10.0
        var rt = 0.0
        for (m in RopeLoopMoves) if (ropeRippleAt(t, m[0]) >= 0) { rs = m[0]; rt = m[2] }
        s.rippleStart = rs
        s.rippleTab = rt
    } else {
        s.kicks = s.touchKicks
        s.kickCount = s.touchCount
        s.kickClock = t0
        if (s.mode == 1) {
            s.gain = 1.0
            if (!s.dragging) {
                // The knot rides a spring to its tab (ω 7.5, ζ 0.78), carrying the release velocity.
                var x = s.p.doubleValue
                var v = s.pVel
                val h = dt / 3
                repeat(3) {
                    v += (-ROPE_KNOT_W * ROPE_KNOT_W * (x - s.target) - 2 * ROPE_KNOT_Z * ROPE_KNOT_W * v) * h
                    x += v * h
                }
                if (reduced) { x = s.target; v = 0.0 }
                if (abs(x - s.target) < 0.0005 && abs(v) < 0.001) { x = s.target; v = 0.0 }
                s.p.doubleValue = x
                s.pVel = v
                if (auto && t0 - s.lastTouch > RETURN_AFTER && abs(x - s.target) < 0.005) {
                    s.mode = 2
                    s.backStart = t0
                    s.backFrom = x
                }
            }
        } else {
            val k = ropeEaseInOut(ropeSeg(t0 - s.backStart, 0.0, RETURN_FOR))
            s.p.doubleValue = s.backFrom * (1 - k)
            s.gain = 1 - k
            if (k >= 1) {
                s.mode = 0
                s.loopStart = t0
                s.touchCount = 0
            }
        }
    }
    // Icons are tugged by how far the rope hangs below its slack line at their x.
    for (i in 0 until ROPE_TABS) {
        val u = ropeTabU(i.toDouble())
        val y = s.ropeY(u) - ROPE_SAG * sin(Math.PI * u)
        s.tug[i] = (y * 0.45).coerceIn(-3.0, 6.0).toFloat()
    }
}

/** A tap on a tab, or a drag that starts on the knot and scrubs it along the rope. */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.ropeGestures(
    s: RopeState,
    w: Float,
    reduced: Boolean,
    feel: (HapticFeedbackType) -> Unit,
    wake: () -> Unit,
) {
    val d = density
    val cell = w / ROPE_TABS
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val start = down.position / d
        val kx = ropeTabU(s.p.doubleValue) * w
        val onKnot = abs(start.x - kx) <= 40 && start.y <= ROPE_TOP_PAD + 40
        var dragging = false
        var tapOk = true
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        var x = start.x
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            tracker.addPosition(change.uptimeMillis, change.position)
            val pos = change.position / d
            x = pos.x
            if (!change.pressed) break
            if (!dragging) {
                if ((pos - start).getDistance() > 12) tapOk = false
                if (onKnot && abs(pos.x - ropeTabU(s.p.doubleValue) * w) > 4) {
                    dragging = true
                    tapOk = false
                    val t0 = nowSeconds()
                    s.takeOver(t0)
                    s.mode = 1
                    s.dragging = true
                    s.pVel = 0.0
                    wake()
                }
            }
            if (dragging) {
                change.consume()
                if (change.positionChange().x != 0f && cell > 0) {
                    var q = pos.x / cell - 0.5
                    if (q < 0) q = -(-q / (1 - q / 0.6)) * 0.55
                    if (q > ROPE_TABS - 1) {
                        val o = q - (ROPE_TABS - 1)
                        q = ROPE_TABS - 1 + (o / (1 + o / 0.6)) * 0.55
                    }
                    val before = s.p.doubleValue.roundToInt()
                    val vel = tracker.calculateVelocity().x / d / cell
                    s.pVel = vel.toDouble()
                    s.p.doubleValue = q
                    val after = ropeClampTab(q).roundToInt()
                    val t0 = nowSeconds()
                    if (after != before) {
                        s.addKick(t0, ropeTabU(after.toDouble()), min(4.0, abs(vel.toDouble()) * 0.8))
                        feel(HapticFeedbackType.SegmentTick)
                    }
                    s.lastTouch = t0
                }
            }
        }
        val t0 = nowSeconds()
        if (dragging) {
            s.dragging = false
            val q = s.p.doubleValue
            val v = if (reduced) 0.0 else s.pVel.coerceIn(-6.0, 6.0)
            val tab = ropeClampTab(floor(q + v * 0.15 + 0.5))
            s.target = tab
            s.pVel = v
            if (!reduced) s.addKick(t0 + 0.3, ropeTabU(tab), 2 + 3.5 * abs(tab - q).pow(0.6))
            s.lastTouch = t0
            feel(HapticFeedbackType.ContextClick)
            wake()
        } else if (tapOk && cell > 0) {
            s.takeOver(t0)
            s.mode = 1
            val tab = ropeClampTab(floor(x / cell).toDouble())
            val from = s.p.doubleValue
            val dist = abs(tab - from).pow(0.6)
            if (!reduced && abs(tab - from) > 0.05) {
                s.addKick(t0 + 0.05, ropeTabU(from), -3.2 * dist)
                s.addKick(t0 + 0.42, ropeTabU(tab), 6 * dist)
            }
            s.target = tab
            s.rippleStart = t0
            s.rippleTab = tab
            s.lastTouch = t0
            feel(HapticFeedbackType.ContextClick)
            wake()
        }
    }
}
