package com.ngocdevv.nativecook.blocks.splash.sketchcoffeesplash

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * One hand-drawn stroke: points a Catmull-Rom curve passes through, drawn as an ink ribbon.
 * [fill]: 0 none, 1 filled with the ground (hides what is under it), 2 filled with ink.
 */
internal class SketchStroke(
    val seed: Int,
    val width: Float,
    val closed: Boolean,
    val fill: Int,
    val jitter: Float,
    val points: FloatArray,
)

/** A stroke turned into paths for one boil variant: what to fill under it, and the ribbon itself. */
internal class SketchInk(val fill: Path?, val fillKind: Int, val line: Path?)

/**
 * Turns strokes into uneven, tapered ink ribbons that wobble a little differently in each variant.
 * Everything here is deterministic, so every loop draws the same lines (same maths as the Swift version).
 */
internal object SketchCoffeeSplashSketch {
    const val VARIANTS = 4

    fun hash(n: Double): Double {
        val v = sin(n * 12.9898) * 43758.5453
        return v - floor(v)
    }

    /** Points (x, y pairs) along a Catmull-Rom curve through [c], about [step] apart. */
    fun spline(c: DoubleArray, closed: Boolean, step: Double = 2.0): DoubleArray {
        val n = c.size / 2
        if (n < 2) return c
        val out = ArrayList<Double>()
        val segs = if (closed) n else n - 1
        for (i in 0 until segs) {
            val i0 = if (closed) (i - 1 + n) % n else max(i - 1, 0)
            val i1 = i % n
            val i2 = (i + 1) % n
            val i3 = if (closed) (i + 2) % n else min(i + 2, n - 1)
            val x0 = c[2 * i0]; val y0 = c[2 * i0 + 1]
            val x1 = c[2 * i1]; val y1 = c[2 * i1 + 1]
            val x2 = c[2 * i2]; val y2 = c[2 * i2 + 1]
            val x3 = c[2 * i3]; val y3 = c[2 * i3 + 1]
            val d = hypot(x2 - x1, y2 - y1)
            val k = max(2, ceil(d / step).toInt())
            for (j in 0 until k) {
                val u = j.toDouble() / k
                val u2 = u * u
                val u3 = u2 * u
                out.add(0.5 * (2 * x1 + (-x0 + x2) * u + (2 * x0 - 5 * x1 + 4 * x2 - x3) * u2 + (-x0 + 3 * x1 - 3 * x2 + x3) * u3))
                out.add(0.5 * (2 * y1 + (-y0 + y2) * u + (2 * y0 - 5 * y1 + 4 * y2 - y3) * u2 + (-y0 + 3 * y1 - 3 * y2 + y3) * u3))
            }
        }
        if (!closed) {
            out.add(c[2 * (n - 1)]); out.add(c[2 * (n - 1) + 1])
        }
        return out.toDoubleArray()
    }

    class Ribbon(val outline: DoubleArray, val centre: DoubleArray)

    /**
     * The ribbon's outline and its (wobbled) centre line. A closed stroke carries on a little past its
     * start, like a pen that overshoots its loop; both ends taper.
     */
    fun ribbon(ctrl: DoubleArray, closed: Boolean, width: Double, seed: Int, variant: Int, jitter: Double): Ribbon {
        val base = spline(ctrl, closed)
        val n0 = base.size / 2
        val pts: DoubleArray = if (closed) {
            val over = max(3, (n0 * 0.07).toInt())
            val extra = min(over, n0 - 1) + 1
            base + base.copyOfRange(0, 2 * extra)
        } else base
        val m = pts.size / 2
        val s = DoubleArray(m)
        for (k in 1 until m) s[k] = s[k - 1] + hypot(pts[2 * k] - pts[2 * k - 2], pts[2 * k + 1] - pts[2 * k - 1])
        val total = max(s[m - 1], 0.001)
        val b = seed * 7.0 + variant * 131.0
        val ph = DoubleArray(6) { hash(b + it + 1) * 2 * Math.PI }
        val jp = DoubleArray(2 * m)
        for (k in 0 until m) {
            val si = s[k]
            jp[2 * k] = pts[2 * k] + jitter * (0.8 * sin(si / 23 + ph[0]) + 0.45 * sin(si / 8.5 + ph[1]))
            jp[2 * k + 1] = pts[2 * k + 1] + jitter * (0.8 * sin(si / 19 + ph[2]) + 0.45 * sin(si / 7.5 + ph[3]))
        }
        val tl = min(16.0, total * 0.3)
        val out = DoubleArray(4 * m)
        for (k in 0 until m) {
            val a = max(k - 1, 0)
            val c = min(k + 1, m - 1)
            val dx = jp[2 * c] - jp[2 * a]
            val dy = jp[2 * c + 1] - jp[2 * a + 1]
            var d = hypot(dx, dy)
            if (d == 0.0) d = 1.0
            val nx = -dy / d
            val ny = dx / d
            val si = s[k]
            val tin = min(1.0, si / tl)
            val tout = min(1.0, (total - si) / tl)
            val tap = 0.14 + 0.86 * sin(min(tin, tout) * Math.PI / 2)
            val w = width * (0.8 + 0.2 * sin(si / 11 + ph[4]) + 0.08 * sin(si / 4.3 + ph[5])) * tap
            val hw = w / 2
            // left side forwards, right side backwards
            out[2 * k] = jp[2 * k] + nx * hw
            out[2 * k + 1] = jp[2 * k + 1] + ny * hw
            val r = 2 * m - 1 - k
            out[2 * r] = jp[2 * k] - nx * hw
            out[2 * r + 1] = jp[2 * k + 1] - ny * hw
        }
        val centre = jp.copyOfRange(0, 2 * (if (closed) n0 else m))
        return Ribbon(out, centre)
    }

    fun path(points: DoubleArray): Path = Path().apply {
        moveTo(points[0].toFloat(), points[1].toFloat())
        var i = 2
        while (i + 1 < points.size) {
            lineTo(points[i].toFloat(), points[i + 1].toFloat()); i += 2
        }
        close()
    }

    fun ink(stroke: SketchStroke, variant: Int): SketchInk {
        val ctrl = DoubleArray(stroke.points.size) { stroke.points[it].toDouble() }
        val r = ribbon(ctrl, stroke.closed, stroke.width.toDouble(), stroke.seed, variant, stroke.jitter.toDouble())
        val fill = if (stroke.fill != 0 && r.centre.size > 4) path(r.centre) else null
        val line = if (stroke.width >= 0.1f && r.outline.size > 4) path(r.outline) else null
        return SketchInk(fill, stroke.fill, line)
    }

    /** Draws prepared ink in the canvas's current transform. */
    fun draw(inks: List<SketchInk>, canvas: Canvas, paint: Paint, ground: Int, ink: Int, alpha: Int = 255) {
        for (item in inks) {
            item.fill?.let {
                paint.color = if (item.fillKind == 1) ground else ink
                paint.alpha = alpha
                canvas.drawPath(it, paint)
            }
            item.line?.let {
                paint.color = ink
                paint.alpha = alpha
                canvas.drawPath(it, paint)
            }
        }
    }
}

/**
 * The artwork, drawn by hand as point lists (in dp). The cup and its splash are drawn around the centre of
 * the cup's rim, the top-view cup around its centre, the hand around the pinch that holds the spoon.
 */
internal object SketchCoffeeSplashArt {
    const val CUP_RADIUS = 46f
    const val STIR_RADIUS = 17f
    const val PINCH_X = 52f
    const val PINCH_Y = -50f
    const val ARM_DX = 0.75f
    const val ARM_DY = 0.66f
    const val ARM_AWAY = 460f

    val cup: List<SketchStroke> = listOf(
        SketchStroke(1, 6.6f, false, 0, 1.4f, floatArrayOf(-119.2f, -8.3f, -92.6f, -20f, -47.6f, -27.7f, 6.8f, -30f, 59.8f, -26.3f, 101f, -17.4f, 122.2f, -5.1f, 119.2f, 8.3f, 92.6f, 20f, 47.6f, 27.7f, -6.8f, 30f, -59.8f, 26.3f, -101f, 17.4f, -122.2f, 5.1f)),
        SketchStroke(2, 4.2f, false, 0, 1.4f, floatArrayOf(-112.5f, 13.3f, -101f, 19.7f, -82.3f, 25.2f, -57.7f, 29.4f, -28.9f, 32.1f, 2f, 33f, 32.9f, 32.1f, 61.7f, 29.4f, 86.3f, 25.2f, 105f, 19.7f, 116.5f, 13.3f)),
        SketchStroke(3, 6.2f, false, 0, 1.4f, floatArrayOf(-122f, 6f, -112f, 64f, -100f, 132f, -73f, 226f)),
        SketchStroke(4, 6.2f, false, 0, 1.4f, floatArrayOf(122f, 4f, 114f, 66f, 100f, 140f, 73f, 224f)),
        SketchStroke(5, 5.5f, false, 0, 1.4f, floatArrayOf(71.6f, 224.4f, 68.5f, 230.6f, 53.5f, 236f, 29.3f, 239.7f, 0f, 241f, -29.3f, 239.7f, -53.5f, 236f, -68.5f, 230.6f, -71.6f, 224.4f)),
        SketchStroke(6, 3.1f, false, 0, 1.1f, floatArrayOf(-28.5f, 218.8f, -42.5f, 217.3f, -54.7f, 215.1f, -64.5f, 212.5f, -71.4f, 209.5f)),
        SketchStroke(7, 2.6f, false, 0, 1.1f, floatArrayOf(-49.3f, 199.4f, -61.9f, 196.8f, -71.5f, 193.6f, -77.6f, 190.1f)),
        SketchStroke(8, 2.6f, false, 0, 1.1f, floatArrayOf(-90.1f, 124f, -96f, 121.2f, -100.3f, 118.3f, -103f, 115.3f)),
        SketchStroke(9, 2.9f, false, 0, 0.7f, floatArrayOf(-107.8f, 34f, -102.3f, 49.9f)),
        SketchStroke(10, 2.9f, false, 0, 0.7f, floatArrayOf(-96.8f, 41f, -91.3f, 56.9f)),
        SketchStroke(11, 2.9f, false, 0, 0.7f, floatArrayOf(-104.4f, 49f, -98.9f, 63.2f)),
        SketchStroke(12, 2.9f, false, 0, 0.7f, floatArrayOf(-93.4f, 56f, -87.9f, 70.2f)),
        SketchStroke(13, 2.9f, false, 0, 0.7f, floatArrayOf(-101.1f, 64f, -95.6f, 78.3f)),
        SketchStroke(14, 2.9f, false, 0, 0.7f, floatArrayOf(-90.1f, 71f, -84.6f, 85.3f)),
        SketchStroke(15, 2.9f, false, 0, 0.7f, floatArrayOf(-97.7f, 79f, -92.2f, 95.2f)),
        SketchStroke(16, 2.9f, false, 0, 0.7f, floatArrayOf(-86.7f, 86f, -81.2f, 102.2f)),
        SketchStroke(17, 2.9f, false, 0, 0.7f, floatArrayOf(-94.4f, 94f, -88.9f, 111f)),
        SketchStroke(18, 2.9f, false, 0, 0.7f, floatArrayOf(-83.4f, 101f, -77.9f, 118f)),
        SketchStroke(19, 2.9f, false, 0, 0.7f, floatArrayOf(-91.1f, 109f, -85.5f, 123.7f)),
        SketchStroke(20, 2.9f, false, 0, 0.7f, floatArrayOf(-80.1f, 116f, -74.5f, 130.7f)),
        SketchStroke(21, 2.9f, false, 0, 0.7f, floatArrayOf(-87.7f, 124f, -82.2f, 138f)),
        SketchStroke(22, 2.9f, false, 0, 0.7f, floatArrayOf(-76.7f, 131f, -71.2f, 145f)),
        SketchStroke(23, 2.9f, false, 0, 0.7f, floatArrayOf(-84.4f, 139f, -78.9f, 154.2f)),
        SketchStroke(24, 2.9f, false, 0, 0.7f, floatArrayOf(-73.4f, 146f, -67.9f, 161.2f)),
        SketchStroke(25, 2.9f, false, 0, 0.7f, floatArrayOf(-81f, 154f, -75.5f, 171.8f)),
        SketchStroke(26, 2.9f, false, 0, 0.7f, floatArrayOf(-70f, 161f, -64.5f, 178.8f)),
        SketchStroke(27, 2.9f, false, 0, 0.7f, floatArrayOf(-77.7f, 169f, -72.2f, 184.6f)),
        SketchStroke(28, 2.9f, false, 0, 0.7f, floatArrayOf(-66.7f, 176f, -61.2f, 191.6f)),
        SketchStroke(29, 2.9f, false, 0, 0.7f, floatArrayOf(-74.4f, 184f, -68.8f, 198.1f)),
        SketchStroke(30, 4f, false, 0, 1.4f, floatArrayOf(92f, 150f, 64f, 178f)),
        SketchStroke(31, 4f, false, 0, 1.4f, floatArrayOf(92f, 172f, 70f, 194f)),
        SketchStroke(32, 3.5f, false, 0, 1.4f, floatArrayOf(88f, 194f, 78f, 204f)),
        SketchStroke(33, 3.3f, false, 0, 1.4f, floatArrayOf(104f, 98f, 96f, 110f)),
    )
    val splash: List<SketchStroke> = listOf(
        SketchStroke(34, 3.8f, true, 2, 1.6f, floatArrayOf(-69.9f, -32.3f, -73f, -35.7f, -77.3f, -40.1f, -81.9f, -44.9f, -86.4f, -50f, -90.3f, -55f, -93.2f, -59.9f, -95.2f, -64.3f, -97.2f, -69.7f, -99f, -75.5f, -100.6f, -81.6f, -102f, -87.9f, -103.1f, -94.2f, -103.9f, -100.4f, -104.4f, -106.6f, -104.6f, -112.9f, -104.6f, -119.4f, -104.3f, -125.9f, -103.7f, -132.4f, -102.9f, -138.9f, -101.9f, -145.2f, -100.6f, -151.5f, -99f, -157.9f, -97.1f, -164.4f, -95f, -170.7f, -92.6f, -176.9f, -90.2f, -182.7f, -87.7f, -188f, -84.5f, -193.8f, -80.9f, -199.3f, -77.1f, -204.4f, -73.1f, -209.3f, -69f, -213.8f, -65.2f, -217.7f, -60.5f, -221.9f, -55.7f, -225.5f, -51f, -228.4f, -46.5f, -230.7f, -43f, -232.3f, -37.3f, -233f, -32.6f, -232.1f, -29.3f, -230.6f, -27.3f, -227.7f, -26.2f, -223f, -25.9f, -219.8f, -30.4f, -216.5f, -36f, -214.4f, -35.1f, -211.5f, -28.7f, -212.8f, -21.9f, -216.7f, -20.1f, -223f, -20.5f, -229.8f, -23.4f, -236f, -28.9f, -240.3f, -36.2f, -242.9f, -44.6f, -243f, -50.5f, -241.8f, -56.3f, -239.9f, -62.5f, -237.1f, -68.8f, -233.6f, -74.8f, -229.4f, -79.9f, -225.4f, -85f, -221f, -90.2f, -216f, -95.3f, -210.5f, -100.1f, -204.5f, -104.6f, -198f, -108.3f, -192f, -111.7f, -185.6f, -115f, -179f, -118f, -172f, -120.7f, -164.9f, -123.2f, -157.7f, -125.3f, -150.5f, -127f, -143.3f, -128.5f, -136f, -129.7f, -128.5f, -130.5f, -120.9f, -131f, -113.4f, -131.2f, -105.8f, -131f, -98.3f, -130.5f, -90.8f, -129.5f, -83.3f, -128.3f, -75.7f, -126.6f, -68.2f, -124.7f, -60.9f, -122.3f, -53.8f, -119.3f, -46.7f, -114.9f, -38.8f, -109.5f, -31.4f, -104f, -24.9f, -98.9f, -19.4f, -94.8f, -15.2f, -92.1f, -12.2f)),
        SketchStroke(35, 0f, true, 1, 1.6f, floatArrayOf(-81.8f, -32.2f, -84.3f, -35.4f, -87.8f, -39.7f, -91.7f, -44.7f, -95.3f, -50.2f, -98.2f, -55.8f, -100.1f, -60.8f, -101.8f, -66.3f, -103.2f, -72.3f, -104.5f, -78.5f, -105.4f, -84.8f, -106.1f, -91.3f, -106.5f, -97f, -106.7f, -103.1f, -106.7f, -109.2f, -106.6f, -115.5f, -106.2f, -121.7f, -105.7f, -127.7f, -105f, -133.5f, -103.7f, -140.3f, -102f, -147.3f, -99.9f, -154.3f, -97.9f, -160.7f, -96.2f, -166f, -95.1f, -170f, -96.2f, -170.4f, -97.7f, -166.6f, -99.8f, -161.3f, -102.2f, -155.1f, -104.7f, -148.2f, -106.9f, -141.1f, -108.6f, -134.1f, -109.7f, -128.3f, -110.6f, -122.1f, -111.3f, -115.8f, -111.9f, -109.4f, -112.2f, -103.1f, -112.4f, -96.9f, -112.3f, -90.9f, -111.9f, -84.2f, -111.2f, -77.5f, -110.2f, -70.9f, -109f, -64.5f, -107.4f, -58.5f, -105.6f, -52.7f, -102.5f, -46.1f, -98.7f, -39.9f, -94.7f, -34.4f, -91.3f, -30f, -89f, -26.8f)),
        SketchStroke(36, 0f, true, 1, 1.6f, floatArrayOf(-92.2f, -166.7f, -90.5f, -170.7f, -88f, -176.3f, -84.9f, -182.6f, -81.6f, -188.9f, -78.3f, -194.3f, -73.5f, -200.2f, -68f, -206f, -62.9f, -211f, -59.5f, -214.5f, -60.1f, -215.2f, -63.8f, -211.9f, -69.1f, -207.2f, -74.9f, -201.6f, -80.1f, -195.6f, -83.9f, -190.2f, -87.6f, -184f, -91f, -177.8f, -94f, -172.5f, -96.2f, -168.8f)),
        SketchStroke(37, 3.4f, true, 2, 1.6f, floatArrayOf(-88.5f, -120.9f, -84.4f, -123f, -78.4f, -125.7f, -71.5f, -129.2f, -65.1f, -133.3f, -60.3f, -137.4f, -55.6f, -141.9f, -51f, -146.7f, -46.9f, -151.6f, -43.4f, -156.3f, -40f, -162f, -37.3f, -167.8f, -35.5f, -173.2f, -34.8f, -178.2f, -37.5f, -184.6f, -41.3f, -188.3f, -42.4f, -187.1f, -39.2f, -183.6f, -37.3f, -177.8f, -38.4f, -174.1f, -40.5f, -169.2f, -43.4f, -164f, -47f, -158.9f, -50.6f, -154.8f, -54.9f, -150.5f, -59.6f, -146.3f, -64.4f, -142.4f, -69f, -139.2f, -75f, -136f, -81.6f, -133.4f, -87.7f, -131.5f, -92.1f, -130.2f)),
        SketchStroke(38, 3.1f, true, 2, 1.6f, floatArrayOf(-113.8f, -112f, -117f, -115.4f, -121.4f, -120.8f, -126.5f, -126.3f, -131.7f, -130.3f, -138.5f, -132.1f, -145f, -131.7f, -150.4f, -129.6f, -154.4f, -125.6f, -156.9f, -120.3f, -157.9f, -115.2f, -155.1f, -109.2f, -151.5f, -105.2f, -150.4f, -106.1f, -153.5f, -110.2f, -155.6f, -115.5f, -154.4f, -119.3f, -151.8f, -123.6f, -148.5f, -126.4f, -144.3f, -127.5f, -139.1f, -127.5f, -134.2f, -125.7f, -130.7f, -122.2f, -126.5f, -116.8f, -122.7f, -111.2f, -120.1f, -107.1f)),
        SketchStroke(39, 3.4f, true, 2, 1.6f, floatArrayOf(-54.9f, -13.2f, -54f, -17.4f, -52.5f, -23f, -50.6f, -29.5f, -48.4f, -36.4f, -46.1f, -43.2f, -43.6f, -49.2f, -41f, -54.9f, -38f, -60.6f, -34.8f, -66.3f, -31.5f, -71.7f, -28.1f, -76.7f, -24.8f, -81.2f, -20.4f, -86.1f, -15.8f, -90.6f, -11.1f, -94.6f, -6.6f, -97.9f, -2.8f, -100.3f, 2.8f, -102.3f, 7.5f, -102.4f, 10.1f, -101.7f, 10.8f, -97.3f, 9.8f, -92.3f, 11.4f, -92f, 12.7f, -97.2f, 12.1f, -103f, 7.9f, -105f, 2.4f, -105.3f, -4.1f, -103.4f, -8.6f, -101f, -13.5f, -97.9f, -18.7f, -94f, -23.8f, -89.6f, -28.7f, -84.6f, -32.6f, -80.1f, -36.5f, -75.1f, -40.3f, -69.7f, -43.9f, -64.1f, -47.3f, -58.3f, -50.5f, -52.6f, -53.6f, -46.3f, -56.5f, -39.4f, -59.2f, -32.4f, -61.6f, -25.9f, -63.7f, -20.5f, -65.4f, -16.8f)),
        SketchStroke(40, 2.9f, true, 2, 1.6f, floatArrayOf(-17.5f, -13.4f, -15.7f, -17.2f, -12.9f, -22.4f, -9.6f, -28.3f, -6.1f, -34f, -2.7f, -38.7f, 2f, -43.7f, 7f, -48.2f, 12f, -52f, 16.4f, -54.6f, 21.6f, -55.5f, 26.4f, -54.5f, 29.9f, -53.8f, 30.2f, -54.9f, 26.8f, -56.2f, 21.7f, -57.6f, 15.6f, -57f, 10.4f, -54.6f, 4.9f, -51.1f, -0.8f, -46.7f, -6.1f, -41.8f, -10.3f, -37f, -14.5f, -31.4f, -18.4f, -25.7f, -21.7f, -20.9f, -24.3f, -17.6f)),
        SketchStroke(41, 2.9f, true, 2, 1.6f, floatArrayOf(62.5f, -24.4f, 64.7f, -28f, 68.2f, -32.9f, 72f, -38f, 75.7f, -41.9f, 80.9f, -45.4f, 86.4f, -47.8f, 90.3f, -48.6f, 93.9f, -44.7f, 96f, -39.6f, 97f, -40f, 95.3f, -45.5f, 91.1f, -50.4f, 85.7f, -50.2f, 79.6f, -48f, 73.6f, -44.6f, 69.1f, -40.7f, 64.6f, -35.8f, 60.7f, -31.1f, 57.8f, -28.1f)),
        SketchStroke(42, 3.8f, false, 0, 1.6f, floatArrayOf(-70.3f, -7.2f, -38.3f, -11.6f, -2.9f, -13.1f, 34.9f, -16f, 70.3f, -17.5f, 102.8f, -15.6f)),
        SketchStroke(43, 3.6f, false, 0, 1.6f, floatArrayOf(22.7f, -20.3f, 25.2f, -23.3f, 28.8f, -24.5f, 32.6f, -23.5f, 35.3f, -20.8f, 36.1f, -17f)),
        SketchStroke(44, 3.4f, false, 0, 1.6f, floatArrayOf(40.6f, -21.9f, 42.7f, -24.4f, 45.9f, -25.4f, 49.1f, -24.6f, 51.4f, -22.2f, 52.1f, -19f)),
    )
    val drops: List<SketchStroke> = listOf(
        SketchStroke(45, 3.4f, true, 2, 1f, floatArrayOf(26.8f, -262.6f, 28.9f, -256.4f, 25.7f, -248.7f, 18.9f, -243.1f, 11.5f, -242.3f, 7.1f, -246.6f, 7.7f, -254f, 13f, -261f, 20.5f, -264.4f)),
        SketchStroke(46, 3.4f, true, 2, 1f, floatArrayOf(66.2f, -223.6f, 64.7f, -216.9f, 57.6f, -211.4f, 48.3f, -209.9f, 41f, -213f, 39.2f, -219.3f, 43.7f, -225.7f, 52.5f, -229.4f, 61.4f, -228.6f)),
        SketchStroke(47, 2.8f, true, 2, 1f, floatArrayOf(-17.8f, -210.3f, -17f, -214.2f, -14.1f, -216.8f, -10.3f, -217.4f, -6.8f, -215.7f, -4.9f, -212.2f, -5.4f, -208.4f, -7.9f, -205.4f, -11.7f, -204.5f, -23.4f, -198.4f)),
        SketchStroke(48, 2.6f, true, 2, 1f, floatArrayOf(13.5f, -169.8f, 10.2f, -166.2f, 5.1f, -166.4f, 1.8f, -170.2f, 3f, -174.9f, 7.7f, -176.8f, 12.3f, -174.5f)),
        SketchStroke(49, 3.6f, true, 2, 1f, floatArrayOf(102.6f, -129.3f, 102.7f, -122.2f, 97.2f, -115.1f, 88.3f, -110.7f, 79.4f, -110.8f, 73.8f, -115.3f, 73.7f, -122.4f, 79.2f, -129.5f, 88.1f, -133.9f, 97f, -133.8f)),
        SketchStroke(50, 3.2f, true, 2, 1f, floatArrayOf(-165.1f, -142.3f, -169.6f, -144.1f, -172.2f, -148.1f, -172.1f, -152.9f, -169.2f, -156.7f, -164.7f, -158.3f, -160.1f, -156.9f, -157.1f, -153.1f, -156.8f, -148.3f, -151f, -131.7f)),
        SketchStroke(51, 2.6f, true, 2, 1f, floatArrayOf(35.9f, -99.3f, 36f, -102.9f, 38.1f, -105.8f, 41.5f, -106.9f, 45f, -105.9f, 47.3f, -103.1f, 47.5f, -99.5f, 45.6f, -96.4f, 42.3f, -95f, 32.1f, -86.8f)),
        SketchStroke(52, 2.4f, true, 2, 1f, floatArrayOf(-126.2f, -186.1f, -128.6f, -183.4f, -132.5f, -183.6f, -135f, -186.5f, -134.1f, -189.8f, -130.6f, -191.2f, -127.1f, -189.5f)),
        SketchStroke(53, 2.4f, true, 2, 1f, floatArrayOf(-7.4f, -249.2f, -9.8f, -246.6f, -13.7f, -246.7f, -16.2f, -249.6f, -15.3f, -253f, -11.8f, -254.3f, -8.3f, -252.6f)),
        SketchStroke(54, 2.4f, true, 2, 1f, floatArrayOf(113.3f, -68.9f, 113.9f, -71.9f, 116.1f, -73.9f, 119.1f, -74.4f, 121.8f, -73f, 123.2f, -70.4f, 122.9f, -67.4f, 120.9f, -65.2f, 118f, -64.5f, 108.6f, -59.4f)),
    )
    val top: List<SketchStroke> = listOf(
        SketchStroke(56, 6.4f, false, 0, 1f, floatArrayOf(-43.2f, -15.7f, -32.1f, -32.9f, -14.7f, -43.6f, 5.7f, -45.6f, 25f, -38.6f, 39.3f, -24f, 45.8f, -4.6f, 43.2f, 15.7f, 32.1f, 32.9f, 14.7f, 43.6f, -5.7f, 45.6f, -25f, 38.6f, -39.3f, 24f, -45.8f, 4.6f)),
        SketchStroke(57, 3.8f, false, 0, 1f, floatArrayOf(47f, -6f, 62f, -6f, 65f, 0f, 62f, 6f, 47f, 6f)),
    )
    val swirl: List<SketchStroke> = listOf(
        SketchStroke(55, 3.4f, false, 0, 1f, floatArrayOf(14.9f, 18.8f, 8.7f, 22.4f, 1.7f, 23.9f, -5.5f, 23.4f, -12.1f, 20.7f, -17.7f, 16.2f)),
    )
    val hand: List<SketchStroke> = listOf(
        SketchStroke(58, 0f, true, 1, 0f, floatArrayOf(0f, -10f, 26f, -16f, 70f, 6f, 140f, 50f, 240f, 128f, 360f, 234f, 480f, 350f, 460f, 436f, 350f, 324f, 230f, 212f, 130f, 122f, 60f, 60f, 16f, 24f)),
        SketchStroke(59, 3.8f, false, 0, 1.3f, floatArrayOf(26f, -16f, 70f, 6f, 140f, 50f, 240f, 128f, 360f, 234f, 480f, 350f)),
        SketchStroke(60, 3.8f, false, 0, 1.3f, floatArrayOf(16f, 24f, 60f, 60f, 130f, 122f, 230f, 212f, 350f, 324f, 460f, 436f)),
        SketchStroke(61, 3.4f, true, 1, 1f, floatArrayOf(-0.7f, -36.1f, -4.8f, -36.5f, -8.2f, -38.9f, -9.9f, -42.7f, -9.5f, -46.8f, -7.1f, -50.2f, -3.3f, -51.9f, 32.7f, -57.9f, 36.8f, -57.5f, 40.2f, -55.1f, 41.9f, -51.3f, 41.5f, -47.2f, 39.1f, -43.8f, 35.3f, -42.1f)),
        SketchStroke(62, 3.6f, true, 1, 1f, floatArrayOf(-14.2f, -18.7f, -19.1f, -19f, -23.2f, -21.8f, -25.3f, -26.2f, -25f, -31.1f, -22.2f, -35.2f, -17.8f, -37.3f, 24.2f, -45.3f, 29.1f, -45f, 33.2f, -42.2f, 35.3f, -37.8f, 35f, -32.9f, 32.2f, -28.8f, 27.8f, -26.7f)),
        SketchStroke(63, 4f, false, 1, 1f, floatArrayOf(16.3f, 22.9f, 5.6f, 26.8f, -5.8f, 25.9f, -15.7f, 20.3f, -22.6f, 11.2f, -25f, 0.1f, -22.6f, -11.1f, -15.9f, -20.2f, -5.9f, -25.8f, 5.4f, -26.8f, 16.2f, -23f, 24.4f, -15.1f)),
    )
    val thumb: List<SketchStroke> = listOf(
        SketchStroke(64, 3.6f, false, 0, 1f, floatArrayOf(-6f, 9.5f, -11.6f, 2.8f, -10.4f, -5.5f, -3.1f, -10.6f, 6f, -9.5f, 11.6f, -2.8f, 10.4f, 5.5f, 3.1f, 10.6f)),
    )


    private fun prepare(strokes: List<SketchStroke>): List<List<SketchInk>> =
        List(SketchCoffeeSplashSketch.VARIANTS) { v -> strokes.map { SketchCoffeeSplashSketch.ink(it, v) } }

    /** Each group in every boil variant, built once. */
    val cupInk by lazy { prepare(cup) }
    val splashInk by lazy { prepare(splash) }
    val dropInk by lazy { prepare(drops) }
    val topInk by lazy { prepare(top) }
    val swirlInk by lazy { prepare(swirl) }
    val handInk by lazy { prepare(hand) }
    val thumbInk by lazy { prepare(thumb) }

    /** Where each drop rests, in the cup's frame (x, y pairs). */
    val dropCentres: List<Pair<Double, Double>> by lazy {
        drops.map { stroke ->
            var x = 0.0
            var y = 0.0
            val n = stroke.points.size / 2
            for (i in 0 until n) {
                x += stroke.points[2 * i]; y += stroke.points[2 * i + 1]
            }
            Pair(x / n, y / n)
        }
    }
}
