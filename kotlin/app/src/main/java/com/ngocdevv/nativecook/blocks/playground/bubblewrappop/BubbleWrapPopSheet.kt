package com.ngocdevv.nativecook.blocks.playground.bubblewrappop

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin

// The sheet of bubble wrap as plain state, stepped once per frame: each bubble's pressure and pop,
// the fingers, the tug of the sheet, the shake, and the swap to a fresh sheet. Units are dp (the
// React Native points) and seconds. Ported line for line from the React Native block's
// BubbleWrapPopSheet.ts.

// Bubbles: radius as a share of the column pitch; the rows sit on a hex grid.
private const val BUBBLE_SHARE = 0.4
private const val COLUMNS = 6
// The film runs this far past the outer bubbles.
private const val FILM_MARGIN = 18.0

// A press builds pressure from 0 to 1 over PRESS_TIME s and the bubble pops at 1. Let go early,
// the dome springs back (1/s², 1/s). A sliding finger pops what it crosses: a bubble it reaches
// within SLIDE_REACH of its radius is committed and pops SLIDE_TIME s later.
private const val PRESS_TIME = 0.46
private const val RELAX_K = 420.0
private const val RELAX_C = 16.0
private const val SLIDE_REACH = 0.85
private const val SLIDE_TIME = 0.045
private const val COMMIT = 0.72
private const val MOVE_SLOP = 8.0
// The air rings and the collapse play out over this long after a pop.
internal const val POP_LIFE = 0.7

// The sheet tugs with the finger, rubber-banded to TUG_MAX dp, and springs home on release.
private const val TUG_MAX = 46.0
private const val TUG_K = 210.0
private const val TUG_C = 19.0
// Each pop knocks the camera with SHAKE_KICK dp/s (half as much on a slide), on a stiff spring.
private const val SHAKE_KICK = 230.0
private const val SHAKE_K = 1100.0
private const val SHAKE_C = 24.0
private const val SHAKE_MAX = 5.0
// Once every bubble is gone the sheet waits SWAP_DELAY s, then slides out as a fresh one slides
// in over SWAP_TIME s.
private const val SWAP_DELAY = 0.55
private const val SWAP_TIME = 1.05
// The counter rolls toward the count on a spring.
private const val COUNT_K = 260.0
private const val COUNT_C = 30.0
// Pops closer together than this give one haptic.
private const val HAPTIC_GAP = 0.05

internal const val EVENT_POP = 1
internal const val EVENT_SWAP = 2

private class SheetFinger {
    var id = -1L
    var down = false
    var scripted = false
    var x = 0.0
    var y = 0.0
    var x0 = 0.0
    var y0 = 0.0
    var lastX = 0.0
    var lastY = 0.0
    /** 0 pressing a bubble, 1 sliding, 2 tugging the sheet. */
    var mode = 0
    var bubble = -1
    var moved = false
    var tugFromX = 0.0
    var tugFromY = 0.0
    var vx = 0.0
    var vy = 0.0
    var lastT = 0.0
}

private fun rubber(d: Double, max: Double) = sign(d) * max * (1 - exp(-abs(d) / max))

/** A full sheet laid out in the given rectangle. */
internal class BubbleSheet(
    val width: Double,
    /** The sheet's rectangle at rest. */
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
    val calm: Boolean,
) {
    var time = 0.0
    val radius: Double
    /** Bubble centres, relative to the sheet's top left. */
    val bx: DoubleArray
    val by: DoubleArray
    val rowOf: IntArray
    val count: Int

    init {
        val inner = right - left - FILM_MARGIN * 2
        val pitch = inner / (COLUMNS + 0.5)
        radius = pitch * BUBBLE_SHARE
        val rowPitch = pitch * 0.866
        val rows = max(1, floor((bottom - top - FILM_MARGIN * 2 - pitch) / rowPitch).toInt() + 1)
        // Centre the rows in the height they leave.
        val used = (rows - 1) * rowPitch + pitch
        val offY = (bottom - top - used) / 2 + pitch / 2
        count = rows * COLUMNS
        bx = DoubleArray(count) { FILM_MARGIN + pitch / 2 + (it % COLUMNS) * pitch + ((it / COLUMNS) % 2) * (pitch / 2) }
        by = DoubleArray(count) { offY + (it / COLUMNS) * rowPitch }
        rowOf = IntArray(count) { it / COLUMNS }
    }

    val pressure = DoubleArray(count)
    private val pressureV = DoubleArray(count)
    private val committed = BooleanArray(count)
    val popped = BooleanArray(count)
    /** Seconds since each bubble popped, or -1. */
    val popAge = DoubleArray(count) { -1.0 }
    /** Which side the finger was on when it popped, for the bulge. */
    val leanX = DoubleArray(count)
    val leanY = DoubleArray(count)
    var pops = 0
    var shown = 0.0
    private var shownV = 0.0
    var tugX = 0.0
    var tugY = 0.0
    private var tugVX = 0.0
    private var tugVY = 0.0
    var shakeX = 0.0
    var shakeY = 0.0
    private var shakeVX = 0.0
    private var shakeVY = 0.0
    /** Seconds since every bubble went, -1 while some remain. */
    private var emptyFor = -1.0
    /** 0 … 1 while a fresh sheet slides in. */
    var swap = 0.0
    private val fingers = arrayOf(SheetFinger(), SheetFinger())
    var events = 0
    var eventsByUser = false
    private var lastHaptic = -1.0

    /** The intact bubble under (x, y) on screen, or -1. */
    fun bubbleAt(x: Double, y: Double, reach: Double): Int {
        val lx = x - left - tugX
        val ly = y - top - tugY
        var best = -1
        var bestD = radius * reach
        for (i in 0 until count) {
            if (popped[i]) continue
            val d = hypot(bx[i] - lx, by[i] - ly)
            if (d < bestD) {
                bestD = d
                best = i
            }
        }
        return best
    }

    private fun fingerOf(id: Long) = fingers.firstOrNull { it.down && it.id == id }

    fun down(id: Long, x: Double, y: Double, scripted: Boolean) {
        if (swap > 0) return
        val f = fingers.firstOrNull { !it.down } ?: return
        f.id = id
        f.down = true
        f.scripted = scripted
        f.x = x
        f.y = y
        f.x0 = x
        f.y0 = y
        f.lastX = x
        f.lastY = y
        f.lastT = time
        f.vx = 0.0
        f.vy = 0.0
        f.moved = false
        // Among the bubbles it presses (or, on a popped one, waits to slide); on the film's edge or
        // off the sheet it tugs the film.
        val b = bubbleAt(x, y, 1.0)
        val lx = x - left - tugX
        val ly = y - top - tugY
        val amongBubbles = lx > FILM_MARGIN && lx < right - left - FILM_MARGIN && ly > FILM_MARGIN && ly < bottom - top - FILM_MARGIN
        f.bubble = b
        f.mode = if (b >= 0 || amongBubbles) 0 else 2
        f.tugFromX = tugX
        f.tugFromY = tugY
        if (b >= 0) {
            leanX[b] = (x - left - tugX - bx[b]) / radius
            leanY[b] = (y - top - tugY - by[b]) / radius
        }
    }

    fun move(id: Long, x: Double, y: Double) {
        val f = fingerOf(id) ?: return
        val px = f.x
        val py = f.y
        f.x = x
        f.y = y
        val dt = time - f.lastT
        if (dt > 0.004) {
            f.vx = f.vx * 0.35 + (x - f.lastX) / dt * 0.65
            f.vy = f.vy * 0.35 + (y - f.lastY) / dt * 0.65
            f.lastX = x
            f.lastY = y
            f.lastT = time
        }
        if (!f.moved && hypot(x - f.x0, y - f.y0) > MOVE_SLOP) {
            f.moved = true
            if (f.mode == 0) f.mode = 1
        }
        if (f.mode == 2) {
            tugX = rubber(f.tugFromX + x - f.x0, TUG_MAX)
            tugY = rubber(f.tugFromY + y - f.y0, TUG_MAX)
            tugVX = 0.0
            tugVY = 0.0
            return
        }
        if (f.mode == 1) {
            // Every bubble the finger passes over on its way from the last point, a few dp apart.
            val steps = max(1, ceil(hypot(x - px, y - py) / 6).toInt())
            for (k in 1..steps) {
                val b = bubbleAt(px + (x - px) * k / steps, py + (y - py) * k / steps, SLIDE_REACH)
                if (b >= 0 && !committed[b]) {
                    committed[b] = true
                    pressure[b] = max(pressure[b], COMMIT)
                    leanX[b] = sign(x - px) * 0.6
                    leanY[b] = sign(y - py) * 0.6
                }
            }
            f.bubble = -1
        }
    }

    fun up(id: Long) {
        val f = fingerOf(id) ?: return
        f.down = false
        if (f.mode == 2) {
            val still = time - f.lastT > 0.08
            tugVX = if (still) 0.0 else f.vx
            tugVY = if (still) 0.0 else f.vy
        }
        f.bubble = -1
    }

    fun userFingers() = fingers.count { it.down && !it.scripted }

    fun cancelScripted() {
        for (f in fingers) if (f.down && f.scripted) up(f.id)
    }

    private fun pop(b: Int, sliding: Boolean, byUser: Boolean) {
        popped[b] = true
        popAge[b] = 0.0
        pressure[b] = 1.0
        pops += 1
        if (!calm) {
            // A knock in a direction set by which bubble it was, so the shake is the same every time.
            val a = (b * 2.399963) % (PI * 2)
            val kick = SHAKE_KICK * (if (sliding) 0.5 else 1.0)
            shakeVX += cos(a) * kick
            shakeVY += sin(a) * kick
        }
        events = events or EVENT_POP
        if (byUser && time - lastHaptic > HAPTIC_GAP) {
            eventsByUser = true
            lastHaptic = time
        }
    }

    /** Advances everything by one frame. */
    fun step(frameDt: Double) {
        val dt = min(frameDt, 1.0 / 30)
        time += dt
        // Pressure: a held bubble builds toward its pop; a committed one runs out fast; the rest relax.
        val pressing = ArrayList<Int>(2)
        var pressedByUser = false
        for (f in fingers) {
            if (f.down && f.mode == 0 && f.bubble >= 0 && !popped[f.bubble]) {
                pressing.add(f.bubble)
                if (!f.scripted) pressedByUser = true
            }
        }
        var anyByUser = pressedByUser
        for (f in fingers) if (f.down && !f.scripted) anyByUser = true
        for (i in 0 until count) {
            if (popped[i]) {
                if (popAge[i] >= 0) popAge[i] += dt
                continue
            }
            if (committed[i]) {
                pressure[i] += dt / SLIDE_TIME * (1 - COMMIT)
                if (pressure[i] >= 1) pop(i, true, anyByUser)
                continue
            }
            if (i in pressing) {
                pressure[i] += dt / PRESS_TIME
                pressureV[i] = 0.0
                if (pressure[i] >= 1) pop(i, false, pressedByUser)
                continue
            }
            if (pressure[i] != 0.0 || pressureV[i] != 0.0) {
                pressureV[i] += (-pressure[i] * RELAX_K - pressureV[i] * RELAX_C) * dt
                pressure[i] += pressureV[i] * dt
                if (abs(pressure[i]) < 0.001 && abs(pressureV[i]) < 0.01) {
                    pressure[i] = 0.0
                    pressureV[i] = 0.0
                }
            }
        }
        // The tug springs home once no finger holds it.
        if (fingers.none { it.down && it.mode == 2 }) {
            tugVX += (-tugX * TUG_K - tugVX * TUG_C) * dt
            tugVY += (-tugY * TUG_K - tugVY * TUG_C) * dt
            tugX += tugVX * dt
            tugY += tugVY * dt
        }
        // Shake, in four substeps: it is stiff.
        repeat(4) {
            val h = dt / 4
            shakeVX += (-shakeX * SHAKE_K - shakeVX * SHAKE_C) * h
            shakeVY += (-shakeY * SHAKE_K - shakeVY * SHAKE_C) * h
            shakeX = max(-SHAKE_MAX, min(SHAKE_MAX, shakeX + shakeVX * h))
            shakeY = max(-SHAKE_MAX, min(SHAKE_MAX, shakeY + shakeVY * h))
        }
        // The counter.
        shownV += ((pops - shown) * COUNT_K - shownV * COUNT_C) * dt
        shown += shownV * dt
        // An empty sheet gives way to a fresh one.
        if (swap > 0) {
            swap += dt / SWAP_TIME
            if (swap >= 1) {
                swap = 0.0
                for (i in 0 until count) {
                    popped[i] = false
                    committed[i] = false
                    pressure[i] = 0.0
                    pressureV[i] = 0.0
                    popAge[i] = -1.0
                }
                emptyFor = -1.0
            }
        } else if (pops >= count) {
            emptyFor = if (emptyFor < 0) 0.0 else emptyFor + dt
            if (emptyFor >= SWAP_DELAY) {
                swap = 0.0001
                pops = 0
                events = events or EVENT_SWAP
                for (f in fingers) f.down = false
            }
        }
    }
}
