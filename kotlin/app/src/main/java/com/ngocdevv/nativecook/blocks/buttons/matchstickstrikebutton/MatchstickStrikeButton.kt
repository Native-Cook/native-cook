package com.ngocdevv.nativecook.blocks.buttons.matchstickstrikebutton

import android.view.HapticFeedbackConstants
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 Matchstick Strike Button — "Hearth", a candle shop's evening ritual whose call to action is lit
 with a real match. A port of MatchstickStrikeButton.tsx (the reference) and the SwiftUI block;
 the numbers are the same.

 Look: a dark walnut table, a product card with an amber candle jar ("Fig & Cedar"), and a big
 oxblood matchbox with a sandpaper striker along its top edge, a printed cream border and the CTA
 "Strike to start" debossed in it. A long match lies above the box.

 Swipe the match head across the striker: fast enough (650 dp/s over 56 dp) and it catches —
 sparks are thrown back against the stroke and fall, the head flares into a fireball, a sulphur
 puff rises and a teardrop flame settles (an AGSL shader that leans against the motion and
 flickers; gradients below API 33). Too slow, and only a dark scrape mark is left on the strip.
 Carry the flame under the CTA and its letters catch one by one (a white flash, then a gold glow);
 let go and the rest catch in a 60 ms chain. 2 s after the last touch the flame is blown out, the
 letters cool and a fresh match fades in at rest (0.9 s); then the loop starts again from t = 0
 (MatchstickStrikeButtonMotion.kt). Reduced motion: no loop and no sparks; the resting screen, and
 a strike still lights the CTA.
*/

private val INK = Color(0xFFF5E6D3)
private val MUTE = Color(245 / 255f, 230 / 255f, 211 / 255f, 0.64f)
private val FAINT = Color(245 / 255f, 230 / 255f, 211 / 255f, 0.42f)
private val EMBER = Color(0xFFF0A35C)

private fun nowSeconds() = System.nanoTime() / 1e9

private class MatchArtSet(val scene: MatchScene, val layers: MatchLayers, val painter: MatchPainter, val ignite: DoubleArray)

private class MatchController(val auto: Boolean, val reduced: Boolean) {
    enum class Mode { Auto, Touch, Back }

    var now by mutableDoubleStateOf(0.0)
    var awake by mutableStateOf(true)
    var mode = Mode.Auto
    var loopStart = -1.0
    var lastTouch = 0.0
    var lastFrame = -1.0
    var finger = false
    var sim = MatchSim(MATCH_CTA.length)
    var grabX = 0.0
    var grabY = 0.0
    private var poseAt = Double.NaN
    private var cached: MatchPose? = null

    fun pose(art: MatchArtSet, at: Double = now): MatchPose {
        val c = cached
        if (c != null && at == poseAt) return c
        val g = art.scene.geo
        val p = when {
            mode != Mode.Auto -> MatchstickMotion.simPose(sim, at, g)
            !auto || loopStart < 0 -> MatchstickMotion.loopPose(0.0, g, art.ignite)
            else -> MatchstickMotion.loopPose((((at - loopStart) % MatchstickMotion.LOOP) + MatchstickMotion.LOOP) % MatchstickMotion.LOOP, g, art.ignite)
        }
        cached = p
        poseAt = at
        return p
    }

    /** One frame: the held match's physics and the mode. */
    fun frame(art: MatchArtSet, at: Double) {
        if (loopStart < 0) loopStart = at
        val dt = if (lastFrame < 0) 1.0 / 60 else min(at - lastFrame, 1.0 / 20)
        lastFrame = at
        if (mode != Mode.Auto) {
            val s = sim
            s.step(at, dt, finger)
            if (mode == Mode.Touch && !finger && at - lastTouch > MatchstickMotion.IDLE_BEFORE_BACK) {
                mode = Mode.Back
                s.backAt = at
                s.back = MatchState().apply { copy(MatchstickMotion.simPose(s, at, art.scene.geo).match) }
                if (s.litAt >= 0 && s.outAt < 0) s.outAt = at
                s.cool = at
            } else if (mode == Mode.Back && at >= s.backAt + MatchstickMotion.BACK_FOR) {
                sim = MatchSim(MATCH_CTA.length)
                mode = Mode.Auto
                loopStart = at
            }
        }
        now = at
    }

    /** Hands whatever the loop shows to the simulation, so a finger never makes anything jump. */
    fun takeOver(art: MatchArtSet, at: Double) {
        val geo = art.scene.geo
        val p = pose(art, now)
        val s = MatchSim(MATCH_CTA.length)
        if (mode == Mode.Auto && p.match.front > 0.5 && p.match.alpha > 0.99) {
            s.x = p.match.x
            s.y = p.match.y
            s.angle = p.match.angle
            s.lift = p.match.lift
            s.burnt = p.match.burnt
            if (p.lit > 0.05) {
                s.litAt = at - 1
                s.px = p.match.x
                s.py = p.match.y
            } else if (p.match.burnt > 0.5) {
                // A spent match: swap it for a fresh one at rest.
                s.x = geo.restX
                s.y = geo.restY
                s.angle = 0.0
                s.lift = 0.0
                s.burnt = 0.0
            }
            for (i in p.heat.indices) if (p.heat[i] > 0.02) s.ignite[i] = at - 0.5
            if (p.lit < 0.05 && p.heat.any { it > 0.02 }) s.cool = at
        } else if (mode == Mode.Touch) {
            return
        } else {
            s.x = geo.restX
            s.y = geo.restY
        }
        sim = s
        mode = Mode.Touch
        cached = null
    }
}

@Composable
fun MatchstickStrikeButton(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val view = LocalView.current
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val c = remember(auto, reduced) { MatchController(auto, reduced) }

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFF150E0A)).clipToBounds()) {
        val density = LocalDensity.current.density
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
        val w = maxWidth.value
        val h = maxHeight.value
        var art by remember { mutableStateOf<MatchArtSet?>(null) }

        // Static art is baked off the main thread; the loop starts on the first frame that has it.
        LaunchedEffect(w, h, top, bottom, density) {
            // The demo hides the status bar and the insets animate for a moment: wait for them to
            // settle (cancellable) so a bake never restarts, and piles up, on every inset frame.
            delay(120)
            art = withContext(Dispatchers.Default) {
                val fonts = MatchFonts()
                val scene = MatchScene(w, h, top, bottom, fonts)
                val layers = MatchstickStrikeButtonArt.bakeLayers(scene, fonts, min(2f, density))
                MatchArtSet(scene, layers, MatchPainter(fonts), MatchstickMotion.igniteTimes(scene.geo))
            }
        }

        val current = art
        LaunchedEffect(current, c, c.awake) {
            if (current == null || !c.awake) return@LaunchedEffect
            while (true) {
                withFrameNanos { n -> c.frame(current, n / 1e9) }
                // With nothing playing and no finger, the frame stays as it is.
                if (!auto && c.mode == MatchController.Mode.Auto) {
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
                    contentDescription = "Strike to start. Swipe the match quickly across the strip on the matchbox to light it."
                }
                .pointerInput(current, c) {
                    val a = current ?: return@pointerInput
                    val geo = a.scene.geo
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var at = nowSeconds()
                        c.takeOver(a, at)
                        c.grabX = c.sim.x - down.position.x / density
                        c.grabY = c.sim.y - down.position.y / density
                        c.finger = true
                        c.lastTouch = at
                        c.awake = true
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
                                val vxNow = tracker.calculateVelocity().x / density.toDouble()
                                val x = max(8.0, min(a.scene.w - 8.0, ch.position.x / density + c.grabX))
                                val y = max(top + 40.0, min(a.scene.h - 20.0, ch.position.y / density + c.grabY))
                                val dx = x - s.x
                                s.x = x
                                s.y = y
                                s.vx = 0.6 * s.vx + 0.4 * vxNow
                                c.lastTouch = at
                                val lit = s.litAt >= 0 && s.outAt < 0
                                val onStrip = y > geo.strikerTop - 10 && y < geo.strikerBottom + 10 && x > geo.strikerLeft && x < geo.strikerRight
                                if (!lit && onStrip && s.lift > 0.3) {
                                    if (abs(vxNow) > MatchstickMotion.STRIKE_SPEED) {
                                        s.run += abs(dx)
                                        val dir = if (dx == 0.0) 1.0 else kotlin.math.sign(dx)
                                        if (!reduced) {
                                            s.addSpark(x - 4, y, dir, at)
                                            if (abs(dx) > 6) s.addSpark(x, y + 1, dir, at)
                                        }
                                        if (s.run >= MatchstickMotion.STRIKE_RUN) {
                                            s.litAt = at
                                            s.outAt = -1.0
                                            s.px = x
                                            s.py = y
                                            s.cool = -1.0
                                            for (i in s.ignite.indices) s.ignite[i] = -1.0
                                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                        }
                                    } else if (abs(dx) > 0.5) {
                                        // Too slow: the head only scrapes the grit.
                                        if (at - s.scrape[2] > 0.4) {
                                            s.scrape[0] = x
                                            s.scrape[1] = x
                                            s.scrape[2] = at
                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        } else {
                                            s.scrape[1] = x
                                            s.scrape[2] = at
                                        }
                                        s.run = 0.0
                                    }
                                } else if (!onStrip) {
                                    s.run = 0.0
                                }
                                if (lit) {
                                    for (i in s.ignite.indices) {
                                        if (s.ignite[i] < 0 && abs(x - geo.letters[i]) < 14 && y > geo.baseline - 46 && y < geo.baseline + 44) s.ignite[i] = at
                                    }
                                }
                            }
                        } finally {
                            at = nowSeconds()
                            val s = c.sim
                            c.finger = false
                            c.lastTouch = at
                            if (s.litAt >= 0 && s.outAt < 0) {
                                // Whatever the flame did not reach catches in a chain from left to right.
                                var k = 0
                                for (i in s.ignite.indices) {
                                    if (s.ignite[i] < 0) {
                                        s.ignite[i] = at + 0.15 + 0.06 * k
                                        k += 1
                                    }
                                }
                            }
                        }
                    }
                },
        ) {
            val a = current ?: return@Canvas
            val pose = c.pose(a)
            scale(density, density, Offset.Zero) {
                a.painter.frame(drawContext.canvas.nativeCanvas, a.scene, a.layers, pose)
            }
        }

        Column(Modifier.offset(x = 20.dp, y = (top + 12).dp).width((w - 40).dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText("HEARTH", style = TextStyle(color = EMBER, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp))
            BasicText("Light the evening", style = TextStyle(color = INK, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp))
            BasicText("Wind-down ritual · 45 min", style = TextStyle(color = MUTE, fontSize = 15.sp, fontWeight = FontWeight.Medium))
        }

        val s = current?.scene
        if (s != null) {
            val jarRight = s.cardX + 18 + ((s.cardH - 32) * 0.8f).roundToInt() + 16
            Column(
                Modifier.offset(x = jarRight.dp, y = (s.cardY + 18).dp).width((s.w - jarRight - (s.w - s.cardX - s.cardW + 16)).dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                BasicText("TONIGHT’S CANDLE", style = TextStyle(color = EMBER, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp))
                BasicText("Fig & Cedar", style = TextStyle(color = INK, fontSize = 20.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
                BasicText("Hand-poured soy · 3 wicks", style = TextStyle(color = MUTE, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MatchTag("40 h burn")
                    MatchTag("Woody")
                }
            }
            Row(
                Modifier.offset(x = 20.dp, y = (s.boxY + s.boxH + 30).dp).width((s.w - 40).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MatchFootItem("21:30", "Lights down")
                MatchRule()
                MatchFootItem("Low Embers", "Playlist")
                MatchRule()
                MatchFootItem("30 %", "Lamps")
            }
        }
    }
}

@Composable
private fun MatchTag(text: String) {
    val shape = RoundedCornerShape(12.dp)
    BasicText(
        text,
        Modifier
            .background(Color(245 / 255f, 230 / 255f, 211 / 255f, 0.08f), shape)
            .border(0.5.dp, Color(245 / 255f, 230 / 255f, 211 / 255f, 0.18f), shape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        style = TextStyle(color = INK, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.MatchFootItem(value: String, label: String) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        BasicText(value, style = TextStyle(color = INK, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"), maxLines = 1)
        BasicText(label, style = TextStyle(color = FAINT, fontSize = 12.sp, fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun MatchRule() {
    Box(Modifier.width(0.5.dp).height(28.dp).background(Color(245 / 255f, 230 / 255f, 211 / 255f, 0.18f)))
}
