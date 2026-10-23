package com.ngocdevv.nativecook.blocks.onboarding.logowindowreveal

import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.surface
import kotlin.math.max

/**
 * @param whenLabel How long until it, written out.
 * @param photo A photo's address; the card and the screen crop it.
 */
data class CountdownEvent(val title: String, val whenLabel: String, val photo: String)

private fun photo(id: String, width: Int = 660, height: Int = 660) =
    "https://images.unsplash.com/photo-$id?auto=format&fit=crop&w=$width&h=$height&q=80"

val SampleEvents = listOf(
    CountdownEvent("Tokyo Trip", "IN 3 WEEKS, 2 DAYS", photo("1540959733332-eab4deabeeaf")),
    CountdownEvent("Kate's Birthday", "IN 6 DAYS", photo("1544005313-94ddf0286df2", 720, (720 / HERO_ASPECT).toInt())),
    CountdownEvent("Paris", "IN 78 DAYS", photo("1502602898657-3e91760cbb34")),
    CountdownEvent("New York", "IN 85 DAYS", photo("1496442226666-8d4d0e62e6e9")),
    CountdownEvent("Maldives", "IN 51 DAYS", photo("1573843981267-be1999ff37cd")),
    CountdownEvent("Santorini", "IN 129 DAYS", photo("1570077188670-e3a8d69ac5ff")),
    CountdownEvent("Summit Hike", "IN 1 YEAR", photo("1464822759023-fed622ff2c3b")),
    CountdownEvent("Bruno's Birthday", "IN 14 DAYS, 3 HOURS", photo("1506794778202-cad84cf45f1d")),
)

// The events of the side columns' rows, from the second row down.
private val Left = listOf(3, 5, 0, 6, 2, 1, 4)
private val Right = listOf(6, 4, 7, 2, 5, 0, 3)

private val Background = Color(0xFFFFFDFF)
private val Ink = Color(0xFF010103)
private val HaloInk = Color(0xFF808083)
private val Placeholder = Color(0xFF26262B)
private val NameInk = Color(0xFF1B1B1B)
private val TaglineInk = Color(0xFF3B4248)
private val Shade = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))

// A heavy condensed face the system carries.
private val Condensed = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))

// Where the hero's lines, the name and the tagline start, as shares of the screen's height.
private const val HERO_TEXT_Y = 0.645f
private const val NAME_Y = 0.505f
private const val TAGLINE_Y = 0.607f
private const val TAGLINE_WIDTH = 290f

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun revealText(size: Float, lineHeight: Float, weight: FontWeight, color: Color, spacing: Float = 0f, family: FontFamily = FontFamily.Default) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    fontFamily = family,
    letterSpacing = spacing.sp,
    textAlign = TextAlign.Center,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val CardWhenStyle = revealText(10f, 14f, FontWeight.Medium, Color.White.copy(alpha = 0.85f), 1f)
private val CardTitleStyle = revealText(32f, 36f, FontWeight.Bold, Color.White, family = Condensed)
private val HeroWhenStyle = revealText(12f, 16f, FontWeight.Medium, Color.White.copy(alpha = 0.85f), 1.2f)
private val HeroTitleStyle = revealText(52f, 56f, FontWeight.Bold, Color.White, family = Condensed)
private val LetterStyle = revealText(46f, 56f, FontWeight.ExtraBold, NameInk)
private val TaglineStyle = revealText(17f, 25f, FontWeight.Normal, TaglineInk)

/** The stretch of the show being played against the clock: from [from] at [t0] until [to], where it stops. */
private data class Run(val t0: Double, val from: Double, val to: Double) {
    fun position(now: Double) = minOf(to, from + max(0.0, now - t0))
}

/** What the touch handlers keep between calls. */
private class Hold {
    // When autoPlay may go on after a touch; infinite while no touch is waited out.
    var resume = Double.POSITIVE_INFINITY

    // Where a drag on the list started; not a number when the drag does not scroll.
    var grabbed = Double.NaN
    var dragged = 0.0
}

/**
 * An app's intro. Its mark, a disc with a window in it, swells and flies at the viewer until the
 * window has swallowed the screen: what was seen through it is a photo, which then shrinks into
 * one card of a list. The list scrolls, zooms out into three columns, and runs up until its last
 * row hangs over the app's name.
 *
 * @param events Eight events, top to bottom. The mark opens on the second; the last hangs in the middle at the end.
 * @param name The app's name, spelled out letter by letter at the end.
 * @param autoPlay Runs the show by itself every [LOOP] seconds.
 * @param onDone Called when the show has reached its end, with the name on the screen.
 */
@Composable
fun LogoWindowReveal(
    events: List<CountdownEvent> = SampleEvents,
    name: String = "UNTIL",
    tagline: String = "Countdown to all of your special moments",
    autoPlay: Boolean = false,
    onDone: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    // The frame loop and the touch handlers outlive a recomposition: they read what the caller passes through these.
    val auto by rememberUpdatedState(autoPlay)
    val done by rememberUpdatedState(onDone)

    // Seconds since the first frame, and how far a finger scrolled the list. Read while drawing.
    val clock = remember { mutableDoubleStateOf(0.0) }
    val extra = remember { mutableDoubleStateOf(0.0) }
    var run by remember { mutableStateOf(Run(0.0, 0.0, 0.0)) }
    val hold = remember { Hold() }

    LaunchedEffect(autoPlay) {
        run = Run(clock.doubleValue, 0.0, if (autoPlay) LOOP else 0.0)
        hold.resume = Double.POSITIVE_INFINITY
        extra.doubleValue = 0.0
    }

    LaunchedEffect(Unit) {
        val first = withFrameNanos { it }
        while (true) {
            val before = clock.doubleValue
            val now = (withFrameNanos { it } - first) / 1e9
            clock.doubleValue = now
            val r = run
            val end = r.t0 + r.to - r.from
            if (r.to == END && before < end && now >= end) done?.invoke()
            if (r.to == LOOP && now >= end) {
                // Closed: the mark again. The loop goes on from when it was due, so that every one is LOOP long.
                extra.doubleValue = 0.0
                run = Run(end, 0.0, if (auto && hold.resume == Double.POSITIVE_INFINITY) LOOP else 0.0)
            } else if (auto && now >= hold.resume && now >= end) {
                // The touches are over: close the show if it is open, and start again.
                hold.resume = Double.POSITIVE_INFINITY
                run = if (r.to == END) Run(now, CLOSE_AT, LOOP) else Run(now, 0.0, LOOP)
            }
        }
    }

    fun position() = run.position(clock.doubleValue)

    // Reduced motion shows the mark and the end only, and fades between the two.
    fun time() = if (still) END else position()
    fun shown() = if (still) stillEnd(position()) else 1.0
    fun markTime() = if (still) 0.0 else position()

    /** A touch takes over: the show stops at its end, and autoPlay starts again a while later. */
    fun touched() {
        if (auto) hold.resume = clock.doubleValue + USER_HOLD
        val r = run
        if (r.to == LOOP && position() < END) run = r.copy(to = END)
    }

    fun tapped() {
        val t = position()
        val idle = run.to == 0.0
        touched()
        // The mark starts the show; the end closes it.
        if (idle) run = Run(clock.doubleValue, SWELL_AT, END)
        else if (t >= END && t < CLOSE_AT) run = Run(clock.doubleValue, CLOSE_AT, LOOP)
    }

    fun released() {
        val r = run
        if (r.t0 == Double.POSITIVE_INFINITY) run = r.copy(t0 = clock.doubleValue + SCROLL_HOLD)
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Background)
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = "$name. $tagline"
            }
            .pointerInput(Unit) { detectTapGestures { tapped() } }
            // A finger on the list scrolls it, and holds the show where it is.
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = {
                        touched()
                        val t = position()
                        hold.grabbed = if (t >= LIST_AT && t < HANG_AT) extra.doubleValue else Double.NaN
                        hold.dragged = 0.0
                        if (!hold.grabbed.isNaN()) run = Run(Double.POSITIVE_INFINITY, t, run.to)
                    },
                    onDragEnd = { released() },
                    onDragCancel = { released() },
                ) { _, amount ->
                    hold.dragged += amount / density
                    if (!hold.grabbed.isNaN()) extra.doubleValue = hold.grabbed - hold.dragged
                }
            },
    ) {
        val width by rememberUpdatedState(maxWidth.value.toDouble())
        val height by rememberUpdatedState(maxHeight.value.toDouble())
        fun event(index: Int) = events[index % events.size]
        val place: (Int, Int) -> CardAt = { column, row -> place(column, row, time(), width, height, extra.doubleValue) }

        for (row in 0 until ROWS) EventCard(event(row), 0, row, place, ::shown)
        Left.forEachIndexed { i, index -> EventCard(event(index), -1, i + 1, place, ::shown) }
        Right.forEachIndexed { i, index -> EventCard(event(index), 1, i + 1, place, ::shown) }

        Row(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = (maxHeight.value * NAME_Y).dp)
                .height(56.dp)
                .graphicsLayer {
                    val out = nameOut(time())
                    alpha = (out.opacity * shown()).toFloat()
                    scaleX = out.scale.toFloat()
                    scaleY = out.scale.toFloat()
                },
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            name.forEachIndexed { index, char ->
                BasicText(
                    char.toString(), style = LetterStyle,
                    modifier = Modifier.graphicsLayer {
                        val l = letter(time(), index)
                        alpha = l.opacity.toFloat()
                        translationY = l.rise.dp.toPx()
                    },
                )
            }
        }
        BasicText(
            tagline, style = TaglineStyle,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (maxHeight.value * TAGLINE_Y).dp)
                .width(TAGLINE_WIDTH.dp)
                .graphicsLayer { alpha = (tagline(time()) * shown()).toFloat() },
        )

        // The photo seen through the mark's window, which becomes the screen and then a card.
        val window = remember { Path() }
        val photoWidth = max(maxWidth.value, maxHeight.value * HERO_ASPECT.toFloat())
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = hero(markTime(), width, height).opacity.toFloat() }
                .drawWithContent {
                    val h = hero(markTime(), width, height)
                    window.rewind()
                    window.addRoundRect(h.box(density))
                    clipPath(window) {
                        this@drawWithContent.drawContent()
                        val top = (h.cy * density).toFloat()
                        val half = (h.h / 2 * density).toFloat()
                        drawRect(Brush.verticalGradient(Shade, top, top + half), Offset(0f, top), Size(size.width, half))
                    }
                }
                .background(Placeholder),
            contentAlignment = Alignment.Center,
        ) {
            // The photo covers the screen with its whole width, and shrinks with the box when that becomes a card.
            Photo(
                event(HERO).photo,
                Modifier.requiredSize(photoWidth.dp, (photoWidth / HERO_ASPECT.toFloat()).dp).graphicsLayer {
                    val k = hero(markTime(), width, height).k.toFloat()
                    scaleX = k
                    scaleY = k
                },
            )
        }

        val disc = remember { Path() }
        Canvas(Modifier.matchParentSize()) {
            surface(0.dp) { _, _, w, h ->
                val m = mark(markTime(), width, height)
                drawCircle(HaloInk, (MARK / 2 * HALO * m.scale).toFloat(), Offset(w / 2f, h / 2f), alpha = m.halo.toFloat())
                disc.markOutline(m.scale, width, height)
                drawPath(disc, Ink, alpha = (m.opacity * (if (still) 1 - shown() else 1.0)).toFloat())
            }
        }

        val words = remember(events) { event(HERO).title.split(" ", limit = 2) }
        Column(
            Modifier.align(Alignment.TopCenter).offset(y = (maxHeight.value * HERO_TEXT_Y).dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                event(HERO).whenLabel, style = HeroWhenStyle, maxLines = 1,
                modifier = Modifier.graphicsLayer {
                    val l = heroLine(markTime(), 0)
                    alpha = l.opacity.toFloat()
                    translationY = l.rise.dp.toPx()
                },
            )
            Spacer(Modifier.height(22.dp))
            BasicText(
                words.joinToString("\n"), style = HeroTitleStyle,
                modifier = Modifier.graphicsLayer {
                    val l = heroLine(markTime(), 1)
                    alpha = l.opacity.toFloat()
                    translationY = l.rise.dp.toPx()
                },
            )
        }
    }
}

@Composable
private fun Photo(address: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val request = remember(address, context) { ImageRequest.Builder(context).data(address).crossfade(200).build() }
    AsyncImage(request, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
}

/**
 * A square card: the event's photo under a shade, with its two lines at the bottom.
 *
 * @param place Where it is now, read while drawing.
 * @param shown How much of all the cards shows, read while drawing.
 */
@Composable
private fun EventCard(event: CountdownEvent, column: Int, row: Int, place: (Int, Int) -> CardAt, shown: () -> Double) {
    Box(
        Modifier
            .size(CARD.dp)
            .graphicsLayer {
                val p = place(column, row)
                alpha = (p.opacity * shown()).toFloat()
                translationX = (p.x - CARD / 2).dp.toPx()
                translationY = (p.y - CARD / 2).dp.toPx()
                scaleX = p.scale.toFloat()
                scaleY = p.scale.toFloat()
            }
            .clip(RoundedCornerShape(CARD_RADIUS.dp))
            .background(Placeholder),
    ) {
        Photo(event.photo, Modifier.fillMaxSize())
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height((CARD / 2).dp).background(Brush.verticalGradient(Shade)))
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 30.dp)
                .graphicsLayer { alpha = place(column, row).text.toFloat() },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(event.whenLabel, style = CardWhenStyle, maxLines = 1)
            Spacer(Modifier.height(6.dp))
            BasicText(event.title, style = CardTitleStyle, maxLines = 1)
        }
    }
}
