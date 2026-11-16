package com.ngocdevv.nativecook.blocks.pickers.beamshapetorch

import android.graphics.Path
import android.graphics.RectF
import com.ngocdevv.nativecook.blocks.pickers.beamshapetorch.BeamShapeTorchTheme as Theme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Shapes (RN `bst-geometry.ts`). The capsule is a continuous-corner rectangle (squircle, corner smoothing 0.6, the iOS / Figma
 * construction) whose size morphs between the island and the panel; the torch and the sun are built once at their own size and
 * placed with transforms; the arc is rebuilt per frame.
 */
internal object BeamShapeTorchShapes {
    private const val SMOOTHING = 0.6
    private val oval = RectF()

    private fun rad(deg: Double) = deg * PI / 180

    /** Rebuilds `p` as a continuous-corner rectangle. The smoothing gives way where the sides are too short (a pill has none). */
    fun squircle(p: Path, x: Double, y: Double, w: Double, h: Double, radius: Double) {
        p.rewind()
        val budget = max(min(w, h) / 2, 0.01)
        val r = min(radius, budget)
        var xi = SMOOTHING
        if ((1 + xi) * r > budget) xi = max(budget / max(r, 0.01) - 1, 0.0)
        val pp = (1 + xi) * r
        val arcMeasure = 90 * (1 - xi)
        val arcLen = sin(rad(arcMeasure / 2)) * r * sqrt(2.0)
        val alpha = (90 - arcMeasure) / 2
        val p3p4 = r * tan(rad(alpha / 2))
        val beta = 45 * xi
        val c = p3p4 * cos(rad(beta))
        val d = c * tan(rad(beta))
        val b = (pp - arcLen - c - d) / 3
        val a = 2 * b
        fun rc(x1: Double, y1: Double, x2: Double, y2: Double, x3: Double, y3: Double) =
            p.rCubicTo(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), x3.toFloat(), y3.toFloat())
        // a circular corner arc of `arcMeasure` deg about (cx, cy), centred on the corner's diagonal `dir` (deg, clockwise from +x)
        fun corner(cx: Double, cy: Double, dir: Double) {
            oval.set((cx - r).toFloat(), (cy - r).toFloat(), (cx + r).toFloat(), (cy + r).toFloat())
            p.arcTo(oval, (dir - arcMeasure / 2).toFloat(), arcMeasure.toFloat(), false)
        }
        p.moveTo((x + w - pp).toFloat(), y.toFloat())
        rc(a, 0.0, a + b, 0.0, a + b + c, d)
        corner(x + w - r, y + r, -45.0)
        rc(d, c, d, b + c, d, a + b + c)
        p.lineTo((x + w).toFloat(), (y + h - pp).toFloat())
        rc(0.0, a, 0.0, a + b, -d, a + b + c)
        corner(x + w - r, y + h - r, 45.0)
        rc(-c, d, -(b + c), d, -(a + b + c), d)
        p.lineTo((x + pp).toFloat(), (y + h).toFloat())
        rc(-a, 0.0, -(a + b), 0.0, -(a + b + c), -d)
        corner(x + r, y + h - r, 135.0)
        rc(-d, -c, -d, -(b + c), -d, -(a + b + c))
        p.lineTo(x.toFloat(), (y + pp).toFloat())
        rc(0.0, -a, 0.0, -(a + b), d, -(a + b + c))
        corner(x + r, y + r, 225.0)
        rc(c, -d, b + c, -d, a + b + c, -d)
        p.close()
    }

    /** The torch, in design pt inside its 28 x 79 box: a cap 8 pt tall with rounded top corners. */
    fun torchCap(): Path {
        val w = Theme.GLYPH_W.toFloat()
        return Path().apply {
            moveTo(0f, 8f)
            lineTo(0f, 2.6f)
            quadTo(0f, 0f, 2.6f, 0f)
            lineTo(w - 2.6f, 0f)
            quadTo(w, 0f, w, 2.6f)
            lineTo(w, 8f)
            close()
        }
    }

    /** A 3 pt gap under the cap, then the head that tapers from the cap's width to the 16.6 pt handle, which ends in 3.5 pt corners. */
    fun torchBody(): Path {
        val w = Theme.GLYPH_W.toFloat()
        val h = Theme.GLYPH_H.toFloat()
        val l = 5.7f
        val r = w - 5.7f
        return Path().apply {
            moveTo(0.3f, 11f)
            lineTo(w - 0.3f, 11f)
            lineTo(w - 0.8f, 21f)
            cubicTo(w - 1.2f, 25.5f, r + 0.2f, 28f, r, 32.5f)
            lineTo(r, h - 3.5f)
            quadTo(r, h, r - 3.5f, h)
            lineTo(l + 3.5f, h)
            quadTo(l, h, l, h - 3.5f)
            lineTo(l, 32.5f)
            cubicTo(l - 0.2f, 28f, 1.2f, 25.5f, 0.8f, 21f)
            close()
        }
    }

    /** The stage spot's lens ring: a bar across the head. */
    fun spotLens(): Path {
        val w = Theme.GLYPH_W.toFloat()
        return Path().apply {
            moveTo(0f, 7f)
            lineTo(0f, 2.6f)
            quadTo(0f, 0f, 2.6f, 0f)
            lineTo(w - 2.6f, 0f)
            quadTo(w, 0f, w, 2.6f)
            lineTo(w, 7f)
            close()
        }
    }

    /** The barrel, narrowing away from the lens. */
    fun spotHead(): Path {
        val w = Theme.GLYPH_W.toFloat()
        return Path().apply {
            moveTo(0.4f, 10f)
            lineTo(w - 0.4f, 10f)
            lineTo(w - 4.6f, 36f)
            lineTo(4.6f, 36f)
            close()
        }
    }

    /** The stand: a stem and a foot. */
    fun spotStem(): Path = Path().apply {
        moveTo(11f, 38.5f)
        lineTo(17f, 38.5f)
        lineTo(17f, 70f)
        lineTo(11f, 70f)
        close()
        moveTo(5f, 73f)
        quadTo(5f, 70f, 8f, 70f)
        lineTo(20f, 70f)
        quadTo(23f, 70f, 23f, 73f)
        quadTo(23f, 79f, 17.5f, 79f)
        lineTo(10.5f, 79f)
        quadTo(5f, 79f, 5f, 73f)
        close()
    }

    /** The sun at the arc's end, centred on 0, 0: a ring and eight short rays. */
    fun sun(): Path = Path().apply {
        addCircle(0f, 0f, Theme.SUN_RING.toFloat(), Path.Direction.CW)
        for (i in 0 until Theme.SUN_RAYS) {
            val a = (i.toDouble() / Theme.SUN_RAYS) * PI * 2
            moveTo((cos(a) * Theme.SUN_RAY_IN).toFloat(), (sin(a) * Theme.SUN_RAY_IN).toFloat())
            lineTo((cos(a) * Theme.SUN_RAY_OUT).toFloat(), (sin(a) * Theme.SUN_RAY_OUT).toFloat())
        }
    }

    /** The arc (solid or dotted): through (cx, apexY), half-width hw, ends `sag` lower. Straight when sag is ~0. */
    fun arc(p: Path, cx: Double, apexY: Double, hw: Double, sag: Double) {
        p.rewind()
        val g = BeamShapeTorchMotion.arcGeometry(cx, apexY, hw, sag)
        if (g.straight) {
            p.moveTo((cx - hw).toFloat(), apexY.toFloat())
            p.lineTo((cx + hw).toFloat(), apexY.toFloat())
        } else {
            val deg = g.half * 180 / PI
            oval.set((cx - g.r).toFloat(), (g.cy - g.r).toFloat(), (cx + g.r).toFloat(), (g.cy + g.r).toFloat())
            p.addArc(oval, (-90 - deg).toFloat(), (2 * deg).toFloat())
        }
    }
}
