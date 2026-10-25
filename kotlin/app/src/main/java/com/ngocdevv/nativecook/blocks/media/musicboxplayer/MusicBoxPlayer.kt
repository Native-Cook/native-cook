package com.ngocdevv.nativecook.blocks.media.musicboxplayer

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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/*
 Music Box Player — "Lullaby", a sleep-sounds app playing "Willow Waltz" on an open music box: a
 brass cylinder studded with pins, a steel comb of 18 teeth, a crank on the front. A port of
 MusicBoxPlayer.tsx (the reference); the numbers are the same.

 Turn the crank (a circular drag around its hub; it only turns forward, like a ratchet) or roll the
 cylinder down with a finger: the cylinder turns a quarter of the crank's angle, every pin that
 passes the comb bends its tooth back and lets it ring (decaying in 0.42 s, a warm glow at its
 tip), a note floats up, the governor's fan spins and the playhead follows. Let go and the cylinder
 runs on and slows (friction 1.6 /s). 2 s after the last touch (once it has stopped) it eases to
 the song's start (0.9 s) and the loop plays again (MusicBoxPlayerMotion.kt has the timeline).
 Reduced motion: no loop; the crank still plays it.
*/

private const val BACK_FOR = 0.9
private const val FADE_IN = 0.25
/** Recent plucks kept for the floating notes (tooth, time pairs). */
private const val RECENT_MAX = 20
private val INK = Color(0xFFF1ECFF)
private val MUTE = Color(241 / 255f, 236 / 255f, 255 / 255f, 0.62f)
private val FAINT = Color(241 / 255f, 236 / 255f, 255 / 255f, 0.42f)
private val GOLD = Color(0xFFF3C987)

private fun nowSeconds() = System.nanoTime() / 1e9

private class MusicBoxSim {
    var a = 0.0
    var omega = 0.0
    var grab = false
    /** 0 the crank, 1 the cylinder. */
    var how = 0
    var lastAng = 0.0
    var lastY = 0.0
    var lastMove = 0.0
    var lastTouch = 0.0
    val last = DoubleArray(MUSICBOX_TEETH) { -1.0 }
    /** Recent plucks in time order: tooth, time. */
    val recent = DoubleArray(RECENT_MAX * 2)
    var recentCount = 0
    var backAt = -1.0
    var backFrom = 0.0
    var backTo = 0.0
    var tick = 0.0

    fun push(j: Int, at: Double) {
        if (recentCount == RECENT_MAX) drop(1)
        recent[recentCount * 2] = j.toDouble()
        recent[recentCount * 2 + 1] = at
        recentCount += 1
    }

    fun drop(n: Int) {
        System.arraycopy(recent, n * 2, recent, 0, (recentCount - n) * 2)
        recentCount -= n
    }

    /** Turns the cylinder to angle a, plucking every pin that passes the comb on the way. */
    fun advance(to: Double, at: Double): Boolean {
        if (to <= a) return false
        var hit = false
        MusicBoxMotion.passed(a, to) { p ->
            val j = MusicBoxMotion.PINS[p * 2 + 1]
            last[j] = at
            push(j, at)
            hit = true
        }
        var old = 0
        while (old < recentCount && at - recent[old * 2 + 1] > MUSICBOX_NOTE_LIFE) old += 1
        if (old > 0) drop(old)
        a = to
        return hit
    }
}

private fun musicBoxWrapPi(x: Double): Double {
    val k = PI * 2
    var y = ((x % k) + k) % k
    if (y > PI) y -= k
    return y
}

private class MusicBoxController(val auto: Boolean) {
    enum class Mode { Auto, Touch, Back }

    var now by mutableDoubleStateOf(0.0)
    var awake by mutableStateOf(true)
    var mode = Mode.Auto
    var loopStart = -1.0
    var artAt = -1.0
    var lastFrame = -1.0
    var sim = MusicBoxSim()
    var plucked = false
    private val pose = MusicBoxPose()
    private var poseAt = Double.NaN

    fun loopTime(at: Double) = if (!auto || loopStart < 0) 0.0 else (((at - loopStart) % LOOP) + LOOP) % LOOP

    fun pose(g: MusicBoxGeo, at: Double = now): MusicBoxPose {
        if (at == poseAt) return pose
        poseAt = at
        if (mode == Mode.Auto) return MusicBoxMotion.loopPose(g, loopTime(at), pose)
        val s = sim
        val turn = PI * 2
        if (mode == Mode.Back) {
            val k = min(1.0, (at - s.backAt) / BACK_FOR)
            val e = k * k * (3 - 2 * k)
            val a = s.backFrom + (s.backTo - s.backFrom) * e
            val from = (((s.backFrom % turn) + turn) % turn) / turn
            return MusicBoxMotion.poseFrom(g, a, a * MUSICBOX_CRANK_RATIO, at, s.last, s.recent, s.recentCount, from * (1 - e), pose)
        }
        return MusicBoxMotion.poseFrom(g, s.a, s.a * MUSICBOX_CRANK_RATIO, at, s.last, s.recent, s.recentCount, (((s.a % turn) + turn) % turn) / turn, pose)
    }

    fun frame(at: Double) {
        if (loopStart < 0) loopStart = at
        if (artAt < 0) artAt = at
        val dt = if (lastFrame < 0) 1.0 / 60 else min(at - lastFrame, 1.0 / 20)
        lastFrame = at
        plucked = false
        if (mode == Mode.Touch) {
            val s = sim
            if (!s.grab) {
                s.omega *= exp(-dt * 1.6)
                if (s.omega < 0.05) s.omega = 0.0
                if (s.advance(s.a + s.omega * dt, at) && at - s.tick > 0.09) {
                    s.tick = at
                    plucked = true
                }
            } else if (at - s.lastMove > 0.08) {
                s.omega *= exp(-dt * 12)
            }
            if (!s.grab && s.omega == 0.0 && at - s.lastTouch > 2) {
                s.backAt = at
                s.backFrom = s.a
                s.backTo = (s.a / (PI * 2)).roundToLong() * PI * 2
                mode = Mode.Back
            }
        } else if (mode == Mode.Back) {
            val s = sim
            val newest = if (s.recentCount > 0) s.recent[s.recentCount * 2 - 1] else -10.0
            if (at - s.backAt >= BACK_FOR && at - newest > MUSICBOX_NOTE_LIFE) {
                sim = MusicBoxSim()
                mode = Mode.Auto
                loopStart = at
            }
        }
        now = at
    }

    fun idle(at: Double) = at - artAt >= FADE_IN && !auto && mode == Mode.Auto

    /** Takes the cylinder from wherever the loop has it, with its ringing teeth and notes. */
    fun takeOver(at: Double) {
        val s = MusicBoxSim()
        s.lastTouch = at
        if (auto) {
            val t = loopTime(at)
            val base = at - t
            s.a = MusicBoxMotion.turnsAt(t) * PI * 2
            // Plucks in time order (PLUCKS is not sorted: the bass pins come after the melody).
            val order = (0 until MusicBoxMotion.PIN_COUNT).filter { MusicBoxMotion.PLUCKS[it] <= t }.sortedBy { MusicBoxMotion.PLUCKS[it] }
            for (p in order) {
                val tp = MusicBoxMotion.PLUCKS[p]
                val j = MusicBoxMotion.PINS[p * 2 + 1]
                if (base + tp > s.last[j]) s.last[j] = base + tp
                if (t - tp < MUSICBOX_NOTE_LIFE) s.push(j, base + tp)
            }
        }
        sim = s
        mode = Mode.Touch
        poseAt = Double.NaN
    }
}

/**
 * "Lullaby": "Willow Waltz" on an open music box. Turn the crank, or roll the cylinder down, to
 * play it.
 *
 * @param autoPlay plays the 8 s crank-the-song loop until touched.
 */
@Composable
fun MusicBoxPlayer(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val view = LocalView.current
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val c = remember(auto) { MusicBoxController(auto) }

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFF161328)).clipToBounds()) {
        val screen = LocalDensity.current
        val density = screen.density
        val insets = WindowInsets.safeDrawing
        val top = insets.getTop(screen) / density
        val bottom = insets.getBottom(screen) / density
        val w = maxWidth.value
        val h = maxHeight.value
        val g = remember(w, h, top, bottom) { MusicBoxLayout(w, h, top, bottom) }
        var art by remember { mutableStateOf<Pair<MusicBoxLayout, MusicBoxBaked>?>(null) }
        val painter = remember(art) { MusicBoxPainter(art?.first ?: g) }

        LaunchedEffect(w, h, top, bottom, density) {
            // Wait for the insets to settle (the demo hides the status bar) before baking.
            delay(120)
            val layout = MusicBoxLayout(w, h, top, bottom)
            val scene = withContext(Dispatchers.Default) { MusicBoxPlayerArt.bakeScene(layout, min(2f, density)) }
            // A rebake (the insets settled late) swaps the art in place; only the first one fades in and starts the loop.
            val first = art == null
            art = layout to scene
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
                if (c.plucked) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                if (c.idle(c.now)) {
                    c.awake = false
                    break
                }
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Willow Waltz on a music box. Turn the crank, or roll the cylinder down, to play it." }
                .pointerInput(current, c) {
                    val lay = current?.first ?: return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val ex = down.position.x / density
                        val ey = down.position.y / density
                        if (c.mode == MusicBoxController.Mode.Back) return@awaitEachGesture
                        val onCrank = hypot(ex - lay.hubX, ey - lay.hubY) < lay.arm + 64
                        val onCylinder = ex > lay.cylX0 - 10 && ex < lay.cylX1 + 10 && ey > lay.cylY - lay.cylR - 30 && ey < lay.combY + 20
                        if (!onCrank && !onCylinder) return@awaitEachGesture
                        var at = nowSeconds()
                        c.awake = true
                        if (c.mode == MusicBoxController.Mode.Auto) c.takeOver(at)
                        val s = c.sim
                        s.grab = true
                        s.how = if (onCrank) 0 else 1
                        s.lastAng = atan2((ey - lay.hubY).toDouble(), (ex - lay.hubX).toDouble())
                        s.lastY = ey.toDouble()
                        s.lastMove = at
                        s.lastTouch = at
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                                ch.consume()
                                at = nowSeconds()
                                val x = ch.position.x / density.toDouble()
                                val y = ch.position.y / density.toDouble()
                                val d = if (s.how == 0) {
                                    val ang = atan2(y - lay.hubY, x - lay.hubX)
                                    val step = musicBoxWrapPi(ang - s.lastAng) / MUSICBOX_CRANK_RATIO
                                    s.lastAng = ang
                                    step
                                } else {
                                    val step = (y - s.lastY) / (lay.cylR * 1.6)
                                    s.lastY = y
                                    step
                                }
                                // A ratchet: it only turns forward.
                                if (d > 0) {
                                    val dt = max(1.0 / 240, at - s.lastMove)
                                    s.omega = 0.6 * s.omega + 0.4 * min(14.0, d / dt)
                                    s.lastMove = at
                                    if (s.advance(s.a + d, at) && at - s.tick > 0.09) {
                                        s.tick = at
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    }
                                }
                                s.lastTouch = at
                            }
                        } finally {
                            at = nowSeconds()
                            s.grab = false
                            s.lastTouch = at
                            if (at - s.lastMove > 0.1) s.omega = 0.0
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
                val pose = c.pose(a.first.geo)
                val fade = MusicBoxPlayerFade.of(c.now, c.artAt)
                if (fade < 1) {
                    painter.placeholder(canvas, a.first)
                    val save = canvas.saveLayerAlpha(0f, 0f, a.first.w, a.first.h, (255 * fade).toInt())
                    painter.frame(canvas, a.first, a.second, pose)
                    canvas.restoreToCount(save)
                } else {
                    painter.frame(canvas, a.first, a.second, pose)
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = (top + 14).dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText("LULLABY", style = TextStyle(color = GOLD, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp))
            BasicText("Willow Waltz", style = TextStyle(color = INK, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp))
            BasicText("Music box · 3/4 · Sleep sounds", style = TextStyle(color = MUTE, fontSize = 15.sp, fontWeight = FontWeight.Medium))
        }

        // The player panel: the song, the hint and three chips (the bar and times are drawn).
        Box(Modifier.offset(x = 16.dp, y = g.panelY.dp).width((w - 32).dp).height(g.panelH.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    BasicText("Willow Waltz", style = TextStyle(color = INK, fontSize = 17.sp, fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicText("Brass comb, 18 notes · Ida Morrow", Modifier.padding(top = 1.dp), style = TextStyle(color = FAINT, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                BasicText("Turn the crank", style = TextStyle(color = GOLD, fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
            }
            Row(
                Modifier.align(Alignment.BottomStart).padding(start = 20.dp, end = 20.dp, bottom = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val chips = arrayOf("Timer 30 min", "Fade out", "Night light")
                for (i in chips.indices) {
                    val on = i == 0
                    Box(
                        Modifier
                            .height(32.dp)
                            .background(if (on) Color(243 / 255f, 201 / 255f, 135 / 255f, 0.16f) else Color(241 / 255f, 236 / 255f, 1f, 0.08f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(chips[i], style = TextStyle(color = if (on) GOLD else MUTE, fontSize = 12.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
                    }
                }
            }
        }
    }
}

/** The baked scene's fade-in over the placeholder. */
private object MusicBoxPlayerFade {
    fun of(now: Double, artAt: Double): Double = if (artAt < 0) 0.0 else min(1.0, max(0.0, (now - artAt) / FADE_IN))
}
