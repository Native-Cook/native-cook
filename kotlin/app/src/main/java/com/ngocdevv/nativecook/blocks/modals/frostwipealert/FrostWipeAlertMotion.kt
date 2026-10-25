package com.ngocdevv.nativecook.blocks.modals.frostwipealert

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// The wipe trail and the droplets (frostMotion.ts), read by the shader. Points are kept in time
// order (oldest first), at most TRAIL of them: position, brush radius (negative = first point of
// a stroke), the frame-clock time it was laid, and when and how fast it refreezes. Its regrow
// progress q goes from -0.05 (clear) to 1.2 (frosted over). In autoPlay the whole trail is rebuilt
// each frame from the loop time; a touch keeps the trail as it is and adds to it.

/** The autoPlay period (s), equal to the manifest's recording.loop. */
const val LOOP = 9.0
internal const val FROST_TRAIL = 32
internal const val FROST_DROPS = 8
internal const val FROST_BRUSH = 44.0
internal const val FROST_STEP = 18.0
private const val HOLD = 1.2
private const val SPAN = 6.0
private const val WIPE_A = 1.4
private const val WIPE_B = 4.0
private const val SCRIPT_REGROW = 5.4
private const val SCRIPT_SPAN = 3.0
private const val ZIGS = 3.5
private const val DROP_LIFE = 2.2
private const val DROP_SAG = 0.3
private const val DROP_FADE = 0.4
private const val SCRIPT_DROPS = 6
private const val SCRIPT_DROP_AT = 4.2
private const val SCRIPT_DROP_LIFE = 1.8
internal const val FROST_IDLE_BEFORE_BACK = 2.0
private const val BACK_FOR = 0.9

internal const val FROST_AUTO = 0
internal const val FROST_LIVE = 1
internal const val FROST_BACK = 2

internal class FrostState(auto: Boolean) {
    var mode = if (auto) FROST_AUTO else FROST_LIVE
    var t = 0.0
    var now = 0.0
    var lastTouch = -100.0
    /** The trail, oldest first; `count` points in use. */
    val px = DoubleArray(FROST_TRAIL)
    val py = DoubleArray(FROST_TRAIL)
    val pr = DoubleArray(FROST_TRAIL)
    val pt = DoubleArray(FROST_TRAIL)
    val rs = DoubleArray(FROST_TRAIL)
    val sp = DoubleArray(FROST_TRAIL)
    var count = 0
    val dx = DoubleArray(FROST_DROPS)
    val dy = DoubleArray(FROST_DROPS)
    val db = DoubleArray(FROST_DROPS) { -100.0 }
    val dl = DoubleArray(FROST_DROPS) { DROP_LIFE }
    val ds = DoubleArray(FROST_DROPS)
    var dropNext = 0
    var wiping = false
    var pen = false
    var laid = 0
    var backAt = 0.0
    var backK = 0.0

    fun push(x: Double, y: Double, r: Double, t0: Double, regrowAt: Double, span: Double) {
        px[count] = x; py[count] = y; pr[count] = r; pt[count] = t0; rs[count] = regrowAt; sp[count] = span
        count += 1
    }

    fun shift() {
        for (a in arrayOf(px, py, pr, pt, rs, sp)) System.arraycopy(a, 1, a, 0, count - 1)
        count -= 1
    }

    /** False once a finger-driven pane has refrozen everywhere and no drop is running. */
    fun moving(): Boolean {
        if (mode != FROST_LIVE || wiping) return true
        for (i in 0 until count) if (regrow(i, -1.0) < 1.25) return true
        for (i in 0 until FROST_DROPS) if (now - db[i] < dl[i]) return true
        return false
    }
}

private fun seg(t: Double, a: Double, b: Double) = min(max((t - a) / (b - a), 0.0), 1.0)
private fun easeInOut(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

private fun hash(i: Double): Double {
    val s = sin(i * 12.9898) * 43758.5453
    return s - floor(s)
}

/** The scripted zig-zag over the alert card (box = x, y, w, h), at progress u (0…1). */
private fun zigzagX(u: Double, box: FloatArray): Double {
    val inset = 26.0
    val left = box[0] + inset
    val right = box[0] + box[2] - inset
    return (left + right) / 2 - (right - left) / 2 * cos(PI * ZIGS * u)
}

private fun zigzagY(u: Double, box: FloatArray) = box[1] + 40.0 + (box[3] - 80.0) * u

/** Rebuilds the trail and droplets for loop time t (frame-clock times, so a touch can continue). */
internal fun FrostState.scriptFrame(t: Double, box: FloatArray) {
    val start = now - t
    count = 0
    val step = (WIPE_B - WIPE_A) / (FROST_TRAIL - 1)
    for (k in 0 until FROST_TRAIL) {
        val te = WIPE_A + k * step
        if (te > t) break
        val u = seg(te, WIPE_A, WIPE_B)
        val grow = min((t - te) / 0.12, 1.0)
        push(zigzagX(u, box), zigzagY(u, box), (if (k == 0) -1 else 1) * FROST_BRUSH * (0.4 + 0.6 * grow), start + te, start + SCRIPT_REGROW, SCRIPT_SPAN)
    }
    for (i in 0 until FROST_DROPS) {
        db[i] = -100.0
        if (i >= SCRIPT_DROPS) continue
        val k = floor(hash((i * 3 + 1).toDouble()) * (FROST_TRAIL - 4)) + 2
        val u = k / (FROST_TRAIL - 1)
        dx[i] = zigzagX(u, box) + (hash((i * 5 + 2).toDouble()) - 0.5) * FROST_BRUSH
        dy[i] = zigzagY(u, box) + FROST_BRUSH * 0.62
        db[i] = start + SCRIPT_DROP_AT + i * 0.11
        dl[i] = SCRIPT_DROP_LIFE - i * 0.11
        ds[i] = 5.5 + 2.5 * hash((i * 7 + 3).toDouble())
    }
}

/** Regrow progress of point i now, raised to at least `floor` while easing back. */
internal fun FrostState.regrow(i: Int, floor: Double): Double {
    val q = (now - rs[i]) / sp[i] * 1.25 - 0.05
    return max(q, floor)
}

/** A droplet's head x, y, trail length and radius now, into out[0..3]; radius 0 when gone. */
internal fun FrostState.dropAt(i: Int, fade: Double, out: FloatArray, at: Int) {
    val age = now - db[i]
    val life = dl[i]
    if (age < 0 || age >= life) {
        out[at] = 0f; out[at + 1] = 0f; out[at + 2] = 0f; out[at + 3] = 0f
        return
    }
    val run = max(age - DROP_SAG, 0.0)
    val fall = 95 * run.pow(1.6)
    val x = dx[i] + 1.6 * sin(run * 6 + i)
    val size = ds[i] * min(age / 0.15, 1.0) * (1 - seg(age, life - DROP_FADE, life)) * fade
    out[at] = x.toFloat(); out[at + 1] = (dy[i] + fall).toFloat(); out[at + 2] = fall.toFloat(); out[at + 3] = size.toFloat()
}

/** Lays a point under the finger. */
internal fun FrostState.lay(x: Double, y: Double) {
    if (count >= FROST_TRAIL) {
        shift()
        // The new oldest point no longer has the one before it.
        if (count > 0) pr[0] = -abs(pr[0])
    }
    push(x, y, (if (pen) 1 else -1) * FROST_BRUSH, now, now + HOLD, SPAN)
    pen = true
    laid += 1
    // Now and then a drop gathers at the lower edge of the wipe.
    if (hash(laid * 1.37) < 0.22) {
        val i = dropNext
        dropNext = (i + 1) % FROST_DROPS
        dx[i] = x + (hash(laid * 2.1) - 0.5) * FROST_BRUSH * 0.8
        dy[i] = y + FROST_BRUSH * 0.62
        db[i] = now + 0.25
        dl[i] = DROP_LIFE
        ds[i] = 5.5 + 2.5 * hash(laid * 3.3)
    }
}

internal fun FrostState.beginBack() {
    mode = FROST_BACK
    backAt = now
    backK = 0.0
    wiping = false
}

/** Easing back to the fully frosted rest; true when done. */
internal fun FrostState.backFrame(): Boolean {
    backK = easeInOut(min((now - backAt) / BACK_FOR, 1.0))
    if (backK >= 1) {
        count = 0
        db.fill(-100.0)
        backK = 0.0
        return true
    }
    return false
}

/** How clear the pane is at (x, y), as the shader sees it (without the noise). */
internal fun FrostState.clearAt(x: Double, y: Double): Double {
    var best = 0.0
    for (i in 0 until count) {
        val r = abs(pr[i])
        var d = hypot(x - px[i], y - py[i])
        if (i > 0 && pr[i] > 0) {
            val ax = px[i - 1]
            val ay = py[i - 1]
            val bx = px[i] - ax
            val by = py[i] - ay
            val h = min(max(((x - ax) * bx + (y - ay) * by) / max(bx * bx + by * by, 0.0001), 0.0), 1.0)
            d = min(d, hypot(x - ax - bx * h, y - ay - by * h))
        }
        val cover = 1 - min(max((d - r * 0.5) / (r * 0.5), 0.0), 1.0)
        if (cover <= 0) continue
        val q = regrow(i, if (mode == FROST_BACK) backK * 1.25 else -1.0)
        if (cover * 0.9 > q) best = max(best, cover)
    }
    return best
}
