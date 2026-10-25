package com.ngocdevv.nativecook.blocks.onboarding.seedsproutwelcome

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.ngocdevv.nativecook.blocks.onboarding.seedsproutwelcome.SeedSproutWelcomeMotion.hash
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Static art for the wall, the sill, the pot, the can and the plant's parts, ported from
 * seedArt.ts / SeedParts.tsx. Everything that never changes shape is drawn once, off the main
 * thread, into bitmaps: the whole back (wall, window light, grain, sill, pot) at screen
 * resolution, and sprites (leaf, petal, can, marker, the wet sheen) in their own frames, so a
 * frame only places them. Skia's `Shadow` and `BlurMask` become `setShadowLayer` and
 * `BlurMaskFilter` on a software canvas; the grain shader is the same hash, computed on the CPU.
 */

/** A pre-rendered piece of art covering [left, top, width, height] in its own frame (dp). */
internal class SeedSprite(val image: ImageBitmap, val left: Float, val top: Float, val width: Float, val height: Float)

internal class SeedArt(
    val back: ImageBitmap,
    val leaf: SeedSprite,
    val petal: SeedSprite,
    val can: SeedSprite,
    val marker: SeedSprite,
    val wet: SeedSprite,
)

private fun argb(r: Int, g: Int, b: Int, a: Float) = (Math.round(a * 255) shl 24) or (r shl 16) or (g shl 8) or b

private fun linear(x0: Float, y0: Float, x1: Float, y1: Float, colors: IntArray, stops: FloatArray? = null) =
    LinearGradient(x0, y0, x1, y1, colors, stops, Shader.TileMode.CLAMP)

private fun fill(shader: Shader? = null, color: Int = -0x1000000) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
    this.color = color
    this.shader = shader
}

/** Draws [path] with Skia-style drop shadows (dy, sigma, colour) under it, then the fill. */
private fun Canvas.shadowed(path: Path, paint: Paint, vararg shadows: Triple<Float, Float, Int>) {
    for (s in shadows) {
        paint.setShadowLayer(sigmaToRadius(s.second), 0f, s.first, s.third)
        drawPath(path, paint)
    }
    paint.clearShadowLayer()
    drawPath(path, paint)
}

private fun blurred(color: Int, sigma: Float) = fill(color = color).apply {
    maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
}

private fun sprite(left: Float, top: Float, width: Float, height: Float, res: Float, draw: Canvas.() -> Unit): SeedSprite {
    val bmp = Bitmap.createBitmap(ceil(width * res).toInt().coerceAtLeast(1), ceil(height * res).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(res, res)
    c.translate(-left, -top)
    c.draw()
    return SeedSprite(bmp.asImageBitmap(), left, top, width, height)
}

// Paths, in pot coordinates or in each part's own frame.

private fun potBody() = Path().apply {
    moveTo(-70f, 12f)
    lineTo(70f, 12f)
    cubicTo(66f, 50f, 60f, 86f, 54f, 104f)
    quadTo(52f, 112f, 44f, 112f)
    lineTo(-44f, 112f)
    quadTo(-52f, 112f, -54f, 104f)
    cubicTo(-60f, 86f, -66f, 50f, -70f, 12f)
    close()
}

private fun potRim() = Path().apply { addRoundRect(RectF(-80f, -8f, 80f, 18f), 9f, 9f, Path.Direction.CW) }

private fun leafPath() = Path().apply {
    moveTo(0f, 0f)
    cubicTo(12f, -14f, 34f, -13f, 50f, 0f)
    cubicTo(34f, 12f, 12f, 13f, 0f, 0f)
    close()
}

private fun leafVeins() = Path().apply {
    moveTo(2f, 0f)
    quadTo(26f, -1.5f, 47f, 0f)
    for (x in floatArrayOf(12f, 22f, 32f)) {
        moveTo(x, -0.6f)
        lineTo(x + 7, -6.5f)
        moveTo(x + 2, 0.4f)
        lineTo(x + 8, 6f)
    }
}

private fun petalPath() = Path().apply {
    moveTo(0f, 0f)
    cubicTo(-12f, -8f, -11f, -30f, -4f, -36f)
    quadTo(0f, -32f, 4f, -36f)
    cubicTo(11f, -30f, 12f, -8f, 0f, 0f)
    close()
}

private fun canBody() = Path().apply {
    moveTo(-40f, -30f)
    lineTo(40f, -30f)
    cubicTo(44f, -30f, 46f, -27f, 46f, -22f)
    lineTo(50f, 30f)
    quadTo(50f, 36f, 44f, 36f)
    lineTo(-44f, 36f)
    quadTo(-50f, 36f, -50f, 30f)
    lineTo(-46f, -22f)
    cubicTo(-46f, -27f, -44f, -30f, -40f, -30f)
    close()
}

private fun canSpout() = Path().apply {
    moveTo(-44f, 8f)
    lineTo(-80f, -20f)
    lineTo(-84f, -16f)
    lineTo(-46f, 22f)
    close()
}

private fun canRose() = Path().apply {
    moveTo(-78f, -24f)
    lineTo(-92f, -34f)
    quadTo(-100f, -26f, -94f, -14f)
    lineTo(-84f, -12f)
    close()
}

private fun canHandle() = Path().apply {
    moveTo(-18f, -30f)
    cubicTo(-14f, -66f, 30f, -66f, 36f, -30f)
    moveTo(42f, -16f)
    cubicTo(66f, -14f, 66f, 18f, 48f, 22f)
}

/** Soft daylight from a window, falling across the wall in two slanted panes. */
private fun windowLight(w: Float, h: Float) = Path().apply {
    for (p in arrayOf(floatArrayOf(w * 0.08f, h * 0.08f, w * 0.32f), floatArrayOf(w * 0.46f, h * 0.04f, w * 0.3f))) {
        val (x, y, pw) = Triple(p[0], p[1], p[2])
        moveTo(x, y)
        lineTo(x + pw, y)
        lineTo(x + pw - h * 0.22f, y + h * 0.5f)
        lineTo(x - h * 0.22f, y + h * 0.5f)
        close()
    }
}

/** Crumbs of soil on its surface (drawn every frame, clipped to the soil). */
internal fun seedSoilCrumbs() = androidx.compose.ui.graphics.Path().apply {
    for (i in 0 until 34) {
        val a = hash(i * 1.9) * PI * 2
        val r = sqrt(hash(i * 3.7))
        val rad = (0.8 + 1.4 * hash(i * 5.3)).toFloat()
        val cx = (cos(a) * r * 60).toFloat()
        val cy = (sin(a) * r * 7).toFloat()
        addOval(androidx.compose.ui.geometry.Rect(cx - rad, cy - rad, cx + rad, cy + rad))
    }
}

/** A seed half: an almond, split along its length. */
internal fun seedHalf(dir: Float) = androidx.compose.ui.graphics.Path().apply {
    moveTo(-8f, 0f)
    quadraticTo(0f, dir * 10, 8f, 0f)
    close()
}

/** The bud: a teardrop on the stem's tip, pointing up. */
internal fun seedBud() = androidx.compose.ui.graphics.Path().apply {
    moveTo(0f, 2f)
    cubicTo(-12f, -2f, -9f, -18f, 0f, -26f)
    cubicTo(9f, -18f, 12f, -2f, 0f, 2f)
    close()
}

/** Stamens around the flower's centre. */
internal fun seedStamens() = androidx.compose.ui.graphics.Path().apply {
    for (i in 0 until 14) {
        val a = i / 14.0 * PI * 2 + hash(i.toDouble()) * 0.3
        val r = 6 + 3 * hash(i * 2.3)
        val x = (cos(a) * r).toFloat()
        val y = (sin(a) * r).toFloat()
        addOval(androidx.compose.ui.geometry.Rect(x - 1.1f, y - 1.1f, x + 1.1f, y + 1.1f))
    }
}

/** The grain's hash, as in the shader: fract(sin(dot(p, (12.9898, 78.233))) * 43758.5453). */
private fun grainHash(x: Double, y: Double): Double {
    val s = sin(x * 12.9898 + y * 78.233) * 43758.5453
    return s - floor(s)
}

/**
 * The grain shader's output, one pixel per cell of its fine octave (1.4 cells a dp):
 * n = h(floor(p · 1.4)) · 0.6 + h(floor(p · 0.4) + 23) · 0.4, grey and opaque.
 */
private fun grainBitmap(w: Float, h: Float): Bitmap {
    val cw = ceil(w * 1.4f).toInt() + 1
    val ch = ceil(h * 1.4f).toInt() + 1
    val px = IntArray(cw * ch)
    for (y in 0 until ch) {
        val cy = floor((y + 0.5) / 1.4 * 0.4) + 23
        for (x in 0 until cw) {
            val cx = floor((x + 0.5) / 1.4 * 0.4) + 23
            val n = grainHash(x.toDouble(), y.toDouble()) * 0.6 + grainHash(cx, cy) * 0.4
            val v = (n * 255).toInt().coerceIn(0, 255)
            px[y * cw + x] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
    }
    return Bitmap.createBitmap(px, cw, ch, Bitmap.Config.ARGB_8888)
}

/** A soft-light paint of the grain at [amount], in the canvas's local dp (overlay before API 29). */
private fun grainPaint(grain: Bitmap, amount: Float) = Paint().apply {
    isFilterBitmap = false
    shader = BitmapShader(grain, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
        setLocalMatrix(Matrix().apply { setScale(1 / 1.4f, 1 / 1.4f) })
    }
    alpha = Math.round(amount * 255)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) blendMode = android.graphics.BlendMode.SOFT_LIGHT
    else xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
}

/** Everything static, for a screen of [w] × [h] dp at [density] with the pot at ([ox], [oy]) × [s]. */
internal fun bakeSeedArt(w: Float, h: Float, density: Float, shelfY: Float, ox: Float, oy: Float, s: Float): SeedArt {
    val grain = grainBitmap(w, h)

    // The back: wall, window light, grain, the sill and the pot.
    val back = Bitmap.createBitmap(ceil(w * density).toInt().coerceAtLeast(1), ceil(h * density).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    Canvas(back).apply {
        scale(density, density)
        drawRect(0f, 0f, w, h, fill(linear(0f, 0f, w * 0.4f, h, intArrayOf(0xFFEEF2E8.toInt(), 0xFFE0E8D8.toInt(), 0xFFCFDCC6.toInt()))))
        drawPath(windowLight(w, h), blurred(argb(255, 253, 240, 0.42f), 26f))
        drawRect(0f, 0f, w, h, grainPaint(grain, 0.42f))
        val shelf = Path().apply { addRect(0f, shelfY, w, shelfY + 20, Path.Direction.CW) }
        shadowed(
            shelf,
            fill(linear(0f, shelfY, 0f, shelfY + 20, intArrayOf(0xFFD9B48A.toInt(), 0xFFC79C6E.toInt(), 0xFFA97E52.toInt()))),
            Triple(10f, 18f, argb(60, 50, 30, 0.22f)),
        )
        drawRect(0f, shelfY, w, shelfY + 1.5f, fill(color = argb(255, 244, 224, 0.8f)))

        save()
        translate(ox, oy)
        scale(s, s)
        drawOval(RectF(-86f, 102f, 86f, 122f), blurred(argb(50, 40, 20, 0.3f), 8f))
        restore()
    }
    // The pot: its soft-light grain is composed in a layer and cut back to the body's outline.
    Canvas(back).apply {
        scale(density, density)
        translate(ox, oy)
        scale(s, s)
        val body = potBody()
        saveLayer(RectF(-72f, 10f, 72f, 114f), null)
        drawPath(
            body,
            fill(linear(-70f, 0f, 70f, 0f, intArrayOf(0xFF9C4527.toInt(), 0xFFD07A4C.toInt(), 0xFFB45A33.toInt(), 0xFF8E3D21.toInt()), floatArrayOf(0f, 0.32f, 0.62f, 1f))),
        )
        drawRect(RectF(-72f, 10f, 72f, 114f), grainPaint(grain, 0.32f))
        // Keep only what lies on the body.
        drawPath(Path().apply {
            addRect(RectF(-72f, 10f, 72f, 114f), Path.Direction.CW)
            op(body, Path.Op.DIFFERENCE)
        }, Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) })
        restore()
        drawRect(RectF(-70f, 18f, 70f, 28f), blurred(argb(70, 25, 10, 0.25f), 4f))
        shadowed(
            potRim(),
            fill(linear(-80f, 0f, 80f, 0f, intArrayOf(0xFFA84E2B.toInt(), 0xFFDC8A5A.toInt(), 0xFFBE6439.toInt(), 0xFF953F22.toInt()), floatArrayOf(0f, 0.3f, 0.62f, 1f))),
            Triple(2f, 3f, argb(80, 30, 10, 0.35f)),
        )
        drawRect(RectF(-76f, -7f, 76f, -5.5f), fill(color = argb(255, 214, 180, 0.6f)))
    }
    grain.recycle()

    val base = density * s
    val leaf = sprite(-10f, -22f, 70f, 46f, base * 1.25f) {
        shadowed(leafPath(), fill(linear(0f, -12f, 50f, 12f, intArrayOf(0xFF86BE68.toInt(), 0xFF55923F.toInt(), 0xFF336F2B.toInt()))), Triple(2f, 3f, argb(30, 60, 20, 0.3f)))
        drawPath(leafVeins(), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            strokeCap = Paint.Cap.ROUND
            color = argb(220, 245, 190, 0.45f)
        })
    }
    val petal = sprite(-20f, -44f, 40f, 54f, base * 1.3f) {
        shadowed(petalPath(), fill(linear(0f, 0f, 0f, -36f, intArrayOf(0xFFD9573F.toInt(), 0xFFEE7A62.toInt(), 0xFFF8B39E.toInt()))), Triple(1.5f, 2.5f, argb(120, 30, 20, 0.3f)))
    }
    val can = sprite(-110f, -80f, 190f, 180f, base) {
        drawPath(canHandle(), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 7f
            strokeCap = Paint.Cap.ROUND
            color = 0xFF2F5E57.toInt()
        })
        drawPath(canSpout(), fill(linear(-80f, -20f, -60f, 20f, intArrayOf(0xFF5E9D93.toInt(), 0xFF3E7870.toInt(), 0xFF2B5A54.toInt()))))
        drawPath(canRose(), fill(linear(-100f, -30f, -80f, -10f, intArrayOf(0xFFD9B26A.toInt(), 0xFFB88B40.toInt(), 0xFF8E6626.toInt()))))
        shadowed(
            canBody(),
            fill(linear(-50f, 0f, 50f, 0f, intArrayOf(0xFF3A746C.toInt(), 0xFF67A79C.toInt(), 0xFF4A8A80.toInt(), 0xFF2E5F59.toInt()), floatArrayOf(0f, 0.3f, 0.6f, 1f))),
            Triple(10f, 18f, argb(30, 50, 40, 0.28f)),
            Triple(1.5f, 2.5f, argb(20, 40, 30, 0.35f)),
        )
        drawRect(-46f, -30f, 46f, -23f, fill(color = argb(255, 255, 255, 0.18f)))
        drawRect(-26f, -20f, -20f, 28f, blurred(argb(255, 255, 255, 0.22f), 2f))
    }
    // The wooden marker, in its own frame (the group's translate (−44, −4), turned −0.12 rad).
    val marker = sprite(-26f, -68f, 52f, 82f, base * 1.2f) {
        drawRect(-2.5f, -30f, 2.5f, 6f, fill(color = 0xFFC9A574.toInt()))
        shadowed(
            Path().apply { addRoundRect(RectF(-17f, -58f, 17f, -28f), 4f, 4f, Path.Direction.CW) },
            fill(linear(-16f, -58f, 16f, -30f, intArrayOf(0xFFEBD3A8.toInt(), 0xFFD9BA86.toInt(), 0xFFC49F68.toInt()))),
            Triple(2f, 3f, argb(70, 45, 20, 0.3f)),
        )
        drawText("Cosmos", 0f, -40f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
            textSize = 10f
            textAlign = Paint.Align.CENTER
            color = 0xFF5B4325.toInt()
        })
    }
    val wet = sprite(-66f, -23f, 132f, 44f, base * 0.5f) {
        drawOval(RectF(-50f, -7f, 50f, 5f), blurred(argb(255, 255, 255, 0.12f), 4f))
    }
    return SeedArt(back.asImageBitmap(), leaf, petal, can, marker, wet)
}
