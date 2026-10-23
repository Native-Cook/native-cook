package com.ngocdevv.nativecook.blocks.playground.truchetwavetiles

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** A two-colour palette, as 0–255 sRGB components. */
class TruchetPalette(val name: String, val ground: FloatArray, val ink: FloatArray)

internal val Palettes = listOf(
    TruchetPalette("Ink", floatArrayOf(16f, 26f, 46f), floatArrayOf(239f, 228f, 206f)),
    TruchetPalette("Kiln", floatArrayOf(190f, 80f, 46f), floatArrayOf(248f, 234f, 210f)),
    TruchetPalette("Moss", floatArrayOf(28f, 56f, 45f), floatArrayOf(222f, 196f, 122f)),
    TruchetPalette("Cobalt", floatArrayOf(240f, 234f, 222f), floatArrayOf(38f, 58f, 182f)),
)
internal val Styles = listOf("Arcs", "Diagonals", "Triangles")

// The grid: COLUMNS square tiles across, as many rows as cover the screen, centred.
internal const val COLUMNS = 8
// Line widths as a share of a tile: thick ribbons for arcs, thinner for diagonals, none once the
// triangles are filled in.
private const val ARC_WIDTH = 0.2f
private const val DIAGONAL_WIDTH = 0.13f
// A quarter circle as a cubic Bézier: control points this share of the radius along the tangents.
internal const val KAPPA = 0.5523f

// A flip turns a tile 90° on a spring: angular frequency OMEGA rad/s, damping ratio DAMPING,
// stepped at SUBSTEP s. Mid-turn a tile lifts by up to LIFT of its size, with a shadow.
private const val OMEGA = (2 * Math.PI / 0.62).toFloat()
private const val DAMPING = 0.52f
private const val SUBSTEP = 1f / 240f
internal const val LIFT = 0.12f
internal const val LIFT_SHADOW = 7f
// A wave front leaves its origin at WAVE_SPEED dp/s; each tile flips as the front reaches its
// centre. A faint ring marks the front for RING_LIFE s.
internal const val WAVE_SPEED = 520f
internal const val RING_LIFE = 1.6f
private const val MAX_WAVES = 6

// The style morphs on a spring of 1.1 s, the thumb on one of 0.42 s; a palette crossfades over
// PALETTE_TIME s on an ease-in-out.
private const val STYLE_DURATION = 1.1f
private const val STYLE_DAMPING = 0.92f
private const val THUMB_DURATION = 0.42f
private const val THUMB_DAMPING = 0.82f
private const val PALETTE_TIME = 1.2f
private val PaletteEase = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
// A touch pauses autoPlay for this long.
private const val USER_HOLD = 4f

// autoPlay, one LOOP in seconds: four waves (so every tile turns a full 360° and the pattern comes
// back), the styles from arcs through diagonals and triangles back to arcs, four palettes.
private const val LOOP = 16f
private val WavesAt = floatArrayOf(0.6f, 4.4f, 8.2f, 12.0f)
private val WaveFromX = floatArrayOf(0.3f, 0.78f, 0.22f, 0.7f)
private val WaveFromY = floatArrayOf(0.34f, 0.68f, 0.8f, 0.18f)
private val StyleAt = floatArrayOf(2.4f, 6.2f, 10.0f)
private val StyleTo = intArrayOf(1, 2, 0)
private val PaletteAt = floatArrayOf(3.3f, 7.1f, 10.9f, 14.6f)

/** The seeded generator the React Native version uses (mulberry32), so the opening pattern matches. */
internal class TruchetRandom(seed: Int) {
    private var s = seed

    fun next(): Double {
        s += 0x6d2b79f5
        var t = s
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + (t xor (t ushr 7)) * (t or 61))
        return ((t xor (t ushr 14)).toLong() and 0xffffffffL).toDouble() / 4294967296.0
    }
}

// The opening pattern: each tile turned 0–3 quarters, from a fixed seed.
private val Opening = TruchetRandom(7).let { r -> IntArray(512) { floor(r.next() * 4).toInt() } }

internal fun clamp01(v: Float) = if (v < 0f) 0f else if (v > 1f) 1f else v

private fun smooth(t: Float): Float {
    val x = clamp01(t)
    return x * x * x * (x * (x * 6 - 15) + 10)
}

/** The tile style at `s` (0 arcs, 1 diagonals, 2 triangles). */
internal class TileStyle(val reach: Float, val round: Float, val fill: Float, val width: Float)

internal fun styleAt(s: Float): TileStyle {
    if (s <= 1f) {
        val e = smooth(s)
        return TileStyle(0.5f + 0.5f * e, 1 - e, 0f, ARC_WIDTH + (DIAGONAL_WIDTH - ARC_WIDTH) * e)
    }
    val e = smooth(s - 1)
    return TileStyle(1f, 0f, e, DIAGONAL_WIDTH * (1 - e))
}

/**
 * Reanimated's `withSpring({ duration, dampingRatio })`: the stiffness is solved so the spring's
 * energy falls to the threshold after 1.5 × the duration, and each frame moves along the exact
 * solution. A new target keeps the current velocity. [timeTo] stands in for `withTiming`.
 */
internal class DurationSpring(var value: Float) {
    private var mode = 0 // 0 still, 1 spring, 2 timing
    private var target = value
    private var velocity = 0f
    private var zeta = 1f
    private var omega0 = 0f
    private var omega1 = 0f
    private var stiffness = 0f
    private var initialEnergy = 0f
    private var from = 0f
    private var time = 0f
    private var duration = 1f

    fun springTo(next: Float, durationS: Float, dampingRatio: Float) {
        val v = if (mode == 1) velocity else 0f
        val x0 = value - next
        target = next
        zeta = dampingRatio
        stiffness = stiffnessFor(x0, v, durationS, dampingRatio)
        omega0 = sqrt(stiffness)
        omega1 = if (zeta < 1f) omega0 * sqrt(1f - zeta * zeta) else 0f
        velocity = v
        initialEnergy = 0.5f * stiffness * x0 * x0 + 0.5f * v * v
        mode = if (initialEnergy > 0f) 1 else 0
        if (mode == 0) value = next
    }

    /** `withTiming` on Reanimated's default inOut(quad). */
    fun timeTo(next: Float, durationS: Float) {
        mode = 2
        from = value
        target = next
        time = 0f
        duration = durationS
        velocity = 0f
    }

    fun step(dt: Float) {
        when (mode) {
            1 -> {
                val x0 = value - target
                val v0 = velocity
                if (zeta < 1f) {
                    val s1 = sin(omega1 * dt)
                    val c1 = cos(omega1 * dt)
                    val envelope = exp(-zeta * omega0 * dt)
                    val frag = envelope * (s1 * ((v0 + zeta * omega0 * x0) / omega1) + x0 * c1)
                    value = target + frag
                    velocity = -zeta * omega0 * frag + envelope * (c1 * (v0 + zeta * omega0 * x0) - omega1 * x0 * s1)
                } else {
                    val envelope = exp(-omega0 * dt)
                    value = target + envelope * (x0 + (v0 + omega0 * x0) * dt)
                    velocity = envelope * -omega0 * (x0 + (v0 + omega0 * x0) * dt) + envelope * (v0 + omega0 * x0)
                }
                val now = 0.5f * stiffness * (target - value) * (target - value) + 0.5f * velocity * velocity
                if (now / initialEnergy <= ENERGY_THRESHOLD) {
                    value = target
                    velocity = 0f
                    mode = 0
                }
            }
            2 -> {
                time += dt
                val p = clamp01(time / duration)
                val e = if (p < 0.5f) 2 * p * p else 1 - (-2 * p + 2) * (-2 * p + 2) / 2
                value = from + (target - from) * e
                if (p >= 1f) mode = 0
            }
        }
    }

    companion object {
        const val ENERGY_THRESHOLD = 6e-9f

        fun stiffnessFor(x0: Float, v0: Float, durationS: Float, zeta: Float): Float {
            if (x0 == 0f && v0 == 0f) return 0f
            val settling = durationS * 1.5
            fun diff(k: Double): Double {
                val omega = sqrt(k) * zeta
                val a = x0 + (v0 + x0 * omega) * settling
                val e = exp(-omega * settling)
                val xt = a * e
                val vt = a * e * -omega + (v0 + x0 * omega) * e
                val e0 = 0.5 * k * x0 * x0 + 0.5 * v0 * v0
                val et = 0.5 * k * xt * xt + 0.5 * vt * vt
                return et / e0 - ENERGY_THRESHOLD
            }
            var lo = Math.ulp(1.0)
            var hi = 8e3
            val rising = diff(hi) >= diff(lo)
            var k = (lo + hi) / 2
            var left = 100
            while (abs(diff(k)) > ENERGY_THRESHOLD * 1e-3 && left > 0) {
                left -= 1
                if ((diff(k) < 0) == rising) lo = k else hi = k
                k = (lo + hi) / 2
            }
            return if (k.isFinite()) k.toFloat() else 170f
        }
    }
}

/** A value moving to a target over a fixed time on the palette's ease-in-out, like `withTiming`. */
internal class PaletteTiming {
    var value = 0f
        private set
    private var from = 0f
    private var to = 0f
    private var t = 1f

    fun timeTo(next: Float) {
        from = value
        to = next
        t = 0f
    }

    fun step(dt: Float) {
        if (t >= 1f) return
        t = min(1f, t + dt / PALETTE_TIME)
        value = from + (to - from) * PaletteEase.transform(t)
    }
}

internal class TruchetWave(val x: Float, val y: Float, val at: Float, val hit: BooleanArray)

/** The grid's state, stepped once a frame like the React Native frame callback. */
internal class TruchetMotion {
    var angle = FloatArray(0)
    var velocity = FloatArray(0)
    var turns = IntArray(0)
    var waves = ArrayList<TruchetWave>()
    var elapsed = 0f
    val morph = DurationSpring(0f)
    val thumb = DurationSpring(0f)
    val palette = PaletteTiming()
    var selected = 0
    var clock = 0f
    var hold = 0f

    var w = 0f
    var h = 0f
    var side = 1f
    var rows = 0
    var top = 0f
    val count get() = COLUMNS * rows

    // Dragging: the tile under the finger last time, and where the finger was.
    var lastTile = -1
    var lastX = 0f
    var lastY = 0f

    fun layout(width: Float, height: Float) {
        if (width == w && height == h) return
        w = width
        h = height
        // No grid until there is a real, finite size (0 before layout, infinite in an unbounded parent).
        if (!(width > 0f && height > 0f) || width.isInfinite() || height.isInfinite()) {
            side = 1f
            rows = 0
            top = 0f
            return
        }
        side = width / COLUMNS
        rows = ceil(height / side).toInt() + 1
        top = (height - rows * side) / 2
    }

    /** Starts a tile on its next quarter turn. */
    fun flip(i: Int, reducedMotion: Boolean) {
        if (i < 0 || i >= angle.size || i >= turns.size || i >= velocity.size) return
        if (reducedMotion) {
            // No turning: it snaps a quarter on, its turns kept within 0–3.
            turns[i] = (turns[i] + 1) % 4
            angle[i] = turns[i] * 90f
            velocity[i] = 0f
            return
        }
        // At most a full turn queued: rapid taps or a scribble over one tile never wind it up further.
        if (turns[i] * 90f - angle[i] >= 360f) return
        turns[i] += 1
    }

    fun sendWave(x: Float, y: Float) {
        while (waves.size > MAX_WAVES - 1) waves.removeAt(0)
        waves.add(TruchetWave(x, y, elapsed, BooleanArray(count)))
    }

    fun chooseStyle(i: Int, reducedMotion: Boolean) {
        thumb.springTo(i.toFloat(), THUMB_DURATION, THUMB_DAMPING)
        if (reducedMotion) morph.timeTo(i.toFloat(), 0.4f) else morph.springTo(i.toFloat(), STYLE_DURATION, STYLE_DAMPING)
        selected = i
    }

    fun nextPalette() = palette.timeTo(floor(palette.value + 0.5f) + 1)

    fun touched() {
        hold = USER_HOLD
    }

    fun tileAt(x: Float, y: Float): Int {
        val c = floor(x / side).toInt()
        val r = floor((y - top) / side).toInt()
        if (c < 0 || c >= COLUMNS || r < 0 || r >= rows) return -1
        return r * COLUMNS + c
    }

    /** Walks from the last point to this one in half-tile steps, so a fast stroke leaves no gaps. */
    fun paintTo(x: Float, y: Float, reducedMotion: Boolean) {
        val dx = x - lastX
        val dy = y - lastY
        val dist = hypot(dx, dy)
        if (!dist.isFinite() || side <= 0f) return
        val steps = max(1, ceil(dist / (side * 0.5f)).toInt())
        for (k in 1..steps) {
            val i = tileAt(lastX + dx * k / steps, lastY + dy * k / steps)
            if (i != lastTile) {
                lastTile = i
                flip(i, reducedMotion)
            }
        }
        lastX = x
        lastY = y
    }

    /** One frame; false while the grid was only just laid out. */
    fun step(dt: Float, auto: Boolean, reducedMotion: Boolean): Boolean {
        val n = count
        if (n == 0) return false
        if (angle.size != n) {
            // A fresh grid in the opening pattern.
            turns = IntArray(n) { Opening[it % Opening.size] }
            angle = FloatArray(n) { turns[it] * 90f }
            velocity = FloatArray(n)
            waves.clear()
            return false
        }
        val now = elapsed + dt
        elapsed = now
        morph.step(dt)
        thumb.step(dt)
        palette.step(dt)

        if (hold > 0f) {
            hold -= dt
            if (hold <= 0f) clock = 0f
        } else if (auto) {
            val before = clock
            val t = (before + dt) % LOOP
            clock = t
            fun passed(at: Float) = (before < at && t >= at) || (t < before && (at > before || at <= t))
            for (k in WavesAt.indices) if (passed(WavesAt[k])) sendWave(WaveFromX[k] * w, WaveFromY[k] * h)
            for (k in StyleAt.indices) if (passed(StyleAt[k])) chooseStyle(StyleTo[k], reducedMotion)
            for (k in PaletteAt.indices) if (passed(PaletteAt[k])) nextPalette()
        }

        // Wave fronts: every tile the front has reached since last frame starts its turn.
        if (waves.isNotEmpty()) {
            val far = hypot(w, h) + side
            val it = waves.iterator()
            while (it.hasNext()) {
                val wave = it.next()
                val reach = (now - wave.at) * WAVE_SPEED
                for (i in 0 until min(n, wave.hit.size)) {
                    if (wave.hit[i]) continue
                    val cx = (i % COLUMNS + 0.5f) * side
                    val cy = top + (i / COLUMNS + 0.5f) * side
                    if (hypot(cx - wave.x, cy - wave.y) <= reach) {
                        wave.hit[i] = true
                        flip(i, reducedMotion)
                    }
                }
                if (!(reach < far || now - wave.at < RING_LIFE)) it.remove()
            }
        }

        // Springs towards each tile's quarter turn; a settled tile has its turns folded back to 0–3.
        if (!reducedMotion) {
            val steps = max(1, ceil(dt / SUBSTEP).toInt())
            val hStep = dt / steps
            for (i in 0 until n) {
                val target = turns[i] * 90f
                var a = angle[i]
                var v = velocity[i]
                if (abs(target - a) < 0.01f && abs(v) < 0.05f) {
                    if (turns[i] >= 4) {
                        turns[i] -= 4
                        angle[i] = turns[i] * 90f
                    }
                    continue
                }
                repeat(steps) {
                    v += (-OMEGA * OMEGA * (a - target) - 2 * DAMPING * OMEGA * v) * hStep
                    a += v * hStep
                }
                if (abs(target - a) < 0.01f && abs(v) < 0.05f) {
                    a = target
                    v = 0f
                }
                angle[i] = a
                velocity[i] = v
            }
        }
        return true
    }
}
