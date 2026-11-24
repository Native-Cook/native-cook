package com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete

import com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete.MentionAutocompleteData.PEOPLE
import com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete.MentionAutocompleteData.mention
import com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete.MentionAutocompleteData.rank
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/*
 Mention Autocomplete — the loop as a pure function of time (a port of mentionMotion.ts).

 The composition follows the script's steps (what the composer holds: text, chips, which is
 selected); everything that moves between steps (the popover, the rows, the chip morph, the caret,
 the ghost finger) comes from `autoPose(t)` in draw and layer lambdas.

 Timeline (s)          what happens
 0.00 – 0.85           rest: empty composer
 0.55                  ghost tap on the composer
 0.85 – 1.21           "cc @" typed; the "@" opens the popover, which grows out of the caret
 1.60 – 1.96           "may" typed one letter at a time; rows filter and re-rank on springs
 2.45                  ghost tap on Maya Okafor
 2.50 – 2.95           the typed "@may" morphs into the chip "@Maya Okafor" and a space after it; the popover
                       shrinks into the caret
 3.10 – 4.00           "for review" typed after the space
 4.00 – 5.00           hold
 5.00 – 5.50           Backspace deletes "for review", then the space
 5.75                  Backspace on the chip selects it (filled accent)
 6.30 – 6.75           Backspace again breaks it back into text
 6.90 – 7.75           the line is deleted, faster and faster
 7.75 – 9.00           rest
*/

/** How the chip is drawn at a step: grown from letters, a chip, selected, or breaking into text. */
enum class MentionAutocompleteChipMode { None, Morph, Chip, Selected, Break }

internal object MentionAutocompleteMotion {
    const val LOOP = 9f
    const val IDLE = 2f
    const val RETURN_LEN = 0.7f
    /** Loop time shown, still, under reduced motion: the composed message with its chip. */
    const val T_STILL = 4.5f

    private const val PICKED = 2 // Maya Okafor
    private const val PREFIX = "cc "
    private const val TOKEN = "@may"
    private const val SUFFIX = "for review"
    val LABEL = mention(PEOPLE[PICKED])
    val CHIP_START = PREFIX.length
    /** The typed letters the chip grows out of. */
    const val MORPH_FROM = TOKEN

    // ---- Timeline -------------------------------------------------------------------------------
    const val FIELD_TAP = 0.55f
    private const val FOCUS_AT = 0.6f
    private const val TYPE_AT = 0.85f
    private const val TYPE_GAP = 0.12f
    private const val AT_AT = TYPE_AT + 3 * TYPE_GAP
    private const val QUERY_AT = 1.6f
    private const val QUERY_GAP = 0.18f
    const val PICK_AT = 2.45f
    private const val CHIP_AT = 2.5f
    const val MORPH_LEN = 0.45f
    private const val MORPH_DONE = 3.0f
    private const val SUFFIX_AT = 3.1f
    private const val SUFFIX_GAP = 0.09f
    private const val DELETE_AT = 5.0f
    private const val DELETE_GAP = 0.05f
    private const val SELECT_AT = 5.75f
    const val SELECT_LEN = 0.16f
    private const val BREAK_AT = 6.3f
    const val BREAK_LEN = 0.45f
    private const val CLEAR_AT = 6.9f
    private const val CLEAR_END = 7.75f
    private const val BLUR_AT = 7.95f

    const val ROW_H = 54f
    const val POP_HEAD = 30f
    const val POP_PAD = 6f
    private const val ROW_MOVE = 0.36f
    /** How long the thread takes to make room for a sent message (and to give it back), in ms. */
    const val SEND_MS = 300

    class Step(
        val doc: MentionAutocompleteDoc,
        /** The mention being typed, for the popover; null while it is closed. */
        val query: String?,
        val chip: MentionAutocompleteChipMode,
    )

    val SCRIPT: List<Step>
    private val TIMES: FloatArray
    private val N = PEOPLE.size
    private val ROW_Y: List<FloatArray>
    private val ROW_A: List<FloatArray>
    private val COUNTS: FloatArray
    private val TEXT_FLAGS: FloatArray

    init {
        val steps = mutableListOf(Step(MentionAutocompleteData.EMPTY_DOC, null, MentionAutocompleteChipMode.None))
        val times = mutableListOf(-1f)
        fun push(at: Float, step: Step) { steps.add(step); times.add(at) }
        val typed = PREFIX + TOKEN
        for (k in 1..typed.length) {
            val text = typed.substring(0, k)
            val at = if (k <= PREFIX.length + 1) TYPE_AT + (k - 1) * TYPE_GAP else QUERY_AT + (k - PREFIX.length - 2) * QUERY_GAP
            val open = k >= PREFIX.length + 1
            push(at, Step(MentionAutocompleteDoc(text, emptyList(), -1), if (open) text.substring(PREFIX.length + 1) else null, MentionAutocompleteChipMode.None))
        }
        fun chipDoc(text: String, selected: Int) = MentionAutocompleteDoc(text, listOf(MentionAutocompleteChip(CHIP_START, LABEL.length, PICKED)), selected)
        val base = PREFIX + LABEL
        // A pick puts a space after the chip, so the caret waits after "@Maya Okafor " and the next word stands apart.
        val spaced = "$base "
        push(CHIP_AT, Step(chipDoc(spaced, -1), null, MentionAutocompleteChipMode.Morph))
        push(MORPH_DONE, Step(chipDoc(spaced, -1), null, MentionAutocompleteChipMode.Chip))
        for (k in 1..SUFFIX.length) push(SUFFIX_AT + (k - 1) * SUFFIX_GAP, Step(chipDoc(spaced + SUFFIX.substring(0, k), -1), null, MentionAutocompleteChipMode.Chip))
        // Backspace takes the words, then the space; only the Backspace after that reaches the chip.
        for (k in SUFFIX.length downTo 0) {
            val text = if (k == 0) base else spaced + SUFFIX.substring(0, k - 1)
            push(DELETE_AT + (SUFFIX.length - k) * DELETE_GAP, Step(chipDoc(text, -1), null, MentionAutocompleteChipMode.Chip))
        }
        push(SELECT_AT, Step(chipDoc(base, 0), null, MentionAutocompleteChipMode.Selected))
        push(BREAK_AT, Step(MentionAutocompleteDoc(base, emptyList(), -1), null, MentionAutocompleteChipMode.Break))
        // The line goes, one character at a time, each gap shorter than the last.
        for (k in 1..base.length) {
            val u = k.toFloat() / base.length
            val at = CLEAR_AT + (CLEAR_END - CLEAR_AT) * (1 - (1 - u).pow(1.6f))
            push(at, Step(MentionAutocompleteDoc(base.substring(0, base.length - k), emptyList(), -1), null, MentionAutocompleteChipMode.None))
        }
        SCRIPT = steps
        TIMES = times.toFloatArray()

        // Popover rows per step: slot (kept while hidden) and visibility.
        val lists = steps.map { if (it.query == null) emptyList() else rank(it.query) }
        // After the pick the rows keep where they were; the popover itself closes.
        var lastOpen: List<Int> = emptyList()
        val held = lists.map { l ->
            if (l.isNotEmpty()) lastOpen = l
            if (l.isNotEmpty()) l else lastOpen
        }
        val rowY = mutableListOf<FloatArray>()
        val rowA = mutableListOf<FloatArray>()
        for (i in 0 until N) {
            val ys = IntArray(steps.size) { held[it].indexOf(i) }
            val asv = FloatArray(steps.size) { if (lists[it].indexOf(i) >= 0) 1f else 0f }
            val known = ys.copyOf()
            for (k in ys.indices) {
                if (known[k] >= 0) continue
                var fill = -1
                var j = k - 1
                while (j >= 0 && fill < 0) { if (known[j] >= 0) fill = known[j]; j -= 1 }
                j = k + 1
                while (j < ys.size && fill < 0) { if (known[j] >= 0) fill = known[j]; j += 1 }
                ys[k] = if (fill < 0) 0 else fill
            }
            rowY.add(FloatArray(ys.size) { ys[it].toFloat() })
            rowA.add(asv)
        }
        ROW_Y = rowY
        ROW_A = rowA
        // The popover keeps its last size while it closes; it is rest-sized again before it next opens.
        COUNTS = FloatArray(held.size) { held[it].size.toFloat() }
        TEXT_FLAGS = FloatArray(steps.size) { if (steps[it].doc.text.isNotEmpty()) 1f else 0f }
    }

    // ---- Curves ---------------------------------------------------------------------------------
    fun clamp01(x: Float) = if (x < 0f) 0f else if (x > 1f) 1f else x

    fun seg(t: Float, a: Float, b: Float) = clamp01((t - a) / (b - a))

    fun easeOutCubic(x: Float): Float {
        val u = 1 - x
        return 1 - u * u * u
    }

    fun easeInOutCubic(x: Float): Float =
        if (x < 0.5f) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

    fun easeOutBack(x: Float): Float {
        val c = 1.5f
        val u = x - 1
        return 1 + (c + 1) * u * u * u + c * u * u
    }

    private fun superpose(values: FloatArray, t: Float, len: Float, stagger: Float): Float {
        var v = values[0]
        for (j in 1 until values.size) {
            val d = values[j] - values[j - 1]
            if (d == 0f) continue
            val at = TIMES[j] + stagger
            v += d * easeOutCubic(seg(t, at, at + len))
        }
        return v
    }

    /** Spring-like settle for row slots: a little overshoot, as the person's springs have. */
    private fun superposeSpring(values: FloatArray, t: Float, len: Float): Float {
        var v = values[0]
        for (j in 1 until values.size) {
            val d = values[j] - values[j - 1]
            if (d == 0f) continue
            v += d * easeOutBack(seg(t, TIMES[j], TIMES[j] + len))
        }
        return v
    }

    fun stepAt(t: Float): Int {
        var k = 0
        for (j in 1 until TIMES.size) if (t >= TIMES[j]) k = j
        return k
    }

    // ---- The pose -------------------------------------------------------------------------------
    class Pose(
        val focus: Float,
        val caret: Float,
        val placeholder: Float,
        /** The send button wakes when there is text. */
        val send: Float,
        /** Popover: 0 → 1 grows out of the caret (overshoots a little), back to 0 when it closes. */
        val pop: Float,
        val count: Float,
        val rowY: FloatArray,
        val rowA: FloatArray,
        /** Chip: grown from the typed letters 0 → 1; selected 0 → 1; breaking into text 0 → 1. */
        val morph: Float,
        val select: Float,
        val breakUp: Float,
    )

    class Dot(val target: Int, val alpha: Float, val scale: Float)

    fun autoPose(t: Float): Pose {
        val k = stepAt(t)
        val rowY = FloatArray(N) { superposeSpring(ROW_Y[it], t, ROW_MOVE) }
        val rowA = FloatArray(N) { superpose(ROW_A[it], t, ROW_MOVE * 0.7f, 0f) }
        val open = easeOutBack(seg(t, AT_AT, AT_AT + 0.36f))
        val close = easeInOutCubic(seg(t, CHIP_AT, CHIP_AT + 0.25f))
        val lastKey = if (TIMES[k] > 0f) TIMES[k] else 0f
        val since = t - lastKey
        val blink = if (since < 0.5f) 1f else clamp01(0.5f + 1.6f * cos(2 * PI.toFloat() * (since - 0.5f) / 1.06f))
        val focus = easeOutCubic(seg(t, FOCUS_AT, FOCUS_AT + 0.2f)) * (1 - seg(t, BLUR_AT, BLUR_AT + 0.25f))
        return Pose(
            focus = focus,
            caret = seg(t, FOCUS_AT + 0.15f, FOCUS_AT + 0.25f) * (1 - seg(t, BLUR_AT - 0.05f, BLUR_AT)) * blink,
            placeholder = 1 - superpose(TEXT_FLAGS, t, 0.12f, 0f),
            send = superpose(TEXT_FLAGS, t, 0.2f, 0f),
            pop = if (t < CHIP_AT) open else 1 - close,
            count = superpose(COUNTS, t, ROW_MOVE, 0f),
            rowY = rowY,
            rowA = rowA,
            morph = if (t < CHIP_AT + MORPH_LEN + 0.6f) seg(t, CHIP_AT, CHIP_AT + MORPH_LEN) else 1f,
            select = easeOutCubic(seg(t, SELECT_AT, SELECT_AT + SELECT_LEN)),
            breakUp = seg(t, BREAK_AT, BREAK_AT + BREAK_LEN),
        )
    }

    /** The ghost finger: taps the composer, then Maya's row. */
    fun autoDot(t: Float): Dot {
        var target = 0
        var alpha = 0f
        var scale = 1f
        val taps = floatArrayOf(FIELD_TAP, PICK_AT)
        for (k in taps.indices) {
            val at = taps[k]
            val a = seg(t, at - 0.24f, at - 0.06f) * (1 - seg(t, at + 0.12f, at + 0.36f))
            if (a > alpha) {
                alpha = a
                target = k
                scale = 1 - 0.22f * sin(PI.toFloat() * seg(t, at - 0.06f, at + 0.16f))
            }
        }
        return Dot(target, alpha, scale)
    }
}
