package com.ngocdevv.nativecook.blocks.forms.addresslookupmap

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 Address Lookup Map — the loop as a pure function of time (a port of addressLookupMotion.ts).

 `autoPose(t, s0)` gives every value the screen draws at loop time t (seconds, 0 ..< LOOP): the
 field, the suggestion rows, the camera over the drawn map, the pin, the filled fields and the
 ghost finger. A person drives the same values from Animatables (AddressLookupMap.kt), so the
 drawing code never knows which of the two is in charge.

 Timeline (s)            what happens
 0.00 – 0.90             rest: empty field, the town from above
 0.85                    ghost tap on the field, it takes the focus
 1.15 – 2.05             "48 Lark" typed, one key every 0.15 s; the suggestions rise in and re-rank
 2.85                    ghost tap on the first suggestion
 2.90 – 3.35             the row flies up into the field, the list folds away
 3.05 – 4.50             the map pans and zooms to the address (log-scale zoom, ease-in-out)
 4.40 – 5.00             the pin drops and bounces; two soft rings at 4.73 and 5.15
 4.95                    callout "Front door"
 5.00 – 5.58             unit, postcode, city fill in, 90 ms apart; the button wakes at 5.45
 5.95 – 7.25             ghost drag on the map: the map slides, the pin lifts and lands again
 7.30                    callout "Side gate · 9 m"
 8.40 – 9.40             everything empties, the map flies back out
 9.40 – 10.0             rest
*/

internal object AddressLookupMapMotion {
    const val LOOP = 10f
    /** Seconds after the last touch before the screen eases back and the loop restarts. */
    const val IDLE = 2f
    const val RETURN_LEN = 0.8f
    /** Loop time shown, without motion, under reduced motion. */
    const val T_STILL = 6.18f

    const val QUERY = "48 Lark"
    /** The suggestion the loop picks (index into ADDRESSES). */
    const val PICK = 0

    // ---- Timeline -------------------------------------------------------------------------------
    const val FIELD_TAP = 0.85f
    private const val FOCUS_AT = 0.9f
    private const val TYPE_AT = 1.15f
    private const val KEY_GAP = 0.15f
    const val PICK_AT = 2.85f
    private const val CHOOSE_AT = 2.9f
    const val CHOOSE_LEN = 0.45f
    private const val FLY_AT = 3.05f
    const val FLY_LEN = 1.45f
    private const val DROP_AT = 4.4f
    const val DROP_LEN = 0.6f
    private val RING_AT = floatArrayOf(4.73f, 5.15f)
    const val RING_LEN = 1.0f
    private const val CALL_A_AT = 4.95f
    private const val FILL_AT = 5.0f
    const val FILL_GAP = 0.09f
    const val FILL_LEN = 0.4f
    private const val READY_AT = 5.45f
    private const val DRAG_IN = 5.95f
    private const val DRAG_AT = 6.2f
    private const val DRAG_END = 7.0f
    private const val CALL_B_AT = 7.3f
    private const val RESET_AT = 8.4f
    private const val CLEAR_AT = 8.75f
    private const val BACK_AT = 8.55f
    private const val BACK_LEN = 0.85f

    /** Suggestion rows: height and how long a re-rank takes. */
    const val ROW_H = 58f
    private const val ROW_MOVE = 0.32f
    private const val ROW_STAGGER = 0.035f

    /** The ghost finger's drag on the map, dp on screen. */
    const val DRAG_DX = -46f
    const val DRAG_DY = -26f

    // ---- Map camera -----------------------------------------------------------------------------
    /** The map is turned a little so the grid does not read as a spreadsheet. */
    const val MAP_ROT = (-8 * PI / 180).toFloat()
    const val OVERVIEW_X = 600f
    const val OVERVIEW_Y = 650f
    /** Overview: this many world units across the map card. */
    const val OVERVIEW_SPAN = 1080f
    /** Close-up scale (screen dp per world unit). */
    const val NEAR = 1.05f
    const val MIN_SCALE_K = 0.85f
    const val MAX_SCALE = 2.2f

    // ---- The script the composition follows (field text, chosen address) -----------------------
    class Step(val query: String, val selected: Int?, val filled: Boolean)

    private val addresses = AddressLookupMapData.ADDRESSES
    val SCRIPT: List<Step>
    val SCRIPT_TIMES: FloatArray
    /** Script steps while the suggestions are open: 1 ..TYPED. */
    val TYPED = QUERY.length

    private val N = addresses.size
    private val ROW_Y: List<FloatArray>
    private val ROW_A: List<FloatArray>
    private val COUNTS: FloatArray

    init {
        val steps = mutableListOf(Step("", null, false))
        val times = mutableListOf(-1f)
        for (k in 1..QUERY.length) {
            steps.add(Step(QUERY.substring(0, k), null, false))
            times.add(TYPE_AT + (k - 1) * KEY_GAP)
        }
        steps.add(Step(addresses[PICK].street, PICK, false)); times.add(CHOOSE_AT)
        steps.add(Step(addresses[PICK].street, PICK, true)); times.add(FILL_AT - 0.02f)
        steps.add(Step("", null, false)); times.add(CLEAR_AT)
        SCRIPT = steps
        SCRIPT_TIMES = times.toFloatArray()

        // Rows per script step: slot (row index, kept while hidden) and visibility. After the pick
        // the list keeps its last rows (the pick folds them away); the clear empties it.
        val last = steps.size - 1
        val lists = steps.mapIndexed { k, s ->
            if (k == 0 || k == last) emptyList() else AddressLookupMapData.rank(if (k > TYPED) QUERY else s.query)
        }
        val rowY = mutableListOf<FloatArray>()
        val rowA = mutableListOf<FloatArray>()
        for (i in 0 until N) {
            val ys = IntArray(steps.size) { k -> lists[k].indexOf(i) }
            val asv = FloatArray(steps.size) { k -> if (lists[k].indexOf(i) >= 0) 1f else 0f }
            // A hidden row keeps its last slot, or waits in the slot it will appear in.
            val known = ys.copyOf()
            for (k in ys.indices) {
                if (known[k] >= 0) continue
                var prev = -1
                for (j in k - 1 downTo 0) if (known[j] >= 0) { prev = known[j]; break }
                var next = -1
                for (j in k + 1 until ys.size) if (known[j] >= 0) { next = known[j]; break }
                ys[k] = if (prev >= 0) prev else if (next >= 0) next else 0
            }
            rowY.add(FloatArray(ys.size) { ys[it].toFloat() })
            rowA.add(asv)
        }
        ROW_Y = rowY
        ROW_A = rowA
        COUNTS = FloatArray(lists.size) { lists[it].size.toFloat() }
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
        val c = 1.4f
        val u = x - 1
        return 1 + (c + 1) * u * u * u + c * u * u
    }

    /** Height of the falling pin above the ground, dp (negative = up), from drop progress 0 ..1. */
    fun dropY(p: Float): Float {
        if (p <= 0f) return -84f
        if (p < 0.5f) {
            val u = p / 0.5f
            return -84f * (1 - u * u)
        }
        if (p >= 1f) return 0f
        // Two bounces, the second a third of the first.
        val u = (p - 0.5f) / 0.5f
        if (u < 0.62f) return -15f * sin(PI.toFloat() * u / 0.62f)
        return -5f * sin(PI.toFloat() * (u - 0.62f) / 0.38f)
    }

    /** Squash of the pin's head as it touches down, 0 ..1. */
    fun dropSquash(p: Float): Float {
        val d1 = abs(p - 0.5f) / 0.06f
        val d2 = abs(p - 0.81f) / 0.05f
        return max(0f, 1 - d1) + 0.35f * max(0f, 1 - d2)
    }

    /** Superposes the script's per-step targets: each change eases in from its own start time. */
    private fun superpose(values: FloatArray, t: Float, len: Float, stagger: Float): Float {
        var v = values[0]
        for (j in 1 until values.size) {
            val d = values[j] - values[j - 1]
            if (d == 0f) continue
            val at = SCRIPT_TIMES[j] + stagger
            v += d * easeOutCubic(seg(t, at, at + len))
        }
        return v
    }

    /** The filled fields empty in reverse order at the end of the loop. */
    private fun fillOut(t: Float, i: Int): Float {
        val at = RESET_AT + (2 - i) * 0.05f
        return 1 - seg(t, at, at + 0.25f)
    }

    /** Index of the script step showing at loop time t. */
    fun stepAt(t: Float): Int {
        var k = 0
        for (j in 1 until SCRIPT_TIMES.size) if (t >= SCRIPT_TIMES[j]) k = j
        return k
    }

    // ---- The pose -------------------------------------------------------------------------------
    class Pose(
        val focus: Float,
        val caret: Float,
        /** The field's value and placeholder opacities. */
        val text: Float,
        val placeholder: Float,
        /** The suggestion card: open 0 ..1, rows it holds (fractional while it resizes). */
        val list: Float,
        val count: Float,
        /** 0 → 1 while the picked row flies into the field and the list folds. */
        val choose: Float,
        val chosen: Int,
        val rowY: FloatArray,
        val rowA: FloatArray,
        val camX: Float,
        val camY: Float,
        val camS: Float,
        /** Drop progress of the pin 0 ..1 (see dropY), and its opacity. */
        val drop: Float,
        val pin: Float,
        val ring1: Float,
        val ring2: Float,
        /** 0 → 1 while the map is held: the pin lifts off its spot. */
        val lift: Float,
        val callA: Float,
        val callB: Float,
        val fill0: Float,
        val fill1: Float,
        val fill2: Float,
        val ready: Float,
    ) {
        fun fill(i: Int) = when (i) { 0 -> fill0; 1 -> fill1; else -> fill2 }
    }

    class Dot(val target: Int, val alpha: Float, val scale: Float, val dx: Float, val dy: Float)

    fun overviewScale(mapW: Float) = mapW / OVERVIEW_SPAN

    /** World offset of the camera when the map is dragged by (dx, dy) dp at scale s. */
    fun dragToWorld(dx: Float, dy: Float, s: Float): FloatArray {
        val c = cos(-MAP_ROT)
        val n = sin(-MAP_ROT)
        return floatArrayOf(-(dx * c - dy * n) / s, -(dx * n + dy * c) / s)
    }

    fun autoPose(t: Float, s0: Float): Pose {
        val target = addresses[PICK]
        val c = if (t < CLEAR_AT + 0.4f) easeInOutCubic(seg(t, CHOOSE_AT, CHOOSE_AT + CHOOSE_LEN)) else 0f

        val rowY = FloatArray(N)
        val rowA = FloatArray(N)
        for (i in 0 until N) {
            val y = superpose(ROW_Y[i], t, ROW_MOVE, 0f)
            rowY[i] = y
            rowA[i] = superpose(ROW_A[i], t, ROW_MOVE * 0.8f, ROW_STAGGER * y)
        }

        // Camera: fly in, follow the ghost drag, fly back out.
        val fly = easeInOutCubic(seg(t, FLY_AT, FLY_AT + FLY_LEN))
        val back = easeInOutCubic(seg(t, BACK_AT, BACK_AT + BACK_LEN))
        val drag = easeInOutCubic(seg(t, DRAG_AT, DRAG_END))
        val off = dragToWorld(DRAG_DX * drag, DRAG_DY * drag, NEAR)
        val k = fly * (1 - back)
        val camX = OVERVIEW_X + (target.x + off[0] - OVERVIEW_X) * k
        val camY = OVERVIEW_Y + (target.y + off[1] - OVERVIEW_Y) * k
        val camS = s0 * (NEAR / s0).pow(k)

        val resetPin = seg(t, RESET_AT + 0.1f, RESET_AT + 0.4f)
        val typing = t - SCRIPT_TIMES[min(stepAt(t), QUERY.length)]
        val blink = if (typing < 0.5f) 1f else clamp01(0.5f + 1.6f * cos(2 * PI.toFloat() * (typing - 0.5f) / 1.06f))
        val textOut = if (t >= RESET_AT + 0.05f && t < CLEAR_AT + 0.15f) 1 - seg(t, RESET_AT + 0.05f, RESET_AT + 0.3f) else 1f

        return Pose(
            focus = easeOutCubic(seg(t, FOCUS_AT, FOCUS_AT + 0.2f)) * (1 - seg(t, CHOOSE_AT + 0.1f, CHOOSE_AT + 0.3f)),
            caret = seg(t, FOCUS_AT + 0.15f, FOCUS_AT + 0.25f) * (1 - seg(t, CHOOSE_AT, CHOOSE_AT + 0.05f)) * blink,
            text = (if (c > 0f && t < CLEAR_AT) seg(c, 0.45f, 0.95f) else 1f) * textOut,
            placeholder = if (t < CLEAR_AT) 1f else seg(t, CLEAR_AT + 0.1f, CLEAR_AT + 0.35f),
            list = if (t < CLEAR_AT) easeOutCubic(seg(t, TYPE_AT, TYPE_AT + 0.3f)) else 0f,
            count = superpose(COUNTS, t, ROW_MOVE, 0f),
            choose = c,
            chosen = PICK,
            rowY = rowY,
            rowA = rowA,
            camX = camX,
            camY = camY,
            camS = camS,
            drop = seg(t, DROP_AT, DROP_AT + DROP_LEN),
            pin = seg(t, DROP_AT, DROP_AT + 0.08f) * (1 - resetPin),
            ring1 = seg(t, RING_AT[0], RING_AT[0] + RING_LEN),
            ring2 = seg(t, RING_AT[1], RING_AT[1] + RING_LEN),
            lift = easeOutCubic(seg(t, DRAG_AT, DRAG_AT + 0.18f)) * (1 - easeOutBack(seg(t, DRAG_END, DRAG_END + 0.35f))),
            callA = easeOutCubic(seg(t, CALL_A_AT, CALL_A_AT + 0.3f)) * (1 - seg(t, DRAG_AT, DRAG_AT + 0.18f)),
            callB = easeOutCubic(seg(t, CALL_B_AT, CALL_B_AT + 0.3f)) * (1 - seg(t, RESET_AT, RESET_AT + 0.2f)),
            fill0 = easeOutCubic(seg(t, FILL_AT, FILL_AT + FILL_LEN)) * fillOut(t, 0),
            fill1 = easeOutCubic(seg(t, FILL_AT + FILL_GAP, FILL_AT + FILL_GAP + FILL_LEN)) * fillOut(t, 1),
            fill2 = easeOutCubic(seg(t, FILL_AT + FILL_GAP * 2, FILL_AT + FILL_GAP * 2 + FILL_LEN)) * fillOut(t, 2),
            ready = easeInOutCubic(seg(t, READY_AT, READY_AT + 0.4f)) * (1 - seg(t, RESET_AT, RESET_AT + 0.35f)),
        )
    }

    /** The ghost finger: taps the field, taps the first suggestion, then drags the map. */
    fun autoDot(t: Float): Dot {
        var target = 0
        var alpha = 0f
        var scale = 1f
        var dx = 0f
        var dy = 0f
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
        val d = seg(t, DRAG_IN, DRAG_IN + 0.2f) * (1 - seg(t, DRAG_END + 0.05f, DRAG_END + 0.3f))
        if (d > alpha) {
            alpha = d
            target = 2
            val p = easeInOutCubic(seg(t, DRAG_AT, DRAG_END))
            dx = DRAG_DX * p
            dy = DRAG_DY * p
            scale = 1 - 0.18f * seg(t, DRAG_AT - 0.12f, DRAG_AT) * (1 - seg(t, DRAG_END, DRAG_END + 0.12f))
        }
        return Dot(target, alpha, scale, dx, dy)
    }

    /** "1.1 km": the distance shown on a suggestion. */
    fun distanceKm(a: AddressLookupMapAddress): String =
        String.format(java.util.Locale.US, "%.1f km", hypot(a.x - OVERVIEW_X, a.y - OVERVIEW_Y) * 0.0042 + 0.6)
}
