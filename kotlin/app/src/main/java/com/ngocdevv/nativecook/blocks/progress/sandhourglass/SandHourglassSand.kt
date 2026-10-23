package com.ngocdevv.nativecook.blocks.progress.sandhourglass

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow

// The grid, in cells. Odd column count so the neck is centred on a column.
internal const val SAND_COLS = 77
internal const val SAND_ROWS = 151
private const val SAND_COUNT = SAND_COLS * SAND_ROWS
// Size of one cell, in dp, as drawn.
internal const val SAND_CELL = 2.2f
// The neck's and the bulbs' half-width, in cells from the middle column: the neck is one cell
// wide, so the sand runs through it as a single thin stream.
private const val NECK = 0.6
private const val BULB = 36.4
// The bulb profile: the straight neck runs for NECK_RUN of the half-height, the funnel widens to
// the bulb's widest point at WIDEST, and the shoulder narrows to SHOULDER of that at the cap.
private const val NECK_RUN = 0.03
private const val WIDEST = 0.6
private const val SHOULDER = 0.68
// Grains in the glass.
internal const val SAND_GRAINS = 2300
// Chance that a grain with both diagonals blocked slides two cells sideways: lowers the pile's slope.
private const val SLIDE_CHANCE = 0.32
// When gravity is this far (in eighths of a turn) off a grid direction, grains slide to that side.
private const val TILT_BIAS = 0.12
// Initial pile: rows of height lost per column away from the middle (a 34° cone).
private const val PILE_SLOPE = 0.68
// The neck's middle row, which the gate guards, and the rows around it counted as "in the neck".
internal const val SAND_MID_ROW = (SAND_ROWS - 1) / 2
private const val NECK_BAND = 6

// Grid directions, counter-clockwise from +x in a y-down frame: 0 = right, 2 = down, 4 = left, 6 = up.
private val DX = intArrayOf(1, 1, 0, -1, -1, -1, 0, 1)
private val DY = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)
// The same directions as steps in the flat grid index.
private val OFFSET = IntArray(8) { DY[it] * SAND_COLS + DX[it] }

/** Half-width of the inside of the glass, in cells, `t` = 0 at the neck … 1 at the caps. */
internal fun sandGlassHalfWidth(t: Double): Double {
    if (t < NECK_RUN) return NECK
    val u = min(1.0, (t - NECK_RUN) / (WIDEST - NECK_RUN))
    var s = 1 - (1 - u).pow(2.4)
    if (t > WIDEST) s = 1 - (1 - SHOULDER) * ((t - WIDEST) / (1 - WIDEST)).pow(2.2)
    return NECK + (BULB - NECK) * s
}

/**
 * The sand of the hourglass: a cellular grain grid that lives in the glass's own frame.
 *
 * Every cell is empty (0) or holds one grain, stored as its tint (1…255) so a grain keeps its
 * colour as it moves. Each pass moves every grain at most one cell along gravity, or slides it
 * diagonally when the cell below is taken, or — now and then — two cells sideways, which lays the
 * pile at about 34° instead of the grid's 45°. Gravity is the screen's "down" seen from the glass,
 * snapped to the nearest of eight grid directions, so turning the glass makes the sand slide and
 * pour the other way. A step-for-step port of the React Native simulation (same seeded xorshift32
 * in 32-bit wrapping maths, same visiting order), so the same seed gives the same grains.
 *
 * A gate at the neck sets how fast the glass runs: a grain may only enter the neck's middle row when
 * a token is free, and tokens come at the timer's rate (grains per second). Opened, the neck lets
 * through about 219 grains a second, all a one-cell neck will pass.
 */
internal class SandHourglassSand(seed: Int) {
    /** 0 = empty, otherwise the grain's tint. */
    val grid = ByteArray(SAND_COUNT)
    /** 1 inside the glass. */
    private val mask = ByteArray(SAND_COUNT)
    /** First and last column inside the glass on each row, first and last row in each column. */
    private val left = IntArray(SAND_ROWS) { SAND_COLS }
    private val right = IntArray(SAND_ROWS) { -1 }
    private val top = IntArray(SAND_COLS) { SAND_ROWS }
    private val bottom = IntArray(SAND_COLS) { -1 }
    /** Grains on each row and in each column, so empty ones are skipped. */
    private val rowCount = IntArray(SAND_ROWS)
    private val colCount = IntArray(SAND_COLS)
    /**
     * Lines (rows, or columns under sideways gravity) where something moved, or might have, in the
     * last pass (`dirty`) and in this one (`next`). A line is only visited when it or the line ahead
     * of it along gravity is dirty, so a settled pile costs nothing.
     */
    private val dirty = ByteArray(SAND_ROWS) { 1 }
    private val next = ByteArray(SAND_ROWS)
    private var byRow = true
    /** Rows whose pixels are already all clear. */
    private val clearRow = ByteArray(SAND_ROWS)
    /** ARGB, one pixel per cell, written by [paint] for the grain shader. */
    val pixels = IntArray(SAND_COUNT)
    /** Colour of each tint, RGB. */
    private val palette = IntArray(256 * 3)
    var seed: Int = if (seed == 0) 1 else seed
        private set
    var pass = 0
        private set
    /** Primary direction of gravity used by the last pass (index into DX/DY). */
    var dir = 2
        private set
    /** Grains in the glass's upper and lower bulbs (its own frame), counted by [paint]. */
    var upper = 0
        private set
    var lower = 0
        private set
    /** Grains in and near the neck (within NECK_BAND rows of its middle), counted by [paint]. */
    var neck = 0
        private set
    /** Grains per second the neck lets through; below 0 it is open. Tokens saved up, at most 2. */
    var gate = -1.0
    private var tokens = 0.0

    init {
        buildPalette()
        val mid = SAND_ROWS / 2.0
        val cx = (SAND_COLS - 1) / 2.0
        for (r in 0 until SAND_ROWS) {
            val w = sandGlassHalfWidth(abs(r + 0.5 - mid) / mid)
            for (c in 0 until SAND_COLS) {
                if (abs(c - cx) <= w) {
                    mask[r * SAND_COLS + c] = 1
                    if (c < left[r]) left[r] = c
                    if (c > right[r]) right[r] = c
                    if (r < top[c]) top[c] = r
                    if (r > bottom[c]) bottom[c] = r
                }
            }
        }
        // Fill the lower bulb from its floor up, highest in the middle: a cone of PILE_SLOPE.
        val cells = ArrayList<Int>()
        val keys = ArrayList<Double>()
        for (r in ceil(mid).toInt() until SAND_ROWS) {
            for (c in left[r]..right[r]) {
                cells.add(r * SAND_COLS + c)
                keys.add(SAND_ROWS - 1 - r + PILE_SLOPE * abs(c - cx) + random() * 0.9)
            }
        }
        val order = cells.indices.sortedBy { keys[it] }
        for (i in 0 until min(SAND_GRAINS, order.size)) {
            val index = cells[order[i]]
            grid[index] = (1 + floor(random() * 255).toInt()).toByte()
            rowCount[index / SAND_COLS] += 1
            colCount[index % SAND_COLS] += 1
        }
    }

    /** A small, fast, seeded random in [0, 1): xorshift32 on the seed. */
    private fun random(): Double {
        var x = seed
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        seed = x
        return (x.toLong() and 0xffffffffL) / 4294967296.0
    }

    /** The colour of every tint: warm sand from umber to pale gold, with dark and glassy flecks. */
    private fun buildPalette() {
        val deep = intArrayOf(150, 74, 38)
        val mid = intArrayOf(204, 118, 62)
        val pale = intArrayOf(238, 178, 112)
        // JavaScript's Math.round: halves go up.
        fun mix(a: Int, b: Int, t: Double) = floor(a + (b - a) * t + 0.5).toInt()
        for (v in 1 until 256) {
            val t = (v - 1) / 254.0
            for (k in 0 until 3) {
                palette[v * 3 + k] = when {
                    // A few dark grains, like flecks of mica.
                    v < 10 -> intArrayOf(92, 50, 32)[k]
                    // And a few clear ones that catch the light.
                    v > 248 -> intArrayOf(255, 236, 206)[k]
                    t < 0.5 -> mix(deep[k], mid[k], t * 2)
                    else -> mix(mid[k], pale[k], t * 2 - 1)
                }
            }
        }
    }

    /** Whether cell `i` is inside the glass and empty. Anything off the grid counts as taken. */
    private fun open(i: Int) = i in 0 until SAND_COUNT && mask[i].toInt() == 1 && grid[i].toInt() == 0

    /** Moves the grain at `from` to `to`, keeping the counts and waking both lines. */
    private fun move(from: Int, to: Int) {
        grid[to] = grid[from]
        grid[from] = 0
        val r = from / SAND_COLS
        val c = from - r * SAND_COLS
        val toRow = to / SAND_COLS
        val toCol = to - toRow * SAND_COLS
        rowCount[r] -= 1
        rowCount[toRow] += 1
        colCount[c] -= 1
        colCount[toCol] += 1
        if (byRow) {
            next[r] = 1
            next[toRow] = 1
        } else {
            next[c] = 1
            next[toCol] = 1
        }
    }

    /**
     * Whether a grain may move from `from` to `to`: only a move into the neck's middle row needs a
     * token, and a grain held back keeps its line awake so it tries again next pass.
     */
    private fun gateAllows(from: Int, to: Int): Boolean {
        if (gate < 0) return true
        val toRow = to / SAND_COLS
        val fromRow = from / SAND_COLS
        if (toRow != SAND_MID_ROW || fromRow == SAND_MID_ROW) return true
        if (tokens >= 1) {
            tokens -= 1
            return true
        }
        next[if (byRow) fromRow else from - fromRow * SAND_COLS] = 1
        return false
    }

    /** Saves up the neck's tokens for `dt` seconds at the gate's rate. */
    fun feedGate(dt: Double) {
        tokens = if (gate < 0) 0.0 else min(2.0, tokens + gate * dt)
    }

    /** One grain's turn, at cell index `i`: along gravity, else a diagonal (downhill side first), else a long slide. */
    private fun updateGrain(i: Int, k: Int, bias: Int) {
        val down = i + OFFSET[k]
        if (open(down)) {
            if (gateAllows(i, down)) move(i, down)
            return
        }
        // The two neighbours of the primary direction; `bias` > 0 means the clockwise one is downhill.
        val a = i + OFFSET[(k + 1) % 8]
        val b = i + OFFSET[(k + 7) % 8]
        val aFree = open(a)
        val bFree = open(b)
        if (aFree || bFree) {
            var clockwise = aFree
            if (aFree && bFree) clockwise = if (bias == 0) random() < 0.5 else bias > 0
            val to = if (clockwise) a else b
            if (gateAllows(i, to)) move(i, to)
            return
        }
        // Straight gravity only: slip two cells sideways and one down, over an empty cell beside it.
        if (k % 2 != 0) return
        // The sideways axis is gravity turned a quarter.
        val side = OFFSET[(k + 2) % 8]
        val rightFree = open(i + side)
        val leftFree = open(i - side)
        if (!leftFree && !rightFree) return
        if (random() >= SLIDE_CHANCE) {
            // It may slide on a later pass: keep its line awake.
            next[if (byRow) i / SAND_COLS else i % SAND_COLS] = 1
            return
        }
        val way = if (leftFree && rightFree) (if (random() < 0.5) 1 else -1) else if (rightFree) 1 else -1
        val to = i + 2 * way * side + OFFSET[k]
        if (open(to) && gateAllows(i, to)) move(i, to)
    }

    /**
     * One pass of the sand under gravity (gx, gy), given in the glass's frame. Cells are visited from
     * the far end of gravity backwards, so a grain that moved is never visited again in the same pass.
     * Rows (or columns) without a grain are skipped.
     */
    fun step(gx: Double, gy: Double) {
        val octant = atan2(gy, gx) / (Math.PI / 4)
        val nearest = floor(octant + 0.5).toInt()
        val off = octant - nearest
        val k = ((nearest % 8) + 8) % 8
        val bias = if (abs(off) > TILT_BIAS) (if (off > 0) 1 else -1) else 0
        val dx = DX[k]
        val dy = DY[k]
        val rowWise = dy != 0
        // A new direction of gravity can move anything.
        if (k != dir || rowWise != byRow) dirty.fill(1)
        dir = k
        byRow = rowWise
        pass += 1
        val alternate = if (pass % 2 == 0) 1 else -1
        if (rowWise) {
            // Rows from the far end of gravity; within a row, from the far end of its sideways part.
            val stepDir = if (dx != 0) -dx else alternate
            for (i in 0 until SAND_ROWS) {
                val r = if (dy > 0) SAND_ROWS - 1 - i else i
                if (rowCount[r] == 0) continue
                val ahead = r + dy
                val awake = dirty[r].toInt() == 1 ||
                    (ahead in 0 until SAND_ROWS && (dirty[ahead].toInt() == 1 || next[ahead].toInt() == 1))
                if (!awake) continue
                val lo = left[r]
                val hi = right[r]
                val base = r * SAND_COLS
                if (stepDir < 0) {
                    for (c in hi downTo lo) if (grid[base + c].toInt() != 0) updateGrain(base + c, k, bias)
                } else {
                    for (c in lo..hi) if (grid[base + c].toInt() != 0) updateGrain(base + c, k, bias)
                }
            }
        } else {
            // Sideways gravity: columns from the far end, rows alternating.
            for (i in 0 until SAND_COLS) {
                val c = if (dx > 0) SAND_COLS - 1 - i else i
                if (colCount[c] == 0) continue
                val ahead = c + dx
                val awake = dirty[c].toInt() == 1 ||
                    (ahead in 0 until SAND_COLS && (dirty[ahead].toInt() == 1 || next[ahead].toInt() == 1))
                if (!awake) continue
                val lo = top[c]
                val hi = bottom[c]
                if (alternate < 0) {
                    for (r in hi downTo lo) if (grid[r * SAND_COLS + c].toInt() != 0) updateGrain(r * SAND_COLS + c, k, bias)
                } else {
                    for (r in lo..hi) if (grid[r * SAND_COLS + c].toInt() != 0) updateGrain(r * SAND_COLS + c, k, bias)
                }
            }
        }
        for (i in 0 until SAND_ROWS) {
            dirty[i] = next[i]
            next[i] = 0
        }
    }

    /**
     * Writes the grid into [pixels] (one ARGB pixel per cell) and counts the grains in each bulb.
     * A grain with open space above it (against gravity) is lit, one buried on all four sides is
     * shaded, so the pile reads as a surface and the stream as loose grains.
     */
    fun paint() {
        val ux = -DX[dir]
        val uy = -DY[dir]
        val midRow = (SAND_ROWS - 1) / 2.0
        var up = 0
        var low = 0
        var inNeck = 0
        for (r in 0 until SAND_ROWS) {
            if (rowCount[r] == 0) {
                if (clearRow[r].toInt() == 1) continue
                clearRow[r] = 1
            } else {
                clearRow[r] = 0
            }
            for (c in left[r]..right[r]) {
                val i = r * SAND_COLS + c
                val v = grid[i].toInt() and 0xff
                if (v == 0) {
                    pixels[i] = 0
                    continue
                }
                if (r < midRow) up += 1 else if (r > midRow) low += 1
                if (abs(r - SAND_MID_ROW) <= NECK_BAND) inNeck += 1
                val ar = r + uy
                val ac = c + ux
                val above = if (ar in 0 until SAND_ROWS && ac in 0 until SAND_COLS) grid[ar * SAND_COLS + ac].toInt() else 0
                var shade = 1.0
                if (above == 0) {
                    shade = 1.14
                } else if (c > 0 && c < SAND_COLS - 1 && r > 0 && r < SAND_ROWS - 1 &&
                    grid[i - 1].toInt() != 0 && grid[i + 1].toInt() != 0 &&
                    grid[i - SAND_COLS].toInt() != 0 && grid[i + SAND_COLS].toInt() != 0
                ) {
                    shade = 0.9
                }
                val red = min(255.0, palette[v * 3] * shade).toInt()
                val green = min(255.0, palette[v * 3 + 1] * shade).toInt()
                val blue = min(255.0, palette[v * 3 + 2] * shade).toInt()
                pixels[i] = (0xff shl 24) or (red shl 16) or (green shl 8) or blue
            }
        }
        upper = up
        lower = low
        neck = inNeck
    }
}
