package com.ngocdevv.nativecook.blocks.progress.storyprogressbars

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.surface
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * @param action Label of the call-to-action button.
 */
class ProgressStory(val title: String, val description: String, val action: String)

private val DefaultStories = listOf(
    ProgressStory(
        "Workspaces",
        "Bring every project, doc and thread together in one shared space.",
        "Create Workspace →",
    ),
    ProgressStory(
        "Integrations",
        "Integrate your apps to create, automate, and command in one place.",
        "Connect Integrations →",
    ),
    ProgressStory(
        "Automations",
        "Turn the steps you repeat into flows that run while you sleep.",
        "Build Automation →",
    ),
    ProgressStory(
        "Insights",
        "See what shipped, what stalled and what needs you next.",
        "View Insights →",
    ),
)

private const val WIDTH = 310f
private const val CARD_H = 280f
private const val HEIGHT = 330f
private const val RADIUS = 27.5f

// The canvas overflows the card by this much on every side to fit its shadow.
private val BLEED = 24.dp

private const val COUNT = 4
private const val SEG_X = 21.5f
private const val SEG_Y = 20.5f
private const val SEG_W = 63f
private const val SEG_PITCH = 68f
private const val SEG_H = 1.5f

private const val HORIZON_Y = 131.5f
private const val HORIZON_X0 = 52f
private const val HORIZON_X1 = 260f

private const val ACTION_X = 15.8f
private const val ACTION_Y = 228f
private const val ACTION_W = 278.4f
private const val ACTION_H = 36.5f
private const val PAUSE = 39f
private const val PAUSE_Y = 290.5f

// Seconds each story stays up, and how long the hand-over to the next one takes.
private const val STORY = 3.4f
private const val TRANSITION = 0.7f
private const val LOOP = STORY * COUNT

// Position shown in the reference: the second story with its bar full.
private const val REST = 0.5f

/** Where a chip sits in one story. */
private class ChipSpot(val x: Float, val y: Float, val tilt: Float, val scale: Float)

/** One of the four floating app chips: its size, face gradient and place in every story. */
private class StoryChip(val size: Float, top: Long, bottom: Long, val at: List<ChipSpot>) {
    val face = Brush.verticalGradient(listOf(Color(top), Color(bottom)), startY = -size / 2f, endY = size / 2f)
}

private val StoryChips = listOf(
    StoryChip(
        29f, 0xFF3B3B3B, 0xFF1F1F1F,
        listOf(ChipSpot(88f, 92f, -8f, 0.85f), ChipSpot(51.8f, 80.8f, -14f, 1f), ChipSpot(70f, 64f, 10f, 0.9f), ChipSpot(60f, 98f, -4f, 0.8f)),
    ),
    StoryChip(
        19.5f, 0xFF9A3CF0, 0xFF5A17A6,
        listOf(ChipSpot(128f, 104f, 6f, 1f), ChipSpot(104.8f, 96.8f, -10f, 1f), ChipSpot(140f, 84f, 0f, 1.35f), ChipSpot(118f, 62f, 12f, 1.05f)),
    ),
    StoryChip(
        18f, 0xFFF7F4ED, 0xFFE2DED3,
        listOf(ChipSpot(170f, 78f, -6f, 1f), ChipSpot(177.5f, 67f, 8f, 1f), ChipSpot(206f, 98f, -10f, 1.05f), ChipSpot(160f, 92f, 4f, 1.4f)),
    ),
    StoryChip(
        28f, 0xFFFFFFFF, 0xFFE8E8E8,
        listOf(ChipSpot(214f, 96f, 10f, 0.85f), ChipSpot(241.5f, 71.3f, 6f, 1f), ChipSpot(252f, 60f, -8f, 0.8f), ChipSpot(236f, 84f, 14f, 0.95f)),
    ),
)

// Glow colour of each story.
private val StoryGlows = arrayOf(
    floatArrayOf(84f, 136f, 255f),
    floatArrayOf(140f, 92f, 255f),
    floatArrayOf(255f, 92f, 176f),
    floatArrayOf(58f, 214f, 196f),
)

// Stars: x, y, radius.
private val StoryStars = arrayOf(
    floatArrayOf(98.5f, 62.2f, 0.8f),
    floatArrayOf(72f, 106.5f, 0.5f),
    floatArrayOf(192.5f, 83.5f, 0.8f),
    floatArrayOf(202.5f, 63.2f, 0.4f),
    floatArrayOf(266.2f, 114.8f, 0.9f),
    floatArrayOf(280f, 81.2f, 0.4f),
    floatArrayOf(41f, 114f, 0.6f),
    floatArrayOf(150f, 96f, 0.5f),
)

// Beams: x, top, bottom.
private val StoryBeams = arrayOf(
    floatArrayOf(131.5f, 86f, 129f),
    floatArrayOf(219f, 94f, 129f),
    floatArrayOf(24f, 100f, 130f),
    floatArrayOf(272f, 72f, 118f),
)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
private val NoFontPadding = PlatformTextStyle(includeFontPadding = false)

private fun storyText(size: Float, lineHeight: Float, weight: FontWeight, color: Color) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = 0.1.sp,
    lineHeightStyle = Centered,
    platformStyle = NoFontPadding,
)

private val TitleStyle = storyText(16f, 22f, FontWeight.Medium, Color(0xFFF5F5F5))
private val DescriptionStyle = storyText(13.5f, 20f, FontWeight.Normal, rgba(255, 255, 255, 0.56f))
private val ActionStyle = storyText(14f, 20f, FontWeight.Medium, Color(0xFFF5F5F5))

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

private fun easeOutCubic(t: Float) = 1f - (1f - t).pow(3)

// Overshoots a little before settling: the chips land in their new places like on a spring.
private fun easeOutBack(t: Float): Float {
    val c = 1.9f
    return 1f + (c + 1f) * (t - 1f).pow(3) + c * (t - 1f).pow(2)
}

/** Story on screen for a position [v] (0…1 across all stories). */
private fun storyAt(v: Float) = (ceil(v * COUNT - 1e-4f).toInt() - 1).coerceIn(0, COUNT - 1)

/**
 * 0…1: how far the story on screen has taken over from the previous one. Without [wraps] the
 * first story has nothing to take over from.
 */
private fun handOver(v: Float, wraps: Boolean): Float {
    val k = storyAt(v)
    if (k == 0 && !wraps) return 1f
    return clamp01((v * COUNT - k) * STORY / TRANSITION)
}

/** Opacity of story [index]'s copy: the old copy is gone before the new one shows. */
private fun copyAlpha(index: Int, story: Int, hand: Float) = when (index) {
    story -> clamp01((hand - 0.45f) / 0.55f)
    (story + COUNT - 1) % COUNT -> 1f - clamp01(hand / 0.4f)
    else -> 0f
}

private class StoryBarsPaints {
    val shadow = SoftRoundRect(rgba(0, 0, 0, 0.55f), sigma = 12f)
    val flash = SoftRoundRect(Color.White, sigma = 3f)
    val chipShadow = SoftRoundRect(rgba(0, 0, 0, 0.5f), sigma = 4f)
    val glint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.White.toArgb()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            maskFilter = BlurMaskFilter(sigmaToRadius(2.2f), BlurMaskFilter.Blur.NORMAL)
        }
    }
    val spark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.White.toArgb()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            maskFilter = BlurMaskFilter(sigmaToRadius(3f), BlurMaskFilter.Blur.SOLID)
        }
    }
    val warm = Brush.radialGradient(
        listOf(rgba(255, 170, 120, 0.2f), rgba(255, 170, 120, 0f)),
        center = Offset.Zero,
        radius = 1f,
    )
    val moon = Brush.verticalGradient(listOf(Color.White, Color(0xFFB9B9B9)), startY = -7.4f, endY = 7.4f)
    val bars = Brush.verticalGradient(listOf(Color.White, Color(0xFFC9C9C9)), startY = -5.3f, endY = 5.3f)
    val pauseDisc = Path().apply { addOval(Rect(0f, 0f, PAUSE, PAUSE)) }
    val triangle = Path().apply {
        moveTo(-3.4f, -5.5f)
        lineTo(5.6f, 0f)
        lineTo(-3.4f, 5.5f)
        close()
    }
}

/** One app chip around its own centre, with a generic glyph: a crescent, three bars, a note or four dots. */
private fun DrawScope.drawStoryChip(paints: StoryBarsPaints, index: Int, chip: StoryChip) {
    val s = chip.size
    val half = s / 2f
    val r = s * 0.27f
    drawIntoCanvas { paints.chipShadow.draw(it.nativeCanvas, -half, -half + 3f, s, s, r) }
    drawRoundRect(chip.face, Offset(-half, -half), Size(s, s), CornerRadius(r))
    drawRoundRect(
        color = rgba(255, 255, 255, 0.2f),
        topLeft = Offset(-half + 0.4f, -half + 0.4f),
        size = Size(s - 0.8f, s - 0.8f),
        cornerRadius = CornerRadius(r - 0.4f),
        style = Stroke(0.8f),
    )
    when (index) {
        0 -> {
            drawCircle(paints.moon, 7.4f, Offset.Zero)
            drawCircle(Color(0xFF2B2B2B), 6f, Offset(3.6f, -3.2f))
        }
        1 -> {
            drawRoundRect(Color.White, Offset(-4.6f, -4f), Size(9.2f, 1.7f), CornerRadius(0.85f))
            drawRoundRect(Color.White, Offset(-4.6f, -0.85f), Size(9.2f, 1.7f), CornerRadius(0.85f))
            drawRoundRect(Color.White, Offset(-4.6f, 2.3f), Size(5.8f, 1.7f), CornerRadius(0.85f))
        }
        2 -> {
            drawRoundRect(Color(0xFFF5B301), Offset(-4f, -5.2f), Size(8f, 10.4f), CornerRadius(1.7f))
            drawRect(rgba(255, 255, 255, 0.92f), Offset(-2.1f, -1.7f), Size(4.2f, 1.1f))
            drawRect(rgba(255, 255, 255, 0.92f), Offset(-2.1f, 0.8f), Size(4.2f, 1.1f))
        }
        else -> {
            drawCircle(Color(0xFF4F9DFF), 2.7f, Offset(-3.8f, -3.8f))
            drawCircle(Color(0xFF34C98A), 2.7f, Offset(3.8f, -3.8f))
            drawCircle(Color(0xFFFF5D7A), 2.7f, Offset(-3.8f, 3.8f))
            drawCircle(Color(0xFFFFC043), 2.7f, Offset(3.8f, 3.8f))
        }
    }
}

/**
 * Dark onboarding card with story-style progress lines across the top, floating app chips over a
 * glowing horizon, a title, a description and a call-to-action, plus a round pause button. Each
 * bar that fills hands the card over to its next story: the copy rises in, the chips fly to a
 * new arrangement and land with a little overshoot, the glow changes colour and a spark runs
 * along the horizon. The pause button really pauses and turns into a play button.
 *
 * @param progress Controlled position, 0…1 across all four stories. Leave null to show the reference (or the autoPlay loop).
 * @param autoPlay Plays the stories one after another, like a story viewer, and starts over after the last.
 * @param stories Exactly four stories; the second one is the reference's.
 * @param onActionClick Called with the index of the story on screen.
 */
@Composable
fun StoryProgressBars(
    progress: Float? = null,
    autoPlay: Boolean = false,
    stories: List<ProgressStory> = DefaultStories,
    onActionClick: (Int) -> Unit = {},
    onPausedChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val controlled = progress != null
    val target = (progress ?: REST).coerceIn(0f, 1f)
    val auto = autoPlay && !controlled && !reducedMotion
    val playing by rememberUpdatedState(auto)
    var paused by remember { mutableStateOf(false) }
    val holding by rememberUpdatedState(paused)

    // Seconds: `time` never stops (ambient effects), `clock` is the position in the story loop.
    val time = remember { mutableFloatStateOf(0f) }
    val clock = remember { mutableFloatStateOf(if (auto) 0f else REST * LOOP) }
    val held = remember { Animatable(target) }
    // 0 = pause glyph, 1 = play glyph.
    val morph = remember { Animatable(0f) }

    LaunchedEffect(auto) {
        clock.floatValue = if (auto) 0f else REST * LOOP
    }

    LaunchedEffect(controlled, target) {
        if (!controlled || held.targetValue == target) return@LaunchedEffect
        held.animateTo(target, tween(if (reducedMotion) 200 else 700, easing = EaseOutStrong))
    }

    LaunchedEffect(paused) {
        morph.animateTo(if (paused) 1f else 0f, tween(240, easing = EaseOutStrong))
    }

    LaunchedEffect(reducedMotion) {
        if (reducedMotion) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = min((now - last) / 1e9f, 0.064f)
            last = now
            time.floatValue += dt
            // The pause button holds the story clock; the scene keeps floating.
            if (!playing || holding) continue
            var t = clock.floatValue + dt
            if (t >= LOOP) t -= LOOP
            clock.floatValue = t
        }
    }

    // Read inside draw / layer lambdas so a frame only redraws, it does not recompose.
    val value = { clamp01(if (controlled) held.value else clock.floatValue / LOOP) }
    // Only the story index recomposes, for the labels read by assistive technology.
    val story by remember(controlled) {
        derivedStateOf { storyAt(clamp01(if (controlled) held.value else clock.floatValue / LOOP)) }
    }
    val current = stories.getOrElse(story) { stories[0] }

    val ambient = if (reducedMotion) 0f else 1f
    val paints = remember { StoryBarsPaints() }

    Box(modifier = modifier.size(WIDTH.dp, HEIGHT.dp)) {
        Box(
            modifier = Modifier
                .size(WIDTH.dp, CARD_H.dp)
                // Tapping the card skips to the next story, like a story viewer.
                .pointerInput(auto) {
                    detectTapGestures {
                        if (auto) {
                            clock.floatValue = ((floor(clock.floatValue / STORY + 1e-4f) + 1f) * STORY) % LOOP
                        }
                    }
                }
                .semantics {
                    contentDescription = "${current.title}. ${current.description}"
                    stateDescription = "Story ${story + 1} of $COUNT"
                    progressBarRangeInfo = ProgressBarRangeInfo((story + 1f) / COUNT, 0f..1f, COUNT - 1)
                },
        ) {
            Box(Modifier.matchParentSize().bleed(BLEED)) {
                Canvas(Modifier.fillMaxSize()) {
                    surface(BLEED) { x, y, _, _ ->
                        val v = value()
                        val t = time.floatValue
                        val k = storyAt(v)
                        val previous = (k + COUNT - 1) % COUNT
                        val hand = handOver(v, auto)
                        translate(x, y) {
                            drawIntoCanvas { paints.shadow.draw(it.nativeCanvas, 6f, 12f, WIDTH - 12f, CARD_H - 8f, RADIUS) }
                            drawRoundRect(
                                brush = Brush.verticalGradient(listOf(Color(0xFF1B1B1B), Color(0xFF141414)), startY = 0f, endY = CARD_H),
                                topLeft = Offset.Zero,
                                size = Size(WIDTH, CARD_H),
                                cornerRadius = CornerRadius(RADIUS),
                            )
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    listOf(rgba(255, 255, 255, 0.2f), rgba(255, 255, 255, 0.07f)),
                                    startY = 0f,
                                    endY = CARD_H,
                                ),
                                topLeft = Offset(0.5f, 0.5f),
                                size = Size(WIDTH - 1f, CARD_H - 1f),
                                cornerRadius = CornerRadius(RADIUS - 0.5f),
                                style = Stroke(1f),
                            )

                            // Story bars.
                            val pill = CornerRadius(SEG_H / 2f)
                            for (index in 0 until COUNT) {
                                val sx = SEG_X + index * SEG_PITCH
                                val filled = SEG_W * clamp01(v * COUNT - index)
                                drawRoundRect(Color.White, Offset(sx, SEG_Y), Size(SEG_W, SEG_H), pill, alpha = 0.2f)
                                // When the set starts again the old bars fade out.
                                if (auto && k == 0) {
                                    drawRoundRect(Color.White, Offset(sx, SEG_Y), Size(SEG_W, SEG_H), pill, alpha = 1f - hand)
                                }
                                // A finished bar flashes as its story hands over.
                                if (k == index + 1 && hand < 1f) {
                                    drawIntoCanvas {
                                        paints.flash.draw(it.nativeCanvas, sx, SEG_Y - 1f, SEG_W, SEG_H + 2f, 2f, 0.9f * (1f - hand))
                                    }
                                }
                                drawRoundRect(Color.White, Offset(sx, SEG_Y), Size(filled, SEG_H), pill)
                                // A glint rides the head of the bar that is filling; it fades out as the bar completes.
                                val glint = if (k == index) clamp01(filled / 3f) * clamp01((SEG_W - filled) / 2f) else 0f
                                if (glint > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    paints.glint.alpha = (255f * glint).roundToInt().coerceIn(0, 255)
                                    drawIntoCanvas { it.nativeCanvas.drawCircle(sx + filled, SEG_Y + SEG_H / 2f, 2.2f, paints.glint) }
                                }
                            }

                            // Scene: faint beams and stars over a glowing horizon.
                            val beam = Brush.verticalGradient(
                                listOf(rgba(255, 255, 255, 0f), rgba(255, 255, 255, 0.13f), rgba(255, 255, 255, 0.04f)),
                                startY = 70f,
                                endY = 130f,
                            )
                            for (b in StoryBeams) drawLine(beam, Offset(b[0], b[1]), Offset(b[0], b[2]), strokeWidth = 0.6f)
                            StoryStars.forEachIndexed { index, star ->
                                val twinkle = if (index % 2 == 0) {
                                    0.62f + ambient * 0.38f * sin(t * 2.3f)
                                } else {
                                    0.62f + ambient * 0.38f * sin(t * 1.7f + 2.1f)
                                }
                                drawCircle(Color.White, star[2], Offset(star[0], star[1]), alpha = 0.9f * twinkle)
                            }

                            // The glow under the horizon takes each story's colour and breathes.
                            val eased = easeOutCubic(hand)
                            val from = StoryGlows[previous]
                            val to = StoryGlows[k]
                            fun glow(alpha: Float) = rgba(
                                (from[0] + (to[0] - from[0]) * eased).roundToInt(),
                                (from[1] + (to[1] - from[1]) * eased).roundToInt(),
                                (from[2] + (to[2] - from[2]) * eased).roundToInt(),
                                alpha,
                            )
                            val middle = (HORIZON_X0 + HORIZON_X1) / 2f
                            val breath = 0.86f + ambient * 0.14f * sin(t * 1.4f)
                            clipRect(0f, HORIZON_Y - 40f, WIDTH, HORIZON_Y) {
                                withTransform({
                                    translate(middle, HORIZON_Y)
                                    scale(112f, 26f, pivot = Offset.Zero)
                                }) {
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            listOf(glow(0.34f), glow(0.12f), glow(0f)),
                                            center = Offset.Zero,
                                            radius = 1f,
                                        ),
                                        radius = 1f,
                                        center = Offset.Zero,
                                        alpha = breath,
                                    )
                                }
                                withTransform({
                                    translate(middle, HORIZON_Y)
                                    scale(46f, 11f, pivot = Offset.Zero)
                                }) {
                                    drawCircle(paints.warm, radius = 1f, center = Offset.Zero, alpha = breath)
                                }
                            }
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    0f to glow(0f),
                                    0.3f to glow(0.9f),
                                    0.5f to rgba(255, 236, 244, 1f),
                                    0.7f to glow(0.9f),
                                    1f to glow(0f),
                                    startX = HORIZON_X0,
                                    endX = HORIZON_X1,
                                ),
                                topLeft = Offset(HORIZON_X0, HORIZON_Y - 0.4f),
                                size = Size(HORIZON_X1 - HORIZON_X0, 0.8f),
                            )
                            // A spark runs along the horizon each time a story takes over.
                            if (hand < 1f) {
                                paints.spark.alpha = (255f * sin(PI.toFloat() * hand)).roundToInt().coerceIn(0, 255)
                                drawIntoCanvas {
                                    it.nativeCanvas.drawCircle(HORIZON_X0 + (HORIZON_X1 - HORIZON_X0) * eased, HORIZON_Y, 2.4f, paints.spark)
                                }
                            }

                            // The chips fly to a new arrangement for every story, and bob gently where they are.
                            val back = easeOutBack(hand)
                            StoryChips.forEachIndexed { index, chip ->
                                val a = chip.at[previous]
                                val b = chip.at[k]
                                val size = a.scale + (b.scale - a.scale) * back
                                withTransform({
                                    translate(
                                        a.x + (b.x - a.x) * back + ambient * 1.2f * sin(t * 0.6f + index),
                                        a.y + (b.y - a.y) * back + ambient * 2.4f * sin(t * 0.9f + index * 1.7f),
                                    )
                                    rotate(a.tilt + (b.tilt - a.tilt) * back + ambient * 2f * sin(t * 0.7f + index * 2.3f), pivot = Offset.Zero)
                                    scale(size, size, pivot = Offset.Zero)
                                }) {
                                    drawStoryChip(paints, index, chip)
                                }
                            }
                        }
                    }
                }
            }

            // Copy: each story's text rises in as it takes over and the old one leaves upwards.
            stories.take(COUNT).forEachIndexed { index, item ->
                Column(
                    modifier = Modifier
                        .offset(19.5.dp, 146.5.dp)
                        .width(276.dp)
                        .graphicsLayer {
                            val v = value()
                            val k = storyAt(v)
                            val hand = handOver(v, auto)
                            alpha = copyAlpha(index, k, hand)
                            translationY = when (index) {
                                k -> (1f - easeOutCubic(hand)) * 10.dp.toPx()
                                (k + COUNT - 1) % COUNT -> -easeOutCubic(hand) * 8.dp.toPx()
                                else -> 0f
                            }
                        }
                        .clearAndSetSemantics {},
                ) {
                    BasicText(text = item.title, style = TitleStyle, maxLines = 1)
                    BasicText(
                        text = item.description,
                        style = DescriptionStyle,
                        maxLines = 2,
                        modifier = Modifier.padding(top = 4.dp),
                    )
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
                label = "action",
            )
            Box(
                modifier = Modifier
                    .offset(ACTION_X.dp, ACTION_Y.dp)
                    .size(ACTION_W.dp, ACTION_H.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button) { onActionClick(story) }
                    .drawBehind {
                        val r = CornerRadius(size.height / 2f)
                        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF212121), Color(0xFF191919))), cornerRadius = r)
                        val inset = 0.5.dp.toPx()
                        drawRoundRect(
                            brush = Brush.verticalGradient(listOf(rgba(255, 255, 255, 0.1f), rgba(255, 255, 255, 0.16f))),
                            topLeft = Offset(inset, inset),
                            size = Size(size.width - inset * 2f, size.height - inset * 2f),
                            cornerRadius = CornerRadius(size.height / 2f - inset),
                            style = Stroke(1.dp.toPx()),
                        )
                    }
                    .semantics { contentDescription = current.action },
                contentAlignment = Alignment.Center,
            ) {
                stories.take(COUNT).forEachIndexed { index, item ->
                    BasicText(
                        text = item.action,
                        style = ActionStyle,
                        maxLines = 1,
                        modifier = Modifier
                            .graphicsLayer {
                                val v = value()
                                alpha = copyAlpha(index, storyAt(v), handOver(v, auto))
                            }
                            .clearAndSetSemantics {},
                    )
                }
            }
        }

        // Round pause button: the two bars turn and shrink away while the play triangle grows in.
        val pauseInteraction = remember { MutableInteractionSource() }
        val pausePressed by pauseInteraction.collectIsPressedAsState()
        LaunchedEffect(pausePressed) {
            if (pausePressed) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
        val pauseScale by animateFloatAsState(
            targetValue = if (pausePressed) PRESS_SCALE else 1f,
            animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
            label = "pause",
        )
        Canvas(
            modifier = Modifier
                .offset((WIDTH - PAUSE).dp, PAUSE_Y.dp)
                .size(PAUSE.dp)
                .graphicsLayer {
                    scaleX = pauseScale
                    scaleY = pauseScale
                }
                .clickable(interactionSource = pauseInteraction, indication = null, role = Role.Button) {
                    paused = !paused
                    onPausedChange(paused)
                }
                .semantics { contentDescription = if (paused) "Play" else "Pause" },
        ) {
            // Authored in dp.
            scale(density, density, pivot = Offset.Zero) {
                val m = morph.value
                val c = PAUSE / 2f
                drawCircle(
                    brush = Brush.verticalGradient(listOf(Color(0xFF2D2D2D), Color(0xFF1F1F1F)), startY = 0f, endY = PAUSE),
                    radius = c,
                    center = Offset(c, c),
                )
                clipPath(paints.pauseDisc) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(rgba(120, 84, 220, 0.6f), rgba(120, 84, 220, 0f)),
                            center = Offset(c, PAUSE + 2f),
                            radius = PAUSE * 0.62f,
                        ),
                        radius = PAUSE * 0.62f,
                        center = Offset(c, PAUSE + 2f),
                        alpha = 0.75f + 0.25f * m,
                    )
                }
                drawCircle(
                    brush = Brush.verticalGradient(
                        listOf(rgba(255, 255, 255, 0.22f), rgba(255, 255, 255, 0.04f)),
                        startY = 0f,
                        endY = PAUSE,
                    ),
                    radius = c - 0.5f,
                    center = Offset(c, c),
                    style = Stroke(1f),
                )
                withTransform({
                    translate(c, c - 1.5f)
                    rotate(m * 90f, pivot = Offset.Zero)
                    scale(1f - 0.5f * m, 1f - 0.5f * m, pivot = Offset.Zero)
                }) {
                    drawRoundRect(paints.bars, Offset(-5.2f, -5.3f), Size(3.2f, 10.6f), CornerRadius(1.6f), alpha = 1f - m)
                    drawRoundRect(paints.bars, Offset(2f, -5.3f), Size(3.2f, 10.6f), CornerRadius(1.6f), alpha = 1f - m)
                }
                withTransform({
                    translate(c, c - 1.5f)
                    rotate((m - 1f) * 90f, pivot = Offset.Zero)
                    scale(0.5f + 0.5f * m, 0.5f + 0.5f * m, pivot = Offset.Zero)
                }) {
                    val ink = Color(0xFFF1F1F1)
                    drawPath(paints.triangle, ink, alpha = m)
                    drawPath(paints.triangle, ink, alpha = m, style = Stroke(1.6f, join = StrokeJoin.Round))
                }
            }
        }
    }
}
