package com.ngocdevv.nativecook.blocks.buttons.popcornpopbutton

import android.os.Build
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.blocks.buttons.popcornpopbutton.PopcornMotion.AUTO_POP
import com.ngocdevv.nativecook.blocks.buttons.popcornpopbutton.PopcornMotion.BY_RANK
import com.ngocdevv.nativecook.blocks.buttons.popcornpopbutton.PopcornMotion.autoHeat
import com.ngocdevv.nativecook.blocks.buttons.popcornpopbutton.PopcornMotion.autoPress
import com.ngocdevv.nativecook.blocks.buttons.popcornpopbutton.PopcornMotion.cookNeeded
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/*
 Popcorn Pop Button — "Reel Night", a movie-night screen whose "Start the movie" button is a little
 copper pan of kernels under a glass lid. A port of PopcornPopButton.tsx (the reference); the
 numbers are the same.

 Hold the button: the pan heats (an orange glow under it, the thermometer arc around the button
 fills gold to red, the kernels jitter), then they pop one by one — each bursts into a puffy corn
 with a squash and a spin, flies under the lid and bounces into the heap — and the heap lifts the
 lid. Let go once 15 have popped and the label rolls to "Enjoy the show"; let go earlier and the
 kernels settle back as the pan cools. 2 s after the last touch the pan tips into the bowl on the
 movie card, fresh kernels drop in and the loop starts again (PopcornPopButtonMotion.kt has the
 timeline). Reduced motion: no loop and no popping; a press rolls the label (a fade) and the pan
 stays still.
*/

/** The bowl-and-refill under a finger is the loop's 1.6 s one, played in this long. */
private const val BACK_FOR = 0.9
/** Distance the label travels when it rolls (dp). */
private const val ROLL = 26f
/** The baked art fades in over this long. */
private const val FADE_IN = 0.25

private val INK = Color(0xFFF7E6DA)
private val MUTE = Color(247 / 255f, 230 / 255f, 218 / 255f, 0.62f)
private val FAINT = Color(247 / 255f, 230 / 255f, 218 / 255f, 0.4f)
private val GOLD = Color(0xFFF4B85C)

private fun nowSeconds() = System.nanoTime() / 1e9

private class PopcornArtSet(val layout: PopcornLayout, val layers: PopcornLayers)

private class PopcornSim : PopcornStory() {
    var holding = false
    var cook = 0.0
    var lastTouch = 0.0
    var backAt = 0.0

    init {
        emptyFor = BACK_FOR
    }

    /** Corns in the pan right now (popped and not yet tipped into the bowl). */
    fun cornCount(at: Double): Int {
        var n = 0
        for (i in 0 until POPCORN_N) if (popAt[i] <= at && goneAt[i] > at) n += 1
        return n
    }
}

private class PopcornController(val auto: Boolean, val reduced: Boolean) {
    enum class Mode { Auto, Touch, Back }

    var now by mutableDoubleStateOf(0.0)
    var awake by mutableStateOf(true)
    var mode = Mode.Auto
    var loopStart = -1.0
    var artAt = -1.0
    var lastFrame = -1.0
    var sim = PopcornSim()
    /** Reduced motion: the label's state and when it last changed. */
    var quietOn = false
    var quietAt = -10.0
    var popped = false
    private val story = PopcornStory()
    private val pose = PopcornPose()
    private var poseAt = Double.NaN

    fun loopTime(at: Double) = if (!auto || loopStart < 0) 0.0 else (((at - loopStart) % LOOP) + LOOP) % LOOP

    fun pose(g: PopcornGeo, at: Double = now): PopcornPose {
        if (at == poseAt) return pose
        poseAt = at
        when {
            reduced -> {
                PopcornMotion.makePose(PopcornMotion.autoStory(0.0, story), g, pose)
                val k = PopcornMotion.clamp01((at - quietAt) / 0.3)
                pose.label = if (quietOn) k else 1 - k
            }
            mode == Mode.Auto -> PopcornMotion.makePose(PopcornMotion.autoStory(loopTime(at), story), g, pose)
            else -> {
                sim.now = at
                PopcornMotion.makePose(sim, g, pose)
            }
        }
        return pose
    }

    /** One frame of the simulation (only under a finger and while it settles). */
    fun frame(at: Double) {
        if (loopStart < 0) loopStart = at
        if (artAt < 0) artAt = at
        val dt = if (lastFrame < 0) 1.0 / 60 else min(at - lastFrame, 1.0 / 20)
        lastFrame = at
        popped = false
        if (mode != Mode.Auto) {
            val s = sim
            if (s.holding) s.heat += (1 - s.heat) * (1 - exp(-dt * 1.6))
            else s.heat = max(0.0, s.heat - dt * (if (mode == Mode.Back) 2.5 else 0.6))
            s.press += ((if (s.holding) 1.0 else 0.0) - s.press) * (1 - exp(-dt * 28))
            // Heat above the threshold cooks the kernels; they pop in rank order (left-over heat pops a few more).
            if (s.heat > 0.45 && mode == Mode.Touch) s.cook += (s.heat - 0.45) * dt * 2.2
            for (r in 0 until POPCORN_N) {
                val i = BY_RANK[r]
                val ready = s.popAt[i] > at || s.goneAt[i] <= at - s.emptyFor
                if (ready && mode == Mode.Touch && s.cook >= cookNeeded(r)) {
                    s.popAt[i] = at
                    s.goneAt[i] = POPCORN_NEVER
                    popped = true
                }
            }
            val count = s.cornCount(at)
            val lifted = s.lidAt <= at && s.lidBack < s.lidAt
            if (!lifted && count >= POPCORN_ENOUGH) s.lidAt = at
            if (mode == Mode.Touch && !s.holding && at - s.lastTouch > 2) {
                // Into the bowl, fresh kernels in, lid down, label back.
                mode = Mode.Back
                s.backAt = at
                s.emptyFor = BACK_FOR
                for (i in 0 until POPCORN_N) if (s.popAt[i] <= at && s.goneAt[i] > at) s.goneAt[i] = at
                if (s.lidAt <= at && s.lidBack < s.lidAt) s.lidBack = at
                if (s.labelAt <= at && s.labelOff < s.labelAt) s.labelOff = at
            } else if (mode == Mode.Back && at > s.backAt + BACK_FOR + 0.05 && s.heat < 0.01) {
                sim = PopcornSim()
                mode = Mode.Auto
                loopStart = at
            }
        }
        now = at
    }

    /** True when nothing on screen moves any more (the frame loop may stop). */
    fun idle(at: Double): Boolean {
        if (at - artAt < FADE_IN) return false
        if (reduced) return at - quietAt > 0.35
        return !auto && mode == Mode.Auto
    }

    /** Hands what the loop shows to the simulation, so a press never makes anything jump. */
    fun takeOver(at: Double) {
        val s = PopcornSim()
        s.lastTouch = at
        if (mode == Mode.Auto && auto) {
            val t = loopTime(at)
            val base = at - t
            var count = 0
            for (i in 0 until POPCORN_N) {
                if (AUTO_POP[i] <= t) {
                    s.popAt[i] = base + AUTO_POP[i]
                    count += 1
                }
                if (t >= 6.4) s.goneAt[i] = base + 6.4
            }
            s.emptyFor = if (t >= 6.4) 1.6 else BACK_FOR
            if (t >= 4.6) s.lidAt = base + 4.6
            if (t >= 6.4) s.lidBack = base + 6.4
            if (t >= 5.0) s.labelAt = base + 5.0
            if (t >= 6.4) s.labelOff = base + 6.4
            s.heat = autoHeat(t)
            s.press = autoPress(t)
            s.cook = if (t < 6.4 && count > 0) cookNeeded(count - 1) + 0.001 else 0.0
        }
        sim = s
        mode = Mode.Touch
        poseAt = Double.NaN
    }
}

/**
 * "Reel Night": a movie-night screen whose round "Start the movie" button is a copper pan of
 * kernels. Hold it to heat the pan and pop them; let go once enough have popped.
 *
 * @param autoPlay plays the 8 s heat-pop-serve loop until touched.
 */
@Composable
fun PopcornPopButton(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val view = LocalView.current
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val c = remember(auto, reduced) { PopcornController(auto, reduced) }

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFF16070C)).clipToBounds()) {
        val screen = LocalDensity.current
        val density = screen.density
        val insets = WindowInsets.safeDrawing
        val top = insets.getTop(screen) / density
        val bottom = insets.getBottom(screen) / density
        val w = maxWidth.value
        val h = maxHeight.value
        val g = remember(w, h, top, bottom) { PopcornLayout(w, h, top, bottom) }
        val painter = remember { PopcornPainter() }
        var art by remember { mutableStateOf<PopcornArtSet?>(null) }

        // Static art is baked off the main thread; the last art stays on screen while a new one bakes.
        LaunchedEffect(w, h, top, bottom, density) {
            // The demo hides the status bar and the insets animate for a moment: wait for them to
            // settle (cancellable) so a bake never restarts, and piles up, on every inset frame.
            delay(120)
            val layout = PopcornLayout(w, h, top, bottom)
            val layers = withContext(Dispatchers.Default) { PopcornPopButtonArt.bakeLayers(layout, top, min(2f, density)) }
            // A rebake (the insets settled late) swaps the art in place; only the first one fades in and starts the loop.
            val first = art == null
            art = PopcornArtSet(layout, layers)
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
                if (c.popped && c.mode == PopcornController.Mode.Touch) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
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
                    contentDescription = "Start the movie. Hold the pan to pop the popcorn, then let go."
                }
                .pointerInput(current, c) {
                    val a = current ?: return@pointerInput
                    val lay = a.layout
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val x = down.position.x / density
                        val y = down.position.y / density
                        val inside = hypot(x - lay.bx, y - lay.by) < lay.D / 2 + 6
                        val ignoring = !inside || c.mode == PopcornController.Mode.Back
                        if (!ignoring) {
                            val at = nowSeconds()
                            if (reduced) {
                                c.quietOn = !c.quietOn
                                c.quietAt = at
                            } else {
                                c.takeOver(at)
                                c.sim.holding = true
                            }
                            c.awake = true
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        }
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                            }
                        } finally {
                            if (!ignoring && !reduced && c.mode == PopcornController.Mode.Touch) {
                                val s = c.sim
                                val at = nowSeconds()
                                s.holding = false
                                s.lastTouch = at
                                val labelOn = s.labelAt <= at && s.labelOff < s.labelAt
                                if (!labelOn && s.cornCount(at) >= POPCORN_ENOUGH) {
                                    s.labelAt = at
                                    val kind = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
                                    view.performHapticFeedback(kind)
                                }
                            }
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
                val fade = PopcornMotion.clamp01((c.now - c.artAt) / FADE_IN)
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
            BasicText("REEL NIGHT", style = TextStyle(color = GOLD, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp))
            BasicText("Friday, 8 pm", style = TextStyle(color = INK, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp))
            BasicText("Living room · 4 guests · Sofa seats", style = TextStyle(color = MUTE, fontSize = 15.sp, fontWeight = FontWeight.Medium))
        }

        // Card copy.
        Column(
            Modifier.offset(x = (g.cardX + 86).dp, y = (g.cardY + 17).dp).width(max(0f, (g.bowlX - 40) - (g.cardX + 86)).dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            BasicText("TONIGHT’S PICK", style = TextStyle(color = GOLD, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp))
            BasicText("Moonlit Harbor", style = TextStyle(color = INK, fontSize = 20.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText("Drama · 1 h 52 min · Rated 4.6", style = TextStyle(color = MUTE, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        // The button's label: scaled with the press around the button's centre, each line rolling.
        val labelTop = g.by + g.D * 0.215f
        val labelH = 58f
        Column(
            Modifier
                .offset(x = (g.bx - g.D * 0.36f).dp, y = labelTop.dp)
                .width((g.D * 0.72f).dp)
                .height(labelH.dp)
                .graphicsLayer {
                    val a = art ?: return@graphicsLayer
                    val s = 1 - 0.03f * c.pose(a.layout.geo).press.toFloat()
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(0.5f, (g.by - labelTop) / labelH)
                },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val label = { art?.let { c.pose(it.layout.geo).label.toFloat() } ?: 0f }
            PopcornRollingLine(label, "Hold to pop", "Fresh, salted, buttered", CAPTION, 8f, reduced)
            PopcornRollingLine(label, "Start the movie", "Enjoy the show", CTA, 1f, reduced)
        }

        Row(Modifier.offset(x = 16.dp, y = g.footerY.dp).width((w - 32).dp)) {
            PopcornFootItem("22", "Kernels")
            PopcornFootItem("Sea salt", "Seasoning")
            PopcornFootItem("3 min", "Pop time")
        }
    }
}

private val CAPTION = TextStyle(color = FAINT, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, textAlign = TextAlign.Center)
private val CTA = TextStyle(color = INK, fontSize = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp, textAlign = TextAlign.Center)

/** Two lines in one 28 dp slot: the first rolls up and out, the second in from below. */
@Composable
private fun PopcornRollingLine(label: () -> Float, a: String, b: String, style: TextStyle, offset: Float, reduced: Boolean) {
    val roll = if (reduced) 0f else ROLL
    Box(Modifier.fillMaxWidth().height(28.dp).clipToBounds()) {
        BasicText(
            a,
            Modifier.fillMaxWidth().offset(y = offset.dp).graphicsLayer {
                val k = label()
                alpha = 1 - k
                translationY = -roll * k * density
            },
            style = style,
            maxLines = 1,
        )
        BasicText(
            b,
            Modifier.fillMaxWidth().offset(y = offset.dp).graphicsLayer {
                val k = label()
                alpha = k
                translationY = roll * (1 - k) * density
            },
            style = style,
            maxLines = 1,
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.PopcornFootItem(value: String, label: String) {
    Column(
        Modifier.weight(1f).padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BasicText(value, style = TextStyle(color = INK, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"), maxLines = 1)
        BasicText(label, style = TextStyle(color = FAINT, fontSize = 12.sp, fontWeight = FontWeight.Medium))
    }
}
