package com.ngocdevv.nativecook.blocks.lists.springdisclosurefaq

import android.graphics.Bitmap
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

// ---------------------------------------------------------------- content

private class Faq(val q: String, val a: String)

private val GROUP_TITLES = listOf("Cards", "Transfers and account")
private val ITEMS = listOf(
    Faq(
        "How do I freeze my card?",
        "Open Cards, pick the card and switch on Freeze. Payments stop straight away, and you can unfreeze it the same way when it turns up.",
    ),
    Faq(
        "What are my daily limits?",
        "You can spend up to £5,000 and take out £400 in cash each day. Change either one under Cards, then Limits.",
    ),
    Faq(
        "Why was my payment declined?",
        "Usually the card is frozen, a limit was reached, or the shop asked for a check we could not confirm. The reason sits next to the payment in Activity.",
    ),
    Faq(
        "When will my transfer arrive?",
        "Transfers to UK accounts arrive within two hours, most of them in seconds. Payments abroad take one to three working days.",
    ),
    Faq(
        "Are there fees abroad?",
        "Paying by card abroad is free, at the exchange rate of the day. Cash is free up to £200 a month, then 2% of what you take out.",
    ),
    Faq(
        "How do I close my account?",
        "Move your money out, then go to Profile, Account, Close account. We keep your statements available for six years.",
    ),
)

// ---------------------------------------------------------------- look and layout (dp)

private val PAGE = Color(0xFFF1EFE9)
private val CARD = Color(0xFFFCFBF8)
private val INK = Color(0xFF1B211E)
private val MUTE = Color(0xFF69706B)
private val BODY = Color(0xFF414A45) // answers: darker than the labels, they are what gets read
private val ACCENT = Color(0xFF2B6A4D)
private val OPEN_TINT = ACCENT.copy(alpha = 0.07f) // behind the open question and its answer
private val HAIRLINE = INK.copy(alpha = 0.10f)
private val FIELD = INK.copy(alpha = 0.06f)

private const val SIDE = 16f // screen margin
private const val ROW_H = 56f // a closed row
private const val LABEL_H = 30f // a group's title above its card
private const val GROUP_GAP = 22f // between the first card and the second title
private const val FOOT_GAP = 20f // between the second card and the contact row
private const val FOOT_H = 64f
private const val CARD_BASE = GROUP_SIZE * ROW_H
private const val CARD2_TOP = LABEL_H + CARD_BASE + GROUP_GAP + LABEL_H
private const val FOOT_TOP = CARD2_TOP + CARD_BASE + FOOT_GAP
private const val ANSWER_X = 16f // answer text inset, left
private const val ANSWER_RIGHT = 20f
private const val ANSWER_BOTTOM = 18f // space under the last line
private const val ANSWER_LINE = 22f // line height
private const val CARD_RADIUS = 20f
private const val DOT = 44f // the ghost finger
private const val DOT_X = 0.62f // its place across the card
private const val LINE_BLEED = 18f // room around an answer line for its blur (3 sigma)

private const val IDLE_S = 2.0 // after the last touch, before the rows ease back to rest
private const val RETURN_S = 1.1 // the ease back, then the film restarts at t = 0

private val CARD_SHAPE = RoundedCornerShape(CARD_RADIUS.dp)

/** Reanimated's default `withTiming` easing. */
private val InOutQuad = Easing { x -> if (x < 0.5f) 2 * x * x else 1 - (-2 * x + 2).pow(2) / 2 }

/** Rows before measuring: about 41 characters to a line. */
private val HEIGHTS_ESTIMATE = FloatArray(ROW_COUNT) { ceil(ITEMS[it].a.length / 41f) * ANSWER_LINE + ANSWER_BOTTOM }

/** An answer laid out once: its lines are drawn one by one. */
private class FaqMeasured(val lines: List<String>, val lineH: Float) {
    val height get() = lines.size * lineH + ANSWER_BOTTOM
}

/** What every moving part reads. The state fields are read in layout, layer and draw lambdas only. */
@Stable
private class FaqState(film: Boolean, reduced: Boolean) {
    var mode by mutableIntStateOf(if (film) MODE_AUTO else MODE_MANUAL)
    var tr by mutableStateOf(restTransitions())
    var tau by mutableDoubleStateOf(0.0) // loop time in the film, the frame clock under a finger
    var heights by mutableStateOf(HEIGHTS_ESTIMATE)
    var film by mutableStateOf(film)
    var reduced by mutableStateOf(reduced)
    var expanded by mutableIntStateOf(REST_ROW) // for screen readers; -1 = none

    private var clock = 0.0 // the frame clock, seconds
    private var loopStart = -1.0
    private var lastTouch = 0.0
    private var returnAt = 0.0

    fun open(j: Int, lag: Double = 0.0) = openAt(mode, tr, reduced, j, tau - lag)

    fun push(first: Int, last: Int, target: Int) = pushAt(mode, tr, reduced, heights, first, last, target, tau)

    /** How far (dp) the second card and its title are pushed down by the first card's answers. */
    fun secondShift() = push(0, GROUP_SIZE - 1, GROUP_SIZE)

    fun frame(now: Double) {
        clock = now
        val m = mode
        if (m == MODE_AUTO) {
            if (!film) {
                tr = restTransitions()
                mode = MODE_MANUAL
                tau = now
                return
            }
            if (loopStart < 0) loopStart = now
            tau = (now - loopStart) % LOOP
            return
        }
        tau = now
        if (!film) return
        if (m == MODE_MANUAL && now - lastTouch > IDLE_S) {
            tr = retarget(MODE_MANUAL, tr, reduced, now, now, -1)
            returnAt = now
            mode = MODE_RETURN
            expanded = REST_ROW
            return
        }
        if (m == MODE_RETURN && now - returnAt > RETURN_S) {
            loopStart = now
            tau = 0.0
            mode = MODE_AUTO
        }
    }

    fun touch() {
        lastTouch = clock
    }

    /** A finger on a row: the film stops and the row toggles from wherever everything is. */
    fun toggle(index: Int) {
        val now = clock
        val next = retarget(mode, tr, reduced, if (mode == MODE_AUTO) tau else now, now, index)
        tr = next
        mode = MODE_MANUAL
        tau = now
        lastTouch = now
        expanded = if (next.to[index] > 0.5) index else -1
    }

    /** Top of row `index` inside the stage at time `at` (dp). */
    fun rowTopAt(index: Int, at: Double): Double {
        val first = if (index < GROUP_SIZE) 0 else GROUP_SIZE
        val cardTop = if (index < GROUP_SIZE) LABEL_H.toDouble()
        else CARD2_TOP + pushAt(mode, tr, reduced, heights, 0, GROUP_SIZE - 1, GROUP_SIZE, at)
        return cardTop + (index - first) * ROW_H + pushAt(mode, tr, reduced, heights, first, index - 1, index, at)
    }
}

/**
 * A help centre whose questions open on a spring. One row is open at a time: its answer's lines rise
 * out of a blur one after another, the "+" turns into a "−", the rows below are pushed down each a
 * beat after the one above, and the row that was open closes in the same move.
 */
@Composable
fun SpringDisclosureFaq(modifier: Modifier = Modifier, autoPlay: Boolean = true) {
    val reduced = rememberLiveReducedMotion()
    val film = autoPlay && !reduced
    val state = remember { FaqState(film, reduced) }
    SideEffect {
        state.film = film
        state.reduced = reduced
    }
    LaunchedEffect(state) {
        var base = -1L
        while (true) {
            withFrameNanos { nanos ->
                if (base < 0) base = nanos
                state.frame((nanos - base) / 1e9)
            }
        }
    }
    BlockSystemBars(darkIcons = true)

    BoxWithConstraints(modifier.fillMaxSize().background(PAGE).faqGrain()) {
        val compact = maxHeight < 740.dp
        val contentW = min(maxWidth.value, 480f)
        val cardW = contentW - SIDE * 2
        val answerW = cardW - ANSWER_X - ANSWER_RIGHT
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val density = LocalDensity.current

        // Each answer is laid out once; its lines are then drawn one by one.
        // no trim: by default a paragraph's first and last lines are shorter than the line height
        val answerStyle = remember {
            TextStyle(
                color = BODY,
                fontSize = 16.sp,
                lineHeight = ANSWER_LINE.sp,
                letterSpacing = (-0.1).sp,
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
            )
        }
        val measurer = rememberTextMeasurer()
        val measured = remember(answerW, density) {
            val widthPx = with(density) { answerW.dp.roundToPx() }
            ITEMS.map { item ->
                val result = measurer.measure(item.a, answerStyle, constraints = Constraints(maxWidth = widthPx))
                val lines = List(result.lineCount) { i ->
                    item.a.substring(result.getLineStart(i), result.getLineEnd(i, visibleEnd = true)).trimEnd()
                }
                val linePx = result.getLineBottom(0) - result.getLineTop(0)
                FaqMeasured(lines, if (linePx > 0f) linePx / density.density else ANSWER_LINE * density.fontScale)
            }
        }
        val heights = remember(measured) { FloatArray(ROW_COUNT) { measured[it].height } }
        SideEffect { if (!state.heights.contentEquals(heights)) state.heights = heights }
        val maxH = max(HEIGHTS_ESTIMATE.max(), heights.max())
        val stageH = FOOT_TOP + FOOT_H + maxH + 8f

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .width(contentW.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(
                    top = insets.calculateTopPadding() + if (compact) 2.dp else 6.dp,
                    bottom = insets.calculateBottomPadding() + 16.dp,
                    start = SIDE.dp,
                    end = SIDE.dp,
                ),
        ) {
            Box(Modifier.fillMaxWidth().height(44.dp)) {
                FaqNavButton(Modifier.align(Alignment.CenterStart)) { chevron(center, 4.dp.toPx(), 6.5.dp.toPx(), 2.4.dp.toPx(), INK, left = true) }
                if (compact) {
                    BasicText("Help", Modifier.align(Alignment.Center), TextStyle(color = INK, fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
                }
                FaqNavButton(Modifier.align(Alignment.CenterEnd)) {
                    for (i in -1..1) drawCircle(INK, 1.9.dp.toPx(), Offset(center.x + i * 6.2.dp.toPx(), center.y))
                }
            }
            if (!compact) {
                BasicText(
                    "Help",
                    Modifier.padding(top = 6.dp).semantics { heading() },
                    TextStyle(color = INK, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp),
                )
            }

            Row(
                Modifier
                    .padding(top = if (compact) 8.dp else 14.dp)
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(FIELD, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier.size(18.dp).drawBehind {
                        val c = Offset(center.x - 1.4.dp.toPx(), center.y - 1.4.dp.toPx())
                        val w = 1.9.dp.toPx()
                        drawCircle(MUTE, 5.3.dp.toPx(), c, style = Stroke(w))
                        drawLine(MUTE, c + Offset(3.9.dp.toPx(), 3.9.dp.toPx()), c + Offset(7.6.dp.toPx(), 7.6.dp.toPx()), w, StrokeCap.Round)
                    },
                )
                BasicText("Search Alder help", style = TextStyle(color = MUTE, fontSize = 17.sp))
            }

            Box(Modifier.padding(top = if (compact) 14.dp else 20.dp).fillMaxWidth().height(stageH.dp)) {
                val labelStyle = TextStyle(color = MUTE, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                BasicText(GROUP_TITLES[0], Modifier.padding(start = 4.dp), labelStyle)
                FaqCard(group = 0, width = cardW.dp, maxH = maxH, state = state) {
                    for (i in 0 until GROUP_SIZE) FaqRow(i, ITEMS[i], measured[i], answerStyle, state)
                }

                BasicText(
                    GROUP_TITLES[1],
                    Modifier
                        .offset(y = (LABEL_H + CARD_BASE + GROUP_GAP).dp)
                        .graphicsLayer { translationY = state.secondShift().toFloat().dp.toPx() }
                        .padding(start = 4.dp),
                    labelStyle,
                )
                FaqCard(group = 1, width = cardW.dp, maxH = maxH, state = state) {
                    for (i in GROUP_SIZE until ROW_COUNT) FaqRow(i, ITEMS[i], measured[i], answerStyle, state)
                }

                FaqFoot(
                    Modifier
                        .offset(y = FOOT_TOP.dp)
                        .graphicsLayer {
                            val shift = state.secondShift() + state.push(GROUP_SIZE, ROW_COUNT - 1, ROW_COUNT)
                            translationY = shift.toFloat().dp.toPx()
                        }
                        .width(cardW.dp),
                )

                FaqGhostTouch(x = cardW * DOT_X, state = state)
            }
        }
    }
}

// ---------------------------------------------------------------- the grouped card

/**
 * One grouped card. Its plate (background, shadows) and its clip grow with the answers inside it; the
 * second card is also pushed down by the first one's. Only the plate's height is a layout animation:
 * the card holds three rows moved in their layers, so nothing else is laid out again.
 */
@Composable
private fun FaqCard(group: Int, width: Dp, maxH: Float, state: FaqState, content: @Composable () -> Unit) {
    val first = group * GROUP_SIZE
    val last = first + GROUP_SIZE - 1
    val top = if (group == 0) LABEL_H else CARD2_TOP
    val shift = { if (group == 0) 0f else state.secondShift().toFloat() }
    // the bottom edge travels with the last row: its own answer plus what pushes it
    val height = {
        val grown = state.push(first, last - 1, last) + state.heights[last] * state.open(last)
        max(CARD_BASE, CARD_BASE + grown.toFloat())
    }
    // read in layout: a quarter dp is under a pixel, and a card at rest is not measured again
    val plateH by remember(group, state) { derivedStateOf { (height() * 4f).roundToInt() / 4f } }
    Box(
        Modifier
            .offset(y = top.dp)
            .width(width)
            .height(CARD_BASE.dp)
            .graphicsLayer { translationY = shift().dp.toPx() }
            .layout { measurable, constraints ->
                val plate = measurable.measure(Constraints.fixed(constraints.maxWidth, plateH.dp.roundToPx()))
                layout(constraints.maxWidth, constraints.maxHeight) { plate.place(0, 0) }
            }
            .cssBoxShadow(INK.copy(alpha = 0.09f), offsetY = 1.dp, blur = 2.dp, radius = CARD_RADIUS.dp)
            .cssBoxShadow(INK.copy(alpha = 0.07f), offsetY = 12.dp, blur = 28.dp, radius = CARD_RADIUS.dp)
            .background(CARD, CARD_SHAPE)
            .border(Dp.Hairline, Color.White.copy(alpha = 0.9f), CARD_SHAPE),
    )
    val clip = remember { Path() }
    Box(
        Modifier
            .offset(y = top.dp)
            .width(width)
            .height((CARD_BASE + maxH).dp)
            .graphicsLayer { translationY = shift().dp.toPx() }
            .drawWithContent {
                val r = CornerRadius(CARD_RADIUS.dp.toPx())
                clip.rewind()
                clip.addRoundRect(RoundRect(Rect(0f, 0f, size.width, height().dp.toPx()), r))
                clipPath(clip) { this@drawWithContent.drawContent() }
            },
    ) { content() }
}

// ---------------------------------------------------------------- a question

@Composable
private fun FaqRow(index: Int, item: Faq, measured: FaqMeasured, answerStyle: TextStyle, state: FaqState) {
    val first = if (index < GROUP_SIZE) 0 else GROUP_SIZE
    val local = index - first
    val pressed = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val openH = ROW_H + measured.height
    val expanded = state.expanded == index
    val bar = remember { Path() }

    Box(
        Modifier
            .fillMaxWidth()
            .height(ROW_H.dp)
            .graphicsLayer { translationY = (local * ROW_H + state.push(first, index - 1, index).toFloat()).dp.toPx() }
            .pointerInput(index) {
                detectTapGestures(
                    onPress = {
                        state.touch()
                        scope.launch { pressed.animateTo(1f, tween(90, easing = InOutQuad)) }
                        tryAwaitRelease()
                        state.touch()
                        scope.launch { pressed.animateTo(0f, tween(240, easing = InOutQuad)) }
                    },
                    onTap = {
                        state.toggle(index)
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    },
                )
            }
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = if (expanded) "${item.q} ${item.a}" else item.q
                stateDescription = if (expanded) "Expanded" else "Collapsed"
            }
            .drawBehind {
                val o = state.open(index)
                val shown = clamp01(o).toFloat()
                val heightPx = openH.dp.toPx()
                drawRect(CARD)
                if (local > 0) drawRect(HAIRLINE, Offset(ANSWER_X.dp.toPx(), 0f), Size(size.width - ANSWER_X.dp.toPx(), 1f))
                // the open row's plate and accent bar reach down behind its answer; the row below covers what is not open
                if (shown > 0f) drawRect(OPEN_TINT, size = Size(size.width, heightPx), alpha = shown)
                // the bar grows down from the question's top edge with the same spring (overshoot included)
                val grow = max(0.0, o).toFloat()
                val barAlpha = clamp01(o * 2).toFloat()
                if (grow > 0f && barAlpha > 0f) {
                    val r = CornerRadius(2.dp.toPx(), 2.dp.toPx() * grow)
                    bar.rewind()
                    bar.addRoundRect(RoundRect(Rect(0f, 0f, 3.dp.toPx(), heightPx * grow), topRight = r, bottomRight = r))
                    drawPath(bar, ACCENT, alpha = barAlpha)
                }
                var p = pressed.value
                if (state.mode == MODE_AUTO) {
                    val t = state.tau
                    for (k in EVENT_T.indices) if (EVENT_ROW[k] == index) p = max(p, touchPress(k, t).toFloat())
                }
                if (p > 0f) drawRect(INK, alpha = 0.05f * p)
            },
    ) {
        BasicText(
            item.q,
            Modifier.align(Alignment.CenterStart).padding(start = ANSWER_X.dp, end = 56.dp),
            TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = ColorProducer { mixGamma(INK, ACCENT, clamp01(state.open(index)).toFloat()) },
        )
        // the upright stroke of the "+" lies down onto the other one and fades: "−"
        Box(
            Modifier.align(Alignment.CenterEnd).padding(end = 14.dp).size(28.dp).drawBehind {
                val o = state.open(index)
                drawCircle(FIELD)
                val long = 12.dp.toPx()
                val thin = 2.dp.toPx()
                val r = CornerRadius(thin / 2)
                drawRoundRect(INK, Offset(center.x - long / 2, center.y - thin / 2), Size(long, thin), r)
                val up = 1f - clamp01(o).toFloat()
                if (up > 0f) {
                    rotate((90 * o).toFloat()) {
                        drawRoundRect(INK, Offset(center.x - thin / 2, center.y - long / 2), Size(thin, long), r, alpha = up)
                    }
                }
            },
        )
        // under the question row; the row below (or the card's edge) covers what is not open yet
        Column(
            Modifier
                .layout { measurable, constraints ->
                    val box = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                    layout(box.width, 0) { box.place(0, ROW_H.dp.roundToPx()) }
                }
                .fillMaxWidth()
                .padding(start = ANSWER_X.dp, end = ANSWER_RIGHT.dp),
        ) {
            measured.lines.forEachIndexed { k, text -> FaqAnswerLine(index, k, text, measured.lineH, answerStyle, state) }
        }
    }
}

/** One line of an answer: it samples its row's `open` a little later than the line above. */
@Composable
private fun FaqAnswerLine(row: Int, k: Int, text: String, lineH: Float, style: TextStyle, state: FaqState) {
    Box(Modifier.fillMaxWidth().height(lineH.dp)) {
        // the layer is larger than the line, so the blur is not cut at the line's box
        Box(
            Modifier
                .layout { measurable, constraints ->
                    val bleed = LINE_BLEED.dp.roundToPx()
                    val layer = measurable.measure(Constraints.fixed(constraints.maxWidth + bleed * 2, constraints.maxHeight + bleed * 2))
                    layout(constraints.maxWidth, constraints.maxHeight) { layer.place(-bleed, -bleed) }
                }
                .graphicsLayer {
                    val reduced = state.reduced
                    val c = lineReveal(state.open(row, if (reduced) 0.0 else LINE_STAGGER * k)).toFloat()
                    alpha = c
                    if (reduced) {
                        translationY = 0f
                        renderEffect = null
                    } else {
                        translationY = (1f - c) * LINE_RISE.dp.toPx()
                        // Android's blur radius is not the sigma: sigma = 0.57735 * radius + 0.5
                        val radius = ((1f - c) * LINE_BLUR.dp.toPx() - 0.5f) / 0.57735f
                        renderEffect = if (c > 0f && c < 1f && radius > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            BlurEffect(radius, radius, TileMode.Decal)
                        } else {
                            null
                        }
                    }
                }
                .padding(LINE_BLEED.dp),
        ) {
            BasicText(text, style = style, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
        }
    }
}

// ---------------------------------------------------------------- the small parts

@Composable
private fun FaqNavButton(modifier: Modifier, icon: DrawScope.() -> Unit) {
    Box(
        modifier
            .size(36.dp)
            .cssBoxShadow(INK.copy(alpha = 0.10f), offsetY = 1.dp, blur = 2.dp, radius = 18.dp)
            .cssBoxShadow(INK.copy(alpha = 0.06f), offsetY = 6.dp, blur = 16.dp, radius = 18.dp)
            .background(CARD, CircleShape)
            .drawBehind(icon),
    )
}

@Composable
private fun FaqFoot(modifier: Modifier) {
    Row(
        modifier
            .height(FOOT_H.dp)
            .cssBoxShadow(INK.copy(alpha = 0.09f), offsetY = 1.dp, blur = 2.dp, radius = CARD_RADIUS.dp)
            .cssBoxShadow(INK.copy(alpha = 0.07f), offsetY = 12.dp, blur = 28.dp, radius = CARD_RADIUS.dp)
            .background(CARD, CARD_SHAPE)
            .border(Dp.Hairline, Color.White.copy(alpha = 0.9f), CARD_SHAPE)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(36.dp).background(ACCENT, CircleShape).drawBehind {
                // a speech bubble with its tail at the bottom left
                val w = 15.dp.toPx()
                val h = 11.5.dp.toPx()
                val left = center.x - w / 2
                val top = center.y - h / 2 - 1.dp.toPx()
                drawRoundRect(CARD, Offset(left, top), Size(w, h), CornerRadius(3.4.dp.toPx()))
                val tail = Path().apply {
                    moveTo(left + 2.4.dp.toPx(), top + h - 1.dp.toPx())
                    lineTo(left + 2.4.dp.toPx(), top + h + 3.2.dp.toPx())
                    lineTo(left + 7.dp.toPx(), top + h - 1.dp.toPx())
                    close()
                }
                drawPath(tail, CARD)
            },
        )
        Column(Modifier.weight(1f)) {
            BasicText("Chat with us", style = TextStyle(color = INK, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp))
            Spacer(Modifier.height(1.dp))
            BasicText("Replies in about 4 minutes", style = TextStyle(color = MUTE, fontSize = 13.sp))
        }
        Box(Modifier.size(13.dp).drawBehind { chevron(center, 2.8.dp.toPx(), 5.dp.toPx(), 2.dp.toPx(), MUTE, left = false) })
    }
}

/** In the film a soft dot shows where the finger taps; it stays where it landed while the rows move. */
@Composable
private fun FaqGhostTouch(x: Float, state: FaqState) {
    Box(
        Modifier
            .size(DOT.dp)
            .graphicsLayer {
                translationX = (x - DOT / 2).dp.toPx()
                if (state.mode != MODE_AUTO) {
                    alpha = 0f
                    return@graphicsLayer
                }
                val t = state.tau
                var best = 0
                var a = 0.0
                for (k in EVENT_T.indices) {
                    val ak = touchAlpha(k, t)
                    if (ak > a) {
                        a = ak
                        best = k
                    }
                }
                val y = state.rowTopAt(EVENT_ROW[best], EVENT_T[best]) + ROW_H / 2
                alpha = a.toFloat()
                translationY = (y.toFloat() - DOT / 2).dp.toPx()
                val scale = 1f - 0.16f * touchPress(best, t).toFloat()
                scaleX = scale
                scaleY = scale
            }
            .background(INK.copy(alpha = 0.14f), CircleShape)
            .border(1.5.dp, Color.White.copy(alpha = 0.75f), CircleShape),
    )
}

private fun DrawScope.chevron(c: Offset, halfW: Float, halfH: Float, stroke: Float, color: Color, left: Boolean) {
    val dir = if (left) 1f else -1f
    val path = Path().apply {
        moveTo(c.x + dir * halfW, c.y - halfH)
        lineTo(c.x - dir * halfW, c.y)
        lineTo(c.x + dir * halfW, c.y + halfH)
    }
    drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Reanimated's `interpolateColor` in RGB: channels are mixed with a gamma of 2.2. */
private fun mixGamma(a: Color, b: Color, t: Float): Color {
    fun ch(x: Float, y: Float): Float {
        val lx = x.pow(2.2f)
        return (lx + (y.pow(2.2f) - lx) * t).pow(1 / 2.2f)
    }
    return Color(ch(a.red, b.red), ch(a.green, b.green), ch(a.blue, b.blue), 1f)
}

// ---------------------------------------------------------------- the paper grain

// A static paper grain over the page so the flat colour does not look vector-made: per device pixel,
// white or black at up to 3.5 %.
private const val GRAIN_AGSL = """
float h21(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
half4 main(float2 p) {
  float n = h21(floor(p)) - 0.5;
  float a = abs(n) * 0.07;
  return n > 0.0 ? half4(a) : half4(0.0, 0.0, 0.0, a);
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object FaqGrainShader {
    fun brush() = ShaderBrush(RuntimeShader(GRAIN_AGSL))
}

/** Below API 33: the same noise from a 128 px tile. */
private fun grainTile(): ShaderBrush {
    val side = 128
    val pixels = IntArray(side * side) { i ->
        val v = sin((i % side) * 127.1 + (i / side) * 311.7) * 43758.5453
        val n = v - kotlin.math.floor(v) - 0.5
        val a = (abs(n) * 0.07 * 255).toInt()
        if (n > 0) (a shl 24) or 0xFFFFFF else a shl 24
    }
    val bitmap = Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888)
    return ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

private fun Modifier.faqGrain(): Modifier = drawWithCache {
    val brush = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) FaqGrainShader.brush() else grainTile()
    onDrawBehind { drawRect(brush) }
}
