package com.ngocdevv.nativecook.blocks.navigation.courierzoomtracker

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import com.ngocdevv.nativecook.blocks.navigation.courierzoomtracker.CourierMotion as M
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The 3D layer of one frame as a triangle mesh: every quad is two triangles with one colour, in painter's order. */
internal class CourierMesh {
    var verts = FloatArray(4096)
    var colors = IntArray(2048)
    var n = 0 // vertices

    fun clear() {
        n = 0
    }

    fun quad(color: Int, x0: Double, y0: Double, x1: Double, y1: Double, x2: Double, y2: Double, x3: Double, y3: Double) {
        if (n + 6 > colors.size) {
            colors = colors.copyOf(colors.size * 2)
            verts = verts.copyOf(verts.size * 2)
        }
        var v = n * 2
        fun put(x: Double, y: Double) {
            verts[v++] = x.toFloat()
            verts[v++] = y.toFloat()
        }
        put(x0, y0); put(x1, y1); put(x2, y2)
        put(x0, y0); put(x2, y2); put(x3, y3)
        for (k in 0 until 6) colors[n + k] = color
        n += 6
    }
}

/** Draws the map for one frame. Holds the static map's RenderNode and the per-frame scratch buffers. */
internal class CourierMapRenderer {
    private val city = CourierCity
    private var node: Any? = null
    private val matrix = Matrix()
    private val rot = Matrix()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val meshPaint = Paint()
    private val travelledPath = Path()
    private val shadowPath = Path()
    private val tri = Path()
    private val mesh = CourierMesh()
    private val r4 = DoubleArray(4)

    // building selection scratch
    private var idx = IntArray(300)
    private var key = DoubleArray(300)
    private var cor = DoubleArray(300 * 8)
    private var order = arrayOfNulls<Int>(300)

    private fun staticMap(c: Canvas) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && c.isHardwareAccelerated) {
            val rn = (node as? RenderNode) ?: RenderNode("courier-map").also { n ->
                val l = floor(CourierCity.MAP_L).toInt()
                val t = floor(CourierCity.MAP_T).toInt()
                n.setPosition(l, t, CourierCity.MAP_R.roundToInt(), CourierCity.MAP_B.roundToInt())
                val rc = n.beginRecording()
                rc.translate(-l.toFloat(), -t.toFloat())
                city.drawStatic(rc)
                n.endRecording()
                node = n
            }
            c.drawRenderNode(rn)
        } else {
            city.drawStatic(c)
        }
    }

    fun release() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) (node as? RenderNode)?.discardDisplayList()
        node = null
    }

    /** The whole map for one frame, in dp. */
    fun draw(c: Canvas, cam: CourierCam, lay: CourierLay, vanD: Double, vanA: Double, travEnd: Double, travA: Double, doorPulse: Double) {
        val zoom = cam.s
        matrix.reset()
        matrix.postTranslate(-cam.cx.toFloat(), -cam.cy.toFloat())
        matrix.postScale(zoom.toFloat(), zoom.toFloat())
        rot.setValues(floatArrayOf(cam.cosB.toFloat(), -cam.sinB.toFloat(), 0f, cam.sinB.toFloat(), cam.cosB.toFloat(), 0f, 0f, 0f, 1f))
        matrix.postConcat(rot)
        matrix.postScale(1f, cam.squash.toFloat())
        matrix.postTranslate(cam.fx.toFloat(), cam.fy.toFloat())

        c.save()
        c.concat(matrix)
        staticMap(c)

        // the route, and the travelled part greyed
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = 0xFFFFFFFF.toInt()
        paint.strokeWidth = (10 / zoom).toFloat()
        c.drawPath(city.routePath, paint)
        paint.color = ROUTE_BLUE
        paint.strokeWidth = (6.5 / zoom).toFloat()
        c.drawPath(city.routePath, paint)
        if (travEnd > 0.0005 && travA > 0.001) {
            travelledPath.rewind()
            val len = city.routeMeasure.length
            city.routeMeasure.getSegment(0f, (travEnd * len).toFloat(), travelledPath, true)
            paint.color = withAlpha(TRAVELLED, M.clamp01(travA))
            c.drawPath(travelledPath, paint)
        }
        // a ring on the pavement at the door once close, and one wide pulse on arrival
        val ringX = CourierCity.DOOR_X.toFloat()
        val ringY = (CourierCity.HOME_Y1 + 2.2).toFloat()
        val ringOpacity = M.smooth01((zoom - 1.6) / 1.2)
        paint.strokeWidth = (1.6 / zoom).toFloat()
        if (ringOpacity > 0.001) {
            paint.color = withAlpha(ROUTE_BLUE, ringOpacity)
            c.drawCircle(ringX, ringY, 3.2f, paint)
        }
        if (doorPulse > 0) {
            paint.color = withAlpha(ROUTE_BLUE, 1 - doorPulse)
            c.drawCircle(ringX, ringY, (3.2 + 10 * doorPulse).toFloat(), paint)
        }
        c.restore()

        // the raised buildings and the van, in screen space
        M.routeAt(vanD, r4)
        val rx = r4[0]
        val ry = r4[1]
        val hx = r4[2]
        val hy = r4[3]
        val va = M.clamp01(vanA)
        val model = vanModelK(cam.s)
        cityFrame(cam, lay, rx, ry, hx, hy, va, model)
        if (va * model > 0.01) {
            val al = doubleArrayOf(-3.3, 3.2, 3.2, -3.3)
            val ac = doubleArrayOf(-1.25, -1.25, 1.25, 1.25)
            shadowPath.rewind()
            for (k in 0 until 4) {
                val wx = rx + hx * al[k] - hy * ac[k] + 0.5
                val wy = ry + hy * al[k] + hx * ac[k] + 0.4
                val px = cam.sx(wx, wy).toFloat()
                val py = cam.sy(wx, wy).toFloat()
                if (k == 0) shadowPath.moveTo(px, py) else shadowPath.lineTo(px, py)
            }
            shadowPath.close()
            paint.reset()
            paint.isAntiAlias = true
            paint.color = argb(40.0, 44.0, 52.0, 0.28 * va * model)
            paint.maskFilter = BlurMaskFilter(blurRadius(max(1.5, 0.9 * cam.s)), BlurMaskFilter.Blur.NORMAL)
            c.drawPath(shadowPath, paint)
            paint.maskFilter = null
        }
        if (mesh.n > 0) {
            c.drawVertices(
                Canvas.VertexMode.TRIANGLES, mesh.n * 2, mesh.verts, 0, null, 0, mesh.colors, 0, null, 0, 0, meshPaint,
            )
        }
        // the location dot while the van is far: a white disc, a blue core and a heading tip
        val dot = va * (1 - model)
        if (dot > 0.01) {
            val cx = cam.sx(rx, ry).toFloat()
            val cy = cam.sy(rx, ry).toFloat()
            paint.reset()
            paint.isAntiAlias = true
            paint.color = argb(20.0, 30.0, 60.0, 0.25 * dot)
            paint.maskFilter = BlurMaskFilter(blurRadius(2.0), BlurMaskFilter.Blur.NORMAL)
            c.drawCircle(cx, cy + 1.5f, 12f, paint)
            paint.maskFilter = null
            paint.color = withAlpha(0xFFFFFFFF.toInt(), dot)
            c.drawCircle(cx, cy, 11.5f, paint)
            paint.color = withAlpha(ROUTE_BLUE, dot)
            c.drawCircle(cx, cy, 8f, paint)
            val tx = cam.sx(rx + hx * 50, ry + hy * 50) - cx
            val ty = cam.sy(rx + hx * 50, ry + hy * 50) - cy
            val dl = if (hypot(tx, ty) == 0.0) 1.0 else hypot(tx, ty)
            val ux = (tx / dl).toFloat()
            val uy = (ty / dl).toFloat()
            tri.rewind()
            tri.moveTo(cx + ux * 6, cy + uy * 6)
            tri.lineTo(cx - ux * 3 - uy * 4, cy - uy * 3 + ux * 4)
            tri.lineTo(cx - ux * 1.2f, cy - uy * 1.2f)
            tri.lineTo(cx - ux * 3 + uy * 4, cy - uy * 3 - ux * 4)
            tri.close()
            paint.color = withAlpha(0xFFFFFFFF.toInt(), dot)
            c.drawPath(tri, paint)
        }
    }

    /** SwiftUI's blur radius is about a Gaussian sigma; Android's BlurMaskFilter takes radius = (sigma - 0.5) / 0.57735. */
    private fun blurRadius(sigma: Double) = max(0.5, (sigma - 0.5) / 0.57735).toFloat()

    // the 3D layer

    /** A box from its four clockwise screen corners (c[off..off+8]), raised by (ex, ey) per metre from z0 to z1: the
     * walls that face the viewer (shaded by a light from the upper left, with windows when `unit` > 0), then the top. */
    private fun box(
        c: DoubleArray, off: Int, ex: Double, ey: Double, z0: Double, z1: Double,
        wr: Double, wg: Double, wb: Double, tr: Double, tg: Double, tb: Double, a: Double, unit: Double,
    ) {
        for (e in 0 until 4) {
            val ax = c[off + e * 2]
            val ay = c[off + e * 2 + 1]
            val bx = c[off + ((e + 1) % 4) * 2]
            val by = c[off + ((e + 1) % 4) * 2 + 1]
            val dx = bx - ax
            val dy = by - ay
            val dl = if (hypot(dx, dy) == 0.0) 1.0 else hypot(dx, dy)
            val nx = dy / dl
            val ny = -dx / dl
            if (nx * M.extrudeX - ny >= 0) continue
            val lit = 0.92 + 0.08 * (nx * -0.6 + ny * -0.8)
            mesh.quad(
                argb(wr * lit, wg * lit, wb * lit, a),
                ax + ex * z0, ay + ey * z0, bx + ex * z0, by + ey * z0, bx + ex * z1, by + ey * z1, ax + ex * z1, ay + ey * z1,
            )
            if (unit > 0) {
                val lenM = dl / unit
                val cols = min(floor(lenM / 3.2).toInt(), 10)
                val floors = min(floor((z1 - z0) / FLOOR_H).toInt(), 6)
                if (cols < 1 || floors < 1) continue
                val g = argb(wr * lit * 0.58 + 40, wg * lit * 0.62 + 46, wb * lit * 0.68 + 58, a)
                val hw = 0.62 / lenM
                for (f in 0 until floors) {
                    val za = z0 + f * FLOOR_H + 1.0
                    val zb = za + 1.45
                    for (w in 0 until cols) {
                        val m = (w + 0.5) / cols
                        val u0 = m - hw
                        val u1 = m + hw
                        mesh.quad(
                            g,
                            ax + dx * u0 + ex * za, ay + dy * u0 + ey * za,
                            ax + dx * u1 + ex * za, ay + dy * u1 + ey * za,
                            ax + dx * u1 + ex * zb, ay + dy * u1 + ey * zb,
                            ax + dx * u0 + ex * zb, ay + dy * u0 + ey * zb,
                        )
                    }
                }
            }
        }
        mesh.quad(
            argb(tr, tg, tb, a),
            c[off] + ex * z1, c[off + 1] + ey * z1, c[off + 2] + ex * z1, c[off + 3] + ey * z1,
            c[off + 4] + ex * z1, c[off + 5] + ey * z1, c[off + 6] + ex * z1, c[off + 7] + ey * z1,
        )
    }

    private val oc = DoubleArray(8)
    private val cabC = DoubleArray(8)
    private val bodyC = DoubleArray(8)

    private fun orientedCorners(
        out: DoubleArray, off: Int, x: Double, y: Double, hx: Double, hy: Double, back: Double, front: Double, half: Double, cam: CourierCam,
    ) {
        val rx = -hy
        val ry = hx
        for (k in 0 until 4) {
            val al = if (k == 0 || k == 3) back else front
            val ac = if (k < 2) -half else half
            val wx = x + hx * al + rx * ac
            val wy = y + hy * al + ry * ac
            out[off + k * 2] = cam.sx(wx, wy)
            out[off + k * 2 + 1] = cam.sy(wx, wy)
        }
        val cross = (out[off + 2] - out[off]) * (out[off + 5] - out[off + 1]) - (out[off + 3] - out[off + 1]) * (out[off + 4] - out[off])
        if (cross < 0) {
            val tx = out[off + 2]; val ty = out[off + 3]
            out[off + 2] = out[off + 6]; out[off + 3] = out[off + 7]
            out[off + 6] = tx; out[off + 7] = ty
        }
    }

    private fun van(x: Double, y: Double, hx: Double, hy: Double, cam: CourierCam, ex: Double, ey: Double, a: Double) {
        orientedCorners(bodyC, 0, x, y, hx, hy, -3.1, 1.3, 1.1, cam)
        orientedCorners(cabC, 0, x, y, hx, hy, 1.3, 3.0, 1.05, cam)
        val kb = (bodyC[0] + bodyC[4]) * M.extrudeX - (bodyC[1] + bodyC[5])
        val kc = (cabC[0] + cabC[4]) * M.extrudeX - (cabC[1] + cabC[5])
        if (kc > kb) {
            box(cabC, 0, ex, ey, 0.35, 2.05, 38.0, 88.0, 222.0, 64.0, 118.0, 255.0, a, -1.0)
            box(bodyC, 0, ex, ey, 0.35, 2.7, 38.0, 88.0, 222.0, 64.0, 118.0, 255.0, a, -1.0)
        } else {
            box(bodyC, 0, ex, ey, 0.35, 2.7, 38.0, 88.0, 222.0, 64.0, 118.0, 255.0, a, -1.0)
            box(cabC, 0, ex, ey, 0.35, 2.05, 38.0, 88.0, 222.0, 64.0, 118.0, 255.0, a, -1.0)
        }
        orientedCorners(oc, 0, x, y, hx, hy, 1.75, 2.85, 0.92, cam)
        quadRaised(argb(28.0, 36.0, 52.0, a), oc, ex * 2.06, ey * 2.06)
        orientedCorners(oc, 0, x, y, hx, hy, -2.8, 1.0, 0.22, cam)
        quadRaised(argb(255.0, 255.0, 255.0, a), oc, ex * 2.71, ey * 2.71)
    }

    private fun quadRaised(color: Int, c: DoubleArray, dx: Double, dy: Double) {
        mesh.quad(color, c[0] + dx, c[1] + dy, c[2] + dx, c[3] + dy, c[4] + dx, c[5] + dy, c[6] + dx, c[7] + dy)
    }

    private fun ensure(n: Int) {
        if (n <= idx.size) return
        val s = n * 2
        idx = idx.copyOf(s)
        key = key.copyOf(s)
        cor = cor.copyOf(s * 8)
        order = order.copyOf(s)
    }

    /** The 3D layer for one frame, into [mesh]. */
    private fun cityFrame(cam: CourierCam, lay: CourierLay, vx: Double, vy: Double, vhx: Double, vhy: Double, vA: Double, model: Double) {
        mesh.clear()
        val h0 = cam.heightScale
        val hs = h0 * M.smooth01((h0 - MIN_HEIGHT_SCALE) / 0.35)
        val showVan = vA * model > 0.003
        if (h0 < MIN_HEIGHT_SCALE && !showVan) return
        val ex = M.extrudeX * hs
        val ey = -hs
        val w = lay.w
        val bottom = min(lay.panel + 60, lay.h)
        var x0 = 1e9
        var y0 = 1e9
        var x1 = -1e9
        var y1 = -1e9
        val sxs = doubleArrayOf(-30.0, w + 30, w + 30, -30.0)
        val sys = doubleArrayOf(-30.0, -30.0, bottom + 50 * hs, bottom + 50 * hs)
        for (k in 0 until 4) {
            val wx = cam.worldX(sxs[k], sys[k])
            val wy = cam.worldY(sxs[k], sys[k])
            x0 = min(x0, wx); x1 = max(x1, wx); y0 = min(y0, wy); y1 = max(y1, wy)
        }
        val ia = max(CourierCity.I0, floor(x0 / CourierCity.BX).toInt())
        val ib = min(CourierCity.I1 - 1, floor(x1 / CourierCity.BX).toInt())
        val ja = max(CourierCity.J0, floor(y0 / CourierCity.BY).toInt())
        val jb = min(CourierCity.J1 - 1, floor(y1 / CourierCity.BY).toInt())
        val bArr = city.buildings
        val start = city.buildingStart
        var count = 0
        if (h0 > MIN_HEIGHT_SCALE && ia <= ib && ja <= jb) {
            outer@ for (i in ia..ib) {
                for (j in ja..jb) {
                    val bi = (i - CourierCity.I0) * CourierCity.NJT + (j - CourierCity.J0)
                    for (b in start[bi] until start[bi + 1]) {
                        if (count >= MAX_BUILDINGS) break@outer
                        ensure(count + 2)
                        val o = count * 8
                        val bx0 = bArr[b * 5]
                        val by0 = bArr[b * 5 + 1]
                        val bx1 = bArr[b * 5 + 2]
                        val by1 = bArr[b * 5 + 3]
                        cor[o] = cam.sx(bx0, by0); cor[o + 1] = cam.sy(bx0, by0)
                        cor[o + 2] = cam.sx(bx1, by0); cor[o + 3] = cam.sy(bx1, by0)
                        cor[o + 4] = cam.sx(bx1, by1); cor[o + 5] = cam.sy(bx1, by1)
                        cor[o + 6] = cam.sx(bx0, by1); cor[o + 7] = cam.sy(bx0, by1)
                        val h = bArr[b * 5 + 4]
                        var mnx = 1e9
                        var mxx = -1e9
                        var mny = 1e9
                        var mxy = -1e9
                        for (p in 0 until 4) {
                            val px = cor[o + p * 2]
                            val py = cor[o + p * 2 + 1]
                            mnx = min(mnx, px); mxx = max(mxx, px); mny = min(mny, py); mxy = max(mxy, py)
                        }
                        if (mxx + ex * h < -2 || mnx > w + 2 || mxy < -2 || mny + ey * h > bottom) continue
                        idx[count] = b
                        key[count] = (cor[o] + cor[o + 4]) / 2 * M.extrudeX - (cor[o + 1] + cor[o + 5]) / 2
                        count++
                    }
                }
            }
        }
        val n = count
        if (showVan) {
            ensure(count + 2)
            orientedCorners(cor, count * 8, vx, vy, vhx, vhy, -3.1, 3.0, 1.1, cam)
            val o = count * 8
            idx[count] = -1
            key[count] = (cor[o] + cor[o + 4]) / 2 * M.extrudeX - (cor[o + 1] + cor[o + 5]) / 2
            count++
        }
        // painter's order: the furthest along the raise direction first (stable)
        for (k in 0 until count) order[k] = k
        java.util.Arrays.sort(order, 0, count) { a, b ->
            val ka = key[a!!]
            val kb = key[b!!]
            if (ka != kb) kb.compareTo(ka) else a.compareTo(b!!)
        }
        val unit = if (hs > 1.9 && n <= 28) cam.s else -1.0
        for (q in 0 until count) {
            val k = order[q]!!
            val b = idx[k]
            if (b < 0) {
                van(vx, vy, vhx, vhy, cam, ex, ey, vA * model)
                continue
            }
            val h = bArr[b * 5 + 4]
            val v = ((b * 7919) % 97) / 97.0
            val wr: Double; val wg: Double; val wb: Double
            val tr: Double; val tg: Double; val tb: Double
            if (b == city.homeIndex) {
                wr = 238.0; wg = 196.0; wb = 160.0; tr = 250.0; tg = 222.0; tb = 196.0
            } else if (h > 24) {
                wr = 208 - 6 * v; wg = 214 - 6 * v; wb = 224 - 6 * v; tr = 234 - 4 * v; tg = 238 - 4 * v; tb = 243 - 4 * v
            } else if (h <= 12 && bArr[b * 5 + 2] - bArr[b * 5] < 14) {
                val f = (b * 3) % 4
                wr = when (f) { 0 -> 240.0; 1 -> 232.0; 2 -> 220.0; else -> 236.0 }
                wg = when (f) { 0 -> 232.0; 1 -> 218.0; 2 -> 228.0; else -> 226.0 }
                wb = when (f) { 0 -> 220.0; 1 -> 204.0; 2 -> 232.0; else -> 218.0 }
                tr = 246 - 3 * v; tg = 243 - 3 * v; tb = 238 - 2 * v
            } else {
                wr = 229 - 6 * v; wg = 223 - 6 * v; wb = 213 - 3 * v
                tr = 250 - 4 * v; tg = 248 - 4 * v; tb = 243 - 2 * v
            }
            val o = k * 8
            box(cor, o, ex, ey, 0.0, h, wr, wg, wb, tr, tg, tb, 1.0, unit)
            if (b == city.homeIndex) {
                // the front door on the south wall (corner 2 → corner 3)
                val dx0 = bArr[b * 5]
                val dx1 = bArr[b * 5 + 2]
                val fa = (CourierCity.DOOR_X - 0.75 - dx0) / (dx1 - dx0)
                val fb = (CourierCity.DOOR_X + 0.75 - dx0) / (dx1 - dx0)
                val sx = cor[o + 6] - cor[o + 4]
                val sy = cor[o + 7] - cor[o + 5]
                val ba = 1 - fa
                val bb = 1 - fb
                val q0x = cor[o + 4] + sx * bb
                val q0y = cor[o + 5] + sy * bb
                val q1x = cor[o + 4] + sx * ba
                val q1y = cor[o + 5] + sy * ba
                mesh.quad(argb(44.0, 78.0, 74.0, 1.0), q0x, q0y, q1x, q1y, q1x + ex * 2.3, q1y + ey * 2.3, q0x + ex * 2.3, q0y + ey * 2.3)
            }
        }
    }

    companion object {
        private const val MAX_BUILDINGS = 240
        private const val MIN_HEIGHT_SCALE = 0.22
        private const val FLOOR_H = 3.2
        const val ROUTE_BLUE = 0xFF2F6BFF.toInt()
        private const val TRAVELLED = 0xFFA9B0BD.toInt()

        /** How much the van is drawn as a 3D model rather than the location dot. */
        fun vanModelK(s: Double) = M.smooth01((s - 1.5) / 1.1)

        fun argb(r: Double, g: Double, b: Double, a: Double): Int {
            fun c(v: Double) = min(max(v, 0.0), 255.0).roundToInt()
            val ia = (min(max(a, 0.0), 1.0) * 255).roundToInt()
            return (ia shl 24) or (c(r) shl 16) or (c(g) shl 8) or c(b)
        }

        fun withAlpha(color: Int, a: Double): Int {
            val base = (color ushr 24) and 0xFF
            val ia = (base * min(max(a, 0.0), 1.0)).roundToInt()
            return (ia shl 24) or (color and 0xFFFFFF)
        }

        /** The film's ghost finger: a soft shadow, a translucent white body, a thin rim; pressed, it sits lower. */
        fun finger(c: Canvas, p: Paint, x: Double, y: Double, vis: Double, press: Double) {
            if (vis < 0.004) return
            val lift = 1 - press
            val r = (21 * (1 + 0.12 * lift)).toFloat()
            val scx = x.toFloat()
            val scy = (y + 3 + 4 * lift).toFloat()
            p.reset()
            p.isAntiAlias = true
            p.shader = RadialGradient(
                scx, scy, r * 1.5f,
                intArrayOf(withAlpha(0xFF000000.toInt(), 0.22 * vis), withAlpha(0xFF000000.toInt(), 0.22 * vis), 0x00000000),
                floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP,
            )
            c.drawCircle(scx, scy, r * 1.5f, p)
            p.shader = null
            p.color = withAlpha(0xFFFFFFFF.toInt(), 0.6 * vis)
            c.drawCircle(x.toFloat(), y.toFloat(), r, p)
            p.color = withAlpha(0xFFFFFFFF.toInt(), 0.35 * vis)
            c.drawCircle(x.toFloat(), y.toFloat(), r * 0.6f, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1.2f
            p.color = argb(150.0, 160.0, 180.0, 0.9 * vis)
            c.drawCircle(x.toFloat(), y.toFloat(), r - 0.6f, p)
            p.style = Paint.Style.FILL
        }
    }
}
