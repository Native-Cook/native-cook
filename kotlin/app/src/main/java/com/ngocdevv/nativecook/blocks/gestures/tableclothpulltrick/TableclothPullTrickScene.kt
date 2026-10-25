package com.ngocdevv.nativecook.blocks.gestures.tableclothpulltrick

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.os.Build
import com.ngocdevv.nativecook.blocks.gestures.tableclothpulltrick.ClothMotion.CLOTH_LEN
import com.ngocdevv.nativecook.blocks.gestures.tableclothpulltrick.ClothMotion.DEPTH
import com.ngocdevv.nativecook.blocks.gestures.tableclothpulltrick.ClothMotion.DROP
import com.ngocdevv.nativecook.blocks.gestures.tableclothpulltrick.ClothMotion.clamp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 The supper table in 3D and the cloth as a mesh. World: x right, y away from the viewer across the
 table (front edge y = 0, back edge y = DEPTH), z up (table top z = 0). The camera looks down at
 46° from the front.

 The cloth is a grid in its own material coordinates: u across (CLOTH_W wide), s along its length
 from the back edge (0) to the hem (CLOTH_LEN). Pulled by d, a material point sits at p = s + d
 along its path: on the table while p ≤ DEPTH, then over the front edge and down (streaming toward
 the viewer by `fling`). A travelling ripple lifts the part on the table. The linen is a bitmap
 baked once; a second, colour-only mesh shades the folds and the hanging part.

 Android 10+ draws both meshes with Canvas.drawVertices; below it the cloth is drawn as a coarser
 grid of bitmap-shader triangles with a flat shade each.
 */

internal const val CLOTH_W = 460.0
private const val COLS = 40
private const val ROWS = 34
internal const val TEX_SCALE = 1.5f
private val ELEV = 46 * PI / 180
private val SIN_E = sin(ELEV)
private val COS_E = cos(ELEV)
private const val CAM_D = 1000.0
private const val TARGET_Y = DEPTH * 0.42
/** Overall zoom of the view (the table runs off both sides of the screen). */
private const val ZOOM = 1.2

internal class ClothGeo(val w: Float, val h: Float, top: Float, bottom: Float) {
    val panelTop = h - max(bottom, 12f) - 150
    private val frontY = panelTop - min(140f, h * 0.17f)
    val cx = w / 2
    val cy: Float
    val headerBottom = top + 112

    init {
        val out = DoubleArray(3)
        clothProject(0.0, 0.0, 0.0, 0.0, 0.0, out)
        cy = (frontY - out[1]).toFloat()
    }

    fun project(x: Double, y: Double, z: Double, out: DoubleArray) = clothProject(cx.toDouble(), cy.toDouble(), x, y, z, out)

    /** An affine matrix drawing the table plane at height z around (x, y) in world units. */
    fun planeMatrix(x: Double, y: Double, z: Double, m: Matrix, s: ClothScratch) {
        project(x, y, z, s.p0)
        project(x + 1, y, z, s.p1)
        project(x, y + 1, z, s.p2)
        val v = s.mv
        v[0] = (s.p1[0] - s.p0[0]).toFloat()
        v[1] = (s.p2[0] - s.p0[0]).toFloat()
        v[2] = s.p0[0].toFloat()
        v[3] = (s.p1[1] - s.p0[1]).toFloat()
        v[4] = (s.p2[1] - s.p0[1]).toFloat()
        v[5] = s.p0[1].toFloat()
        v[6] = 0f
        v[7] = 0f
        v[8] = 1f
        m.setValues(v)
    }
}

/** Screen point and perspective scale of a world point, into out. */
internal fun clothProject(cx: Double, cy: Double, x: Double, y: Double, z: Double, out: DoubleArray) {
    val ry = y - TARGET_Y
    val yc = ry * SIN_E + z * COS_E
    val s = ZOOM * CAM_D / (CAM_D + ry * COS_E - z * SIN_E)
    out[0] = cx + x * s
    out[1] = cy - yc * s
    out[2] = s
}

/** Scratch reused by one drawing thread. */
internal class ClothScratch {
    val p0 = DoubleArray(3)
    val p1 = DoubleArray(3)
    val p2 = DoubleArray(3)
    val pt = DoubleArray(5)
    val mv = FloatArray(9)
    val matrix = Matrix()
    val path = Path()
    val verts = FloatArray((COLS + 1) * (ROWS + 1) * 2)
    val colors = IntArray((COLS + 1) * (ROWS + 1))
    val fbVerts = FloatArray((FB_COLS + 1) * (FB_ROWS + 1) * 2)
    val fbDark = FloatArray((FB_COLS + 1) * (FB_ROWS + 1))
    val fbLight = FloatArray((FB_COLS + 1) * (FB_ROWS + 1))
    val src = FloatArray(6)
    val dst = FloatArray(6)
    val tri = Path()
    val local = Matrix()
}

private fun c(vararg v: Pair<Int, Float>) = clothColors(*v)
private fun f(vararg v: Float) = v

// ——— Baked ———

private fun worldQuad(g: ClothGeo, vararg pts: Double): Path {
    val out = DoubleArray(3)
    val p = Path()
    var i = 0
    while (i < pts.size) {
        g.project(pts[i], pts[i + 1], pts[i + 2], out)
        if (i == 0) p.moveTo(out[0].toFloat(), out[1].toFloat()) else p.lineTo(out[0].toFloat(), out[1].toFloat())
        i += 3
    }
    p.close()
    return p
}

/** Wall, the walnut table (top and apron), the floor. */
internal fun clothDrawRoom(canvas: Canvas, k: ClothKit, g: ClothGeo) {
    val wide = 900.0
    canvas.drawRect(0f, 0f, g.w, g.h, k.linear(0f, 0f, 0f, g.h, c(0x2a1d1a to 1f, 0x22171a to 1f, 0x140d0c to 1f), f(0f, 0.5f, 1f)))
    // Damask-ish wall: soft vertical panels and a warm pool of candlelight.
    for (i in 0 until 6) canvas.drawRect(i * g.w / 5 - 4, 0f, i * g.w / 5 - 2, g.h, k.fill(0, 0.12f))
    val o = DoubleArray(3)
    g.project(0.0, DEPTH, 0.0, o)
    val backY = o[1].toFloat()
    canvas.drawRect(0f, 0f, g.w, g.h, k.radial(g.cx, backY - 30, g.w * 0.9f, c(0xb0703a to 0.32f, 0x6a3a22 to 0.12f, 0 to 0f), f(0f, 0.5f, 1f)))
    // Floor under the table.
    g.project(0.0, 0.0, 0.0, o)
    val frontY = o[1].toFloat()
    canvas.drawRect(0f, frontY, g.w, g.h, k.linear(0f, frontY, 0f, g.h, c(0x1a100c to 1f, 0x22150f to 1f, 0x120b08 to 1f), f(0f, 0.4f, 1f)))
    // Apron: the table's front face, then the top.
    canvas.drawPath(
        worldQuad(g, -wide, 0.0, 0.0, wide, 0.0, 0.0, wide, 0.0, -58.0, -wide, 0.0, -58.0),
        k.linear(0f, frontY, 0f, frontY + 50, c(0x4a2614 to 1f, 0x341a0d to 1f, 0x1e0f07 to 1f), f(0f, 0.5f, 1f)),
    )
    g.project(0.0, 0.0, -58.0, o)
    val apronBottom = o[1].toFloat()
    canvas.drawRect(0f, apronBottom, g.w, apronBottom + 40, k.linear(0f, apronBottom, 0f, apronBottom + 40, c(0 to 0.6f, 0 to 0f), null))
    val topPath = worldQuad(g, -wide, 0.0, 0.0, wide, 0.0, 0.0, wide, DEPTH + 60, 0.0, -wide, DEPTH + 60, 0.0)
    canvas.drawPath(topPath, k.linear(0f, backY, 0f, frontY, c(0x5a2e17 to 1f, 0x6e3a1e to 1f, 0x4f2812 to 1f), f(0f, 0.6f, 1f)))
    canvas.save()
    canvas.clipPath(topPath)
    k.brushed(canvas, 0f, backY - 60, g.w, frontY - backY + 60, 0.2f, 13)
    // Plank seams running away from the viewer.
    val b = DoubleArray(3)
    var x = -wide
    while (x <= wide) {
        g.project(x, 0.0, 0.0, o)
        g.project(x, DEPTH + 60, 0.0, b)
        canvas.drawLine(o[0].toFloat(), o[1].toFloat(), b[0].toFloat(), b[1].toFloat(), k.stroke(0x1c0c05, 1f, 0.45f))
        x += 120
    }
    canvas.restore()
    canvas.drawLine(0f, frontY + 0.5f, g.w, frontY + 0.5f, k.stroke(0xd89a62, 1f, 0.35f))
    k.grain(canvas, 0f, 0f, g.w, g.h, 0.06f, 2)
    // Vignette.
    canvas.drawRect(0f, 0f, g.w, g.h, k.radial(g.cx, frontY - 60, g.h * 0.75f, c(0 to 0f, 0 to 0.1f, 0 to 0.6f), f(0f, 0.55f, 1f)))
}

/** The linen in material space (draw with the canvas at TEX_SCALE pixels per pt). */
internal fun clothDrawLinen(canvas: Canvas, k: ClothKit) {
    val w = CLOTH_W.toFloat()
    val l = CLOTH_LEN.toFloat()
    canvas.drawRect(0f, 0f, w, l, k.linear(0f, 0f, 0f, l, c(0xf7f2e8 to 1f, 0xf1ebdf to 1f, 0xece4d6 to 1f), f(0f, 0.6f, 1f)))
    // Weave: streaks one per thread direction (the second turned a quarter), multiplied in.
    k.brushed(canvas, 0f, 0f, w, l, 0.16f, 3, multiply = true)
    canvas.save()
    canvas.rotate(90f)
    k.brushed(canvas, 0f, -w, l, w, 0.16f, 5, multiply = true)
    canvas.restore()
    // Slubs: uneven thread thickness, the irregular streaks of real linen (long along x).
    canvas.save()
    canvas.scale(6f, 1.4f)
    k.brushed(canvas, 0f, 0f, w / 6f, l / 1.4f, 0.12f, 8, multiply = true)
    canvas.restore()
    // Windowpane check: a strong and a fine terracotta line each way.
    val strong = k.stroke(0xc0603a, 2.4f, 0.42f)
    val fine = k.stroke(0xc0603a, 0.9f, 0.36f)
    var x = 16f
    while (x < w) {
        canvas.drawLine(x, 0f, x, l, strong)
        canvas.drawLine(x + 6, 0f, x + 6, l, fine)
        x += 64
    }
    var y = 20f
    while (y < l - 30) {
        canvas.drawLine(0f, y, w, y, strong)
        canvas.drawLine(0f, y + 6, w, y + 6, fine)
        y += 64
    }
    // Hem: a woven band and a stitched edge.
    canvas.drawRect(0f, l - 26, w, l - 17, k.fill(0xb4532f, 0.85f))
    canvas.drawRect(0f, l - 13, w, l - 10.5f, k.fill(0xb4532f, 0.7f))
    val stitch = k.stroke(0x8a3a1e, 0.8f, 0.5f)
    stitch.pathEffect = DashPathEffect(floatArrayOf(3f, 2f), 0f)
    canvas.drawLine(0f, l - 4, w, l - 4, stitch)
    // Pressed creases from the linen cupboard.
    canvas.drawLine(w / 2, 0f, w / 2, l, k.stroke(0xffffff, 1.4f, 0.35f))
    canvas.drawLine(w / 2 + 1.5f, 0f, w / 2 + 1.5f, l, k.stroke(0x8a7a60, 0.8f, 0.15f))
    val half = (DEPTH / 2).toFloat()
    canvas.drawLine(0f, half, w, half, k.stroke(0xffffff, 1.4f, 0.3f))
    canvas.drawLine(0f, half + 1.5f, w, half + 1.5f, k.stroke(0x8a7a60, 0.8f, 0.12f))
}

// ——— The mesh ———

private const val FB_COLS = 12
private const val FB_ROWS = 12

/** Texture coordinates (bitmap pixels) and triangle indices of the cloth grid, built once. */
internal class ClothMesh {
    val texs = FloatArray((COLS + 1) * (ROWS + 1) * 2)
    val indices = ShortArray(COLS * ROWS * 6)

    init {
        var n = 0
        for (j in 0..ROWS) for (i in 0..COLS) {
            texs[n++] = (i.toDouble() / COLS * CLOTH_W * TEX_SCALE).toFloat()
            texs[n++] = (j.toDouble() / ROWS * CLOTH_LEN * TEX_SCALE).toFloat()
        }
        n = 0
        for (j in 0 until ROWS) for (i in 0 until COLS) {
            val a = j * (COLS + 1) + i
            val b = a + 1
            val cc = a + COLS + 1
            val d = cc + 1
            indices[n++] = a.toShort(); indices[n++] = b.toShort(); indices[n++] = cc.toShort()
            indices[n++] = b.toShort(); indices[n++] = d.toShort(); indices[n++] = cc.toShort()
        }
    }
}

/** Where a material point (u across, s along) lies for a pose, and how it is shaded: [x, y, z, dark, light]. */
private fun clothPoint(pose: ClothPose, u: Double, s: Double, phase: Double, sway: Double, out: DoubleArray) {
    val tau = 2 * PI
    val p = s + pose.d
    var x = u
    val y: Double
    val z: Double
    var dark = 0.03 + 0.035 * (0.5 + 0.5 * sin(u / 83 + s / 61)) + 0.025 * (0.5 + 0.5 * sin(u / 37 - s / 29 + 2))
    var light: Double
    if (p <= DEPTH) {
        // On the table (or above it, thrown): a travelling ripple plus air under the middle.
        val k = tau / 120
        val across = 0.65 + 0.35 * sin(u / 70 + 1.3)
        val arg = k * s - phase + u / 160
        val cs = clamp(s, 0.0, DEPTH)
        val bump = sin(PI * cs / DEPTH) * (0.75 + 0.25 * cos(u / 90))
        val rip = pose.wave * across * (0.5 + 0.5 * sin(arg))
        z = max(0.0, pose.lift + pose.air * bump + rip)
        val slope = pose.wave * across * 0.5 * cos(arg) * k + pose.air * cos(PI * cs / DEPTH) * PI / DEPTH
        dark += clamp(-slope * 1.3, 0.0, 0.34)
        light = clamp(slope * 1.1, 0.0, 0.24)
        y = DEPTH - p + pose.back
        x += pose.wave * 0.1 * sin(arg * 0.7)
    } else {
        // Over the front edge: hinged at the edge, hanging at `hang` (0 = flat forward, 1 = down),
        // in soft vertical folds that deepen toward the hem; streaming toward the viewer on a yank.
        val q = p - DEPTH
        val fl = pose.fling
        val hem = clamp(q / DROP, 0.0, 1.0).pow(0.8)
        val beta = pose.hang * PI / 2
        val ez = pose.lift + pose.air * 0.15
        val fa = u / 13 + 0.8 * sin(u / 37 + 1.1)
        val hang1 = min(pose.hang, 1.0)
        val foldDepth = (5 + 6 * hem) * hang1 * (1 - 0.5 * fl)
        var yy = pose.back - q * cos(beta) - (0.5 + 0.5 * sin(fa)) * foldDepth - 1.5 - 3 * sway * hem * (0.6 + 0.4 * sin(u / 90))
        z = ez - q * sin(beta)
        // The yank: the drape streams out toward the viewer and a wave whips down it to the hem.
        val whip = q / 38 - pose.d / 26 + u / 150
        val reach = clamp(q / 50, 0.0, 1.0)
        yy -= q * fl * 0.5 + fl * 34 * sin(whip) * reach
        y = yy
        x += fl * 12 * sin(q / 50 - phase + u / 120)
        val fslope = cos(fa) * (1 + (0.8 * cos(u / 37 + 1.1) / 37) * 13)
        val wslope = fl * cos(whip) * reach
        dark += 0.1 + 0.12 * clamp(q / 140, 0.0, 1.0) + clamp(-fslope, 0.0, 1.0) * 0.16 * hang1 * (1 - fl) + clamp(-wslope, 0.0, 1.0) * 0.4
        light = clamp(fslope, 0.0, 1.0) * 0.12 * hang1 * (1 - fl) + clamp(wslope, 0.0, 1.0) * 0.3
    }
    out[0] = x
    out[1] = y
    out[2] = z
    out[3] = dark
    out[4] = light
}

private fun shadeColor(dark: Double, light: Double, alpha: Float): Int =
    if (dark > light) {
        (((dark * alpha) * 255).roundToInt().coerceIn(0, 255) shl 24) or (51 shl 16) or (28 shl 8) or 15
    } else {
        (((light * alpha) * 255).roundToInt().coerceIn(0, 255) shl 24) or (255 shl 16) or (245 shl 8) or 224
    }

/** Builds and draws the cloth for a pose with a pull of `d`: the linen, then its shading; `alpha` < 1 for ghosts. */
internal fun clothDraw(
    canvas: Canvas,
    k: ClothKit,
    s: ClothScratch,
    g: ClothGeo,
    mesh: ClothMesh,
    linen: BitmapShader,
    pose: ClothPose,
    d: Double,
    alpha: Float = 1f,
) {
    val tau = 2 * PI
    val phase = tau * 9 * pose.time / LOOP
    val sway = sin(tau * 2 * pose.time / LOOP)
    val saved = pose.d
    pose.d = d
    val useMesh = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && canvas.isHardwareAccelerated
    val cols = if (useMesh) COLS else FB_COLS
    val rows = if (useMesh) ROWS else FB_ROWS
    val verts = if (useMesh) s.verts else s.fbVerts
    var onScreen = false
    var n = 0
    for (j in 0..rows) {
        val sv = j.toDouble() / rows * CLOTH_LEN
        for (i in 0..cols) {
            val u = (i.toDouble() / cols - 0.5) * CLOTH_W
            clothPoint(pose, u, sv, phase, sway, s.pt)
            g.project(s.pt[0], s.pt[1], s.pt[2], s.p0)
            if (s.p0[1] < g.panelTop + 40 && s.p0[1] > -40) onScreen = true
            verts[n * 2] = s.p0[0].toFloat()
            verts[n * 2 + 1] = s.p0[1].toFloat()
            if (useMesh) {
                s.colors[n] = shadeColor(s.pt[3], s.pt[4], alpha)
            } else {
                s.fbDark[n] = s.pt[3].toFloat()
                s.fbLight[n] = s.pt[4].toFloat()
            }
            n++
        }
    }
    pose.d = saved
    if (!onScreen) return
    if (useMesh) {
        val p = k.fill(0)
        p.shader = linen
        p.alpha = (alpha * 255).roundToInt()
        canvas.drawVertices(Canvas.VertexMode.TRIANGLES, verts.size, verts, 0, mesh.texs, 0, null, 0, mesh.indices, 0, mesh.indices.size, p)
        // Android blends vertex colours with the paint's colour (modulate): white keeps them as they are.
        val q = k.fill(0xffffff)
        canvas.drawVertices(Canvas.VertexMode.TRIANGLES, verts.size, verts, 0, null, 0, s.colors, 0, mesh.indices, 0, mesh.indices.size, q)
    } else {
        clothDrawFallback(canvas, k, s, linen, cols, rows, alpha)
    }
}

/** Below Android 10: each grid triangle filled with the linen mapped onto it, then a flat shade. */
private fun clothDrawFallback(canvas: Canvas, k: ClothKit, s: ClothScratch, linen: BitmapShader, cols: Int, rows: Int, alpha: Float) {
    val tex = k.fill(0)
    tex.shader = linen
    tex.alpha = (alpha * 255).roundToInt()
    val shade = k.fill(0)
    val tw = (CLOTH_W * TEX_SCALE).toFloat()
    val th = (CLOTH_LEN * TEX_SCALE).toFloat()
    for (j in 0 until rows) for (i in 0 until cols) {
        val a = j * (cols + 1) + i
        val b = a + 1
        val cc = a + cols + 1
        val d = cc + 1
        for (t in 0 until 2) {
            val i0 = if (t == 0) a else b
            val i1 = if (t == 0) b else d
            val i2 = cc
            fun texX(v: Int) = (v % (cols + 1)).toFloat() / cols * tw
            fun texY(v: Int) = (v / (cols + 1)).toFloat() / rows * th
            s.src[0] = texX(i0); s.src[1] = texY(i0)
            s.src[2] = texX(i1); s.src[3] = texY(i1)
            s.src[4] = texX(i2); s.src[5] = texY(i2)
            s.dst[0] = s.fbVerts[i0 * 2]; s.dst[1] = s.fbVerts[i0 * 2 + 1]
            s.dst[2] = s.fbVerts[i1 * 2]; s.dst[3] = s.fbVerts[i1 * 2 + 1]
            s.dst[4] = s.fbVerts[i2 * 2]; s.dst[5] = s.fbVerts[i2 * 2 + 1]
            if (!s.local.setPolyToPoly(s.src, 0, s.dst, 0, 3)) continue
            linen.setLocalMatrix(s.local)
            s.tri.reset()
            s.tri.moveTo(s.dst[0], s.dst[1])
            s.tri.lineTo(s.dst[2], s.dst[3])
            s.tri.lineTo(s.dst[4], s.dst[5])
            s.tri.close()
            canvas.drawPath(s.tri, tex)
            val dark = (s.fbDark[i0] + s.fbDark[i1] + s.fbDark[i2]) / 3.0
            val light = (s.fbLight[i0] + s.fbLight[i1] + s.fbLight[i2]) / 3.0
            shade.color = shadeColor(dark, light, alpha)
            canvas.drawPath(s.tri, shade)
        }
    }
    linen.setLocalMatrix(null)
}

/** Speed streaks on the table behind the cloth's back edge while it is yanked. */
internal fun clothDrawStreaks(canvas: Canvas, k: ClothKit, s: ClothScratch, g: ClothGeo, pose: ClothPose) {
    val kk = clamp(pose.speed / 2200, 0.0, 1.0)
    if (kk <= 0.02) return
    val edge = DEPTH - pose.d
    if (edge < -DROP) return
    for (i in 0 until 16) {
        val h = sin(i * 12.9898 + 4.7) * 43758.5453
        val r = h - floor(h)
        val x = -190 + i / 15.0 * 380 + (r - 0.5) * 18
        val y0 = max(edge, 0.0) + 6
        val len = (60 + 120 * r) * kk
        g.project(x, y0, 1.0, s.p1)
        g.project(x, y0 + len, 1.0, s.p2)
        val p = k.stroke(0, (1.6 + 2.2 * r).toFloat())
        p.strokeCap = Paint.Cap.BUTT
        p.shader = LinearGradient(
            s.p1[0].toFloat(), s.p1[1].toFloat(), s.p2[0].toFloat(), s.p2[1].toFloat(),
            clothArgb(0xfff7e6, (0.75 * kk).toFloat()), clothArgb(0xfff7e6, 0f), Shader.TileMode.CLAMP,
        )
        canvas.drawLine(s.p1[0].toFloat(), s.p1[1].toFloat(), s.p2[0].toFloat(), s.p2[1].toFloat(), p)
    }
}

/** The linen bitmap as a shader for the mesh. */
internal fun clothLinenShader(linen: Bitmap) = BitmapShader(linen, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
