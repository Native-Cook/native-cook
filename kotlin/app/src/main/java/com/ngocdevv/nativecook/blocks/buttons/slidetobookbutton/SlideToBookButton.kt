package com.ngocdevv.nativecook.blocks.buttons.slidetobookbutton

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.TAU
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.surface
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val WIDTH = 240f
private const val HEIGHT = 64f

// The canvas overflows the button by this much on every side so its edge is not clipped.
private val BLEED = 4.dp
private const val INSET = 10f
private const val KNOB_W = 72f
private const val TRAVEL = WIDTH - INSET * 2 - KNOB_W

// How far the resting knob stretches while it "breathes" to hint at the slide.
private const val NUDGE = 8f

// The label sits in the space to the right of the resting knob.
private const val LABEL_LEFT = INSET + KNOB_W + 4f
private const val LABEL_W = WIDTH - 14f - LABEL_LEFT
private const val HOLD_MS = 1700L
private const val AUTOPLAY_PAUSE_MS = 1700L

private val Ink = Color(0xFF0C0C0D)
private val TagFill = Color(0xFFDCFBD3)
private val Gloss = arrayOf(
    0f to Color.White.copy(alpha = 0.8f),
    0.35f to Color.White.copy(alpha = 0.35f),
    1f to Color.White.copy(alpha = 0f),
)
private val Lime = arrayOf(
    0f to Color(0xFF7DFE3F),
    0.6f to Color(0xFFA2FF24),
    1f to Color(0xFFA8FF20),
)
private val Shine = arrayOf(
    0f to Color.White.copy(alpha = 0f),
    0.5f to Color.White.copy(alpha = 0.7f),
    1f to Color.White.copy(alpha = 0f),
)
private val LabelStyle = TextStyle(
    color = Color(0xFF8C8C8C),
    fontSize = 19.sp,
    fontWeight = FontWeight.Normal,
    letterSpacing = 0.1.sp,
)
private val TagStyle = TextStyle(color = Color(0xFF3F6B35), fontSize = 10.sp, fontWeight = FontWeight.Medium)

// Dotted arrow: the head stays lit, the three tail dots light up one after the other.
private const val DOT = 3.2f
private const val DOT_R = 1.05f
private val ArrowHead = floatArrayOf(2f, 0f, 1f, 0f, 1f, -1f, 0f, -2f, 1f, 1f, 0f, 2f)
private val ArrowTail = floatArrayOf(-2f, -1f, 0f)

// Phone handset outline and its two signal arcs, in dp around the icon's centre.
private val Handset = floatArrayOf(
    4.41f, 6.18f, 3.6f, 6.11f, 2.79f, 5.98f, 1.99f, 5.79f, 1.21f, 5.54f, 0.44f, 5.23f, -0.29f, 4.87f, -1.0f, 4.46f,
    -1.68f, 3.99f, -2.32f, 3.48f, -2.92f, 2.92f, -3.48f, 2.32f, -3.99f, 1.68f, -4.46f, 1.0f, -4.87f, 0.29f, -5.23f,
    -0.44f, -5.54f, -1.21f, -5.79f, -1.99f, -5.98f, -2.79f, -6.11f, -3.6f, -6.18f, -4.41f, -1.29f, -4.67f, -1.25f,
    -4.21f, -1.18f, -3.75f, -1.07f, -3.31f, -0.93f, -2.87f, -1.42f, -2.14f, -2.32f, -1.08f, -2.43f, -0.28f, -2.06f,
    0.25f, -1.66f, 0.75f, -1.22f, 1.22f, -0.75f, 1.66f, -0.25f, 2.06f, 0.28f, 2.43f, 1.08f, 2.32f, 2.14f, 1.42f,
    2.87f, 0.93f, 3.31f, 1.07f, 3.75f, 1.18f, 4.21f, 1.25f, 4.67f, 1.29f,
)
private val SignalNear = floatArrayOf(
    2.07f, -4.97f, 2.61f, -4.85f, 3.12f, -4.64f, 3.59f, -4.36f, 4.0f, -4.0f, 4.36f, -3.59f, 4.64f, -3.12f, 4.85f,
    -2.61f, 4.97f, -2.07f,
)
private val SignalFar = floatArrayOf(
    2.49f, -7.94f, 3.5f, -7.71f, 4.46f, -7.33f, 5.34f, -6.79f, 6.13f, -6.13f, 6.79f, -5.34f, 7.33f, -4.46f, 7.71f,
    -3.5f, 7.94f, -2.49f,
)

private fun Path.polyline(points: FloatArray, close: Boolean) {
    for (i in points.indices step 2) {
        if (i == 0) moveTo(points[i], points[i + 1]) else lineTo(points[i], points[i + 1])
    }
    if (close) close()
}

private val PhonePath = Path().apply {
    polyline(Handset, close = true)
    polyline(SignalNear, close = false)
    polyline(SignalFar, close = false)
}

private fun seg(t: Float, a: Float, b: Float) = min(1f, max(0f, (t - a) / (b - a)))

/** Width of the lime knob for a stretch [p] (0 resting, 1 filling the pill) at time [t]. */
private fun knobWidth(p: Float, t: Float): Float {
    val breathe = 0.5f - 0.5f * cos(TAU * t / 1.8f)
    return KNOB_W + TRAVEL * p + NUDGE * breathe * (1f - seg(p, 0f, 0.2f))
}

private class SlideToBookMotion {
    val stretch = Animatable(0f)
    val tag = Animatable(0f)
    var busy = false

    /** One booking. The stretch is the button's state change, so it is not skipped for reduced motion. */
    suspend fun confirm() {
        if (busy) return
        busy = true
        try {
            coroutineScope {
                launch { stretch.animateTo(1f, tween(420, easing = EaseOutStrong)) }
                delay(220)
                launch { tag.animateTo(1f, tween(260, easing = EaseOutStrong)) }
                delay(HOLD_MS)
                launch { tag.animateTo(0f, tween(200)) }
                delay(150)
                launch { stretch.animateTo(0f, tween(480, easing = FastOutSlowInEasing)) }
                delay(560)
            }
        } finally {
            // If the run was cancelled half-way, drop back to the idle state.
            withContext(NonCancellable) {
                stretch.snapTo(0f)
                tag.snapTo(0f)
            }
            busy = false
        }
    }
}

/**
 * Black "Book a call" pill with a lime knob. Tap it, or slide the knob to the right: the knob
 * stretches across the whole pill, a phone icon replaces the arrow and a small "Call booked" tag
 * pops up above before everything slides back.
 *
 * @param doneLabel Shown in the tag above the button once the call is booked.
 * @param onClick Called when the booking is confirmed, by a tap or by sliding the knob across.
 * @param autoPlay Replays the booking by itself with a pause between runs.
 */
@Composable
fun SlideToBookButton(
    label: String = "Book a call",
    doneLabel: String = "Call booked",
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val dpScale = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }
    val motion = remember { SlideToBookMotion() }
    val confirmed by rememberUpdatedState(onClick)
    var clock by remember { mutableFloatStateOf(0f) }
    var dragged by remember { mutableFloatStateOf(0f) }

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

    // The knob breathes, the arrow marches and the label shines all the time; reduced motion stops them.
    LaunchedEffect(reducedMotion) {
        if (reducedMotion) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { clock = (it - start) / 1e9f }
        }
    }

    LaunchedEffect(autoPlay) {
        if (!autoPlay) return@LaunchedEffect
        while (true) {
            delay(AUTOPLAY_PAUSE_MS)
            motion.confirm()
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESS_SCALE else 1f,
        animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
        label = "press",
    )

    // A horizontal slide moves the knob; it confirms past half-way, otherwise the knob springs back.
    val slide = rememberDraggableState { delta ->
        if (!motion.busy) {
            dragged += delta / dpScale
            scope.launch { motion.stretch.snapTo((dragged / TRAVEL).coerceIn(0f, 1f)) }
        }
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                confirmed()
                scope.launch { motion.confirm() }
            }
            .draggable(
                state = slide,
                orientation = Orientation.Horizontal,
                onDragStarted = { dragged = 0f },
                onDragStopped = {
                    if (!motion.busy) {
                        if (abs(dragged) > 0f && dragged / TRAVEL > 0.5f) {
                            confirmed()
                            motion.confirm()
                        } else {
                            motion.stretch.animateTo(0f, tween(320, easing = EaseOutStrong))
                        }
                    }
                },
            )
            .semantics { contentDescription = label }
            .size(WIDTH.dp, HEIGHT.dp),
    ) {
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.matchParentSize()) {
                val p = motion.stretch.value
                surface(BLEED) { x, y, w, h ->
                    drawRoundRect(Ink, Offset(x, y), Size(w, h), CornerRadius(h / 2))
                    // Glossy edge: a thin light line following the top of the pill.
                    drawRoundRect(
                        brush = Brush.verticalGradient(*Gloss, startY = y, endY = y + h * 0.4f),
                        topLeft = Offset(x + 3.5f, y + 3.5f),
                        size = Size(w - 7f, h - 7f),
                        cornerRadius = CornerRadius(h / 2 - 3.5f),
                        style = Stroke(1f),
                    )

                    val knob = knobWidth(p, clock)
                    val knobH = h - INSET * 2
                    drawRoundRect(
                        brush = Brush.horizontalGradient(*Lime, startX = x + INSET, endX = x + INSET + knob),
                        topLeft = Offset(x + INSET, y + INSET),
                        size = Size(knob, knobH),
                        cornerRadius = CornerRadius(knobH / 2),
                    )

                    val cx = x + INSET + knob / 2
                    val cy = y + h / 2
                    val arrow = 1f - seg(p, 0.05f, 0.5f)
                    if (arrow > 0f) {
                        for (i in ArrowHead.indices step 2) {
                            drawCircle(Ink, DOT_R, Offset(cx + ArrowHead[i] * DOT, cy + ArrowHead[i + 1] * DOT), alpha = arrow)
                        }
                        ArrowTail.forEachIndexed { index, dx ->
                            val lit = 0.3f + 0.7f * (0.5f + 0.5f * sin(TAU * (clock / 0.9f - index * 0.22f)))
                            drawCircle(Ink, DOT_R, Offset(cx + dx * DOT, cy), alpha = lit * arrow)
                        }
                    }

                    val phone = seg(p, 0.4f, 0.85f)
                    if (phone > 0f) {
                        translate(cx - 1f, cy + 1f) {
                            scale(1.1f * (0.7f + 0.3f * seg(p, 0.4f, 1f)), pivot = Offset.Zero) {
                                drawPath(
                                    PhonePath,
                                    Ink,
                                    alpha = phone,
                                    style = Stroke(1.35f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                                )
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .offset(x = LABEL_LEFT.dp)
                .size(LABEL_W.dp, HEIGHT.dp)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    // The stretching knob wipes the label away from the left.
                    val cover = max(0f, INSET + knobWidth(motion.stretch.value, clock) + 2f - LABEL_LEFT) * density
                    clipRect(left = cover) {
                        this@drawWithContent.drawContent()
                        if (!reducedMotion) {
                            // A band of brighter text sweeping across the label.
                            val centre = (-40f + (LABEL_W + 80f) * seg(clock % 2.6f, 0.3f, 1.3f)) * density
                            val half = 32f * density
                            drawRect(
                                brush = Brush.horizontalGradient(*Shine, startX = centre - half, endX = centre + half),
                                topLeft = Offset(centre - half, 0f),
                                size = Size(half * 2, size.height),
                                blendMode = BlendMode.SrcAtop,
                            )
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(label, style = LabelStyle, maxLines = 1)
        }

        // "Call booked" tag above the pill, with a small tail pointing down at it.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .wrapContentWidth(unbounded = true)
                .offset(y = (-26).dp)
                .graphicsLayer {
                    val shown = motion.tag.value
                    alpha = shown
                    scaleX = 0.9f + 0.1f * shown
                    scaleY = 0.9f + 0.1f * shown
                    translationY = (1f - shown) * 6.dp.toPx()
                }
                .drawBehind {
                    val tail = 8.dp.toPx()
                    rotate(45f, pivot = Offset(size.width / 2, size.height - 1.dp.toPx())) {
                        drawRoundRect(
                            TagFill,
                            topLeft = Offset(size.width / 2 - tail / 2, size.height - 1.dp.toPx() - tail / 2),
                            size = Size(tail, tail),
                            cornerRadius = CornerRadius(1.5.dp.toPx()),
                        )
                    }
                    drawRoundRect(TagFill, cornerRadius = CornerRadius(8.dp.toPx()))
                }
                .height(21.dp)
                .padding(horizontal = 9.dp)
                .wrapContentHeight(),
        ) {
            BasicText(doneLabel, style = TagStyle, maxLines = 1)
        }
    }
}
