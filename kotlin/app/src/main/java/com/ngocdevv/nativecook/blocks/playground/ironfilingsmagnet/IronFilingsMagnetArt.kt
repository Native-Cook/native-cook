package com.ngocdevv.nativecook.blocks.playground.ironfilingsmagnet

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LightingColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/*
 Drawing for Iron Filings Magnet (a port of ironArt.ts): the walnut desk and the sheet of graph
 paper with its printed header and footer labels (rendered once into a bitmap), the two bar
 magnets seen faintly through the paper (one pre-rendered bitmap, drawn rotated per frame), the
 live readings, and the filing sprites: one atlas with the soft shadow and the metal in six
 darkness steps (React Native modulates a colour per filing; Swift uses the same six steps).
 */

internal const val IRON_INK = 0xFF2A2622.toInt()
internal const val IRON_INK_SOFT = 0xFF6F665B.toInt()
internal const val IRON_BACKGROUND = 0xFF2A1F18

/** A sprite cell: 12 × 3 dp drawn at 4 px per dp (sprite units), the atlas at SPRITE_SCALE × that. */
internal const val IRON_SPRITE_W = 48
internal const val IRON_SPRITE_H = 12
private const val SPRITE_SCALE = 2
/** Metal darkness steps for the tufts (k = 1 − 0.38 · stand). */
internal const val IRON_TINTS = 6

private fun weighted(base: Typeface, weight: Int): Typeface =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(base, weight, false)
    } else {
        Typeface.create(base, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
    }

private fun textPaint(face: Typeface, size: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    typeface = face
    textSize = size
    this.color = color
}

/** The six faces of the block (sizes in dp: the canvases are scaled by the density). */
internal class IronFonts {
    val kicker = textPaint(weighted(Typeface.SANS_SERIF, 700), 11f, IRON_INK_SOFT)
    val title = textPaint(weighted(Typeface.SERIF, 700), 34f, IRON_INK)
    val sub = textPaint(weighted(Typeface.SERIF, 400), 14f, IRON_INK_SOFT)
    val label = textPaint(weighted(Typeface.SANS_SERIF, 700), 10f, IRON_INK_SOFT)
    val value = textPaint(weighted(Typeface.SANS_SERIF, 600), 22f, IRON_INK)
    val pole = textPaint(weighted(Typeface.SANS_SERIF, 800), 12f, 0xFFFFFFFF.toInt())
}

private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

private fun argb(a: Float, r: Int, g: Int, b: Int) = ((a * 255).roundToInt() shl 24) or (r shl 16) or (g shl 8) or b

/** The filing outline in sprite units (48 × 12). */
private fun filingPath() = Path().apply {
    moveTo(1.5f, 6f)
    cubicTo(9f, 1.2f, 36f, 0.8f, 46.5f, 4.6f)
    lineTo(46.5f, 7.2f)
    cubicTo(36f, 11.2f, 9f, 10.8f, 1.5f, 6f)
    close()
}

/**
 * The sprite atlas: row 0 the filing's soft shadow, rows 1 … IRON_TINTS the metal (tapered,
 * gunmetal with a bright ridge) darkened in steps.
 */
internal fun ironSpriteAtlas(): Bitmap {
    val bmp = Bitmap.createBitmap(IRON_SPRITE_W * SPRITE_SCALE, IRON_SPRITE_H * SPRITE_SCALE * (IRON_TINTS + 1), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(SPRITE_SCALE.toFloat(), SPRITE_SCALE.toFloat())
    val path = filingPath()
    val shadow = fill(argb(0.55f, 40, 30, 18)).apply { maskFilter = BlurMaskFilter(sigmaToRadius(1.6f), BlurMaskFilter.Blur.NORMAL) }
    c.drawPath(path, shadow)
    val ridgePath = Path().apply {
        moveTo(8f, 4.2f)
        lineTo(38f, 3.8f)
    }
    for (j in 0 until IRON_TINTS) {
        val k = 1f - 0.38f * j / (IRON_TINTS - 1)
        val kc = (k * 255).roundToInt()
        val mul = LightingColorFilter((0xFF shl 24) or (kc shl 16) or (kc shl 8) or kc, 0)
        c.save()
        c.translate(0f, (IRON_SPRITE_H * (j + 1)).toFloat())
        val metal = fill(0xFF34353A.toInt()).apply {
            shader = LinearGradient(
                0f, 2f, 0f, 10f,
                intArrayOf(0xFF8E939C.toInt(), 0xFF3A3C42.toInt(), 0xFF17181C.toInt()),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP,
            )
            colorFilter = mul
        }
        c.drawPath(path, metal)
        val ridge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.9f
            strokeCap = Paint.Cap.ROUND
            color = argb(0.75f, 225, 230, 238)
            colorFilter = mul
        }
        c.drawPath(ridgePath, ridge)
        c.restore()
    }
    return bmp
}

/** Source rectangle of an atlas row in bitmap pixels. */
internal fun ironSpriteRow(row: Int) = Rect(
    0,
    row * IRON_SPRITE_H * SPRITE_SCALE,
    IRON_SPRITE_W * SPRITE_SCALE,
    (row + 1) * IRON_SPRITE_H * SPRITE_SCALE,
)

/** A noise bitmap of cells × cells grey values, the stand-in for Skia's fractal noise grain. */
private fun noise(cw: Int, ch: Int, seed: Int): Bitmap {
    val rnd = Random(seed)
    val px = IntArray(cw * ch) {
        val v = rnd.nextInt(256)
        (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    return Bitmap.createBitmap(px, cw, ch, Bitmap.Config.ARGB_8888)
}

/** Desk, sheet, ruling, header and footer labels, at density px per dp. */
internal fun ironPaperBitmap(g: IronGeo, fonts: IronFonts, density: Float): Bitmap {
    val bw = max(1, (g.w * density).roundToInt())
    val bh = max(1, (g.h * density).roundToInt())
    val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(density, density)
    val full = RectF(0f, 0f, g.w, g.h)
    val desk = Paint().apply {
        shader = LinearGradient(
            0f, 0f, g.w, g.h,
            intArrayOf(0xFF3B2C22.toInt(), 0xFF2A1F18.toInt(), 0xFF1E1612.toInt()),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    c.drawRect(full, desk)
    // Low-frequency mottling (Skia: fractal noise at 0.02, 10 %): 12 dp cells, smoothed.
    val coarse = noise(ceil(g.w / 12f).toInt() + 1, ceil(g.h / 12f).toInt() + 1, 11)
    c.drawBitmap(coarse, null, RectF(0f, 0f, coarse.width * 12f, coarse.height * 12f), Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 26 })

    val sheet = RectF(g.paperX, g.paperY, g.paperX + g.paperW, g.paperY + g.paperH)
    val amb = fill(argb(0.45f, 0, 0, 0)).apply { maskFilter = BlurMaskFilter(sigmaToRadius(14f), BlurMaskFilter.Blur.NORMAL) }
    c.save()
    c.translate(0f, 10f)
    c.drawRoundRect(sheet, 6f, 6f, amb)
    c.restore()
    val contact = fill(argb(0.5f, 0, 0, 0)).apply { maskFilter = BlurMaskFilter(sigmaToRadius(1.5f), BlurMaskFilter.Blur.NORMAL) }
    c.save()
    c.translate(0f, 1.5f)
    c.drawRoundRect(sheet, 6f, 6f, contact)
    c.restore()
    val paper = fill(0xFFEFE6D2.toInt()).apply {
        shader = RadialGradient(
            g.w * 0.45f, g.paperY + g.paperH * 0.4f, max(1f, g.paperH * 0.75f),
            intArrayOf(0xFFF4ECDA.toInt(), 0xFFEDE3CE.toInt(), 0xFFE2D5BB.toInt()),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    c.drawRoundRect(sheet, 6f, 6f, paper)
    c.save()
    val clip = Path().apply { addRoundRect(sheet, 6f, 6f, Path.Direction.CW) }
    c.clipPath(clip)
    // Fine paper grain (Skia: fractal noise at 0.8, 6 %): one cell per dp.
    val fine = noise(ceil(g.w).toInt() + 1, ceil(g.h).toInt() + 1, 5)
    c.drawBitmap(fine, null, RectF(0f, 0f, fine.width.toFloat(), fine.height.toFloat()), Paint().apply { alpha = 15 })
    // Graph ruling, every fifth line a little darker, and the red margin.
    val step = 9f * g.u
    val strong = fill(argb(0.13f, 70, 110, 160))
    val soft = fill(argb(0.06f, 70, 110, 160))
    var k = 0
    var x = g.paperX + 6
    while (x < g.paperX + g.paperW) {
        c.drawRect(x, g.paperY, x + 0.6f, g.paperY + g.paperH, if (k % 5 == 0) strong else soft)
        x += step
        k += 1
    }
    k = 0
    var y = g.paperY + 6
    while (y < g.paperY + g.paperH) {
        c.drawRect(g.paperX, y, g.paperX + g.paperW, y + 0.6f, if (k % 5 == 0) strong else soft)
        y += step
        k += 1
    }
    c.drawRect(g.paperX + 22, g.paperY, g.paperX + 22.9f, g.paperY + g.paperH, fill(argb(0.35f, 196, 74, 70)))
    c.restore()

    // Printed header.
    val hx = g.paperX + 34
    c.drawText("FIELD NOTES  ·  NO. 7", hx, g.paperY + 34 * g.u, fonts.kicker)
    c.drawText("Magnetism", hx, g.paperY + 72 * g.u, fonts.title)
    c.drawText("Exp. 04 — A bar magnet under the page", hx, g.paperY + 94 * g.u, fonts.sub)
    val date = "Oct 25"
    c.drawText(date, g.paperX + g.paperW - 18 - fonts.sub.measureText(date), g.paperY + 34 * g.u, fonts.sub)
    // Footer labels (values are drawn live).
    val fy = g.footerY
    c.drawRect(hx, fy - 14 * g.u, g.paperX + g.paperW - 18, fy - 14 * g.u + 0.8f, fill(argb(0.25f, 42, 38, 34)))
    val cols = ironFooterColumns(g)
    val labels = arrayOf("FIELD AT CENTRE", "POLES", "FILINGS")
    for (i in 0 until 3) c.drawText(labels[i], cols[i], fy + 6 * g.u, fonts.label)
    c.drawText("$IRON_FILINGS", cols[2], fy + 30 * g.u, fonts.value)
    coarse.recycle()
    fine.recycle()
    return bmp
}

internal fun ironFooterColumns(g: IronGeo): FloatArray {
    val x = g.paperX + 34
    return floatArrayOf(x, x + (g.paperW - 52) * 0.42f, x + (g.paperW - 52) * 0.72f)
}

/**
 * One bar magnet in its own frame, without labels: blue south half on the left, red north half on
 * the right, soft-edged. The bitmap covers (−L − PAD … L + PAD) × (−T − PAD … T + PAD) dp.
 */
internal class IronMagnetSprite(g: IronGeo, density: Float) {
    val halfLength = g.half + 8 * g.u
    val halfThick = g.thick
    val pad = 4f
    val bitmap: Bitmap
    val dst = RectF(-halfLength - pad, -halfThick - pad, halfLength + pad, halfThick + pad)

    init {
        val l = halfLength
        val t = halfThick
        val bw = max(1, ((2 * (l + pad)) * density).roundToInt())
        val bh = max(1, ((2 * (t + pad)) * density).roundToInt())
        bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        c.scale(bw / (2 * (l + pad)), bh / (2 * (t + pad)))
        c.translate(l + pad, t + pad)
        val blur = BlurMaskFilter(sigmaToRadius(0.9f), BlurMaskFilter.Blur.NORMAL)
        fun gradient(stops: IntArray) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, -t, 0f, t, stops, floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            maskFilter = blur
        }
        val south = gradient(intArrayOf(0xFF7EA2D6.toInt(), 0xFF3E6AAE.toInt(), 0xFF2A4C86.toInt()))
        c.drawRoundRect(RectF(-l, -t, 0f, t), 4f, 4f, south)
        val north = gradient(intArrayOf(0xFFE58A80.toInt(), 0xFFC8443B.toInt(), 0xFF9A2F29.toInt()))
        c.drawRoundRect(RectF(0f, -t, l, t), 0.5f, 0.5f, north)
        c.drawRoundRect(RectF(l - 8, -t, l, t), 4f, 4f, north)
    }
}

/** Reused paints for the per-frame layer. */
internal class IronLivePaints {
    val sprite = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    val magnet = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = (0.66f * 255).roundToInt() }
    val tab = fill(IRON_INK)
    val chevron = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = 0xFFEFE6D2.toInt()
    }
    val chevronPath = Path()
    val tabRect = RectF()
}

/** A magnet seen through the paper at pose (x, y, a), its N and S printed on it. */
private fun magnet(c: Canvas, g: IronGeo, sprite: IronMagnetSprite, paints: IronLivePaints, fonts: IronFonts, x: Float, y: Float, a: Float) {
    c.save()
    c.translate(x, y)
    c.rotate(Math.toDegrees(a.toDouble()).toFloat())
    c.drawBitmap(sprite.bitmap, null, sprite.dst, paints.magnet)
    val l = sprite.halfLength
    val p = fonts.pole
    c.drawText("N", l - 16 * g.u - p.measureText("N") / 2, 4 * g.u, p)
    c.drawText("S", -l + 16 * g.u - p.measureText("S") / 2, 4 * g.u, p)
    c.restore()
}

/** Magnets (under the filings) and B's pull tab while it is parked. */
internal fun ironDrawUnder(c: Canvas, g: IronGeo, m: FloatArray, sprite: IronMagnetSprite, paints: IronLivePaints, fonts: IronFonts) {
    magnet(c, g, sprite, paints, fonts, m[0], m[1], m[2])
    magnet(c, g, sprite, paints, fonts, m[3], m[4], m[5])
    val parked = max(0f, min(1f, (m[3] - (g.w - 40 * g.u)) / (40 * g.u)))
    if (parked > 0.01f) {
        val tx = g.paperX + g.paperW - 30
        paints.tab.alpha = (0.85f * parked * 255).roundToInt()
        paints.tabRect.set(tx, m[4] - 22, tx + 26, m[4] + 22)
        c.drawRoundRect(paints.tabRect, 13f, 13f, paints.tab)
        paints.chevron.alpha = (parked * 255).roundToInt()
        val path = paints.chevronPath
        path.rewind()
        path.moveTo(tx + 15, m[4] - 7)
        path.lineTo(tx + 9, m[4])
        path.lineTo(tx + 15, m[4] + 7)
        c.drawPath(path, paints.chevron)
    }
}

/** The live readings at the foot of the page. */
internal fun ironDrawReadings(c: Canvas, g: IronGeo, millitesla: Float, poles: Int, fonts: IronFonts, cols: FloatArray) {
    val y = g.footerY + 30 * g.u
    val v = millitesla.roundToInt().toString()
    c.drawText(v, cols[0], y, fonts.value)
    c.drawText(" mT", cols[0] + fonts.value.measureText(v), y, fonts.sub)
    c.drawText(poles.toString(), cols[1], y, fonts.value)
}
