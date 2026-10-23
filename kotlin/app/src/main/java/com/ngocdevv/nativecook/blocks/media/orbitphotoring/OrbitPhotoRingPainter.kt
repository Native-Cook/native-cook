package com.ngocdevv.nativecook.blocks.media.orbitphotoring

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// Pebbled leather: bright pebbles on a darker ground, in the face's own coordinates so the grain
// turns and foreshortens with the body. Android 13+; below that the leather is a flat colour.
private const val LEATHER_AGSL = """
uniform float3 tone;
uniform float grain;

float2 hash(float2 p) {
  return fract(sin(float2(dot(p, float2(127.1, 311.7)), dot(p, float2(269.5, 183.3)))) * 43758.5453);
}

half4 main(float2 xy) {
  float2 g = xy * grain;
  float2 i = floor(g);
  float2 f = fract(g);
  float d = 8.0;
  for (int y = -1; y <= 1; y++) {
    for (int x = -1; x <= 1; x++) {
      float2 o = float2(float(x), float(y));
      float2 r = o + hash(i + o) - f;
      d = min(d, dot(r, r));
    }
  }
  float pebble = 1.0 - smoothstep(0.04, 0.6, d);
  return half4(half3(tone * (0.78 + 0.34 * pebble)), 1.0);
}
"""

/** A paint's colour alpha also fades its shader, so a shader is always set with an opaque colour. */
internal var Paint.shading: Shader?
    get() = shader
    set(value) {
        color = android.graphics.Color.BLACK
        shader = value
    }

private fun argb(r: Float, g: Float, b: Float, a: Float) = android.graphics.Color.argb(a.coerceIn(0f, 1f), r, g, b)

/** A colour of the camera vector at `i`, brightened by `k`. */
internal fun tone(v: FloatArray, i: Int, k: Float, a: Float = 1f) =
    argb(clamp01(v[i] * k), clamp01(v[i + 1] * k), clamp01(v[i + 2] * k), a)

/** Every paint, shader and scratch object the picture needs, made once. */
internal class OrbitPaints {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val art = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val layer = Paint()
    val matrix = Matrix()
    val rect = RectF()
    val src = Rect()
    val bounds = Rect()
    val leather: Shader? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RuntimeShader(LEATHER_AGSL) else null
    // The Tumble style's ray-marched toy camera (Android 13+).
    val toy: OrbitToyPainter? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) OrbitToyPainter() else null
    private val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(Typeface.SANS_SERIF, 800, false) else Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    private val engraveFace = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(Typeface.SANS_SERIF, 700, false) else Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    val name = text(display, 25f)
    val spec = text(Typeface.MONOSPACE, 10f)
    val label = text(Typeface.MONOSPACE, 9f)
    val caption = text(Typeface.MONOSPACE, 6.5f)
    val engrave = text(engraveFace, 10f)

    // Blurred prints behind the body are drawn through render nodes with a blur (Android 12+).
    private val nodes = HashMap<Int, Any>()

    @RequiresApi(Build.VERSION_CODES.Q)
    fun node(key: Int): RenderNode = nodes.getOrPut(key) { RenderNode("orbit-print-$key") } as RenderNode

    private fun text(face: Typeface, size: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = face
        textSize = size
    }

    private val widths = HashMap<Long, Float>()

    /**
     * The width of one letter as the React Native version measures it with Skia's `measureText`:
     * the glyph's bounds rounded out to whole points plus a point each side; a space, which Skia
     * measures as nothing, is given a third of the size.
     */
    fun width(paint: Paint, c: Char): Float {
        if (c == ' ') return paint.textSize * 0.34f
        val key = (System.identityHashCode(paint).toLong() shl 16) or c.code.toLong()
        return widths.getOrPut(key) {
            paint.getTextBounds(c.toString(), 0, 1, bounds)
            if (bounds.isEmpty) 0f else bounds.width() + 2f
        }
    }
}

/** Text with extra space between letters, set around `x` by `align` (0 left, 0.5 middle, 1 right). */
internal fun drawTracked(canvas: Canvas, paints: OrbitPaints, text: String, paint: Paint, x: Float, y: Float, tracking: Float, align: Float, color: Int) {
    paint.color = color
    var width = 0f
    for (i in text.indices) width += paints.width(paint, text[i]) + if (i < text.length - 1) tracking else 0f
    var at = x - width * align
    for (c in text) {
        canvas.drawText(c.toString(), at, y, paint)
        at += paints.width(paint, c) + tracking
    }
}

/** Brushed chrome (or black paint) along a face's vertical: bright, dark, bright, dark. */
private fun metal(v: FloatArray, y0: Float, y1: Float) = LinearGradient(
    0f, y0, 0f, y1,
    intArrayOf(tone(v, V_METAL, 1.3f), tone(v, V_METAL, 0.9f), tone(v, V_METAL, 1.12f), tone(v, V_METAL, 0.6f), tone(v, V_METAL, 0.85f)),
    floatArrayOf(0f, 0.28f, 0.55f, 0.82f, 1f),
    Shader.TileMode.CLAMP,
)

private fun shadeRect(canvas: Canvas, p: OrbitPaints, x: Float, y: Float, w: Float, h: Float, light: Float) {
    p.fill.shader = null
    p.fill.color = argb(0f, 0f, 0f, (1f - light) * 0.72f)
    canvas.drawRect(x, y, x + w, y + h, p.fill)
}

/**
 * A face of the body: leather between a metal top plate and a thin metal foot, or plain metal on
 * the top and bottom. The front also carries the finder windows, the accent dot and the name.
 */
private fun paintBodyFace(canvas: Canvas, p: OrbitPaints, v: FloatArray, kind: Int, w: Float, h: Float, light: Float, detail: Boolean, label: String) {
    val x0 = -w / 2f
    val y0 = -h / 2f
    val fill = p.fill
    if (kind >= 4) {
        // Top or bottom: a metal plate, its gradient running front to back.
        fill.shading = metal(v, y0, y0 + h)
        canvas.drawRect(x0, y0, x0 + w, y0 + h, fill)
        shadeRect(canvas, p, x0, y0, w, h, light)
        return
    }
    val plate = h * v[V_PLATE]
    val leather = p.leather
    if (detail && leather != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val shader = leather as RuntimeShader
        shader.setFloatUniform("tone", v[V_LEATHER], v[V_LEATHER + 1], v[V_LEATHER + 2])
        shader.setFloatUniform("grain", LEATHER_GRAIN)
        fill.shading = shader
    } else {
        fill.shader = null
        fill.color = tone(v, V_LEATHER, 1f)
    }
    canvas.drawRect(x0, y0, x0 + w, y0 + h, fill)
    fill.shading = metal(v, y0, y0 + plate)
    canvas.drawRect(x0, y0, x0 + w, y0 + plate, fill)
    fill.shading = metal(v, y0 + h - 7f, y0 + h)
    canvas.drawRect(x0, y0 + h - 7f, x0 + w, y0 + h, fill)
    // A dark seam where the leather meets the metal.
    fill.shader = null
    fill.color = argb(0f, 0f, 0f, 0.45f)
    canvas.drawRect(x0, y0 + plate, x0 + w, y0 + plate + 1.2f, fill)
    canvas.drawRect(x0, y0 + h - 8f, x0 + w, y0 + h - 7f, fill)

    if (kind == 1 && detail) {
        // The back: the film door's outline, the eyepiece on the top plate and a film window.
        p.stroke.strokeWidth = 1.2f
        p.stroke.color = argb(0f, 0f, 0f, 0.4f)
        p.rect.set(x0 + 12f, y0 + plate + 7f, x0 + w - 12f, y0 + h - 15f)
        canvas.drawRoundRect(p.rect, 5f, 5f, p.stroke)
        fill.shader = null
        fill.color = 0xFF0E0E10.toInt()
        val ex = w * 0.22f
        p.rect.set(ex - 15f, y0 + plate * 0.18f, ex + 15f, y0 + plate * 0.82f)
        canvas.drawRoundRect(p.rect, 4f, 4f, fill)
        fill.color = tone(v, V_GLASS, 1.2f)
        p.rect.set(ex - 9f, y0 + plate * 0.3f, ex + 9f, y0 + plate * 0.7f)
        canvas.drawRoundRect(p.rect, 2.5f, 2.5f, fill)
        val fx = -w * 0.18f
        fill.shading = metal(v, y0 + plate + 18f, y0 + plate + 40f)
        p.rect.set(fx - 20f, y0 + plate + 18f, fx + 20f, y0 + plate + 40f)
        canvas.drawRoundRect(p.rect, 3f, 3f, fill)
        fill.shader = null
        fill.color = tone(v, V_ACCENT, 0.9f, 0.85f)
        canvas.drawRect(fx - 15f, y0 + plate + 23f, fx + 15f, y0 + plate + 35f, fill)
    }
    if (kind == 0 && detail) {
        // Two finder windows on a rangefinder, fading in and out with the morph.
        val windows = v[V_WINDOWS]
        if (windows > 0.02f) {
            for ((cx, ww) in listOf(-w * 0.34f to 34f, w * 0.3f to 22f)) {
                p.rect.set(cx - ww / 2f, y0 + plate * 0.24f, cx + ww / 2f, y0 + plate * 0.76f)
                fill.shading = LinearGradient(
                    cx - ww / 2f, y0, cx + ww / 2f, y0 + plate,
                    intArrayOf(tone(v, V_GLASS, 1.8f, windows), tone(v, V_GLASS, 0.4f, windows), tone(v, V_GLASS, 1.1f, windows)),
                    floatArrayOf(0f, 0.6f, 1f),
                    Shader.TileMode.CLAMP,
                )
                canvas.drawRoundRect(p.rect, 3f, 3f, fill)
                fill.shader = null
                p.stroke.strokeWidth = 1.4f
                p.stroke.color = argb(0f, 0f, 0f, 0.5f * windows)
                canvas.drawRoundRect(p.rect, 3f, 3f, p.stroke)
            }
        }
        // The accent dot and the engraved name on the top plate.
        fill.shader = null
        fill.color = tone(v, V_ACCENT, 1f)
        val dotY = y0 + plate + (h - plate) * 0.22f
        canvas.drawCircle(-w * 0.4f, dotY, 4.2f, fill)
        fill.color = argb(1f, 1f, 1f, 0.45f)
        canvas.drawCircle(-w * 0.4f - 1.2f, dotY - 1.2f, 1.3f, fill)
        val ink = if (v[V_METAL] + v[V_METAL + 1] + v[V_METAL + 2] > 1.5f) argb(0.1f, 0.1f, 0.11f, 0.75f) else argb(0.85f, 0.85f, 0.83f, 0.8f)
        drawTracked(canvas, p, label, p.engrave, w * 0.44f, y0 + plate * 0.66f, 1.6f, 1f, ink)
    }
    shadeRect(canvas, p, x0, y0, w, h, light)
}

/** A metal box: the prism hump or the hood on top. Its front carries a small dark name plate. */
private fun paintHumpFace(canvas: Canvas, p: OrbitPaints, v: FloatArray, kind: Int, w: Float, h: Float, light: Float) {
    val fill = p.fill
    fill.shading = metal(v, -h / 2f, h / 2f)
    canvas.drawRect(-w / 2f, -h / 2f, w / 2f, h / 2f, fill)
    fill.shader = null
    if (kind == 0 && h > 14f) {
        fill.color = 0xFF101012.toInt()
        p.rect.set(-w * 0.3f, -h * 0.18f, w * 0.3f, h * 0.24f)
        canvas.drawRoundRect(p.rect, 2f, 2f, fill)
        fill.color = tone(v, V_ACCENT, 1f, 0.9f)
        canvas.drawRect(-w * 0.22f, h * 0.02f, w * 0.22f, h * 0.02f + 1.4f, fill)
    }
    shadeRect(canvas, p, -w / 2f, -h / 2f, w, h, light)
}

private fun concatFace(canvas: Canvas, p: OrbitPaints, m: FloatArray) {
    p.matrix.setValues(m)
    canvas.concat(p.matrix)
}

// The faces of a box: centre (as a share of w, h, d about `at`), u, v, normal; drawn w×h, d×h or w×d.
private val BOX_U = arrayOf(floatArrayOf(1f, 0f, 0f), floatArrayOf(-1f, 0f, 0f), floatArrayOf(0f, 0f, 1f), floatArrayOf(0f, 0f, -1f), floatArrayOf(1f, 0f, 0f), floatArrayOf(1f, 0f, 0f))
private val BOX_V = arrayOf(floatArrayOf(0f, 1f, 0f), floatArrayOf(0f, 1f, 0f), floatArrayOf(0f, 1f, 0f), floatArrayOf(0f, 1f, 0f), floatArrayOf(0f, 0f, -1f), floatArrayOf(0f, 0f, 1f))
private val BOX_N = arrayOf(floatArrayOf(0f, 0f, -1f), floatArrayOf(0f, 0f, 1f), floatArrayOf(1f, 0f, 0f), floatArrayOf(-1f, 0f, 0f), floatArrayOf(0f, -1f, 0f), floatArrayOf(0f, 1f, 0f))

/**
 * A box of the camera, `hump` false for the body and true for the hump. Only the faces turned
 * towards the eye are drawn; a box is convex, so they never overlap.
 */
private fun drawBox(canvas: Canvas, p: OrbitPaints, view: View3, pose: Pose, v: FloatArray, x: Float, y: Float, z: Float, w: Float, h: Float, d: Float, hump: Boolean, detail: Boolean, label: String) {
    for (k in 0 until 6) {
        if (hump && k == 5) continue
        val c = when (k) {
            0 -> floatArrayOf(x, y, z - d / 2f)
            1 -> floatArrayOf(x, y, z + d / 2f)
            2 -> floatArrayOf(x + w / 2f, y, z)
            3 -> floatArrayOf(x - w / 2f, y, z)
            4 -> floatArrayOf(x, y - h / 2f, z)
            else -> floatArrayOf(x, y + h / 2f, z)
        }
        val fw = if (k == 2 || k == 3) d else w
        val fh = if (k >= 4) d else h
        val info = face(view, pose, c, BOX_U[k], BOX_V[k], BOX_N[k])
        val m = info.m ?: continue
        if (!info.visible) continue
        canvas.save()
        concatFace(canvas, p, m)
        if (hump) paintHumpFace(canvas, p, v, k, fw, fh, info.light) else paintBodyFace(canvas, p, v, k, fw, fh, info.light, detail, label)
        canvas.restore()
    }
}

/** A knob standing on the top plate: slices of a cylinder, then a knurled cap. */
private fun drawKnob(canvas: Canvas, p: OrbitPaints, view: View3, pose: Pose, v: FloatArray, x: Float, top: Float, z: Float, r: Float, h: Float, detail: Boolean) {
    val slices = if (detail) 4 else 2
    val fill = p.fill
    for (i in 0..slices) {
        val s = h * i / slices
        val info = face(view, pose, floatArrayOf(x, top - s, z), U_X, V_BACK, N_UP)
        val m = info.m ?: continue
        canvas.save()
        concatFace(canvas, p, m)
        if (i < slices) {
            fill.shader = null
            fill.color = tone(v, V_METAL, if (i % 2 == 0) 0.55f else 0.75f)
            canvas.drawCircle(0f, 0f, r, fill)
        } else {
            fill.shading = RadialGradient(-r * 0.3f, -r * 0.3f, r * 1.3f, intArrayOf(tone(v, V_METAL, 1.35f), tone(v, V_METAL, 0.7f)), null, Shader.TileMode.CLAMP)
            canvas.drawCircle(0f, 0f, r, fill)
            fill.shader = null
            if (detail) {
                p.stroke.color = argb(0f, 0f, 0f, 0.35f)
                p.stroke.strokeWidth = 0.8f
                for (k in 0 until 18) {
                    val a = k / 18f * TWO_PI
                    canvas.drawLine(cos(a) * r * 0.72f, sin(a) * r * 0.72f, cos(a) * r * 0.95f, sin(a) * r * 0.95f, p.stroke)
                }
            }
        }
        canvas.restore()
    }
}

/**
 * The lens: a metal mount, a ribbed black barrel built from slices of a cylinder, a chrome front
 * ring and the glass with its reflections. Drawn from the body outwards, or the other way round
 * when the lens points away.
 */
private fun drawLens(canvas: Canvas, p: OrbitPaints, view: View3, pose: Pose, v: FloatArray, outward: Boolean, detail: Boolean, flash: Float) {
    val r = v[V_LENS_R]
    val length = v[V_LENS_L]
    val lx = v[V_LENS_X]
    val ly = v[V_LENS_Y]
    val front = -v[V_D] / 2f
    val slices = if (detail) max(6, jsRound(length / 3f)) else 4
    val fill = p.fill
    val order = if (outward) (0..slices + 1) else (slices + 1 downTo 0)
    for (i in order) {
        // Slice 0 is the mount; slices 1…n the barrel; n + 1 the front with the glass.
        val s = if (i == 0) 2f else if (i == slices + 1) length else 2f + (length - 2f) * i / (slices + 1)
        val info = face(view, pose, floatArrayOf(lx, ly, front - s), U_X, V_Y, N_FRONT)
        val m = info.m ?: continue
        canvas.save()
        concatFace(canvas, p, m)
        fill.shader = null
        if (i == 0) {
            fill.color = tone(v, V_METAL, 0.8f)
            canvas.drawCircle(0f, 0f, r * 1.16f, fill)
        } else if (i <= slices) {
            val nearFront = i >= slices - 1
            if (nearFront) {
                fill.color = tone(v, V_METAL, if (i % 2 == 0) 0.75f else 1f)
            } else {
                // A sheen across each rib, so the barrel reads as a lit cylinder when seen from the side.
                val k = if (i % 2 == 0) 1f else 1.5f
                fill.shading = LinearGradient(-r, -r, r, r, intArrayOf(tone(v, V_BARREL, 3.4f * k), tone(v, V_BARREL, 1.3f * k), tone(v, V_BARREL, 0.8f * k)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            }
            canvas.drawCircle(0f, 0f, r * if (nearFront) 1.02f else 0.98f, fill)
            fill.shader = null
        } else {
            fill.shading = LinearGradient(-r, -r, r, r, intArrayOf(tone(v, V_METAL, 1.35f), tone(v, V_METAL, 0.65f), tone(v, V_METAL, 1.1f)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            canvas.drawCircle(0f, 0f, r * 1.02f, fill)
            fill.shader = null
            fill.color = 0xFF0B0B0D.toInt()
            canvas.drawCircle(0f, 0f, r * 0.86f, fill)
            if (detail) {
                p.stroke.color = argb(1f, 1f, 1f, 0.35f)
                p.stroke.strokeWidth = 0.7f
                for (k in 0 until 28) {
                    val a = k / 28f * TWO_PI
                    val long = if (k % 4 == 0) 0.74f else 0.79f
                    canvas.drawLine(cos(a) * r * long, sin(a) * r * long, cos(a) * r * 0.84f, sin(a) * r * 0.84f, p.stroke)
                }
            }
            // The glass: deep colour falling to black, two reflections, and the shutter's flash.
            fill.shading = RadialGradient(
                -r * 0.18f, -r * 0.22f, r * 0.8f,
                intArrayOf(tone(v, V_GLASS, 2.1f), tone(v, V_GLASS, 0.9f), argb(0.02f, 0.02f, 0.03f, 1f)),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(0f, 0f, r * 0.66f, fill)
            fill.shader = null
            p.stroke.strokeWidth = 1f
            p.stroke.color = tone(v, V_GLASS, 2.4f, 0.5f)
            canvas.drawCircle(0f, 0f, r * 0.4f, p.stroke)
            fill.color = argb(1f, 1f, 1f, 0.55f)
            canvas.save()
            canvas.rotate(-35f, -r * 0.26f, -r * 0.3f)
            p.rect.set(-r * 0.26f - r * 0.2f, -r * 0.3f - r * 0.08f, -r * 0.26f + r * 0.2f, -r * 0.3f + r * 0.08f)
            canvas.drawOval(p.rect, fill)
            canvas.restore()
            fill.color = argb(1f, 1f, 1f, 0.4f)
            canvas.drawCircle(r * 0.28f, r * 0.24f, r * 0.05f, fill)
            if (flash > 0.01f) {
                fill.shading = RadialGradient(0f, 0f, r * 0.9f, intArrayOf(argb(1f, 1f, 1f, flash), argb(1f, 0.97f, 0.9f, 0f)), null, Shader.TileMode.CLAMP)
                canvas.drawCircle(0f, 0f, r * 0.9f, fill)
                fill.shader = null
            }
        }
        if (i > 0) {
            // The barrel and the front ring take the light of the way the lens points.
            fill.shader = null
            fill.color = argb(0f, 0f, 0f, (1f - max(info.light, if (i == slices + 1) 0.85f else 0f)) * 0.72f)
            canvas.drawCircle(0f, 0f, r * 1.02f, fill)
        }
        canvas.restore()
    }
}

/** What stands on the top plate: the prism hump or hood, the speed dial with its release, the rewind knob. */
private fun drawTopParts(canvas: Canvas, p: OrbitPaints, view: View3, pose: Pose, v: FloatArray, detail: Boolean, label: String) {
    val w = v[V_W]
    val h = v[V_H]
    val d = v[V_D]
    val hh = v[V_HUMP_H]
    if (hh > 0.8f) drawBox(canvas, p, view, pose, v, 0f, -h / 2f - hh / 2f, d * 0.04f, v[V_HUMP_W], hh, v[V_HUMP_D], true, detail, label)
    drawKnob(canvas, p, view, pose, v, w * 0.36f, -h / 2f, -d * 0.05f, min(15f, w * 0.065f), 6f, detail)
    drawKnob(canvas, p, view, pose, v, w * 0.36f, -h / 2f - 6f, -d * 0.05f, 5.5f, 5f, detail)
    drawKnob(canvas, p, view, pose, v, -w * 0.36f, -h / 2f, 0f, min(13f, w * 0.058f), 9f, detail)
}

/**
 * The whole camera at `pose`: the parts on its top plate and the lens are drawn before or after
 * the body depending on whether they face the eye, which is all the depth sorting a box with a
 * knob on top and a lens in front needs.
 */
internal fun drawCamera(canvas: Canvas, p: OrbitPaints, view: View3, pose: Pose, v: FloatArray, detail: Boolean, label: String, flash: Float) {
    val w = v[V_W]
    val h = v[V_H]
    val d = v[V_D]
    val front = face(view, pose, floatArrayOf(0f, 0f, -d / 2f), U_X, V_Y, N_FRONT)
    val top = face(view, pose, floatArrayOf(0f, -h / 2f, 0f), U_X, V_BACK, N_UP)
    if (!front.visible) drawLens(canvas, p, view, pose, v, false, detail, flash)
    if (!top.visible) drawTopParts(canvas, p, view, pose, v, detail, label)
    drawBox(canvas, p, view, pose, v, 0f, 0f, 0f, w, h, d, false, detail, label)
    if (top.visible) drawTopParts(canvas, p, view, pose, v, detail, label)
    if (front.visible) drawLens(canvas, p, view, pose, v, true, detail, flash)
}

/**
 * A polaroid: a white card with a slightly tall photo, and on the wide bottom margin a caption in
 * pencil grey and a small ring mark. 100 × 126 units.
 */
private fun paintPrint(canvas: Canvas, p: OrbitPaints, print: PrintState, image: LoadedPhoto?, caption: String, now: Float) {
    val fill = p.fill
    fill.shader = null
    fill.color = if (print.facing) 0xFFF4F2EC.toInt() else 0xFFD9D5CC.toInt()
    p.rect.set(-50f, -63f, 50f, 63f)
    canvas.drawRoundRect(p.rect, 2f, 2f, fill)
    if (print.facing) {
        fill.color = 0xFF26262A.toInt()
        canvas.drawRect(-44f, -57f, 44f, 41f, fill)
        if (image != null) {
            // The photo fills the window, cropped about its middle.
            val bitmap = image.bitmap
            val sw = min(bitmap.width.toFloat(), bitmap.height * 88f / 98f)
            val sh = sw * 98f / 88f
            p.src.set(((bitmap.width - sw) / 2f).roundToInt(), ((bitmap.height - sh) / 2f).roundToInt(), ((bitmap.width + sw) / 2f).roundToInt(), ((bitmap.height + sh) / 2f).roundToInt())
            p.rect.set(-44f, -57f, 44f, 41f)
            p.art.alpha = (clamp01((now - image.at) / IMAGE_FADE) * 255f).roundToInt()
            canvas.drawBitmap(bitmap, p.src, p.rect, p.art)
        }
        // A soft sheen across the gloss, and the caption in pencil grey with a small ring mark.
        fill.shading = LinearGradient(-44f, -57f, 44f, 41f, intArrayOf(argb(1f, 1f, 1f, 0.16f), argb(1f, 1f, 1f, 0f), argb(1f, 1f, 1f, 0.06f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(-44f, -57f, 44f, 41f, fill)
        fill.shader = null
        val pencil = argb(0.22f, 0.21f, 0.2f, 0.7f)
        p.caption.color = pencil
        canvas.drawText(caption, -43f, 54.5f, p.caption)
        p.stroke.strokeWidth = 0.8f
        p.stroke.color = pencil
        canvas.drawCircle(40f, 52f, 2.6f, p.stroke)
    }
    if (print.shade > 0.004f) {
        fill.color = argb(0f, 0f, 0f, print.shade)
        p.rect.set(-50f, -63f, 50f, 63f)
        canvas.drawRoundRect(p.rect, 2f, 2f, fill)
    }
}

/**
 * A print in the ring, or flying in or out of it. A faded or blurred print is drawn as a layer:
 * through a render node with a blur on Android 12+, or as a plain faded layer below that.
 */
internal fun drawPrint(canvas: Canvas, p: OrbitPaints, print: PrintState, image: LoadedPhoto?, caption: String, now: Float, density: Float) {
    val m = print.m
    val layered = print.alpha < 0.995f || print.blur > 0.25f
    if (!layered) {
        canvas.save()
        concatFace(canvas, p, m)
        paintPrint(canvas, p, print, image, caption, now)
        canvas.restore()
        return
    }
    var left = Float.POSITIVE_INFINITY
    var top = Float.POSITIVE_INFINITY
    var right = Float.NEGATIVE_INFINITY
    var bottom = Float.NEGATIVE_INFINITY
    for (k in 0 until 4) {
        val cx = if (k == 1 || k == 2) 50f else -50f
        val cy = if (k >= 2) 63f else -63f
        val w = m[6] * cx + m[7] * cy + 1f
        val sx = (m[0] * cx + m[1] * cy + m[2]) / w
        val sy = (m[3] * cx + m[4] * cy + m[5]) / w
        left = min(left, sx)
        right = max(right, sx)
        top = min(top, sy)
        bottom = max(bottom, sy)
    }
    val pad = 2f + print.blur * 3f
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && canvas.isHardwareAccelerated) {
        // Recorded in pixels, so the blur works at the screen's resolution.
        val l = floor((left - pad) * density).toInt()
        val t = floor((top - pad) * density).toInt()
        val r = ceil((right + pad) * density).toInt()
        val b = ceil((bottom + pad) * density).toInt()
        val node = p.node(print.key)
        node.setPosition(l, t, r, b)
        val rc = node.beginRecording()
        rc.translate(-l.toFloat(), -t.toFloat())
        rc.scale(density, density)
        concatFace(rc, p, m)
        paintPrint(rc, p, print, image, caption, now)
        node.endRecording()
        val radius = sigmaToRadius(print.blur * density)
        node.setRenderEffect(if (print.blur > 0.25f) RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.DECAL) else null)
        node.alpha = clamp01(print.alpha)
        canvas.save()
        canvas.scale(1f / density, 1f / density)
        canvas.drawRenderNode(node)
        canvas.restore()
        return
    }
    canvas.saveLayerAlpha(left - pad, top - pad, right + pad, bottom + pad, (clamp01(print.alpha) * 255f).roundToInt())
    concatFace(canvas, p, m)
    paintPrint(canvas, p, print, image, caption, now)
    canvas.restore()
}

/** A layer paint that keeps `s` of the colour and greys the rest, at `alpha`. */
internal fun OrbitPaints.thumbLayer(s: Float, alpha: Float): Paint {
    val r = (1f - s) * 0.2126f
    val g = (1f - s) * 0.7152f
    val b = (1f - s) * 0.0722f
    layer.colorFilter = ColorMatrixColorFilter(
        ColorMatrix(floatArrayOf(r + s, g, b, 0f, 0f, r, g + s, b, 0f, 0f, r, g, b + s, 0f, 0f, 0f, 0f, 0f, 1f, 0f)),
    )
    layer.alpha = (alpha * 255f).roundToInt().coerceIn(0, 255)
    return layer
}

internal class LoadedPhoto(val bitmap: Bitmap, val at: Float)

internal const val TWO_PI = (PI * 2).toFloat()

// Android takes blur radii; Skia (and the RN code) thinks in sigma.
private fun sigmaToRadius(sigma: Float) = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.01f)
