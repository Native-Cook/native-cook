package com.ngocdevv.nativecook.blocks.cards.cutoutwindowcard

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.surface
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.drop

enum class WindowIcon { Snow, Peak, Tree, House, Sun, Sail, Glass, Fork }

data class WindowDetail(val label: String, val value: String)

/**
 * @param title One or two lines, separated by a line break.
 * @param tint Flat colour shown until the photo arrives.
 * @param details The four small lines at the bottom left.
 * @param icons The four pictograms at the bottom right.
 */
data class WindowPage(
    val title: String,
    val photo: String,
    val tint: Color,
    val details: List<WindowDetail>,
    val icons: List<WindowIcon>,
)

private const val WIDTH = 340f
private const val HEIGHT = 580f
private const val RADIUS = 28f
private const val CARD_WIDTH = 196f
private const val CARD_HEIGHT = 300f
private const val CARD_RADIUS = 8f
private const val CARD_X = (WIDTH - CARD_WIDTH) / 2f
private const val CARD_Y = (HEIGHT - CARD_HEIGHT) / 2f
private const val WINDOW_INSET = 10f
private const val WINDOW_WIDTH = CARD_WIDTH - WINDOW_INSET * 2f
private const val WINDOW_HEIGHT = 118f
private const val WINDOW_RADIUS = 3f

// The window's corner, measured in the panel.
private const val WINDOW_X = CARD_X + WINDOW_INSET
private const val WINDOW_Y = CARD_Y + WINDOW_INSET

// Dp the picture lags behind its slide of the window over one page.
private const val WINDOW_DRIFT = 48f

// Opacity of the dark veil over the photo behind the card.
private const val SCRIM = 0.16f

// The box the titles roll through, and the one of the four small lines.
private const val TITLE_Y = 138f
private const val TITLE_LINE = 30f
private const val TITLE_BOX = 64f
private const val DETAIL_LINE = 10.5f
private const val DETAILS_BOX = DETAIL_LINE * 4f
private const val ICON = 14f
private const val ICON_GAP = 4f
private const val ICONS_WIDTH = ICON * 4f + ICON_GAP * 3f
private const val DETAILS_WIDTH = CARD_WIDTH - 24f - ICONS_WIDTH - 6f

// Distance of the eye from the card, in dp: sets how strong the perspective is.
private const val PERSPECTIVE = 800f

// Degrees the card turns per page per second of slide, and the most it turns.
private const val TILT_GAIN = 2.6f
private const val TILT_MAX = 13f

// How fast the turn follows the speed, per second.
private const val TILT_RATE = 12f

// Dp of the card's side that show per degree of turn.
private const val EDGE = 0.34f

// The spring that carries the pages to rest (mass 1).
private const val STIFFNESS = 150f
private const val DAMPING = 20f

// Finger travel that turns one page.
private const val DRAG_SPAN = 260f

// A release lands on the page the slide would reach after this many seconds at its speed.
private const val THROW_TIME = 0.18f

// autoPlay (seconds): the three default pages go round once per LOOP, the first flick at FIRST
// and the next ones every STEP.
private const val LOOP = 9f
private const val FIRST = 0.8f
private const val STEP = LOOP / 3f

// After a touch the loop waits this long before it starts again.
private const val USER_HOLD = 2.5f

// The card's layer is this much larger than the card on every side, so its shadow is not cut.
private const val CARD_BLEED = 70f

private val Ink = Color(0xFF0F3D2A)

private fun photo(id: String) = "https://images.unsplash.com/photo-$id?auto=format&fit=crop&w=680&h=1160&q=80"

private val Pages = listOf(
    WindowPage(
        title = "Winter\nBreaks",
        photo = photo("1506905925346-21bda4d32df4"),
        tint = Color(0xFF6F7391),
        details = listOf(
            WindowDetail("DATE", "11 Apr 2024"),
            WindowDetail("FROM", "London Gatwick"),
            WindowDetail("FOR", "14 Nights"),
            WindowDetail("BOARD", "Half Board"),
        ),
        icons = listOf(WindowIcon.Snow, WindowIcon.Peak, WindowIcon.Tree, WindowIcon.House),
    ),
    WindowPage(
        title = "Villa\nEscapes",
        photo = photo("1516483638261-f4dbaf036963"),
        tint = Color(0xFFA98A78),
        details = listOf(
            WindowDetail("DATE", "03 May 2024"),
            WindowDetail("FROM", "London Gatwick"),
            WindowDetail("FOR", "4 Nights"),
            WindowDetail("BOARD", "Self-Catering"),
        ),
        icons = listOf(WindowIcon.House, WindowIcon.Sun, WindowIcon.Sail, WindowIcon.Glass),
    ),
    WindowPage(
        title = "Summer\nHot Spots",
        photo = photo("1501785888041-af3ef285b470"),
        tint = Color(0xFF4A9D97),
        details = listOf(
            WindowDetail("DATE", "19 Jul 2024"),
            WindowDetail("FROM", "London Gatwick"),
            WindowDetail("FOR", "7 Nights"),
            WindowDetail("BOARD", "Room Only"),
        ),
        icons = listOf(WindowIcon.Sun, WindowIcon.Sail, WindowIcon.Tree, WindowIcon.Fork),
    ),
)

/** Polylines of a pictogram in a 16 × 16 box, as x, y pairs. The sun also has a circle of radius 2.7 in its middle. */
private fun strokes(icon: WindowIcon): List<FloatArray> = when (icon) {
    WindowIcon.Snow -> listOf(
        floatArrayOf(8f, 1.8f, 8f, 14.2f),
        floatArrayOf(2.6f, 4.9f, 13.4f, 11.1f),
        floatArrayOf(2.6f, 11.1f, 13.4f, 4.9f),
    )
    WindowIcon.Peak -> listOf(floatArrayOf(1.2f, 13.6f, 6f, 4f, 8.6f, 8.8f, 10.6f, 6.2f, 14.8f, 13.6f, 1.2f, 13.6f))
    WindowIcon.Tree -> listOf(
        floatArrayOf(8f, 1.8f, 3.4f, 10.6f, 12.6f, 10.6f, 8f, 1.8f),
        floatArrayOf(8f, 10.6f, 8f, 14.2f),
    )
    WindowIcon.House -> listOf(
        floatArrayOf(1.8f, 8f, 8f, 2.4f, 14.2f, 8f),
        floatArrayOf(3.6f, 6.8f, 3.6f, 13.8f, 12.4f, 13.8f, 12.4f, 6.8f),
        floatArrayOf(6.8f, 13.8f, 6.8f, 10.2f, 9.2f, 10.2f, 9.2f, 13.8f),
    )
    WindowIcon.Sun -> listOf(
        floatArrayOf(8f, 1.4f, 8f, 3f),
        floatArrayOf(8f, 13f, 8f, 14.6f),
        floatArrayOf(1.4f, 8f, 3f, 8f),
        floatArrayOf(13f, 8f, 14.6f, 8f),
        floatArrayOf(3.3f, 3.3f, 4.5f, 4.5f),
        floatArrayOf(12.7f, 3.3f, 11.5f, 4.5f),
        floatArrayOf(3.3f, 12.7f, 4.5f, 11.5f),
        floatArrayOf(12.7f, 12.7f, 11.5f, 11.5f),
    )
    WindowIcon.Sail -> listOf(
        floatArrayOf(8f, 1.8f, 8f, 10f),
        floatArrayOf(8f, 2.6f, 12.8f, 10f, 8f, 10f),
        floatArrayOf(6.6f, 4.8f, 3.6f, 10f, 6.6f, 10f, 6.6f, 4.8f),
        floatArrayOf(2f, 12f, 14f, 12f, 11.8f, 14.4f, 4.2f, 14.4f, 2f, 12f),
    )
    WindowIcon.Glass -> listOf(
        floatArrayOf(3f, 2.6f, 13f, 2.6f, 8f, 8.4f, 3f, 2.6f),
        floatArrayOf(8f, 8.4f, 8f, 13.8f),
        floatArrayOf(5.2f, 13.8f, 10.8f, 13.8f),
    )
    WindowIcon.Fork -> listOf(
        floatArrayOf(4.6f, 1.8f, 4.6f, 14.2f),
        floatArrayOf(2.8f, 1.8f, 2.8f, 5.6f, 6.4f, 5.6f, 6.4f, 1.8f),
        floatArrayOf(11.2f, 14.2f, 11.2f, 1.8f, 13.2f, 5f, 13.2f, 8.2f, 11.2f, 8.2f),
    )
}

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private val TitleStyle = TextStyle(
    color = Ink,
    fontSize = 31.sp,
    lineHeight = TITLE_LINE.sp,
    fontWeight = FontWeight.ExtraBold,
    letterSpacing = (-1.3).sp,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val DetailStyle = TextStyle(
    color = Ink,
    fontSize = 7.5.sp,
    lineHeight = DETAIL_LINE.sp,
    fontWeight = FontWeight.Medium,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/** Per-frame state of the pages. [pos] is the page in view, with fractions while it slides. */
private class Pager {
    val pos = mutableFloatStateOf(0f)
    val target = mutableFloatStateOf(0f)

    /** Pages per second. */
    var velocity = 0f

    /** Degrees the card is turned around its vertical axis. */
    val tilt = mutableFloatStateOf(0f)

    /** Where the loop started counting from, and its clock. */
    var base = 0f
    var clock = 0f

    /** Seconds the loop still waits after a touch. */
    var hold = 0f
    var touching = false

    /** Where the pages were when the finger took them. */
    var grabbed = 0f
    var previous = 0f
}

private fun wrapIndex(index: Int, count: Int) = ((index % count) + count) % count

/** Pages flicked [clock] seconds into the autoPlay loop. */
private fun flicks(clock: Float, count: Int): Float {
    var done = 0f
    for (index in 0 until count) if (clock >= FIRST + STEP * index) done += 1f
    return done
}

/** Where page [index] stands: 0 in view, 1 one page to the right, −1 one page to the left. */
private fun relative(index: Int, pos: Float, count: Int): Float {
    var place = (index - pos) % count
    if (place <= -count / 2f) place += count
    if (place > count / 2f) place -= count
    return place
}

private fun smooth(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/** Opacity of a text that rolls through its box with the page at [place]: it fades only as it leaves the box. */
private fun rollFade(place: Float) = smooth((0.75f - abs(place)) / 0.3f)

/** Reduced motion: the pages stay in place and the one further away fades over the nearer one. */
private fun crossFade(place: Float) = if (abs(place) <= 0.5f) 1f else max(0f, 1f - abs(place))

/**
 * Opacity of pictogram number [order] of the page at [place]: gone before the page is half-way
 * out, and the further right the later it comes.
 */
private fun popFade(place: Float, order: Int) = smooth((0.47f - 0.03f * order - abs(place)) / 0.2f)

/**
 * A photo panel with a cream card standing in its middle. The top of the card is a window onto the
 * same photo, lined up with the one behind. A sideways swipe turns the page: the photos slide, the
 * card turns around its vertical axis with the speed of the slide and straightens as it settles,
 * the picture in the window slides at its own slower rate, and the texts roll over.
 *
 * @param pages The pages, in a ring. At least three.
 * @param autoPlay Turns a page every three seconds, as if a finger flicked it.
 * @param onChange Called with the index of the page the panel is settling on.
 */
@Composable
fun CutoutWindowCard(
    pages: List<WindowPage> = Pages,
    autoPlay: Boolean = false,
    onChange: ((index: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val looping by rememberUpdatedState(auto)
    val count = pages.size
    val view = LocalView.current
    val changed by rememberUpdatedState(onChange)

    val pager = remember { Pager() }

    LaunchedEffect(auto) {
        pager.hold = 0f
        pager.clock = 0f
        pager.base = pager.target.floatValue
    }

    LaunchedEffect(count) {
        var last = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            val dt = min((frame - last) / 1e9f, 0.064f)
            last = frame
            if (dt <= 0f) continue
            var p = pager.pos.floatValue
            var goal = pager.target.floatValue

            if (looping && pager.hold <= 0f) {
                val loop = STEP * count
                pager.clock += dt
                if (pager.clock >= loop) {
                    // Once round the ring: the same pages under smaller numbers.
                    pager.clock -= loop
                    p -= count
                    pager.previous -= count
                }
                goal = pager.base + flicks(pager.clock, count)
            } else if (pager.hold > 0f && !pager.touching) {
                pager.hold -= dt
                if (pager.hold <= 0f) {
                    if (p == goal) {
                        p = wrapIndex(p.roundToInt(), count).toFloat()
                        pager.previous = p
                        goal = p
                        pager.base = p
                        pager.clock = 0f
                    } else {
                        pager.hold = 0.01f
                    }
                }
            }

            if (pager.touching) {
                // The finger moves the pages; keep a smoothed speed for the release and the turn.
                pager.velocity += ((p - pager.previous) / dt - pager.velocity) * min(1f, dt * 30f)
            } else {
                pager.velocity += (STIFFNESS * (goal - p) - DAMPING * pager.velocity) * dt
                p += pager.velocity * dt
                if (abs(goal - p) < 0.0004f && abs(pager.velocity) < 0.004f) {
                    p = goal
                    pager.velocity = 0f
                }
            }

            val lean = (-pager.velocity * TILT_GAIN).coerceIn(-TILT_MAX, TILT_MAX)
            var turned = pager.tilt.floatValue + (lean - pager.tilt.floatValue) * (1f - exp(-dt * TILT_RATE))
            if (pager.velocity == 0f && abs(turned) < 0.01f) turned = 0f

            pager.pos.floatValue = p
            pager.previous = p
            pager.target.floatValue = goal
            pager.tilt.floatValue = turned
        }
    }

    val current by remember(count) { derivedStateOf { wrapIndex(pager.target.floatValue.roundToInt(), count) } }
    LaunchedEffect(count) {
        snapshotFlow { wrapIndex(pager.target.floatValue.roundToInt(), count) }.drop(1).collect { changed?.invoke(it) }
    }

    fun place(index: Int) = relative(index, pager.pos.floatValue, count)
    fun tilt() = if (reducedMotion) 0f else pager.tilt.floatValue

    Box(
        modifier
            .size(WIDTH.dp, HEIGHT.dp)
            .clip(RoundedCornerShape(RADIUS.dp))
            .background(Color.Black)
            .clearAndSetSemantics {
                contentDescription = pages[current].title.replace("\n", " ")
                stateDescription = pages[current].details.joinToString { "${it.label} ${it.value}" }
            }
            .pointerInput(count) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        // A touch takes over from the loop, which starts again from rest a little later.
                        pager.touching = true
                        pager.grabbed = pager.pos.floatValue
                        pager.hold = USER_HOLD
                    },
                    onDragEnd = {
                        pager.touching = false
                        pager.hold = USER_HOLD
                        val home = Math.round(pager.grabbed).toFloat()
                        val landing = Math.round(pager.pos.floatValue + pager.velocity * THROW_TIME).toFloat()
                        val next = landing.coerceIn(home - 1f, home + 1f)
                        if (next != Math.round(pager.target.floatValue).toFloat()) {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                        pager.target.floatValue = next
                    },
                    onDragCancel = { pager.touching = false },
                ) { change, amount ->
                    change.consume()
                    pager.hold = USER_HOLD
                    // One page at most per swipe.
                    val home = Math.round(pager.grabbed).toFloat()
                    pager.pos.floatValue = (pager.pos.floatValue - amount / density / DRAG_SPAN).coerceIn(home - 1f, home + 1f)
                }
            },
    ) {
        pages.forEachIndexed { index, page ->
            val far by remember(index, count) { derivedStateOf { abs(place(index)) > 0.5f } }
            Box(
                Modifier
                    .zIndex(if (far) 1f else 0f)
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = if (reducedMotion) 0f else place(index) * size.width
                        alpha = if (reducedMotion) crossFade(place(index)) else 1f
                    },
            ) { Photo(page) }
        }
        // The photo behind is a little darker than the picture in the window.
        Box(Modifier.zIndex(2f).fillMaxSize().background(rgba(8, 14, 11, SCRIM)))

        Box(
            Modifier
                .zIndex(3f)
                .offset((CARD_X - CARD_BLEED).dp, (CARD_Y - CARD_BLEED).dp)
                // The layer is wider than the panel, so it must not be squeezed into it.
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .size((CARD_WIDTH + CARD_BLEED * 2f).dp, (CARD_HEIGHT + CARD_BLEED * 2f).dp)
                .graphicsLayer {
                    rotationY = tilt()
                    // Android measures the eye's distance in 72nds of an inch of raw pixels.
                    cameraDistance = PERSPECTIVE.dp.toPx() / 72f
                },
            contentAlignment = Alignment.Center,
        ) {
            val shadow = remember { SoftRoundRect(rgba(0, 0, 0, 0.34f), sigma = 22f) }
            val shape = RoundedCornerShape(CARD_RADIUS.dp)
            Canvas(Modifier.fillMaxSize()) {
                surface(CARD_BLEED.dp) { x, y, w, h -> drawIntoCanvas { shadow.draw(it.nativeCanvas, x, y + 14f, w, h, CARD_RADIUS) } }
            }
            Box(Modifier.size(CARD_WIDTH.dp, CARD_HEIGHT.dp)) {
                // The card's side: it shows on the edge that turns towards the viewer.
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationX = (-tilt() * EDGE).dp.toPx() }
                        .background(Color(0xFFD3CBB7), shape),
                )
                Box(Modifier.fillMaxSize().background(Color(0xFFF7F3EA), shape))

                // The window: one slide per page, each holding the photo where the panel shows it.
                Box(
                    Modifier
                        .offset(WINDOW_INSET.dp, WINDOW_INSET.dp)
                        .size(WINDOW_WIDTH.dp, WINDOW_HEIGHT.dp)
                        .clip(RoundedCornerShape(WINDOW_RADIUS.dp))
                        .border(1.dp, Ink.copy(alpha = 0.16f), RoundedCornerShape(WINDOW_RADIUS.dp)),
                ) {
                    pages.forEachIndexed { index, page ->
                        val far by remember(index, count) { derivedStateOf { abs(place(index)) > 0.5f } }
                        Box(
                            Modifier
                                .zIndex(if (far) 1f else 0f)
                                .fillMaxSize()
                                .graphicsLayer {
                                    translationX = if (reducedMotion) 0f else place(index) * size.width
                                    alpha = if (reducedMotion) crossFade(place(index)) else 1f
                                    clip = true
                                },
                        ) {
                            Box(
                                Modifier
                                    .offset((-WINDOW_X).dp, (-WINDOW_Y).dp)
                                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                                    .graphicsLayer {
                                        translationX = if (reducedMotion) 0f else (-place(index) * WINDOW_DRIFT).dp.toPx()
                                    },
                            ) { Photo(page) }
                        }
                    }
                }

                // The title rolls up and out of its box as the next one rolls in from below.
                Box(Modifier.offset(12.dp, TITLE_Y.dp).size((CARD_WIDTH - 24f).dp, TITLE_BOX.dp).clipToBounds()) {
                    pages.forEachIndexed { index, page ->
                        BasicText(
                            page.title,
                            Modifier
                                .offset(0.dp, 2.dp)
                                .wrapContentSize(Alignment.TopStart, unbounded = true)
                                .graphicsLayer {
                                    translationY = if (reducedMotion) 0f else (place(index) * TITLE_BOX).dp.toPx()
                                    alpha = rollFade(place(index))
                                },
                            style = TitleStyle,
                            maxLines = 2,
                            softWrap = false,
                        )
                    }
                }

                Box(
                    Modifier
                        .offset(12.dp, (CARD_HEIGHT - 14f - DETAILS_BOX).dp)
                        .size(DETAILS_WIDTH.dp, DETAILS_BOX.dp)
                        .clipToBounds(),
                ) {
                    pages.forEachIndexed { index, page ->
                        Column(
                            Modifier.graphicsLayer {
                                translationY = if (reducedMotion) 0f else (place(index) * DETAILS_BOX).dp.toPx()
                                alpha = rollFade(place(index))
                            },
                        ) {
                            for (detail in page.details) {
                                BasicText(
                                    buildAnnotatedString {
                                        withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(detail.label) }
                                        append(" ${detail.value}")
                                    },
                                    Modifier.height(DETAIL_LINE.dp).wrapContentHeight(),
                                    style = DetailStyle,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                }

                // The pictograms change one after another.
                pages.forEachIndexed { index, page ->
                    Row(
                        Modifier.offset((CARD_WIDTH - 12f - ICONS_WIDTH).dp, (CARD_HEIGHT - 14f - ICON).dp),
                        horizontalArrangement = Arrangement.spacedBy(ICON_GAP.dp),
                    ) {
                        page.icons.forEachIndexed { order, icon ->
                            Glyph(
                                icon,
                                Modifier.graphicsLayer {
                                    val shown = popFade(place(index), order)
                                    val grown = if (reducedMotion) 1f else 0.6f + 0.4f * shown
                                    alpha = shown
                                    scaleX = grown
                                    scaleY = grown
                                },
                            )
                        }
                    }
                }

                // Turned away from the light, the face gets a little darker.
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = abs(tilt()) * 0.006f }.background(Color.Black, shape))
            }
        }
    }
}

/** The photo over a flat tint of its main colour, faded in when it arrives. */
@Composable
private fun Photo(page: WindowPage) {
    AsyncImage(
        model = ImageRequest.Builder(LocalPlatformContext.current).data(page.photo).crossfade(200).build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(WIDTH.dp, HEIGHT.dp).background(page.tint),
    )
}

/** A small line pictogram, drawn from the same strokes in every stack. */
@Composable
private fun Glyph(icon: WindowIcon, modifier: Modifier = Modifier) {
    val lines = remember(icon) { strokes(icon) }
    Canvas(modifier.size(ICON.dp)) {
        val unit = size.width / 16f
        val path = Path()
        for (line in lines) {
            for (index in line.indices step 2) {
                if (index == 0) path.moveTo(line[0] * unit, line[1] * unit) else path.lineTo(line[index] * unit, line[index + 1] * unit)
            }
        }
        val stroke = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawPath(path, Ink, style = stroke)
        if (icon == WindowIcon.Sun) drawCircle(Ink, 2.7f * unit, Offset(8f * unit, 8f * unit), style = stroke)
    }
}
