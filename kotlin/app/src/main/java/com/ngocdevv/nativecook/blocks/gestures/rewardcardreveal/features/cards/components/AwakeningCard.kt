package com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.features.cards.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.R
import com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.features.cards.theme.CARD_HEIGHT_RATIO
import com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.shared.graphics.CARD_PERSPECTIVE_DP
import com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.shared.graphics.cameraDistanceForPerspective
import com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.shared.graphics.cssColor
import com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.shared.graphics.fixedSp
import com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.shared.graphics.rememberReduceMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

private const val DESIGN_WIDTH = 320f
private const val CARD_TILT_DEGREES = -2.55f
private const val ENTRY_DURATION = 600
private const val FLIP_HOLD_DURATION = 100
private const val FLIP_DELAY = ENTRY_DURATION + FLIP_HOLD_DURATION
private const val FLIP_DURATION = 1300
private const val SETTLE_DURATION = 650
private const val PLANE_MOTION_DURATION = FLIP_DURATION + SETTLE_DURATION
private const val SWAY_DELAY = FLIP_DELAY + FLIP_DURATION
private const val SWAY_DURATION = 9000
private const val SWAY_ANGLE_DEGREES = 3.3f
private const val SWAY_PITCH_DEGREES = 4.2f
private const val SWAY_YAW_DEGREES = 6.4f
private const val SWAY_HORIZONTAL_RATIO = 0.024f
private const val SWAY_VERTICAL_RATIO = 0.032f
private const val EXIT_SETTLE_DURATION = 500
private const val EXIT_TRANSLATE_Y = 2f
private const val EXIT_SCALE = 0.985f
private val EXIT_EASING = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/** Reanimated `Easing.inOut(Easing.quad)`. */
private val SWAY_EASING = Easing { progress ->
    if (progress < 0.5f) 2f * progress * progress
    else 1f - 2f * (1f - progress) * (1f - progress)
}
private val CORNER_EMBLEM_GLYPHS = "mymind·mymind·mymind·".toList()

private fun flipMillis(percent: Float): Int = FLIP_DELAY + (percent * FLIP_DURATION).toInt()
private fun planeMillis(percent: Float): Int = FLIP_DELAY + (percent * PLANE_MOTION_DURATION).toInt()

// CARD_ENTRY_KEYFRAMES — linear, 600 ms.
private val ENTRY_OPACITY_SPEC = keyframes<Float> {
    durationMillis = ENTRY_DURATION
    0f at 0 using LinearEasing
    0.24f at 120 using LinearEasing
    0.58f at 288 using LinearEasing
    0.75f at 432 using LinearEasing
    0.88f at 516 using LinearEasing
    1f at ENTRY_DURATION
}
private val ENTRY_SCALE_SPEC = keyframes<Float> {
    durationMillis = ENTRY_DURATION
    1.4f at 0 using LinearEasing
    1.31f at 120 using LinearEasing
    1.16f at 288 using LinearEasing
    1.07f at 432 using LinearEasing
    1.03f at 516 using LinearEasing
    1f at ENTRY_DURATION
}

// CARD_BACK_FLIP_KEYFRAMES — delay 700 ms, 1300 ms linear.
private val BACK_FLIP_SPEC = keyframes<Float> {
    durationMillis = FLIP_DELAY + FLIP_DURATION
    0f at 0 using LinearEasing
    0f at FLIP_DELAY using LinearEasing
    60f at flipMillis(0.33f) using LinearEasing
    88f at flipMillis(0.44f) using LinearEasing
    114f at flipMillis(0.50f) using LinearEasing
    138f at flipMillis(0.60f) using LinearEasing
    150f at flipMillis(0.67f) using LinearEasing
    174f at flipMillis(0.82f) using LinearEasing
    180f at FLIP_DELAY + FLIP_DURATION
}

// CARD_FRONT_FLIP_KEYFRAMES
private val FRONT_FLIP_SPEC = keyframes<Float> {
    durationMillis = FLIP_DELAY + FLIP_DURATION
    -180f at 0 using LinearEasing
    -180f at FLIP_DELAY using LinearEasing
    -120f at flipMillis(0.33f) using LinearEasing
    -92f at flipMillis(0.44f) using LinearEasing
    -66f at flipMillis(0.50f) using LinearEasing
    -42f at flipMillis(0.60f) using LinearEasing
    -30f at flipMillis(0.67f) using LinearEasing
    -6f at flipMillis(0.82f) using LinearEasing
    0f at FLIP_DELAY + FLIP_DURATION
}

// CARD_PLANE_MOTION_KEYFRAMES — delay 700 ms, 1950 ms linear.
private val PLANE_TRANSLATE_SPEC = keyframes<Float> {
    durationMillis = FLIP_DELAY + PLANE_MOTION_DURATION
    0f at 0 using LinearEasing
    0f at FLIP_DELAY using LinearEasing
    0f at planeMillis(0.55f) using LinearEasing
    5f at planeMillis(0.72f) using LinearEasing
    0f at FLIP_DELAY + PLANE_MOTION_DURATION
}
private val PLANE_SCALE_SPEC = keyframes<Float> {
    durationMillis = FLIP_DELAY + PLANE_MOTION_DURATION
    1f at 0 using LinearEasing
    1f at FLIP_DELAY using LinearEasing
    1f at planeMillis(0.55f) using LinearEasing
    1.03f at planeMillis(0.72f) using LinearEasing
    1f at FLIP_DELAY + PLANE_MOTION_DURATION
}

@Composable
private fun AuroraField(height: Dp, width: Dp, modifier: Modifier = Modifier) {
    val scale = width.value / 304f
    val panelShape = remember(width, height) {
        GenericShape { size, _ ->
            val s = size.width / width.value * scale // design unit → px
            val w = size.width
            moveTo(53f * s, 0f)
            lineTo(w - 8f * s, 0f)
            quadraticTo(w, 0f, w, 8f * s)
            lineTo(w, 377f * s)
            cubicTo(w, 382f * s, w - 3f * s, 385f * s, w - 9f * s, 386f * s)
            cubicTo(w - 64f * s, 398f * s, 64f * s, 398f * s, 9f * s, 386f * s)
            cubicTo(3f * s, 385f * s, 0f, 382f * s, 0f, 377f * s)
            lineTo(0f, 58f * s)
            cubicTo(0f, 52f * s, 4f * s, 48f * s, 10f * s, 48f * s)
            lineTo(15f * s, 48f * s)
            cubicTo(31f * s, 48f * s, 43f * s, 35f * s, 43f * s, 19f * s)
            lineTo(43f * s, 15f * s)
            cubicTo(43f * s, 7f * s, 45f * s, 0f, 53f * s, 0f)
            close()
        }
    }

    Box(modifier = modifier.size(width, height).clip(panelShape)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to cssColor("#f2ad70"),
                        0.27f to cssColor("#e5b8dd"),
                        0.48f to cssColor("#bcdce0"),
                        0.73f to cssColor("#edd9bd"),
                        1f to cssColor("#ddc2ed"),
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                ),
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .blur((13f * scale).dp, BlurredEdgeTreatment.Unbounded),
        ) {
            val s = scale * density
            fun glow(cx: Float, cy: Float, r: Float, color: Color) {
                val center = Offset(cx * s, cy * s)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color, color.copy(alpha = 0f)),
                        center = center,
                        radius = r * s,
                    ),
                    radius = r * s,
                    center = center,
                )
            }
            glow(21f, 35f, 166f, Color(249f / 255f, 163f / 255f, 49f / 255f, 0.9f))
            glow(161f, -2f, 165f, Color(216f / 255f, 45f / 255f, 193f / 255f, 0.88f))
            glow(305f, 20f, 145f, Color(151f / 255f, 211f / 255f, 219f / 255f, 0.9f))
            glow(286f, 263f, 116f, Color(250f / 255f, 222f / 255f, 105f / 255f, 0.82f))

            val center = Offset(158f * s, 159f * s)
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        "#fa7654", "#f4d95d", "#ff805c", "#dc77c7", "#f13570", "#ff1759", "#d931bd", "#9dcede", "#fa7654",
                    ).map(::cssColor),
                    center = center,
                ),
                radius = 177f * s,
                center = center,
                alpha = 0.84f,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to cssColor("rgba(255, 30, 63, 0)"),
                        0.6f to cssColor("rgba(255, 16, 58, 0.68)"),
                        0.79f to cssColor("rgba(255, 73, 43, 0.48)"),
                        1f to cssColor("rgba(255, 120, 65, 0)"),
                    ),
                    center = center,
                    radius = 177f * s,
                ),
                radius = 177f * s,
                center = center,
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to cssColor("rgba(255,255,255,0)"),
                        0.53f to cssColor("rgba(255,247,226,0.12)"),
                        0.77f to cssColor("rgba(250,232,222,0.58)"),
                        1f to cssColor("rgba(236,213,244,0.78)"),
                    ),
                    start = Offset(size.width * 0.42f, size.height * 0.25f),
                    end = Offset(size.width * 0.58f, size.height),
                ),
            )
        }
    }
}

@Composable
private fun CornerEmblem(scale: Float) {
    val size = (48f * scale).dp
    val center = size / 2
    val orbitRadius = 17.5f * scale
    val glyphBoxSize = (6f * scale).dp

    Box(
        modifier = Modifier
            .offset(x = (4f * scale).dp, y = (5f * scale).dp)
            .size(size),
        contentAlignment = Alignment.Center,
    ) {
        CORNER_EMBLEM_GLYPHS.forEachIndexed { index, glyph ->
            val angle = -112f + (index * 360f) / CORNER_EMBLEM_GLYPHS.size
            val radians = angle * PI.toFloat() / 180f
            val fontSize = (if (glyph == '·') 3.5f else 4.4f) * scale
            BasicText(
                text = glyph.toString(),
                maxLines = 1,
                style = TextStyle(
                    color = cssColor("#aab3b8"),
                    fontSize = fontSize.fixedSp(),
                    fontWeight = FontWeight.Bold,
                    lineHeight = glyphBoxSize.fixedSp(),
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = center + (cos(radians) * orbitRadius).dp - glyphBoxSize / 2,
                        y = center + (sin(radians) * orbitRadius).dp - glyphBoxSize / 2,
                    )
                    .size(glyphBoxSize)
                    .graphicsLayer { rotationZ = angle + 90f },
            )
        }

        Box(modifier = Modifier.size((22f * scale).dp, (20f * scale).dp)) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = (1f * scale).dp, y = (-1f * scale).dp)
                    .size((18f * scale).dp)
                    .background(cssColor("#acb6ba"), RoundedCornerShape((9f * scale).dp)),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = (7f * scale).dp, y = (-4f * scale).dp)
                    .size((17f * scale).dp)
                    .background(cssColor("#f9fbfb"), RoundedCornerShape((8f * scale).dp)),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = (3f * scale).dp)
                    .size((7f * scale).dp, (12f * scale).dp)
                    .graphicsLayer { rotationZ = 10f }
                    .background(cssColor("#d8dde0"), RoundedCornerShape((4f * scale).dp)),
            )
        }
    }
}

@Composable
private fun AchievementMedal(scale: Float) {
    val size = (38f * scale).dp
    Box(modifier = Modifier.size(size)) {
        Image(
            painter = painterResource(R.drawable.rewardcardreveal_ball),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = 1.34f
                    scaleY = 1.34f
                },
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .border((2.5f * scale).dp, cssColor("rgba(255,255,255,0.96)"), CircleShape),
        )
    }
}

@Composable
private fun TinySeal(scale: Float) {
    val size = (15f * scale).dp
    Box(
        modifier = Modifier
            .size(size)
            .background(cssColor("#9188cf"), CircleShape)
            .border((1.5f * scale).dp, cssColor("#d9d9ee"), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = "✿",
            style = TextStyle(
                color = cssColor("#f5f3ff"),
                fontSize = (7.2f * scale).fixedSp(),
                fontWeight = FontWeight.Black,
            ),
        )
    }
}

/** RN `transform: [{ skewX: '-7deg' }]`, which graphicsLayer cannot express. */
private fun Modifier.skewX(degrees: Float): Modifier = drawWithContent {
    val skew = tan(degrees * PI.toFloat() / 180f)
    val matrix = Matrix().apply { values[Matrix.SkewX] = skew }
    withTransform({
        translate(size.width / 2f, size.height / 2f)
        transform(matrix)
        translate(-size.width / 2f, -size.height / 2f)
    }) {
        this@drawWithContent.drawContent()
    }
}

@Composable
fun AwakeningCardFace(width: Dp, modifier: Modifier = Modifier) {
    val scale = width.value / DESIGN_WIDTH
    val density = LocalDensity.current.density
    val height = width * CARD_HEIGHT_RATIO
    val panelHeight = (400f * scale).dp
    val panelWidth = width - (16f * scale).dp
    val portraitSize = (220f * scale).dp
    val radius = (12f * scale).dp
    val d = { value: Float -> (value * scale).dp }

    Box(
        modifier = modifier
            .size(width, height)
            .shadow(elevation = (12f * scale).dp, shape = RoundedCornerShape(radius), clip = false,
                ambientColor = cssColor("rgba(80, 92, 108, 0.2)"), spotColor = cssColor("rgba(80, 92, 108, 0.2)"))
            .clip(RoundedCornerShape(radius))
            .background(cssColor("#f8fafb"))
            .border(1.dp, cssColor("rgba(255, 255, 255, 0.92)"), RoundedCornerShape(radius)),
    ) {
        // Footer plate
        Box(
            modifier = Modifier
                .offset(x = d(8f), y = d(394f))
                .width(width - d(16f))
                .height(height - d(394f) - d(7f))
                .background(cssColor("rgba(247, 249, 251, 0.96)")),
        )

        Box(
            modifier = Modifier
                .offset(x = d(8f), y = d(8f))
                .size(panelWidth, panelHeight)
                .clip(RoundedCornerShape(d(8f))),
        ) {
            AuroraField(height = panelHeight, width = panelWidth)
        }

        Box(
            modifier = Modifier
                .offset(x = d(56f), y = d(58f))
                .size(portraitSize)
                .shadow(d(7f), CircleShape, ambientColor = cssColor("rgba(220, 32, 77, 0.28)"),
                    spotColor = cssColor("rgba(220, 32, 77, 0.28)"))
                .background(cssColor("#fffefd"), CircleShape),
        )

        CornerEmblem(scale = scale)

        BasicText(
            text = "001 / 151",
            maxLines = 1,
            style = TextStyle(
                color = cssColor("rgba(108, 111, 116, 0.48)"),
                fontSize = (3.4f * scale).fixedSp(),
                fontWeight = FontWeight.Bold,
                letterSpacing = (0.65f * scale).sp,
            ),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = -d(4f), y = d(226f))
                .graphicsLayer { rotationZ = 90f },
        )

        BasicText(
            text = "The Awakening",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                color = cssColor("#fffdf5"),
                fontFamily = FontFamily.Serif,
                fontSize = (22.5f * scale).fixedSp(),
                fontWeight = FontWeight.Bold,
                lineHeight = (28f * scale).fixedSp(),
                textAlign = TextAlign.Center,
                shadow = Shadow(
                    color = cssColor("rgba(137, 84, 96, 0.17)"),
                    offset = Offset(0f, 1f * scale * density),
                    blurRadius = 2f * scale * density,
                ),
            ),
            modifier = Modifier
                .offset(x = d(18f), y = d(309f))
                .width(width - d(18f) - d(12f)),
        )

        BasicText(
            text = "You’ve taken the first step in your journey.\nA fresh, new mind awaits you.",
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                color = cssColor("rgba(255, 255, 250, 0.97)"),
                fontSize = (10.5f * scale).fixedSp(),
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.SemiBold,
                lineHeight = (13f * scale).fixedSp(),
                textAlign = TextAlign.Center,
                shadow = Shadow(
                    color = cssColor("rgba(142, 91, 102, 0.14)"),
                    offset = Offset(0f, 1f * scale * density),
                    blurRadius = 1.5f * scale * density,
                ),
            ),
            modifier = Modifier
                .offset(x = d(24f), y = d(347f))
                .width(width - d(24f) - d(18f)),
        )

        Box(
            modifier = Modifier
                .offset(x = d(15f), y = d(405f))
                .size(d(22f), d(18f))
                .skewX(-7f)
                .background(cssColor("#8790c9"), RoundedCornerShape(d(4f))),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = "E",
                style = TextStyle(
                    color = cssColor("#f7f9ff"),
                    fontSize = (13f * scale).fixedSp(),
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Black,
                    lineHeight = (15f * scale).fixedSp(),
                ),
            )
        }

        BasicText(
            text = "Epic Milestone",
            style = TextStyle(
                color = cssColor("#747d91"),
                fontSize = (7.7f * scale).fixedSp(),
                fontWeight = FontWeight.SemiBold,
            ),
            modifier = Modifier.offset(x = d(43f), y = d(409f)),
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = -d(12f), y = d(407f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = "+100 Tranquility",
                style = TextStyle(
                    color = cssColor("#747d91"),
                    fontSize = (7.7f * scale).fixedSp(),
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Box(modifier = Modifier.width(d(6f)))
            TinySeal(scale = scale)
        }

        Box(modifier = Modifier.offset(x = d(DESIGN_WIDTH / 2f - 19f), y = d(383f))) {
            AchievementMedal(scale = scale)
        }

        BasicText(
            text = "Art by mymind.com",
            style = TextStyle(color = cssColor("#a0a7b1"), fontSize = (6.2f * scale).fixedSp()),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = d(14f), y = -d(6f)),
        )

        BasicText(
            text = "✦✦✦✦",
            style = TextStyle(
                color = cssColor("#485164"),
                fontSize = (8f * scale).fixedSp(),
                fontWeight = FontWeight.Black,
                letterSpacing = (5f * scale).sp,
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -d(5.5f))
                .fillMaxWidth(),
        )

        BasicText(
            text = "© mymind Inc.",
            style = TextStyle(color = cssColor("#a9afb7"), fontSize = (6.2f * scale).fixedSp()),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = -d(12f), y = -d(6f)),
        )
    }
}

/**
 * "The Awakening" collectible: fades/zooms in, flips from the holographic back to the front,
 * settles, then sways gently until `exiting` asks it to settle flat before navigating back.
 */
@Composable
fun AwakeningCard(
    width: Dp,
    modifier: Modifier = Modifier,
    exiting: Boolean = false,
    onExitReady: (() -> Unit)? = null,
) {
    val height = width * CARD_HEIGHT_RATIO
    val reduceMotion = rememberReduceMotion()
    val exitCallback by rememberUpdatedState(onExitReady)

    val entryOpacity = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val entryScale = remember { Animatable(if (reduceMotion) 1f else 1.4f) }
    val backRotation = remember { Animatable(if (reduceMotion) 180f else 0f) }
    val frontRotation = remember { Animatable(if (reduceMotion) 0f else -180f) }
    val planeTranslateY = remember { Animatable(0f) }
    val planeScale = remember { Animatable(1f) }
    val swayProgress = remember { Animatable(if (reduceMotion) 0.5f else 0f) }
    val exitProgress = remember { Animatable(0f) }

    // One-shot CSS keyframe animations (entry, flip, plane settle).
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) {
            entryOpacity.snapTo(1f)
            entryScale.snapTo(1f)
            backRotation.snapTo(180f)
            frontRotation.snapTo(0f)
            planeTranslateY.snapTo(0f)
            planeScale.snapTo(1f)
            return@LaunchedEffect
        }
        coroutineScope {
            launch { entryOpacity.animateTo(1f, ENTRY_OPACITY_SPEC) }
            launch { entryScale.animateTo(1f, ENTRY_SCALE_SPEC) }
            launch { backRotation.animateTo(180f, BACK_FLIP_SPEC) }
            launch { frontRotation.animateTo(0f, FRONT_FLIP_SPEC) }
            launch { planeTranslateY.animateTo(0f, PLANE_TRANSLATE_SPEC) }
            launch { planeScale.animateTo(1f, PLANE_SCALE_SPEC) }
        }
    }

    // Sway loop / exit settle (mirrors the Reanimated effect).
    LaunchedEffect(exiting, reduceMotion) {
        if (exiting) {
            if (reduceMotion) {
                swayProgress.snapTo(0.5f)
                exitProgress.snapTo(1f)
                exitCallback?.invoke()
                return@LaunchedEffect
            }
            coroutineScope {
                launch {
                    swayProgress.animateTo(0.5f, tween(EXIT_SETTLE_DURATION, easing = EXIT_EASING))
                }
                launch {
                    exitProgress.animateTo(1f, tween(EXIT_SETTLE_DURATION, easing = EXIT_EASING))
                }
            }
            exitCallback?.invoke()
            return@LaunchedEffect
        }

        exitProgress.snapTo(0f)
        if (reduceMotion) {
            swayProgress.snapTo(0.5f)
            return@LaunchedEffect
        }
        swayProgress.snapTo(0f)
        swayProgress.animateTo(
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(SWAY_DURATION, easing = SWAY_EASING),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(SWAY_DELAY),
            ),
        )
    }

    Box(
        modifier = modifier
            .size(width, height)
            .semantics {
                role = Role.Image
                contentDescription = "The Awakening, Epic Milestone, cộng 100 Tranquility"
            }
            .graphicsLayer {
                alpha = entryOpacity.value
                scaleX = entryScale.value
                scaleY = entryScale.value
                rotationZ = CARD_TILT_DEGREES
            },
    ) {
        // Sway plane
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    val signed = swayProgress.value * 2f - 1f
                    val dismiss = exitProgress.value
                    val widthPx = width.toPx()
                    cameraDistance = cameraDistanceForPerspective(CARD_PERSPECTIVE_DP, density)
                    translationX = signed * widthPx * SWAY_HORIZONTAL_RATIO
                    translationY = signed * signed * widthPx * SWAY_VERTICAL_RATIO +
                        dismiss * EXIT_TRANSLATE_Y * density
                    rotationX = signed * SWAY_PITCH_DEGREES
                    rotationY = -signed * SWAY_YAW_DEGREES
                    rotationZ = signed * SWAY_ANGLE_DEGREES - CARD_TILT_DEGREES
                    val s = 1f - dismiss * (1f - EXIT_SCALE)
                    scaleX = s
                    scaleY = s
                },
        ) {
            // Flip plane
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        translationY = planeTranslateY.value * density
                        scaleX = planeScale.value
                        scaleY = planeScale.value
                    },
            ) {
                // Back face
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            val rotation = backRotation.value
                            cameraDistance = cameraDistanceForPerspective(CARD_PERSPECTIVE_DP, density)
                            rotationY = rotation
                            alpha = if (abs(rotation) <= 90f) 1f else 0f
                        }
                        .clip(RoundedCornerShape(width * 0.045f)),
                ) {
                    CollectionCardBack(
                        width = width,
                        collectorMarkFinish = CollectorMarkFinish.Holographic,
                    )
                }

                // Front face
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            val rotation = frontRotation.value
                            cameraDistance = cameraDistanceForPerspective(CARD_PERSPECTIVE_DP, density)
                            rotationY = rotation
                            alpha = if (abs(rotation) <= 90f) 1f else 0f
                        },
                ) {
                    AwakeningCardFace(width = width)
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFE5E9EF)
@Composable
private fun AwakeningCardFacePreview() {
    Box(modifier = Modifier.padding(16.dp)) {
        AwakeningCardFace(width = 300.dp)
    }
}
