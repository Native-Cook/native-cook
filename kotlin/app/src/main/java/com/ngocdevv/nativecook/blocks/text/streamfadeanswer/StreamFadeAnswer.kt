package com.ngocdevv.nativecook.blocks.text.streamfadeanswer

import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 Stream Fade Answer — one turn of a chat with a travel assistant. The question sits in its
 bubble; the answer streams in word by word (StreamingText: every word fades up from a blur,
 6 dp below, 28 ms after the one before, in a layout that is final before the first word
 shows): a heading, two paragraphs, three itinerary rows that slide in behind a hairline, a
 closing tip and a cost chip that counts up. A grey line with a moving light band stands in
 while the assistant thinks, and the thread scrolls to keep the newest line in view.

 Everything is a function of one stream clock S (StreamFadeMotion.kt). The loop moves it; a
 tap jumps it to the end; a horizontal drag scrubs it. The clock is read only in draw and
 layer blocks (and by the chip's number), so a frame recomposes nothing else.
 A port of the React Native block, number for number.
*/

private val PAGE = Color(0xFFF6F4EF)
private val INK = Color(0xFF1D1C1A)
private val SOFT = Color(0xFF55524C)
private val MUTE = Color(0xFF8A867E)
private val LINE = rgba(29, 28, 26, 0.12f)
private val BUBBLE = Color(0xFF2B2A27)
private val BUBBLE_INK = Color(0xFFF6F4EF)
private val ACCENT = Color(0xFF2F6B4F)
private val ACCENT_SOFT = rgba(47, 107, 79, 0.11f)
private val FIELD = Color(0xFFFDFCFA)

private const val QUESTION = "Plan a 3-day trip to Lisbon under €400"
private const val THINKING = "Checking fares and places to stay"
private const val HEADING = "Three days in Lisbon"
private val PARAGRAPHS = listOf(
    "Here is a plan that comes to €372, flights from Berlin included. I picked the second week of May: fares drop once the holidays are over, and the hills are still cool enough to walk all day.",
    "You would stay three nights in a guesthouse in Alfama at €52 a night, a short climb from the river and two minutes from the tram.",
)

private class Day(val day: String, val title: String, val detail: String, val cost: String)

private val ROWS = listOf(
    Day("1", "Alfama and the river", "Tram 28, Santa Luzia lookout, sardines", "€118"),
    Day("2", "Belém by bike", "Riverside path, custard tarts, the tower", "€131"),
    Day("3", "Sintra day trip", "Train from Rossio, Pena Palace, sunset", "€123"),
)
private const val TIP = "Buy the 24-hour transit pass each morning. It covers the trams, the metro and the lift up to Carmo."
private const val TOTAL = 372

private const val HEADER_H = 44f
private const val COMPOSER_H = 64f
private const val SIDE = 20f
private const val MAX_CONTENT = 560f
private const val ROW_H = 56f
private const val CHIP_H = 38f
/** Room kept under the newest line while the thread follows the stream. */
private const val FOLLOW_PAD = 16f
/** The scroll follows the stream through a short running average, so a new line never jerks it. */
private const val FOLLOW_LAG = 0.12f

private const val IDLE = 2f
/** A full-width drag covers this share of the stream. */
private const val SCRUB_SPAN = 0.8f
/** Past either end the answer leans this many dp per (rubber-banded) second. */
private const val LEAN = 36f

private const val BODY_SIZE = 16f
private const val BODY_LINE = 23f

private class Placed(val y: Float, val at: Float)

/** The whole thread, measured before anything is shown: positions (dp) and start times (s). */
private class ThreadPlan(w: Float) {
    val width = min(w - SIDE * 2, MAX_CONTENT)
    val left = (w - width) / 2
    val bubbleX: Float
    val bubbleY: Float
    val bubbleW: Float
    val bubbleH: Float
    val labelY: Float
    val answerY: Float
    val heading: Placed
    val paragraphs: List<Placed>
    val rows: List<Placed>
    val tip: Placed
    val chip: Placed
    val height: Float
    /** Stream time of the finished answer. */
    val end: Float
    /** Where each block starts on the stream clock: scrubbing ticks at these. */
    val marks: FloatArray
    /** Bottom of what has been revealed, as (stream time, y) key frames. */
    private val keyS: FloatArray
    private val keyY: FloatArray

    init {
        val ks = ArrayList<Float>()
        val ky = ArrayList<Float>()
        val mk = ArrayList<Float>()
        var y = 14f

        val textMax = min(width * 0.86f, 340f) - 28
        val q = measureStreamingText(QUESTION, textMax, fontSize = 16f, lineHeight = 22f)
        bubbleW = ceil(q.widest) + 28
        bubbleX = left + width - bubbleW
        bubbleY = y
        bubbleH = q.height + 20
        y += bubbleH + 22
        labelY = y
        y += 22 + 10
        answerY = y

        var at = 0f
        ks += -1f
        ky += answerY + 28

        val h = measureStreamingText(HEADING, width, fontSize = 22f, lineHeight = 28f, fontWeight = 700, seed = 1f)
        heading = Placed(y, at)
        ks += at; ks += at + h.lastStart
        ky += y + 28; ky += y + h.height
        y += h.height + 6
        at += h.lastStart + BLOCK_PAUSE

        val ps = ArrayList<Placed>()
        for (i in PARAGRAPHS.indices) {
            val m = measureStreamingText(PARAGRAPHS[i], width, fontSize = BODY_SIZE, lineHeight = BODY_LINE, seed = 2f + i)
            ps += Placed(y, at)
            mk += at
            ks += at; ks += at + m.lastStart
            ky += y + BODY_LINE; ky += y + m.height
            y += m.height + (if (i == PARAGRAPHS.size - 1) 12 else 10)
            at += m.lastStart + BLOCK_PAUSE
        }
        paragraphs = ps

        val rs = ArrayList<Placed>()
        for (i in ROWS.indices) {
            rs += Placed(y, at)
            mk += at
            ks += at + ROW_DUR * 0.5f
            ky += y + ROW_H
            y += ROW_H
            at += ROW_GAP
        }
        rows = rs
        y += 12
        at += ROW_DUR * 0.5f

        val t = measureStreamingText(TIP, width, fontSize = BODY_SIZE, lineHeight = BODY_LINE, seed = 7f)
        tip = Placed(y, at)
        mk += at
        ks += at; ks += at + t.lastStart
        ky += y + BODY_LINE; ky += y + t.height
        y += t.height + 14
        at += t.lastStart + BLOCK_PAUSE

        chip = Placed(y, at)
        mk += at
        ks += at + CHIP_DUR
        ky += y + CHIP_H
        y += CHIP_H
        end = at + 0.05f + CHIP_COUNT + 0.2f
        height = y
        marks = mk.toFloatArray()
        keyS = ks.toFloatArray()
        keyY = ky.toFloatArray()
    }

    /** Bottom edge of what the stream has revealed at S: linear between the key frames. */
    private fun revealedAt(s: Float): Float {
        val n = keyS.size
        if (s <= keyS[0]) return keyY[0]
        for (i in 1 until n) {
            if (s < keyS[i]) {
                val u = (s - keyS[i - 1]) / max(0.0001f, keyS[i] - keyS[i - 1])
                return keyY[i - 1] + (keyY[i] - keyY[i - 1]) * u
            }
        }
        return keyY[n - 1]
    }

    /** How far the thread is scrolled up at S so the newest line stays in the viewport. */
    fun scrollAt(s: Float, viewH: Float): Float {
        var sum = 0f
        for (k in 0 until 4) sum += revealedAt(s - FOLLOW_LAG * k)
        return max(0f, sum / 4 + FOLLOW_PAD - viewH)
    }

    /** The scroll the loop shows at loop time t: at rest, gliding back for the next turn, then following the stream. */
    fun loopScrollAt(t: Float, playing: Boolean, viewH: Float): Float {
        if (!playing || t < SCROLL_BACK_A) return scrollAt(end, viewH)
        if (t < SCROLL_BACK_B) return scrollAt(end, viewH) * (1 - easeInOutCubic(seg(t, SCROLL_BACK_A, SCROLL_BACK_B)))
        return scrollAt(t - STREAM_AT, viewH)
    }

    fun blockIndex(s: Float): Int {
        var n = 0
        for (m in marks) if (s >= m) n += 1
        return n
    }
}

/** The clocks and the hand. Plain fields, stepped once a frame and read in draw and layer blocks. */
private class Turn(val thread: ThreadPlan, val w: Float, val viewH: Float) {
    /** Bumped once a frame and by touches; every reader below subscribes to it. */
    val frame = mutableLongStateOf(0L)
    /** autoPlay and not reduced motion. State, so a change redraws even when no frame is running. */
    var auto by mutableStateOf(true)

    /** Loop time. Zero is the rest: the finished answer. */
    private var clock = 0f
    private var touching = false
    /** Under touch: the stream time, its speed after a release (s/s), and the state of the hand. */
    private var manual = thread.end
    private var speed = 0f
    private var dragging = false
    private var skipping = false
    private var dragFrom = 0f
    private var idleFor = 0f
    /** What the loop was showing when the finger arrived, eased away so nothing pops. */
    private var heldAlpha = 1f
    private var heldThink = 0f
    private var heldScroll = 0f
    private var lastBlock = -1

    private fun observe(): Long = frame.longValue

    /** One frame. True when a scrub crossed the start of a block (a selection tick). */
    fun step(dt: Float): Boolean {
        val block = if (touching && dragging) thread.blockIndex(manual) else -1
        val crossed = block >= 0 && lastBlock >= 0 && block != lastBlock
        lastBlock = block
        advance(dt)
        return crossed
    }

    private fun advance(dt: Float) {
        if (!touching) {
            if (auto) clock = (clock + dt) % LOOP
            return
        }
        val ease = min(1f, dt * 10)
        heldAlpha += (1 - heldAlpha) * ease
        heldThink *= 1 - ease
        heldScroll *= 1 - ease
        if (dragging) return
        idleFor += dt
        val end = thread.end
        var s = manual
        var v = speed
        if (idleFor > IDLE) {
            // Back to rest, which is the first frame of the loop.
            s += (end - s) * min(1f, dt * 7)
            v = 0f
            if (abs(end - s) < 0.01f) {
                manual = end
                speed = 0f
                skipping = false
                clock = 0f
                touching = false
                return
            }
        } else if (skipping) {
            s += (end - s) * min(1f, dt * 9)
            if (end - s < 0.01f) {
                s = end
                skipping = false
            }
        } else {
            // A release keeps its speed, slowing against drag; past an end a spring brings it back.
            s += v * dt
            v *= exp(-dt * 4.5f)
            if (s < 0) {
                s += (0 - s) * min(1f, dt * 12)
                v = 0f
            } else if (s > end) {
                s += (end - s) * min(1f, dt * 12)
                v = 0f
            }
        }
        manual = s
        speed = v
    }

    /** The stream clock everything reads. */
    fun stream(): Float {
        observe()
        if (!touching) return if (auto) streamAt(clock, thread.end) else thread.end
        return manual
    }

    /** How far the thread is scrolled up, dp. */
    fun scroll(): Float {
        observe()
        return if (!touching) thread.loopScrollAt(clock, auto, viewH) else thread.scrollAt(manual, viewH) + heldScroll
    }

    fun answerAlpha(): Float {
        observe()
        return if (!touching) (if (auto) answerAlphaAt(clock) else 1f) else heldAlpha
    }

    /** Past either end of a scrub the answer leans, dp. */
    fun lean(): Float {
        observe()
        if (!touching) return 0f
        val over = if (manual < 0) manual else if (manual > thread.end) manual - thread.end else 0f
        return over * LEAN
    }

    fun thinkingAlpha(): Float {
        observe()
        return if (!touching) (if (auto) thinkingAlphaAt(clock) else 0f) else heldThink
    }

    fun thinkingBand(): Float {
        observe()
        return if (!touching) (if (auto) thinkingBandAt(clock) else 0f) else 0.5f
    }

    // ---- Touch: tap to finish, drag sideways to scrub ----------------------------------------

    private fun takeOver() {
        if (!touching) {
            val t = clock
            val s = if (auto) streamAt(t, thread.end) else thread.end
            val shown = thread.loopScrollAt(t, auto, viewH)
            val start = s.coerceIn(0f, thread.end)
            heldAlpha = if (auto) answerAlphaAt(t) else 1f
            heldThink = if (auto) thinkingAlphaAt(t) else 0f
            heldScroll = shown - thread.scrollAt(start, viewH)
            manual = start
            speed = 0f
            touching = true
        }
        idleFor = 0f
    }

    fun scrubStart() {
        takeOver()
        dragging = true
        skipping = false
        dragFrom = manual.coerceIn(0f, thread.end)
        frame.longValue++
    }

    /** `dx` in dp since the scrub began. */
    fun scrubMove(dx: Float) {
        val end = thread.end
        val raw = dragFrom + (dx / (w * SCRUB_SPAN)) * end
        manual = if (raw < 0) rubber(raw) else if (raw > end) end + rubber(raw - end) else raw
        idleFor = 0f
    }

    /** `vx` in dp/s at release. */
    fun scrubEnd(vx: Float) {
        if (!dragging) return
        dragging = false
        idleFor = 0f
        speed = if (manual < 0 || manual > thread.end) 0f else (vx / (w * SCRUB_SPAN)) * thread.end
    }

    fun tap() {
        takeOver()
        speed = 0f
        skipping = true
        frame.longValue++
    }
}

/**
 * @param autoPlay Plays the turn by itself every [LOOP] seconds; a touch takes over.
 */
@Composable
fun StreamFadeAnswer(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
) {
    BlockSystemBars(darkIcons = true)
    val density = LocalDensity.current.density
    val view = LocalView.current
    val reducedMotion = rememberReducedMotion()
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val insetTop = insets.calculateTopPadding()
    val bottomPad = max(insets.calculateBottomPadding().value, 10f)

    BoxWithConstraints(modifier.fillMaxSize().background(PAGE).clipToBounds()) {
        val w = maxWidth.value
        val h = maxHeight.value
        val top = insetTop.value + HEADER_H
        val viewH = h - top - (COMPOSER_H + bottomPad)
        val thread = remember(w) { ThreadPlan(w) }
        val turn = remember(thread, w, viewH) { Turn(thread, w, viewH).apply { auto = autoPlay && !reducedMotion } }
        SideEffect { turn.auto = autoPlay && !reducedMotion }
        val stream = remember(turn) { { turn.stream() } }

        // The finished answer, no loop, no gestures under reduced motion.
        LaunchedEffect(turn, reducedMotion) {
            if (reducedMotion) return@LaunchedEffect
            var last = 0L
            while (true) {
                withFrameNanos { now ->
                    val dt = if (last == 0L) 0.016f else min((now - last) / 1e9f, 0.05f)
                    last = now
                    if (turn.step(dt)) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    turn.frame.longValue = now
                }
            }
        }

        // The thread, between the header and the composer.
        Box(
            Modifier
                .offset(y = top.dp)
                .fillMaxWidth()
                .height(viewH.dp)
                .clipToBounds()
                .pointerInput(turn, reducedMotion) {
                    if (reducedMotion) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var scrubbing = false
                        var failed = false
                        var released = false
                        var farthest = 0f
                        var fromX = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                released = true
                                break
                            }
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val dx = (change.position.x - down.position.x) / density
                            val dy = (change.position.y - down.position.y) / density
                            farthest = max(farthest, hypot(dx, dy))
                            // A sideways move of 8 dp starts the scrub; 14 dp up or down first gives way.
                            if (!scrubbing && !failed) {
                                if (abs(dy) > 14) {
                                    failed = true
                                } else if (abs(dx) > 8) {
                                    scrubbing = true
                                    fromX = change.position.x
                                    turn.scrubStart()
                                }
                            }
                            if (scrubbing) {
                                turn.scrubMove((change.position.x - fromX) / density)
                                change.consume()
                            }
                        }
                        if (scrubbing) {
                            turn.scrubEnd(tracker.calculateVelocity().x / density)
                        } else if (released && !failed && farthest <= 10) {
                            turn.tap()
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        }
                    }
                },
        ) {
            // A little taller than the thread, so the chip's shadow is inside the answer's layer while it
            // fades. Unbounded: on a short screen the thread is taller than the viewport, and a capped
            // height would cut everything below it out of that layer during the fade.
            Box(
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(Alignment.Top, unbounded = true)
                    .height((thread.height + 40).dp)
                    .graphicsLayer { translationY = -turn.scroll() * density },
            ) {
                // The question.
                Box(
                    Modifier
                        .offset(thread.bubbleX.dp, thread.bubbleY.dp)
                        .size(thread.bubbleW.dp, thread.bubbleH.dp)
                        .cssBoxShadow(rgba(29, 28, 26, 0.12f), offsetY = 10.dp, blur = 24.dp, radius = 20.dp)
                        .cssBoxShadow(rgba(29, 28, 26, 0.22f), offsetY = 1.dp, blur = 2.dp, radius = 20.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(BUBBLE)
                        .insetTopLight(Color.White.copy(alpha = 0.1f), 20.dp)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    StreamingText(QUESTION, width = (thread.bubbleW - 28).dp, fontSize = 16.dp, lineHeight = 22.dp, color = BUBBLE_INK, settled = true)
                }

                // The assistant's label.
                Row(
                    Modifier.offset(thread.left.dp, thread.labelY.dp).height(22.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .drawBehind {
                                drawRect(
                                    Brush.linearGradient(
                                        0f to Color(0xFF4F9272), 0.55f to Color(0xFF2F6B4F), 1f to Color(0xFF214F3A),
                                        start = Offset(size.width * 0.2f, 0f), end = Offset(size.width * 0.8f, size.height),
                                    ),
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(7.dp).background(rgba(246, 244, 239, 0.92f), CircleShape))
                    }
                    Text("Juniper", style = TextStyle(color = SOFT, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
                }

                ThinkingLine(turn, Modifier.offset(thread.left.dp, thread.answerY.dp).size(thread.width.dp, 28.dp))

                // The answer.
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        alpha = turn.answerAlpha()
                        translationX = turn.lean() * density
                    },
                ) {
                    StreamingText(
                        HEADING, width = thread.width.dp, modifier = Modifier.offset(thread.left.dp, thread.heading.y.dp),
                        time = stream, start = thread.heading.at, fontSize = 22.dp, lineHeight = 28.dp, fontWeight = 700, color = INK, seed = 1f,
                    )
                    for (i in PARAGRAPHS.indices) {
                        StreamingText(
                            PARAGRAPHS[i], width = thread.width.dp, modifier = Modifier.offset(thread.left.dp, thread.paragraphs[i].y.dp),
                            time = stream, start = thread.paragraphs[i].at, fontSize = BODY_SIZE.dp, lineHeight = BODY_LINE.dp, color = INK, seed = 2f + i,
                        )
                    }
                    for (i in ROWS.indices) {
                        DayRow(
                            ROWS[i], stream, thread.rows[i].at,
                            Modifier.offset(thread.left.dp, thread.rows[i].y.dp).size(thread.width.dp, ROW_H.dp),
                        )
                    }
                    StreamingText(
                        TIP, width = thread.width.dp, modifier = Modifier.offset(thread.left.dp, thread.tip.y.dp),
                        time = stream, start = thread.tip.at, fontSize = BODY_SIZE.dp, lineHeight = BODY_LINE.dp, color = SOFT, seed = 7f,
                    )
                    CostChip(stream, thread.chip.at, Modifier.offset(thread.left.dp, thread.chip.y.dp))
                }
            }
        }

        // The header.
        Row(
            Modifier
                .fillMaxWidth()
                .height(top.dp)
                .background(PAGE)
                .drawBehind { drawRect(LINE, topLeft = Offset(0f, size.height - 1f), size = Size(size.width, 1f)) }
                .padding(top = insetTop)
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.CenterStart) { BackIcon(19.dp, INK) }
            Text("Lisbon trip", style = TextStyle(color = INK, fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
            Box(Modifier.size(44.dp), contentAlignment = Alignment.CenterEnd) { ComposeIcon(20.dp, INK) }
        }

        // The composer, over a fade of the page.
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().clearAndSetSemantics { }) {
            Box(
                Modifier.fillMaxWidth().height(26.dp).background(
                    Brush.verticalGradient(0f to PAGE.copy(alpha = 0f), 0.5f to PAGE.copy(alpha = 0.86f), 1f to PAGE),
                ),
            )
            Box(Modifier.fillMaxWidth().height((COMPOSER_H - 26 + 8).dp).background(PAGE), contentAlignment = Alignment.TopCenter) {
                Row(
                    Modifier
                        .offset(y = (-8).dp)
                        .size(min(w - SIDE * 2, MAX_CONTENT).dp, 46.dp)
                        .cssBoxShadow(rgba(29, 28, 26, 0.09f), offsetY = 10.dp, blur = 26.dp, radius = 23.dp)
                        .cssBoxShadow(rgba(29, 28, 26, 0.07f), offsetY = 1.dp, blur = 2.dp, radius = 23.dp)
                        .clip(CircleShape)
                        .background(FIELD)
                        .insetTopLight(Color.White.copy(alpha = 0.9f), 23.dp)
                        .border(Dp.Hairline, LINE, CircleShape)
                        .padding(start = 18.dp, end = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Ask a follow-up", style = TextStyle(color = MUTE, fontSize = 16.sp, fontWeight = FontWeight.Normal))
                    Box(Modifier.size(32.dp).background(ACCENT, CircleShape), contentAlignment = Alignment.Center) { SendIcon(15.dp, PAGE) }
                }
            }
            Spacer(Modifier.height(bottomPad.dp))
        }
    }
}

/** CSS `inset 0 1px 0`: the sliver of a rounded box that the same box, one dp lower, does not cover. */
private fun Modifier.insetTopLight(color: Color, radius: Dp) = drawWithContent {
    drawContent()
    val box = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius.toPx()))) }
    val lower = Path().apply { addPath(box, Offset(0f, 1.dp.toPx())) }
    drawPath(Path.combine(PathOperation.Difference, box, lower), color)
}

private const val THINK_FONT_SIZE = 16f
private const val BAND = 70f

/** "Checking fares…": grey text with a lighter band travelling across its letters. */
@Composable
private fun ThinkingLine(turn: Turn, modifier: Modifier) {
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
            typeface = systemTypeface(500)
            textSize = THINK_FONT_SIZE
        }
    }
    val textWidth = remember(paint) { paint.measureText(THINKING) }
    val band = remember {
        LinearGradient(
            -BAND, 0f, BAND, 0f,
            intArrayOf(0xFFA5A199.toInt(), 0xFFE9E6DF.toInt(), 0xFFA5A199.toInt()), null, Shader.TileMode.CLAMP,
        )
    }
    val bandAt = remember { Matrix() }
    Canvas(modifier.clearAndSetSemantics { }) {
        val alpha = turn.thinkingAlpha()
        if (alpha <= 0.001f) return@Canvas
        bandAt.setTranslate(-BAND + (textWidth + BAND) * turn.thinkingBand(), 0f)
        band.setLocalMatrix(bandAt)
        // Opaque before the shader goes on: a paint keeps the alpha of its last colour.
        paint.alpha = 255
        paint.shader = band
        paint.alpha = (alpha * 255).roundToInt()
        drawIntoCanvas { c ->
            val canvas = c.nativeCanvas
            canvas.save()
            canvas.scale(density, density)
            canvas.drawText(THINKING, 0f, 20f, paint)
            canvas.restore()
        }
    }
}

/** One day of the itinerary: a hairline draws in from the left, the row rises in behind it. */
@Composable
private fun DayRow(row: Day, stream: () -> Float, at: Float, modifier: Modifier) {
    Box(
        modifier.drawBehind {
            val p = easeOutCubic(seg(stream(), at, at + LINE_DUR))
            if (p > 0f) drawRect(LINE, size = Size(size.width * p, 1f))
        },
    ) {
        Row(
            Modifier.fillMaxSize().graphicsLayer {
                val p = easeOutCubic(seg(stream(), at + 0.06f, at + 0.06f + ROW_DUR))
                alpha = p
                translationY = ROW_RISE * (1 - p) * density
            },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(34.dp).background(ACCENT_SOFT, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Text(row.day, style = TextStyle(color = ACCENT, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(row.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(color = INK, fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
                Text(row.detail, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(color = MUTE, fontSize = 14.sp, fontWeight = FontWeight.Normal))
            }
            Text(row.cost, style = TextStyle(color = INK, fontSize = 16.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"))
        }
    }
}

/** The total: pops in, and the number counts up to it. */
@Composable
private fun CostChip(stream: () -> Float, at: Float, modifier: Modifier) {
    // The layer is larger than the chip, so its shadow is not cut off while the chip fades in.
    Box(
        modifier
            .offset((-CHIP_BLEED).dp, (-CHIP_BLEED).dp)
            .graphicsLayer {
                val p = easeOutCubic(seg(stream(), at, at + CHIP_DUR))
                alpha = p
                translationY = 6 * (1 - p) * density
                scaleX = 0.94f + 0.06f * p
                scaleY = 0.94f + 0.06f * p
            }
            .padding(CHIP_BLEED.dp),
    ) {
        Row(
            Modifier
                .height(CHIP_H.dp)
                .cssBoxShadow(rgba(29, 28, 26, 0.07f), offsetY = 8.dp, blur = 20.dp, radius = (CHIP_H / 2).dp)
                .cssBoxShadow(rgba(29, 28, 26, 0.08f), offsetY = 1.dp, blur = 2.dp, radius = (CHIP_H / 2).dp)
                .clip(CircleShape)
                .background(FIELD)
                .insetTopLight(Color.White.copy(alpha = 0.9f), (CHIP_H / 2).dp)
                .border(Dp.Hairline, LINE, CircleShape)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val strong = TextStyle(color = INK, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")
            Text("€", style = strong)
            ChipAmount(stream, at, strong)
            Text("of €400", style = TextStyle(color = MUTE, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"))
            Box(Modifier.padding(horizontal = 9.dp).size(3.dp).background(MUTE.copy(alpha = 0.7f), CircleShape))
            Text("€28 to spare", style = TextStyle(color = ACCENT, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"))
        }
    }
}

private const val CHIP_BLEED = 28f

/** The counting number, alone in its own composable: only this text recomposes while it counts. */
@Composable
private fun ChipAmount(stream: () -> Float, at: Float, style: TextStyle) {
    val amount by remember(stream, at) {
        derivedStateOf { (TOTAL * easeOutCubic(seg(stream(), at + 0.05f, at + 0.05f + CHIP_COUNT))).roundToInt() }
    }
    Text("$amount", modifier = Modifier.width(32.dp), maxLines = 1, softWrap = false, style = style)
}

// ---- Icons, drawn on a 24-unit grid ----------------------------------------------------------

@Composable
private fun BackIcon(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val u = this.size.width / 24
        val path = Path().apply {
            moveTo(20 * u, 12 * u)
            lineTo(5 * u, 12 * u)
            moveTo(12 * u, 5 * u)
            lineTo(5 * u, 12 * u)
            lineTo(12 * u, 19 * u)
        }
        drawPath(path, color, style = Stroke(2.1f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun ComposeIcon(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val u = this.size.width / 24
        val frame = Path().apply {
            moveTo(11 * u, 4 * u)
            lineTo(6.5f * u, 4 * u)
            quadraticTo(4 * u, 4 * u, 4 * u, 6.5f * u)
            lineTo(4 * u, 17.5f * u)
            quadraticTo(4 * u, 20 * u, 6.5f * u, 20 * u)
            lineTo(17.5f * u, 20 * u)
            quadraticTo(20 * u, 20 * u, 20 * u, 17.5f * u)
            lineTo(20 * u, 13 * u)
        }
        val pencil = Path().apply {
            moveTo(19.6f * u, 4.4f * u)
            lineTo(10.6f * u, 13.4f * u)
            lineTo(9.6f * u, 14.6f * u)
        }
        drawPath(frame, color, style = Stroke(1.9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(pencil, color, style = Stroke(2.3f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun SendIcon(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val u = this.size.width / 24
        val path = Path().apply {
            moveTo(12 * u, 20.5f * u)
            lineTo(12 * u, 4.5f * u)
            moveTo(5 * u, 11.5f * u)
            lineTo(12 * u, 4.5f * u)
            lineTo(19 * u, 11.5f * u)
        }
        drawPath(path, color, style = Stroke(3f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
