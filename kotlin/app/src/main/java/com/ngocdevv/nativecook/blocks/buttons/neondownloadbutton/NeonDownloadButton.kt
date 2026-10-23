package com.ngocdevv.nativecook.blocks.buttons.neondownloadbutton

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.surface
import android.graphics.BlurMaskFilter
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
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
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val WIDTH = 220f
private const val HEIGHT = 52f

// Width of the pill that announces the result.
private const val DONE_WIDTH = 170f
private const val R = HEIGHT / 2
private const val KNOB_R = 20.5f
private const val STROKE = 2.2f

// The progress dot rides the middle of the outline.
private const val RING_R = R - STROKE / 2

// The canvas overflows the button by this much on every side to fit the glow and the dot's flight.
private val BLEED = 60.dp
private const val AUTO_PAUSE_MS = 1500L

// The whole run is one clock in seconds; every part below is a window on it.
private val T_FLASH = 0f to 0.2f
private val T_RIPPLE = 0f to 0.5f
private val T_COLLAPSE = 0.08f to 0.6f
private val T_LABEL_OUT = 0.1f to 0.35f
private val T_TO_STOP = 0.1f to 0.7f
private val T_DOT_IN = 0.7f to 0.9f
private val T_ARC_IN = 0.85f to 0.95f
private val T_LAP = 0.9f to 2.5f
private val T_ARC_OUT = 2.5f to 2.8f
private val T_LAUNCH = 2.5f to 2.95f
private val T_KNOB_OUT = 2.55f to 2.85f
private val T_EXPAND = 2.6f to 3.1f
private val T_FALL = 2.95f to 3.45f
private const val T_IMPACT = 3.45f
private val T_DOT_OUT = 3.45f to 3.52f
private val T_GREEN = 3.45f to 3.65f
private val T_DONE_IN = 3.5f to 3.8f
private val T_DONE_OUT = 5.3f to 5.5f
private val T_CYAN = 5.3f to 5.6f
private val T_RETURN = 5.3f to 5.9f
private val T_KNOB_IN = 5.4f to 5.75f
private val T_TO_ARROW = 5.4f to 5.9f
private val T_LABEL_IN = 5.55f to 5.9f
private const val TOTAL = 6f

// How high the dot is thrown above the button before it drops in.
private const val APEX = 44f
private const val DOT_R = 5.5f

private val Cyan = Color(0xFF02D1FE)
private val Green = Color(0xFF00FE8C)
private val Fill = Color(0xFF2C2E2F)
private val LabelStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Light, letterSpacing = 0.2.sp)

private fun seg(t: Float, window: Pair<Float, Float>) =
    ((t - window.first) / (window.second - window.first)).coerceIn(0f, 1f)

private fun easeOut(x: Float) = 1f - (1f - x).pow(3)

private fun easeInOut(x: Float) = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f

private fun easeOutBack(x: Float): Float {
    val c = 1.7f
    return 1f + (c + 1f) * (x - 1f).pow(3) + c * (x - 1f).pow(2)
}

/** Pill width: collapses to a circle, opens into the result pill, then returns to full width. */
private fun pillWidth(t: Float, still: Boolean): Float {
    if (still) return WIDTH
    return WIDTH +
        (HEIGHT - WIDTH) * easeInOut(seg(t, T_COLLAPSE)) +
        (DONE_WIDTH - HEIGHT) * easeOutBack(seg(t, T_EXPAND)) +
        (WIDTH - DONE_WIDTH) * easeInOut(seg(t, T_RETURN))
}

/** How far the bottom edge sags: a damped wobble set off by the dot landing. */
private fun wobble(t: Float, still: Boolean): Float {
    val since = t - T_IMPACT
    if (still || since <= 0f || since > 1.2f) return 0f
    return 7f * exp(-5.5f * since) * sin(16f * since)
}

private class NeonMotion {
    /** Seconds into the current run; 0 while idle. */
    var t by mutableFloatStateOf(0f)
    var busy by mutableStateOf(false)

    val shape = Path()
    val arc = Path()
    val arrow = Path().apply {
        moveTo(0f, -7.5f)
        lineTo(0f, 7.5f)
        moveTo(-6f, 1.5f)
        lineTo(0f, 7.5f)
        lineTo(6f, 1.5f)
    }

    // Hardware canvases only honour mask filters from API 28; below that the glow is left out.
    val glow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Paint().apply {
            style = PaintingStyle.Stroke
            strokeWidth = 6f
            color = rgba(2, 209, 254, 0.7f)
            asFrameworkPaint().maskFilter = BlurMaskFilter(sigmaToRadius(7f), BlurMaskFilter.Blur.NORMAL)
        }
    } else {
        null
    }

    /** The pill's outline around ([cx], [cy]), with the bottom edge bent by [sag]. */
    fun outline(cx: Float, cy: Float, w: Float, sag: Float) {
        val left = cx - w / 2
        val right = cx + w / 2
        val top = cy - R
        shape.reset()
        shape.moveTo(left + R, top)
        shape.lineTo(right - R, top)
        shape.arcTo(Rect(right - HEIGHT, top, right, top + HEIGHT), -90f, 180f, false)
        shape.quadraticTo(cx, top + HEIGHT + sag * 2, left + R, top + HEIGHT)
        shape.arcTo(Rect(left, top, left + HEIGHT, top + HEIGHT), 90f, 180f, false)
        shape.close()
    }

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
 * Neon "download" pill with a round arrow knob. A tap collapses it to a circle around the knob,
 * a white dot laps the outline as progress, is thrown off the top and drops, green, into the pill
 * that opens to say "done!", its outline wobbling from the hit; then it returns.
 *
 * @param autoPlay Downloads by itself over and over, with a pause between runs.
 */
@Composable
fun NeonDownloadButton(
    label: String = "download",
    doneLabel: String = "done!",
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
) {
    // With animations off the clock still runs, but the shape changes and the dot are switched off.
    val still = rememberReducedMotion()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }
    val motion = remember { NeonMotion() }
    // Without motion the label simply gives way to the result when the outline turns green.
    val labelOut = if (still) T_GREEN else T_LABEL_OUT
    val slide = if (still) 0f else 12f

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
            // The label gives way to the progress ring and the result, so name the button explicitly.
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, _, _ ->
                    val t = motion.t
                    val cx = x + WIDTH / 2
                    val cy = y + R
                    val w = pillWidth(t, still)
                    motion.outline(cx, cy, w, wobble(t, still))

                    motion.glow?.let { glow -> drawIntoCanvas { it.drawPath(motion.shape, glow) } }
                    drawPath(motion.shape, Fill)
                    drawPath(motion.shape, Cyan, style = Stroke(STROKE))
                    val turned = seg(t, T_GREEN) - seg(t, T_CYAN)
                    if (turned > 0f) drawPath(motion.shape, Green, alpha = turned, style = Stroke(STROKE))

                    // Progress: a white dot laps the ring and leaves a white arc behind it.
                    val lap = easeInOut(seg(t, T_LAP))
                    val arcAlpha = if (still) 0f else seg(t, T_ARC_IN) * (1f - seg(t, T_ARC_OUT))
                    if (arcAlpha > 0f && lap > 0.001f) {
                        drawArc(
                            color = Color.White,
                            startAngle = -90f,
                            sweepAngle = min(360f * lap, 359.9f),
                            useCenter = false,
                            topLeft = Offset(cx - RING_R, cy - RING_R),
                            size = Size(RING_R * 2, RING_R * 2),
                            alpha = arcAlpha,
                            style = Stroke(2.8f, cap = StrokeCap.Round),
                        )
                    }

                    // A white ring spreads from where the knob was pressed.
                    if (!still && t > 0f && t < T_RIPPLE.second) {
                        val spread = seg(t, T_RIPPLE)
                        drawCircle(
                            Color.White,
                            radius = KNOB_R + 14f * easeOut(spread),
                            center = Offset(x + R, cy),
                            alpha = 0.9f * (1f - spread),
                            style = Stroke(2f),
                        )
                    }

                    // The knob keeps its place at the pill's left end, which is the middle once it is a
                    // circle. It fades out there while the result pill opens around it, and comes back
                    // at the left end.
                    val knobShown = if (still) 1f else 1f - seg(t, T_KNOB_OUT) + seg(t, T_KNOB_IN)
                    if (knobShown > 0f) {
                        val centred = t > T_EXPAND.first && t < T_KNOB_IN.first
                        val knobX = if (centred) cx else cx - w / 2 + R
                        val knobScale = 0.6f + 0.4f * knobShown
                        // 0 = download arrow, 1 = stop square.
                        val morph = if (still) 0f else seg(t, T_TO_STOP) - seg(t, T_TO_ARROW)
                        withTransform({
                            translate(knobX, cy)
                            scale(knobScale, knobScale, pivot = Offset.Zero)
                        }) {
                            drawCircle(Cyan, KNOB_R, Offset.Zero, alpha = knobShown)
                            if (t > 0f && t < T_FLASH.second) {
                                drawCircle(Color.White, KNOB_R, Offset.Zero, alpha = 0.8f * (1f - seg(t, T_FLASH)) * knobShown)
                            }
                            val arrowAlpha = (1f - min(1f, morph / 0.45f)) * knobShown
                            if (arrowAlpha > 0f) {
                                withTransform({ rotate(morph * 135f, pivot = Offset.Zero) }) {
                                    drawPath(
                                        motion.arrow, Color.White, alpha = arrowAlpha,
                                        style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                                    )
                                }
                            }
                            val squareAlpha = seg(morph, 0.3f to 0.7f) * knobShown
                            if (squareAlpha > 0f) {
                                val s = 0.6f + 0.4f * morph
                                withTransform({
                                    rotate((morph - 1f) * 135f, pivot = Offset.Zero)
                                    scale(s, s, pivot = Offset.Zero)
                                }) {
                                    drawRoundRect(
                                        Color.White, Offset(-5.5f, -5.5f), Size(11f, 11f), CornerRadius(1.5f),
                                        alpha = squareAlpha,
                                    )
                                }
                            }
                        }
                    }

                    // Then the dot is thrown off the top, turns green and drops into the opened pill.
                    val dotR = if (still) 0f else DOT_R * easeOutBack(seg(t, T_DOT_IN)) * (1f - seg(t, T_DOT_OUT))
                    if (dotR > 0f) {
                        var dx = cx
                        var dy: Float
                        var rx = dotR
                        var ry = dotR
                        if (t <= T_LAP.second) {
                            val a = (-90f + 360f * lap) * (Math.PI.toFloat() / 180f)
                            dx = cx + RING_R * cos(a)
                            dy = cy + RING_R * sin(a)
                        } else {
                            val apex = -RING_R - APEX
                            val fall = seg(t, T_FALL)
                            val launching = t < T_LAUNCH.second
                            dy = cy + if (launching) -RING_R - APEX * easeOut(seg(t, T_LAUNCH)) else apex + (R - 7f - apex) * fall * fall
                            // Faster means longer and thinner.
                            val speed = if (launching) 1f - seg(t, T_LAUNCH) else fall
                            rx = dotR * (1f - 0.2f * speed)
                            ry = dotR * (1f + 0.5f * speed)
                        }
                        val topLeft = Offset(dx - rx, dy - ry)
                        val oval = Size(rx * 2, ry * 2)
                        drawOval(Color.White, topLeft, oval)
                        drawOval(Green, topLeft, oval, alpha = seg(t, T_LAUNCH.first to T_LAUNCH.first + 0.3f))
                    }
                }
            }
        }

        // The idle label is centred in the space to the right of the knob.
        BasicText(
            label,
            style = LabelStyle,
            maxLines = 1,
            modifier = Modifier
                .padding(start = (HEIGHT - 8f).dp)
                .graphicsLayer {
                    val out = seg(motion.t, labelOut)
                    val back = seg(motion.t, T_LABEL_IN)
                    alpha = 1f - out + back
                    translationY = slide * (if (back > 0f) 1f - easeOut(back) else easeOut(out)) * density
                },
        )
        BasicText(
            doneLabel,
            style = LabelStyle,
            maxLines = 1,
            modifier = Modifier.graphicsLayer {
                val shown = seg(motion.t, T_DONE_IN)
                val out = seg(motion.t, T_DONE_OUT)
                alpha = shown - out
                translationY = (slide * (1f - easeOut(shown)) + slide * easeOut(out)) * density
            },
        )
    }
}
