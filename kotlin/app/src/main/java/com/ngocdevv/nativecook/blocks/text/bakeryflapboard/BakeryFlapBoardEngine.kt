package com.ngocdevv.nativecook.blocks.text.bakeryflapboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

// The board: two lines for the time and four for the menu, COLUMNS flaps each. A flap is as tall as
// FLAP_ASPECT × its width; CASE is the margin of the brass-edged case, PAD the room inside it.
private const val FLAP_ASPECT = 1.74f
internal const val FLAP_GAP = 2f
internal const val ROW_GAP = 9f
private const val LABEL_H = 24f
internal const val CASE = 16f
internal const val PAD = 14f
internal const val TIME_ROWS = 2
private const val BOARD_AT = 0.32f

// A flip: each step of the drum takes STEP s; columns start COL_DELAY s apart plus up to COL_JITTER s,
// like a real board's motors. The last flap of a cell lands with a bounce over BOUNCE s.
private const val STEP = 0.05
private const val COL_DELAY = 0.028
private const val COL_JITTER = 0.018
private const val BOUNCE = 0.17

// The strip of photos.
internal const val TILE_W = 128f
internal const val TILE_H = 164f
internal const val TILE_GAP = 10f

// The item card grows out of its line; its spring, and a drag down by DISMISS dp closes it.
private const val CARD_DELAY = 0.55
internal const val CARD_FADE = 0.18f
private const val CARD_STIFFNESS = 170f
private const val CARD_DAMPING = 22f
private const val DISMISS = 360f
private const val DISMISS_SPEED = 600f
private const val CLOSE_BELOW = 0.6f
private const val RUBBER = 0.2f

// autoPlay (seconds): lines change one after another, the time gives way to a notice and comes
// back, one line is "tapped" and its card opens and closes, and by LOOP every line is back.
internal const val LOOP = 18.0

private class Cue(val at: Double, val row: Int, val item: Int? = null, val notice: Boolean? = null, val card: Boolean? = null)

private val SCRIPT = listOf(
    Cue(0.6, 2, item = 1),
    Cue(2.0, 5, item = 1),
    Cue(3.4, 0, notice = true),
    Cue(5.0, 4, item = 1, card = true),
    Cue(8.8, -1, card = false),
    Cue(9.8, 2, item = 0),
    Cue(11.2, 3, item = 1),
    Cue(12.4, 0, notice = false),
    Cue(13.6, 5, item = 0),
    Cue(14.4, 4, item = 0),
    Cue(15.2, 3, item = 0),
)

// The time the board shows while it plays by itself, so every loop is the same.
private const val DEMO_HOUR = 7
private const val DEMO_MINUTE = 10
// After a touch the loop waits this long; longer when a card is open, so it can be read.
private const val USER_HOLD = 4.0
private const val CARD_HOLD = 7.0

internal fun clamp01(v: Float) = min(1f, max(0f, v))
internal fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

/** True when the loop clock went from `before` to `now` across `at`, wrapping at LOOP. */
private fun passed(before: Double, now: Double, at: Double) =
    (before < at && now >= at) || (now < before && (at > before || at <= now))

/** Where the board and each of its lines sit for a screen, in dp. */
internal class BoardLayout(width: Float, height: Float, top: Float, bottom: Float, rowCount: Int) {
    val x = CASE
    val w = width - CASE * 2
    val left = x + PAD
    val pitch = (w - PAD * 2) / COLUMNS
    val cellW = pitch - FLAP_GAP
    val cellH = (cellW * FLAP_ASPECT).roundToInt().toFloat()
    val h = PAD * 2 + LABEL_H * 2 + rowCount * (cellH + ROW_GAP) - ROW_GAP + 6f
    val y: Float
    val rows = FloatArray(rowCount)
    val labels = ArrayList<Float>()

    init {
        val regionTop = stripBottom(top) + 18f
        val regionBottom = height - bottom - 44f
        // The board sits a little above the middle of the room under the strip.
        y = max(regionTop, regionTop + (regionBottom - regionTop - h) * BOARD_AT)
        var cursor = y + PAD
        for (r in 0 until rowCount) {
            if (r == 0 || r == TIME_ROWS) {
                if (r == TIME_ROWS) cursor += 6f
                labels.add(cursor + 15f)
                cursor += LABEL_H
            }
            rows[r] = cursor
            cursor += cellH + ROW_GAP
        }
    }

    fun cellX(i: Int) = left + i * pitch + FLAP_GAP / 2f

    companion object {
        fun stripBottom(top: Float) = top + 40f + TILE_H
    }
}

/** The card is CARD_OVERHANG dp wider than the case on each side. */
internal const val CARD_OVERHANG = 4f
/** The card's photo is CARD_PHOTO × its width tall; the text under it takes CARD_BODY dp. */
internal const val CARD_PHOTO = 0.7f
internal const val CARD_BODY = 164f

/**
 * The card's frame when fully open, centred on the screen between the safe areas (the scrim hides
 * the board, so the screen is what it is seen against). On a short screen it shrinks to fit, keeping
 * its photo's proportions.
 */
internal class CardRect(width: Float, height: Float, top: Float, bottom: Float) {
    private val space = max(1f, (height - bottom - 12f) - (top + 12f))
    val w = max(1f, min(width - (CASE - CARD_OVERHANG) * 2f, (space - CARD_BODY) / CARD_PHOTO))
    val h = w * CARD_PHOTO + CARD_BODY
    val x = (width - w) / 2f
    val y = top + 12f + max(0f, space - h) / 2f
}

/** A line of flaps: what it showed when its last change began, what it is going to, and when. */
internal class FlapRow(val from: IntArray, val to: IntArray, val text: String, val at: Double)

/**
 * The state of one cell: the character showing (`cur`), the one coming (`next`) and how far the
 * falling half has turned (0…1), or −1 once it has settled; `bounce` runs 0…1 just after it lands.
 */
internal class FlapCell {
    var cur = 0
    var next = 0
    var p = -1f
    var bounce = -1f
}

/** A column's own small delay, fixed per column, so a line never starts as one block. */
private val JITTER = DoubleArray(COLUMNS) { i ->
    val s = sin(i * 12.9898 + 4.1) * 43758.5453
    (s - floor(s)) * COL_JITTER
}

private fun drumIndex(ch: Char) = DRUM.indexOf(ch).let { if (it < 0) 0 else it }

private fun indices(text: String) = IntArray(COLUMNS) { drumIndex(text.getOrElse(it) { ' ' }) }

internal fun cellAt(row: FlapRow, i: Int, now: Double, out: FlapCell): FlapCell {
    val n = DRUM.length
    val from = row.from[i]
    val to = row.to[i]
    val steps = (to - from + n) % n
    out.p = -1f
    out.bounce = -1f
    if (steps == 0) {
        out.cur = to
        out.next = to
        return out
    }
    val t = now - row.at - i * COL_DELAY - JITTER[i]
    if (t < 0) {
        out.cur = from
        out.next = from
        return out
    }
    val k = floor(t / STEP).toInt()
    if (k >= steps) {
        val after = (t - steps * STEP) / BOUNCE
        out.cur = to
        out.next = to
        out.bounce = if (after < 1) after.toFloat() else -1f
        return out
    }
    out.cur = (from + k) % n
    out.next = (from + k + 1) % n
    out.p = ((t - k * STEP) / STEP).toFloat()
    return out
}

/** The characters a line shows `now`, mid-flip or not. */
private fun showing(row: FlapRow, now: Double): IntArray {
    val cell = FlapCell()
    return IntArray(COLUMNS) { cellAt(row, it, now, cell).cur }
}

/** The board's state, stepped once a frame like the RN frame callback, and the gestures that move it. */
internal class BakeryFlapBoardMotion(private val menu: List<List<BakeItem>>, notice: List<String>, auto: Boolean) {
    var width = 0f
    var height = 0f
    var top = 0f
    var bottom = 0f
    var reducedMotion = false
    val rowCount = TIME_ROWS + menu.size

    private val noticeLines = notice.map(::centred)
    private val items = IntArray(menu.size)
    private var showNotice = false
    private var time = if (auto) demoTime() else liveTime()
    var rows = Array(rowCount) { r ->
        // On first appearance every line flips in from blank.
        val to = if (r < TIME_ROWS) time[r] else line(r - TIME_ROWS, 0)
        FlapRow(IntArray(COLUMNS), indices(to), to, 0.35 + r * 0.16)
    }
        private set
    var elapsed = 0.0
        private set
    private var clock = 0.0
    private var looping = auto
    private var hold = 0.0
    private var lastLiveCheck = Double.NEGATIVE_INFINITY

    // The item card: 0 folded into its line … 1 open, its velocity and target, the line it came from,
    // and a line waiting to open once its flip is under way (with the time it opens).
    var cardOpen = 0f
        private set
    private var cardVelocity = 0f
    private var cardTarget = 0f
    var cardRow = -1
        private set
    var cardItem by mutableStateOf(menu[0][0])
        private set
    private var cardDragging = false
    private var cardFrom = 0f
    private var pending: Triple<Int, Double, Boolean>? = null

    var onTick: () -> Unit = {}
    var onSoft: () -> Unit = {}
    var onOpen: (BakeItem) -> Unit = {}

    private fun liveTime(): List<String> {
        val now = Calendar.getInstance()
        return timeInWords(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE)).map(::centred)
    }

    private fun demoTime() = timeInWords(DEMO_HOUR, DEMO_MINUTE).map(::centred)

    private fun line(row: Int, item: Int) = menu[row][item].let { menuLine(it.board, it.price) }

    fun resetLoop(auto: Boolean) {
        looping = auto
        clock = 0.0
        hold = 0.0
        time = if (auto) demoTime() else liveTime()
    }

    /** Moves menu line `row` to `item` (or the next one), and asks for its card if `card`. */
    private fun advance(row: Int, item: Int, card: Boolean, user: Boolean) {
        if (row < TIME_ROWS) {
            showNotice = !showNotice
            return
        }
        val list = menu[row - TIME_ROWS]
        items[row - TIME_ROWS] = if (item < 0) (items[row - TIME_ROWS] + 1) % list.size else item
        if (card) pending = Triple(row, elapsed + CARD_DELAY, user)
    }

    private fun closeCard(velocity: Float) {
        cardTarget = 0f
        cardVelocity = velocity
        pending = null
    }

    fun step(dt: Double) {
        val now = elapsed + dt
        elapsed = now

        // The time in words, kept current while the board is not playing by itself.
        if (!looping && now - lastLiveCheck >= 15.0) {
            lastLiveCheck = now
            time = liveTime()
        }

        if (hold > 0) {
            hold -= dt
            if (hold <= 0 && looping) {
                // Back to the loop's resting board before it starts over.
                clock = 0.0
                closeCard(0f)
                showNotice = false
                items.fill(0)
            }
        } else if (looping) {
            val before = clock
            val t = (before + dt) % LOOP
            clock = t
            for (cue in SCRIPT) {
                if (!passed(before, t, cue.at)) continue
                when {
                    cue.notice != null -> showNotice = cue.notice
                    cue.card == false -> closeCard(0f)
                    cue.item != null -> advance(cue.row, cue.item, cue.card == true, false)
                }
            }
        }

        // Each line chases the text it should show; a change starts from whatever it shows now.
        for (r in 0 until rowCount) {
            val wanted = if (r < TIME_ROWS) (if (showNotice && r < noticeLines.size) noticeLines[r] else time[r]) else line(r - TIME_ROWS, items[r - TIME_ROWS])
            if (rows[r].text == wanted) continue
            rows[r] = FlapRow(showing(rows[r], now), indices(wanted), wanted, now)
        }

        val ask = pending
        if (ask != null && now >= ask.second) {
            pending = null
            cardRow = ask.first
            cardTarget = 1f
            val entry = menu[ask.first - TIME_ROWS][items[ask.first - TIME_ROWS]]
            cardItem = entry
            if (ask.third) onOpen(entry)
        }

        if (!cardDragging) {
            val d = dt.toFloat()
            if (reducedMotion) {
                val gap = cardTarget - cardOpen
                cardOpen += sign(gap) * min(abs(gap), d / 0.22f)
            } else {
                cardVelocity += (CARD_STIFFNESS * (cardTarget - cardOpen) - CARD_DAMPING * cardVelocity) * d
                cardOpen += cardVelocity * d
            }
            if (cardTarget == 0f && cardOpen < 0.002f && abs(cardVelocity) < 0.05f) {
                cardOpen = 0f
                cardVelocity = 0f
                cardRow = -1
            }
        }
    }

    // Touch

    fun panStart() {
        if (cardRow < 0 || cardTarget != 1f) return
        hold = CARD_HOLD
        cardDragging = true
        cardFrom = cardOpen
    }

    fun panUpdate(translationY: Float) {
        if (!cardDragging) return
        val p = cardFrom - translationY / DISMISS
        cardOpen = if (p > 1f) 1f + (p - 1f) * RUBBER else max(0f, p)
    }

    fun panEnd(rawVelocityY: Float) {
        if (!cardDragging) return
        // A velocity tracker with too few samples can answer NaN, which would reach the card's
        // roundToInt sizes through the spring; treat it as a still finger.
        val velocityY = if (rawVelocityY.isFinite()) rawVelocityY else 0f
        cardDragging = false
        val velocity = -velocityY / DISMISS
        if (velocityY > DISMISS_SPEED || (cardOpen < CLOSE_BELOW && velocityY > -DISMISS_SPEED)) {
            closeCard(velocity)
            onSoft()
        } else {
            cardTarget = 1f
            cardVelocity = velocity
        }
    }

    fun tap(x: Float, y: Float) {
        if (cardRow >= 0 || pending != null) {
            hold = USER_HOLD
            closeCard(0f)
            return
        }
        val layout = BoardLayout(width, height, top, bottom, rowCount)
        for (r in 0 until rowCount) {
            val rowTop = layout.rows[r]
            if (y < rowTop - ROW_GAP / 2f || y > rowTop + layout.cellH + ROW_GAP / 2f) continue
            if (x < layout.x || x > layout.x + layout.w) continue
            hold = if (r < TIME_ROWS) USER_HOLD else CARD_HOLD
            advance(r, -1, r >= TIME_ROWS, true)
            onTick()
            return
        }
    }
}
