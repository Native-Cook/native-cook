// The block is built for Android 12 (API 31) and newer; the demo checks before showing it.
@file:androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.S)

package com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.console

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.sin

// Surfaces of the lifted console that only exist in 3D, painted with
// android.graphics into bitmaps Filament samples. Drawing units match
// DeviceArt: the housing is 1000 × 284. Port of console-back.ts.
private const val W = 1000f
private const val H = 284f

private fun face(weight: Int): Typeface = Typeface.create(Typeface.SANS_SERIF, weight, false)

private fun rgba(r: Int, g: Int, b: Int, a: Double) = Color.argb((a.coerceIn(0.0, 1.0) * 255).toFloat() / 255f, r / 255f, g / 255f, b / 255f)

// Deterministic noise so the brushing is the same on every lift.
private class Random(seed: Long) {
    private var s = seed
    fun next(): Double {
        s = (s * 16807) % 2147483647
        return s / 2147483647.0
    }
}

private fun spaced(c: Canvas, text: String, paint: Paint, cx: Float, y: Float, tracking: Float, color: Int) {
    paint.color = color
    val chars = text.map { it.toString() }
    // RN Skia measureText: ink-bounds widths (a space is 0).
    val widths = chars.map { paint.skiaMeasureWidth(it) }
    val total = widths.sum() + tracking * (text.length - 1)
    var x = cx - total / 2
    chars.forEachIndexed { i, ch ->
        c.drawText(ch, x, y, paint)
        x += widths[i] + tracking
    }
}

// Laser-etched text: a dark lip above, a faint catch-light below, then the
// frosted fill, so it reads as cut into the metal.
private fun engraved(c: Canvas, text: String, size: Float, weight: Int, cx: Float, y: Float, tracking: Float, alpha: Double) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face(weight); textSize = size }
    spaced(c, text, paint, cx, y - 1, tracking, rgba(0, 0, 0, 0.55 * alpha))
    spaced(c, text, paint, cx, y + 1, tracking, rgba(255, 255, 255, 0.14 * alpha))
    spaced(c, text, paint, cx, y, tracking, rgba(200, 200, 205, 0.75 * alpha))
}

private fun screw(c: Canvas, x: Float, y: Float) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.shader = LinearGradient(
        x, y - 12, x, y + 12,
        intArrayOf(0xFF5C5C60.toInt(), 0xFF2A2A2D.toInt(), 0xFF141416.toInt()), floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP,
    )
    c.drawCircle(x, y, 12f, p)
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.4f; color = rgba(0, 0, 0, 0.7)
    }
    c.drawCircle(x, y, 12.6f, ring)
    // Six-lobe drive.
    val drive = Path()
    for (i in 0 until 12) {
        val a = (i * Math.PI) / 6 - Math.PI / 2
        val r = if (i % 2 == 0) 5.6 else 3.2
        val px = (x + cos(a) * r).toFloat()
        val py = (y + sin(a) * r).toFloat()
        if (i == 0) drive.moveTo(px, py) else drive.lineTo(px, py)
    }
    drive.close()
    c.drawPath(drive, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0C0C0D.toInt() })
}

/** The back plate: dark anodized aluminium, brushed, with an etched mark. */
fun paintConsoleBack(c: Canvas) {
    val rr = RectF(0f, 0f, W, H)
    val base = Paint(Paint.ANTI_ALIAS_FLAG)
    base.shader = LinearGradient(
        0f, 0f, 0f, H,
        intArrayOf(0xFF5A5A5F.toInt(), 0xFF45454A.toInt(), 0xFF36363A.toInt()), floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP,
    )
    c.drawRoundRect(rr, H / 2, H / 2, base)

    c.save()
    c.clipPath(Path().apply { addRoundRect(rr, H / 2, H / 2, Path.Direction.CW) })
    val r = Random(11)
    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 0.7f }
    for (i in 0 until 420) {
        val y = (r.next() * H).toFloat()
        val light = r.next() < 0.5
        line.color = if (light) rgba(255, 255, 255, 0.012 + r.next() * 0.03) else rgba(0, 0, 0, 0.04 + r.next() * 0.07)
        val x0 = (r.next() * 300).toFloat()
        c.drawLine(x0, y, x0 + 500 + (r.next() * 500).toFloat(), y, line)
    }
    c.restore()

    engraved(c, "GLOWKNOB", 40f, 500, W / 2, 136f, 15f, 1.0)
    engraved(c, "TACTILE CONSOLE  ·  GK-01", 14f, 500, W / 2, 170f, 4.5f, 0.75)
    engraved(c, "Designed in Saigon  ·  Painted by Compose  ·  Lit by Filament", 11.5f, 400, W / 2, 226f, 1.2f, 0.55)

    // The two channels, as small inlaid dots beside the mark.
    val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    dot.color = Tactile.warm[1].toInt()
    c.drawCircle(W / 2 - 218, 122f, 4.5f, dot)
    dot.color = Tactile.cool[1].toInt()
    c.drawCircle(W / 2 + 218, 122f, 4.5f, dot)

    for ((x, y) in listOf(186f to 78f, 814f to 78f, 186f to 206f, 814f to 206f)) screw(c, x, y)

    // Rim: a soft dark edge where the plate meets the chamfer.
    val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f; color = rgba(0, 0, 0, 0.45)
    }
    c.drawRoundRect(rr, H / 2, H / 2, rim)
}

/** Knurled edge of the knob, tiled around the cylinder. Each ridge is a
 * soft gradient, not hard lines: the texture has no mipmaps, so hard edges
 * would shimmer as the knob turns. */
fun paintKnurl(c: Canvas, width: Int, height: Int) {
    val ridges = 16
    val step = width.toFloat() / ridges
    val ridge = Paint()
    ridge.shader = LinearGradient(
        0f, 0f, step, 0f,
        intArrayOf(0xFF1A1A1C.toInt(), 0xFF2E2E31.toInt(), 0xFF4E4E52.toInt(), 0xFF2E2E31.toInt(), 0xFF1A1A1C.toInt()),
        floatArrayOf(0f, 0.3f, 0.55f, 0.8f, 1f),
        Shader.TileMode.REPEAT,
    )
    c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), ridge)
}
