package com.ngocdevv.nativecook.blocks.media.lighttableloupe

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 Light Table Loupe — the data and the timeline (a port of LightTableLoupeModel.swift; the numbers are
 the same).

 Twelve 35 mm slides, each a transparency drawn as gradient art: a sky gradient, an optional sun (a disc
 with a glow three times its radius) and up to two ridges, each a band filled from
 y = base + amp · sin(2π f u + phase) (u = 0 … 1 across the window, y as a fraction of its height) down
 to the bottom. All numbers are fractions of the slide's window.

 autoPlay timeline (LOOP = 9 s); the loupe parks at the lower right of the table:
   0.0–0.6   rest: the loupe parked, "Drag the loupe over a slide".
   0.6–1.6   the loupe lifts and glides (ease in-out, on an arc bowed 18 % of the way sideways) to slide 4;
             the hint fades 0.6–0.75.
   1.6       it lands: the view under the glass pulls focus (blur 6 → 0 in 0.25 s) and the caption fades in.
   2.4–3.2   on to slide 6 (focus at 3.2). 4.0–4.8 on to slide 8 (focus at 4.8).
   5.0       a tap: the loupe dips 4 % (0.25 s) and a red grease-pencil circle is drawn round slide 8 in
             0.4 s, the slide shifting 3 dp and tilting 1.5° (ease-out-back 0.3 s); "1 MARKED" fades in.
   6.0–7.0   the loupe glides back to its place; the caption fades and the hint returns 7.0–7.25.
   7.6–8.2   the mark is wiped off (fades out) and the slide settles back.
   8.2–9.0   rest, the same frame as t = 0.
 All the while the table light breathes ±1.5 % (two cycles a loop) and a soft sheen crosses the glass once
 a loop (left to right, 1.6–4.6 s). The viewer projects the slide under the loupe with the caption's
 fade, its lamp flickering ±1 % eighteen times a loop.
 */

internal class LtlRidge(val base: Double, val amp: Double, val freq: Double, val phase: Double, val color: Long)

internal class LtlSlide(
    val title: String, val top: Long, val bottom: Long,
    val sunX: Double, val sunY: Double, val sunR: Double, val sunColor: Long, val glow: Double,
    val ridges: List<LtlRidge>,
)

internal class LtlMark(val draw: Double, val alpha: Double, val tilt: Double)

/** What the screen shows at one moment. Points are screen dp. */
internal class LtlFrame {
    var loupeX = 0.0
    var loupeY = 0.0
    var lift = 0.0
    var press = 0.0
    var focus = -1
    var focusAt = -10.0
    var blur = 0.0
    var caption = 0.0
    var hint = 1.0
    var markDraw = 0.0
    var markAlpha = 0.0
    var markTilt = 0.0
    var marks: MutableMap<Int, LtlMark> = mutableMapOf()
    var markCount = 0
    var counter = 0.0
    var pulse = 0.0
    var sheen = -1.0
    /** The viewer's lamp flicker, −1 … 1 (18 cycles a loop). */
    var flicker = 0.0
}

internal object LightTableLoupeModel {
    const val LOOP = 9.0
    const val AURORA = 6
    const val TAP_AT = 5.0
    const val MARKED = 7

    val slides = listOf(
        LtlSlide("Harbor at dusk", 0x1B2A6B, 0xF7814A, 0.70, 0.60, 0.10, 0xFFD27A, 0.55, listOf(LtlRidge(0.70, 0.03, 1.5, 0.4, 0x2A1E4A), LtlRidge(0.86, 0.01, 4.0, 1.0, 0x182040))),
        LtlSlide("Wheat in June", 0x3F9BEA, 0xCFE9FF, 0.20, 0.25, 0.08, 0xFFF6C8, 0.6, listOf(LtlRidge(0.62, 0.05, 0.8, 0.2, 0xE9B949), LtlRidge(0.80, 0.03, 1.6, 2.0, 0xC98A2B))),
        LtlSlide("Open sea", 0x0E4C92, 0x7FD1F0, 0.50, 0.30, 0.06, 0xFFFFFF, 0.5, listOf(LtlRidge(0.55, 0.006, 6.0, 0.0, 0x0A6FA8), LtlRidge(0.78, 0.02, 3.0, 1.2, 0x04507F))),
        LtlSlide("Night street", 0x0B0820, 0x40206A, 0.78, 0.22, 0.06, 0xF5E9C8, 0.45, listOf(LtlRidge(0.60, 0.08, 3.2, 0.6, 0x120C2E), LtlRidge(0.84, 0.03, 5.0, 2.2, 0xE0457B))),
        LtlSlide("Desert road", 0xF2A65A, 0xFFE1A8, 0.50, 0.50, 0.14, 0xFFF1D0, 0.6, listOf(LtlRidge(0.64, 0.04, 1.1, 1.4, 0xC4622D), LtlRidge(0.84, 0.02, 2.0, 0.3, 0x8A3A1C))),
        LtlSlide("Glacier lake", 0x5FB2E6, 0xE6F6FF, 0.25, 0.20, 0.05, 0xFFFFFF, 0.5, listOf(LtlRidge(0.48, 0.12, 1.8, 0.9, 0x6F8FB0), LtlRidge(0.72, 0.0, 1.0, 0.0, 0x2BB3B8))),
        LtlSlide("Northern lights", 0x031426, 0x0E3B4A, 0.40, 0.32, 0.0, 0x38F2A0, 0.0, listOf(LtlRidge(0.78, 0.05, 2.4, 0.7, 0x020A12))),
        LtlSlide("Pink beach", 0xFF8FA8, 0xFFE0C2, 0.70, 0.38, 0.09, 0xFFFFFF, 0.55, listOf(LtlRidge(0.64, 0.01, 3.0, 0.0, 0x4FC6D8), LtlRidge(0.80, 0.02, 1.2, 0.8, 0xFFD6B0))),
        LtlSlide("Alpine snow", 0x2F6FD6, 0xA9D2FF, 0.80, 0.20, 0.05, 0xFFFFFF, 0.5, listOf(LtlRidge(0.55, 0.15, 2.2, 0.3, 0xF4F8FF), LtlRidge(0.78, 0.06, 3.0, 1.9, 0x1F5A3A))),
        LtlSlide("Tulip rows", 0x6EC3FF, 0xE8F7FF, 0.15, 0.20, 0.06, 0xFFFBE0, 0.55, listOf(LtlRidge(0.60, 0.02, 1.0, 0.5, 0xFF3D6E), LtlRidge(0.78, 0.02, 1.3, 2.4, 0xFFB703))),
        LtlSlide("City at dusk", 0x2E1A55, 0xFF6F61, 0.30, 0.66, 0.11, 0xFFC27A, 0.6, listOf(LtlRidge(0.70, 0.10, 4.5, 0.2, 0x1D1236), LtlRidge(0.90, 0.01, 1.0, 0.0, 0x120A24))),
        LtlSlide("Fern forest", 0x0F3D2E, 0x7BC47F, 0.60, 0.15, 0.08, 0xE6FFB0, 0.7, listOf(LtlRidge(0.50, 0.10, 3.0, 1.1, 0x0B2E21), LtlRidge(0.78, 0.06, 5.0, 0.4, 0x145C3A))),
    )

    /** Stops: −1 is the parking place, otherwise a slide index. [from, to, start, end, bow]. */
    private class Glide(val from: Int, val to: Int, val start: Double, val end: Double, val bow: Double)
    private val glides = listOf(Glide(-1, 3, 0.6, 1.6, 0.18), Glide(3, 5, 2.4, 3.2, -0.18), Glide(5, 7, 4.0, 4.8, 0.18), Glide(7, -1, 6.0, 7.0, -0.18))

    fun clamp(x: Double) = min(1.0, max(0.0, x))
    fun smoothstep(a: Double, b: Double, x: Double): Double {
        val t = clamp((x - a) / (b - a))
        return t * t * (3 - 2 * t)
    }
    fun inOut(x: Double): Double {
        val t = clamp(x)
        return if (t < 0.5) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2
    }
    fun outBack(x: Double): Double {
        val t = clamp(x)
        val c1 = 1.70158
        val c3 = c1 + 1
        return 1 + c3 * (t - 1).pow(3) + c1 * (t - 1).pow(2)
    }

    /** Where along an arc from a to b, bowed sideways by [bow] of its length. */
    fun arc(ax: Double, ay: Double, bx: Double, by: Double, u: Double, bow: Double): DoubleArray {
        val e = inOut(u)
        val mx = (ax + bx) / 2
        val my = (ay + by) / 2
        val dx = bx - ax
        val dy = by - ay
        val cx = mx - dy * bow
        val cy = my + dx * bow
        return doubleArrayOf(
            (1 - e) * (1 - e) * ax + 2 * (1 - e) * e * cx + e * e * bx,
            (1 - e) * (1 - e) * ay + 2 * (1 - e) * e * cy + e * e * by,
        )
    }

    /** What plays at loop time t, given the parking place and the slides' centres. */
    fun frame(t: Double, parkX: Double, parkY: Double, center: (Int) -> DoubleArray): LtlFrame {
        val f = LtlFrame()
        fun at(stop: Int) = if (stop < 0) doubleArrayOf(parkX, parkY) else center(stop)
        f.loupeX = parkX; f.loupeY = parkY
        for ((i, g) in glides.withIndex()) {
            if (t < g.start) {
                val p = at(g.from); f.loupeX = p[0]; f.loupeY = p[1]
                break
            }
            if (t < g.end) {
                val u = (t - g.start) / (g.end - g.start)
                val a = at(g.from)
                val b = at(g.to)
                val p = arc(a[0], a[1], b[0], b[1], u, g.bow)
                f.loupeX = p[0]; f.loupeY = p[1]
                f.lift = sin(PI * u)
                break
            }
            val p = at(g.to); f.loupeX = p[0]; f.loupeY = p[1]
            if (g.to >= 0) {
                f.focus = g.to
                f.focusAt = g.end
                val leave = if (i + 1 < glides.size) glides[i + 1].start else LOOP
                f.caption = smoothstep(g.end, g.end + 0.25, t) * (1 - smoothstep(leave, leave + 0.15, t))
            } else {
                f.focus = -1
            }
        }
        if (f.focus < 0) f.focusAt = -10.0
        if (f.focus >= 0 && t >= f.focusAt) f.blur = 6 * (1 - smoothstep(f.focusAt, f.focusAt + 0.25, t))
        f.hint = 1 - smoothstep(0.6, 0.75, t) + smoothstep(7.0, 7.25, t)
        if (t >= TAP_AT && t < TAP_AT + 0.25) f.press = sin(PI * (t - TAP_AT) / 0.25)
        if (t >= TAP_AT) {
            f.markDraw = clamp((t - TAP_AT) / 0.4)
            f.markTilt = outBack((t - TAP_AT) / 0.3) * (1 - inOut((t - 7.6) / 0.6))
            f.markAlpha = 1 - smoothstep(7.6, 8.2, t)
        }
        f.counter = smoothstep(TAP_AT, TAP_AT + 0.3, t) * (1 - smoothstep(7.6, 8.2, t))
        f.pulse = sin(2 * PI * 2 * t / LOOP)
        f.sheen = if (t >= 1.6 && t < 4.6) (t - 1.6) / 3.0 else -1.0
        f.flicker = sin(2 * PI * 18 * t / LOOP)
        return f
    }

    /** Seeded noise: the same numbers as the Swift and React Native versions (32-bit integer hash). */
    fun hash(i: Int, seed: Int): Double {
        var x = i * 374_761_393 + seed * 668_265_263
        x = (x xor (x ushr 13)) * 1_274_126_177
        x = x xor (x ushr 16)
        return (x.toLong() and 0xFFFFFFFFL).toDouble() / 4_294_967_296.0
    }
}
