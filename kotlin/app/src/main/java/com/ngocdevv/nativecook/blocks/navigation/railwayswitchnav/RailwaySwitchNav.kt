package com.ngocdevv.nativecook.blocks.navigation.railwayswitchnav

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationEndReason
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/*
 Railway Switch Nav — "Northline", a commuter app whose bottom navigation is a little signal box:
 four painted levers with steel handles (Home, Trips, Tickets, Account) under an enamel track
 diagram, ported from RailwaySwitchNav.tsx. Pulling a lever sets a route: the two switch blades
 throw, the section's signal and lever lamp turn green, the route lights up, a two-car train runs
 to the new section's platform, and the page slides in from the side the train went. The
 machinery is android.graphics drawing in one layer (RailwaySwitchNavArt.kt); the pages are Compose.

 autoPlay (LOOP = 9 s, RailwaySwitchNavMotion.kt; a "trip" is one lever pull):
   0.0 – 1.2   rest at Home.
   1.2 – 3.6   pull Tickets, the switches throw, the train runs Home → Tickets, its page slides in.
   4.4 – 6.4   pull Trips, the train runs back left, the page comes from the left.
   6.8 – 8.4   pull Home, the train returns; rest to 9.0.

 Touch: drag a lever down (it follows the finger, rubber-banding past its stop) or tap it; past
 40 % it commits the trip (a light haptic, another when the train arrives), short of that it
 springs back. 2 s after the last touch the train goes home on a quick trip and the loop starts
 again. Reduced motion: no loop; a lever pull changes the page and lamps at once, no train run.
 */

internal val RailBackground = Color(0xFF131D19)
private const val IDLE_S = 2f
/** The pages' height when the screen has room; on a short screen they get what is left. */
private const val PAGE_H = 320f
private const val HOME_PULL = 0.3f
private const val HOME_RUN = 0.55f

private fun rspring(ms: Int, damping: Float) = (2.0 * PI / (ms / 1000.0)).let { spring<Float>(dampingRatio = damping, stiffness = (it * it).toFloat()) }

private class RailState(auto: Boolean, private val reduced: Boolean) {
    val t = mutableFloatStateOf(0f)
    var manual by mutableStateOf(!auto)
    val frame = mutableIntStateOf(0)
    var clock = 0f
    var resumeAt = -1f
    /** The finger's trip: start (clock s), pull, run, from, to, pullFrom. */
    val trip = floatArrayOf(-100f, 0.6f, 1.0f, 1f, 0f, 0f)
    var drag by mutableIntStateOf(-1)
    val dragAmount = Animatable(0f)
    var wake by mutableIntStateOf(0)
    var arrived = false
    private val pose = RailPose()

    fun startTrip(to: Int, pull: Float, run: Float, pullFrom: Float) {
        val from = trip[4]
        trip[0] = clock
        trip[1] = pull
        trip[2] = run
        trip[3] = from
        trip[4] = to.toFloat()
        trip[5] = pullFrom
        frame.intValue += 1
    }

    fun settled() = clock - trip[0] >= trip[1] + trip[2]

    /** Reads state: call inside draw / graphicsLayer lambdas (or the frame loop). */
    fun pose(): RailPose {
        if (!manual) return railLoopPose(t.floatValue, pose)
        frame.intValue
        val tau = if (reduced) 100f else clock - trip[0]
        val from = trip[3].toInt()
        val to = trip[4].toInt()
        railTripPose(tau, trip[1], trip[2], if (from == to) (if (to == 0) 1 else 0) else from, to, trip[5], pose)
        val d = drag
        if (d >= 0) pose.levers[d] = dragAmount.value
        return pose
    }
}

private fun Density.dpOffset(x: Float, y: Float) = IntOffset(x.dp.roundToPx(), y.dp.roundToPx())

@Composable
fun RailwaySwitchNav(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val state = remember(auto, reduced) { RailState(auto, reduced) }
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density
    val grainA = remember { railGrainTile(1) }
    val grainB = remember { railGrainTile(2) }

    LaunchedEffect(state, auto, state.wake) {
        var last = -1L
        while (true) {
            val keep = withFrameNanos { now ->
                val dt = if (last < 0) 0f else min((now - last) / 1e9f, 0.05f)
                last = now
                state.clock += dt
                if (!state.manual) {
                    state.t.floatValue = (state.t.floatValue + dt) % LOOP
                    return@withFrameNanos auto
                }
                val at = state.resumeAt
                if (at > 0f && state.clock >= at && state.drag < 0) {
                    if (state.trip[4] != 0f) {
                        state.startTrip(0, HOME_PULL, HOME_RUN, 0f)
                        state.resumeAt = state.clock + railTripLength(HOME_PULL, HOME_RUN)
                    } else {
                        state.resumeAt = -1f
                        state.t.floatValue = 0f
                        state.manual = false
                    }
                }
                // A light haptic when a finger's train arrives.
                if (state.manual) {
                    val arrived = state.pose().run > 0.995f
                    if (arrived && !state.arrived && state.resumeAt > 0f && state.trip[4] != 0f) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                    state.arrived = arrived
                }
                state.frame.intValue += 1
                auto || state.drag >= 0 || state.dragAmount.isRunning || state.clock - state.trip[0] < railTripLength(state.trip[1], state.trip[2]) + 0.1f
            }
            if (!keep) break
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(RailBackground)) {
        val width = maxWidth.value
        val height = maxHeight.value
        // The pages take up to PAGE_H; the track diagram (the hero) grows into the rest, 150 – 240 dp.
        val pageTop = safeTop + 64f
        val mapBottom = height - safeBottom - FRAME_H - 12f
        val mapH = max(MAP_MIN, min(240f, mapBottom - pageTop - 16f - PAGE_H))
        val mapTop = mapBottom - mapH
        val pageH = mapTop - 16f - pageTop
        val g = remember(width, height, mapTop, mapH) { RailGeo(width, height, mapTop, mapH) }
        val d = density.density
        val still = remember(g, d) { railRecordStatic(g, d, grainA, grainB) }
        val painter = remember(g, d) { RailPainter(g, d) }

        fun leverAt(x: Float): Int {
            var best = 0
            for (k in 1 until COUNT) if (abs(x - g.xs[k]) < abs(x - g.xs[best])) best = k
            return best
        }

        Spacer(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0f to Color(0xFF18251F), 0.5f to Color(0xFF131D19), 1f to Color(0xFF0E1512))),
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(RailGold)) {
                    Box(Modifier.offset(4.dp, 13.dp).width(20.dp).height(3.dp).background(Color(0xFF1A2420), RoundedCornerShape(1.5.dp)))
                    Box(Modifier.offset(4.dp, 9.dp).width(20.dp).height(3.dp).rotate(-35f).background(Color(0xFF1A2420), RoundedCornerShape(1.5.dp)))
                }
                BasicText("Northline", style = TextStyle(color = RailInk, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp))
            }
            Row(
                Modifier
                    .height(28.dp)
                    .background(Color(236, 244, 236).copy(alpha = 0.06f), RoundedCornerShape(14.dp))
                    .border(Dp.Hairline, Color(236, 244, 236).copy(alpha = 0.16f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(7.dp).background(Color(0xFF7BE39A), CircleShape))
                BasicText("Live · Elm Cross", style = TextStyle(color = RailMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
            }
        }

        // The pages, clipped to their area; each slides in its own cached layer.
        Box(
            Modifier
                .offset { dpOffset(24f, pageTop) }
                .width((width - 48f).dp)
                .height(max(160f, pageH).dp)
                .clipToBounds(),
        ) {
            val span = width - 48f + 24f
            for (k in 0 until COUNT) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                            val p = state.pose()
                            val dir = if (p.to > p.from) 1f else -1f
                            var a = 0f
                            var x = 0f
                            if (p.from == p.to) {
                                a = if (k == p.to) 1f else 0f
                            } else if (k == p.to) {
                                a = 1f
                                x = dir * span * (1f - p.page)
                            } else if (k == p.from) {
                                a = if (p.page < 1f) 1f else 0f
                                x = -dir * span * p.page
                            }
                            alpha = a
                            translationX = x.dp.toPx()
                        },
                ) {
                    RailPage(k, pageH)
                }
            }
        }

        // The diagram and the lever frame: a recorded picture, then the moving layer.
        Spacer(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    onDrawBehind { scale(d, d, pivot = Offset.Zero) { drawIntoCanvas { it.nativeCanvas.drawPicture(still) } } }
                },
        )
        Spacer(
            Modifier
                .fillMaxSize()
                .graphicsLayer()
                .drawBehind {
                    val p = state.pose()
                    scale(d, d, pivot = Offset.Zero) { drawIntoCanvas { painter.draw(it.nativeCanvas, p) } }
                },
        )

        // Labels over the art.
        BasicText(
            "BOX 4 · ELM CROSS JUNCTION",
            Modifier.offset { dpOffset(g.px + 24f, g.py + g.ph - 22f) },
            style = TextStyle(color = Color(232, 224, 200).copy(alpha = 0.45f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp),
        )
        for (k in 0 until COUNT) {
            BasicText(
                RailSections[k].uppercase(),
                Modifier.offset { dpOffset(g.xs[k] - 82f, g.top + g.plat + 6f) }.width(56.dp),
                style = TextStyle(color = Color(232, 224, 200).copy(alpha = 0.6f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp, textAlign = TextAlign.End),
                maxLines = 1,
            )
        }
        for (k in 0 until COUNT) {
            val plate = TextStyle(color = Color(60, 40, 10).copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
            Box(Modifier.offset { dpOffset(g.xs[k] - 36f, g.pivot + 12f) }.width(72.dp).height(24.dp), contentAlignment = Alignment.Center) {
                BasicText(RailSections[k].uppercase(), style = plate, maxLines = 1)
                BasicText(
                    RailSections[k].uppercase(),
                    Modifier.graphicsLayer { alpha = railClamp01(state.pose().signals[k]) },
                    style = plate.copy(color = Color(0xFF2B1C05)),
                    maxLines = 1,
                )
            }
        }

        // The levers take the finger.
        Box(
            Modifier
                .offset { dpOffset(0f, g.frame) }
                .fillMaxWidth()
                .height(max(0f, height - g.frame).dp)
                .semantics { contentDescription = "Sections: Home, Trips, Tickets, Account. Pull a lever to go there." }
                .pointerInput(g, auto, reduced) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val sx = down.position.x / d
                        val sy = down.position.y / d
                        val startNs = System.nanoTime()
                        var ex = sx
                        var ey = sy
                        // Take over from the loop at its current trip.
                        state.resumeAt = -1f
                        if (!state.manual) {
                            val (ti, tau) = railLoopTrip(state.t.floatValue)
                            val tr = RailTrips[ti]
                            state.trip[0] = state.clock - tau
                            state.trip[1] = tr.pull
                            state.trip[2] = tr.run
                            state.trip[3] = tr.from.toFloat()
                            state.trip[4] = tr.to.toFloat()
                            state.trip[5] = 0f
                            state.manual = true
                        }
                        state.wake += 1
                        val k = leverAt(sx)
                        var crossed = false
                        var amount = 0f
                        if (k != state.trip[4].toInt() && state.settled()) {
                            scope.launch { state.dragAmount.snapTo(0f) }
                            state.drag = k
                        } else {
                            state.drag = -1
                        }
                        try {
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                ex = ch.position.x / d
                                ey = ch.position.y / d
                                if (!ch.pressed) break
                                if (state.drag >= 0) {
                                    val raw = (ey - sy) / 70f
                                    val over = max(0f, raw - 1f)
                                    amount = max(-0.06f, min(raw, 1f) + 0.5f * over / (1f + over))
                                    val a = amount
                                    scope.launch { state.dragAmount.snapTo(a) }
                                    if (amount > 0.4f && !crossed) {
                                        crossed = true
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    }
                                }
                                ch.consume()
                            }
                        } finally {
                            val dk = state.drag
                            if (dk >= 0) {
                                val tap = hypot(ex - sx, ey - sy) < 10f && (System.nanoTime() - startNs) / 1e9f < 0.35f
                                if (amount > 0.4f || tap) {
                                    state.startTrip(dk, TOUCH_PULL, TOUCH_RUN, if (tap) 0f else min(1f, amount))
                                    state.drag = -1
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                } else {
                                    scope.launch {
                                        val r = state.dragAmount.animateTo(0f, rspring(380, 0.7f))
                                        if (r.endReason == AnimationEndReason.Finished) state.drag = -1
                                    }
                                }
                            }
                            if (auto) state.resumeAt = state.clock + IDLE_S + 0.01f
                            state.wake += 1
                        }
                    }
                },
        )
    }
}
