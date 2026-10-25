package com.ngocdevv.nativecook.blocks.splash.brushinksplash

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Picture
import android.graphics.RadialGradient
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/*
 Layout and static art. The painted strokes are fixed geometry, built once: one triangle mesh for
 all of them, its texture coordinates in stroke space (see BrushInkSplashShader.kt), drawn with
 Canvas.drawVertices. Per frame only the shader's uniforms change. The finger's stroke uses the
 same mesh builder, rebuilt when the finger moves.
 */

/** How far past the stroke's nominal half-width the mesh reaches (room for the bleed). */
internal const val INK_MARGIN = 1.7f

internal class InkLayout(
    val width: Float,
    val height: Float,
    val ensoX: Float,
    val ensoY: Float,
    val ensoR: Float,
    val wordTop: Float,
    val wordK: Float,
    val taglineTop: Float,
    val footerTop: Float,
)

internal fun inkLayout(width: Float, height: Float, top: Float, bottom: Float): InkLayout {
    val r = min(min(width * 0.34f, 140f), (height - top - bottom) * 0.18f)
    val y = top + (height - top - bottom) * 0.38f
    val k = min(1.3f, width / 300f)
    val wordTop = y + r + 44f
    return InkLayout(width, height, width / 2f, y, r, wordTop, k, wordTop + 60f * k + 26f, height - max(bottom, 16f) - 34f)
}

/** A growable triangle mesh: positions and texture coordinates as x, y pairs, 16-bit indices. */
internal class InkMesh {
    var verts = FloatArray(512)
    var texs = FloatArray(512)
    var indices = ShortArray(768)
    var vertexFloats = 0
    var indexCount = 0

    fun clear() {
        vertexFloats = 0
        indexCount = 0
    }

    fun vertex(x: Float, y: Float, u: Float, v: Float) {
        if (vertexFloats + 2 > verts.size) {
            verts = verts.copyOf(verts.size * 2)
            texs = texs.copyOf(texs.size * 2)
        }
        verts[vertexFloats] = x
        verts[vertexFloats + 1] = y
        texs[vertexFloats] = u
        texs[vertexFloats + 1] = v
        vertexFloats += 2
    }

    fun triangle(a: Int, b: Int, c: Int) {
        if (indexCount + 3 > indices.size) indices = indices.copyOf(indices.size * 2)
        indices[indexCount] = a.toShort()
        indices[indexCount + 1] = b.toShort()
        indices[indexCount + 2] = c.toShort()
        indexCount += 3
    }

    val vertexCount get() = vertexFloats / 2
}

/** Adds one stroke to a mesh: two vertices per centreline point, two triangles per step. */
internal fun inkAddStroke(mesh: InkMesh, xs0: FloatArray, ys0: FloatArray, hw0: FloatArray, n: Int, index: Int, start: Float, margin: Float) {
    if (n < 2) return
    val base = mesh.vertexCount
    // The centreline runs on past both ends by a half-width, so the shader can round the caps.
    val l0 = hypot(xs0[1] - xs0[0], ys0[1] - ys0[0]).takeIf { it > 0f } ?: 1f
    val l1 = hypot(xs0[n - 1] - xs0[n - 2], ys0[n - 1] - ys0[n - 2]).takeIf { it > 0f } ?: 1f
    val sx = xs0[0] - (xs0[1] - xs0[0]) / l0 * hw0[0] * 1.2f
    val sy = ys0[0] - (ys0[1] - ys0[0]) / l0 * hw0[0] * 1.2f
    val ex = xs0[n - 1] + (xs0[n - 1] - xs0[n - 2]) / l1 * hw0[n - 1] * 1.2f
    val ey = ys0[n - 1] + (ys0[n - 1] - ys0[n - 2]) / l1 * hw0[n - 1] * 1.2f
    val m = n + 2
    fun x(i: Int) = when (i) { 0 -> sx; m - 1 -> ex; else -> xs0[i - 1] }
    fun y(i: Int) = when (i) { 0 -> sy; m - 1 -> ey; else -> ys0[i - 1] }
    fun w(i: Int) = when (i) { 0 -> hw0[0]; m - 1 -> hw0[n - 1]; else -> hw0[i - 1] }
    var along = start - hw0[0] * 1.2f
    for (i in 0 until m) {
        if (i > 0) along += hypot(x(i) - x(i - 1), y(i) - y(i - 1))
        val a = max(0, i - 1)
        val b = min(m - 1, i + 1)
        var tx = x(b) - x(a)
        var ty = y(b) - y(a)
        val tl = hypot(tx, ty).takeIf { it > 0f } ?: 1f
        tx /= tl
        ty /= tl
        val off = w(i) * margin
        mesh.vertex(x(i) - ty * off, y(i) + tx * off, along, -margin + 4f * index)
        mesh.vertex(x(i) + ty * off, y(i) - tx * off, along, margin + 4f * index)
        if (i > 0) {
            val p = base + (i - 1) * 2
            mesh.triangle(p, p + 1, p + 2)
            mesh.triangle(p + 1, p + 3, p + 2)
        }
    }
}

/** The splash's strokes (ensō first, then the wordmark), their plan, and one mesh for all. */
internal class InkSplash(
    val plan: InkPlan,
    val mesh: InkMesh,
    val strokes: List<InkStroke>,
    val ensoStartX: Float,
    val ensoStartY: Float,
    val ensoEndX: Float,
    val ensoEndY: Float,
    val ensoEndAngle: Float,
)

internal fun inkBuildSplash(l: InkLayout): InkSplash {
    val strokes = listOf(inkEnso(l.ensoX, l.ensoY, l.ensoR)) + inkWordmark(l.width / 2f, l.wordTop, l.wordK)
    val p = inkPlan(strokes)
    val mesh = InkMesh()
    strokes.forEachIndexed { i, s ->
        inkAddStroke(mesh, s.xs, s.ys, s.hw, s.xs.size, i, p.starts[i], if (i == 0) INK_MARGIN else 1.6f)
    }
    val e = strokes[0]
    val n = e.xs.size
    return InkSplash(
        p, mesh, strokes, e.xs[0], e.ys[0], e.xs[n - 1], e.ys[n - 1],
        atan2(e.ys[n - 1] - e.ys[n - 4], e.xs[n - 1] - e.xs[n - 4]),
    )
}

/**
 * Draws once into a bitmap `w` × `h` px: through a picture into a hardware bitmap when asked (so
 * runtime shaders render exactly as on screen), into a plain bitmap otherwise.
 */
private fun inkBake(w: Int, h: Int, hardware: Boolean, draw: (Canvas) -> Unit): Bitmap? {
    if (w <= 0 || h <= 0 || w > 8192 || h > 8192) return null
    return try {
        if (hardware && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val picture = Picture()
            draw(picture.beginRecording(w, h))
            picture.endRecording()
            Bitmap.createBitmap(picture, w, h, Bitmap.Config.HARDWARE)
        } else {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { draw(Canvas(it)) }
        }
    } catch (_: RuntimeException) {
        null
    } catch (_: OutOfMemoryError) {
        null
    }
}

/** The rice paper, baked into one bitmap at `scale` pixels per dp (gradients below Android 13). */
internal fun inkBakePaper(l: InkLayout, scale: Float): Bitmap? {
    val w = ceil(l.width * scale).toInt()
    val h = ceil(l.height * scale).toInt()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val shader = runCatching { RuntimeShader(INK_PAPER_AGSL) }.getOrNull()
        if (shader != null) {
            shader.setFloatUniform("size", l.width, l.height)
            inkBake(w, h, true) { c ->
                c.scale(scale, scale)
                c.drawRect(0f, 0f, l.width, l.height, Paint().apply { this.shader = shader })
            }?.let { return it }
        }
    }
    return inkBake(w, h, false) { c ->
        c.scale(scale, scale)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, l.height, 0xFFF4EFE4.toInt(), 0xFFEDE6D9.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, l.width, l.height, p)
        p.shader = RadialGradient(
            l.width / 2f, l.height / 2f, max(l.width, l.height) * 0.75f,
            intArrayOf(0x00000000, 0x00000000, 0x17000000), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, 0f, l.width, l.height, p)
    }
}
