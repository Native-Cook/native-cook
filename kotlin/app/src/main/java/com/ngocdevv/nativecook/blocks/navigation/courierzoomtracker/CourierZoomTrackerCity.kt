package com.ngocdevv.nativecook.blocks.navigation.courierzoomtracker

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

// The invented city the courier drives through, in metres (x east, y south, like the screen): the street grid, the
// river, the parks, about 4,600 building footprints with heights, the courier's route (along the streets, corners
// rounded, on the right-hand lane) and the static map. Seeded, so every run and every port draws the same city.
// The static map is drawn once into a RenderNode (CourierZoomTrackerDraw), so a frame only replays it under a new
// camera matrix instead of recording thousands of shapes.
internal object CourierCity {
    const val BX = 120.0
    const val BY = 90.0
    const val I0 = -7
    const val I1 = 33
    const val J0 = -10
    const val J1 = 44
    const val NJT = J1 - J0
    const val CITY_X0 = I0 * BX
    const val CITY_Y0 = J0 * BY
    const val CITY_X1 = I1 * BX
    const val CITY_Y1 = J1 * BY

    /** The street grid carries on past the buildings into the outskirts. */
    private const val OI0 = -22
    private const val OI1 = 48
    private const val OJ0 = -24
    private const val OJ1 = 58

    const val MINOR = 14.0
    const val AVENUE = 24.0
    private val aveI = intArrayOf(7, 17)
    private val aveJ = intArrayOf(14, 33)
    private val bridges = intArrayOf(3, 7, 12, 17, 22)
    private const val RIVER_HALF = 72.0

    object Palette {
        const val LAND = 0xFFF2EFE9.toInt()
        const val PARK = 0xFFCFE5C2.toInt()
        const val PARK_PATH = 0xFFE4F0DA.toInt()
        const val WATER = 0xFFAFD3EC.toInt()
        const val WATER_EDGE = 0xFF9DC6E2.toInt()
        const val CASING = 0xFFDDD7CC.toInt()
        const val MINOR = 0xFFFFFFFF.toInt()
        const val AVE_CASING = 0xFFE6D6A5.toInt()
        const val AVENUE = 0xFFFFF0C4.toInt()
        const val AVE_LINE = 0xFFE9D79E.toInt()
        const val FOOTPRINT = 0xFFE5E0D7.toInt()
        const val GARDEN = 0xFFD9EBCB.toInt()
        const val TREE = 0xFFB8D9A4.toInt()
        const val TREE_DARK = 0xFFA5CC8F.toInt()
    }

    fun hash(n: Double): Double {
        val s = sin(n * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }

    private fun roundHalfUp(x: Double) = floor(x + 0.5)

    fun riverY(x: Double) = 2310 + 120 * sin(x / 700) + 55 * sin(x / 260 + 1)

    private fun isAveI(i: Int) = i in aveI
    private fun isAveJ(j: Int) = j in aveJ

    fun parkKind(i: Int, j: Int): Int {
        if (i in 2..5 && j in 30..35) return 1
        if ((i == 10 && j == 20) || (i == 19 && j == 40) || (i == 14 && j == 5) || (i == 22 && j == 27) || (i == 18 && j == 11) ||
            (i == 19 && j == 8)
        ) return 1
        val cx = (i + 0.5) * BX
        val cy = (j + 0.5) * BY
        val d = abs(cy - riverY(cx))
        if (d < RIVER_HALF + 50) return 2
        if (d < RIVER_HALF + 120) return 1
        return 0
    }

    // the courier's home stop

    const val HOME_X0 = 2313.0
    const val HOME_Y0 = 698.0
    const val HOME_X1 = 2325.0
    const val HOME_Y1 = 710.0
    private const val HOME_H = 10.0
    const val DOOR_X = 2319.0
    private const val STOP_X = 2319.0
    private const val STOP_Y = 716.5
    const val HUB_X = 843.5
    const val HUB_Y = 2700.0

    // buildings

    private fun isTerrace(i: Int, j: Int) = i in 18..20 && j in 6..8
    private const val ROW_DEPTH = 12.0

    private class DoubleList {
        var a = DoubleArray(1024)
        var n = 0
        fun add(v: Double) {
            if (n == a.size) a = a.copyOf(n * 2)
            a[n++] = v
        }
        fun toArray() = a.copyOf(n)
    }

    /** Footprints and heights, five numbers each (x0, y0, x1, y1, h), grouped by block. */
    val buildings: DoubleArray

    /** Where each block's buildings start in [buildings] (index / 5), plus the end. */
    val buildingStart: IntArray

    /** The customer's house. */
    val homeIndex: Int

    init {
        val b = DoubleList()
        val start = IntArray((I1 - I0) * (J1 - J0) + 1)
        var startN = 0
        var homeI = -1
        fun push(x0: Double, y0: Double, x1: Double, y1: Double, h: Double) {
            b.add(x0); b.add(y0); b.add(x1); b.add(y1); b.add(h)
        }
        fun heightAt(cx: Double, cy: Double, seed: Double): Double {
            val dt = hypot(cx - 1500, cy - 2050)
            val town = max(0.0, 1 - dt / 1100)
            val r = hash(seed)
            val base = 7 + 8 * r
            return roundHalfUp(base + town * town * (16 + 30 * hash(seed + 3.1)))
        }
        fun terraceRow(x0: Double, x1: Double, y0: Double, y1: Double, seed: Double) {
            var x = x0
            var k = 0.0
            while (x1 - x > 7) {
                var w = 10 + 3 * hash(seed + k * 1.7)
                if (x1 - x - w < 8) w = x1 - x
                push(x + 0.15, y0, x + w - 0.15, y1, roundHalfUp((8.5 + 3 * hash(seed + k * 2.3 + 0.5)) * 2) / 2)
                x += w
                k += 1
            }
        }
        for (i in I0 until I1) {
            for (j in J0 until J1) {
                start[startN++] = b.n / 5
                if (parkKind(i, j) > 0) continue
                fun hw(s: Boolean) = (if (s) AVENUE / 2 else MINOR / 2) + 3
                val x0 = i * BX + hw(isAveI(i))
                val x1 = (i + 1) * BX - hw(isAveI(i + 1))
                val y0 = j * BY + hw(isAveJ(j))
                val y1 = (j + 1) * BY - hw(isAveJ(j + 1))
                if (isTerrace(i, j)) {
                    val seed = (i * 53 + j * 11).toDouble()
                    terraceRow(x0, x1, y0, y0 + ROW_DEPTH, seed)
                    if (i == 19 && j == 7) {
                        terraceRow(x0, HOME_X0, y1 - ROW_DEPTH, y1, seed + 40)
                        homeI = b.n / 5
                        push(HOME_X0 + 0.15, HOME_Y0, HOME_X1 - 0.15, HOME_Y1, HOME_H)
                        terraceRow(HOME_X1, x1, y1 - ROW_DEPTH, y1, seed + 80)
                    } else {
                        terraceRow(x0, x1, y1 - ROW_DEPTH, y1, seed + 40)
                    }
                    continue
                }
                val seed = (i * 131 + j * 17).toDouble()
                val layout = hash(seed + 0.7)
                val cols = if (layout < 0.55) 2 else if (layout < 0.8) 3 else 1
                val rows = if (hash(seed + 1.9) < 0.7) 2 else 1
                val cw = (x1 - x0) / cols
                val ch = (y1 - y0) / rows
                for (a in 0 until cols) {
                    for (bb in 0 until rows) {
                        val k = seed * 7 + (a * 3 + bb * 5)
                        if (hash(k + 4.4) < 0.07) continue
                        val g = 1.5
                        val sx0 = x0 + a * cw + (if (a == 0) 0.0 else g) + 2 * hash(k + 1.1)
                        val sx1 = x0 + (a + 1) * cw - (if (a == cols - 1) 0.0 else g) - 2 * hash(k + 2.2)
                        val sy0 = y0 + bb * ch + (if (bb == 0) 0.0 else g) + (if (rows == 2 && bb == 1) 3 * hash(k + 5.5) else 0.0)
                        val sy1 = y0 + (bb + 1) * ch - (if (bb == rows - 1) 0.0 else g) - (if (rows == 2 && bb == 0) 3 * hash(k + 6.6) else 0.0)
                        push(sx0, sy0, sx1, sy1, heightAt((sx0 + sx1) / 2, (sy0 + sy1) / 2, k))
                    }
                }
            }
        }
        start[startN] = b.n / 5
        buildings = b.toArray()
        buildingStart = start
        homeIndex = homeI
    }

    // the route

    const val ROUTE_STEP = 3.0

    val routeX: DoubleArray
    val routeY: DoubleArray

    init {
        val way = arrayOf(
            doubleArrayOf(7 * BX, 30 * BY), doubleArrayOf(7 * BX, 14 * BY), doubleArrayOf(17 * BX, 14 * BY), doubleArrayOf(17 * BX, 10 * BY),
            doubleArrayOf(21 * BX, 10 * BY), doubleArrayOf(21 * BX, 8 * BY), doubleArrayOf(DOOR_X, 8 * BY),
        )
        val fillet = 26.0
        val lane = 3.5
        val px = ArrayList<Double>()
        val py = ArrayList<Double>()
        px.add(way[0][0]); py.add(way[0][1])
        for (k in 1 until way.size - 1) {
            val ax = way[k - 1][0]; val ay = way[k - 1][1]
            val bx = way[k][0]; val by = way[k][1]
            val cx = way[k + 1][0]; val cy = way[k + 1][1]
            val l1 = hypot(bx - ax, by - ay)
            val l2 = hypot(cx - bx, cy - by)
            val p0x = bx - (bx - ax) / l1 * fillet
            val p0y = by - (by - ay) / l1 * fillet
            val p2x = bx + (cx - bx) / l2 * fillet
            val p2y = by + (cy - by) / l2 * fillet
            for (s in 0..12) {
                val u = s / 12.0
                px.add((1 - u) * (1 - u) * p0x + 2 * (1 - u) * u * bx + u * u * p2x)
                py.add((1 - u) * (1 - u) * p0y + 2 * (1 - u) * u * by + u * u * p2y)
            }
        }
        px.add(way.last()[0]); py.add(way.last()[1])
        val cum = DoubleArray(px.size)
        for (k in 1 until px.size) cum[k] = cum[k - 1] + hypot(px[k] - px[k - 1], py[k] - py[k - 1])
        val total = cum.last()
        val n = floor(total / ROUTE_STEP).toInt()
        val cx = DoubleArray(n + 1)
        val cy = DoubleArray(n + 1)
        var sg = 0
        for (s in 0..n) {
            val d = if (s == n) total else s * ROUTE_STEP
            while (sg < cum.size - 2 && cum[sg + 1] < d) sg++
            val k = (d - cum[sg]) / max(cum[sg + 1] - cum[sg], 1e-6)
            cx[s] = px[sg] + (px[sg + 1] - px[sg]) * k
            cy[s] = py[sg] + (py[sg + 1] - py[sg]) * k
        }
        val x = DoubleArray(n + 1)
        val y = DoubleArray(n + 1)
        for (s in 0..n) {
            val a = max(s - 2, 0)
            val b = minOf(s + 2, n)
            val hx = cx[b] - cx[a]
            val hy = cy[b] - cy[a]
            val hl = if (hypot(hx, hy) == 0.0) 1.0 else hypot(hx, hy)
            x[s] = cx[s] + (-hy / hl) * lane
            y[s] = cy[s] + (hx / hl) * lane
        }
        x[n] = STOP_X
        y[n] = STOP_Y
        routeX = x
        routeY = y
    }

    val routeLen: Double get() = (routeX.size - 1) * ROUTE_STEP

    val routePath: Path = Path().apply {
        moveTo(routeX[0].toFloat(), routeY[0].toFloat())
        for (k in 1 until routeX.size) lineTo(routeX[k].toFloat(), routeY[k].toFloat())
    }

    val routeMeasure = PathMeasure(routePath, false)

    // the static map

    private fun streetInWater(x0: Double, y0: Double, x1: Double, y1: Double): Boolean {
        for (s in 0..4) {
            val x = x0 + (x1 - x0) * s / 4
            val y = y0 + (y1 - y0) * s / 4
            if (abs(y - riverY(x)) < RIVER_HALF + 4) return true
        }
        return false
    }

    /** The static map's shapes, in metres. Streets are merged into straight runs (x0, y0, x1, y1 centre lines). */
    private class MapGeometry {
        val parks = ArrayList<Float>()
        val minorRuns = ArrayList<Float>()
        val aveRuns = ArrayList<Float>()
        val aveDashes = ArrayList<Float>()
        val zebra = ArrayList<Float>()
        val gardens = ArrayList<Float>()
        val treesDark = ArrayList<Float>()
        val trees = ArrayList<Float>()
        val footprints = ArrayList<Float>()
        val parkPaths = Path()
        val river = Path()
    }

    private val geometry: MapGeometry by lazy { buildGeometry() }

    /** Builds the city's data off the main thread before the clock starts. */
    fun prepare() {
        geometry.footprints.size
    }

    private fun buildGeometry(): MapGeometry {
        val m = MapGeometry()
        fun rect(list: ArrayList<Float>, x0: Double, y0: Double, x1: Double, y1: Double) {
            list.add(x0.toFloat()); list.add(y0.toFloat()); list.add(x1.toFloat()); list.add(y1.toFloat())
        }
        for (i in I0 until I1) for (j in J0 until J1) {
            if (parkKind(i, j) > 0) rect(m.parks, i * BX - 1, j * BY - 1, (i + 1) * BX + 1, (j + 1) * BY + 1)
        }
        m.parkPaths.moveTo((2 * BX).toFloat(), (33.6 * BY).toFloat())
        m.parkPaths.cubicTo((3 * BX).toFloat(), (32 * BY).toFloat(), (4.6 * BX).toFloat(), (36 * BY).toFloat(), (6 * BX).toFloat(), (33 * BY).toFloat())
        m.parkPaths.moveTo((4.4 * BX).toFloat(), (30 * BY).toFloat())
        m.parkPaths.cubicTo((4 * BX).toFloat(), (32 * BY).toFloat(), (5.4 * BX).toFloat(), (34 * BY).toFloat(), (4.6 * BX).toFloat(), (36 * BY).toFloat())

        val xs = ArrayList<Double>()
        var x = OI0 * BX
        while (x <= OI1 * BX) { xs.add(x); x += 20 }
        m.river.moveTo(xs[0].toFloat(), (riverY(xs[0]) - RIVER_HALF).toFloat())
        for (k in 1 until xs.size) m.river.lineTo(xs[k].toFloat(), (riverY(xs[k]) - RIVER_HALF).toFloat())
        for (k in xs.size - 1 downTo 0) m.river.lineTo(xs[k].toFloat(), (riverY(xs[k]) + RIVER_HALF).toFloat())
        m.river.close()

        fun dashes(x0: Double, y0: Double, x1: Double, y1: Double) {
            // a [6, 7] dash restarting at each street segment, 0.7 m wide with butt caps
            val len = hypot(x1 - x0, y1 - y0)
            val ux = (x1 - x0) / len
            val uy = (y1 - y0) / len
            var o = 0.0
            while (o < len) {
                val e = minOf(o + 6, len)
                val ax = x0 + ux * o; val ay = y0 + uy * o
                val bx = x0 + ux * e; val by = y0 + uy * e
                rect(m.aveDashes, minOf(ax, bx) - abs(uy) * 0.35, minOf(ay, by) - abs(ux) * 0.35, maxOf(ax, bx) + abs(uy) * 0.35, maxOf(ay, by) + abs(ux) * 0.35)
                o += 13
            }
        }
        for (i in OI0..OI1) {
            val xi = i * BX
            val list = if (isAveI(i)) m.aveRuns else m.minorRuns
            var runStart = Int.MIN_VALUE
            for (j in OJ0..OJ1) {
                val open = j < OJ1 && !(streetInWater(xi, j * BY, xi, (j + 1) * BY) && i !in bridges)
                if (open) {
                    if (runStart == Int.MIN_VALUE) runStart = j
                    if (isAveI(i)) dashes(xi, j * BY, xi, (j + 1) * BY)
                } else if (runStart != Int.MIN_VALUE) {
                    rect(list, xi, runStart * BY, xi, j * BY)
                    runStart = Int.MIN_VALUE
                }
            }
        }
        for (j in OJ0..OJ1) {
            val yj = j * BY
            val list = if (isAveJ(j)) m.aveRuns else m.minorRuns
            var runStart = Int.MIN_VALUE
            for (i in OI0..OI1) {
                val open = i < OI1 && !streetInWater(i * BX, yj, (i + 1) * BX, yj)
                if (open) {
                    if (runStart == Int.MIN_VALUE) runStart = i
                    if (isAveJ(j)) dashes(i * BX, yj, (i + 1) * BX, yj)
                } else if (runStart != Int.MIN_VALUE) {
                    rect(list, runStart * BX, yj, i * BX, yj)
                    runStart = Int.MIN_VALUE
                }
            }
        }

        fun zebraAt(zx: Double, zy: Double, vertical: Boolean) {
            for (s in -3..3) {
                val o = s * 1.8 - 0.45
                if (vertical) rect(m.zebra, zx + o, zy - 2.5, zx + o + 0.9, zy + 2.5)
                else rect(m.zebra, zx - 2.5, zy + o, zx + 2.5, zy + o + 0.9)
            }
        }
        for ((i, j) in listOf(19 to 8, 20 to 8, 21 to 8, 21 to 9, 20 to 7, 19 to 9)) {
            val zx = i * BX
            val zy = j * BY
            zebraAt(zx - 12, zy, false)
            zebraAt(zx + 12, zy, false)
            zebraAt(zx, zy - 12, true)
            zebraAt(zx, zy + 12, true)
        }
        fun tree(tx: Double, ty: Double, r: Double, off: Double) {
            rect(m.treesDark, tx + off - r, ty + off - r, tx + off + r, ty + off + r)
            rect(m.trees, tx - r, ty - r, tx + r, ty + r)
        }
        for (i in 16..23) for (j in 5..11) {
            if (parkKind(i, j) != 0) continue
            val x0 = i * BX
            val y0 = j * BY
            var s = 14.0
            while (s < BX - 10) {
                val k = (i * 977 + j * 31) + s
                tree(x0 + s, y0 + MINOR / 2 + 1.3, 1.7 + 0.7 * hash(k), 0.6)
                s += 13
            }
        }
        for (i in I0 until I1) for (j in J0 until J1) {
            if (!(isTerrace(i, j) && parkKind(i, j) == 0)) continue
            val gx0 = i * BX + MINOR / 2 + 3
            val gy0 = j * BY + MINOR / 2 + 3 + ROW_DEPTH
            val gw = BX - MINOR - 6
            val gh = BY - MINOR - 6 - ROW_DEPTH * 2
            rect(m.gardens, gx0, gy0, gx0 + gw, gy0 + gh)
            for (k in 0 until 9) {
                val s = (i * 31 + j * 7 + k).toDouble()
                tree(gx0 + 5 + (gw - 10) * hash(s + 0.3), gy0 + 4 + (gh - 8) * hash(s + 0.9), 2 + 1.5 * hash(s + 1.7), 0.7)
            }
        }
        for (k in 0 until 26) {
            val fk = k.toDouble()
            tree(19 * BX + 14 + (BX - 28) * hash(fk * 3.1 + 0.2), 8 * BY + 14 + (BY - 28) * hash(fk * 5.3 + 0.7), 2.6 + 1.8 * hash(fk * 7.7), 0.8)
        }
        val b = buildings
        var k = 0
        while (k < b.size) {
            rect(m.footprints, b[k], b[k + 1], b[k + 2], b[k + 3])
            k += 5
        }
        return m
    }

    /** World box the static map covers (for the RenderNode's bounds). */
    const val MAP_L = OI0 * BX - 200
    const val MAP_T = OJ0 * BY - 200
    const val MAP_R = OI1 * BX + 200
    const val MAP_B = OJ1 * BY + 200

    /** Draws the whole static map in metres. */
    fun drawStatic(c: Canvas) {
        val m = geometry
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        fun rects(list: ArrayList<Float>, color: Int, grow: Float = 0f) {
            p.color = color
            p.style = Paint.Style.FILL
            var i = 0
            while (i < list.size) {
                c.drawRect(list[i] - grow, list[i + 1] - grow, list[i + 2] + grow, list[i + 3] + grow, p)
                i += 4
            }
        }
        fun ovals(list: ArrayList<Float>, color: Int) {
            p.color = color
            p.style = Paint.Style.FILL
            var i = 0
            while (i < list.size) {
                c.drawOval(list[i], list[i + 1], list[i + 2], list[i + 3], p)
                i += 4
            }
        }
        rects(m.parks, Palette.PARK)
        p.color = Palette.WATER
        c.drawOval((3 * BX + 10).toFloat(), (31 * BY + 20).toFloat(), (3 * BX + 180).toFloat(), (31 * BY + 130).toFloat(), p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 5f
        p.color = Palette.PARK_PATH
        c.drawPath(m.parkPaths, p)
        p.style = Paint.Style.FILL
        p.color = Palette.WATER
        c.drawPath(m.river, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f
        p.color = Palette.WATER_EDGE
        c.drawPath(m.river, p)
        // streets: square caps, so a run's rect reaches half a width past each end
        rects(m.minorRuns, Palette.CASING, ((MINOR + 2) / 2).toFloat())
        rects(m.minorRuns, Palette.MINOR, (MINOR / 2).toFloat())
        rects(m.aveRuns, Palette.AVE_CASING, ((AVENUE + 2.5) / 2).toFloat())
        rects(m.aveRuns, Palette.AVENUE, (AVENUE / 2).toFloat())
        rects(m.aveDashes, Palette.AVE_LINE)
        rects(m.zebra, 0xFFFFFFFF.toInt())
        rects(m.gardens, Palette.GARDEN)
        ovals(m.treesDark, Palette.TREE_DARK)
        ovals(m.trees, Palette.TREE)
        rects(m.footprints, Palette.FOOTPRINT)
    }
}
