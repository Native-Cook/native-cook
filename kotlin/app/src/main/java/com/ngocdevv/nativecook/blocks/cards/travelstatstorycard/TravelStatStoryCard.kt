package com.ngocdevv.nativecook.blocks.cards.travelstatstorycard

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
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
import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.surface
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * @param photo Address of the photo.
 * @param tint Flat colour shown until the photo arrives.
 */
data class StatPhoto(val photo: String, val tint: Color)

enum class StatIcon { Trail, Waves, Star }

/**
 * @param tag Label of the outlined pill.
 * @param figure The number the figure counts up to.
 * @param glow Colour of the glow behind the story.
 * @param thumbs The two pill-shaped thumbnails.
 * @param cover The photo that fills the card for a moment as the story comes in.
 */
data class StatStory(
    val icon: StatIcon,
    val category: String,
    val tag: String,
    val figure: Int,
    val headline: String,
    val detail: String,
    val glow: Color,
    val thumbs: List<StatPhoto>,
    val cover: StatPhoto,
    val unit: String = "%",
)

private const val WIDTH = 316f
private const val HEIGHT = 424f
private const val RADIUS = 32f

// Inset of the header row, and of the text under it.
private const val EDGE = 13f
private const val PAD = 26f

// Diameter of the round chips in the header.
private const val CHIP = 34f

private const val FIGURE_TOP = 104f
private const val FIGURE_HEIGHT = 88f
private const val TEXT_TOP = 219f
private const val THUMB_TOP = 298f
private const val THUMB_WIDTH = 108f
private const val THUMB_HEIGHT = 54f
private const val BAR_TOP = 392f
private const val BAR_WIDTH = 236f
private const val BAR_HEIGHT = 3f
private const val BAR_GAP = 8f

// autoPlay: a story stays this long, the first change comes FIRST_AT seconds after the card appears.
private const val STORY = 2.8f
private const val FIRST_AT = 0.9f

// After a tap the loop waits this long before it moves on from the tapped story.
private const val RESUME_AFTER = 2f

// A story change, in seconds after the tap.
private const val LEAVE = 0.26f
private const val ENTER_AT = 0.32f
private const val ENTER_STAGGER = 0.06f
private const val ENTER = 0.46f
private const val RISE = 12f
private const val COVER_AT = 0.06f
private const val COVER_IN = 0.26f
private const val COVER_OUT_AT = 0.42f
private const val COVER_OUT = 0.5f
private const val COVER_ZOOM = 0.12f
private const val BLUR = 12f
private const val BLUR_OUT_AT = 0.38f
private const val BLUR_OUT = 0.4f
private const val GLOW = 0.6f
private const val COUNT = 0.9f

// The figure starts counting at this share of its value.
private const val COUNT_FROM = 0.15f
private const val SEGMENT_FADE = 0.3f

// Length of the plain cross-fade under reduced motion.
private const val CALM_FADE = 0.2f

// The card's canvas overflows by this much to fit its shadow.
private val SHADOW_BLEED = 80.dp

private fun photo(id: String, width: Int, height: Int) =
    "https://images.unsplash.com/photo-$id?auto=format&fit=crop&w=$width&h=$height&q=80"

private val Stories = listOf(
    StatStory(
        icon = StatIcon.Trail, category = "Backpacking", tag = "Budget Travel", figure = 35,
        headline = "backpackers visit Southeast Asia",
        detail = "every year to explore its rich culture and beautiful landscapes.",
        glow = Color(0xFFF2600C),
        thumbs = listOf(
            StatPhoto(photo("1526772662000-3f88f10405ff", 220, 110), Color(0xFF778691)),
            StatPhoto(photo("1506905925346-21bda4d32df4", 220, 110), Color(0xFF6E6F80)),
        ),
        cover = StatPhoto(photo("1528127269322-539801943592", 640, 860), Color(0xFF849B9B)),
    ),
    StatStory(
        icon = StatIcon.Waves, category = "Island Hopping", tag = "Slow Travel", figure = 62,
        headline = "of island trips include a boat day",
        detail = "with snorkelling stops and sandbank picnics the most booked extras.",
        glow = Color(0xFF0B9C8F),
        thumbs = listOf(
            StatPhoto(photo("1514282401047-d79a71a590e8", 220, 110), Color(0xFF4EC6C6)),
            StatPhoto(photo("1506929562872-bb421503ef21", 220, 110), Color(0xFF5D8F89)),
        ),
        cover = StatPhoto(photo("1505142468610-359e7d316be0", 640, 860), Color(0xFF588B90)),
    ),
    StatStory(
        icon = StatIcon.Star, category = "Stargazing", tag = "Winter Travel", figure = 48,
        headline = "of aurora trips are booked for March",
        detail = "when long clear nights meet the mildest weather of the season.",
        glow = Color(0xFF6D3FF0),
        thumbs = listOf(
            StatPhoto(photo("1531366936337-7c912a4589a7", 220, 110), Color(0xFF546187)),
            StatPhoto(photo("1504829857797-ddff29c27927", 220, 110), Color(0xFF8F9EA4)),
        ),
        cover = StatPhoto(photo("1519681393784-d120267933ba", 640, 860), Color(0xFF434F6D)),
    ),
)

private fun polyline(vararg points: Float, closed: Boolean = false) = Path().apply {
    moveTo(points[0], points[1])
    for (n in 2 until points.size step 2) lineTo(points[n], points[n + 1])
    if (closed) close()
}

// The arrow pointing up and to the right, in a 12-point box.
private val ArrowShaft = polyline(1.5f, 10.5f, 10.5f, 1.5f)
private val ArrowHead = polyline(3.5f, 1.5f, 10.5f, 1.5f, 10.5f, 8.5f)
private val Caret = polyline(0f, 0f, 7f, 0f, 3.5f, 4.5f, closed = true)

// 16-point pictograms for a story's category; the star is filled, the others are lines.
private val Trail = polyline(1.5f, 13f, 6f, 4.5f, 8.6f, 9.2f, 10.4f, 6.4f, 14.5f, 13f)
private val WaveHigh = polyline(1.5f, 6.4f, 4.1f, 4.6f, 6.7f, 6.4f, 9.3f, 4.6f, 11.9f, 6.4f, 14.5f, 4.6f)
private val WaveLow = polyline(1.5f, 11.4f, 4.1f, 9.6f, 6.7f, 11.4f, 9.3f, 9.6f, 11.9f, 11.4f, 14.5f, 9.6f)
private val Star = polyline(8f, 1.5f, 9.6f, 6.4f, 14.5f, 8f, 9.6f, 9.6f, 8f, 14.5f, 6.4f, 9.6f, 1.5f, 8f, 6.4f, 6.4f, closed = true)
private val ThinLine = Stroke(1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round)

private val Hairline = rgba(255, 255, 255, 0.42f)
private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun statText(size: Float, lineHeight: Float, weight: FontWeight, color: Color, spacing: Float = 0f) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    fontFeatureSettings = "tnum",
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val CategoryStyle = statText(12f, 16f, FontWeight.Medium, rgba(255, 255, 255, 0.92f))
private val TagStyle = statText(11.5f, 15f, FontWeight.Medium, Color.White)
private val FigureStyle = statText(78f, FIGURE_HEIGHT, FontWeight.Light, Color.White, -2.5f)
private val HeadlineStyle = statText(13.5f, 16f, FontWeight.SemiBold, Color.White)
private val DetailStyle = statText(13f, 17.5f, FontWeight.Normal, rgba(255, 255, 255, 0.56f))

/**
 * Which story is on screen. [anchor] is the story shown at [since]; while [running], one more story
 * has passed for every STORY seconds after it.
 */
private class StoryClock(auto: Boolean) {
    var anchor by mutableIntStateOf(0)

    // The loop opens on a story that has long settled and moves on FIRST_AT seconds later.
    var since by mutableFloatStateOf(-(STORY - if (auto) FIRST_AT else 0f))
    var running by mutableStateOf(auto)

    /** Seconds since the card appeared, written every frame. */
    var now by mutableFloatStateOf(0f)

    fun index(count: Int): Int {
        if (!running) return anchor
        return (anchor + floor((now - since) / STORY).toInt()) % count
    }

    /** Seconds since the story on screen came in. */
    fun elapsed(): Float {
        val passed = now - since
        return if (running) passed - floor(passed / STORY) * STORY else passed
    }
}

// --- Where every part of the card stands `e` seconds after a story came in. Under reduced motion
// (`calm`) stories cross-fade and nothing else moves. ---

private fun ramp(e: Float, from: Float, over: Float) = ((e - from) / over).coerceIn(0f, 1f)

private fun easeOut(t: Float) = 1f - (1f - t).pow(3)

private fun easeInOut(t: Float) = t * t * (3f - 2f * t)

/** Opacity of the story that is going away. */
private fun leaving(e: Float, calm: Boolean) = 1f - if (calm) ramp(e, 0f, CALM_FADE) else easeOut(ramp(e, 0f, LEAVE))

/** Opacity of one part of the story that is coming in, the parts following each other. */
private fun entering(e: Float, calm: Boolean, part: Int) =
    if (calm) ramp(e, 0f, CALM_FADE) else easeOut(ramp(e, ENTER_AT + ENTER_STAGGER * part, ENTER))

/** How far below its place a part of the incoming story still is. */
private fun lift(e: Float, calm: Boolean, part: Int) = if (calm) 0f else RISE * (1f - entering(e, calm, part))

private fun coverOpacity(e: Float, calm: Boolean) =
    if (calm) 0f else easeInOut(ramp(e, COVER_AT, COVER_IN)) * (1f - easeInOut(ramp(e, COVER_OUT_AT, COVER_OUT)))

private fun coverScale(e: Float) = 1f + COVER_ZOOM * (1f - easeOut(ramp(e, COVER_AT, COVER_OUT_AT + COVER_OUT - COVER_AT)))

/** Share of the full blur, 0…1. */
private fun blurShare(e: Float, calm: Boolean) =
    if (calm) 0f else easeInOut(ramp(e, 0f, LEAVE)) * (1f - easeInOut(ramp(e, BLUR_OUT_AT, BLUR_OUT)))

/** Opacity of story [i]'s glow. Glows are stacked in order, so the one on top does the fading. */
private fun glowOpacity(e: Float, calm: Boolean, i: Int, index: Int, previous: Int): Float {
    val mixed = easeInOut(ramp(e, 0f, if (calm) CALM_FADE else GLOW))
    return when (i) {
        index -> if (i > previous) mixed else 1f
        previous -> if (i > index) 1f - mixed else if (mixed < 1f) 1f else 0f
        else -> 0f
    }
}

private fun countTo(e: Float, calm: Boolean, figure: Int): Int {
    if (calm) return figure
    val from = (figure * COUNT_FROM).roundToInt()
    return (from + (figure - from) * easeOut(ramp(e, ENTER_AT, COUNT))).roundToInt()
}

/**
 * A dark statistics card that tells one travel fact at a time, like a story: a glow in the story's
 * colour, a category and a tag, a large thin figure, two lines of text, two photo pills and a
 * segmented bar. A tap moves to the next story: the card blurs, a photo washes over it in the new
 * colour, and the new figure counts up while its segment starts to fill.
 *
 * @param autoPlay Moves to the next story by itself every few seconds, in a loop.
 * @param onChange Called with the story's index when a tap moves on to it.
 */
@Composable
fun TravelStatStoryCard(
    autoPlay: Boolean = false,
    stories: List<StatStory> = Stories,
    onChange: ((index: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val calm = rememberReducedMotion()
    val auto = autoPlay && !calm
    val looping by rememberUpdatedState(auto)
    val count = stories.size
    val view = LocalView.current

    val clock = remember { StoryClock(auto) }
    var taps by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { clock.now = (it - start) / 1e9f }
    }

    LaunchedEffect(auto, count) {
        clock.anchor = clock.index(count)
        clock.since = clock.now - (STORY - if (auto) FIRST_AT else 0f)
        clock.running = auto
    }

    LaunchedEffect(taps) {
        if (taps == 0) return@LaunchedEffect
        // A touch takes over: the loop picks up from the tapped story once it has rested.
        delay((RESUME_AFTER * 1000).toLong())
        clock.running = looping
    }

    fun advance() {
        val next = (clock.index(count) + 1) % count
        clock.anchor = next
        clock.since = clock.now
        clock.running = false
        taps++
        onChange?.invoke(next)
    }

    val current by remember(count) { derivedStateOf { clock.index(count) } }
    val story = stories[current]

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    LaunchedEffect(pressed) {
        if (pressed) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
        label = "press",
    )
    val shadow = remember { SoftRoundRect(rgba(46, 30, 18, 0.3f), sigma = 22f) }

    Box(
        modifier
            .size(WIDTH.dp, HEIGHT.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = ::advance)
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = "${story.category}. ${story.figure}${story.unit} ${story.headline} ${story.detail}"
                onClick("Shows the next story") {
                    advance()
                    true
                }
            },
    ) {
        Box(Modifier.matchParentSize().bleed(SHADOW_BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(SHADOW_BLEED) { x, y, w, h ->
                    drawIntoCanvas { shadow.draw(it.nativeCanvas, x, y + 20f, w, h, RADIUS) }
                }
            }
        }

        Box(Modifier.matchParentSize().clip(RoundedCornerShape(RADIUS.dp)).background(Color(0xFF151413))) {
            stories.forEachIndexed { i, item ->
                Glow(item.glow) {
                    val index = clock.index(count)
                    glowOpacity(clock.elapsed(), calm, i, index, (index + count - 1) % count)
                }
            }

            // What a story change blurs: the cover photos and the stories themselves.
            Box(
                Modifier.matchParentSize().graphicsLayer {
                    val sigma = BLUR.dp.toPx() * blurShare(clock.elapsed(), calm)
                    renderEffect = if (sigma > 0.5f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val radius = sigmaToRadius(sigma)
                        BlurEffect(radius, radius, TileMode.Decal)
                    } else {
                        null
                    }
                },
            ) {
                stories.forEachIndexed { i, item ->
                    Box(
                        Modifier.matchParentSize().graphicsLayer {
                            alpha = if (i == clock.index(count)) coverOpacity(clock.elapsed(), calm) else 0f
                            scaleX = coverScale(clock.elapsed())
                            scaleY = scaleX
                        },
                    ) {
                        Picture(item.cover, Modifier.matchParentSize())
                        Box(Modifier.matchParentSize().background(item.glow.copy(alpha = 0.5f)))
                    }
                }
                stories.forEachIndexed { i, item ->
                    Content(item, i, count, clock, calm)
                }
            }

            Box(
                Modifier
                    .offset((WIDTH - EDGE - CHIP).dp, EDGE.dp)
                    .size(CHIP.dp)
                    .border(1.dp, Hairline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Arrow(12f, Color.White)
            }

            val width = (BAR_WIDTH - BAR_GAP * (count - 1)) / count
            Row(
                Modifier.offset(((WIDTH - BAR_WIDTH) / 2f).dp, BAR_TOP.dp),
                horizontalArrangement = Arrangement.spacedBy(BAR_GAP.dp),
            ) {
                for (i in stories.indices) {
                    Box(
                        Modifier
                            .size(width.dp, BAR_HEIGHT.dp)
                            .background(rgba(255, 255, 255, 0.22f), CircleShape)
                            .drawBehind {
                                // The story on screen fills its segment; the one before it fades back to dim.
                                val index = clock.index(count)
                                val e = clock.elapsed()
                                val share = if (i == index && !calm) ramp(e, 0f, STORY) else 1f
                                val lit = when (i) {
                                    index -> 1f
                                    (index + count - 1) % count -> 1f - ramp(e, 0f, SEGMENT_FADE)
                                    else -> 0f
                                }
                                drawRoundRect(
                                    color = Color.White.copy(alpha = lit),
                                    size = Size(max(size.height, size.width * share), size.height),
                                    cornerRadius = CornerRadius(size.height / 2f),
                                )
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.Glow(color: Color, opacity: () -> Float) {
    val wide = remember(color) {
        Brush.radialGradient(
            0f to color,
            0.42f to color.copy(alpha = 0.55f),
            0.75f to color.copy(alpha = 0.12f),
            1f to color.copy(alpha = 0f),
            center = Offset(WIDTH * 0.3f, 0f),
            radius = 330f,
        )
    }
    val low = remember(color) {
        Brush.radialGradient(
            listOf(color.copy(alpha = 0.16f), color.copy(alpha = 0f)),
            center = Offset(WIDTH * 0.2f, HEIGHT * 1.05f),
            radius = 200f,
        )
    }
    Canvas(Modifier.matchParentSize().graphicsLayer { alpha = opacity() }) {
        scale(density, density, pivot = Offset.Zero) {
            drawRect(wide, size = Size(WIDTH, HEIGHT))
            drawRect(low, size = Size(WIDTH, HEIGHT))
        }
    }
}

/** A photo that fades in over its tint once it has loaded. */
@Composable
private fun Picture(photo: StatPhoto, modifier: Modifier) {
    val context = LocalContext.current
    val request = remember(photo.photo, context) { ImageRequest.Builder(context).data(photo.photo).crossfade(200).build() }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.background(photo.tint),
    )
}

@Composable
private fun Arrow(size: Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size.dp)) {
        val unit = size.dp.toPx() / 12f
        scale(unit, unit, pivot = Offset.Zero) {
            val line = Stroke(1.4f * 12f / size, cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawPath(ArrowShaft, color, style = line)
            drawPath(ArrowHead, color, style = line)
        }
    }
}

/** One story, in four parts that come in one after another: the header, the figure, the text, the thumbnails. */
@Composable
private fun BoxScope.Content(story: StatStory, i: Int, count: Int, clock: StoryClock, calm: Boolean) {
    // Only the story that comes in moves: each part rises into place as it appears.
    fun Modifier.part(n: Int) = graphicsLayer {
        val index = clock.index(count)
        val e = clock.elapsed()
        if (i == index) {
            alpha = entering(e, calm, n)
            translationY = lift(e, calm, n).dp.toPx()
        } else {
            alpha = if (i == (index + count - 1) % count) leaving(e, calm) else 0f
        }
    }

    val shown by remember(story.figure, i, count, calm) {
        derivedStateOf { if (i == clock.index(count)) countTo(clock.elapsed(), calm, story.figure) else story.figure }
    }

    Box(Modifier.matchParentSize()) {
        Row(
            Modifier
                .part(0)
                .offset(EDGE.dp, EDGE.dp)
                .size((WIDTH - EDGE * 2f - CHIP - 4f).dp, CHIP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(CHIP.dp).background(rgba(255, 255, 255, 0.18f), CircleShape), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(16.dp)) {
                    scale(density, density, pivot = Offset.Zero) {
                        when (story.icon) {
                            StatIcon.Trail -> drawPath(Trail, Color.White, style = ThinLine)
                            StatIcon.Waves -> {
                                drawPath(WaveHigh, Color.White, style = ThinLine)
                                drawPath(WaveLow, Color.White, style = ThinLine)
                            }
                            StatIcon.Star -> drawPath(Star, Color.White)
                        }
                    }
                }
            }
            Spacer(Modifier.width(9.dp))
            BasicText(story.category, style = CategoryStyle, maxLines = 1)
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.height(CHIP.dp).border(1.dp, Hairline, CircleShape).padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(story.tag, style = TagStyle, maxLines = 1)
                Canvas(Modifier.size(7.dp, 4.5.dp)) {
                    scale(density, density, pivot = Offset.Zero) { drawPath(Caret, rgba(255, 255, 255, 0.9f)) }
                }
            }
        }

        Row(Modifier.part(1).offset((PAD - 3f).dp, FIGURE_TOP.dp).height(FIGURE_HEIGHT.dp)) {
            BasicText("$shown${story.unit}", style = FigureStyle, maxLines = 1)
            Spacer(Modifier.width(7.dp))
            Arrow(13f, rgba(255, 255, 255, 0.9f), Modifier.padding(top = 21.dp))
        }

        Column(Modifier.part(2).offset(PAD.dp, TEXT_TOP.dp).width((WIDTH - PAD * 2f).dp)) {
            BasicText(story.headline, style = HeadlineStyle, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            BasicText(story.detail, style = DetailStyle, maxLines = 2)
        }

        Row(Modifier.part(3).offset(PAD.dp, THUMB_TOP.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (thumb in story.thumbs.take(2)) {
                Picture(thumb, Modifier.size(THUMB_WIDTH.dp, THUMB_HEIGHT.dp).clip(CircleShape))
            }
        }
    }
}
