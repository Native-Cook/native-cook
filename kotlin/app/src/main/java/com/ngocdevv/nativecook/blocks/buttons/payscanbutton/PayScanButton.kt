package com.ngocdevv.nativecook.blocks.buttons.payscanbutton

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.TAU
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.surface
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val WIDTH = 180f
private const val HEIGHT = 60f

// The canvas overflows the button by this much on every side to fit the shadow.
private val BLEED = 30.dp
private const val AUTOPLAY_PAUSE_MS = 1500L

// Card icon: centre offset from the pill's centre and half-size; it morphs into the scan frame.
private const val CARD_DX = -29.5f
private const val CARD_HW = 22.5f
private const val CARD_HH = 14.5f
private const val CARD_R = 3.5f
private const val FRAME_HALF = 13.5f
private const val FRAME_R = 5f
private const val FRAME_ARM = 7.5f
private const val LABEL_LEFT = 97f
private val Ink = Color(0xFFF1EDFF)
private val PillBrushColors = listOf(Color(0xFFB3A7FA), Color(0xFFAB9FF6))
private val LabelStyle = TextStyle(
    color = Ink,
    fontSize = 19.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = 0.6.sp,
)

// One run, in seconds. `PayScanFrame(END)` equals `PayScanFrame(0)`, so runs chain without a reset.
private const val T_SCAN = 1.45f
private const val T_SCAN_END = 3.85f
private const val T_DONE = 4.3f
private const val T_RETURN = 6.6f
private const val END = 7.4f

private fun seg(t: Float, a: Float, b: Float) = min(1f, max(0f, (t - a) / (b - a)))
private fun easeOut(x: Float) = 1f - (1f - x) * (1f - x) * (1f - x)
private fun easeInOut(x: Float) = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f

/** Everything the button shows at time [t] of a run. Same numbers as the React Native version. */
private class PayScanFrame(t: Float, still: Boolean) {
    private val away = t < T_RETURN
    private val first = t < 3f

    // Pill width: shrinks to a circle for the scan, widens again for the result.
    val w = if (first) {
        WIDTH + (HEIGHT - WIDTH) * easeInOut(seg(t, 0.45f, 0.95f))
    } else {
        HEIGHT + (WIDTH - HEIGHT) * easeInOut(seg(t, 4.1f, 4.6f))
    }

    // Label wipes: fraction of the text revealed from its left edge.
    val pay = if (first) 1f - easeOut(seg(t, 0f, 0.35f)) else easeOut(seg(t, 6.75f, 7.15f))
    val done = easeOut(seg(t, T_DONE + 0.1f, 4.85f)) * (1f - easeOut(seg(t, 6.3f, 6.65f)))

    // Card stripe and chip.
    val detail = if (first) 1f - seg(t, 0f, 0.25f) else easeOut(seg(t, 6.95f, 7.3f))

    // Card → centred square, then the square's sides open into four brackets.
    val morph = if (away) easeInOut(seg(t, 0.12f, 0.6f)) else 0f
    val bracket = if (away) easeOut(seg(t, 0.9f, 1.25f)) else 0f
    val face = if (away) easeOut(seg(t, 1.1f, T_SCAN)) else 0f
    val icon = if (away) 1f - seg(t, 4.0f, 4.25f) else 1f

    // The card outline draws itself back in at the end.
    val outline = if (away) 1f else easeInOut(seg(t, T_RETURN, 7.1f))
    val scan = seg(t, T_SCAN, T_SCAN + 0.2f) * (1f - seg(t, T_SCAN_END - 0.15f, T_SCAN_END))
    val scanY = if (still) 0f else -cos(TAU * (t - T_SCAN) / 1.25f) * 8f
}

private class PayScanMotion {
    var t by mutableFloatStateOf(0f)
    var busy = false

    val shadow = SoftRoundRect(rgba(122, 116, 214, 0.34f), sigma = 9f)
    val corners = List(4) { Path() }
    val trimmed = Path()
    val measure = PathMeasure()
    val scratch = Path()

    /**
     * One pay → scan → complete run, timed off the frame clock so it also plays when system
     * animations are off: it is a sequence of states, not decoration.
     */
    suspend fun run(onComplete: () -> Unit) {
        if (busy) return
        busy = true
        try {
            val start = withFrameNanos { it }
            var announced = false
            while (true) {
                val seconds = (withFrameNanos { it } - start) / 1e9f
                t = min(seconds, END)
                if (!announced && seconds >= T_DONE) {
                    announced = true
                    onComplete()
                }
                if (seconds >= END) break
            }
        } finally {
            t = 0f
            busy = false
        }
    }
}

private const val K = 0.5523f

/** One rounded corner at (px, py) with arms [ax]/[ay] long pointing towards (dx, dy). */
private fun Path.corner(
    px: Float, py: Float, dx: Float, dy: Float, ax: Float, ay: Float, r: Float, horizontalFirst: Boolean,
) {
    val c = r * (1f - K)
    reset()
    if (horizontalFirst) {
        moveTo(px + dx * ax, py)
        lineTo(px + dx * r, py)
        cubicTo(px + dx * c, py, px, py + dy * c, px, py + dy * r)
        lineTo(px, py + dy * ay)
    } else {
        moveTo(px, py + dy * ay)
        lineTo(px, py + dy * r)
        cubicTo(px, py + dy * c, px + dx * c, py, px + dx * r, py)
        lineTo(px + dx * ax, py)
    }
}

/**
 * Lavender "PAY" pill. A tap folds it into a round button where the card icon becomes a face-scan
 * frame, a scan line sweeps the face, then the pill widens again to say "COMPLETE" and resets.
 *
 * @param doneLabel Shown once the scan has finished.
 * @param autoPlay Replays pay → scan → complete by itself with a pause between runs.
 * @param onComplete Called when a run reaches the "complete" state.
 */
@Composable
fun PayScanButton(
    label: String = "PAY",
    doneLabel: String = "COMPLETE",
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    onComplete: () -> Unit = {},
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }
    val motion = remember { PayScanMotion() }
    val completed by rememberUpdatedState(onComplete)

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

    LaunchedEffect(autoPlay) {
        if (!autoPlay) return@LaunchedEffect
        while (true) {
            delay(AUTOPLAY_PAUSE_MS)
            motion.run { completed() }
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESS_SCALE else 1f,
        animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
        label = "press",
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                onClick()
                scope.launch { motion.run { completed() } }
            }
            .semantics { contentDescription = label }
            .size(WIDTH.dp, HEIGHT.dp),
    ) {
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                val f = PayScanFrame(motion.t, reducedMotion)
                surface(BLEED) { x, y, w, h ->
                    val cx = x + w / 2
                    val cy = y + h / 2
                    val left = cx - f.w / 2

                    drawIntoCanvas { motion.shadow.draw(it.nativeCanvas, left + 5f, y + 8f, f.w, h, h / 2) }
                    drawRoundRect(
                        brush = Brush.verticalGradient(PillBrushColors, startY = y, endY = y + h),
                        topLeft = Offset(left, y),
                        size = Size(f.w, h),
                        cornerRadius = CornerRadius(h / 2),
                    )

                    val line = Stroke(1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round)

                    // Card outline / scan frame: four corners whose arms meet (closed shape) or pull back (brackets).
                    val ix = cx + CARD_DX * (1f - f.morph)
                    val hw = CARD_HW + (FRAME_HALF - CARD_HW) * f.morph
                    val hh = CARD_HH + (FRAME_HALF - CARD_HH) * f.morph
                    val r = CARD_R + (FRAME_R - CARD_R) * f.morph
                    val ax = hw + (FRAME_ARM - hw) * f.bracket
                    val ay = hh + (FRAME_ARM - hh) * f.bracket
                    motion.corners[0].corner(ix + hw, cy - hh, -1f, 1f, ax, ay, r, true)
                    motion.corners[1].corner(ix + hw, cy + hh, -1f, -1f, ax, ay, r, false)
                    motion.corners[2].corner(ix - hw, cy + hh, 1f, -1f, ax, ay, r, true)
                    motion.corners[3].corner(ix - hw, cy - hh, 1f, 1f, ax, ay, r, false)
                    if (f.icon > 0f && f.outline > 0f) {
                        // The four corners are equally long, so trimming the outline is a per-corner trim.
                        motion.corners.forEachIndexed { index, corner ->
                            val part = (f.outline * 4f - index).coerceIn(0f, 1f)
                            if (part >= 1f) {
                                drawPath(corner, Ink, alpha = f.icon, style = line)
                            } else if (part > 0f) {
                                motion.measure.setPath(corner, false)
                                motion.trimmed.reset()
                                motion.measure.getSegment(0f, motion.measure.length * part, motion.trimmed, true)
                                drawPath(motion.trimmed, Ink, alpha = f.icon, style = line)
                            }
                        }
                    }

                    if (f.detail > 0f) {
                        val cardLeft = cx + CARD_DX - CARD_HW
                        val stripeY = cy - CARD_HH + 9.5f
                        drawLine(
                            Ink,
                            Offset(cardLeft, stripeY),
                            Offset(cardLeft + CARD_HW * 2f * f.detail, stripeY),
                            strokeWidth = 1.7f,
                            alpha = min(1f, f.detail * 3f),
                        )
                        drawRoundRect(
                            color = Ink,
                            topLeft = Offset(cx + CARD_DX + CARD_HW - 14.5f, cy + CARD_HH - 11f),
                            size = Size(9.5f, 6f),
                            cornerRadius = CornerRadius(1.8f),
                            style = Stroke(1.5f),
                            alpha = seg(f.detail, 0.5f, 1f),
                        )
                    }

                    val faceAlpha = f.face * f.icon
                    if (faceAlpha > 0f) {
                        val face = motion.scratch
                        face.reset()
                        face.moveTo(cx - 5.5f, cy - 4.5f)
                        face.lineTo(cx - 5.5f, cy - 0.9f)
                        face.moveTo(cx + 5.5f, cy - 4.5f)
                        face.lineTo(cx + 5.5f, cy - 0.9f)
                        face.moveTo(cx - 4f, cy + 3.6f)
                        face.quadraticTo(cx, cy + 7.6f, cx + 4f, cy + 3.6f)
                        scale(0.6f + 0.4f * f.face, pivot = Offset(cx, cy)) {
                            drawPath(face, Ink, alpha = faceAlpha, style = line)
                        }
                    }

                    if (f.scan > 0f) {
                        drawLine(
                            Ink,
                            Offset(cx - 10.5f, cy + f.scanY),
                            Offset(cx + 10.5f, cy + f.scanY),
                            strokeWidth = 1.7f,
                            cap = StrokeCap.Round,
                            alpha = f.scan,
                        )
                    }
                }
            }
        }
        // Labels are wiped in and out from their left edge.
        BasicText(
            text = label,
            style = LabelStyle,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = LABEL_LEFT.dp)
                .drawWithContent {
                    val reveal = PayScanFrame(motion.t, reducedMotion).pay
                    clipRect(right = size.width * reveal) { this@drawWithContent.drawContent() }
                },
        )
        BasicText(
            text = doneLabel,
            style = LabelStyle,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.Center)
                .drawWithContent {
                    val reveal = PayScanFrame(motion.t, reducedMotion).done
                    clipRect(right = size.width * reveal) { this@drawWithContent.drawContent() }
                },
        )
    }
}
