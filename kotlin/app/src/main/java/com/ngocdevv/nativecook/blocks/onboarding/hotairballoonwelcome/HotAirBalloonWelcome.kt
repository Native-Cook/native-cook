package com.ngocdevv.nativecook.blocks.onboarding.hotairballoonwelcome

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationEndReason
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 Hot Air Balloon Welcome — the onboarding of "Drift", a travel planner, ported from
 HotAirBalloonWelcome.tsx. A striped hot-air balloon stands on a meadow at dawn over layered hills;
 each onboarding step is an altitude. Hold the burner button: the flame roars, the envelope swells
 a little and the balloon climbs, the hills slide away with parallax, clouds come down past it, and
 the step text rolls. The sky, four hill layers and three cloud sprites are baked once off the main
 thread at up to 2× (HotAirBalloonWelcomeScenery.kt; drawn as vectors until the bake lands); the
 balloon is drawn every frame (HotAirBalloonWelcomeArt.kt). The text panel is Compose.

 autoPlay (LOOP = 10 s, HotAirBalloonWelcomeMotion.kt):
   0.0 – 1.6   rest on the ground, step 1.
   1.6 – 4.0   burn and rise to step 2; the text rolls at 2.4.
   4.6 – 7.0   burn and rise to step 3; the text rolls at 5.2.
   7.0 – 8.8   a smooth descent to the ground (a short burn at 8.0); the text rolls back. Rest to 10.

 Touch: hold the burner button to heat and climb 0.6 steps a second (rubber-banding past step 3);
 let go and the balloon settles on the nearest step (spring 700 ms, damping 0.85). Drag the sky to
 change altitude directly; a flick carries its velocity into the same spring. A tick as each step
 is reached. 2 s after the last touch the balloon descends to the ground (900 ms) and the loop
 restarts. Reduced motion: the ground frame, no loop and no drifting clouds; the button and drag
 change the step with short fades.
 */

private val Ink = Color(0xFFFBF1EA)
private val Muted = Color(0xFFFBF1EA).copy(alpha = 0.64f)
private val Coral = Color(0xFFE5664F)
internal val BalloonBackground = Color(0xFF1A1430)
private const val PANEL_H = 256f
private const val IDLE_MS = 2000L
private const val RETURN_MS = 900
private val EaseInOut = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)

private val Titles = listOf("Plan trips that float", "Rise above the noise", "See the whole route")
private val Bodies = listOf(
    "Drift gathers flights, stays and day plans into one calm itinerary.",
    "We watch prices and delays, and only nudge you when it matters.",
    "Every leg on one offline map, from takeoff to landing.",
)

private fun bspring(ms: Int, damping: Float) = (2.0 * PI / (ms / 1000.0)).let { spring<Float>(dampingRatio = damping, stiffness = (it * it).toFloat()) }

private class BalloonState(auto: Boolean) {
    val t = mutableFloatStateOf(0f)
    var manual by mutableStateOf(!auto)
    val frame = mutableIntStateOf(0)
    val alt = Animatable(0f)
    val heat = Animatable(0f)
    val turn = Animatable(0f)
    var holding = false
    var mtime = 0f
    var wake by mutableIntStateOf(0)
    var lastStep = -1
    var resume: kotlinx.coroutines.Job? = null
    private val pose = BalloonPose()

    /** Reads state: call inside draw / graphicsLayer lambdas. */
    fun pose(): BalloonPose {
        if (!manual) return balloonLoopPose(t.floatValue, pose)
        frame.intValue
        val a = alt.value
        pose.alt = a
        pose.heat = heat.value
        pose.step = max(0f, min(STEPS - 1f, a))
        pose.turn = turn.value
        pose.time = mtime
        return pose
    }
}

private fun Density.dpOffset(x: Float, y: Float) = IntOffset(x.dp.roundToPx(), y.dp.roundToPx())

private val FlamePath = PathParser().parsePathString(
    "M9 1C10.5 5 15.5 8 15.5 13.5C15.5 17.6 12.6 21 9 21C5.4 21 2.5 17.6 2.5 13.5C2.5 10.6 4.2 8.6 5.6 7.4C5.8 9.6 6.8 11 8 11.6C7.6 7.6 8.2 4.2 9 1Z",
).toPath()

@Composable
fun HotAirBalloonWelcome(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val density = LocalDensity.current
    val d = density.density
    val scope = rememberCoroutineScope()
    val state = remember(auto) { BalloonState(auto) }
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / d
    val safeBottom = insets.getBottom(density) / d

    BoxWithConstraints(modifier.fillMaxSize().background(BalloonBackground)) {
        val width = maxWidth.value
        val height = maxHeight.value
        val panelTop = height - safeBottom - PANEL_H
        val g = remember(width, height, panelTop, safeTop) { BalloonGeo(width, height, panelTop - 8f, safeTop + 70f) }
        val painter = remember(g, d) { BalloonPainter(g, d) }

        // The bake depends only on the width, height and ground; it waits for the size to settle
        // (insets animate while the status bar hides) and keeps the last art on screen meanwhile.
        var art by remember { mutableStateOf<BalloonArt?>(null) }
        var artKey by remember { mutableStateOf("") }
        val key = "$width/$height/${panelTop - 8f}"
        LaunchedEffect(key) {
            if (key == artKey && art != null) return@LaunchedEffect
            delay(120)
            val baked = withContext(Dispatchers.Default) { bakeBalloonArt(BalloonGeo(width, height, panelTop - 8f, safeTop + 70f), d) }
            art = baked
            artKey = key
        }
        val shownArt = art

        fun settle(velocity: Float) {
            val a = state.alt.value
            val target = max(0, min(STEPS - 1, (a + velocity * 0.2f).roundToInt())).toFloat()
            scope.launch {
                if (reduced) state.alt.animateTo(target, tween(200)) else state.alt.animateTo(target, bspring(700, 0.85f), initialVelocity = velocity)
            }
        }

        fun backToRest() {
            val k = (state.turn.value / PatternAngle).roundToInt()
            scope.launch { state.turn.animateTo(k * PatternAngle, tween(RETURN_MS, easing = EaseInOut)) }
            scope.launch { state.heat.animateTo(0f, tween(300)) }
            scope.launch {
                val r = state.alt.animateTo(0f, tween(RETURN_MS, easing = EaseInOut))
                if (r.endReason == AnimationEndReason.Finished && auto) {
                    state.t.floatValue = 0f
                    state.turn.snapTo(0f)
                    state.manual = false
                }
            }
        }

        fun scheduleResume() {
            if (!auto) return
            state.resume?.cancel()
            state.resume = scope.launch {
                delay(IDLE_MS)
                while (state.holding) delay(50)
                state.resume = null
                backToRest()
            }
        }

        fun takeOver() {
            state.resume?.cancel()
            state.resume = null
            if (!state.manual) {
                val p = balloonLoopPose(state.t.floatValue, BalloonPose())
                val alt = p.alt
                val heat = p.heat
                val turn = p.turn
                state.mtime = state.t.floatValue
                state.lastStep = alt.roundToInt()
                scope.launch { state.alt.snapTo(alt) }
                scope.launch {
                    state.heat.snapTo(heat)
                    state.heat.animateTo(0f, tween(350))
                }
                scope.launch { state.turn.snapTo(turn) }
                state.manual = true
            } else {
                scope.launch { state.alt.stop() }
                scope.launch { state.turn.stop() }
            }
            state.wake += 1
        }

        LaunchedEffect(state, auto, reduced, state.wake) {
            var last = -1L
            while (true) {
                var dt = 0f
                val keep = withFrameNanos { now ->
                    dt = if (last < 0) 0f else min((now - last) / 1e9f, 0.05f)
                    last = now
                    if (!state.manual) {
                        state.t.floatValue = (state.t.floatValue + dt) % LOOP
                        return@withFrameNanos auto
                    }
                    // Under reduced motion the clouds and the sway hold still.
                    if (!reduced) state.mtime = (state.mtime + dt) % (LOOP * 6f)
                    // A tick as a finger brings the balloon to each step.
                    val s = state.alt.value.roundToInt()
                    if (state.lastStep >= 0 && s != state.lastStep) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    state.lastStep = s
                    state.frame.intValue += 1
                    !reduced || state.holding || state.alt.isRunning || state.heat.isRunning
                }
                if (state.manual && state.holding) {
                    val h = min(1f, state.heat.value + dt * 6.5f)
                    state.heat.snapTo(h)
                    if (!reduced) {
                        val a = state.alt.value
                        val over = max(0f, a - (STEPS - 1))
                        state.alt.snapTo(a + dt * 0.6f * h / (1f + over * 6f))
                    }
                }
                if (!keep) break
            }
        }

        // The scene: one layer that redraws per frame; the sky takes the drag.
        Spacer(
            Modifier
                .fillMaxSize()
                .graphicsLayer()
                .drawBehind {
                    val p = state.pose()
                    val a = shownArt
                    scale(d, d, pivot = Offset.Zero) { drawIntoCanvas { painter.draw(it.nativeCanvas, a, p) } }
                }
                .semantics { contentDescription = "A hot-air balloon at dawn. Drag up or down to change altitude." }
                .pointerInput(g, reduced, auto) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        takeOver()
                        val from = state.alt.value
                        val sy = down.position.y / d
                        var active = false
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        try {
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                                tracker.addPosition(ch.uptimeMillis, ch.position)
                                val dy = ch.position.y / d - sy
                                if (!active && abs(dy) > 8f) active = true
                                if (active) {
                                    var a = from - dy / g.rise
                                    if (a < 0f) a = -(-a / (1f - a)) * 0.35f
                                    if (a > STEPS - 1) {
                                        val over = a - (STEPS - 1)
                                        a = STEPS - 1 + over / (1f + over) * 0.35f
                                    }
                                    val v = if (reduced) max(0, min(STEPS - 1, a.roundToInt())).toFloat() else a
                                    scope.launch { state.alt.snapTo(v) }
                                    ch.consume()
                                }
                            }
                        } finally {
                            if (active) settle(-tracker.calculateVelocity().y / d / g.rise)
                            state.wake += 1
                            scheduleResume()
                        }
                    }
                },
        )

        // Header.
        Row(
            Modifier
                .offset { dpOffset(24f, safeTop + 12f) }
                .width((width - 48f).dp)
                .height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BasicText("drift", style = TextStyle(color = Ink, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp))
            BasicText(
                "Skip",
                Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp).semantics { role = Role.Button },
                style = TextStyle(color = Muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            )
        }

        // The text panel. It takes its own touches so the sky's drag does not start under it.
        Box(
            Modifier
                .offset { dpOffset(0f, panelTop) }
                .fillMaxWidth()
                .height((height - panelTop).dp)
                .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false) } },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .cssBoxShadow(Color.Black.copy(alpha = 0.2f), offsetY = (-1).dp, blur = 2.dp, radius = 30.dp)
                    .cssBoxShadow(Color(10, 6, 30).copy(alpha = 0.35f), offsetY = (-16).dp, blur = 40.dp, radius = 30.dp)
                    .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .background(Brush.verticalGradient(0f to Color(40, 30, 66).copy(alpha = 0.96f), 0.4f to Color(30, 22, 52).copy(alpha = 0.98f), 1f to Color(0xFF1A1430))),
            )
            Box(Modifier.padding(horizontal = 30.dp).fillMaxWidth().height(Dp.Hairline).background(Color(255, 220, 200).copy(alpha = 0.3f)))
            Column(
                Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = (safeBottom + 16f).dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.padding(bottom = 2.dp).height(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(3) { Box(Modifier.size(8.dp).background(Color(0xFFFBF1EA).copy(alpha = 0.22f), CircleShape)) }
                    }
                    Box(
                        Modifier
                            .size(8.dp)
                            .graphicsLayer { translationX = state.pose().step * 16.dp.toPx() }
                            .background(Coral, CircleShape),
                    )
                }
                BalloonRollStack(state, Titles, 36.dp, TextStyle(color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp), lines = 1)
                BalloonRollStack(state, Bodies, 44.dp, TextStyle(color = Muted, fontSize = 15.sp, lineHeight = 21.sp), lines = 2)
                BalloonBurner(
                    state,
                    Modifier
                        .padding(top = 8.dp)
                        .pointerInput(reduced, auto) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false).consume()
                                takeOver()
                                state.holding = true
                                if (reduced) {
                                    val next = min(STEPS - 1, state.alt.value.roundToInt() + 1).toFloat()
                                    scope.launch { state.alt.animateTo(next, tween(200)) }
                                }
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                try {
                                    while (true) {
                                        val ev = awaitPointerEvent()
                                        if (ev.changes.none { it.pressed }) break
                                        ev.changes.forEach { it.consume() }
                                    }
                                } finally {
                                    state.holding = false
                                    scope.launch { state.heat.animateTo(0f, tween(350)) }
                                    if (!reduced) settle(0.6f)
                                    state.wake += 1
                                    scheduleResume()
                                }
                            }
                        },
                )
                BasicText(
                    "Already planning with Drift? Sign in",
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    style = TextStyle(color = Color(0xFFFBF1EA).copy(alpha = 0.45f), fontSize = 13.sp, textAlign = TextAlign.Center),
                )
            }
        }
    }
}

/** The burner button: a coral pill whose glow follows the heat, with its flame mark. */
@Composable
private fun BalloonBurner(state: BalloonState, modifier: Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .cssBoxShadow(Color.Black.copy(alpha = 0.35f), offsetY = 1.dp, blur = 2.dp, radius = 28.dp)
            .cssBoxShadow(Color(229, 102, 79).copy(alpha = 0.35f), offsetY = 12.dp, blur = 28.dp, radius = 28.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFFC9533F))
            .semantics {
                role = Role.Button
                contentDescription = "Hold to rise"
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = state.pose().heat.coerceIn(0f, 1f) }.background(Color(0xFFFF9A55), RoundedCornerShape(28.dp)))
        // inset 0 1 0: a one-dp light band along the inside of the top edge.
        Spacer(
            Modifier.fillMaxSize().drawBehind {
                val r = 28.dp.toPx()
                val outer = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))) }
                val inner = Path().apply { addRoundRect(RoundRect(0f, 1.dp.toPx(), size.width, size.height + 1.dp.toPx(), CornerRadius(r))) }
                clipPath(outer) { drawPath(Path.combine(PathOperation.Difference, outer, inner), Color(255, 230, 210).copy(alpha = 0.35f)) }
            },
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Canvas(Modifier.width(18.dp).height(22.dp)) {
                val s = size.width / 18f
                scale(s, s, pivot = Offset.Zero) {
                    drawPath(FlamePath, Brush.verticalGradient(0f to Color(0xFFFF7A3D), 0.5f to Color(0xFFFFB255), 1f to Color(0xFFFFE7B0), startY = 0f, endY = 22f))
                }
            }
            BasicText("Hold to rise", style = TextStyle(color = Color(0xFFFFF6EE), fontSize = 17.sp, fontWeight = FontWeight.Bold))
        }
    }
}

/** One text of a list, chosen by the continuous step: each rolls inside a clipped box. */
@Composable
private fun BalloonRollStack(state: BalloonState, items: List<String>, height: Dp, style: TextStyle, lines: Int) {
    Box(Modifier.fillMaxWidth().height(height).clipToBounds()) {
        items.forEachIndexed { i, text ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(height)
                    .graphicsLayer {
                        val dd = i - state.pose().step
                        alpha = max(0f, 1f - abs(dd) * 1.15f)
                        translationY = dd * height.toPx()
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicText(text, style = style, maxLines = lines)
            }
        }
    }
}
