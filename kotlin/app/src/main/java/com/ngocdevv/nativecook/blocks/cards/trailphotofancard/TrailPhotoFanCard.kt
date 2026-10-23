package com.ngocdevv.nativecook.blocks.cards.trailphotofancard

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.TAU
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.surface
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.delay

/**
 * @param photo Address of the photo.
 * @param tint Flat colour shown until the photo arrives.
 */
data class TrailPhoto(val photo: String, val tint: Color)

enum class TrailIcon { Clock, Map, Bolt }

data class TrailFact(val icon: TrailIcon, val label: String)

private const val WIDTH = 316f
private const val HEIGHT = 404f
private const val RADIUS = 28f
private const val PAD = 20f
private val Ink = Color(0xFF0C0C0C)

private const val TITLE_TOP = 18f
private const val TITLE_LINE = 62f
private const val ARROW_GAP = 10f

// The arrow is drawn in a 44 x 34 box; the window it slides through is a little wider.
private const val ARROW_BOX = 46f
private const val ARROW_HEIGHT = 34f
private const val ARROW_STROKE = 7f

// How far one slide carries the arrow: out of the window on the right, in again from the left.
private const val ARROW_TRAVEL = 52f

private const val PHOTO_WIDTH = 82f
private const val PHOTO_HEIGHT = 110f
private const val PHOTO_RADIUS = 15f
private const val PHOTO_BORDER = 4f
private const val FACTS_TOP = 300f
private const val SUMMARY_TOP = 324f

private class Pose(val x: Float, val y: Float, val angle: Float, val scale: Float)

// Centre, angle in degrees and scale of each photo, bottom photo first.
private val Deck = listOf(
    Pose(61f, 222f, 0f, 0.9f),
    Pose(79f, 222f, 0f, 0.95f),
    Pose(97f, 222f, 0f, 1f),
)
private val Fan = listOf(
    Pose(65f, 224f, -5f, 1f),
    Pose(152f, 219f, 8f, 1f),
    Pose(231f, 186f, 25f, 1f),
)

/** A spring of mass 1 given the way the other stacks give it. */
private fun physicalSpring(stiffness: Float, damping: Float) =
    spring(dampingRatio = damping / (2f * sqrt(stiffness)), stiffness = stiffness, visibilityThreshold = 0.0005f)

private const val PRESS_SCALE = 0.98f
private val OpenSpring = physicalSpring(stiffness = 260f, damping = 19f)
private val CloseSpring = physicalSpring(stiffness = 300f, damping = 26f)
private val PressSpring = physicalSpring(stiffness = 320f, damping = 24f)

// Milliseconds between one photo and the next.
private const val STAGGER = 70L
private const val TINT_MS = 260
private val TintEase = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
private const val SLIDE_MS = 520
private val SlideEase = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

// autoPlay loop (seconds): a rest, a long press, and a rest again while the deck folds.
private const val PRESS_AT = 0.8
private const val RELEASE_AT = 3.2
private const val LOOP = 5.0

// After the last touch the loop waits this long before it starts again from rest.
private const val RESUME_AFTER = 2.0

// The sky is drawn this far beyond the card on every side.
private val SKY_BLEED = 110.dp

// The card's and the photos' canvases overflow by this much to fit their shadows.
private val CARD_BLEED = 80.dp
private val PHOTO_BLEED = 44.dp

/** A cloud in the card's coordinates: round puffs (x, y, radius) standing on a flat pill. */
private class Cloud(val puffs: List<FloatArray>, val base: Rect)

private val NearClouds = listOf(
    Cloud(
        listOf(
            floatArrayOf(-28f, -18f, 30f), floatArrayOf(14f, -40f, 40f), floatArrayOf(66f, -52f, 46f),
            floatArrayOf(118f, -30f, 36f), floatArrayOf(156f, -10f, 26f),
        ),
        Rect(Offset(-58f, -22f), Size(240f, 42f)),
    ),
    Cloud(
        listOf(
            floatArrayOf(150f, 452f, 30f), floatArrayOf(196f, 430f, 42f), floatArrayOf(252f, 412f, 52f),
            floatArrayOf(310f, 428f, 44f), floatArrayOf(352f, 448f, 32f),
        ),
        Rect(Offset(118f, 436f), Size(268f, 46f)),
    ),
)
private val FarClouds = listOf(
    Cloud(
        listOf(floatArrayOf(300f, -62f, 18f), floatArrayOf(326f, -72f, 24f), floatArrayOf(352f, -60f, 18f)),
        Rect(Offset(280f, -66f), Size(92f, 22f)),
    ),
    Cloud(
        listOf(floatArrayOf(-10f, 448f, 16f), floatArrayOf(14f, 440f, 22f), floatArrayOf(40f, 450f, 16f)),
        Rect(Offset(-28f, 446f), Size(86f, 18f)),
    ),
)

/** Clouds of one depth, painted white with a soft edge. Drawn in dp units. */
private class CloudLayer(clouds: List<Cloud>, sigma: Float, alpha: Float) {
    private val path = Path().apply {
        for (cloud in clouds) {
            addRoundRect(RoundRect(cloud.base, CornerRadius(cloud.base.height / 2f)))
            for ((x, y, r) in cloud.puffs) addOval(Rect(Offset(x, y), r))
        }
    }.asAndroidPath()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.White.copy(alpha = alpha).toArgb()
        maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
    }

    fun draw(canvas: android.graphics.Canvas) = canvas.drawPath(path, paint)
}

// The arrow twice: one copy leaves on the right while the next comes in from the left.
private val Arrows = Path().apply {
    for (x in floatArrayOf(1f, 1f - ARROW_TRAVEL)) {
        moveTo(x, 17f); lineTo(x + 36.5f, 17f)
        moveTo(x + 22f, 2.5f); lineTo(x + 36.5f, 17f); lineTo(x + 22f, 31.5f)
    }
}

private fun polyline(vararg points: Float, closed: Boolean) = Path().apply {
    moveTo(points[0], points[1])
    for (i in 2 until points.size step 2) lineTo(points[i], points[i + 1])
    if (closed) close()
}

// 12-point pictograms: a grey shape and the lines cut out of it.
private val ClockHands = polyline(6f, 3.3f, 6f, 6f, 8f, 7.2f, closed = false)
private val MapSheet = polyline(1f, 2.6f, 4.4f, 1.2f, 7.6f, 2.6f, 11f, 1.2f, 11f, 9.4f, 7.6f, 10.8f, 4.4f, 9.4f, 1f, 10.8f, closed = true)
private val BoltShape = polyline(7.2f, 0.8f, 2.4f, 6.9f, 5.6f, 6.9f, 4.8f, 11.2f, 9.6f, 5.1f, 6.4f, 5.1f, closed = true)
private val GlyphGrey = Color(0xFF8C8C8C)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun cardText(size: Float, lineHeight: Float, weight: FontWeight, color: Color, spacing: Float = 0f) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val TitleStyle = cardText(58f, TITLE_LINE, FontWeight.Bold, Ink, -2.6f)
private val FactStyle = cardText(10.5f, 14f, FontWeight.SemiBold, Color(0xFF1A1A1A))
private val SummaryStyle = cardText(14f, 20f, FontWeight.Normal, Color(0xFF737373))

private fun mix(from: Float, to: Float, t: Float) = from + (to - from) * t

/** Waits until [seconds] after [start], so a loop timed with it does not drift. */
private suspend fun sleepUntil(start: Long, seconds: Double) {
    val left = start + (seconds * 1e9).toLong() - System.nanoTime()
    if (left > 0) delay(left / 1_000_000)
}

/**
 * A white trail card floating among clouds. While it is pressed the title turns olive, its arrow
 * slides out to the right and back in from the left, and the deck of three photos under the title
 * springs open into a rising fan; letting go folds the deck again.
 *
 * @param autoPlay Presses the card by itself, holds it open and lets go, in a loop.
 * @param title The last word shares its line with the arrow.
 * @param photos The deck, bottom photo first; three are shown.
 * @param accent Colour of the title while the card is pressed.
 * @param clouds Draws the clouds the card floats among.
 * @param onPress Called when the card is tapped.
 */
@Composable
fun TrailPhotoFanCard(
    autoPlay: Boolean = false,
    title: String = "Mountain Hike",
    photos: List<TrailPhoto> = listOf(
        TrailPhoto("https://images.unsplash.com/photo-1526772662000-3f88f10405ff?auto=format&fit=crop&w=240&h=320&q=80", Color(0xFF778691)),
        TrailPhoto("https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?auto=format&fit=crop&w=240&h=320&q=80", Color(0xFF6E756D)),
        TrailPhoto("https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?auto=format&fit=crop&w=240&h=320&q=80", Color(0xFF647C82)),
    ),
    facts: List<TrailFact> = listOf(
        TrailFact(TrailIcon.Clock, "~8 Hours"),
        TrailFact(TrailIcon.Map, "8 km"),
        TrailFact(TrailIcon.Bolt, "Medium Level"),
    ),
    summary: String = "Hiking on a mountain blends physical challenge with natural beauty, offering sweeping views.",
    accent: Color = Color(0xFF5A5A32),
    clouds: Boolean = true,
    onPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val view = LocalView.current
    val tapped by rememberUpdatedState(onPress)

    // A finger is on the card.
    var held by remember { mutableStateOf(false) }
    // The autoPlay loop is pressing the card.
    var played by remember { mutableStateOf(false) }
    // A touch stopped the loop; it starts again from rest a while after the last one.
    var paused by remember { mutableStateOf(false) }
    var touches by remember { mutableIntStateOf(0) }
    val open = held || played

    LaunchedEffect(auto, paused) {
        played = false
        if (!auto || paused) return@LaunchedEffect
        val start = System.nanoTime()
        var turn = 0
        while (true) {
            sleepUntil(start, turn * LOOP + PRESS_AT)
            played = true
            sleepUntil(start, turn * LOOP + RELEASE_AT)
            played = false
            turn++
        }
    }

    LaunchedEffect(touches) {
        if (touches == 0) return@LaunchedEffect
        delay((RESUME_AFTER * 1000).toLong())
        if (!held) paused = false
    }

    val titleColor by animateColorAsState(
        targetValue = if (open) accent else Ink,
        animationSpec = tween(TINT_MS, easing = TintEase),
        label = "tint",
    )
    val press by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else PressSpring,
        label = "press",
    )
    // 0…1 over one slide of the arrow; both ends look the same.
    val slide = remember { Animatable(0f) }
    LaunchedEffect(open, reducedMotion) {
        if (!open || reducedMotion) return@LaunchedEffect
        slide.snapTo(0f)
        slide.animateTo(1f, tween(SLIDE_MS, easing = SlideEase))
    }

    val cut = title.lastIndexOf(' ')
    val first = if (cut < 0) null else title.substring(0, cut)
    val last = title.substring(cut + 1)
    val shadow = remember { SoftRoundRect(rgba(6, 58, 88, 0.34f), sigma = 22f) }

    Box(modifier.size(WIDTH.dp, HEIGHT.dp)) {
        if (clouds) Sky(still = reducedMotion)

        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = mix(1f, PRESS_SCALE, press)
                    scaleY = scaleX
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            held = true
                            paused = true
                            played = false
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            val released = tryAwaitRelease()
                            held = false
                            touches++
                            if (released) tapped?.invoke()
                        },
                    )
                }
                .clearAndSetSemantics {
                    role = Role.Button
                    contentDescription = "$title. ${facts.joinToString { it.label }}. $summary"
                },
        ) {
            Box(Modifier.matchParentSize().bleed(CARD_BLEED)) {
                Canvas(Modifier.fillMaxSize()) {
                    surface(CARD_BLEED) { x, y, w, h ->
                        drawIntoCanvas { shadow.draw(it.nativeCanvas, x, y + 20f, w, h, RADIUS) }
                        drawRoundRect(Color.White, Offset(x, y), Size(w, h), CornerRadius(RADIUS))
                    }
                }
            }

            Column(Modifier.offset(PAD.dp, TITLE_TOP.dp)) {
                if (first != null) {
                    BasicText(first, Modifier.height(TITLE_LINE.dp), TitleStyle, maxLines = 1, color = { titleColor })
                }
                Row(
                    Modifier.height(TITLE_LINE.dp),
                    horizontalArrangement = Arrangement.spacedBy(ARROW_GAP.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(last, style = TitleStyle, maxLines = 1, color = { titleColor })
                    Canvas(Modifier.size(ARROW_BOX.dp, ARROW_HEIGHT.dp).clipToBounds()) {
                        scale(density, density, pivot = Offset.Zero) {
                            translate(left = slide.value * ARROW_TRAVEL) {
                                drawPath(Arrows, titleColor, style = Stroke(ARROW_STROKE, join = StrokeJoin.Miter))
                            }
                        }
                    }
                }
            }

            photos.take(3).forEachIndexed { index, photo ->
                DeckPhoto(photo, index, open, reducedMotion)
            }

            Row(
                Modifier.offset(PAD.dp, FACTS_TOP.dp).height(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (fact in facts) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Glyph(fact.icon)
                        BasicText(fact.label, style = FactStyle, maxLines = 1)
                    }
                }
            }

            BasicText(
                summary,
                Modifier.offset(PAD.dp, SUMMARY_TOP.dp).width((WIDTH - PAD * 2f).dp),
                SummaryStyle,
                maxLines = 3,
            )
        }
    }
}

@Composable
private fun DeckPhoto(photo: TrailPhoto, index: Int, open: Boolean, reducedMotion: Boolean) {
    val fan = remember { Animatable(0f) }
    LaunchedEffect(open, reducedMotion) {
        val to = if (open) 1f else 0f
        if (reducedMotion) {
            fan.snapTo(to)
        } else {
            // The fan opens from the bottom photo up and folds from the top photo down.
            delay((if (open) index else 2 - index) * STAGGER)
            fan.animateTo(to, if (open) OpenSpring else CloseSpring)
        }
    }

    val lifted = remember { SoftRoundRect(rgba(14, 38, 52, 0.3f), sigma = 12f) }
    val resting = remember { SoftRoundRect(rgba(14, 38, 52, 0.22f), sigma = 4f) }
    val context = LocalContext.current
    val request = remember(photo.photo, context) { ImageRequest.Builder(context).data(photo.photo).crossfade(200).build() }

    Box(
        Modifier
            .size(PHOTO_WIDTH.dp, PHOTO_HEIGHT.dp)
            .graphicsLayer {
                val t = fan.value
                val from = Deck[index]
                val to = Fan[index]
                translationX = (mix(from.x, to.x, t) - PHOTO_WIDTH / 2f).dp.toPx()
                translationY = (mix(from.y, to.y, t) - PHOTO_HEIGHT / 2f).dp.toPx()
                rotationZ = mix(from.angle, to.angle, t)
                scaleX = mix(from.scale, to.scale, t)
                scaleY = scaleX
            },
    ) {
        Box(Modifier.matchParentSize().bleed(PHOTO_BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(PHOTO_BLEED) { x, y, w, h ->
                    drawIntoCanvas {
                        // The shadow of a photo that has lifted off the deck.
                        lifted.draw(it.nativeCanvas, x, y + 12f, w, h, PHOTO_RADIUS, alpha = fan.value.coerceIn(0f, 1f))
                        resting.draw(it.nativeCanvas, x, y + 3f, w, h, PHOTO_RADIUS)
                    }
                    drawRoundRect(Color.White, Offset(x, y), Size(w, h), CornerRadius(PHOTO_RADIUS))
                }
            }
        }
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .padding(PHOTO_BORDER.dp)
                .fillMaxSize()
                .clip(RoundedCornerShape((PHOTO_RADIUS - PHOTO_BORDER).dp))
                .background(photo.tint),
        )
    }
}

@Composable
private fun Glyph(icon: TrailIcon) {
    Canvas(Modifier.size(12.dp)) {
        scale(density, density, pivot = Offset.Zero) {
            when (icon) {
                TrailIcon.Clock -> {
                    drawCircle(GlyphGrey, 5.5f, Offset(6f, 6f))
                    drawPath(ClockHands, Color.White, style = Stroke(1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                TrailIcon.Map -> {
                    drawPath(MapSheet, GlyphGrey)
                    drawLine(Color.White, Offset(4.4f, 1.2f), Offset(4.4f, 9.4f), 1f, StrokeCap.Round)
                    drawLine(Color.White, Offset(7.6f, 2.6f), Offset(7.6f, 10.8f), 1f, StrokeCap.Round)
                }
                TrailIcon.Bolt -> drawPath(BoltShape, GlyphGrey)
            }
        }
    }
}

/**
 * Soft clouds behind the card: two near ones it seems to rest on and two far ones, swaying against
 * each other once per loop.
 */
@Composable
private fun Sky(still: Boolean) {
    // Radians of the sway.
    val turn = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(still) {
        turn.floatValue = 0f
        if (still) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { turn.floatValue = ((it - start) / 1e9 / LOOP % 1.0).toFloat() * TAU }
        }
    }
    val far = remember { CloudLayer(FarClouds, sigma = 9f, alpha = 0.55f) }
    val near = remember { CloudLayer(NearClouds, sigma = 6f, alpha = 0.96f) }

    Box(Modifier.fillMaxSize().bleed(SKY_BLEED)) {
        CloudCanvas(far) { Offset(-3f * sin(turn.floatValue), 1.5f * cos(turn.floatValue)) }
        CloudCanvas(near) { Offset(6f * sin(turn.floatValue), -2f * cos(turn.floatValue)) }
    }
}

/** One depth of clouds on its own layer, moved by [sway] (in dp) without being drawn again. */
@Composable
private fun CloudCanvas(layer: CloudLayer, sway: () -> Offset) {
    Canvas(
        Modifier.fillMaxSize().graphicsLayer {
            val offset = sway()
            translationX = offset.x.dp.toPx()
            translationY = offset.y.dp.toPx()
        },
    ) {
        surface(SKY_BLEED) { x, y, _, _ ->
            translate(x, y) { drawIntoCanvas { layer.draw(it.nativeCanvas) } }
        }
    }
}
