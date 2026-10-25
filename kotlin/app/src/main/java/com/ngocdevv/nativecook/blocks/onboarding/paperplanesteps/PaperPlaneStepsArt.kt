package com.ngocdevv.nativecook.blocks.onboarding.paperplanesteps

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.ceil
import kotlin.math.roundToInt

/*
 Paper Plane Steps — the writing paper, drawn once into an atlas (a port of planeArt.ts): the three
 onboarding sheets side by side and, fourth, the back of the paper. Each sheet: cream laid paper
 with grain, faint ruled lines and a red margin, a perforated stamp in the top-right corner (a
 small landscape in its own palette per step) under a wavy postmark, the step number, a title and
 three lines of copy.
*/

private class StepCopy(val kicker: String, val title: String, val lines: List<String>)

private val STEP_COPY = listOf(
    StepCopy("No. 1 · Write", "Write it slow", listOf("Notes travel at the pace", "of paper. Take your time,", "nobody is watching you type.")),
    StepCopy("No. 2 · Fold", "Fold it up", listOf("Every letter is folded by", "hand and leaves the post", "office at nine each evening.")),
    StepCopy("No. 3 · Send", "Let it fly", listOf("Friends far away get it", "the next day, the way a", "letter should arrive.")),
)

private fun hex(v: String) = android.graphics.Color.parseColor(v)
private fun rgba(r: Int, g: Int, b: Int, a: Float) = android.graphics.Color.argb((a * 255f).roundToInt(), r, g, b)

private val STAMPS = listOf(
    intArrayOf(hex("#F6B26B"), hex("#E07A4F"), hex("#8E3B46"), hex("#3E2A4F")),
    intArrayOf(hex("#9ED8D1"), hex("#4FA3A5"), hex("#2C6E7F"), hex("#1D3B52")),
    intArrayOf(hex("#D9C2F0"), hex("#9C7CC8"), hex("#5E4B8B"), hex("#2E2A4F")),
)

private val INK = hex("#2B2F45")
private val PAPER = intArrayOf(hex("#FBF6EA"), hex("#F5EEDD"), hex("#EFE6D2"))

private fun face(family: Typeface, weight: Int): Typeface =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(family, weight, false) else Typeface.create(family, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)

private class PlaneFonts {
    val kicker = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; typeface = face(Typeface.SANS_SERIF, 700) }
    val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 29f; typeface = face(Typeface.SERIF, 700) }
    val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 15.5f; typeface = face(Typeface.SERIF, 400) }
    val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 6.5f; typeface = face(Typeface.SANS_SERIF, 700) }
}

private fun stamp(c: Canvas, x: Float, y: Float, w: Float, h: Float, pal: IntArray, fonts: PlaneFonts) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    // Shadow, then the perforated stamp paper: a white rectangle with half-circle bites along its edges.
    p.color = rgba(60, 40, 20, 0.18f)
    p.maskFilter = BlurMaskFilter(sigmaToRadius(2f), BlurMaskFilter.Blur.NORMAL)
    c.drawRect(x + 1, y + 2, x + 1 + w, y + 2 + h, p)
    p.maskFilter = null
    val body = Path().apply { addRect(x, y, x + w, y + h, Path.Direction.CW) }
    val bites = Path()
    val r = 2.2f
    val nw = (w / 6).roundToInt()
    val nh = (h / 6).roundToInt()
    for (i in 0..nw) {
        val bx = x + i * w / nw
        bites.addCircle(bx, y, r, Path.Direction.CW)
        bites.addCircle(bx, y + h, r, Path.Direction.CW)
    }
    for (i in 0..nh) {
        val by = y + i * h / nh
        bites.addCircle(x, by, r, Path.Direction.CW)
        bites.addCircle(x + w, by, r, Path.Direction.CW)
    }
    val cut = Path()
    if (!cut.op(body, bites, Path.Op.DIFFERENCE)) cut.set(body)
    p.color = hex("#FFFCF4")
    c.drawPath(cut, p)
    // The picture: sky in three stops, a sun, two hills.
    val ix = x + 5
    val iy = y + 5
    val iw = w - 10
    val ih = h - 16
    p.color = android.graphics.Color.BLACK
    p.shader = LinearGradient(ix, iy, ix, iy + ih, intArrayOf(pal[0], pal[1], pal[2]), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
    c.drawRect(ix, iy, ix + iw, iy + ih, p)
    p.shader = null
    p.color = rgba(255, 246, 220, 0.9f)
    c.drawCircle(ix + iw * 0.66f, iy + ih * 0.42f, iw * 0.14f, p)
    c.save()
    c.clipRect(ix, iy, ix + iw, iy + ih)
    val hill = Path().apply {
        moveTo(ix - 2, iy + ih * 0.78f)
        quadTo(ix + iw * 0.3f, iy + ih * 0.5f, ix + iw * 0.62f, iy + ih * 0.8f)
        lineTo(ix + iw + 2, iy + ih + 2)
        lineTo(ix - 2, iy + ih + 2)
        close()
    }
    p.color = pal[2]
    c.drawPath(hill, p)
    val hill2 = Path().apply {
        moveTo(ix + iw * 0.3f, iy + ih + 2)
        quadTo(ix + iw * 0.7f, iy + ih * 0.56f, ix + iw + 2, iy + ih * 0.74f)
        lineTo(ix + iw + 2, iy + ih + 2)
        close()
    }
    p.color = pal[3]
    c.drawPath(hill2, p)
    c.restore()
    fonts.mark.color = INK
    c.drawText("40", x + 6, y + h - 4, fonts.mark)
}

private fun postmark(c: Canvas, x: Float, y: Float, fonts: PlaneFonts) {
    val s = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
        color = rgba(52, 62, 96, 0.55f)
    }
    c.drawCircle(x, y, 19f, s)
    c.drawCircle(x, y, 14.5f, s)
    for (k in 0 until 4) {
        val wy = y - 9 + k * 6
        val wave = Path().apply {
            moveTo(x + 20, wy)
            for (i in 1..8) quadTo(x + 20 + i * 7 - 3.5f, wy + if (i % 2 == 1) -2.5f else 2.5f, x + 20 + i * 7, wy)
        }
        c.drawPath(wave, s)
    }
    fonts.mark.color = rgba(52, 62, 96, 0.6f)
    val a = "25 OCT"
    c.drawText(a, x - fonts.mark.measureText(a) / 2, y + 3, fonts.mark)
}

/** The atlas: 4 × SW wide, SH tall, at [scale] pixels per dp. Call off the main thread. */
internal fun buildPlanePaper(sw: Float, sh: Float, scale: Float): Bitmap {
    val fonts = PlaneFonts()
    val aw = sw * 4
    val bmp = Bitmap.createBitmap(ceil(aw * scale).toInt(), ceil(sh * scale).toInt(), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(scale, scale)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    for (k in 0 until 4) {
        val x0 = k * sw
        val back = k == 3
        p.shader = LinearGradient(
            x0, 0f, x0 + sw * 0.4f, sh,
            if (back) intArrayOf(hex("#F3ECDC"), hex("#EDE4D0"), hex("#E6DCC6")) else PAPER,
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        c.drawRect(x0, 0f, x0 + sw, sh, p)
        p.shader = null
        if (back) continue
        val copy = STEP_COPY[k]
        // Ruled lines and the margin.
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.7f
            color = rgba(90, 130, 190, 0.22f)
        }
        var y = 200f
        while (y < sh - 18) {
            c.drawLine(x0 + 14, y, x0 + sw - 14, y, line)
            y += 24
        }
        line.color = rgba(200, 80, 80, 0.28f)
        c.drawLine(x0 + 30, 0f, x0 + 30, sh, line)
        stamp(c, x0 + sw - 66, 18f, 48f, 58f, STAMPS[k], fonts)
        postmark(c, x0 + sw - 92, 74f, fonts)
        // Copy, measured so it always fits the sheet.
        fonts.kicker.color = hex("#B4553E")
        c.drawText(copy.kicker.uppercase(), x0 + 40, 118f, fonts.kicker)
        fonts.title.color = INK
        c.drawText(copy.title, x0 + 40, 154f, fonts.title)
        fonts.body.color = INK
        val room = sw - 40 - 16
        copy.lines.forEachIndexed { i, l ->
            val w = fonts.body.measureText(l)
            c.save()
            if (w > room) {
                c.translate(x0 + 40, 0f)
                c.scale(room / w, 1f)
                c.drawText(l, 0f, 196f + i * 24, fonts.body)
            } else {
                c.drawText(l, x0 + 40, 196f + i * 24, fonts.body)
            }
            c.restore()
        }
        // A signature flourish at the foot of the copy.
        val sy = 196f + 3 * 24 + 18
        val sig = Path().apply {
            moveTo(x0 + 40, sy)
            cubicTo(x0 + 56, sy - 18, x0 + 66, sy + 10, x0 + 80, sy - 4)
            cubicTo(x0 + 92, sy - 14, x0 + 100, sy + 6, x0 + 118, sy - 2)
        }
        val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.4f
            strokeCap = Paint.Cap.ROUND
            color = rgba(43, 47, 69, 0.7f)
        }
        c.drawPath(sig, pen)
    }
    // Paper grain at 1 px per dp, upscaled without filtering.
    val gw = ceil(aw).toInt()
    val gh = ceil(sh).toInt()
    val grain = IntArray(gw * gh)
    var seed = 98765L
    for (i in grain.indices) {
        seed = (seed * 1103515245L + 12345L) and 0x7fffffffL
        val v = seed.toDouble() / 0x7fffffff
        val dark = v < 0.5
        val a = kotlin.math.floor(kotlin.math.abs(v - 0.5) * 2 * 26).toInt()
        grain[i] = if (dark) android.graphics.Color.argb(a, 70, 55, 30) else android.graphics.Color.argb(a, 255, 252, 240)
    }
    val g = Bitmap.createBitmap(grain, gw, gh, Bitmap.Config.ARGB_8888)
    c.drawBitmap(g, Rect(0, 0, gw, gh), RectF(0f, 0f, aw, sh), Paint())
    g.recycle()
    return bmp
}

/**
 * The screen behind the paper: a dusk-blue gradient in three stops with a warm glow at the upper
 * right where the plane flies off, a vignette and grain (AGSL, API 33+; no P3 step).
 */
internal const val PLANE_BACKDROP_AGSL = """
uniform float2 size;

float h2(float2 q) { return fract(sin(dot(q, float2(127.1, 311.7))) * 43758.5453); }

half4 main(float2 p) {
  float2 uv = p / size;
  float3 a = float3(0.082, 0.106, 0.188);
  float3 b = float3(0.118, 0.137, 0.224);
  float3 c0 = float3(0.165, 0.149, 0.212);
  float3 c = mix(a, b, smoothstep(0.0, 0.5, uv.y));
  c = mix(c, c0, smoothstep(0.5, 1.0, uv.y));
  // Warm light at the upper right, where the planes leave.
  float2 g = (uv - float2(0.95, 0.08)) * float2(1.0, size.y / size.x);
  c += float3(0.16, 0.08, 0.05) * exp(-dot(g, g) * 3.0);
  float2 v = uv - 0.5;
  c *= 1.0 - 0.38 * smoothstep(0.3, 0.9, length(v * float2(1.1, 0.9)));
  c += (h2(floor(p)) - 0.5) * 0.04;
  return half4(half3(c), 1.0);
}
"""
