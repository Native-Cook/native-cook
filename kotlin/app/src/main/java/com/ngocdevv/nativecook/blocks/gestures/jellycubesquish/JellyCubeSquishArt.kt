package com.ngocdevv.nativecook.blocks.gestures.jellycubesquish

import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import com.ngocdevv.nativecook.blocks.gestures.jellycubesquish.JellyMath.FACE_COUNT
import com.ngocdevv.nativecook.blocks.gestures.jellycubesquish.JellyMath.HALF
import com.ngocdevv.nativecook.blocks.gestures.jellycubesquish.JellyMath.N
import com.ngocdevv.nativecook.blocks.gestures.jellycubesquish.JellyMath.PER
import com.ngocdevv.nativecook.blocks.gestures.jellycubesquish.JellyMath.TO_EYE
import com.ngocdevv.nativecook.blocks.gestures.jellycubesquish.JellyMath.TO_LIGHT
import com.ngocdevv.nativecook.blocks.gestures.jellycubesquish.JellyMath.clamp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 The jelly's art. Baked once: the dessert bar backdrop (a blush wall, a marble counter), the ceramic
 plate and the cube's soft contact shadow (a sprite). Per frame: the shadow and coloured caustic on
 the plate, the cube's far faces, an inner glow and suspended bubbles, its near faces (colour-only
 meshes lit per vertex: diffuse, a denser core, a Fresnel rim) and an additive specular mesh, then
 the demo finger and the flavour's name.

 Android 10+ draws the meshes with Canvas.drawVertices; below it each face is drawn as a coarser
 grid of flat-coloured quads.
 */

internal class JellyGeo(val w: Float, val h: Float, top: Float, bottom: Float) {
    val ctaTop = h - max(bottom, 16f) - 8 - 56
    val chipsTop = ctaTop - 16 - 44
    val nameBase = chipsTop - 22
    val cx = w / 2
    // The cube's foot sits so the cube centres between the header and the name.
    val cy = top + 112 + (nameBase - 70 - (top + 112)) * 0.66f

    fun project(x: Double, y: Double, z: Double, out: DoubleArray) = JellyMath.project(cx.toDouble(), cy.toDouble(), x, y, z, out)

    fun planeMatrix(x: Double, y: Double, z: Double, m: Matrix, s: JellyScratch) {
        project(x, y, z, s.a)
        project(x + 1, y, z, s.b)
        project(x, y + 1, z, s.c)
        val v = s.mv
        v[0] = (s.b[0] - s.a[0]).toFloat()
        v[1] = (s.c[0] - s.a[0]).toFloat()
        v[2] = s.a[0].toFloat()
        v[3] = (s.b[1] - s.a[1]).toFloat()
        v[4] = (s.c[1] - s.a[1]).toFloat()
        v[5] = s.a[1].toFloat()
        v[6] = 0f
        v[7] = 0f
        v[8] = 1f
        m.setValues(v)
    }
}

internal class JellyFonts {
    val name = jellyFont(false, 22f, 700)
    val price = jellyFont(false, 22f, 600)
}

private const val TOTAL = PER * FACE_COUNT

/** Per-frame buffers, reused (main thread). */
internal class JellyScratch {
    val a = DoubleArray(3)
    val b = DoubleArray(3)
    val c = DoubleArray(3)
    val p = DoubleArray(3)
    val mv = FloatArray(9)
    val matrix = Matrix()
    val world = DoubleArray(TOTAL * 3)
    val screen = FloatArray(TOTAL * 2)
    val normals = DoubleArray(PER * 3)
    val rgb = DoubleArray(3)
    val mid = DoubleArray(3)
    val nearPos = FloatArray(TOTAL * 2)
    val nearCol = IntArray(TOTAL)
    val specCol = IntArray(TOTAL)
    val nearIdx = ShortArray(FACE_COUNT * N * N * 6)
    val farPos = FloatArray(TOTAL * 2)
    val farCol = IntArray(TOTAL)
    val farIdx = ShortArray(FACE_COUNT * N * N * 6)
    val path = Path()
    val quad = Path()
    val rect = RectF()
}

private fun c(vararg v: Pair<Int, Float>) = jellyColors(*v)
private fun f(vararg v: Float) = v

private fun argbOf(r: Double, g: Double, b: Double, a: Double): Int =
    ((a.coerceIn(0.0, 1.0) * 255).roundToInt() shl 24) or
        ((r.coerceIn(0.0, 1.0) * 255).roundToInt() shl 16) or
        ((g.coerceIn(0.0, 1.0) * 255).roundToInt() shl 8) or
        (b.coerceIn(0.0, 1.0) * 255).roundToInt()

/**
 * A true multiply. PorterDuff MULTIPLY also multiplies alpha, so where the wash fades out it would
 * punch a black hole in the frame; BlendMode.MULTIPLY (API 29) is Skia's. Below it, a softer normal tint.
 */
private fun multiply(p: Paint): Paint {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) p.blendMode = BlendMode.MULTIPLY else p.alpha = p.alpha / 2
    return p
}

private fun additive(p: Paint): Paint {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) p.blendMode = BlendMode.PLUS else p.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    return p
}

// ——— Baked ———

internal fun jellyDrawBackdrop(canvas: Canvas, k: JellyKit, g: JellyGeo) {
    val s = JellyScratch()
    g.project(0.0, 0.0, 0.0, s.a)
    val horizon = s.a[1].toFloat() - 70
    canvas.drawRect(0f, 0f, g.w, horizon, k.linear(0f, 0f, 0f, horizon, c(0xf6e6e1 to 1f, 0xf1d9d3 to 1f, 0xe9cbc4 to 1f), f(0f, 0.6f, 1f)))
    canvas.drawRect(0f, 0f, g.w, horizon, k.radial(g.cx, horizon - 120, g.w * 0.8f, c(0xfff6f2 to 0.7f, 0xfff6f2 to 0f), f(0f, 1f)))
    // Marble counter: pale stone with soft grey veins, lit from the top.
    canvas.drawRect(0f, horizon, g.w, g.h, k.linear(0f, horizon, 0f, g.h, c(0xf3eeea to 1f, 0xe7e0db to 1f, 0xd6cdc7 to 1f), f(0f, 0.4f, 1f)))
    canvas.save()
    canvas.clipRect(0f, horizon, g.w, g.h)
    // Veins: long soft streaks (Skia's turbulence in the original), multiplied in.
    canvas.save()
    canvas.translate(0f, horizon)
    canvas.rotate(-8f)
    canvas.scale(5f, 6f)
    k.brushed(canvas, -g.w / 5f, 0f, g.w * 2 / 5f, (g.h - horizon) / 5f, 0.18f, 17, multiply = true)
    canvas.restore()
    canvas.restore()
    canvas.drawLine(0f, horizon, g.w, horizon, k.stroke(0xffffff, 1.2f, 0.8f))
    canvas.drawRect(0f, horizon, g.w, horizon + 24, k.linear(0f, horizon, 0f, horizon + 24, c(0x8a6a62 to 0.18f, 0x8a6a62 to 0f), null))
    k.grain(canvas, 0f, 0f, g.w, g.h, 0.08f, 9)
    // The plate: a shallow ceramic dish.
    canvas.save()
    g.planeMatrix(6.0, -10.0, 0.0, s.matrix, s)
    canvas.concat(s.matrix)
    canvas.drawCircle(0f, 0f, 140f, k.fill(0x6a4a44, 0.22f, 16f))
    canvas.restore()
    canvas.save()
    g.planeMatrix(0.0, 0.0, 0.0, s.matrix, s)
    canvas.concat(s.matrix)
    canvas.drawCircle(0f, 0f, 134f, k.radial(-30f, -40f, 170f, c(0xffffff to 1f, 0xf7f3f0 to 1f, 0xe2dad5 to 1f), f(0f, 0.6f, 1f)))
    canvas.drawCircle(0f, 0f, 112f, k.stroke(0xd7cec9, 2f, 0.7f))
    canvas.drawCircle(0f, 0f, 110f, k.radial(0f, 0f, 112f, c(0xfdfbfa to 1f, 0xf1ece8 to 1f, 0xe6dfda to 1f), f(0f, 0.85f, 1f)))
    canvas.drawCircle(0f, 0f, 131f, k.stroke(0xffffff, 1.5f, 0.9f))
    canvas.restore()
}

/** The cube's contact shadow in plate coordinates: a rounded square of half size HALF + 6, blur 12. */
internal const val JELLY_SHADOW_HALF = 78f
internal const val JELLY_SHADOW_PAD = 40f

internal fun jellyDrawShadow(canvas: Canvas, k: JellyKit) {
    val m = JELLY_SHADOW_PAD
    canvas.drawRoundRect(m, m, m + JELLY_SHADOW_HALF * 2, m + JELLY_SHADOW_HALF * 2, 34f, 34f, k.fill(0x5a2a2a, 1f, 12f))
}

// ——— Per frame ———

/** The jelly's colour at a rest point while a new flavour fills from the plate up, into out. */
private fun colourAt(col: JellyColour, x: Double, y: Double, z: Double, out: DoubleArray) {
    val a = JellyFlavours[col.from]
    val b = JellyFlavours[col.to]
    if (col.from == col.to || col.fill <= 0) {
        out[0] = a.r; out[1] = a.g; out[2] = a.b
        return
    }
    if (col.fill >= 1) {
        out[0] = b.r; out[1] = b.g; out[2] = b.b
        return
    }
    val front = -24 + col.fill * (2 * HALF + 48)
    val wave = 7 * sin(x / 15 + col.fill * 9) + 5 * sin(y / 19 - col.fill * 6)
    val kk = clamp((front + wave - z + 12) / 24, 0.0, 1.0)
    val t = kk * kk * (3 - 2 * kk)
    out[0] = a.r + (b.r - a.r) * t
    out[1] = a.g + (b.g - a.g) * t
    out[2] = a.b + (b.b - a.b) * t
}

private val WIN = doubleArrayOf(-0.62, -0.2, -0.1, 0.05, -0.18, 0.62, -0.66, 0.4)

/** Draws the whole cube (shadow, far faces, inside, near faces, gloss). */
internal fun jellyDrawCube(
    canvas: Canvas,
    k: JellyKit,
    s: JellyScratch,
    g: JellyGeo,
    rest: DoubleArray,
    shadow: android.graphics.Bitmap,
    bitmapPaint: Paint,
    d: JellyDeform,
    col: JellyColour,
    time: Double,
) {
    for (i in 0 until TOTAL) {
        JellyMath.deform(d, rest[i * 3], rest[i * 3 + 1], rest[i * 3 + 2], s.p)
        s.world[i * 3] = s.p[0]
        s.world[i * 3 + 1] = s.p[1]
        s.world[i * 3 + 2] = s.p[2]
        g.project(s.p[0], s.p[1], s.p[2], s.a)
        s.screen[i * 2] = s.a[0].toFloat()
        s.screen[i * 2 + 1] = s.a[1].toFloat()
    }
    colourAt(col, 0.0, 0.0, HALF, s.mid)
    val mr = s.mid[0]
    val mg = s.mid[1]
    val mb = s.mid[2]
    // Contact shadow and the light that came through the jelly, on the plate.
    val bulge = (1 + max(0.0, d.sq) * 0.6).toFloat()
    canvas.save()
    g.planeMatrix(10.0, -8.0, 0.0, s.matrix, s)
    canvas.concat(s.matrix)
    val hs = (HALF.toFloat() * bulge + 6) / JELLY_SHADOW_HALF
    canvas.scale(hs, hs)
    val sh = JELLY_SHADOW_HALF + JELLY_SHADOW_PAD
    s.rect.set(-sh, -sh, sh, sh)
    bitmapPaint.alpha = (0.32f * 255).roundToInt()
    canvas.drawBitmap(shadow, null, s.rect, bitmapPaint)
    bitmapPaint.alpha = 255
    canvas.restore()
    canvas.save()
    g.planeMatrix(58.0, 70.0, 0.0, s.matrix, s)
    canvas.concat(s.matrix)
    val caustic = k.fill(0)
    caustic.shader = RadialGradient(
        0f, 0f, 96f,
        intArrayOf(argbOf(mr, mg, mb, 0.55), argbOf(mr, mg, mb, 0.18), argbOf(mr, mg, mb, 0.0)),
        floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
    )
    canvas.drawCircle(0f, 0f, 96f, multiply(caustic))
    canvas.drawCircle(-10f, -10f, 40f, k.radial(-10f, -10f, 40f, c(0xffffff to 0.35f, 0xffffff to 0f), null))
    canvas.restore()

    // Per-vertex shading, faces split by which way they face.
    var nearN = 0
    var nearI = 0
    var farN = 0
    var farI = 0
    val hx = TO_LIGHT[0] + TO_EYE[0]
    val hy = TO_LIGHT[1] + TO_EYE[1]
    val hz = TO_LIGHT[2] + TO_EYE[2]
    val hl = sqrt(hx * hx + hy * hy + hz * hz)
    val w = s.world
    for (face in 0 until FACE_COUNT) {
        val base = face * PER
        var facing = 0.0
        for (j in 0..N) for (i in 0..N) {
            val a = base + j * (N + 1) + min(i + 1, N)
            val b = base + j * (N + 1) + max(i - 1, 0)
            val cc = base + min(j + 1, N) * (N + 1) + i
            val e = base + max(j - 1, 0) * (N + 1) + i
            val ux = w[a * 3] - w[b * 3]
            val uy = w[a * 3 + 1] - w[b * 3 + 1]
            val uz = w[a * 3 + 2] - w[b * 3 + 2]
            val vx = w[cc * 3] - w[e * 3]
            val vy = w[cc * 3 + 1] - w[e * 3 + 1]
            val vz = w[cc * 3 + 2] - w[e * 3 + 2]
            var nx = uy * vz - uz * vy
            var ny = uz * vx - ux * vz
            var nz = ux * vy - uy * vx
            val l = sqrt(nx * nx + ny * ny + nz * nz).takeIf { it != 0.0 } ?: 1.0
            nx /= l
            ny /= l
            nz /= l
            // Point outward: away from the cube's axis.
            val kk = base + j * (N + 1) + i
            if (nx * w[kk * 3] + ny * w[kk * 3 + 1] + nz * (w[kk * 3 + 2] - HALF) < 0) {
                nx = -nx
                ny = -ny
                nz = -nz
            }
            val ni = (j * (N + 1) + i) * 3
            s.normals[ni] = nx
            s.normals[ni + 1] = ny
            s.normals[ni + 2] = nz
            facing += nx * TO_EYE[0] + ny * TO_EYE[1] + nz * TO_EYE[2]
        }
        val near = facing > 0
        val offset = if (near) nearN else farN
        for (j in 0..N) for (i in 0..N) {
            val kk = base + j * (N + 1) + i
            val ni = (j * (N + 1) + i) * 3
            val nx = s.normals[ni]
            val ny = s.normals[ni + 1]
            val nz = s.normals[ni + 2]
            colourAt(col, rest[kk * 3], rest[kk * 3 + 1], rest[kk * 3 + 2], s.rgb)
            val diff = max(0.0, nx * TO_LIGHT[0] + ny * TO_LIGHT[1] + nz * TO_LIGHT[2])
            val view = max(0.0, nx * TO_EYE[0] + ny * TO_EYE[1] + nz * TO_EYE[2])
            if (near) {
                // Looking straight in, the jelly is thinner and glows; at grazing angles it is deep and
                // saturated, with a white Fresnel sheen on top.
                val fres = (1 - view).pow(3)
                val lit = 0.42 + 0.38 * diff + 0.3 * view
                s.nearPos[nearN * 2] = s.screen[kk * 2]
                s.nearPos[nearN * 2 + 1] = s.screen[kk * 2 + 1]
                s.nearCol[nearN] = argbOf(
                    min(1.0, s.rgb[0] * lit + 0.42 * fres + 0.03),
                    min(1.0, s.rgb[1] * lit + 0.42 * fres + 0.03),
                    min(1.0, s.rgb[2] * lit + 0.42 * fres + 0.04),
                    0.58 + 0.34 * fres + 0.08 * (1 - diff),
                )
                val spec = max(0.0, (nx * hx + ny * hy + nz * hz) / hl).pow(70) * 1.4
                s.specCol[nearN] = argbOf(spec, spec, spec, min(1.0, spec))
                nearN++
            } else {
                s.farPos[farN * 2] = s.screen[kk * 2]
                s.farPos[farN * 2 + 1] = s.screen[kk * 2 + 1]
                s.farCol[farN] = argbOf(s.rgb[0] * 0.7, s.rgb[1] * 0.62, s.rgb[2] * 0.66, 0.5)
                farN++
            }
        }
        val idx = if (near) s.nearIdx else s.farIdx
        var n = if (near) nearI else farI
        for (j in 0 until N) for (i in 0 until N) {
            val a = offset + j * (N + 1) + i
            idx[n++] = a.toShort(); idx[n++] = (a + 1).toShort(); idx[n++] = (a + N + 1).toShort()
            idx[n++] = (a + 1).toShort(); idx[n++] = (a + N + 2).toShort(); idx[n++] = (a + N + 1).toShort()
        }
        if (near) nearI = n else farI = n
    }
    val useMesh = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && canvas.isHardwareAccelerated
    // Android blends vertex colours with the paint's colour (modulate): white keeps them as they are.
    if (farI > 0) drawMesh(canvas, k, s, useMesh, s.farPos, s.farCol, s.farIdx, farN, farI, false)
    // Inside: a soft lighter core and a few suspended bubbles drifting on whole-loop sines.
    JellyMath.deform(d, 0.0, 0.0, HALF, s.p)
    g.project(s.p[0], s.p[1], s.p[2], s.a)
    val coreX = s.a[0].toFloat()
    val coreY = s.a[1].toFloat() - 10
    val coreR = (HALF * 1.1 * s.a[2]).toFloat()
    val glow = k.fill(0)
    glow.shader = RadialGradient(
        coreX, coreY, coreR,
        intArrayOf(argbOf(min(1.0, mr + 0.3), min(1.0, mg + 0.3), min(1.0, mb + 0.3), 0.55), argbOf(mr, mg, mb, 0.0)),
        null, Shader.TileMode.CLAMP,
    )
    canvas.drawCircle(coreX, coreY, coreR, glow)
    for (bb in 0 until 9) {
        val hx2 = sin(bb * 12.9898 + 4.1) * 43758.5453
        val r1 = hx2 - floor(hx2)
        val hy2 = sin(bb * 78.233 + 1.7) * 43758.5453
        val r2 = hy2 - floor(hy2)
        val hz2 = sin(bb * 39.425 + 9.2) * 43758.5453
        val r3 = hz2 - floor(hz2)
        val drift = 3 * sin(2 * PI * (1 + bb % 2) * time / LOOP + bb)
        JellyMath.deform(d, (r1 - 0.5) * HALF * 1.2, (r2 - 0.5) * HALF * 1.2, 26 + r3 * (2 * HALF - 52) + drift, s.p)
        g.project(s.p[0], s.p[1], s.p[2], s.a)
        val bx = s.a[0].toFloat()
        val by = s.a[1].toFloat()
        val r = ((1.6 + 3.4 * ((bb * 7) % 5) / 4) * s.a[2]).toFloat()
        canvas.drawCircle(bx, by, r, k.fill(0xffffff, 0.16f))
        canvas.drawCircle(bx, by, r, k.stroke(0xffffff, 0.8f, 0.5f))
        canvas.drawCircle(bx - r * 0.35f, by - r * 0.35f, r * 0.3f, k.fill(0xffffff, 0.8f))
    }
    if (nearI > 0) {
        drawMesh(canvas, k, s, useMesh, s.nearPos, s.nearCol, s.nearIdx, nearN, nearI, false)
        drawMesh(canvas, k, s, useMesh, s.nearPos, s.specCol, s.nearIdx, nearN, nearI, true)
    }
    // A window reflected in the top face and a streak down the front: they ride the deformation.
    val win = s.path
    win.reset()
    for (i in 0 until 4) {
        JellyMath.deform(d, WIN[i * 2] * HALF * 0.8, WIN[i * 2 + 1] * HALF * 0.8, 2 * HALF, s.p)
        g.project(s.p[0], s.p[1], s.p[2], s.a)
        if (i == 0) win.moveTo(s.a[0].toFloat(), s.a[1].toFloat()) else win.lineTo(s.a[0].toFloat(), s.a[1].toFloat())
    }
    win.close()
    canvas.drawPath(win, k.fill(0xffffff, 0.32f, 3f))
    val streak = s.path
    streak.reset()
    for (i in 0..8) {
        JellyMath.deform(d, -HALF * 0.62, -HALF, HALF * (0.32 + i / 8.0 * 1.4), s.p)
        g.project(s.p[0], s.p[1], s.p[2], s.a)
        if (i == 0) streak.moveTo(s.a[0].toFloat(), s.a[1].toFloat()) else streak.lineTo(s.a[0].toFloat(), s.a[1].toFloat())
    }
    canvas.drawPath(streak, k.stroke(0xffffff, 6f, 0.22f, 3f))
    canvas.drawPath(streak, k.stroke(0xffffff, 1.2f, 0.35f))
}

/** One colour-only mesh: drawVertices on Android 10+, else flat quads over every other grid cell. */
private fun drawMesh(canvas: Canvas, k: JellyKit, s: JellyScratch, useMesh: Boolean, pos: FloatArray, col: IntArray, idx: ShortArray, verts: Int, count: Int, add: Boolean) {
    val p = k.fill(0xffffff)
    if (add) additive(p)
    if (useMesh) {
        canvas.drawVertices(Canvas.VertexMode.TRIANGLES, verts * 2, pos, 0, null, 0, col, 0, idx, 0, count, p)
        return
    }
    // Fallback: each face's grid in 2 × 2 cells, a flat average colour per cell.
    val faces = verts / PER
    for (fc in 0 until faces) {
        val base = fc * PER
        var j = 0
        while (j < N) {
            var i = 0
            while (i < N) {
                val a = base + j * (N + 1) + i
                val b = a + 2
                val c2 = a + 2 * (N + 1)
                val d2 = c2 + 2
                s.quad.reset()
                s.quad.moveTo(pos[a * 2], pos[a * 2 + 1])
                s.quad.lineTo(pos[b * 2], pos[b * 2 + 1])
                s.quad.lineTo(pos[d2 * 2], pos[d2 * 2 + 1])
                s.quad.lineTo(pos[c2 * 2], pos[c2 * 2 + 1])
                s.quad.close()
                p.color = averageColour(col[a], col[b], col[c2], col[d2])
                canvas.drawPath(s.quad, p)
                i += 2
            }
            j += 2
        }
    }
}

private fun averageColour(a: Int, b: Int, c: Int, d: Int): Int {
    fun ch(v: Int, sh: Int) = (v ushr sh) and 0xff
    var out = 0
    for (sh in intArrayOf(24, 16, 8, 0)) out = out or (((ch(a, sh) + ch(b, sh) + ch(c, sh) + ch(d, sh)) / 4) shl sh)
    return out
}

/** The demo finger: a soft translucent fingertip. */
internal fun jellyDrawFinger(canvas: Canvas, k: JellyKit, s: JellyScratch, g: JellyGeo, f: DoubleArray) {
    val a = f[3].toFloat()
    if (a <= 0.01f) return
    g.project(f[0], f[1], f[2], s.a)
    val x = s.a[0].toFloat()
    val y = s.a[1].toFloat()
    canvas.drawCircle(x, y + 3, 22f, k.fill(0x3a1a1a, 0.18f * a, 6f))
    canvas.drawCircle(x, y, 20f, k.fill(0xffffff, 0.42f * a))
    canvas.drawCircle(x, y, 20f, k.stroke(0xffffff, 1.5f, 0.85f * a))
}

private val NAMES = Array(JellyFlavours.size) { "${JellyFlavours[it].name} Wobble" }
private const val PRICE = "\$6.50"

/** "Strawberry Wobble" rolling to the next flavour's name, and the price. */
internal fun jellyDrawName(canvas: Canvas, k: JellyKit, g: JellyGeo, ft: JellyFonts, col: JellyColour) {
    val roll = col.roll.toFloat()
    canvas.save()
    canvas.clipRect(0f, g.nameBase - 30, g.w, g.nameBase + 10)
    for (j in 0 until 2) {
        val a = 1 - min(1f, abs(j - roll))
        if (a <= 0.01f) continue
        k.text(canvas, NAMES[if (j == 0) col.from else col.to], 24f, g.nameBase + (j - roll) * 28, ft.name, 0x3a1f22, a)
    }
    canvas.restore()
    k.text(canvas, PRICE, g.w - 24 - ft.price.measureText(PRICE), g.nameBase, ft.price, 0x3a1f22)
}
