package com.ngocdevv.nativecook.blocks.buttons.submitconfettibutton

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.TAU
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.surface
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val WIDTH = 200f
private const val HEIGHT = 48f

// The pill narrows to this while it is loading.
private const val LOADING_WIDTH = 160f

// The canvases overflow the button by this much so the confetti has room to fly.
private val BLEED = 116.dp
private val ICON = 16.dp
private const val AUTO_PAUSE_MS = 1500L

// The whole run is one clock in seconds; every part below is a window on it.
private val T_LABEL_OUT = 0f to 0.2f
private val T_SQUEEZE = 0f to 0.4f
private const val T_DOTS_IN = 0.25f
private val T_DOTS_OUT = 2.0f to 2.15f
private val T_WIDEN = 2.0f to 2.45f
private const val T_BURST = 2.2f
private val T_DONE_IN = 2.7f to 2.95f
private val T_CHECK = 2.9f to 3.2f
private val T_DONE_OUT = 5.3f to 5.55f
private val T_LABEL_IN = 5.85f to 6.15f
private const val TOTAL = 6.2f

private const val DOT_GAP = 9f
private const val DOT_R = 2.3f
private const val DOT_PERIOD = 1.0f

private val Navy = Color(0xFF191E2C)
private val DotBlue = Color(0xFF5D7BFF)
private val ArrowBlue = Color(0xFF4A7AFF)
private val CheckGreen = Color(0xFF3EE888)
private val ConfettiColors = listOf(Color(0xFFA9B9F3), Color(0xFF2B55CD), Color(0xFF6444EC), Color(0xFF8393DE))
private const val CONFETTI_COUNT = 40
private const val CONFETTI_LIFE = 2.6f

// Air drag: pieces shoot out, stall within a third of a second, then drift down.
private const val DRAG = 3.2f

private val LabelStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)

private fun seg(t: Float, window: Pair<Float, Float>) =
    ((t - window.first) / (window.second - window.first)).coerceIn(0f, 1f)

private fun easeOut(x: Float) = 1f - (1f - x).pow(3)

private fun easeOutBack(x: Float): Float {
    val c = 1.9f
    return 1f + (c + 1f) * (x - 1f).pow(3) + c * (x - 1f).pow(2)
}

/** Cheap repeatable noise in 0…1, identical on every platform. */
private fun noise(i: Int, k: Int): Float {
    val s = sin(i * 12.9898 + k * 78.233) * 43758.5453
    return (s - floor(s)).toFloat()
}

private class Piece(i: Int) {
    val color = i % ConfettiColors.size
    val x0 = (noise(i, 1) - 0.5f) * WIDTH * 0.72f

    // Fan out: pieces born towards an end of the pill lean that way.
    private val angle = (noise(i, 2) - 0.5f) * 1.3f + (x0 / WIDTH) * 0.9f
    private val speed = 200f + noise(i, 3) * 260f
    val vx = sin(angle) * speed
    val vy = -cos(angle) * speed
    val fall = 95f + noise(i, 4) * 75f
    val rot = noise(i, 5) * TAU
    val spin = (noise(i, 6) - 0.5f) * 12f
    val flip = 3f + noise(i, 7) * 7f
    val phase = noise(i, 8) * TAU
    val tiny = noise(i, 9) < 0.22f
    val hw = if (tiny) 1.2f else 2.6f + noise(i, 10) * 2.6f
    val hh = if (tiny) 1.2f else 2f + noise(i, 11) * 2.4f
    val life = 1.7f + noise(i, 12) * 0.9f
    val sway = 6f + noise(i, 13) * 10f
    val swayRate = 2f + noise(i, 14) * 2f
}

private val Pieces = List(CONFETTI_COUNT) { Piece(it) }

/** Every piece of one colour, [age] seconds after the burst, as a single path of tumbling quads. */
private fun confetti(path: Path, age: Float, color: Int, x: Float, y: Float) {
    path.reset()
    if (age <= 0f || age >= CONFETTI_LIFE) return
    val cx = x + WIDTH / 2
    val top = y + 4f
    val d = (1f - exp(-DRAG * age)) / DRAG
    for (p in Pieces) {
        if (p.color != color || age >= p.life) continue
        val size = min(1f, age / 0.06f) * (1f - seg(age, p.life - 0.35f to p.life))
        val px = cx + p.x0 + p.vx * d + p.sway * sin(p.swayRate * age + p.phase) * min(1f, age)
        val py = top + p.vy * d + p.fall * (age - d)
        val a = p.rot + p.spin * age
        // Tumbling end over end squashes the piece to a sliver and back.
        val hh = p.hh * size * (if (p.tiny) 1f else cos(p.flip * age + p.phase))
        val hw = p.hw * size
        val ax = cos(a) * hw
        val ay = sin(a) * hw
        val bx = -sin(a) * hh
        val by = cos(a) * hh
        path.moveTo(px - ax - bx, py - ay - by)
        path.lineTo(px + ax - bx, py + ay - by)
        path.lineTo(px + ax + bx, py + ay + by)
        path.lineTo(px - ax + bx, py - ay + by)
        path.close()
    }
}

/** Pill width at clock time [t]: squeezes in for loading, springs back out for the result. */
private fun pillWidth(t: Float, still: Boolean): Float {
    if (still) return WIDTH
    val squeeze = easeOut(seg(t, T_SQUEEZE)) - easeOutBack(seg(t, T_WIDEN))
    return WIDTH - (WIDTH - LOADING_WIDTH) * squeeze
}

private class SubmitMotion {
    /** Seconds into the current run; 0 while idle. */
    var t by mutableFloatStateOf(0f)
    var busy by mutableStateOf(false)

    val confetti = List(ConfettiColors.size) { Path() }
    val arrow = Path().apply {
        moveTo(8f, 2.5f)
        lineTo(8f, 13f)
        moveTo(3.5f, 8.5f)
        lineTo(8f, 13f)
        lineTo(12.5f, 8.5f)
    }
    val check = Path().apply {
        moveTo(2.5f, 8.5f)
        lineTo(6.5f, 12.5f)
        lineTo(13.5f, 4.5f)
    }
    val checkMeasure = PathMeasure().apply { setPath(check, false) }
    val checkDrawn = Path()

    /** One run of the clock. It ends where it began (label showing), so the rewind is invisible. */
    suspend fun run() {
        if (busy) return
        busy = true
        try {
            val start = withFrameNanos { it }
            while (true) {
                val seconds = (withFrameNanos { it } - start) / 1e9f
                if (seconds >= TOTAL) break
                t = seconds
            }
        } finally {
            t = 0f
            busy = false
        }
    }
}

/**
 * Navy "Submit" pill. A tap squeezes it into three loading dots, then it springs back open with a
 * burst of confetti and a green-checked "Success" before returning to its label.
 *
 * @param autoPlay Submits by itself over and over, with a pause between runs.
 */
@Composable
fun SubmitConfettiButton(
    label: String = "Submit",
    successLabel: String = "Success",
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
) {
    // With animations off the clock still runs, but the squeeze, the dot wave and the confetti
    // are switched off, leaving plain fades.
    val still = rememberReducedMotion()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }
    val motion = remember { SubmitMotion() }
    val slide = if (still) 0f else 1f

    LaunchedEffect(interaction) {
        interaction.interactions.collect {
            when (it) {
                is PressInteraction.Press -> {
                    pressed = true
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> pressed = false
            }
        }
    }

    LaunchedEffect(autoPlay, still) {
        if (!autoPlay || still) return@LaunchedEffect
        while (true) {
            delay(AUTO_PAUSE_MS)
            motion.run()
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESS_SCALE else 1f,
        animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
        label = "press",
    )

    Box(
        modifier = modifier
            .size(WIDTH.dp, HEIGHT.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                if (!motion.busy) {
                    onClick()
                    scope.launch { motion.run() }
                }
            }
            // The label is swapped for dots and the result, so name the button explicitly.
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, _, _ ->
                    val t = motion.t
                    val w = pillWidth(t, still)
                    drawRoundRect(Navy, Offset(x + (WIDTH - w) / 2, y), Size(w, HEIGHT), CornerRadius(HEIGHT / 2))

                    // The three loading dots: they pop in one after another, then a wave runs
                    // through them every second.
                    if (t > T_DOTS_IN && t < T_DOTS_OUT.second) {
                        for (i in 0 until 3) {
                            val start = T_DOTS_IN + i * 0.06f
                            val pop = if (still) seg(t, start to start + 0.2f) else easeOutBack(seg(t, start to start + 0.25f))
                            val phase = (t - 0.9f - i * 0.12f) / DOT_PERIOD
                            val beat = phase - floor(phase)
                            val hop = if (still || beat > 0.45f) 0f else sin(beat / 0.45f * Math.PI.toFloat()) * 5f
                            val r = DOT_R * pop * (1f - seg(t, T_DOTS_OUT))
                            if (r > 0.05f) {
                                drawCircle(DotBlue, r, Offset(x + WIDTH / 2 + (i - 1) * DOT_GAP, y + HEIGHT / 2 - hop))
                            }
                        }
                    }
                }
            }
        }

        // The label drops away on the way out and comes back down from above.
        Row(
            modifier = Modifier.graphicsLayer {
                val out = seg(motion.t, T_LABEL_OUT)
                val back = seg(motion.t, T_LABEL_IN)
                alpha = 1f - out + back
                translationY = slide * (if (back > 0f) -8f * (1f - easeOut(back)) else 10f * easeOut(out)) * density
            },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.size(ICON)) {
                val unit = size.width / 16f
                scale(unit, unit, pivot = Offset.Zero) {
                    drawPath(motion.arrow, ArrowBlue, style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            BasicText(label, style = LabelStyle, maxLines = 1)
        }

        Row(
            modifier = Modifier.graphicsLayer {
                val shown = seg(motion.t, T_DONE_IN)
                alpha = shown - seg(motion.t, T_DONE_OUT)
                translationY = slide * 8f * (1f - easeOut(shown)) * density
            },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.size(ICON)) {
                val drawn = easeOut(seg(motion.t, T_CHECK))
                if (drawn > 0f) {
                    motion.checkDrawn.reset()
                    motion.checkMeasure.getSegment(0f, motion.checkMeasure.length * drawn, motion.checkDrawn, true)
                    val unit = size.width / 16f
                    scale(unit, unit, pivot = Offset.Zero) {
                        drawPath(motion.checkDrawn, CheckGreen, style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
            BasicText(successLabel, style = LabelStyle, maxLines = 1)
        }

        // Confetti flies in front of the pill and its label.
        if (!still) {
            Box(Modifier.matchParentSize().bleed(BLEED)) {
                Canvas(Modifier.fillMaxSize()) {
                    surface(BLEED) { x, y, _, _ ->
                        val age = motion.t - T_BURST
                        if (age > 0f && age < CONFETTI_LIFE) {
                            for ((index, color) in ConfettiColors.withIndex()) {
                                confetti(motion.confetti[index], age, index, x, y)
                                drawPath(motion.confetti[index], color)
                            }
                        }
                    }
                }
            }
        }
    }
}
