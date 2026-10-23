package com.ngocdevv.nativecook.blocks.progress.projectprogresscard

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.TAU
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.surface
import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * @param color Avatar fill.
 * @param ink Colour of the initials.
 */
class ProjectCollaborator(val initials: String, val color: Color, val ink: Color)

private val ProjectCardCollaborators = listOf(
    ProjectCollaborator("AM", Color(0xFFE7A273), Color(0xFF5A2E12)),
    ProjectCollaborator("RK", Color(0xFFD9D3C5), Color(0xFF4B4636)),
    ProjectCollaborator("JS", Color(0xFFC6D2DA), Color(0xFF2F3F4A)),
)

private const val WIDTH = 340f
private const val HEIGHT = 225f
private const val RADIUS = 32f

// The canvas overflows the card by this much on every side to fit its shadow.
private val BLEED = 40.dp

private const val BAR_X = 25.5f
private const val BAR_Y = 107f
private const val BAR_W = 289f
private const val BAR_H = 29.5f

private const val SPINNER_X = 32.5f
private const val SPINNER_Y = 26.5f
private const val SPOKES = 8

private const val AVATAR = 34f
private const val AVATAR_PITCH = 38.7f

// Value shown in the reference, and the autoPlay loop built around it (seconds).
private const val REST = 0.65f
private const val START = 0.35f
private const val FILL = 2.2f
private const val HOLD = 3.2f
private const val RESET = 0.55f
private const val LOOP = START + FILL + HOLD + RESET

// A moment in the loop where everything is at rest: used when nothing is playing.
private const val REST_T = START + FILL + 1f
private const val SWEEP_PERIOD = 2.8f
private const val SWEEP_TRAVEL = 1.15f
private const val SWEEP_W = 70f

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
private val NoFontPadding = PlatformTextStyle(includeFontPadding = false)

private fun cardText(size: Float, lineHeight: Float, weight: FontWeight, color: Color) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    lineHeightStyle = Centered,
    platformStyle = NoFontPadding,
)

private val TitleStyle = cardText(14.8f, 20f, FontWeight.Medium, Color(0xFFF4F4F4))
private val TagStyle = cardText(9.2f, 13f, FontWeight.Medium, Color(0xFFC9C9C9))
private val NumberStyle = cardText(31f, 38f, FontWeight.Medium, Color(0xFFF2F2F2))
    .copy(letterSpacing = (-1).sp, fontFeatureSettings = "tnum")
private val DueStyle = cardText(11.5f, 15f, FontWeight.SemiBold, Color(0xFFD6DFE1))
private val CollaboratorsStyle = cardText(11f, 15f, FontWeight.Medium, Color(0xFFB6B6B6))
private val InitialsStyle = cardText(11f, 14f, FontWeight.Bold, Color.Black).copy(letterSpacing = 0.2.sp)
private val DetailsLabelStyle = cardText(9.5f, 14f, FontWeight.Medium, Color.White)

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

private fun easeInOutCubic(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

// Overshoots a little before settling: avatars pop in like they are on a spring.
private fun easeOutBack(t: Float): Float {
    val c = 2.2f
    return 1f + (c + 1f) * (t - 1f).pow(3) + c * (t - 1f).pow(2)
}

/** Progress at [t] seconds into the autoPlay loop. */
private fun loopValue(t: Float) = when {
    t < START -> 0f
    t < START + FILL -> REST * easeInOutCubic((t - START) / FILL)
    t < LOOP - RESET -> REST
    else -> REST * (1f - easeInOutCubic((t - (LOOP - RESET)) / RESET))
}

/** 0…1: how busy the spinner is at [t] seconds into the loop (it only chases while the bar moves). */
private fun loopActivity(t: Float) = clamp01((t - START) / 0.25f) * clamp01((START + FILL + 0.1f - t) / 0.45f)

/** Scale of avatar [index]: they leave during the reset and pop back in one after another. */
private fun avatarScale(t: Float, index: Int) =
    if (t > LOOP - RESET) {
        1f - clamp01((t - (LOOP - RESET)) / (RESET * 0.7f))
    } else {
        easeOutBack(clamp01((t - 0.12f - 0.11f * index) / 0.5f))
    }

private fun fillWidth(p: Float) = BAR_H + (BAR_W - BAR_H) * clamp01(p)

/** Places the element by its leading edge and vertical centre inside the card. */
private fun Modifier.leadingAt(x: Dp, centerY: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(x.roundToPx(), centerY.roundToPx() - placeable.height / 2)
    }
}

/** Places the element by its centre inside the card. */
private fun Modifier.centerAt(x: Dp, y: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(x.roundToPx() - placeable.width / 2, y.roundToPx() - placeable.height / 2)
    }
}

private val ProjectFillColors = intArrayOf(
    Color(0xFFC0FE6E).toArgb(),
    Color(0xFF93FDA9).toArgb(),
    Color(0xFF6BFCDF).toArgb(),
    Color(0xFF6BFCDF).toArgb(),
)
private val ProjectFillStops = floatArrayOf(0f, 0.42f, 0.78f, 1f)

private class ProjectCardPaints {
    val shadow = SoftRoundRect(rgba(60, 70, 90, 0.3f), sigma = 18f)

    // The bar's own gradient with a soft edge: the glow under the fill.
    val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            maskFilter = BlurMaskFilter(sigmaToRadius(11f), BlurMaskFilter.Blur.NORMAL)
        }
    }
    private var glowX = Float.NaN

    fun glowAt(bx: Float): Paint {
        if (bx != glowX) {
            glowX = bx
            glow.shader = LinearGradient(
                bx, 0f, bx + BAR_W * 0.72f, 0f, ProjectFillColors, ProjectFillStops, Shader.TileMode.CLAMP,
            )
        }
        return glow
    }

    val arrow = Path().apply {
        moveTo(1f, 4.5f)
        lineTo(8f, 4.5f)
        moveTo(5f, 1.5f)
        lineTo(8f, 4.5f)
        lineTo(5f, 7.5f)
    }
}

/**
 * Dark project card: a spinner glyph, title and tag, a large percentage and a lime-to-mint pill
 * bar with the due date sitting on its track, then the collaborators and a "More details" pill.
 * The bar glows and breathes, a band of light sweeps along it, the spinner chases while the value
 * moves and the avatars pop back in each time the loop restarts.
 *
 * @param progress Controlled progress, 0…1. Leave null to show the reference's 65% (or the autoPlay loop).
 * @param autoPlay Fills from 0 to 65% by itself, holds, resets and starts over.
 */
@Composable
fun ProjectProgressCard(
    progress: Float? = null,
    autoPlay: Boolean = false,
    title: String = "Project Progress",
    tag: String = "Onboarding prototype",
    due: String = "Due July 28",
    collaborators: List<ProjectCollaborator> = ProjectCardCollaborators,
    detailsLabel: String = "More details",
    onDetailsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val controlled = progress != null
    val target = (progress ?: REST).coerceIn(0f, 1f)
    val auto = autoPlay && !controlled && !reducedMotion
    val looping by rememberUpdatedState(auto)

    // Seconds: `time` never stops (ambient effects), `clock` is the position in the autoPlay loop.
    val time = remember { mutableFloatStateOf(0f) }
    val clock = remember { mutableFloatStateOf(if (auto) 0f else REST_T) }
    var playing by remember { mutableStateOf(false) }
    val held = remember { Animatable(target) }
    // When a controlled value last changed, on the `time` axis.
    val movedAt = remember { mutableFloatStateOf(-10f) }

    LaunchedEffect(auto) {
        playing = auto
        clock.floatValue = if (auto) 0f else REST_T
    }

    LaunchedEffect(controlled, target) {
        if (!controlled || held.targetValue == target) return@LaunchedEffect
        movedAt.floatValue = time.floatValue
        held.animateTo(target, tween(if (reducedMotion) 200 else 700, easing = EaseOutStrong))
    }

    LaunchedEffect(reducedMotion) {
        if (reducedMotion) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = min((now - last) / 1e9f, 0.064f)
            last = now
            time.floatValue += dt
            if (!playing) continue
            var t = clock.floatValue + dt
            if (looping) {
                if (t >= LOOP) t -= LOOP
            } else if (t >= REST_T) {
                // A tapped, one-off run stops at the resting frame.
                t = REST_T
                playing = false
            }
            clock.floatValue = t
        }
    }

    // Read inside draw / layer lambdas so a frame only redraws, it does not recompose.
    val value = { if (controlled) held.value else loopValue(clock.floatValue) }
    val activity = {
        when {
            reducedMotion -> 0f
            !controlled -> loopActivity(clock.floatValue)
            else -> {
                val since = time.floatValue - movedAt.floatValue
                clamp01(since / 0.2f) * clamp01((0.9f - since) / 0.4f)
            }
        }
    }
    // The number is real text: recompose only when the whole percent changes.
    val percent by remember(controlled) {
        derivedStateOf { ((if (controlled) held.value else loopValue(clock.floatValue)) * 100f).roundToInt() }
    }

    val ambient = if (reducedMotion) 0f else 1f
    val paints = remember { ProjectCardPaints() }

    Box(
        modifier = modifier
            .size(WIDTH.dp, HEIGHT.dp)
            .pointerInput(controlled, reducedMotion) {
                detectTapGestures {
                    if (!controlled && !reducedMotion) {
                        clock.floatValue = 0f
                        playing = true
                    }
                }
            }
            .semantics(mergeDescendants = true) {
                contentDescription = "$title, $tag, $due"
                progressBarRangeInfo = ProgressBarRangeInfo(percent / 100f, 0f..1f)
            },
    ) {
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, _, _ ->
                    val p = value()
                    val t = time.floatValue

                    drawIntoCanvas { paints.shadow.draw(it.nativeCanvas, x + 6, y + 16, WIDTH - 12, HEIGHT - 8, RADIUS) }
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            0f to Color(0xFF525252),
                            0.06f to Color(0xFF3B3B3B),
                            0.16f to Color(0xFF353535),
                            0.5f to Color(0xFF313131),
                            0.93f to Color(0xFF1F1F1F),
                            0.985f to Color(0xFF242424),
                            1f to Color(0xFF3D3B3C),
                            startY = y,
                            endY = y + HEIGHT,
                        ),
                        topLeft = Offset(x, y),
                        size = Size(WIDTH, HEIGHT),
                        cornerRadius = CornerRadius(RADIUS),
                    )

                    // Spinner: a bright head travels clockwise and leaves a fading trail while the bar moves.
                    val busy = activity()
                    for (i in 0 until SPOKES) {
                        val a = i.toFloat() / SPOKES * TAU - PI.toFloat() / 2f
                        val phase = ((t * 1.25f - i.toFloat() / SPOKES) % 1f + 1f) % 1f
                        drawLine(
                            color = Color(0xFFF4F4F4),
                            start = Offset(x + SPINNER_X + 3f * cos(a), y + SPINNER_Y + 3f * sin(a)),
                            end = Offset(x + SPINNER_X + 8f * cos(a), y + SPINNER_Y + 8f * sin(a)),
                            strokeWidth = 1.5f,
                            cap = StrokeCap.Round,
                            alpha = 1f - busy * 0.78f * phase,
                        )
                    }

                    val bx = x + BAR_X
                    val by = y + BAR_Y
                    val width = fillWidth(p)
                    // The pill has a minimum length, so it fades in over the first few percent.
                    val fillAlpha = clamp01(p / 0.03f)
                    val pill = CornerRadius(BAR_H / 2)
                    val shading = Brush.horizontalGradient(
                        0f to Color(0xFFC0FE6E),
                        0.42f to Color(0xFF93FDA9),
                        0.78f to Color(0xFF6BFCDF),
                        1f to Color(0xFF6BFCDF),
                        startX = bx,
                        endX = bx + BAR_W * 0.72f,
                    )

                    // Glow first so the translucent track sits on top of it, as in the reference.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val glow = paints.glowAt(bx)
                        glow.alpha = (255f * fillAlpha * (0.84f + ambient * 0.14f * sin(t * 1.9f))).roundToInt().coerceIn(0, 255)
                        drawIntoCanvas {
                            it.nativeCanvas.drawRoundRect(bx, by, bx + width, by + BAR_H, BAR_H / 2, BAR_H / 2, glow)
                        }
                    }
                    drawRoundRect(rgba(118, 125, 128, 0.86f), Offset(bx, by), Size(BAR_W, BAR_H), pill)
                    drawRoundRect(
                        color = rgba(255, 255, 255, 0.1f),
                        topLeft = Offset(bx + 0.5f, by + 0.5f),
                        size = Size(BAR_W - 1f, BAR_H - 1f),
                        cornerRadius = CornerRadius(BAR_H / 2 - 0.5f),
                        style = Stroke(1f),
                    )
                    drawRoundRect(shading, Offset(bx, by), Size(width, BAR_H), pill, alpha = fillAlpha)

                    // A band of light crosses the fill every few seconds.
                    val phase = (t % SWEEP_PERIOD) / SWEEP_TRAVEL
                    if (ambient > 0f && phase < 1f) {
                        val sx = bx - SWEEP_W + (width + SWEEP_W) * phase
                        drawRoundRect(
                            brush = Brush.linearGradient(
                                0f to rgba(255, 255, 255, 0f),
                                0.5f to rgba(255, 255, 255, 0.55f),
                                1f to rgba(255, 255, 255, 0f),
                                start = Offset(sx, by),
                                end = Offset(sx + SWEEP_W, by + 12f),
                            ),
                            topLeft = Offset(bx, by),
                            size = Size(width, BAR_H),
                            cornerRadius = pill,
                            alpha = fillAlpha * sin(PI.toFloat() * phase),
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.leadingAt(25.5.dp, 50.5.dp).clearAndSetSemantics {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.5.dp),
        ) {
            BasicText(text = title, style = TitleStyle, maxLines = 1)
            Box(
                modifier = Modifier
                    .height(22.dp)
                    .background(rgba(255, 255, 255, 0.02f), CircleShape)
                    .border(1.dp, rgba(255, 255, 255, 0.09f), CircleShape)
                    .padding(horizontal = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(text = tag, style = TagStyle, maxLines = 1)
            }
        }

        Row(Modifier.leadingAt(25.5.dp, 82.5.dp).clearAndSetSemantics {}) {
            BasicText(text = percent.toString(), style = NumberStyle, modifier = Modifier.alignByBaseline())
            BasicText(
                text = "%",
                style = NumberStyle.copy(color = Color(0xFF8B8B8B)),
                modifier = Modifier.alignByBaseline(),
            )
        }

        val dueY = (BAR_Y + BAR_H / 2).dp
        BasicText(
            text = due,
            style = DueStyle,
            maxLines = 1,
            modifier = Modifier.centerAt(269.dp, dueY).clearAndSetSemantics {},
        )
        // The due date turns dark where the fill has reached it.
        BasicText(
            text = due,
            style = DueStyle.copy(color = Color(0xFF1F5A4C)),
            maxLines = 1,
            modifier = Modifier
                .centerAt(269.dp, dueY)
                .drawWithContent {
                    val p = value()
                    val edge = if (p <= 0f) 0f else (BAR_X + fillWidth(p)).dp.toPx()
                    val left = 269.dp.roundToPx() - size.width / 2f
                    clipRect(right = (edge - left).coerceIn(0f, size.width)) { this@drawWithContent.drawContent() }
                }
                .clearAndSetSemantics {},
        )

        BasicText(
            text = buildAnnotatedString {
                append("Collaborators ")
                withStyle(SpanStyle(color = Color(0xFF7C7C7C))) { append(collaborators.size.toString()) }
            },
            style = CollaboratorsStyle,
            maxLines = 1,
            modifier = Modifier.leadingAt(30.dp, 156.dp).clearAndSetSemantics {},
        )

        collaborators.forEachIndexed { index, person ->
            Box(
                modifier = Modifier
                    .centerAt((30f + AVATAR / 2 + AVATAR_PITCH * index).dp, 189.5.dp)
                    .size(AVATAR.dp)
                    .graphicsLayer {
                        val s = if (controlled || reducedMotion) 1f else avatarScale(clock.floatValue, index)
                        scaleX = s
                        scaleY = s
                        alpha = clamp01(s * 2f)
                    }
                    .background(Color(0xFF474747), RoundedCornerShape(11.dp))
                    .padding(2.dp)
                    .background(person.color, RoundedCornerShape(9.dp))
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                BasicText(text = person.initials, style = InitialsStyle.copy(color = person.ink))
            }
        }

        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        LaunchedEffect(pressed) {
            if (pressed) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
        val scale by animateFloatAsState(
            targetValue = if (pressed) PRESS_SCALE else 1f,
            animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
            label = "press",
        )
        Row(
            modifier = Modifier
                .offset(225.dp, 185.3.dp)
                .size(89.5.dp, 22.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onDetailsClick)
                .drawBehind {
                    val r = CornerRadius(size.height / 2)
                    drawRoundRect(
                        brush = Brush.verticalGradient(listOf(Color(0xFF2C3438), Color(0xFF1E1F20), Color(0xFF232425))),
                        cornerRadius = r,
                    )
                    // Glassy rim: cool light from the top left, a white glint at the bottom right.
                    val inset = 0.5.dp.toPx()
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            0f to rgba(120, 150, 170, 0.55f),
                            0.4f to rgba(120, 150, 170, 0.18f),
                            0.7f to rgba(255, 255, 255, 0.1f),
                            1f to rgba(255, 255, 255, 0.6f),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height),
                        ),
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2, size.height - inset * 2),
                        cornerRadius = CornerRadius(size.height / 2 - inset),
                        style = Stroke(1.dp.toPx()),
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            BasicText(text = detailsLabel, style = DetailsLabelStyle, maxLines = 1)
            Canvas(Modifier.padding(start = 3.dp).size(9.dp)) {
                // The arrow is authored in dp.
                scale(density, density, pivot = Offset.Zero) {
                    drawPath(
                        path = paints.arrow,
                        color = Color.White,
                        style = Stroke(1.1f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            }
        }
    }
}
