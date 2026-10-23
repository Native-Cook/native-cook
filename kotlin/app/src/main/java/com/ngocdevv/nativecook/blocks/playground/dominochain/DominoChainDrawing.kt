package com.ngocdevv.nativecook.blocks.playground.dominochain

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// The felt, the React Native SkSL unchanged: AGSL is the same dialect (API 33+). Coordinates are dp:
// the canvas is scaled by the density before it is drawn.
private const val FELT_AGSL = """
uniform float2 size;
uniform float2 lamp;
uniform float reach;
uniform float rail;

float hash(float2 p) {
  float3 q = fract(float3(p.x, p.y, p.x) * 0.1031);
  q += dot(q, q.yzx + 33.33);
  return fract((q.x + q.y) * q.z);
}

float noise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  f = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), f.x), mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), f.x), f.y);
}

half4 main(float2 p) {
  float2 d = (p - lamp) / reach * float2(1.0, 1.45);
  float pool = exp(-dot(d, d) * 1.25);
  float tooth = hash(floor(p * 1.6)) - 0.5;
  float mottle = noise(p * 0.045) - 0.5;
  float nap = noise(float2(p.x * 0.5, p.y * 0.12)) - 0.5;
  float3 base = float3(0.06, 0.25, 0.18);
  float3 lit = float3(0.13, 0.43, 0.31);
  float3 c = mix(base * 0.55, lit, pool);
  c *= 1.0 + tooth * 0.12 + mottle * 0.16 + nap * 0.07;
  float far = clamp((p.y - rail) / (size.y * 0.35), 0.0, 1.0);
  c *= 0.62 + 0.38 * far;
  return half4(c, 1.0);
}
"""

// The light: a lamp above and behind the table, to the left. Shadows fall towards the viewer.
private val LightLen = sqrt(0.42 * 0.42 + 1 + 0.25)
private val LX = -0.42 / LightLen
private val LY = 1 / LightLen
private val LZ = 0.5 / LightLen
private const val SHADOW_ALPHA = 0.42f
private const val SHADOW_BLUR = 3.2f
// The tiles: ivory with ink pips, lit by the lamp.
private const val IVORY_R = 0.95
private const val IVORY_G = 0.92
private const val IVORY_B = 0.85
private const val PIP_RADIUS = 2.1f

/** The pips of a domino half, as (u, v) in a −1…1 grid. */
private val PipLayouts = arrayOf(
    floatArrayOf(),
    floatArrayOf(0f, 0f),
    floatArrayOf(-1f, -1f, 1f, 1f),
    floatArrayOf(-1f, -1f, 0f, 0f, 1f, 1f),
    floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f),
    floatArrayOf(-1f, -1f, 1f, -1f, 0f, 0f, -1f, 1f, 1f, 1f),
    floatArrayOf(-1f, -1f, 1f, -1f, -1f, 0f, 1f, 0f, -1f, 1f, 1f, 1f),
)

// Faces as corner indices (counter-clockwise seen from outside): front, back, top, left, right.
private val Faces = arrayOf(intArrayOf(4, 5, 7, 6), intArrayOf(0, 2, 3, 1), intArrayOf(2, 6, 7, 3), intArrayOf(0, 4, 6, 2), intArrayOf(1, 3, 7, 5))

/** Sets a blur on [p] like Skia's MakeBlur(sigma): Android takes a radius, sigma = 0.57735·r + 0.5. */
private fun blur(sigma: Float) = BlurMaskFilter(max(0.1f, (sigma - 0.5f) / 0.57735f), BlurMaskFilter.Blur.NORMAL)

private fun argb(a: Double, r: Int, g: Int, b: Int) = Color.argb((a.coerceIn(0.0, 1.0) * 255).roundToInt(), r, g, b)

/** Draws one frame of the table, reusing its paths and paints. */
internal class DominoDrawing {
    private val felt = Paint()
    private var feltShader: Any? = null
    // The runtime shader on Android 13+, or the gradients and noise tile if it is missing or fails to compile.
    private val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && makeFeltSafely()
    private val fallback = if (modern) null else FeltFallback()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = argb(SHADOW_ALPHA.toDouble(), 4, 16, 10)
        maskFilter = blur(SHADOW_BLUR)
    }
    private val contactPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = argb(0.5, 2, 10, 6)
        maskFilter = blur(1.2f)
    }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.6f
        strokeJoin = Paint.Join.ROUND
        color = argb(0.55, 70, 58, 40)
    }
    private val pip = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1C1C1E.toInt() }
    private val groove = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.9f
        strokeCap = Paint.Cap.ROUND
        color = argb(0.75, 40, 36, 30)
    }
    private val chalk = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        maskFilter = blur(1.4f)
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.4f
        maskFilter = blur(1.5f)
    }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val shadows = Path()
    private val contacts = Path()
    private val face = Path()
    private val trace = Path()
    private val matrix = Matrix()
    private val values = FloatArray(9)
    private val oval = RectF()
    private val q = DoubleArray(24)
    private val sp = DoubleArray(24)
    private val p = DoubleArray(3)
    private val a = DoubleArray(3)
    private val b = DoubleArray(3)
    private val e = DoubleArray(3)
    private val f = DoubleArray(3)
    private val xs = DoubleArray(8)
    private val ys = DoubleArray(8)
    private val pop = Pop()
    private var orderRun = IntArray(2 * MAX_DOMINOES)
    private var orderI = IntArray(2 * MAX_DOMINOES)
    private var depth = DoubleArray(2 * MAX_DOMINOES)
    private var idx = IntArray(2 * MAX_DOMINOES)
    private var roomKey = Float.NaN
    private val room = Paint()
    private val front = Paint()
    private val top = Paint()
    private val lip = Paint()

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun makeFeltSafely(): Boolean = try {
        makeFelt()
        true
    } catch (e: RuntimeException) {
        feltShader = null
        false
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun makeFelt() {
        val s = RuntimeShader(FELT_AGSL)
        feltShader = s
        felt.shader = s
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun updateFelt(width: Float, height: Float, lampX: Float, lampY: Float, railY: Float) {
        val s = feltShader as RuntimeShader
        s.setFloatUniform("size", width, height)
        s.setFloatUniform("lamp", lampX, lampY)
        s.setFloatUniform("reach", width * 0.95f)
        s.setFloatUniform("rail", railY)
        felt.shader = s
    }

    fun draw(canvas: Canvas, engine: DominoEngine) {
        val layout = engine.layout
        drawTable(canvas, layout)

        // The trace of a path being drawn, fading once the finger lifts.
        val time = engine.liveRun.time
        val traceAlpha = if (engine.drawing) 1.0 else max(0.0, 1 - (time - engine.traceEnd) / 0.6)
        val path = engine.path
        if (traceAlpha > 0 && path.size > 1) {
            trace.rewind()
            for (k in 0 until path.size) {
                layout.project(path.xs[k], 0.0, path.zs[k], p)
                if (k == 0) trace.moveTo(p[0].toFloat(), p[1].toFloat()) else trace.lineTo(p[0].toFloat(), p[1].toFloat())
            }
            chalk.color = argb(0.32 * traceAlpha, 235, 245, 225)
            canvas.drawPath(trace, chalk)
        }

        // Every domino of both runs, far to near.
        var n = 0
        for (k in 0 until 2) {
            val c = engine.runs[k]
            for (i in 0 until c.count) {
                val s = sin(c.tilt[i])
                layout.project(c.x[i] + c.hx[i] * (TILE_H / 2) * s, 0.0, c.z[i] + c.hz[i] * (TILE_H / 2) * s, p)
                orderRun[n] = k
                orderI[n] = i
                depth[n] = p[2]
                n += 1
            }
        }
        // Insertion sort by depth, farthest first (stable, as in RN).
        for (k in 0 until n) {
            var j = k
            idx[k] = k
            while (j > 0 && depth[idx[j - 1]] < depth[k]) {
                idx[j] = idx[j - 1]
                j -= 1
            }
            idx[j] = k
        }

        // Shadows first, all in one soft path, then the tiles.
        shadows.rewind()
        contacts.rewind()
        for (k in 0 until n) addShadow(engine, engine.runs[orderRun[idx[k]]], orderI[idx[k]])
        canvas.drawPath(shadows, shadowPaint)
        canvas.drawPath(contacts, contactPaint)
        for (k in 0 until n) drawDomino(canvas, engine, engine.runs[orderRun[idx[k]]], orderI[idx[k]])

        // A soft glow under the first tile while the run waits to be tipped.
        val live = engine.liveRun
        if (live.count > 1 && !live.anyFallen && !engine.drawing && live.sink[0] == Double.POSITIVE_INFINITY) {
            val settle = min(1.0, max(0.0, (live.time - live.born[live.count - 1] - 0.4) / 0.5))
            if (settle > 0) {
                val pulse = 0.5 + 0.5 * sin(live.time * 3.2)
                layout.project(live.x[0], 0.0, live.z[0], p)
                val r = (17 + 4 * pulse).toFloat()
                ring.color = argb((0.22 + 0.3 * pulse) * settle, 250, 236, 190)
                oval.set(p[0].toFloat() - r, p[1].toFloat() - r * 0.62f, p[0].toFloat() + r, p[1].toFloat() + r * 0.62f)
                canvas.drawOval(oval, ring)
            }
        }

        // The autoPlay finger.
        val g = engine.ghost
        if (g > 0.01) {
            fill.shader = null
            fill.color = argb(0.16 * g, 255, 255, 255)
            canvas.drawCircle(engine.fingerX.toFloat(), engine.fingerY.toFloat(), 17f, fill)
            stroke.strokeWidth = 1.5f
            stroke.color = argb(0.5 * g, 255, 255, 255)
            canvas.drawCircle(engine.fingerX.toFloat(), engine.fingerY.toFloat(), 17f, stroke)
        }
    }

    /** The room, the far rail and the felt. */
    private fun drawTable(canvas: Canvas, layout: DominoLayout) {
        val width = layout.width.toFloat()
        val height = layout.height.toFloat()
        val railY = layout.railY.toFloat()
        layout.project(0.0, RAIL_HEIGHT, layout.farZ, a)
        layout.project(0.0, RAIL_HEIGHT, layout.farZ + RAIL_DEPTH, b)
        val ay = a[1].toFloat()
        val by = b[1].toFloat()
        if (roomKey != railY + height * 3) {
            roomKey = railY + height * 3
            room.shader = LinearGradient(0f, 0f, 0f, railY, 0xFF07090A.toInt(), 0xFF141A17.toInt(), Shader.TileMode.CLAMP)
            front.shader = LinearGradient(0f, ay, 0f, railY, 0xFF6B4426.toInt(), 0xFF2A170B.toInt(), Shader.TileMode.CLAMP)
            top.shader = LinearGradient(0f, by, 0f, ay, 0xFF3D2514.toInt(), 0xFF8A5A33.toInt(), Shader.TileMode.CLAMP)
            lip.shader = LinearGradient(0f, railY, 0f, railY + 22, argb(0.55, 0, 0, 0), 0, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, width, railY + 2, room)
        layout.project(SPIRAL_X - 30, 0.0, SPIRAL_Z + 40, p)
        if (modern) {
            updateFelt(width, height, p[0].toFloat(), p[1].toFloat(), railY)
        } else {
            fallback?.update(felt, width, height, p[0].toFloat(), p[1].toFloat(), railY)
        }
        canvas.drawRect(0f, railY, width, height, felt)
        // The rail: its top and its front, with a lit edge between them.
        canvas.drawRect(0f, ay, width, railY + 0.5f, front)
        canvas.drawRect(0f, by, width, ay, top)
        stroke.strokeWidth = 1f
        stroke.color = argb(0.45, 255, 214, 160)
        canvas.drawLine(0f, ay, width, ay, stroke)
        canvas.drawRect(0f, railY, width, railY + 22, lip)
    }

    /** The 3D corners of a domino into q: index = a·4 + b·2 + c over (back/front, bottom/top, left/right). */
    private fun corners(engine: DominoEngine, c: DominoRun, i: Int): Double {
        c.pop(i, pop)
        val sc = if (engine.reduced) 1.0 else pop.scale
        val lift = if (engine.reduced) 0.0 else pop.lift
        val fx = c.hx[i]
        val fz = c.hz[i]
        // Lateral axis: the heading turned a quarter.
        val lx = -fz
        val lz = fx
        val ct = cos(c.tilt[i])
        val st = sin(c.tilt[i])
        val th = TILE_T * sc
        val hh = TILE_H * sc
        val ww = TILE_W * sc
        // The pivot: the front bottom edge.
        val px = c.x[i] + fx * (th / 2)
        val pz = c.z[i] + fz * (th / 2)
        for (fa in 0 until 2) {
            for (fb in 0 until 2) {
                for (k in 0 until 2) {
                    val la = if (fa == 0) -th else 0.0
                    val lb = if (fb == 0) 0.0 else hh
                    val lc = (if (k == 0) -0.5 else 0.5) * ww
                    // Tip forward about the pivot.
                    val ra = la * ct + lb * st
                    val rb = -la * st + lb * ct
                    val m = (fa * 4 + fb * 2 + k) * 3
                    q[m] = px + fx * ra + lx * lc
                    q[m + 1] = rb + lift
                    q[m + 2] = pz + fz * ra + lz * lc
                }
            }
        }
        return pop.alpha
    }

    /** Adds a domino's shadow (its corners cast along the light onto the table) and its contact shade. */
    private fun addShadow(engine: DominoEngine, c: DominoRun, i: Int) {
        val layout = engine.layout
        val alpha = corners(engine, c, i)
        if (alpha < 0.3) return
        // Cast each corner along the light onto the table, then wrap the eight in a hull.
        for (k in 0 until 8) {
            val y = max(0.0, q[k * 3 + 1])
            val x = q[k * 3] - (LX / LY) * y
            val z = q[k * 3 + 2] - (LZ / LY) * y
            layout.project(x, 0.0, z, p)
            xs[k] = p[0]
            ys[k] = p[1]
        }
        // Gift wrap.
        var start = 0
        for (k in 1 until 8) if (xs[k] < xs[start] || (xs[k] == xs[start] && ys[k] < ys[start])) start = k
        var cur = start
        var first = true
        for (guard in 0 until 9) {
            if (first) shadows.moveTo(xs[cur].toFloat(), ys[cur].toFloat()) else shadows.lineTo(xs[cur].toFloat(), ys[cur].toFloat())
            first = false
            var next = (cur + 1) % 8
            for (k in 0 until 8) {
                val cross = (xs[next] - xs[cur]) * (ys[k] - ys[cur]) - (ys[next] - ys[cur]) * (xs[k] - xs[cur])
                if (cross < 0) next = k
            }
            cur = next
            if (cur == start) break
        }
        shadows.close()
        // A tight dark line where the base meets the felt.
        if (q[1] < 1.5) {
            layout.project(q[0], 0.0, q[2], a)
            layout.project(q[3], 0.0, q[5], b)
            layout.project(q[12], 0.0, q[14], e)
            layout.project(q[15], 0.0, q[17], f)
            contacts.moveTo(a[0].toFloat(), a[1].toFloat())
            contacts.lineTo(b[0].toFloat(), b[1].toFloat())
            contacts.lineTo(f[0].toFloat(), f[1].toFloat())
            contacts.lineTo(e[0].toFloat(), e[1].toFloat())
            contacts.close()
        }
    }

    /** One domino: its visible faces, lit by the lamp, the pips on its front. */
    private fun drawDomino(canvas: Canvas, engine: DominoEngine, c: DominoRun, i: Int) {
        val layout = engine.layout
        val alpha = corners(engine, c, i)
        if (alpha <= 0.01) return
        for (k in 0 until 8) {
            layout.project(q[k * 3], q[k * 3 + 1], q[k * 3 + 2], p)
            sp[k * 3] = p[0]
            sp[k * 3 + 1] = p[1]
            sp[k * 3 + 2] = p[2]
        }
        val fx = c.hx[i]
        val fz = c.hz[i]
        val lx = -fz
        val lz = fx
        val ct = cos(c.tilt[i])
        val st = sin(c.tilt[i])
        if (alpha < 1) canvas.saveLayerAlpha(null, (alpha * 255).roundToInt())
        for (fi in 0 until 5) {
            val fc = Faces[fi]
            val ia = fc[0]
            val ib = fc[1]
            val ic = fc[2]
            val id = fc[3]
            // The face's outward normal: forward, back, up after the tip, then the two sides.
            val nx: Double
            val ny: Double
            val nz: Double
            when (fi) {
                0 -> { nx = fx * ct; ny = -st; nz = fz * ct }
                1 -> { nx = -fx * ct; ny = st; nz = -fz * ct }
                2 -> { nx = fx * st; ny = ct; nz = fz * st }
                3 -> { nx = -lx; ny = 0.0; nz = -lz }
                else -> { nx = lx; ny = 0.0; nz = lz }
            }
            // Visible when it faces the camera.
            val mx = (q[ia * 3] + q[ic * 3]) / 2
            val my = (q[ia * 3 + 1] + q[ic * 3 + 1]) / 2
            val mz = (q[ia * 3 + 2] + q[ic * 3 + 2]) / 2
            if (nx * (0 - mx) + ny * (CamY - my) + nz * (CamZ - mz) <= 0) continue
            val light = max(0.0, nx * LX + ny * LY + nz * LZ)
            val shade = 0.52 + 0.5 * light
            val r = (min(1.0, IVORY_R * shade) * 255).roundToInt()
            val g = (min(1.0, IVORY_G * shade) * 255).roundToInt()
            val bl = (min(1.0, IVORY_B * shade * 0.98) * 255).roundToInt()
            fill.shader = null
            fill.color = Color.rgb(r, g, bl)
            face.rewind()
            face.moveTo(sp[ia * 3].toFloat(), sp[ia * 3 + 1].toFloat())
            face.lineTo(sp[ib * 3].toFloat(), sp[ib * 3 + 1].toFloat())
            face.lineTo(sp[ic * 3].toFloat(), sp[ic * 3 + 1].toFloat())
            face.lineTo(sp[id * 3].toFloat(), sp[id * 3 + 1].toFloat())
            face.close()
            canvas.drawPath(face, fill)
            canvas.drawPath(face, edge)
            // The big faces: a groove across the middle, and pips on the front.
            if (fi <= 1) {
                // Face frame: u across (corner a → the corner beside it on the bottom), v up.
                val ox = sp[ia * 3]
                val oy = sp[ia * 3 + 1]
                val bottom = if (fi == 0) 5 else 1
                val topK = if (fi == 0) 6 else 2
                values[0] = ((sp[bottom * 3] - ox) / TILE_W).toFloat()
                values[1] = ((sp[topK * 3] - ox) / TILE_H).toFloat()
                values[2] = ox.toFloat()
                values[3] = ((sp[bottom * 3 + 1] - oy) / TILE_W).toFloat()
                values[4] = ((sp[topK * 3 + 1] - oy) / TILE_H).toFloat()
                values[5] = oy.toFloat()
                values[6] = 0f
                values[7] = 0f
                values[8] = 1f
                matrix.setValues(values)
                canvas.save()
                canvas.concat(matrix)
                canvas.drawLine(3f, (TILE_H / 2).toFloat(), (TILE_W - 3).toFloat(), (TILE_H / 2).toFloat(), groove)
                if (fi == 0) {
                    for (h in 0 until 2) {
                        val layoutPips = PipLayouts[if (h == 0) c.pipsA[i] else c.pipsB[i]]
                        val v0 = (if (h == 0) TILE_H * 0.25 else TILE_H * 0.75).toFloat()
                        var k = 0
                        while (k < layoutPips.size) {
                            canvas.drawCircle((TILE_W / 2).toFloat() + layoutPips[k] * 5.2f, v0 + layoutPips[k + 1] * 5.2f, PIP_RADIUS, pip)
                            k += 2
                        }
                    }
                }
                canvas.restore()
            }
        }
        if (alpha < 1) canvas.restore()
    }
}

// ─── Below Android 13 ────────────────────────────────────────────────────────────────────────────
// No runtime shaders: the lamp's pool is an elliptical radial gradient sampled from the shader's own
// falloff, the darkening towards the rail a linear gradient, and the felt's tooth and mottling a tile
// computed once on the CPU with the shader's own hash and noise.

private fun fract(v: Double) = v - floor(v)

private fun hash(x: Double, y: Double): Double {
    var qx = fract(x * 0.1031)
    var qy = fract(y * 0.1031)
    var qz = fract(x * 0.1031)
    val d = qx * (qy + 33.33) + qy * (qz + 33.33) + qz * (qx + 33.33)
    qx += d
    qy += d
    qz += d
    return fract((qx + qy) * qz)
}

private fun noise(x: Double, y: Double): Double {
    val ix = floor(x)
    val iy = floor(y)
    var fx = x - ix
    var fy = y - iy
    fx = fx * fx * (3 - 2 * fx)
    fy = fy * fy * (3 - 2 * fy)
    val top = hash(ix, iy) + (hash(ix + 1, iy) - hash(ix, iy)) * fx
    val bottom = hash(ix, iy + 1) + (hash(ix + 1, iy + 1) - hash(ix, iy + 1)) * fx
    return top + (bottom - top) * fy
}

private class FeltFallback {
    private val tile = 192
    private val grain: Shader = run {
        // Two texels per dp; the tooth's cells are 1/1.6 dp, close enough at this size.
        val bitmap = Bitmap.createBitmap(tile * 2, tile * 2, Bitmap.Config.ARGB_8888)
        val px = IntArray(bitmap.width * bitmap.height)
        for (j in 0 until bitmap.height) {
            for (i in 0 until bitmap.width) {
                val x = i * 0.5
                val y = j * 0.5
                val tooth = hash(floor(x * 1.6), floor(y * 1.6)) - 0.5
                val mottle = noise(x * 0.045, y * 0.045) - 0.5
                val nap = noise(x * 0.5, y * 0.12) - 0.5
                val v = (tooth * 0.12 + mottle * 0.16 + nap * 0.07) * 0.3
                val al = (min(1.0, kotlin.math.abs(v)) * 255).toInt()
                px[j * bitmap.width + i] = if (v >= 0) Color.argb(al, 255, 255, 255) else Color.argb(al, 0, 0, 0)
            }
        }
        bitmap.setPixels(px, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
            setLocalMatrix(Matrix().apply { setScale(0.5f, 0.5f) })
        }
    }
    private var key = Float.NaN

    fun update(paint: Paint, width: Float, height: Float, lampX: Float, lampY: Float, railY: Float) {
        val k = lampX + lampY * 3 + width * 7 + height * 11 + railY * 13
        if (k == key) return
        key = k
        val reach = width * 0.95f
        val steps = 16
        val colors = IntArray(steps)
        val stops = FloatArray(steps)
        for (s in 0 until steps) {
            val t = s.toDouble() / (steps - 1)
            // The pool at a distance of t·reach·1.6 from the lamp.
            val dd = t * 1.6
            val pool = exp(-dd * dd * 1.25)
            val r = (0.06 * 0.55 + (0.13 - 0.06 * 0.55) * pool)
            val g = (0.25 * 0.55 + (0.43 - 0.25 * 0.55) * pool)
            val b = (0.18 * 0.55 + (0.31 - 0.18 * 0.55) * pool)
            colors[s] = Color.rgb((r * 255).roundToInt(), (g * 255).roundToInt(), (b * 255).roundToInt())
            stops[s] = t.toFloat()
        }
        val pool = RadialGradient(0f, 0f, reach * 1.6f, colors, stops, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(
                Matrix().apply {
                    setScale(1f, 1 / 1.45f)
                    postTranslate(lampX, lampY)
                },
            )
        }
        // Darker towards the far rail: × (0.62 + 0.38·far) as black over the felt.
        val far = LinearGradient(0f, railY, 0f, railY + height * 0.35f, argb(0.38, 0, 0, 0), 0, Shader.TileMode.CLAMP)
        paint.shader = ComposeShader(ComposeShader(pool, far, PorterDuff.Mode.SRC_OVER), grain, PorterDuff.Mode.SRC_OVER)
    }
}
