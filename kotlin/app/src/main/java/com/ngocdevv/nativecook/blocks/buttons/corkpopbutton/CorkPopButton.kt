package com.ngocdevv.nativecook.blocks.buttons.corkpopbutton

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 Cork Pop Button — "Maison Vale", a wine shop's order-placed screen whose call to action is a
 bottle of champagne: the "Celebrate" label on its shoulder, gold foil and a wire cage on the neck.
 A port of CorkPopButton.tsx (the reference); the numbers are the same.

 Drag the cork up: it twists against the finger (sideways drag rocks it), the cage's key spins
 loose and the cork inches out (16 dp at most, falling off: 16 (1 - e^(-pull / 70))). Past 105 dp
 (or a fast flick past 55 dp) it pops: cork and cage fly off with a spin, a cold puff of CO2, foam
 gushes up and runs down the neck, gold bubbles rise across the screen, the order card lifts with
 a sheen, and the line under it rolls to "Cheers". Let go before that and the cork settles back.
 2 s after the last touch the open bottle sinks away and a corked one rises in its place, then the
 loop starts again (CorkPopButtonMotion.kt has the timeline).
 Reduced motion: no loop; a tap on the cork rolls the line (a fade) and nothing flies.
*/

private val INK = Color(0xFFF3EBD6)
private val MUTE = Color(243 / 255f, 235 / 255f, 214 / 255f, 0.64f)
private val GOLD = Color(0xFFD9B56A)

/** The swap under a finger: the loop's 1.6 s one, played in this long. */
private const val SWAP_TOUCH = 0.9
private const val POP_PULL = 105.0
private const val ROLL = 24f
private const val FADE_IN = 0.25

private fun nowSeconds() = System.nanoTime() / 1e9

private class CorkSim {
    var inch = 0.0
    var rock = 0.0
    var loose = 0.0
    var strain = 0.0
    var popAt = -1.0
    var backAt = -1.0
    var swapFor = SWAP_TOUCH
    var lastTouch = 0.0
    var grab = false
    var tick = 0
}

private class CorkArtSet(val layout: CorkLayout, val layers: CorkLayers)

private class CorkController(val auto: Boolean, val reduced: Boolean) {
    enum class Mode { Auto, Touch }

    var now by mutableDoubleStateOf(0.0)
    var awake by mutableStateOf(true)
    var mode = Mode.Auto
    var loopStart = -1.0
    var artAt = -1.0
    var lastFrame = -1.0
    var sim = CorkSim()
    var quietOn = false
    var quietAt = -10.0
    private val clocks = CorkClocks()
    private val pose = CorkPose()
    private var poseAt = Double.NaN

    fun loopTime(at: Double) = if (!auto || loopStart < 0) 0.0 else (((at - loopStart) % LOOP) + LOOP) % LOOP

    fun pose(g: CorkGeo, at: Double = now): CorkPose {
        if (at == poseAt) return pose
        poseAt = at
        when {
            reduced -> {
                CorkMotion.makePose(CorkMotion.autoClocks(0.0, clocks), g, pose)
                val k = CorkMotion.clamp01((at - quietAt) / 0.3)
                pose.label = if (quietOn) k else 1 - k
            }
            mode == Mode.Auto -> CorkMotion.makePose(CorkMotion.autoClocks(loopTime(at), clocks), g, pose)
            else -> {
                val s = sim
                clocks.inch = s.inch
                clocks.rock = s.rock
                clocks.loose = s.loose
                clocks.shake = s.strain * 0.8 * sin(2 * PI * 41 * at)
                clocks.finger = 0.0
                clocks.popE = if (s.popAt >= 0) at - s.popAt else -1.0
                clocks.swapE = if (s.backAt >= 0) at - s.backAt else -1.0
                clocks.swapFor = s.swapFor
                CorkMotion.makePose(clocks, g, pose)
            }
        }
        return pose
    }

    fun frame(at: Double) {
        if (loopStart < 0) loopStart = at
        if (artAt < 0) artAt = at
        val dt = if (lastFrame < 0) 1.0 / 60 else min(at - lastFrame, 1.0 / 20)
        lastFrame = at
        if (mode == Mode.Touch) {
            val s = sim
            if (!s.grab && s.popAt < 0) {
                // Let go before the pop: the cork settles back and the cage tightens.
                val k = 1 - exp(-dt * 9)
                s.inch -= s.inch * k
                s.rock -= s.rock * k
                s.loose -= s.loose * k
                s.strain -= s.strain * k
            }
            val idle = !s.grab && at - s.lastTouch > 2
            if (idle && s.popAt < 0) {
                restart(at)
            } else if (idle && s.backAt < 0 && at - s.popAt > 2.2) {
                s.backAt = at
                s.swapFor = SWAP_TOUCH
            } else if (s.backAt >= 0 && at - s.backAt > s.swapFor && at - s.popAt > CORK_BUBBLES_DONE) {
                restart(at)
            }
        }
        now = at
    }

    private fun restart(at: Double) {
        sim = CorkSim()
        mode = Mode.Auto
        loopStart = at
    }

    fun idle(at: Double): Boolean {
        if (at - artAt < FADE_IN) return false
        if (reduced) return at - quietAt > 0.35
        return !auto && mode == Mode.Auto
    }

    /** Hands what the loop shows to the simulation, so a touch never makes anything jump. */
    fun takeOver(at: Double) {
        val s = CorkSim()
        s.lastTouch = at
        if (mode == Mode.Auto && auto) {
            val t = loopTime(at)
            val c = CorkMotion.autoClocks(t, CorkClocks())
            if (t < CORK_POP_AT) {
                s.inch = c.inch
                s.rock = c.rock
                s.loose = c.loose
            } else {
                s.popAt = at - (t - CORK_POP_AT)
                if (t >= CORK_SWAP_AT) {
                    s.backAt = at - (t - CORK_SWAP_AT)
                    s.swapFor = CORK_SWAP_FOR
                }
            }
        }
        sim = s
        mode = Mode.Touch
        poseAt = Double.NaN
    }
}

/**
 * "Maison Vale": an order-placed screen whose call to action is a corked bottle of champagne.
 * Drag the cork up until it pops.
 *
 * @param autoPlay plays the 9 s twist-pop-celebrate loop until touched.
 */
@Composable
fun CorkPopButton(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val view = LocalView.current
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val c = remember(auto, reduced) { CorkController(auto, reduced) }

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFF0C1E18)).clipToBounds()) {
        val screen = LocalDensity.current
        val density = screen.density
        val top = WindowInsets.safeDrawing.getTop(screen) / density
        val w = maxWidth.value
        val h = maxHeight.value
        val g = remember(w, h, top) { CorkLayout(w, h, top) }
        val painter = remember { CorkPainter() }
        var art by remember { mutableStateOf<CorkArtSet?>(null) }

        LaunchedEffect(w, h, top, density) {
            // Wait for the insets to settle (the demo hides the status bar) before baking.
            delay(120)
            val layout = CorkLayout(w, h, top)
            val layers = withContext(Dispatchers.Default) { CorkPopButtonArt.bakeLayers(layout, CorkFonts(), min(2f, density)) }
            // A rebake (the insets settled late) swaps the art in place; only the first one fades in and starts the loop.
            val first = art == null
            art = CorkArtSet(layout, layers)
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
                withFrameNanos { n -> c.frame(n / 1e9) }
                if (c.idle(c.now)) {
                    c.awake = false
                    break
                }
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    role = Role.Button
                    contentDescription = "Celebrate. Drag the champagne cork up until it pops."
                }
                .pointerInput(current, c) {
                    val a = current ?: return@pointerInput
                    val lay = a.layout
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val x0 = down.position.x / density
                        val y0 = down.position.y / density
                        val near = hypot(x0 - lay.cx, y0 - (lay.lipY - lay.corkH * 0.5f)) < 84
                        if (!near) return@awaitEachGesture
                        var at = nowSeconds()
                        c.awake = true
                        if (reduced) {
                            c.quietOn = !c.quietOn
                            c.quietAt = at
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            return@awaitEachGesture
                        }
                        if (c.mode == CorkController.Mode.Touch) c.sim.lastTouch = at else c.takeOver(at)
                        if (c.sim.popAt < 0) {
                            c.sim.grab = true
                            c.sim.tick = 0
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        }
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                                ch.consume()
                                tracker.addPosition(ch.uptimeMillis, ch.position)
                                at = nowSeconds()
                                val s = c.sim
                                s.lastTouch = at
                                if (!s.grab || s.popAt >= 0) continue
                                val tx = (ch.position.x - down.position.x) / density.toDouble()
                                val ty = (ch.position.y - down.position.y) / density.toDouble()
                                val vy = tracker.calculateVelocity().y / density.toDouble()
                                val pull = max(0.0, -ty)
                                s.inch = 16 * (1 - exp(-pull / 70))
                                s.rock = max(-8.0, min(8.0, tx * 0.12))
                                s.loose = min(1.0, pull / 95 + abs(tx) / 300)
                                s.strain = max(0.0, min(1.0, (pull - 60) / 45))
                                val step = floor(pull / 22).toInt()
                                if (step > s.tick) {
                                    s.tick = step
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                } else if (step < s.tick) {
                                    s.tick = step
                                }
                                if (pull >= POP_PULL || (pull > 55 && vy < -1100)) {
                                    s.popAt = at
                                    s.grab = false
                                    s.inch = 0.0
                                    s.rock = 0.0
                                    s.loose = 0.0
                                    s.strain = 0.0
                                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                }
                            }
                        } finally {
                            c.sim.grab = false
                            c.sim.lastTouch = nowSeconds()
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
                val fade = CorkMotion.clamp01((c.now - c.artAt) / FADE_IN)
                if (c.artAt < 0 || fade < 1) {
                    painter.placeholder(canvas, a.layout)
                    val save = canvas.saveLayerAlpha(0f, 0f, a.layout.w, a.layout.h, (255 * (if (c.artAt < 0) 0.0 else fade)).toInt())
                    painter.frame(canvas, a.layout, a.layers, pose)
                    canvas.restoreToCount(save)
                } else {
                    painter.frame(canvas, a.layout, a.layers, pose)
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = (top + 14).dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText("MAISON VALE", style = TextStyle(color = GOLD, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp))
            BasicText("Thank you, Ines", style = TextStyle(color = INK, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp))
            BasicText("Your order is placed and on its way", style = TextStyle(color = MUTE, fontSize = 15.sp, fontWeight = FontWeight.Medium))
        }

        // The hint under the card, rolling to "Cheers".
        Box(
            Modifier
                .offset(y = g.hintY.dp)
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .height(24.dp)
                .clipToBounds(),
        ) {
            val label = { art?.let { c.pose(it.layout.geo).label.toFloat() } ?: 0f }
            val roll = if (reduced) 0f else ROLL
            BasicText(
                "Twist the cork up to celebrate",
                Modifier.fillMaxWidth().offset(y = 2.dp).graphicsLayer {
                    val k = label()
                    alpha = 1 - k
                    translationY = -roll * k * density
                },
                style = HINT,
                maxLines = 1,
            )
            BasicText(
                "Cheers! See you on Thursday",
                Modifier.fillMaxWidth().offset(y = 2.dp).graphicsLayer {
                    val k = label()
                    alpha = k
                    translationY = roll * (1 - k) * density
                },
                style = HINT.copy(color = GOLD, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
        }
    }
}

private val HINT = TextStyle(color = MUTE, fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
