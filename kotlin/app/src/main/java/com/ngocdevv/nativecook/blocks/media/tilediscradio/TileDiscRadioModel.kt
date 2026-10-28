package com.ngocdevv.nativecook.blocks.media.tilediscradio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 Tile Disc Radio — stations, tile layouts, the change timeline and the driver (a port of
 TileDiscRadioModel.swift; no drawing here).

 A station is a palette and a 4 × 4 tile layout. A cell code is kind × 16 + mask: kind 0 empty, 1 full,
 2 top + bottom halves, 3 left + right halves, 4…7 one half only (top, right, bottom, left); the mask
 holds the tile's big ("petal") corners, TL 1, TR 2, BR 4, BL 8, clockwise.

 Layouts are written as they look on screen. The disc keeps turning a quarter turn per change, so after
 k quarter turns its local cell (c, r) shows screen cell rot^k(c, r), with its kind and corners turned
 too: a station's resting disc looks the same whatever k is, and the loop closes after three changes.
*/

internal class TdrStation(
    val name: String,
    val call: String,
    val freq: Double,
    val show: String,
    val vol: String,
    val listening: String,
    val frame: Long,
    val bgTile: Long,
    val line: Long,
    val tile: Long,
)

/** One tile of a cell, in cell units (0…1), with its big corners. */
internal class TdrTile(val x: Double, val y: Double, val w: Double, val h: Double, val mask: Int)

/** A station change (or, with dir 0, the disc springing back to rest after a short drag). */
internal data class TdrChange(
    val from: Int = 0,
    val to: Int = 0,
    /** Quarter turns of the disc before the change. */
    val kFrom: Int = 0,
    /** +1 next (clockwise), −1 previous, 0 none. */
    val dir: Int = 0,
    val t0: Double = -1e9,
    /** Disc angle in degrees when the change starts. */
    val a0: Double = 0.0,
    /** Leans back 4° before turning (a tap); a change thrown by a drag starts at once. */
    val antic: Boolean = true,
    /** The transport glyph that was pressed: −1 previous, +1 next, 0 none. */
    val press: Int = 0,
) {
    val kTo: Int get() = kFrom + dir
}

internal class TdrFrame(
    val ch: TdrChange = TdrChange(),
    /** Seconds since the change started (large once settled). */
    val ct: Double = 99.0,
    /** Disc angle in degrees. */
    val angle: Double = 0.0,
    val playing: Boolean = true,
    /** A clock for the live dot (loop time in autoPlay). */
    val clock: Double = 0.0,
    val reduced: Boolean = false,
)

internal object TdrModel {
    const val LOOP = 9.0
    /** Loop times at which autoPlay presses "next". */
    val SCRIPT = doubleArrayOf(1.5, 4.3, 7.1)
    /** A change has settled after this long; rows are still sliding until BUSY. */
    const val DONE = 1.8
    const val BUSY = 1.0
    const val IDLE_BEFORE_BACK = 2.0

    val STATIONS = listOf(
        TdrStation("Lumen Sounds", "KVRA", 91.7, "Night Shift with Ora Lind", "VOL.13", "2.4k",
            0x8A08FD, 0xFC57E1, 0xFC1E53, 0xFDB400),
        TdrStation("Field Notes", "KOAL", 98.3, "Low Tide with Remi Okafor", "VOL.14", "1.8k",
            0x007A42, 0x8CF21E, 0x253DF2, 0x1CC8FD),
        TdrStation("Ember Hours", "KWEN", 104.5, "Morning Drift with Ines Varga", "VOL.15", "3.1k",
            0xF2511B, 0xFFA98A, 0x2A1FD1, 0xFFF0D6),
    )

    /** Disc layouts, row by row as seen on screen. */
    val DISC_LAYOUTS = listOf(
        intArrayOf(
            0x00, 0x18, 0x13, 0x12,
            0x00, 0x19, 0x10, 0x36,
            0x00, 0x18, 0x14, 0x29,
            0x12, 0x2C, 0x16, 0x18,
        ),
        intArrayOf(
            0x18, 0x14, 0x13, 0x12,
            0x11, 0x10, 0x10, 0x16,
            0x6C, 0x68, 0x26, 0x6C,
            0x19, 0x10, 0x33, 0x14,
        ),
        intArrayOf(
            0x12, 0x19, 0x16, 0x18,
            0x36, 0x00, 0x13, 0x10,
            0x10, 0x56, 0x1C, 0x29,
            0x13, 0x18, 0x10, 0x16,
        ),
    )

    /** The background behind the disc (the same for every station; only its colour changes). */
    val BG_LAYOUT = intArrayOf(
        0x19, 0x13, 0x13, 0x16,
        0x18, 0x10, 0x10, 0x34,
        0x29, 0x10, 0x10, 0x16,
        0x19, 0x1C, 0x1C, 0x14,
    )

    // Tiles

    fun tiles(code: Int): List<TdrTile> {
        val m = code and 15
        return when (code shr 4) {
            1 -> listOf(TdrTile(0.0, 0.0, 1.0, 1.0, m))
            2 -> listOf(TdrTile(0.0, 0.0, 1.0, 0.5, m and 3), TdrTile(0.0, 0.5, 1.0, 0.5, m and 12))
            3 -> listOf(TdrTile(0.0, 0.0, 0.5, 1.0, m and 9), TdrTile(0.5, 0.0, 0.5, 1.0, m and 6))
            4 -> listOf(TdrTile(0.0, 0.0, 1.0, 0.5, m))
            5 -> listOf(TdrTile(0.5, 0.0, 0.5, 1.0, m))
            6 -> listOf(TdrTile(0.0, 0.5, 1.0, 0.5, m))
            7 -> listOf(TdrTile(0.0, 0.0, 0.5, 1.0, m))
            else -> emptyList()
        }
    }

    /** The code of local cell (c, r) of a station's disc after k quarter turns clockwise. */
    fun localCode(station: Int, k: Int, c: Int, r: Int): Int {
        val q = ((k % 4) + 4) % 4
        var sc = c
        var sr = r
        repeat(q) {
            val nc = 3 - sr
            sr = sc
            sc = nc
        }
        val code = DISC_LAYOUTS[station][sr * 4 + sc]
        var kind = code shr 4
        val m = code and 15
        var mask = 0
        for (b in 0 until 4) if (m and (1 shl ((b + q) % 4)) != 0) mask = mask or (1 shl b)
        if (kind == 2 || kind == 3) {
            if (q % 2 == 1) kind = if (kind == 2) 3 else 2
        } else if (kind >= 4) {
            // A half seen on screen at side s sits at local side s − q.
            kind = 4 + ((kind - 4 - q) % 4 + 4) % 4
        }
        return (kind shl 4) or mask
    }

    // Curves

    fun clamp01(x: Double) = min(max(x, 0.0), 1.0)
    fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))
    fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t
    fun easeOutCubic(x: Double) = 1 - (1 - x).pow(3)
    fun easeInCubic(x: Double) = x * x * x
    fun easeInOutCubic(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    fun easeInOut(x: Double) = 0.5 - 0.5 * cos(PI * x)

    /** A spring's remaining distance (1 → 0) from rest, released with no speed. */
    fun springDecay(t: Double, response: Double, damping: Double): Double {
        val w = 2 * PI / response
        val zw = damping * w
        val wd = w * sqrt(1 - damping * damping)
        return exp(-zw * t) * (cos(wd * t) + zw / wd * sin(wd * t))
    }

    // Timeline of a change (ct = seconds since it started)

    fun angle(ch: TdrChange, ct: Double): Double {
        val a1 = ch.kTo * 90.0
        if (ch.dir == 0) {
            if (ct >= DONE) return a1
            return a1 + (ch.a0 - a1) * springDecay(ct, 0.45, 0.8)
        }
        if (ch.antic) {
            val pre = ch.a0 - 4.0 * ch.dir
            if (ct < 0.12) return lerp(ch.a0, pre, easeOutCubic(max(0.0, ct) / 0.12))
            val t = ct - 0.12
            if (t >= DONE - 0.12) return a1
            return a1 + (pre - a1) * springDecay(t, 0.8, 0.7)
        }
        if (ct >= DONE) return a1
        return a1 + (ch.a0 - a1) * springDecay(max(0.0, ct), 0.8, 0.7)
    }

    /** How far local row r has run its conveyor (0 → 1). */
    fun rowP(ct: Double, r: Int): Double {
        val s = 0.10 + 0.06 * r
        return easeInOutCubic(seg(ct, s, s + 0.72))
    }

    fun gapMix(ct: Double) = easeInOut(seg(ct, 0.30, 0.70))
    fun pageMix(ct: Double) = easeInOut(seg(ct, 0.20, 0.80))
    fun freqMix(ct: Double) = easeInOutCubic(seg(ct, 0.10, 1.00))

    fun bgMix(ct: Double, i: Int, j: Int, dir: Int): Double {
        val s = 0.15 + 0.045 * ((if (dir >= 0) i else 3 - i) + j)
        return easeInOut(seg(ct, s, s + 0.30))
    }

    fun wordsOut(ct: Double) = 1 - easeInCubic(seg(ct, 0.05, 0.20))
    fun wordIn(ct: Double, n: Int): Double {
        val s = 0.70 + 0.05 * n
        return easeOutCubic(seg(ct, s, s + 0.30))
    }

    fun titleOut(ct: Double) = easeInCubic(seg(ct, 0.0, 0.25))
    fun titleIn(ct: Double) = easeOutCubic(seg(ct, 0.45, 0.85))

    /** Scale of the pressed transport glyph. */
    fun press(ct: Double): Double {
        if (ct < 0 || ct >= 0.4) return 1.0
        if (ct < 0.08) return 1 - 0.14 * easeOutCubic(ct / 0.08)
        return 0.86 + 0.14 * easeOutCubic((ct - 0.08) / 0.32)
    }

    /** Reduced motion: one 0.3 s crossfade for everything. */
    fun fade(ct: Double) = easeInOut(seg(ct, 0.0, 0.3))

    // autoPlay

    /** The scripted change in effect at loop time tau, and the time since it started. */
    fun autoChange(tau: Double): Pair<TdrChange, Double> {
        var i = -1
        for (n in SCRIPT.indices) if (tau >= SCRIPT[n]) i = n
        if (i < 0) return TdrChange() to 99.0
        val ch = TdrChange(i, (i + 1) % 3, i, 1, SCRIPT[i], i * 90.0, true, 1)
        return ch to tau - SCRIPT[i]
    }

    fun frame(ch: TdrChange, ct: Double, clock: Double, playing: Boolean, reduced: Boolean, angle: Double? = null) = TdrFrame(
        ch = ch,
        ct = min(ct, 99.0),
        angle = angle ?: if (reduced) ch.kTo * 90.0 else angle(ch, ct),
        playing = playing,
        clock = clock,
        reduced = reduced,
    )
}

/** Plays the script, takes over on touch and goes home 2 s after the last one. */
internal class TdrDriver {
    private var autoStart: Double? = null
    private var manual = false
    private var ch = TdrChange()
    private var dragging = false
    private var dragBase = 0.0
    private var dragAngle = 0.0
    private var armed = 0
    private var lastTouch = -1e9
    private var returning = false
    private var playing = true

    /** True once nothing moves after a touch (lets the frame loop stop without autoPlay). */
    fun settled(now: Double) = !dragging && now - ch.t0 >= TdrModel.DONE + 0.1 && now - lastTouch >= 0.5

    fun frame(now: Double, auto: Boolean, reduced: Boolean): TdrFrame {
        if (auto && manual && !dragging) {
            val ct = now - ch.t0
            if (returning) {
                if (ct >= TdrModel.DONE) {
                    manual = false
                    returning = false
                    autoStart = now
                }
            } else if (now >= lastTouch + TdrModel.IDLE_BEFORE_BACK && ct >= TdrModel.DONE) {
                playing = true
                if (ch.to == 0) {
                    manual = false
                    autoStart = now
                } else {
                    start(if (ch.to == 1) -1 else 1, now, TdrModel.angle(ch, ct), true, 0)
                    returning = true
                }
            }
        }
        if (auto && !manual) {
            val s = autoStart ?: now.also { autoStart = it }
            val tau = (now - s) % TdrModel.LOOP
            val (c, ct) = TdrModel.autoChange(tau)
            return TdrModel.frame(c, ct, tau, true, reduced)
        }
        if (dragging) {
            val rest = TdrChange(ch.to, ch.to, ch.kTo, 0, -1e9, dragAngle, false, 0)
            return TdrModel.frame(rest, 99.0, now, playing, reduced, dragAngle)
        }
        return TdrModel.frame(ch, now - ch.t0, now, playing, reduced)
    }

    /** Takes over from the loop where it is. */
    private fun capture(now: Double, auto: Boolean) {
        lastTouch = now
        returning = false
        if (manual) return
        manual = true
        val s = autoStart
        if (auto && s != null) {
            val tau = (now - s) % TdrModel.LOOP
            val (c, ct) = TdrModel.autoChange(tau)
            ch = c.copy(t0 = now - ct)
        }
    }

    private fun busy(now: Double) = ch.dir != 0 && now - ch.t0 < TdrModel.BUSY

    private fun start(dir: Int, now: Double, a0: Double, antic: Boolean, press: Int) {
        ch = TdrChange(ch.to, (ch.to + dir + 3) % 3, ch.kTo, dir, now, a0, antic, press)
    }

    /** −1 previous, +1 next, 0 play / pause. False when the disc is still busy. */
    fun tap(button: Int, now: Double, auto: Boolean, reduced: Boolean): Boolean {
        capture(now, auto)
        if (button == 0) {
            playing = !playing
            return true
        }
        if (busy(now)) return false
        start(button, now, TdrModel.angle(ch, now - ch.t0), !reduced, button)
        return true
    }

    fun beginDrag(now: Double, auto: Boolean): Boolean {
        capture(now, auto)
        if (busy(now)) return false
        dragging = true
        dragBase = TdrModel.angle(ch, now - ch.t0)
        // The disc is now at rest on ch.to; fold the change into a settled one.
        ch = TdrChange(ch.to, ch.to, ch.kTo, 0, -1e9, dragBase, false, 0)
        dragAngle = dragBase
        armed = 0
        return true
    }

    /** Turns the disc with the finger; returns true when it crosses the change threshold (a detent). */
    fun drag(dx: Double, radius: Double, now: Double): Boolean {
        lastTouch = now
        var deg = -dx / radius * 180 / PI
        val a = abs(deg)
        if (a > 60) deg = (60 + (a - 60) * 0.35) * (if (deg < 0) -1 else 1)
        dragAngle = dragBase + deg
        val off = dragAngle - ch.kTo * 90.0
        val side = if (off > 22) 1 else if (off < -22) -1 else 0
        val crossed = side != 0 && side != armed
        armed = side
        return crossed
    }

    /** Releases the disc: a change if it was turned or thrown far enough, else it springs back. */
    fun endDrag(vx: Double, now: Double): Boolean {
        if (!dragging) return false
        dragging = false
        lastTouch = now
        val off = dragAngle - ch.kTo * 90.0
        var dir = 0
        if (off > 22 || (vx < -600 && off > 0)) dir = 1
        else if (off < -22 || (vx > 600 && off < 0)) dir = -1
        if (dir != 0) {
            start(dir, now, dragAngle, false, 0)
            return true
        }
        ch = TdrChange(ch.to, ch.to, ch.kTo, 0, now, dragAngle, false, 0)
        return false
    }
}
