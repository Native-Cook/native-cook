package com.ngocdevv.nativecook.blocks.playground.marblingcomb

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// The paint floating on the size: a stack of closed outlines, one per drop, oldest first. A port,
// step for step, of the React Native `marblingSim.ts`; see there for the maths.
//
// Every operation is applied once, forward, to every outline's vertices (the marbling maps of Lu,
// Jaffer et al.), so nothing is stored or replayed:
//
// - A drop of radius r at c is circle-area preserving: every point p moves out to
//   c + (p − c)·√(1 + r² / |p − c|²). A spreading drop is the same map in increments of r².
// - A tine moving by v pushes every point by v·G·u^d, d being the point's distance from the tine's
//   path, u = 2^(−1/c) (Jaffer's tine line), fading behind the tine over `wake` dp.
//
// After each step every touched outline is refined: long edges split, points on straight runs
// dropped, points far off screen removed. An outline wholly off screen is deleted, and one that
// surrounds the whole screen becomes the ground colour. A vertex budget coarsens the refinement when
// the pattern grows very fine, so the cost of a frame stays bounded. Plain arithmetic on arrays with
// a fixed tick, scratch buffers reused between ticks. No Android here: it compiles on its own.

// Longest edge on screen, dp, and the coarsest it may grow to under the budget.
private const val SEGMENT = 6.0
private const val SEGMENT_MAX = 14.0
// A point on a straight run is dropped when it lies within this distance of the chord, dp.
private const val FLATNESS = 0.08
// Points closer than this to the last one kept are dropped, dp.
private const val MIN_GAP = 0.6
// Total vertices aimed for.
private const val BUDGET = 16000
// Hard limits, past the budget's coarsening: beyond them the oldest outline (the bottom of the
// stack) is folded into the ground, so neither the outlines nor their vertices grow without bound.
private const val HARD_BUDGET = 40000
private const val MAX_BLOTS = 160
// An edge is never split into more parts than this.
private const val MAX_SPLIT = 256
// Margin around the screen within which outlines are kept exact, dp.
private const val MARGIN = 48.0
// The tine's push is ignored past this many halvings, and past this many wake lengths behind it.
private const val REACH = 9.0
private const val WAKE_REACH = 4.0
private val LN2 = ln(2.0)

/** `Math.round` of JavaScript: halves round up. */
internal fun marbleRound(v: Double) = floor(v + 0.5)

/** One drop's outline (closed polygon, `n` points) and its colour, an index into the palette. */
internal class Blot(val id: Int, var xs: DoubleArray, var ys: DoubleArray, var n: Int, val color: Int) {
    /** Bumped every time the outline changes, so a drawing of it can be cached. */
    var rev = 0
    var moved = true
    /** Bounding box, refreshed whenever the outline is refined. */
    var minX = 0.0
    var minY = 0.0
    var maxX = 0.0
    var maxY = 0.0
}

/** A tine (stylus) or a row of tines (comb). */
internal class MarbleTool(
    /** Distance between tines along the comb's axis, dp (ignored for one tine). */
    var spacing: Double,
    /** Tines on each side of the middle one: 0 for a stylus. */
    var side: Double,
    /** The comb's axis (unit vector): its tines sit along it. */
    var axisX: Double,
    var axisY: Double,
    /** Distance over which the push halves, dp (Jaffer's c). */
    var sharpness: Double,
    /** Share of the tine's motion the paint right at the tine follows. */
    var gain: Double,
    /** Distance behind the tine over which its push fades, dp. */
    var wake: Double,
)

/** A drop still spreading: its centre, final radius and how far through its spread it is. */
private class Spread(
    val id: Int,
    val cx: Double,
    val cy: Double,
    val radius: Double,
    val color: Int,
    var age: Double,
    val time: Double,
    /** r² already applied. */
    var done: Double,
)

/** The spread of a drop from 0 to 1 over its time: fast at first, then slowing (cubic ease-out on its area). */
private fun spreadAt(t: Double): Double {
    val k = min(1.0, max(0.0, t))
    val r = 1 - k
    return 1 - r * r * r
}

/** The tray is `width` × `height` dp; the engine refreshes both every frame so it follows the layout. */
internal class Marble(var width: Double, var height: Double, ground: Int) {
    val blots = ArrayList<Blot>()
    private val spreads = ArrayList<Spread>()
    var ground = ground
        private set
    private var nextId = 1
    /** Bumped whenever any outline changes or one is added or removed. */
    var rev = 1
        private set
    /** Longest edge allowed on screen, dp: grows past SEGMENT when the budget is exceeded. */
    var segment = SEGMENT
        private set
    var vertices = 0
        private set

    // Scratch buffers, reused every tick.
    private val batchX = DoubleArray(64)
    private val batchY = DoubleArray(64)
    private val batchR2 = DoubleArray(64)
    private var batchN = 0
    private var outX = DoubleArray(1024)
    private var outY = DoubleArray(1024)

    /** Clears the tray to its ground colour. */
    fun clear(ground: Int) {
        blots.clear()
        spreads.clear()
        this.ground = ground
        segment = SEGMENT
        vertices = 0
        rev += 1
    }

    /** Drops paint at (x, y): it spreads to `radius` over `time` s. */
    fun drop(x: Double, y: Double, radius: Double, color: Int, time: Double) {
        spreads.add(Spread(nextId, x, y, radius, color, 0.0, max(time, 1e-3), 0.0))
        nextId += 1
    }

    /** The drop map for the drops in the batch, applied to each point in order. */
    private fun pushOutBatch() {
        val count = batchN
        for (blot in blots) {
            val xs = blot.xs
            val ys = blot.ys
            for (i in 0 until blot.n) {
                var x = xs[i]
                var y = ys[i]
                for (k in 0 until count) {
                    val dx = x - batchX[k]
                    val dy = y - batchY[k]
                    val d2 = dx * dx + dy * dy
                    if (d2 < 1e-9) continue
                    val f = sqrt(1 + batchR2[k] / d2)
                    x = batchX[k] + dx * f
                    y = batchY[k] + dy * f
                }
                xs[i] = x
                ys[i] = y
            }
            blot.moved = true
        }
    }

    private fun batchAdd(x: Double, y: Double, r2: Double) {
        // More drops than the batch holds in one tick: apply what is there first (never happens in practice).
        if (batchN == batchX.size) {
            pushOutBatch()
            batchN = 0
        }
        batchX[batchN] = x
        batchY[batchN] = y
        batchR2[batchN] = r2
        batchN += 1
    }

    /** Spreads every growing drop by one tick, all of them applied to each point in turn, oldest first. */
    private fun spreadDrops(dt: Double) {
        batchN = 0
        for (s in spreads) {
            s.age += dt
            val target = s.radius * s.radius * spreadAt(s.age / s.time)
            val r2 = target - s.done
            if (r2 <= 0) continue
            if (s.done == 0.0) {
                // A new drop: the outlines so far make room first, then its own outline appears at its first radius.
                if (batchN > 0) pushOutBatch()
                batchN = 0
                batchAdd(s.cx, s.cy, r2)
                pushOutBatch()
                batchN = 0
                val r = sqrt(r2)
                val n = max(40, ceil(PI * 2 * s.radius / SEGMENT).toInt())
                val xs = DoubleArray(n)
                val ys = DoubleArray(n)
                for (k in 0 until n) {
                    val a = (k.toDouble() / n) * PI * 2
                    xs[k] = s.cx + cos(a) * r
                    ys[k] = s.cy + sin(a) * r
                }
                val blot = Blot(s.id, xs, ys, n, s.color)
                blot.minX = s.cx - r
                blot.minY = s.cy - r
                blot.maxX = s.cx + r
                blot.maxY = s.cy + r
                blots.add(blot)
            } else {
                batchAdd(s.cx, s.cy, r2)
            }
            s.done = target
        }
        if (batchN > 0) pushOutBatch()
        batchN = 0
        spreads.removeAll { it.age >= it.time }
    }

    /** Moves a tool from (ax, ay) to (bx, by), dragging the paint around each of its tines. */
    fun moveTool(tool: MarbleTool, ax: Double, ay: Double, bx: Double, by: Double) {
        val vx = bx - ax
        val vy = by - ay
        val len = sqrt(vx * vx + vy * vy)
        if (len < 1e-4) return
        val mx = vx / len
        val my = vy / len
        val k = LN2 / tool.sharpness
        val reach = REACH * tool.sharpness
        val behind = len + tool.wake * WAKE_REACH
        val gx = vx * tool.gain
        val gy = vy * tool.gain
        val ax2 = tool.axisX
        val ay2 = tool.axisY
        val spacing = max(1.0, tool.spacing)
        val side = tool.side
        val invWake = 1 / tool.wake
        for (blot in blots) {
            // Skip an outline whose box lies wholly ahead of the tool's reach or wholly behind its wake
            // (and, for one tine, wholly to one side of it).
            var lo = Double.POSITIVE_INFINITY
            var hi = Double.NEGATIVE_INFINITY
            var left = Double.POSITIVE_INFINITY
            var right = Double.NEGATIVE_INFINITY
            for (c in 0 until 4) {
                val cx = (if (c and 1 != 0) blot.maxX else blot.minX) - bx
                val cy = (if (c and 2 != 0) blot.maxY else blot.minY) - by
                val along = cx * mx + cy * my
                val across = cx * my - cy * mx
                if (along < lo) lo = along
                if (along > hi) hi = along
                if (across < left) left = across
                if (across > right) right = across
            }
            if (lo > reach || hi < -behind) continue
            if (side == 0.0 && (left > reach || right < -reach)) continue
            val xs = blot.xs
            val ys = blot.ys
            var moved = false
            for (i in 0 until blot.n) {
                var rx = xs[i] - bx
                var ry = ys[i] - by
                var along = rx * mx + ry * my
                if (along > reach + spacing || along < -behind - spacing) continue
                if (side > 0) {
                    // The nearest tine along the comb's axis.
                    var j = marbleRound((rx * ax2 + ry * ay2) / spacing)
                    if (j > side) j = side else if (j < -side) j = -side
                    rx -= ax2 * j * spacing
                    ry -= ay2 * j * spacing
                    along = rx * mx + ry * my
                    if (along > reach || along < -behind) continue
                }
                var across = rx * my - ry * mx
                if (across < 0) across = -across
                if (across > reach) continue
                // Ahead of the tine the push falls off with distance, beside its path with the
                // distance across, and behind it also over the wake.
                val w = if (along > 0) {
                    exp(-k * sqrt(along * along + across * across))
                } else if (along >= -len) {
                    exp(-k * across)
                } else {
                    exp(-k * across + (along + len) * invWake)
                }
                if (w < 2e-4) continue
                xs[i] += gx * w
                ys[i] += gy * w
                moved = true
            }
            if (moved) blot.moved = true
        }
    }

    /** Which sides of the kept area a point lies outside of, as bits (left 1, right 2, top 4, bottom 8). */
    private fun outside(x: Double, y: Double): Int {
        var code = 0
        if (x < -MARGIN) code = code or 1 else if (x > width + MARGIN) code = code or 2
        if (y < -MARGIN) code = code or 4 else if (y > height + MARGIN) code = code or 8
        return code
    }

    /** Whether (x, y) lies inside the outline (even–odd rule). */
    private fun contains(blot: Blot, x: Double, y: Double): Boolean {
        val xs = blot.xs
        val ys = blot.ys
        var inside = false
        var j = blot.n - 1
        for (i in 0 until blot.n) {
            if ((ys[i] > y) != (ys[j] > y) && x < (xs[j] - xs[i]) * (y - ys[i]) / (ys[j] - ys[i]) + xs[i]) inside = !inside
            j = i
        }
        return inside
    }

    private fun push(count: Int, x: Double, y: Double): Int {
        if (count == outX.size) {
            outX = outX.copyOf(count * 2)
            outY = outY.copyOf(count * 2)
        }
        outX[count] = x
        outY[count] = y
        return count + 1
    }

    /** Splits long edges and drops redundant points. */
    private fun refine(blot: Blot) {
        val xs = blot.xs
        val ys = blot.ys
        val n = blot.n
        if (n < 3) return
        val seg = segment
        val flat = FLATNESS * (seg / SEGMENT)
        var out = 0
        var lastX = xs[0]
        var lastY = ys[0]
        var lastCode = outside(lastX, lastY)
        out = push(out, lastX, lastY)
        for (i in 1..n) {
            val closing = i == n
            val px = if (closing) xs[0] else xs[i]
            val py = if (closing) ys[0] else ys[i]
            val code = outside(px, py)
            if (!closing) {
                val nx = xs[(i + 1) % n]
                val ny = ys[(i + 1) % n]
                val nextCode = outside(nx, ny)
                // Outside the same side as the point before and after it: the chord stays outside too.
                if (lastCode and code and nextCode != 0) continue
                val cx = nx - lastX
                val cy = ny - lastY
                val chord = sqrt(cx * cx + cy * cy)
                val gx = px - lastX
                val gy = py - lastY
                val gap = sqrt(gx * gx + gy * gy)
                if (gap < MIN_GAP) continue
                // On a straight run, short enough to stand for: the point adds nothing.
                if (chord < seg && chord > 1e-6 && abs((px - lastX) * cy - (py - lastY) * cx) / chord < flat) continue
            }
            // Split the edge from the last point kept when it is long and may be on screen.
            if (lastCode and code == 0) {
                val ex = px - lastX
                val ey = py - lastY
                val len = sqrt(ex * ex + ey * ey)
                if (len > seg && len.isFinite()) {
                    val parts = min(MAX_SPLIT, ceil(len / seg).toInt())
                    for (s in 1 until parts) {
                        val t = s.toDouble() / parts
                        out = push(out, lastX + (px - lastX) * t, lastY + (py - lastY) * t)
                    }
                }
            }
            if (closing) break
            out = push(out, px, py)
            lastX = px
            lastY = py
            lastCode = code
        }
        // The old arrays become the next scratch buffers.
        blot.xs = outX
        blot.ys = outY
        blot.n = out
        outX = xs
        outY = ys
    }

    /** Refines what moved, deletes what left the screen and folds a screen-covering outline into the ground. */
    private fun tidy() {
        var total = 0
        var changed = false
        var b = blots.size - 1
        while (b >= 0) {
            val blot = blots[b]
            if (!blot.moved) {
                total += blot.n
                b -= 1
                continue
            }
            blot.moved = false
            blot.rev += 1
            changed = true
            refine(blot)
            val xs = blot.xs
            val ys = blot.ys
            var all = 15
            var any = 0
            var inView = false
            var minX = Double.POSITIVE_INFINITY
            var minY = Double.POSITIVE_INFINITY
            var maxX = Double.NEGATIVE_INFINITY
            var maxY = Double.NEGATIVE_INFINITY
            for (i in 0 until blot.n) {
                val x = xs[i]
                val y = ys[i]
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                val c = outside(x, y)
                all = all and c
                if (c == 0) inView = true
                any = any or c
            }
            blot.minX = minX
            blot.minY = minY
            blot.maxX = maxX
            blot.maxY = maxY
            if (!inView) {
                if (all == 0 && any != 0 && contains(blot, width / 2, height / 2)) {
                    // It covers the whole screen: it is the ground now, and nothing under it shows.
                    ground = blot.color
                    blots.subList(0, b + 1).clear()
                    break
                }
                blots.removeAt(b)
                b -= 1
                continue
            }
            total += blot.n
            b -= 1
        }
        // Too many outlines or vertices even at the coarsest edges: the bottom one becomes the ground.
        while (blots.size > 1 && (blots.size > MAX_BLOTS || total > HARD_BUDGET)) {
            val bottom = blots.removeAt(0)
            ground = bottom.color
            total -= bottom.n
            changed = true
        }
        if (changed) rev += 1
        vertices = total
        // Coarsen the outlines while the pattern is over budget; refine again once it is not.
        if (total > BUDGET) {
            segment = min(SEGMENT_MAX, segment * 1.08)
        } else if (total < BUDGET * 0.7) {
            segment = max(SEGMENT, segment / 1.02)
        }
    }

    /** One fixed tick: drops spread, then everything that moved is refined. Tools move through `moveTool`. */
    fun step(dt: Double) {
        if (spreads.isNotEmpty()) spreadDrops(dt)
        tidy()
    }
}

// ─── The self-playing script ─────────────────────────────────────────────────────────────────────
// One loop, LOOP s long, from a clean tray back to a clean tray: a sprinkle of drops in a loose
// grid, a few drops on drops for rings, a coarse comb down and back up (gel-git), a fine comb across
// (nonpareil), then a print. Positions are shares of the tray; radii shares of its width.

internal const val LOOP = 16.0
/** When the sheet is laid on. */
internal const val PRINT_AT = 11.2

/** A small, fast, seeded random in [0, 1): xorshift32 on `state[0]`. */
internal fun marbleSeeded(state: IntArray): Double {
    var x = state[0]
    x = x xor (x shl 13)
    x = x xor (x ushr 17)
    x = x xor (x shl 5)
    state[0] = x
    return (x.toLong() and 0xffffffffL).toDouble() / 4294967296.0
}

internal class ScriptDrop(val at: Double, val x: Double, val y: Double, val r: Double, val color: Int)

/** A comb pulled in a straight line from (x0, y0) to (x1, y1) between two times. */
internal class CombPass(
    val from: Double,
    val to: Double,
    val x0: Double,
    val y0: Double,
    val x1: Double,
    val y1: Double,
    /** The comb's axis is horizontal (tines side by side across the screen) or vertical. */
    val horizontal: Boolean,
    /** Tine spacing as a share of the width. */
    val spacing: Double,
    val sharpness: Double,
    val gain: Double,
    val wake: Double,
)

// Colours cycled by the sprinkle (indices into the palette; 0 is the ground).
private val SprinkleColors = intArrayOf(1, 2, 3, 4, 6, 5, 1, 3, 2, 7, 4, 1)
private const val GRID_COLS = 4
private const val GRID_ROWS = 6
private const val SPRINKLE_START = 0.35
private const val SPRINKLE_GAP = 0.12

internal val ScriptDrops: List<ScriptDrop> = run {
    val state = intArrayOf(0x5eed1234)
    val cells = IntArray(GRID_COLS * GRID_ROWS) { it }
    // A fixed shuffle, so the sprinkle lands in a lively order.
    for (i in cells.size - 1 downTo 1) {
        val j = floor(marbleSeeded(state) * (i + 1)).toInt()
        val t = cells[i]
        cells[i] = cells[j]
        cells[j] = t
    }
    val drops = ArrayList<ScriptDrop>()
    cells.forEachIndexed { k, cell ->
        val col = cell % GRID_COLS
        val row = cell / GRID_COLS
        val stagger = if (row % 2 == 0) 0.0 else 0.5
        val sx = marbleSeeded(state)
        val sy = marbleSeeded(state)
        val sr = marbleSeeded(state)
        drops.add(
            ScriptDrop(
                at = SPRINKLE_START + k * SPRINKLE_GAP,
                x = (col + 0.25 + stagger * 0.5 + sx * 0.3) / GRID_COLS,
                y = (row + 0.3 + sy * 0.4) / GRID_ROWS,
                r = 0.15 + sr * 0.03,
                color = SprinkleColors[k % SprinkleColors.size],
            ),
        )
    }
    // Drops on drops: rings at three spots.
    val rings = listOf(
        doubleArrayOf(0.3, 0.3, 3.0, 2.0),
        doubleArrayOf(0.72, 0.52, 1.0, 7.0),
        doubleArrayOf(0.38, 0.76, 6.0, 4.0),
    )
    rings.forEachIndexed { k, ring ->
        val at = 3.5 + k * 0.32
        drops.add(ScriptDrop(at, ring[0], ring[1], 0.085, ring[2].toInt()))
        drops.add(ScriptDrop(at + 0.16, ring[0], ring[1], 0.06, ring[3].toInt()))
    }
    drops
}

internal val ScriptPasses = listOf(
    // Gel-git: a coarse comb down the tray, then back up between its first lines.
    CombPass(4.9, 6.7, 0.5, -0.08, 0.5, 1.08, horizontal = true, spacing = 0.15, sharpness = 9.0, gain = 0.8, wake = 70.0),
    CombPass(6.95, 8.75, 0.575, 1.08, 0.575, -0.08, horizontal = true, spacing = 0.15, sharpness = 9.0, gain = 0.8, wake = 70.0),
    // Nonpareil: a fine comb across.
    CombPass(9.0, 10.7, -0.12, 0.5, 1.12, 0.5, horizontal = false, spacing = 0.06, sharpness = 6.0, gain = 0.45, wake = 26.0),
)

/** Ease in and out (cubic), for a comb that gathers speed and slows at the far edge. */
internal fun marbleEaseInOut(t: Double): Double {
    val k = min(1.0, max(0.0, t))
    return if (k < 0.5) 4 * k * k * k else 1 - (-2 * k + 2).pow(3) / 2
}

/** Whether a time `at` was passed going from `before` to `t` on a looping clock. */
internal fun marbleCrossed(before: Double, t: Double, at: Double) =
    (before < at && t >= at) || (t < before && (at > before || at <= t))
