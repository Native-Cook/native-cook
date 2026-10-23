package com.ngocdevv.nativecook.blocks.buttons.liquiduploadbutton

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
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
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlinx.coroutines.delay

enum class UploadShape { Pill, Round }

enum class UploadTone { Blue, Dark, Light }

private const val HEIGHT = 52f
private const val PILL_WIDTH = 164f

// The icon sits in a circle as tall as the button; the liquid rises inside it.
private const val R = HEIGHT / 2
private const val LABEL_SLIDE = 30f
private const val AUTO_PAUSE_MS = 1100L
private const val AUTO_HOLD_MS = 2400L

// One clock in seconds runs the upload (0 → T_DONE) and later the reset (T_DONE → TOTAL).
private val T_SWAP_LOADING = 0f to 0.35f
private val T_FLAT = 0f to 0.18f
private val T_DROP = 0.05f to 0.4f
private val T_DASH_OUT = 0.25f to 0.45f
private val T_RISE = 0.45f to 2.15f
private val T_SWAP_DONE = 2.35f to 2.7f
private val T_CHECK = 2.5f to 2.85f
private const val T_DONE = 2.9f
private val T_FADE = 2.9f to 3.25f
private val T_SWAP_IDLE = 2.95f to 3.35f
private val T_ARROW_BACK = 3.1f to 3.45f
private const val TOTAL = 3.5f

// The liquid's surface bulges in the middle and ripples while it rises.
private const val DOME = 4.5f
private const val WAVE = 1.4f
private const val ARROW_HALF = 6.5f
private const val HEAD = 5.2f
private const val TICK = 5.5f

private class UploadPalette(val base: Color, val well: Color, val liquid: Color, val ink: Color, val doneInk: Color)

private fun palette(shape: UploadShape, tone: UploadTone): UploadPalette {
    val round = shape == UploadShape.Round
    val white = Color.White
    return when (tone) {
        UploadTone.Light -> UploadPalette(
            Color(0xFFF2F7FF), if (round) Color(0xFFF2F7FF) else Color(0xFFE4ECFB), Color(0xFF3350FC), Color(0xFF3350FC), white,
        )
        UploadTone.Dark ->
            if (round) {
                UploadPalette(Color(0xFF1C2030), Color(0xFF1C2030), Color(0xFF57CF88), white, white)
            } else {
                UploadPalette(Color(0xFF1B1F2E), Color(0xFF171A27), Color(0xFF272C3C), white, white)
            }
        UploadTone.Blue ->
            if (round) {
                UploadPalette(Color(0xFF2743E7), Color(0xFF2743E7), Color(0xFF4A66EC), white, white)
            } else {
                UploadPalette(Color(0xFF3350FC), Color(0xFF2743E7), Color(0xFF4A66EC), white, white)
            }
    }
}

private fun seg(t: Float, window: Pair<Float, Float>) =
    ((t - window.first) / (window.second - window.first)).coerceIn(0f, 1f)

private fun easeOut(x: Float) = 1f - (1f - x).pow(3)

/** Height of the liquid's surface at [u] (−1…1 across the circle), relative to the circle's centre. */
private fun surface(u: Float, t: Float): Float {
    val rise = seg(t, T_RISE).pow(1.7f)
    val swell = sin(Math.PI.toFloat() * rise)
    val level = R - rise * (2 * R + DOME + 2f)
    return level - DOME * swell * (1f - u * u) + WAVE * swell * sin(u * 2.6f + t * 6f)
}

private enum class UploadPhase { Idle, Uploading, Done, Resetting }

private class UploadMotion {
    var phase by mutableStateOf(UploadPhase.Idle)

    /** Seconds on the clock; read only while drawing. */
    var t by mutableFloatStateOf(0f)
    var firstRun = true

    val well = Path()
    val liquid = Path()
    val stroke = Path()
    val check = Path().apply {
        moveTo(-6.5f, 0.5f)
        lineTo(-2f, 5f)
        lineTo(7f, -4.5f)
    }
    val checkMeasure = PathMeasure().apply { setPath(check, false) }

    /** Runs the clock from [from] to [to] in real time. */
    suspend fun run(from: Float, to: Float) {
        val start = withFrameNanos { it }
        while (true) {
            val now = from + (withFrameNanos { it } - start) / 1e9f
            if (now >= to) break
            t = now
        }
        t = to
    }
}

/**
 * "Upload" button whose icon circle fills with liquid. A tap folds the arrow into a tick that
 * floats up on the rising, rippling level while the label reads "Uploading"; when the circle is
 * full a check is drawn and the label reads "Uploaded". A second tap (or autoPlay) resets it.
 *
 * @param shape [UploadShape.Pill] shows the label next to the icon, [UploadShape.Round] is the icon alone.
 * @param autoPlay Uploads by itself, shows the result, resets and starts over.
 * @param autoPlayDelayMillis Extra wait before the first automatic run, to stagger a group of buttons.
 */
@Composable
fun LiquidUploadButton(
    label: String = "Upload",
    loadingLabel: String = "Uploading",
    doneLabel: String = "Uploaded",
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    shape: UploadShape = UploadShape.Pill,
    tone: UploadTone = UploadTone.Blue,
    autoPlay: Boolean = false,
    autoPlayDelayMillis: Long = 0,
) {
    // With animations off the clock still runs, but the drawing switches to plain fades.
    val still = rememberReducedMotion()
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }
    val motion = remember { UploadMotion() }
    val round = shape == UploadShape.Round
    val width = if (round) HEIGHT else PILL_WIDTH
    val colors = remember(shape, tone) { palette(shape, tone) }
    val labelStyle = remember(colors) { TextStyle(color = colors.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
    val slide = if (still) 0f else LABEL_SLIDE

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

    // The clock: an upload runs it to T_DONE and leaves it there, a reset runs it out and rewinds.
    LaunchedEffect(motion.phase) {
        when (motion.phase) {
            UploadPhase.Uploading -> {
                motion.run(0f, T_DONE)
                motion.phase = UploadPhase.Done
            }
            UploadPhase.Resetting -> {
                motion.run(T_DONE, TOTAL)
                // The reset ends looking exactly like the start, so rewinding the clock is invisible.
                motion.t = 0f
                motion.phase = UploadPhase.Idle
            }
            else -> Unit
        }
    }

    // autoPlay starts an upload after a pause and clears the result after a moment; left alone,
    // the result stays up until the next tap.
    LaunchedEffect(motion.phase, autoPlay, autoPlayDelayMillis, still, pressed) {
        if (!autoPlay || still || pressed) return@LaunchedEffect
        when (motion.phase) {
            UploadPhase.Idle -> {
                delay(AUTO_PAUSE_MS + if (motion.firstRun) autoPlayDelayMillis else 0)
                motion.firstRun = false
                motion.phase = UploadPhase.Uploading
            }
            UploadPhase.Done -> {
                delay(AUTO_HOLD_MS)
                motion.phase = UploadPhase.Resetting
            }
            else -> Unit
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESS_SCALE else 1f,
        animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
        label = "press",
    )
    val status = when (motion.phase) {
        UploadPhase.Uploading -> loadingLabel
        UploadPhase.Done -> doneLabel
        else -> label
    }

    Box(
        modifier = modifier
            .size(width.dp, HEIGHT.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                when (motion.phase) {
                    UploadPhase.Idle -> {
                        onClick()
                        motion.phase = UploadPhase.Uploading
                    }
                    UploadPhase.Done -> motion.phase = UploadPhase.Resetting
                    else -> Unit
                }
            }
            .semantics { contentDescription = status },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(Modifier.matchParentSize()) {
            // Draw in dp, like the Skia canvas of the React Native version.
            scale(density, density, pivot = Offset.Zero) {
                val t = motion.t
                val cx = width - R
                val resetting = t > T_DONE
                // How far the returning arrow still has to rise during the reset.
                val back = seg(t, T_ARROW_BACK)
                val arrowOffset = if (resetting && !still) 8f * (1f - easeOut(back)) else 0f
                val line = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round)

                drawRoundRect(colors.base, Offset.Zero, Size(width, HEIGHT), CornerRadius(R))
                if (!round) drawCircle(colors.well, R, Offset(cx, R))

                motion.well.reset()
                motion.well.addOval(Rect(cx - R, 0f, cx + R, HEIGHT))
                clipPath(motion.well) {
                    // The liquid. Reduced motion has no rising level: the fill simply fades in.
                    val fade = 1f - seg(t, T_FADE)
                    if (still) {
                        drawCircle(colors.liquid, R, Offset(cx, R), alpha = seg(t, T_RISE) * fade)
                    } else if (t > T_RISE.first) {
                        motion.liquid.reset()
                        motion.liquid.moveTo(cx - R, HEIGHT)
                        for (i in 0..12) {
                            val u = -1f + i / 6f
                            motion.liquid.lineTo(cx + u * R, R + surface(u, t))
                        }
                        motion.liquid.lineTo(cx + R, HEIGHT)
                        motion.liquid.close()
                        drawPath(motion.liquid, colors.liquid, alpha = fade)
                    }

                    // The arrow's stem. It drops to the bottom of the circle as a short tick and
                    // floats up on the liquid.
                    if (resetting || still) {
                        drawLine(
                            colors.ink,
                            Offset(cx, R + ARROW_HALF + arrowOffset),
                            Offset(cx, R - ARROW_HALF + arrowOffset),
                            strokeWidth = 2f,
                            cap = StrokeCap.Round,
                            alpha = if (resetting) back else 1f - seg(t, T_DASH_OUT),
                        )
                    } else if (t < T_RISE.second) {
                        val drop = seg(t, T_DROP) * seg(t, T_DROP)
                        val length = ARROW_HALF * 2 - (ARROW_HALF * 2 - TICK) * drop
                        val floor = min(ARROW_HALF + (R - 1.5f - ARROW_HALF) * drop, surface(0f, t))
                        drawLine(
                            colors.ink,
                            Offset(cx, R + floor),
                            Offset(cx, R + floor - length),
                            strokeWidth = 2f,
                            cap = StrokeCap.Round,
                        )
                    }
                }

                // The arrow's head. It folds flat into a dash at the start of an upload.
                val fold = if (resetting || still) 0f else easeOut(seg(t, T_FLAT))
                val tip = R - ARROW_HALF + 3.5f * fold + arrowOffset
                val wing = HEAD * (1f - fold)
                val headAlpha = if (resetting) back else 1f - seg(t, T_DASH_OUT)
                if (headAlpha > 0f) {
                    motion.stroke.reset()
                    motion.stroke.moveTo(cx - HEAD, tip + wing)
                    motion.stroke.lineTo(cx, tip)
                    motion.stroke.lineTo(cx + HEAD, tip + wing)
                    drawPath(motion.stroke, colors.ink, alpha = headAlpha, style = line)
                }

                val drawn = easeOut(seg(t, T_CHECK))
                if (drawn > 0f) {
                    motion.stroke.reset()
                    motion.checkMeasure.getSegment(0f, motion.checkMeasure.length * drawn, motion.stroke, true)
                    motion.stroke.translate(Offset(cx, R))
                    drawPath(motion.stroke, colors.doneInk, alpha = 1f - seg(t, T_FADE), style = line)
                }
            }
        }

        if (!round) {
            // The label area is everything left of the icon circle; labels sliding out are clipped by it.
            Box(
                Modifier.width((PILL_WIDTH - HEIGHT + 8f).dp).fillMaxHeight().clipToBounds(),
                contentAlignment = Alignment.Center,
            ) {
                // Labels leave through the top and arrive from below; the reset runs the other way.
                BasicText(
                    label,
                    style = labelStyle,
                    maxLines = 1,
                    modifier = Modifier.graphicsLayer {
                        val out = seg(motion.t, T_SWAP_LOADING)
                        val back = seg(motion.t, T_SWAP_IDLE)
                        alpha = 1f - out + back
                        translationY = -slide * (if (back > 0f) 1f - easeOut(back) else easeOut(out)) * density
                    },
                )
                BasicText(
                    loadingLabel,
                    style = labelStyle,
                    maxLines = 1,
                    modifier = Modifier.graphicsLayer {
                        val shown = seg(motion.t, T_SWAP_LOADING)
                        val out = seg(motion.t, T_SWAP_DONE)
                        alpha = shown - out
                        translationY = (slide * (1f - easeOut(shown)) - slide * easeOut(out)) * density
                    },
                )
                BasicText(
                    doneLabel,
                    style = labelStyle,
                    maxLines = 1,
                    modifier = Modifier.graphicsLayer {
                        val shown = seg(motion.t, T_SWAP_DONE)
                        val out = seg(motion.t, T_SWAP_IDLE)
                        alpha = shown - out
                        translationY = (slide * (1f - easeOut(shown)) + slide * easeOut(out)) * density
                    },
                )
            }
        }
    }
}
