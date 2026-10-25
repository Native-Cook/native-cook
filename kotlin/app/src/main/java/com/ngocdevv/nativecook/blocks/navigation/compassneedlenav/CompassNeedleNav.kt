package com.ngocdevv.nativecook.blocks.navigation.compassneedlenav

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/*
 Compass Needle Nav — "Northbound", a hiking app whose bottom navigation is a liquid compass.
 A port of CompassNeedleNav.tsx (the reference) and the SwiftUI block; the numbers are the same.

 Look: a night-green ground; the page (Map with a contour map and the day's trail, Trips, Gear
 with a pack list, Me with stats and badges); a dock with a compass: a knurled anodised housing,
 a bezel with degrees every 30° and the cardinals (N in red), a well of tinted damping fluid with
 a faint card, a red-and-cream faceted needle floating in it with a shadow, an air bubble and a
 glass dome. Map, Trips, Gear and Me sit round it at N, E, S and W.

 Tap a tab: the needle swings to it through the liquid (an underdamped spring, ω 7 rad/s,
 ζ 0.4 — it overshoots 25 % and settles in ~1.4 s), the bubble drifts against the spin, and the
 pages, which hang on a wheel centred on the compass one every 90°, swing round so the new page
 comes in from the side its tab sits on (critically damped, ω 5.5 rad/s), fading with the angle.
 Drag round the bezel and the needle and the wheel follow the finger (a bead marks it, a light
 tick at every quarter turn); let go and it settles on the nearest tab. 2 s after the last touch
 everything eases back to N (0.9 s) and the loop (CompassNeedleNavMotion.kt, LOOP = 8 s: E, S,
 then on round to N) starts again. Reduced motion: no loop, no swing; a tab shows its page at once.
*/

private val ACCENT = Color(0xFFF07A3A)
private val INK = Color(0xFFEEF1E8)
private val MUTE = Color(238 / 255f, 241 / 255f, 232 / 255f, 0.55f)

private fun nowSeconds() = System.nanoTime() / 1e9

private class CompassArtSet(val scene: CompassScene, val layers: CompassLayers, val painter: CompassPainter)

private class CompassController(val auto: Boolean, val reduced: Boolean) {
    enum class Mode { Auto, Touch, Back }

    var now by mutableDoubleStateOf(0.0)
    var awake by mutableStateOf(true)
    var mode = Mode.Auto
    var loopStart = -1.0
    var lastTouch = 0.0
    var lastFrame = -1.0
    var dragging = false
    var sim = CompassSim()
    /** Under reduced motion a tab simply shows its page. */
    var still = 0.0
    private var poseAt = Double.NaN
    private var cached: CompassPose? = null

    private fun loopT(at: Double) = (((at - loopStart) % CompassMotion.LOOP) + CompassMotion.LOOP) % CompassMotion.LOOP

    fun pose(at: Double = now): CompassPose {
        val c = cached
        if (c != null && at == poseAt) return c
        val p = when {
            reduced -> CompassPose(still, still, 0.0, Double.NaN, still)
            mode != Mode.Auto -> CompassMotion.simPose(sim, at)
            !auto || loopStart < 0 -> CompassMotion.loopPose(0.0)
            else -> CompassMotion.loopPose(loopT(at))
        }
        cached = p
        poseAt = at
        return p
    }

    /** The needle bobs twice a loop, so the loop still closes. */
    fun bob(): Double {
        if (reduced) return 0.0
        val time = if (mode == Mode.Auto) loopT(now) else now
        return 2 * PI * 2 * time / CompassMotion.LOOP
    }

    fun frame(at: Double) {
        if (loopStart < 0) loopStart = at
        val dt = if (lastFrame < 0) 1.0 / 60 else min(at - lastFrame, 1.0 / 20)
        lastFrame = at
        if (mode == Mode.Touch) {
            val s = sim
            s.step(dt)
            if (!dragging && at - lastTouch > CompassMotion.IDLE_BEFORE_BACK) {
                s.fromNeedle = s.needle
                s.fromWheel = s.wheel
                s.target = 360 * CompassMotion.jsRound(s.wheel / 360)
                s.backAt = at
                mode = Mode.Back
            }
        } else if (mode == Mode.Back && at >= sim.backAt + CompassMotion.BACK_FOR) {
            sim = CompassSim()
            mode = Mode.Auto
            loopStart = at
        }
        cached = null
        now = at
    }

    fun takeOver() {
        if (mode == Mode.Touch) return
        val p = pose()
        val s = CompassSim()
        s.needle = p.needle
        s.wheel = p.wheel
        s.vel = p.spin
        s.target = p.target
        sim = s
        mode = Mode.Touch
        cached = null
    }

    fun select(index: Int, at: Double) {
        lastTouch = at
        if (reduced) {
            still = index * 90.0
            cached = null
            now = at
            return
        }
        takeOver()
        val s = sim
        // The shortest way round to the tab's heading.
        s.target = s.needle + CompassMotion.wrap(index * 90 - s.needle)
        awake = true
    }
}

@Composable
fun CompassNeedleNav(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val view = LocalView.current
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val c = remember(auto, reduced) { CompassController(auto, reduced) }

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFF0F1C18)).clipToBounds()) {
        val density = LocalDensity.current.density
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
        val w = maxWidth.value
        val h = maxHeight.value
        var art by remember { mutableStateOf<CompassArtSet?>(null) }

        // Static art is baked off the main thread; the loop starts on the first frame that has it.
        LaunchedEffect(w, h, top, bottom, density) {
            // The demo hides the status bar and the insets animate for a moment: wait for them to
            // settle (cancellable) so a bake does not restart on every inset frame.
            delay(120)
            art = withContext(Dispatchers.Default) {
                val scene = CompassScene(w, h, top, bottom)
                CompassArtSet(scene, CompassNeedleNavArt.bakeLayers(scene, CompassFonts(), min(2f, density)), CompassPainter())
            }
        }

        val current = art
        LaunchedEffect(current, c, c.awake) {
            if (current == null || !c.awake) return@LaunchedEffect
            while (true) {
                withFrameNanos { n -> c.frame(n / 1e9) }
                // With nothing playing and no finger, the frame stays as it is.
                if (!auto && c.mode == CompassController.Mode.Auto) {
                    c.awake = false
                    break
                }
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Compass navigation. Drag round the bezel to turn, or tap Map, Trips, Gear or Me." }
                .pointerInput(current, c) {
                    val a = current ?: return@pointerInput
                    val sc = a.scene
                    fun angle(x: Float, y: Float) = atan2((x - sc.cx).toDouble(), -(y - sc.cy).toDouble()) * 180 / PI
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (reduced) return@awaitEachGesture
                        val x0 = down.position.x / density
                        val y0 = down.position.y / density
                        val dist = hypot(x0 - sc.cx, y0 - sc.cy)
                        if (dist < sc.r * 0.45f || dist > sc.r + 40) return@awaitEachGesture
                        down.consume()
                        c.takeOver()
                        c.dragging = true
                        c.awake = true
                        c.sim.finger = angle(x0, y0)
                        c.lastTouch = nowSeconds()
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                                ch.consume()
                                val s = c.sim
                                val ang = angle(ch.position.x / density, ch.position.y / density)
                                val before = CompassMotion.jsRound(s.target / 90)
                                s.target += CompassMotion.wrap(ang - s.finger)
                                s.finger = ang
                                if (CompassMotion.jsRound(s.target / 90) != before) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                c.lastTouch = nowSeconds()
                            }
                        } finally {
                            c.dragging = false
                            val s = c.sim
                            s.target = 90 * CompassMotion.jsRound(s.target / 90)
                            s.finger = Double.NaN
                            c.lastTouch = nowSeconds()
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    }
                },
        ) {
            val a = current ?: return@Canvas
            val pose = c.pose()
            val bob = c.bob()
            scale(density, density, Offset.Zero) {
                a.painter.frame(drawContext.canvas.nativeCanvas, a.scene, a.layers, pose, bob)
            }
        }

        Row(
            Modifier.offset(x = 20.dp, y = (top + 8).dp).width((w - 40).dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BasicText("NORTHBOUND", style = TextStyle(color = ACCENT, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp))
            val pill = RoundedCornerShape(14.dp)
            Row(
                Modifier
                    .height(28.dp)
                    .background(Color(238 / 255f, 241 / 255f, 232 / 255f, 0.07f), pill)
                    .border(0.5.dp, Color(238 / 255f, 241 / 255f, 232 / 255f, 0.16f), pill)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Canvas(Modifier.size(14.dp)) {
                    scale(density, density, Offset.Zero) {
                        drawCircle(Color(0xFFF5C26B), 3f, Offset(7f, 7f))
                        drawPath(sunRays, Color(0xFFF5C26B), style = Stroke(1.3f, cap = StrokeCap.Round))
                    }
                }
                BasicText("6° · Clear", style = TextStyle(color = INK, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"))
            }
        }

        val s = current?.scene
        if (s != null) {
            val select = { i: Int ->
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                c.select(i, nowSeconds())
            }
            CompassTab(0, Modifier.offset(x = (s.cx - 50).dp, y = (s.cy - s.r - 44).dp).size(100.dp, 32.dp), c, select)
            CompassTab(1, Modifier.offset(x = (s.cx + s.r + 18).dp, y = (s.cy - 26).dp).size(56.dp, 52.dp), c, select)
            CompassTab(2, Modifier.offset(x = (s.cx - 50).dp, y = (s.cy + s.r + 10).dp).size(100.dp, 32.dp), c, select)
            CompassTab(3, Modifier.offset(x = (s.cx - s.r - 74).dp, y = (s.cy - 26).dp).size(56.dp, 52.dp), c, select)
        }
    }
}

private val sunRays = PathParser().parsePathString(
    "M7 1V2.6M7 11.4V13M1 7H2.6M11.4 7H13M2.8 2.8L3.9 3.9M10.1 10.1L11.2 11.2M2.8 11.2L3.9 10.1M10.1 3.9L11.2 2.8",
).toPath()

private val tabIcons = arrayOf(
    "M2 4.5L6.5 2.5L11.5 4.5L16 2.5V13.5L11.5 15.5L6.5 13.5L2 15.5Z M6.5 2.5V13.5 M11.5 4.5V15.5",
    "M4 14C4 9 14 10 14 5",
    "M5 6.5C5 4 6.8 2.5 9 2.5C11.2 2.5 13 4 13 6.5 M4 7H14V15.5H4Z M7 10.5H11",
    "M3 16C3.8 12.6 6.2 11 9 11C11.8 11 14.2 12.6 15 16",
).map { PathParser().parsePathString(it).toPath() }

private fun DrawScope.compassIcon(index: Int, color: Color) {
    scale(density, density, Offset.Zero) {
        val stroke = Stroke(1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawPath(tabIcons[index], color, style = stroke)
        when (index) {
            1 -> {
                drawCircle(color, 1.8f, Offset(4f, 14.5f))
                drawCircle(color, 1.8f, Offset(14f, 3.5f))
            }
            3 -> drawCircle(color, 3.2f, Offset(9f, 6f), style = stroke)
        }
    }
}

/** A tab round the compass: muted, with an accent copy over it that shows while its page is in. */
@Composable
private fun CompassTab(index: Int, modifier: Modifier, c: CompassController, onSelect: (Int) -> Unit) {
    val select by rememberUpdatedState(onSelect)
    val vertical = index == 1 || index == 3
    @Composable
    fun inner(color: Color, layer: Modifier) {
        val icon = @Composable { Canvas(Modifier.size(18.dp)) { compassIcon(index, color) } }
        val label = @Composable { BasicText(CompassTabs[index], style = TextStyle(color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)) }
        if (vertical) {
            Column(layer, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                icon()
                label()
            }
        } else {
            Row(layer, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                icon()
                label()
            }
        }
    }
    Box(
        modifier
            .semantics {
                role = Role.Tab
                contentDescription = CompassTabs[index]
                onClick { select(index); true }
            }
            .pointerInput(Unit) { detectTapGestures(onTap = { select(index) }) },
        contentAlignment = Alignment.Center,
    ) {
        inner(MUTE, Modifier)
        inner(
            ACCENT,
            Modifier.graphicsLayer {
                alpha = CompassMotion.clamp01(1 - abs(CompassMotion.wrap(c.pose().wheel - index * 90)) / 45).toFloat()
            },
        )
    }
}
