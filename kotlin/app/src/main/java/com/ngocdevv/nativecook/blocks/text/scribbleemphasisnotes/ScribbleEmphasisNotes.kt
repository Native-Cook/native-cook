package com.ngocdevv.nativecook.blocks.text.scribbleemphasisnotes

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// "Folio": a page of study notes that a marker annotates by itself, on real measured text. The geometry is
// ScribbleStrokes.kt (built once per layout), the text measurement ScribblePage.kt, the finger's highlights
// ScribbleMarks.kt. The paper, the text and every mark are one canvas, so the highlighter really multiplies.

private object Copy {
    const val BRAND = "Folio"
    const val COURSE = "Biology · Unit 4"
    const val TITLE = "Cellular respiration"
    const val META = "Lecture 7 · Thu 12 Nov · 4 min read"
    const val LABEL = "Key idea"
    const val PAGE = "p. 142"
    const val TRAY = "Highlighter"
    const val HINT = "Tap a sentence, or drag across words"
    const val MARKED = "Marked on this page"
}

/** Plain ASCII, so character offsets are the same in every stack. */
private const val TEXT =
    "Cellular respiration turns glucose into energy the cell can spend. " +
        "Most of it is released in the mitochondria, where the electron transport chain pumps protons across the inner membrane. " +
        "That gradient drives ATP synthase, making about 30 ATP per glucose. " +
        "Without oxygen the chain backs up and the cell falls back on fermentation."

private const val TERM_CIRCLE = "mitochondria"
private const val TERM_HIGHLIGHT = "electron transport chain"
private const val TERM_UNDERLINE = "about 30 ATP per glucose"
private const val BRACKET_SENTENCE = 3

private class MarkedEntry(val term: String, val how: String, val mark: Int)

private val MARKED = listOf(
    MarkedEntry("mitochondria", "Circled", Mark.CIRCLE),
    MarkedEntry("electron transport chain", "Highlighted", Mark.HIGHLIGHT),
    MarkedEntry("30 ATP per glucose", "Underlined", Mark.UNDERLINE),
)

private class Tint(val name: String, val color: Color)

/** Highlighter inks (sRGB). Multiplied over the page. */
private val TINTS = listOf(
    Tint("Lemon", Color(0xFFFFE14A)),
    Tint("Rose", Color(0xFFFF9FB6)),
    Tint("Mint", Color(0xFF8FE6B4)),
)

// Palette: warm paper, one ink, two pens.
private val DESK = Color(0xFFECE7DD)
private val PAPER = Color(0xFFFBF8F1)
private val INK = Color(0xFF24211C)
private val INK_SOFT = Color(0xFF8A8273)
private val HAIRLINE = Color(36 / 255f, 33 / 255f, 28 / 255f, 0.09f)
private val PEN_RED = Color(0xFFD5432E)
private val PEN_BLUE = Color(0xFF2D4FCB)
private val WARM_SHADOW = Color(64 / 255f, 50 / 255f, 28 / 255f, 1f)

// Layout, in dp.
private const val SIDE = 16f
private const val MAX_WIDTH = 560f
private const val HEADER = 98f
private const val CARD_RADIUS = 22f
private const val CARD_PAD = 20f
private const val CARD_HEAD = 46f
private const val GUTTER = 34f
private const val GUTTER_EDGE = 6f
private const val NOTE_AREA = 60f
private const val NOTE_DROP = 40.0
private const val NOTE_BACK = 66.0
private const val TRAY_GAP = 14f
private const val TRAY = 58f
private const val ROW = 44f
private const val LIST_HEAD = 32f

private const val MODE_AUTO = 0
private const val MODE_MANUAL = 1

/** The marks' geometry for one layout of the page. */
private class Art(
    val circle: FloatArray,
    val highlight: FloatArray,
    val underline: FloatArray,
    val bracket: FloatArray,
    val note: FloatArray,
    val arrow: FloatArray,
)

private fun makeArt(page: ScribblePage, textX: Float, textY: Float, textWidth: Float, fontSize: Float): Art {
    fun find(phrase: String): List<Band> {
        val start = max(TEXT.indexOf(phrase), 0)
        return page.bands(start, start + phrase.length)
    }
    val right = (textX + textWidth).toDouble()
    val sentence = min(BRACKET_SENTENCE, page.sentenceFirst.size - 1)
    val firstLine = page.wordLine.getOrElse(page.sentenceFirst[sentence]) { 0 }
    val lastLine = page.wordLine.getOrElse(page.sentenceLast[sentence]) { firstLine }
    val (bracket, nose) = bracketMark(
        right + Brace.GAP,
        (page.lineBase[firstLine] - fontSize * 0.86f).toDouble(),
        (page.lineBase[lastLine] + fontSize * 0.26f).toDouble(),
        29.0,
    )
    val noteX = right - NOTE_BACK
    val noteBase = textY + page.height + NOTE_DROP
    val (note, noteWidth) = noteMark("exam!", noteX, noteBase, 37.0)
    val noteEndX = noteX + noteWidth * cos(NoteSpec.TILT)
    val noteEndY = noteBase + noteWidth * sin(NoteSpec.TILT)
    val from = P(noteEndX + 3, noteEndY - NoteSpec.X_HEIGHT * 2.1)
    val to = P(nose.x + 4.5, nose.y + 6)
    val circleBand = find(TERM_CIRCLE).firstOrNull()
        ?: Band(textX, textX + 80f, page.lineBase.firstOrNull() ?: (textY + fontSize))
    return Art(
        circle = circleMark(circleBand, fontSize, 11.0),
        highlight = flatBands(find(TERM_HIGHLIGHT)),
        underline = underlineMark(find(TERM_UNDERLINE), 23.0),
        bracket = bracket,
        note = note,
        arrow = arrowMark(listOf(from, P(to.x + 6, mix(from.y, to.y, 0.5)), P(to.x + 4, mix(from.y, to.y, 0.82)), to), 53.0),
    )
}

/** One clock and the finger's highlights. Stepped once per frame; the canvas reads it while drawing. */
@Stable
private class ScribbleEngine(reduced: Boolean) {
    var clock by mutableFloatStateOf(if (reduced) HOLD else 0f)

    /** Freezes the drawing where a finger took over (LOOP = no cap). */
    var cap by mutableFloatStateOf(LOOP)
    var mode by mutableIntStateOf(MODE_AUTO)
    private var idle = 0f
    private var touches = 0
    private var seen = 0
    private var held = false

    val marks = emptyMarks()

    /** Bumped when [marks] changes, so the canvas redraws. */
    var version by mutableIntStateOf(0)
    private var stamp = 1
    var tintIndex = 0
    private var startWord = -1
    private var dragSlot = -1
    private var dragWord = -1

    fun reset(reduced: Boolean) {
        // Reduced motion: the finished page, loop stopped.
        clock = if (reduced) HOLD else 0f
        cap = LOOP
        mode = MODE_AUTO
    }

    fun touched() {
        touches += 1
    }

    fun frame(dt: Float, reduced: Boolean, playing: Boolean) {
        if (stepMarks(marks, dt, reduced)) version += 1
        if (reduced) return

        // A touch takes the page: the pen finishes the mark it is on and stops.
        if (touches != seen) {
            seen = touches
            idle = 0f
            if (mode == MODE_AUTO) {
                mode = MODE_MANUAL
                cap = min(cap, parkAt(clock))
            }
        }
        var t = clock
        if (mode == MODE_MANUAL) {
            val limit = cap
            if (t >= LIFT_START) {
                // Caught lifting off: let it finish, then wait on the clean page.
                t += dt
                if (t >= LOOP) {
                    t = 0f
                    cap = 0f
                }
                clock = t
            } else if (t < limit) {
                clock = min(limit, t + dt * CATCH_UP)
            }
            if (!playing) return
            if (!held) idle += dt
            if (idle >= RESUME_AFTER && t < LIFT_START) {
                liftMarks(marks, -1)
                version += 1
                if (limit > DRAW_FROM[0] && t > 0f) {
                    // Something is on the page: lift it off. The jump cannot be seen, since the drawing reads the cap.
                    clock = LIFT_START
                } else {
                    cap = LOOP
                }
                mode = MODE_AUTO
            }
            return
        }
        if (!playing) return
        t += dt
        if (t >= LOOP) {
            t -= LOOP
            cap = LOOP
        }
        clock = t
    }

    fun begin(x: Float, y: Float, page: ScribblePage) {
        touched()
        held = true
        dragSlot = -1
        val inside = y > page.y - 10f && y < page.y + page.height + 10f
        val word = if (inside) wordAt(x, y, page) else -1
        startWord = word
        dragWord = word
    }

    fun update(x: Float, y: Float, dx: Float, dy: Float, page: ScribblePage, onStart: () -> Unit, onWord: () -> Unit) {
        val from = startWord
        if (from < 0) return
        if (dragSlot < 0) {
            if (hypot(dx, dy) < TAP_SLOP) return
            // The touch has become a drag: a highlight starts under the first word and follows the finger.
            val slot = takeSlot(marks)
            stamp += 1
            putMark(marks, slot, from, from, 1f, tintIndex, stamp)
            dragSlot = slot
            version += 1
            onStart()
        }
        val word = wordAt(x, y, page)
        if (word < 0 || word == dragWord) return
        dragWord = word
        val o = dragSlot * SLOT
        marks[o + S_FIRST] = min(from, word).toFloat()
        marks[o + S_LAST] = max(from, word).toFloat()
        version += 1
        onWord()
    }

    fun finish(page: ScribblePage, onTap: () -> Unit) {
        held = false
        idle = 0f
        val word = startWord
        startWord = -1
        if (word < 0 || dragSlot >= 0) return
        // A tap: the sentence's highlight is swiped on, or the highlight under the finger lifts off.
        val over = markOver(marks, word)
        if (over >= 0) {
            liftMarks(marks, over)
        } else {
            val sentence = page.wordSentence[word]
            stamp += 1
            putMark(marks, takeSlot(marks), page.sentenceFirst[sentence], page.sentenceLast[sentence], 0f, tintIndex, stamp)
        }
        version += 1
        onTap()
    }
}

/**
 * An opaque colour that, multiplied over an opaque page, gives what the ink at [alpha] would: 1 - a + a * ink per
 * channel. PorterDuff's MULTIPLY has no alpha term of its own, and this needs neither API 29 nor a layer.
 */
private fun multiplyPaint(color: Color, alpha: Float, stroke: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = Color(
        red = 1f - alpha + alpha * color.red,
        green = 1f - alpha + alpha * color.green,
        blue = 1f - alpha + alpha * color.blue,
    ).toArgb()
    xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
    if (stroke) {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
}

private class ScribblePaints {
    private fun pen(color: Color) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    val red = pen(PEN_RED)
    val blue = pen(PEN_BLUE)
    val fills = TINTS.map { multiplyPaint(it.color, BandSpec.OPACITY, stroke = false) }
    val streaks = TINTS.map { multiplyPaint(it.color, BandSpec.STREAK, stroke = true) }
    val band = Path()
    val cardNear = SoftRoundRect(WARM_SHADOW.copy(alpha = 0.12f), sigma = 1.5f)
    val cardFar = SoftRoundRect(WARM_SHADOW.copy(alpha = 0.10f), sigma = 16f)
    val paper = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PAPER.toArgb() }
}

private fun Density.textStyle(size: Float, line: Float, weight: FontWeight, color: Color, tracking: Float = 0f, tabular: Boolean = false) = TextStyle(
    fontSize = size.dp.toSp(),
    lineHeight = line.dp.toSp(),
    fontWeight = weight,
    color = color,
    letterSpacing = tracking.dp.toSp(),
    fontFeatureSettings = if (tabular) "tnum" else null,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/**
 * A page of study notes that annotates itself: a circle, a highlighter swipe, a wavy underline, a bracket, a
 * handwritten note and an arrow, drawn on the real measured text. Tap a sentence or drag across words to highlight.
 */
@Composable
fun ScribbleEmphasisNotes(modifier: Modifier = Modifier, autoPlay: Boolean = true) {
    val reducedMotion = rememberLiveReducedMotion()
    val playing = autoPlay && !reducedMotion
    val engine = remember { ScribbleEngine(reducedMotion) }
    var tint by remember { mutableIntStateOf(0) }
    val view = LocalView.current
    val impact = remember(view) { { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK); Unit } }
    val tick = remember(view) { { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); Unit } }

    BlockSystemBars(darkIcons = true)

    // The frame loop outlives recompositions, so what it reads goes through state.
    val reduced by rememberUpdatedState(reducedMotion)
    val isPlaying by rememberUpdatedState(playing)
    LaunchedEffect(reducedMotion) { engine.reset(reducedMotion) }
    LaunchedEffect(engine) {
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last < 0L) 0.016f else min((now - last) / 1_000_000_000f, 0.048f)
                last = now
                if (dt > 0f) engine.frame(dt, reduced, isPlaying)
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(DESK).clipToBounds()) {
        val density = LocalDensity.current
        val bars = WindowInsets.systemBars
        val insetTop = bars.getTop(density) / density.density
        val insetBottom = bars.getBottom(density) / density.density
        val width = maxWidth.value
        val height = maxHeight.value

        // Where things sit. The paragraph is laid out and measured once per size.
        val column = min(width, MAX_WIDTH)
        val left = (width - column) / 2f + SIDE
        val cardWidth = column - SIDE * 2f
        val headTop = insetTop + 8f
        val cardTop = headTop + HEADER
        val large = column >= 390f
        val fontSize = if (large) 18f else 17f
        val lineHeight = if (large) 29f else 27f
        val textX = left + CARD_PAD
        val textY = cardTop + CARD_HEAD
        val textWidth = cardWidth - CARD_PAD - GUTTER - GUTTER_EDGE

        val page = remember(textX, textY, textWidth, fontSize, lineHeight, density.density) {
            ScribblePage(TEXT, textX, textY, textWidth, fontSize, lineHeight, INK.toArgb(), density.density)
        }
        val art = remember(page) { makeArt(page, textX, textY, textWidth, fontSize) }
        val paints = remember { ScribblePaints() }
        val cardHeight = CARD_HEAD + page.height + NOTE_AREA
        val trayTop = cardTop + cardHeight + TRAY_GAP
        val listTop = trayTop + TRAY + TRAY_GAP
        val fit = floor((height - max(insetBottom, 12f) - listTop - LIST_HEAD - 8f) / ROW).toInt()
        val rows = if (fit >= 2) min(fit, MARKED.size) else 0

        Spacer(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val t = engine.clock
                    val limit = engine.cap
                    engine.version
                    drawIntoCanvas {
                        drawPage(it.nativeCanvas, this.density, page, art, paints, engine, t, limit, left, cardTop, cardWidth, cardHeight)
                    }
                },
        )

        Header(left, headTop, cardWidth)
        CardHead(left, cardTop, cardWidth)

        // The card takes the touches: the paragraph's words, by where the finger is on the screen.
        Box(
            Modifier
                .offset(left.dp, cardTop.dp)
                .size(cardWidth.dp, cardHeight.dp)
                .semantics { contentDescription = "$TEXT ${Copy.HINT}" }
                .pointerInput(page, left, cardTop) {
                    val d = this.density
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        engine.begin(down.position.x / d + left, down.position.y / d + cardTop, page)
                        try {
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                change.consume()
                                engine.update(
                                    change.position.x / d + left,
                                    change.position.y / d + cardTop,
                                    (change.position.x - down.position.x) / d,
                                    (change.position.y - down.position.y) / d,
                                    page,
                                    onStart = impact,
                                    onWord = tick,
                                )
                            }
                        } finally {
                            engine.finish(page, onTap = impact)
                        }
                    }
                },
        )

        Tray(left, trayTop, cardWidth, tint) { index ->
            engine.touched()
            engine.tintIndex = index
            tint = index
            tick()
        }

        if (rows > 0) {
            Column(
                Modifier
                    .offset(left.dp, listTop.dp)
                    .width(cardWidth.dp)
                    .padding(horizontal = 4.dp),
            ) {
                val headStyle = remember(density) { density.textStyle(13f, 16f, FontWeight.Bold, INK_SOFT, -0.1f) }
                Text(Copy.MARKED, style = headStyle, modifier = Modifier.height(LIST_HEAD.dp))
                MARKED.take(rows).forEachIndexed { i, entry ->
                    MarkedRow(entry, first = i == 0, engine = engine)
                }
            }
        }

        val head = remember { FloatArray(2) }
        Box(
            Modifier
                .size(Ghost.SIZE.dp)
                .graphicsLayer {
                    val t = engine.clock
                    val limit = engine.cap
                    bandHead(art.highlight, markAt(t, limit, Mark.HIGHLIGHT), head)
                    val shown = if (engine.mode == MODE_AUTO && limit >= LOOP && isPlaying) ghostAt(t) else 0f
                    val pressed = seg(t, Ghost.SHOWN, DRAW_FROM[Mark.HIGHLIGHT]) * (1f - seg(t, Ghost.LIFT, Ghost.GONE))
                    alpha = shown
                    translationX = (head[0] - Ghost.SIZE / 2f) * this.density
                    translationY = (head[1] - fontSize * 0.3f - Ghost.SIZE / 2f) * this.density
                    val s = 1f - 0.14f * pressed
                    scaleX = s
                    scaleY = s
                }
                .background(Color(36 / 255f, 33 / 255f, 28 / 255f, 0.18f), CircleShape)
                .border(1.5.dp, Color.White.copy(alpha = 0.9f), CircleShape),
        )
    }
}

/** The card, its text, then the highlighter (multiplied over the printed text), then the pens on top. */
private fun drawPage(
    canvas: Canvas,
    density: Float,
    page: ScribblePage,
    art: Art,
    paints: ScribblePaints,
    engine: ScribbleEngine,
    t: Float,
    limit: Float,
    left: Float,
    cardTop: Float,
    cardWidth: Float,
    cardHeight: Float,
) {
    canvas.save()
    canvas.scale(density, density)
    paints.cardFar.draw(canvas, left, cardTop + 12f, cardWidth, cardHeight, CARD_RADIUS)
    paints.cardNear.draw(canvas, left, cardTop + 1f, cardWidth, cardHeight, CARD_RADIUS)
    canvas.drawRoundRect(left, cardTop, left + cardWidth, cardTop + cardHeight, CARD_RADIUS, CARD_RADIUS, paints.paper)
    canvas.restore()

    page.draw(canvas)

    canvas.save()
    canvas.scale(density, density)
    val list = engine.marks
    for (i in 0 until USER_MARKS) {
        val o = i * SLOT
        if (list[o + S_USED] == 0f) continue
        val bands = wordBands(list[o + S_FIRST].toInt(), list[o + S_LAST].toInt(), page)
        val ink = list[o + S_TINT].toInt()
        drawHighlight(
            canvas,
            paints.band,
            paints.fills[ink],
            paints.streaks[ink],
            bands,
            page.fontSize,
            100 + list[o + S_STAMP].toInt() * 17,
            easeInOutCubic(list[o + S_AMOUNT]),
        )
    }
    drawHighlight(canvas, paints.band, paints.fills[0], paints.streaks[0], art.highlight, page.fontSize, 7, markAt(t, limit, Mark.HIGHLIGHT))
    drawInk(canvas, paints.red, art.circle, markAt(t, limit, Mark.CIRCLE))
    drawInk(canvas, paints.blue, art.underline, markAt(t, limit, Mark.UNDERLINE))
    drawInk(canvas, paints.red, art.bracket, markAt(t, limit, Mark.BRACKET))
    drawInk(canvas, paints.red, art.note, markAt(t, limit, Mark.NOTE))
    drawInk(canvas, paints.red, art.arrow, markAt(t, limit, Mark.ARROW))
    canvas.restore()
}

@Composable
private fun Header(left: Float, top: Float, width: Float) {
    val density = LocalDensity.current
    val brand = remember(density) { density.textStyle(15f, 20f, FontWeight.Bold, PEN_RED, -0.2f) }
    val course = remember(density) { density.textStyle(13f, 18f, FontWeight.Medium, INK_SOFT) }
    val meta = remember(density) { density.textStyle(14f, 18f, FontWeight.Medium, INK_SOFT, tabular = true) }
    val title = remember(density) { density.textStyle(31f, 36f, FontWeight.ExtraBold, INK, -0.7f) }
    // One line, shrunk to fit on a narrow phone.
    val measurer = rememberTextMeasurer()
    val available = with(density) { (width - 8f).dp.roundToPx() }
    val fitted = remember(title, available) {
        val natural = measurer.measure(Copy.TITLE, title, maxLines = 1, softWrap = false).size.width
        if (natural <= available || natural == 0) {
            title
        } else {
            val k = available.toFloat() / natural
            title.copy(fontSize = title.fontSize * k, letterSpacing = title.letterSpacing * k)
        }
    }
    Column(Modifier.offset(left.dp, top.dp).width(width.dp).padding(horizontal = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(Copy.BRAND, style = brand, modifier = Modifier.alignByBaseline())
            Text(Copy.COURSE, style = course, modifier = Modifier.alignByBaseline())
        }
        Text(
            Copy.TITLE,
            style = fitted,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(top = 6.dp).semantics { heading() },
        )
        Text(Copy.META, style = meta, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun CardHead(left: Float, top: Float, width: Float) {
    val density = LocalDensity.current
    val label = remember(density) { density.textStyle(13f, 18f, FontWeight.Bold, INK, -0.1f) }
    val pageNumber = remember(density) { density.textStyle(13f, 18f, FontWeight.Medium, INK_SOFT, tabular = true) }
    Row(
        Modifier
            .offset(left.dp, top.dp)
            .size(width.dp, CARD_HEAD.dp)
            .padding(start = CARD_PAD.dp, end = CARD_PAD.dp, top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(Copy.LABEL, style = label)
        Text(Copy.PAGE, style = pageNumber)
    }
}

@Composable
private fun Tray(left: Float, top: Float, width: Float, chosen: Int, onPick: (Int) -> Unit) {
    val density = LocalDensity.current
    val title = remember(density) { density.textStyle(15f, 19f, FontWeight.Bold, INK, -0.2f) }
    val hint = remember(density) { density.textStyle(12.5f, 16f, FontWeight.Medium, INK_SOFT) }
    Row(
        Modifier
            .offset(left.dp, top.dp)
            .size(width.dp, TRAY.dp)
            .cssBoxShadow(WARM_SHADOW.copy(alpha = 0.07f), 8.dp, 18.dp, 20.dp)
            .cssBoxShadow(WARM_SHADOW.copy(alpha = 0.10f), 1.dp, 2.dp, 20.dp)
            .background(PAPER, RoundedCornerShape(20.dp))
            .padding(start = CARD_PAD.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(Copy.TRAY, style = title, maxLines = 1)
            Text(
                Copy.HINT,
                style = hint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TINTS.forEachIndexed { i, entry ->
                val on = i == chosen
                Box(
                    Modifier
                        .size(38.dp)
                        .border(2.dp, if (on) INK else Color.Transparent, CircleShape)
                        .semantics {
                            selected = on
                            contentDescription = "${entry.name} highlighter"
                        }
                        .clickable(interactionSource = null, indication = null, role = Role.Button) { onPick(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(26.dp)
                            .background(entry.color, CircleShape)
                            .border(with(density) { 1f.toDp() }, Color(36 / 255f, 33 / 255f, 28 / 255f, 0.18f), CircleShape),
                    )
                }
            }
        }
    }
}

/** One line of the summary: it is faint until the loop has drawn its mark. */
@Composable
private fun MarkedRow(entry: MarkedEntry, first: Boolean, engine: ScribbleEngine) {
    val density = LocalDensity.current
    val term = remember(density) { density.textStyle(16f, 20f, FontWeight.SemiBold, INK, -0.2f) }
    val how = remember(density) { density.textStyle(14f, 18f, FontWeight.Medium, INK_SOFT) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(ROW.dp)
            .graphicsLayer { alpha = mix(0.42f, 1f, markAt(engine.clock, engine.cap, entry.mark)) },
    ) {
        if (!first) {
            Box(Modifier.fillMaxWidth().height(with(density) { 1f.toDp() }).background(HAIRLINE))
        }
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .padding(end = 12.dp)
                    .size(18.dp)
                    .border(1.5.dp, Color(36 / 255f, 33 / 255f, 28 / 255f, 0.28f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .graphicsLayer {
                            val on = markAt(engine.clock, engine.cap, entry.mark)
                            alpha = on
                            val s = mix(0.5f, 1f, on)
                            scaleX = s
                            scaleY = s
                        }
                        .background(PEN_RED, CircleShape),
                )
            }
            Text(entry.term, style = term, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(entry.how, style = how, maxLines = 1)
        }
    }
}
