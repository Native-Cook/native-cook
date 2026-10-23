package com.ngocdevv.nativecook.blocks.playground.pendulumwave

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// Drawing for the pendulum wave on an Android canvas already scaled to dp, step for step as the
// React Native Skia drawing: a perspective camera orbiting the scene, a walnut frame drawn as shaded
// boxes, V-strung brass bobs with light trails, their shadows on the table, and the comb.
// Everything is painted back to front (faces and bobs are sorted by depth each frame), lit by one
// light from above, a little to the left and front.

/** What the camera looks at. */
internal const val TARGET_Y = 6.2
// The table's extent, and the frame: beam, posts at the row's ends, feet.
private const val TABLE_X = 14.0
private const val TABLE_Z = 16.0
private const val POST_Z = (COUNT - 1) / 2.0 * SPACING + 1.3

private fun norm(x: Double, y: Double, z: Double): DoubleArray {
    val l = sqrt(x * x + y * y + z * z)
    return doubleArrayOf(x / l, y / l, z / l)
}

// Toward the light.
private val Light = norm(-0.35, 1.0, 0.3)
private val Wood = doubleArrayOf(0.42, 0.25, 0.14)

/** The camera on the screen: centre, focal length, position and its right, up and forward axes. */
internal class View3 {
    var cx = 0.0
    var cy = 0.0
    var f = 0.0
    val c = DoubleArray(3)
    var r = DoubleArray(3)
    val u = DoubleArray(3)
    var w = DoubleArray(3)

    // The last projected point: screen x, y and depth.
    var px = 0.0
    var py = 0.0
    var pd = 0.0

    /** Points the camera for an orbit `cam` on a canvas of the given width. */
    fun set(cam: Camera, width: Double, focal: Double, centreY: Double) {
        val yaw = cam.yaw * PI / 180
        val pitch = cam.pitch * PI / 180
        c[0] = cam.dist * cos(pitch) * sin(yaw)
        c[1] = TARGET_Y + cam.dist * sin(pitch)
        c[2] = cam.dist * cos(pitch) * cos(yaw)
        w = norm(-c[0], TARGET_Y - c[1], -c[2])
        // right = forward × up, up' = right × forward.
        r = norm(-w[2], 0.0, w[0])
        u[0] = r[1] * w[2] - r[2] * w[1]
        u[1] = r[2] * w[0] - r[0] * w[2]
        u[2] = r[0] * w[1] - r[1] * w[0]
        cx = width / 2
        cy = centreY
        f = focal
    }

    /** World point to screen, into px, py and pd. */
    fun project(x: Double, y: Double, z: Double) {
        val dx = x - c[0]
        val dy = y - c[1]
        val dz = z - c[2]
        val depth = max(0.1, dx * w[0] + dy * w[1] + dz * w[2])
        val sx = dx * r[0] + dy * r[1] + dz * r[2]
        val sy = dx * u[0] + dy * u[1] + dz * u[2]
        px = cx + f * sx / depth
        py = cy - f * sy / depth
        pd = depth
    }
}

/** A colour from 0…1 channels (clamped like Skia's) and alpha. */
private fun rgba(r: Double, g: Double, b: Double, a: Double): Int {
    fun ch(v: Double) = (min(1.0, max(0.0, v)) * 255).roundToInt()
    return (ch(a) shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
}

private class Box(val cx: Double, val cy: Double, val cz: Double, val sx: Double, val sy: Double, val sz: Double, val alpha: Double)

// The six faces of a unit box: outward normal, then the two in-face axes.
private val Faces = arrayOf(
    doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0),
    doubleArrayOf(-1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0),
    doubleArrayOf(0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0),
    doubleArrayOf(0.0, -1.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0),
    doubleArrayOf(0.0, 0.0, 1.0, 1.0, 0.0, 0.0, 0.0, 1.0, 0.0),
    doubleArrayOf(0.0, 0.0, -1.0, 1.0, 0.0, 0.0, 0.0, 1.0, 0.0),
)

/** One thing to paint, with its depth from the camera. */
private class Item {
    var depth = 0.0
    var kind = 0
    var index = 0
    var face = 0
}

/** Paints, paths and the sort list, reused from frame to frame. */
internal class PendulumPainter {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val path = Path()
    private val shadowPath = Path()
    private val items = Array(64) { Item() }
    private var itemCount = 0
    private val order = arrayOfNulls<Item>(64)
    private var density = 1f
    private val bobShadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wideShadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spec = Paint(Paint.ANTI_ALIAS_FLAG)
    private var specSigma = -1f

    // A face's centre and its two half-axes, from faceCorners.
    private val fc = DoubleArray(3)
    private val fa = DoubleArray(3)
    private val fd = DoubleArray(3)

    /**
     * React Native blurs the shadows by a sigma in device pixels (not scaled with the canvas), so
     * here they are divided by the density of the canvas, which is in dp.
     */
    fun setDensity(d: Float) {
        if (d == density && bobShadow.maskFilter != null) return
        density = d
        bobShadow.color = rgba(0.02, 0.01, 0.0, 0.42)
        bobShadow.maskFilter = BlurMaskFilter(sigmaToRadius(3.5f / d), BlurMaskFilter.Blur.NORMAL)
        wideShadow.maskFilter = BlurMaskFilter(sigmaToRadius(5f / d), BlurMaskFilter.Blur.NORMAL)
    }

    // Android takes blur radii; Skia (and the RN code) thinks in sigma.
    private fun sigmaToRadius(sigma: Float) = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.01f)

    private fun Canvas.segment(v: View3, x0: Double, y0: Double, z0: Double, x1: Double, y1: Double, z1: Double, color: Int, width: Float) {
        v.project(x0, y0, z0)
        val ax = v.px.toFloat()
        val ay = v.py.toFloat()
        v.project(x1, y1, z1)
        line.color = color
        line.strokeWidth = width
        drawLine(ax, ay, v.px.toFloat(), v.py.toFloat(), line)
    }

    /** Adds a world-space polygon to `p`. */
    private fun polygon(p: Path, v: View3, pts: Array<DoubleArray>) {
        for (i in pts.indices) {
            v.project(pts[i][0], pts[i][1], pts[i][2])
            if (i == 0) p.moveTo(v.px.toFloat(), v.py.toFloat()) else p.lineTo(v.px.toFloat(), v.py.toFloat())
        }
        p.close()
    }

    /** The frame's boxes: the beam (faded by `beamAlpha`), the posts, the feet. */
    private fun frameBoxes(beamAlpha: Double) = arrayOf(
        Box(0.0, BEAM_Y + 0.3, 0.0, 0.6, 0.6, POST_Z * 2 + 0.5, beamAlpha),
        Box(0.0, (BEAM_Y + 0.6) / 2, -POST_Z, 0.5, BEAM_Y + 0.6, 0.5, 1.0),
        Box(0.0, (BEAM_Y + 0.6) / 2, POST_Z, 0.5, BEAM_Y + 0.6, 0.5, 1.0),
        Box(0.0, 0.2, -POST_Z, 5.4, 0.4, 0.9, 1.0),
        Box(0.0, 0.2, POST_Z, 5.4, 0.4, 0.9, 1.0),
    )

    /** One face of a box: its centre into fc and its half-axes into fa and fd. */
    private fun faceCorners(b: Box, f: DoubleArray) {
        val hx = b.sx / 2
        val hy = b.sy / 2
        val hz = b.sz / 2
        fc[0] = b.cx + f[0] * hx
        fc[1] = b.cy + f[1] * hy
        fc[2] = b.cz + f[2] * hz
        fa[0] = f[3] * hx
        fa[1] = f[4] * hy
        fa[2] = f[5] * hz
        fd[0] = f[6] * hx
        fd[1] = f[7] * hy
        fd[2] = f[8] * hz
    }

    /** A walnut face, lit by the light and grained along its longer side. */
    private fun Canvas.drawFace(v: View3, b: Box, f: DoubleArray) {
        faceCorners(b, f)
        val lit = 0.32 + 0.68 * max(0.0, f[0] * Light[0] + f[1] * Light[1] + f[2] * Light[2])
        val top = if (f[1] > 0.5) 1.12 else 1.0
        path.reset()
        val corners = arrayOf(
            doubleArrayOf(fc[0] - fa[0] - fd[0], fc[1] - fa[1] - fd[1], fc[2] - fa[2] - fd[2]),
            doubleArrayOf(fc[0] + fa[0] - fd[0], fc[1] + fa[1] - fd[1], fc[2] + fa[2] - fd[2]),
            doubleArrayOf(fc[0] + fa[0] + fd[0], fc[1] + fa[1] + fd[1], fc[2] + fa[2] + fd[2]),
            doubleArrayOf(fc[0] - fa[0] + fd[0], fc[1] - fa[1] + fd[1], fc[2] - fa[2] + fd[2]),
        )
        polygon(path, v, corners)
        fill.shader = null
        fill.color = rgba(Wood[0] * lit * top, Wood[1] * lit * top, Wood[2] * lit * top, b.alpha)
        drawPath(path, fill)
        // Grain: a few darker lines along the face's longer axis.
        val la = sqrt(fa[0] * fa[0] + fa[1] * fa[1] + fa[2] * fa[2])
        val ld = sqrt(fd[0] * fd[0] + fd[1] * fd[1] + fd[2] * fd[2])
        val along = if (la > ld) fa else fd
        val across = if (la > ld) fd else fa
        val grain = rgba(0.12, 0.06, 0.03, 0.22 * b.alpha)
        for (i in 1..3) {
            val t = -0.7 + i * 0.37
            val ox = fc[0] + across[0] * t
            val oy = fc[1] + across[1] * t
            val oz = fc[2] + across[2] * t
            segment(
                v,
                ox - along[0] * 0.98, oy - along[1] * 0.98, oz - along[2] * 0.98,
                ox + along[0] * 0.98, oy + along[1] * 0.98, oz + along[2] * 0.98,
                grain, 0.7f,
            )
        }
        // A light catch along the top edge of side faces.
        val e0 = corners[3]
        val e1 = corners[2]
        segment(v, e0[0], e0[1], e0[2], e1[0], e1[1], e1[2], rgba(1.0, 0.86, 0.66, 0.12 * b.alpha * lit), 0.8f)
    }

    /** One brass bob with its V of string and its light trail. */
    private fun Canvas.drawBob(v: View3, s: PendulumSim, k: Int, stringAlpha: Double) {
        v.project(s.bobX(k, s.x[k]), s.bobY(k, s.x[k]), rowZ(k))
        val px = v.px
        val py = v.py
        val pd = v.pd
        val z = rowZ(k)
        // The trail: newest sample first, fading and thinning with age.
        val head = s.trailHead
        var prevX = px
        var prevY = py
        for (i in 1 until TRAIL) {
            val x = s.trail[k][(head - i + TRAIL * 2) % TRAIL]
            v.project(s.bobX(k, x), s.bobY(k, x), z)
            val age = i.toDouble() / TRAIL
            val len = hypot(v.px - prevX, v.py - prevY)
            if (len > 0.3) {
                line.color = rgba(1.0, 0.86, 0.6, 0.5 * (1 - age) * (1 - age))
                line.strokeWidth = (2.2 * (1 - age) + 0.4).toFloat()
                drawLine(prevX.toFloat(), prevY.toFloat(), v.px.toFloat(), v.py.toFloat(), line)
            }
            prevX = v.px
            prevY = v.py
        }
        // The strings.
        val str = rgba(0.92, 0.9, 0.85, 0.5 * stringAlpha)
        line.color = str
        line.strokeWidth = 0.75f
        v.project(0.0, BEAM_Y, z - V_HALF)
        drawLine(v.px.toFloat(), v.py.toFloat(), px.toFloat(), py.toFloat(), line)
        v.project(0.0, BEAM_Y, z + V_HALF)
        drawLine(v.px.toFloat(), v.py.toFloat(), px.toFloat(), py.toFloat(), line)
        // The bob: brass lit from the upper left, a warm bounce from the table on its lower edge.
        val r = (v.f * BOB_R / pd).toFloat()
        val x = px.toFloat()
        val y = py.toFloat()
        fill.color = 0xFF000000.toInt()
        fill.shader = RadialGradient(
            // Android's RadialGradient throws on a radius of 0 (a camera with no room yet).
            x - r * 0.35f, y - r * 0.42f, max(0.01f, r * 1.45f),
            intArrayOf(rgba(1.0, 0.96, 0.8, 1.0), rgba(0.89, 0.7, 0.33, 1.0), rgba(0.55, 0.37, 0.12, 1.0), rgba(0.2, 0.12, 0.03, 1.0)),
            floatArrayOf(0f, 0.22f, 0.62f, 1f),
            Shader.TileMode.CLAMP,
        )
        drawCircle(x, y, r, fill)
        fill.shader = null
        line.color = 0xFF000000.toInt()
        line.strokeWidth = max(0.6f, r * 0.18f)
        line.shader = LinearGradient(x, y, x, y + r, rgba(1.0, 0.7, 0.4, 0.0), rgba(1.0, 0.72, 0.42, 0.45), Shader.TileMode.CLAMP)
        drawCircle(x, y, r * 0.9f, line)
        line.shader = null
        val sigma = max(0.4f, r * 0.1f)
        if (sigma != specSigma) {
            specSigma = sigma
            spec.color = rgba(1.0, 1.0, 1.0, 0.9)
            spec.maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
        }
        drawCircle(x - r * 0.38f, y - r * 0.44f, r * 0.16f, spec)
    }

    /** The comb: a brass rail through the bobs' line with a tooth between each pair. */
    private fun Canvas.drawComb(v: View3, s: PendulumSim) {
        val shown = s.combShown
        if (shown <= 0.01) return
        val sink = (1 - shown) * 0.8
        val ys = DoubleArray(COUNT + 2)
        path.reset()
        for (k in -1..COUNT) {
            val kk = min(COUNT - 1, max(0, k))
            val y = s.bobY(kk, s.combX) - sink
            ys[k + 1] = y
            val z = if (k < 0) rowZ(0) - 0.9 else if (k >= COUNT) rowZ(COUNT - 1) + 0.9 else rowZ(k)
            v.project(s.combX, y, z)
            if (k == -1) path.moveTo(v.px.toFloat(), v.py.toFloat()) else path.lineTo(v.px.toFloat(), v.py.toFloat())
        }
        v.project(s.combX, 5.0, 0.0)
        val w = (v.f * 0.09 / v.pd).toFloat()
        line.color = rgba(0.16, 0.1, 0.04, 0.5 * shown)
        line.strokeWidth = w + 1.6f
        drawPath(path, line)
        line.color = rgba(0.86, 0.66, 0.32, shown)
        line.strokeWidth = w
        drawPath(path, line)
        line.color = rgba(1.0, 0.95, 0.8, 0.55 * shown)
        line.strokeWidth = w * 0.3f
        drawPath(path, line)
        // Teeth between the bobs, so each string sits in its own slot.
        val tooth = rgba(0.8, 0.6, 0.28, shown)
        for (k in 0 until COUNT - 1) {
            val y = (ys[k + 1] + ys[k + 2]) / 2
            val z = rowZ(k) + SPACING / 2
            segment(v, s.combX, y - 0.5, z, s.combX, y + 0.5, z, tooth, max(1f, w * 0.55f))
        }
    }

    /** Where a world point's shadow falls on the table: x and z. */
    private fun shadowX(x: Double, y: Double) = x - Light[0] * y / Light[1]
    private fun shadowZ(z: Double, y: Double) = z - Light[2] * y / Light[1]

    /** Soft shadows on the table, cast away from the light. */
    private fun Canvas.drawShadows(v: View3, s: PendulumSim, beamAlpha: Double) {
        shadowPath.reset()
        val ring = Array(10) { DoubleArray(3) }
        for (k in 0 until COUNT) {
            val bx = s.bobX(k, s.x[k])
            val by = s.bobY(k, s.x[k])
            val cx = shadowX(bx, by)
            val cz = shadowZ(rowZ(k), by)
            val r = BOB_R * 1.25
            for (i in 0 until 10) {
                val a = i / 10.0 * PI * 2
                ring[i][0] = cx + cos(a) * r
                ring[i][1] = 0.001
                ring[i][2] = cz + sin(a) * r
            }
            shadowPath.reset()
            polygon(shadowPath, v, ring)
            drawPath(shadowPath, bobShadow)
        }
        // The posts and the beam.
        wideShadow.color = rgba(0.02, 0.01, 0.0, 0.32)
        for (side in intArrayOf(-1, 1)) {
            val z = side * POST_Z
            val tx = shadowX(0.0, BEAM_Y)
            val tz = shadowZ(z, BEAM_Y)
            shadowPath.reset()
            polygon(
                shadowPath, v,
                arrayOf(
                    doubleArrayOf(-0.25, 0.001, z), doubleArrayOf(0.25, 0.001, z),
                    doubleArrayOf(tx + 0.25, 0.001, tz), doubleArrayOf(tx - 0.25, 0.001, tz),
                ),
            )
            drawPath(shadowPath, wideShadow)
        }
        val bx = shadowX(0.0, BEAM_Y)
        val z0 = shadowZ(-POST_Z, BEAM_Y)
        val z1 = shadowZ(POST_Z, BEAM_Y)
        wideShadow.color = rgba(0.02, 0.01, 0.0, 0.32 * beamAlpha)
        shadowPath.reset()
        polygon(
            shadowPath, v,
            arrayOf(
                doubleArrayOf(bx - 0.3, 0.001, z0), doubleArrayOf(bx + 0.3, 0.001, z0),
                doubleArrayOf(bx + 0.3, 0.001, z1), doubleArrayOf(bx - 0.3, 0.001, z1),
            ),
        )
        drawPath(shadowPath, wideShadow)
    }

    /** The whole scene for camera `v`; `topness` (0…1) fades the beam for the view from above. */
    fun Canvas.drawScene(v: View3, s: PendulumSim, width: Float, height: Float, topness: Double) {
        // The studio: a dark warm wall with a pool of light behind the frame.
        fill.color = 0xFF000000.toInt()
        fill.shader = LinearGradient(
            0f, 0f, 0f, height,
            intArrayOf(rgba(0.05, 0.055, 0.065, 1.0), rgba(0.085, 0.075, 0.07, 1.0), rgba(0.03, 0.025, 0.022, 1.0)),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        drawRect(0f, 0f, width, height, fill)
        v.project(0.0, TARGET_Y, 0.0)
        fill.shader = RadialGradient(
            v.px.toFloat(), v.py.toFloat(), max(0.01f, height * 0.55f),
            rgba(0.32, 0.24, 0.16, 0.5), rgba(0.32, 0.24, 0.16, 0.0), Shader.TileMode.CLAMP,
        )
        drawRect(0f, 0f, width, height, fill)

        // The table: walnut, lit in a pool under the frame.
        path.reset()
        polygon(
            path, v,
            arrayOf(
                doubleArrayOf(-TABLE_X, 0.0, -TABLE_Z), doubleArrayOf(TABLE_X, 0.0, -TABLE_Z),
                doubleArrayOf(TABLE_X, 0.0, TABLE_Z), doubleArrayOf(-TABLE_X, 0.0, TABLE_Z),
            ),
        )
        v.project(0.0, 0.0, 0.0)
        fill.shader = RadialGradient(
            v.px.toFloat(), v.py.toFloat(), max(0.01f, (v.f * 15 / v.pd).toFloat()),
            intArrayOf(rgba(0.3, 0.19, 0.11, 1.0), rgba(0.17, 0.1, 0.055, 1.0), rgba(0.06, 0.035, 0.02, 1.0)),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        drawPath(path, fill)
        fill.shader = null
        // Long grain lines across the table.
        val grain = rgba(0.05, 0.025, 0.01, 0.18)
        for (i in -8..8) {
            val x = i * 1.05 + sin(i * 3.7) * 0.2
            segment(v, x, 0.0, -TABLE_Z, x + sin(i * 1.3) * 0.3, 0.0, TABLE_Z, grain, 0.8f)
        }
        val beamAlpha = 1 - 0.78 * topness
        drawShadows(v, s, beamAlpha)

        // Everything standing on the table, back to front.
        val boxes = frameBoxes(beamAlpha)
        itemCount = 0
        for (i in boxes.indices) {
            for (j in Faces.indices) {
                val f = Faces[j]
                faceCorners(boxes[i], f)
                val toX = v.c[0] - fc[0]
                val toY = v.c[1] - fc[1]
                val toZ = v.c[2] - fc[2]
                if (toX * f[0] + toY * f[1] + toZ * f[2] <= 0) continue
                // The beam's faces sort as if at the beam's middle line nearest the camera.
                v.project(fc[0], fc[1], if (i == 0) max(-POST_Z, min(POST_Z, v.c[2])) else fc[2])
                add(v.pd, 0, i, j)
            }
        }
        var farthest = 0.0
        for (k in 0 until COUNT) {
            v.project(s.bobX(k, s.x[k]), s.bobY(k, s.x[k]), rowZ(k))
            farthest = max(farthest, v.pd)
            add(v.pd, 1, k, 0)
        }
        add(farthest + 0.05, 2, 0, 0)
        // Farthest first (insertion sort, stable like the React Native one).
        for (i in 0 until itemCount) order[i] = items[i]
        for (i in 1 until itemCount) {
            val it = order[i]!!
            var j = i - 1
            while (j >= 0 && order[j]!!.depth < it.depth) {
                order[j + 1] = order[j]
                j -= 1
            }
            order[j + 1] = it
        }
        val stringAlpha = 1 - 0.55 * topness
        for (i in 0 until itemCount) {
            val it = order[i]!!
            when (it.kind) {
                0 -> drawFace(v, boxes[it.index], Faces[it.face])
                1 -> drawBob(v, s, it.index, stringAlpha)
                else -> drawComb(v, s)
            }
        }
    }

    private fun add(depth: Double, kind: Int, index: Int, face: Int) {
        val it = items[itemCount++]
        it.depth = depth
        it.kind = kind
        it.index = index
        it.face = face
    }
}
