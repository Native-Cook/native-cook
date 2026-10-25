package com.ngocdevv.nativecook.blocks.onboarding.jigsawstepswelcome

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/*
 Jigsaw Steps Welcome — the autoPlay timeline (a port of jigsawMotion.ts; LOOP = 10 s), pure
 functions of t.

   0.0 – 1.4   rest: twelve pieces scattered under the board, tilted up to ±26°.
   1.4 – 2.8   step 1, the corners: four pieces fly home one after another (0.2 s apart, 0.75 s
               each): lifted (scale up to 1.07, a 28 dp arc, the shadow growing and softening),
               ease-in-out across, the tilt unwinding with an overshoot, a 3 % squash as each
               lands, and a light running round its edges (0.35 s).
   3.2 – 4.6   step 2, the outer middles.          5.0 – 6.4   step 3, the middle row.
   6.4 – 7.2   the cut lines fade out and the photo is whole.
   7.0 – 8.2   the photo comes alive: a slow push-in (1 → 1.06) and a warm gleam.
   8.05 – 8.25 the whole photo hands back to the pieces, and 8.2 – 9.6 they scatter back to the
               pile (0.06 s apart, 0.7 s each, a lower arc).
   9.6 – 10    rest. The text above the button rolls to each step as it begins.
 */
internal object JigsawMotion {
    const val LOOP = 10f
    const val COLS = 4
    const val ROWS = 3
    const val COUNT = COLS * ROWS
    private val STEP_AT = floatArrayOf(1.4f, 3.2f, 5.0f)
    private const val FLIGHT = 0.75f
    private const val STAGGER = 0.2f
    private const val SCATTER_AT = 8.2f
    private const val SCATTER_GAP = 0.06f
    private const val SCATTER_LEN = 0.7f
    private const val ARC = 28f

    /** Which pieces each onboarding step snaps in: corners, then the outer middles, then the middle row. */
    val STEPS = arrayOf(intArrayOf(0, 3, 8, 11), intArrayOf(1, 2, 9, 10), intArrayOf(4, 5, 6, 7))

    fun clamp01(x: Float) = if (x < 0f) 0f else if (x > 1f) 1f else x
    fun seg(t: Float, a: Float, b: Float) = clamp01((t - a) / (b - a))
    fun smoothstep(a: Float, b: Float, x: Float): Float { val u = clamp01((x - a) / (b - a)); return u * u * (3f - 2f * u) }
    fun easeInOutCubic(u: Float) = if (u < 0.5f) 4f * u * u * u else 1f - (-2f * u + 2f).pow(3) / 2f
    fun easeOutBack(u: Float, c1: Float): Float { val c3 = c1 + 1f; return 1f + c3 * (u - 1f).pow(3) + c1 * (u - 1f).pow(2) }

    private val flight = Array(COUNT) { i ->
        var r = floatArrayOf(0f, 0f, 0f)
        for (k in STEPS.indices) {
            val j = STEPS[k].indexOf(i)
            if (j >= 0) r = floatArrayOf(k.toFloat(), j.toFloat(), STEP_AT[k] + j * STAGGER)
        }
        r
    }

    /** The order pieces were placed in (0…11), so the last placed leaves first. */
    private fun placeOrder(i: Int) = (flight[i][0] * 4 + flight[i][1]).toInt()

    /**
     * Piece i at loop time t into out[o..o+6]: x, y, rotation (rad), scale, lift 0…1, edge light
     * 0…1, groove 0…1. home is [x, y], scatter [x, y, rot].
     */
    fun piece(i: Int, t: Float, home: FloatArray, scatter: FloatArray, out: FloatArray, o: Int) {
        val start = flight[i][2]
        val u = seg(t, start, start + FLIGHT)
        val back = SCATTER_AT + (COUNT - 1 - placeOrder(i)) * SCATTER_GAP
        val v = seg(t, back, back + SCATTER_LEN)
        val e = easeInOutCubic(u) * (1f - easeInOutCubic(v))
        val pi = PI.toFloat()
        out[o] = scatter[0] + (home[0] - scatter[0]) * e
        out[o + 1] = scatter[1] + (home[1] - scatter[1]) * e - (ARC * sin(pi * u) + ARC * 0.7f * sin(pi * v))
        out[o + 2] = scatter[2] * (1f - easeOutBack(u, 1.7f)) + scatter[2] * easeInOutCubic(v)
        val lift = sin(pi * u) + sin(pi * v)
        val landed = start + FLIGHT
        val squash = if (t > landed) 0.03f * sin(pi * seg(t, landed, landed + 0.18f)) else 0f
        out[o + 3] = 1f + 0.07f * lift - squash
        out[o + 4] = lift
        out[o + 5] = if (t >= landed && t < landed + 0.35f) 1f - seg(t, landed, landed + 0.35f) else 0f
        out[o + 6] = if (u >= 1f && v <= 0f) 1f - smoothstep(6.4f, 7.2f, t) else 0f
    }

    /** The whole photo laid over the finished board: [opacity, push-in scale, gleam position 0…1]. */
    fun whole(t: Float, out: FloatArray) {
        out[0] = smoothstep(6.4f, 7.2f, t) * (1f - seg(t, 8.05f, 8.25f))
        out[1] = 1f + 0.06f * easeInOutCubic(seg(t, 7.0f, 8.2f))
        out[2] = seg(t, 7.1f, 8.0f)
    }

    /** The text's roll position: 0 before the first step, k while step k plays, back to 0 at the end. */
    fun textRoll(t: Float): Float {
        var r = 0f
        for (a in STEP_AT) r += smoothstep(a, a + 0.45f, t)
        return r - STEP_AT.size * smoothstep(SCATTER_AT, SCATTER_AT + 0.55f, t)
    }

    /** Which piece flies in overlay slot j (drawn above everything) at t, or -1. */
    fun slotPiece(j: Int, t: Float): Int {
        for (k in STEPS.indices) {
            val a = STEP_AT[k] + j * STAGGER
            if (t >= a && t < a + FLIGHT + 0.3f) return STEPS[k][j]
        }
        return -1
    }
}
