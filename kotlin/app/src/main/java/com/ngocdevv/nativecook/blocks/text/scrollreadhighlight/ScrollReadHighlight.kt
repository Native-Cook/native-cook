package com.ngocdevv.nativecook.blocks.text.scrollreadhighlight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.blocks.text.scrollreadhighlight.ScrollReadHighlightMotion.FADE_WORDS
import com.ngocdevv.nativecook.blocks.text.scrollreadhighlight.ScrollReadHighlightMotion.LOOP
import com.ngocdevv.nativecook.blocks.text.scrollreadhighlight.ScrollReadHighlightMotion.RESUME_AFTER
import com.ngocdevv.nativecook.blocks.text.scrollreadhighlight.ScrollReadHighlightMotion.RETURN_SECONDS
import com.ngocdevv.nativecook.blocks.text.scrollreadhighlight.ScrollReadHighlightMotion.WORDS_PER_MINUTE
import com.ngocdevv.nativecook.blocks.text.scrollreadhighlight.ScrollReadHighlightMotion.easeInOutCubic
import com.ngocdevv.nativecook.blocks.text.scrollreadhighlight.ScrollReadHighlightMotion.frontAt
import com.ngocdevv.nativecook.blocks.text.scrollreadhighlight.ScrollReadHighlightMotion.ghostOpacityAt
import com.ngocdevv.nativecook.blocks.text.scrollreadhighlight.ScrollReadHighlightMotion.ghostTravelAt
import com.ngocdevv.nativecook.blocks.text.scrollreadhighlight.ScrollReadHighlightMotion.mix
import com.ngocdevv.nativecook.blocks.text.scrollreadhighlight.ScrollReadHighlightMotion.scriptAt
import com.ngocdevv.nativecook.blocks.text.scrollreadhighlight.ScrollReadHighlightMotion.seg
import com.ngocdevv.nativecook.blocks.text.scrollreadhighlight.ScrollReadHighlightMotion.wordInk
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 Scroll Read Highlight — "Longhand", a long read whose words ink in as you scroll. A port of the React Native block
 (ScrollReadHighlight.tsx); the numbers are the same.

 autoPlay (LOOP = 10 s, ScrollReadHighlightMotion.kt): rest at the top; three ease-out flicks to 20 %, 42 % and 62 %
 of the scroll range (1.0 – 2.6, 2.7 – 4.3, 4.4 – 6.2 s) with a ghost finger pushing each one up; hold; an
 ease-in-out return to the top 7.0 – 8.6 s; rest. One scroll value drives the reading front, the amber progress line
 and the minutes left.

 Touch: the page is a real verticalScroll; a finger on it stops the script at once, and 2 s after the last touch or
 fling it eases back to the top in 1.1 s and the loop restarts. Reduced motion: every word inked, no loop, scrolling
 stays native.

 Drawing: the article is laid out once (ScrollReadHighlightPage.kt). A frame draws only the visible paragraphs with
 one drawText per line: ink when passed, dim when not reached, and for the paragraph at the front the dim text with
 each reached word drawn again in ink at its smoothstep alpha.
 */

class ScrollReadArticle(val section: String, val title: String, val byline: String, val paragraphs: List<String>)

val SCROLL_READ_ARTICLE = ScrollReadArticle(
    section = "Longhand",
    title = "The last ferry to Orrin",
    byline = "Words by Tomas Vale",
    paragraphs = listOf(
        "The boat leaves at ten past six, whether or not anyone is on it. Most evenings there are four of us: a nurse going home, two boys with a crate of bait, and whoever has misread the timetable and is now committed to an hour on the water.",
        "Nobody talks much on the way out. The engine takes up the room where a conversation would go, and the harbour lights slide behind the stern one at a time, until the only thing left to look at is the long grey line where the sea stops pretending to be flat.",
        "Ines has run this crossing for twenty-six years. She knows the channel by the colour of it, and she will tell you the sandbar moved in the winter of the big storm, as if it had packed a bag and left without saying where it was going.",
        "When the council proposed a bridge, she went to the meeting and said nothing at all. She sat in the second row with her coat on, listened to the engineers talk about journey times, and went back to work in the morning. The bridge was never funded.",
        "What a bridge would save is forty minutes. What it would cost is harder to put in a report. On the ferry you cannot be reached, and you cannot hurry, and for one hour a day the island is still far enough away to be somewhere you arrive.",
        "Halfway across, the nurse usually takes off her shoes. The boys stop arguing. The light goes the colour of weak tea, and for a little while everyone on board looks like someone who has remembered what they were going to say and decided it can wait.",
        "Then the jetty comes up out of the dusk, smaller than you expected, with one lamp and a dog that belongs to nobody. Ines cuts the engine early and lets the tide do the last of the work. The rope goes over, the boat settles, and we all step off without saying goodnight, because we will see each other tomorrow.",
    ),
)

private val BACKGROUND = Color(0xFF15171B)
private val INK = Color(0xFFF1ECE2)
private val AMBER = Color(0xFFE9B872)
private fun cream(a: Float) = Color(241 / 255f, 236 / 255f, 226 / 255f, a)
private fun night(a: Float) = Color(21 / 255f, 23 / 255f, 27 / 255f, a)

private val COLORS = PageColors(
    ink = INK.toArgb(),
    dim = cream(0.26f).toArgb(),
    soft = Color(0xFF9A988F).toArgb(),
    coverTop = Color(0xFF2B3346).toArgb(),
    coverMid = Color(0xFFB0705A).toArgb(),
    coverLow = Color(0xFFEDB27C).toArgb(),
    hill = Color(0xFF3A3A48).toArgb(),
    hillNear = Color(0xFF262A35).toArgb(),
    sun = Color(255 / 255f, 221 / 255f, 170 / 255f, 0.95f).toArgb(),
)

private const val SIDE = 22f
private const val MAX_WIDTH = 620f
private const val NAV = 48f

private const val MODE_AUTO = 0
private const val MODE_MANUAL = 1
private const val MODE_RETURN = 2

/** What one layout of the page fixes: the scroll range and the reading line's ends, in px. */
private class ReadGeo(val page: ReadPage, val maxScroll: Float, val readStart: Float, val readEnd: Float, val reduced: Boolean)

/** Scroll values in px. Drawn values are state; read them only in draw or layer lambdas (and the minutes' derived state). */
private class ReadController {
    var offset by mutableFloatStateOf(0f)
    var clock by mutableFloatStateOf(0f)
    var mode by mutableIntStateOf(MODE_AUTO)

    // Read only by the frame loop and the touch handlers.
    var held = false
    var idle = 0f
    var returnFrom = 0f
    var returnClock = 0f

    /** Advances the script; returns the scroll position the view must follow, or NaN when it should be left alone. */
    fun step(dt: Float, playing: Boolean, maxScroll: Float): Float {
        if (!playing) return Float.NaN
        when (mode) {
            MODE_AUTO -> {
                var t = clock + dt
                if (t >= LOOP) t -= LOOP
                clock = t
                val y = scriptAt(t) * maxScroll
                offset = y
                return y
            }
            MODE_MANUAL -> {
                if (held) return Float.NaN
                idle += dt
                if (idle >= RESUME_AFTER) {
                    returnFrom = offset
                    returnClock = 0f
                    mode = MODE_RETURN
                }
                return Float.NaN
            }
            else -> {
                val k = min(returnClock + dt / RETURN_SECONDS, 1f)
                returnClock = k
                val y = returnFrom * (1 - easeInOutCubic(k))
                offset = y
                if (k >= 1) {
                    clock = 0f
                    mode = MODE_AUTO
                }
                return y
            }
        }
    }

    /** The reading front in words (fractional). Reads [offset]. */
    fun front(g: ReadGeo): Float {
        val p = g.page
        if (g.reduced) return p.wordCount + FADE_WORDS
        val y = offset
        val share = min(max(y / g.maxScroll, 0f), 1f)
        val line = max(y, 0f) + mix(g.readStart, g.readEnd, share)
        // At the very end the last words are carried home, so a finished article is fully inked.
        val ending = seg(share, 0.985f, 1f)
        return frontAt(line, p.lineTop, p.lineHeight, p.lineFirst, p.lineCount) + ending * FADE_WORDS
    }
}

private fun drawArticle(canvas: android.graphics.Canvas, page: ReadPage, offset: Float, viewport: Float, front: Float, allInked: Boolean) {
    canvas.save()
    canvas.translate(0f, -offset)
    if (offset < page.headHeight) page.head(canvas)
    val ink = page.inkPaint
    for (block in page.blocks) {
        if (block.y + block.height < offset - 4 || block.y > offset + viewport + 4) continue
        val para = block.paragraph
        if (allInked || front >= block.last + FADE_WORDS) {
            para.draw(canvas, page.left, block.y, ink)
        } else if (front <= block.first) {
            para.draw(canvas, page.left, block.y, page.dimPaint)
        } else {
            para.draw(canvas, page.left, block.y, page.dimPaint)
            for (word in block.first..block.last) {
                val alpha = wordInk(front, word)
                if (alpha <= 0f) break
                ink.alpha = (alpha * 255).roundToInt()
                canvas.drawText(para.text, page.wordStart[word], page.wordEnd[word], page.wordX[word], block.y + page.wordBaseline[word], ink)
            }
            ink.alpha = 255
        }
    }
    canvas.restore()
}

@Composable
fun ScrollReadHighlight(modifier: Modifier = Modifier, autoPlay: Boolean = true, article: ScrollReadArticle = SCROLL_READ_ARTICLE) {
    val reduced = rememberLiveReducedMotion()
    val playing = autoPlay && !reduced
    val c = remember { ReadController() }
    val scroll = rememberScrollState()

    BoxWithConstraints(modifier.fillMaxSize().background(BACKGROUND)) {
        val density = LocalDensity.current.density
        val insetTop = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding().value
        val insetBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
        val width = maxWidth.value
        val height = maxHeight.value

        val navHeight = insetTop + NAV
        val viewport = max(height - navHeight, 1f)
        val column = min(width - SIDE * 2, MAX_WIDTH)
        val left = (width - column) / 2
        val bodySize = if (width >= 390) 20f else 18f
        val bodyLeading = if (width >= 390) 31f else 28f

        val page = remember(article, left, column, bodySize, bodyLeading, density) {
            makePage(article.paragraphs, article.title, article.byline, left * density, column * density, bodySize, bodyLeading, density, COLORS)
        }
        val contentHeight = page.height + insetBottom * density
        val viewportPx = viewport * density
        val maxScroll = max(contentHeight - viewportPx, 1f)
        // The reading line starts three lines into the body and reaches the last line exactly at the end of the scroll.
        val readStart = page.firstBodyTop + bodyLeading * density * 3.4f
        val readEnd = page.lastBottom + 2 * density - maxScroll
        val allText = remember(article) { (listOf(article.title, article.byline) + article.paragraphs).joinToString("\n\n") }

        val geo = remember(page, maxScroll, readStart, readEnd, reduced) { ReadGeo(page, maxScroll, readStart, readEnd, reduced) }
        val geoNow = rememberUpdatedState(geo)
        val playingNow = rememberUpdatedState(playing)

        LaunchedEffect(reduced, page) {
            c.clock = 0f
            c.mode = MODE_AUTO
            c.offset = 0f
            scroll.scrollTo(0)
        }
        LaunchedEffect(c) {
            var last = -1L
            while (true) {
                withFrameNanos { now ->
                    val dt = if (last < 0) 0.016f else min((now - last) / 1e6f, 48f) / 1000f
                    last = now
                    if (dt > 0f) {
                        val y = c.step(dt, playingNow.value, geoNow.value.maxScroll)
                        if (!y.isNaN()) scroll.dispatchRawDelta(y.roundToInt() - scroll.value.toFloat())
                    }
                }
            }
        }
        // A real scroll (drag or fling) moves the page and keeps the script waiting.
        LaunchedEffect(c, scroll) {
            snapshotFlow { scroll.value }.collect { value ->
                if (c.mode == MODE_MANUAL) {
                    c.offset = value.toFloat()
                    c.idle = 0f
                }
            }
        }

        // The reader.
        Box(
            Modifier
                .offset(y = navHeight.dp)
                .fillMaxWidth()
                .height(viewport.dp)
                .clipToBounds(),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val y = c.offset
                val at = c.front(geo)
                drawIntoCanvas { drawArticle(it.nativeCanvas, page, y, viewportPx, at, reduced) }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    // A finger resting on the page stops the script too, before it becomes a drag.
                    .pointerInput(c) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val down = event.changes.any { it.pressed }
                                if (down && !c.held) {
                                    c.mode = MODE_MANUAL
                                    c.held = true
                                    c.idle = 0f
                                } else if (!down && c.held) {
                                    c.held = false
                                    c.idle = 0f
                                }
                            }
                        }
                    }
                    .verticalScroll(scroll),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((contentHeight / density).dp)
                        .semantics { contentDescription = allText },
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .background(Brush.verticalGradient(0f to BACKGROUND, 0.4f to night(0.6f), 1f to night(0f))),
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height((56 + insetBottom).dp)
                    .background(Brush.verticalGradient(0f to night(0f), 0.6f to night(0.7f), 1f to BACKGROUND)),
            )
        }

        // The nav bar: section, minutes left, the progress line.
        Box(
            Modifier
                .fillMaxWidth()
                .height(navHeight.dp)
                .background(BACKGROUND),
        ) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(top = insetTop.dp, start = SIDE.dp, end = SIDE.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    article.section,
                    style = TextStyle(color = INK, fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
                )
                MinutesLeft(c, geo)
            }
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(cream(0.1f)),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            scaleX = min(max(c.front(geo) / max(page.wordCount, 1), 0f), 1f)
                        }
                        .background(AMBER),
                )
            }
        }

        // The ghost finger of the loop.
        Box(
            Modifier
                .offset((width - 84).dp, (height * 0.66f).dp)
                .size(44.dp)
                .graphicsLayer {
                    val t = c.clock
                    alpha = if (c.mode == MODE_AUTO && playing) ghostOpacityAt(t) else 0f
                    translationY = -ghostTravelAt(t) * 120 * density
                }
                .background(cream(0.2f), CircleShape)
                .border(1.dp, cream(0.55f), CircleShape),
        )
    }
}

@Composable
private fun MinutesLeft(c: ReadController, geo: ReadGeo) {
    val minutes by remember(c, geo) {
        derivedStateOf {
            val wordsLeft = max(geo.page.wordCount - c.front(geo), 0f)
            if (wordsLeft < 0.5f) 0 else max(ceil(wordsLeft / WORDS_PER_MINUTE).toInt(), 1)
        }
    }
    BasicText(
        if (minutes == 0) "Finished" else "$minutes min left",
        style = TextStyle(color = AMBER, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
    )
}
