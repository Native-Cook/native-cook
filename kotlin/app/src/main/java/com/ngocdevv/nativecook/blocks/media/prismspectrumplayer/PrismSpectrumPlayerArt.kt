package com.ngocdevv.nativecook.blocks.media.prismspectrumplayer

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.os.Build
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 The player's controls (a port of prismSpectrumArt.ts), drawn over the hero. The static ones
 (header icons, heart, previous / next / forward, the "up next" card) are rendered once into a
 bitmap; the moving ones (scrubber, times, back-5 press, play button with its play ↔ pause morph)
 are drawn every frame from the pose, which is a handful of draws. The soft shadows and the
 play button's rainbow rim are bitmaps made once and scaled with the press.
 */

private const val INK = 0xFFEEEAF8.toInt()
private val INK_DIM = argb(0.55f, 238, 234, 248)
private val TRACK = argb(0.16f, 238, 234, 248)
internal const val PRISM_PLAY_R = 34f
private const val PIF = PI.toFloat()
private val RAINBOW = intArrayOf(0xFFFF6B8B.toInt(), 0xFFFFB36B.toInt(), 0xFFF6EF7A.toInt(), 0xFF7AF0A6.toInt(), 0xFF6BC8FF.toInt(), 0xFFA18BFF.toInt())

private fun argb(a: Float, r: Int, g: Int, b: Int) = ((a.coerceIn(0f, 1f) * 255).roundToInt() shl 24) or (r shl 16) or (g shl 8) or b

private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
private fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color
    style = Paint.Style.STROKE
    strokeWidth = width
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
}

private fun weighted(base: Typeface, weight: Int): Typeface =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(base, weight, false)
    } else {
        Typeface.create(base, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
    }

internal class PrismFonts {
    val mono = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = weighted(Typeface.MONOSPACE, 500); textSize = 12f; color = INK_DIM }
    val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = weighted(Typeface.SANS_SERIF, 700); textSize = 9f; color = INK }
}

/** Button centres along the controls row. */
internal fun prismControlX(g: PrismGeo, i: Int): Float {
    val step = min(70f, g.w * 0.178f)
    return g.w / 2 + (i - 2) * step * (if (i == 0 || i == 4) 0.98f else 1f)
}

/** A skip icon: a bar and a triangle, pointing left (dir −1) or right (dir 1). */
private fun skip(c: Canvas, x: Float, y: Float, dir: Int, s: Float) {
    val p = Path().apply {
        moveTo(x - dir * 7 * s, y - 9 * s)
        lineTo(x + dir * 6 * s, y)
        lineTo(x - dir * 7 * s, y + 9 * s)
        close()
    }
    val paint = fill(INK)
    c.drawPath(p, paint)
    c.drawPath(p, stroke(INK, 2.2f * s))
    val bx = x + dir * 8.5f * s - 1.6f * s
    c.drawRoundRect(RectF(bx, y - 9 * s, bx + 3.2f * s, y + 9 * s), 1.6f * s, 1.6f * s, paint)
}

/** The paints and paths of a "5 seconds" arrow, kept for the per-frame back-5 button. */
private class JumpArt {
    val ring = stroke(INK, 2f)
    val head = fill(INK)
    val arc = Path()
    val tip = Path()
    val oval = RectF()
}

/** A circular "5 seconds" arrow: back (dir −1) or forward (dir 1). */
private fun jump(c: Canvas, x: Float, y: Float, dir: Int, s: Float, font: Paint, scale: Float, art: JumpArt) {
    c.save()
    c.translate(x, y)
    c.scale(scale, scale)
    val r = 12 * s
    // The ring is open at the top, on the side the arrow points to.
    val start = if (dir < 0) -110f else -70f
    val sweep = if (dir < 0) 300f else -300f
    art.oval.set(-r, -r, r, r)
    art.arc.rewind()
    art.arc.addArc(art.oval, start, sweep)
    art.ring.strokeWidth = 2 * s
    c.drawPath(art.arc, art.ring)
    val a = start * PIF / 180
    val px = cos(a) * r
    val py = sin(a) * r
    // Tangent against the sweep: the head points where the ring was opened.
    val tx = if (dir < 0) sin(a) else -sin(a)
    val ty = if (dir < 0) -cos(a) else cos(a)
    art.tip.rewind()
    art.tip.moveTo(px + tx * 5.5f * s, py + ty * 5.5f * s)
    art.tip.lineTo(px - ty * 4 * s - tx * 0.5f * s, py + tx * 4 * s - ty * 0.5f * s)
    art.tip.lineTo(px + ty * 4 * s - tx * 0.5f * s, py - tx * 4 * s - ty * 0.5f * s)
    art.tip.close()
    c.drawPath(art.tip, art.head)
    c.drawText("5", -font.measureText("5") / 2, 3.6f * s, font)
    c.restore()
}

/** Heart outline. */
private fun heart(c: Canvas, x: Float, y: Float, s: Float) {
    val p = Path().apply {
        moveTo(x, y + 8 * s)
        cubicTo(x - 12 * s, y, x - 10.5f * s, y - 9.5f * s, x - 5 * s, y - 9 * s)
        cubicTo(x - 2.2f * s, y - 8.8f * s, x - 0.6f * s, y - 6.8f * s, x, y - 5.2f * s)
        cubicTo(x + 0.6f * s, y - 6.8f * s, x + 2.2f * s, y - 8.8f * s, x + 5 * s, y - 9 * s)
        cubicTo(x + 10.5f * s, y - 9.5f * s, x + 12 * s, y, x, y + 8 * s)
        close()
    }
    c.drawPath(p, stroke(INK, 1.8f * s))
}

private fun controlScale(g: PrismGeo) = max(0.85f, min(1f, g.u))

/** Everything that never moves, at density px per dp: header icons, heart, previous / next / forward 5, the up-next card. */
internal fun prismStaticBitmap(g: PrismGeo, fonts: PrismFonts, density: Float): Bitmap {
    val bmp = Bitmap.createBitmap(max(1, (g.w * density).roundToInt()), max(1, (g.h * density).roundToInt()), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(density, density)
    val s = controlScale(g)
    // Header: chevron down on the left, ellipsis on the right.
    val hy = g.top + 24
    c.drawPath(Path().apply {
        moveTo(24f, hy - 3.5f)
        lineTo(31f, hy + 3.5f)
        lineTo(38f, hy - 3.5f)
    }, stroke(INK, 2.2f))
    for (i in 0 until 3) c.drawCircle(g.w - 38 + i * 7, hy, 1.8f, fill(INK))
    heart(c, g.w - 36, g.titleTop + 20, 1f)
    skip(c, prismControlX(g, 1), g.controlsY, -1, s)
    skip(c, prismControlX(g, 3), g.controlsY, 1, s)
    jump(c, prismControlX(g, 4), g.controlsY, 1, s, fonts.small, 1f, JumpArt())
    // The "up next" card: a glassy row with a small cover (a spectrum in a dark square).
    val cardY = g.h - g.bottom - 76
    c.drawRoundRect(RectF(16f, cardY, g.w - 16, cardY + 64), 18f, 18f, fill(argb(0.06f, 238, 234, 248)))
    c.drawRoundRect(RectF(16.5f, cardY + 0.5f, g.w - 16.5f, cardY + 63.5f), 17.5f, 17.5f, stroke(argb(0.10f, 238, 234, 248), 1f))
    val cover = fill(0xFF1A1838.toInt()).apply {
        shader = LinearGradient(26f, cardY + 10, 70f, cardY + 54, intArrayOf(0xFF2B2160.toInt(), 0xFF151530.toInt(), 0xFF0E1A2E.toInt()), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    }
    c.drawRoundRect(RectF(26f, cardY + 10, 70f, cardY + 54), 10f, 10f, cover)
    for (i in RAINBOW.indices) {
        c.drawLine(34f, cardY + 34, 66f, cardY + 22 + i * 4.2f, stroke(RAINBOW[i], 2f))
    }
    // Queue icon on the card.
    val qy = cardY + 32
    for (i in 0 until 3) {
        c.drawCircle(g.w - 44, qy - 6 + i * 6, 1.4f, fill(INK_DIM))
        c.drawLine(g.w - 39, qy - 6 + i * 6, g.w - 26, qy - 6 + i * 6, stroke(INK_DIM, 1.6f))
    }
    return bmp
}

/** A soft disc or ring drawn once and placed (scaled) around a centre. */
private class PrismSoft(val radius: Float, val pad: Float, density: Float, draw: (Canvas) -> Unit) {
    val bitmap: Bitmap = Bitmap.createBitmap(
        max(1, ((radius + pad) * 2 * density).roundToInt()),
        max(1, ((radius + pad) * 2 * density).roundToInt()),
        Bitmap.Config.ARGB_8888,
    ).also {
        val cv = Canvas(it)
        cv.scale(it.width / ((radius + pad) * 2), it.height / ((radius + pad) * 2))
        cv.translate(radius + pad, radius + pad)
        draw(cv)
    }
    private val dst = RectF()

    /** Draws it centred at (x, y), scaled so its radius is r. */
    fun draw(c: Canvas, x: Float, y: Float, r: Float, paint: Paint) {
        val k = r / radius
        val e = (radius + pad) * k
        dst.set(x - e, y - e, x + e, y + e)
        c.drawBitmap(bitmap, null, dst, paint)
    }
}

/** The moving controls: scrubber, times, back-5 and the play button. */
internal class PrismDynamicArt(private val g: PrismGeo, private val fonts: PrismFonts, density: Float) {
    private val s = controlScale(g)
    private val baseR = PRISM_PLAY_R * s
    private val track = fill(TRACK)
    private val done = fill(INK)
    private val knob = fill(0xFFF4F1FC.toInt())
    private val knobShadow = PrismSoft(6.5f, 8f, density) { it.drawCircle(0f, 0f, 6.5f, fill(argb(0.45f, 6, 6, 24)).apply { maskFilter = BlurMaskFilter(sigmaToRadius(2.5f), BlurMaskFilter.Blur.NORMAL) }) }
    private val ambient = PrismSoft(baseR * 0.92f, 36f, density) { it.drawCircle(0f, 0f, baseR * 0.92f, fill(argb(0.42f, 70, 58, 170)).apply { maskFilter = BlurMaskFilter(sigmaToRadius(14f), BlurMaskFilter.Blur.NORMAL) }) }
    private val contact = PrismSoft(baseR, 6f, density) { it.drawCircle(0f, 0f, baseR, fill(argb(0.5f, 6, 6, 24)).apply { maskFilter = BlurMaskFilter(sigmaToRadius(2f), BlurMaskFilter.Blur.NORMAL) }) }
    private val rim = PrismSoft(baseR + 3, 8f, density) {
        it.drawCircle(0f, 0f, baseR + 3, stroke(0xFFFFFFFF.toInt(), 2.4f).apply {
            shader = SweepGradient(0f, 0f, RAINBOW + RAINBOW[0], null)
            maskFilter = BlurMaskFilter(sigmaToRadius(3f / density), BlurMaskFilter.Blur.NORMAL)
        })
    }
    private val softPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val face = fill(0xFFE8E4F6.toInt())
    private val faceEdge = stroke(argb(0.7f, 255, 255, 255), 1.2f)
    private val icon = fill(0xFF1B1830.toInt())
    private val iconEdge = stroke(0xFF1B1830.toInt(), 2.4f)
    private val iconPath = Path()
    private val jumpArt = JumpArt()
    private val rect = RectF()

    fun draw(c: Canvas, pose: PrismPose, pressPlay: Float) {
        // Scrubber.
        val x0 = 24f
        val x1 = g.w - 24
        val frac = min(1f, max(0f, pose.elapsed / PRISM_TRACK_LENGTH))
        val kx = x0 + (x1 - x0) * frac
        rect.set(x0, g.scrubY - 2, x1, g.scrubY + 2)
        c.drawRoundRect(rect, 2f, 2f, track)
        done.shader = LinearGradient(x0, 0f, max(x0 + 1, kx), 0f, argb(0.75f, 196, 186, 255), argb(0.95f, 238, 234, 248), Shader.TileMode.CLAMP)
        rect.set(x0, g.scrubY - 2, x0 + max(4f, kx - x0), g.scrubY + 2)
        c.drawRoundRect(rect, 2f, 2f, done)
        knobShadow.draw(c, kx, g.scrubY + 1.5f, 6.5f, softPaint)
        c.drawCircle(kx, g.scrubY, 6.5f, knob)
        val elapsed = floor(pose.elapsed)
        val left = prismFormatTime(elapsed)
        val right = "-" + prismFormatTime(PRISM_TRACK_LENGTH - elapsed)
        val ty = g.scrubY + 24
        c.drawText(left, x0, ty, fonts.mono)
        c.drawText(right, x1 - fonts.mono.measureText(right), ty, fonts.mono)

        // Back 5 s, which dips when pressed.
        jump(c, prismControlX(g, 0), g.controlsY, -1, s, fonts.small, 1 - 0.1f * pose.pressBack, jumpArt)

        // The play button: two shadows, a three-stop face, a top edge highlight, a rainbow rim while playing.
        val px = g.w / 2
        val py = g.controlsY
        val r = baseR * (1 - 0.08f * pressPlay)
        softPaint.alpha = 255
        ambient.draw(c, px, py + 10 * (1 - 0.5f * pressPlay), r * 0.92f, softPaint)
        contact.draw(c, px, py + 1.5f, r, softPaint)
        if (pose.play > 0.01f) {
            softPaint.alpha = (0.55f * pose.play * 255).roundToInt().coerceIn(0, 255)
            rim.draw(c, px, py, r + 3, softPaint)
            softPaint.alpha = 255
        }
        face.shader = LinearGradient(px, py - r, px, py + r, intArrayOf(0xFFF7F5FD.toInt(), 0xFFDCD7EF.toInt(), 0xFFBDB5DC.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(px, py, r, face)
        c.drawCircle(px, py, r - 0.6f, faceEdge)
        playPause(c, px, py, pose.icon, s * (1 - 0.08f * pressPlay))
    }

    /** Play ↔ pause: two quads, each vertex moving from the play triangle's half to a pause bar. */
    private fun playPause(c: Canvas, x: Float, y: Float, m: Float, sc: Float) {
        iconEdge.strokeWidth = 2.4f * sc
        for (q in 0 until 2) {
            iconPath.rewind()
            for (v in 0 until 4) {
                val ax = PLAY_SHAPE[q][v * 2] + 1.5f
                val ay = PLAY_SHAPE[q][v * 2 + 1]
                val bx = PAUSE_SHAPE[q][v * 2]
                val by = PAUSE_SHAPE[q][v * 2 + 1]
                val px = x + (ax + (bx - ax) * m) * sc
                val py = y + (ay + (by - ay) * m) * sc
                if (v == 0) iconPath.moveTo(px, py) else iconPath.lineTo(px, py)
            }
            iconPath.close()
            c.drawPath(iconPath, icon)
            c.drawPath(iconPath, iconEdge)
        }
    }

    private companion object {
        val PLAY_SHAPE = arrayOf(
            floatArrayOf(-7f, -11f, 1.5f, -5.5f, 1.5f, 5.5f, -7f, 11f),
            floatArrayOf(1.5f, -5.5f, 10f, 0f, 10f, 0f, 1.5f, 5.5f),
        )
        val PAUSE_SHAPE = arrayOf(
            floatArrayOf(-9f, -10f, -3f, -10f, -3f, 10f, -9f, 10f),
            floatArrayOf(3f, -10f, 9f, -10f, 9f, 10f, 3f, 10f),
        )
    }
}
