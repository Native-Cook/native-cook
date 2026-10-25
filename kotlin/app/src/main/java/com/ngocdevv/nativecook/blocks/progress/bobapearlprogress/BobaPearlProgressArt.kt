package com.ngocdevv.nativecook.blocks.progress.bobapearlprogress

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan
import kotlin.random.Random

/*
 Drawing for Boba Pearl Progress (a port of bobaCup.ts and bobaPanel.ts). One clear plastic cup in
 its own frame (origin at the middle of its base, y down, heights negative): back rim, milk tea with
 a waving surface and brown-sugar streaks, the pearls (one pre-rendered sprite, one bitmap draw
 each), the tea's tint over them, foam, the pour, the sealing film and straw, then the front of the
 glass. The backdrop, the pearl sprite, the cup's table shadows and the step halo are bitmaps made
 once; the percentage, status line and tracker are drawn per frame.
 */

internal const val BOBA_BACKGROUND = 0xFFF4E2D0
private const val INK = 0xFF3B2418.toInt()
private const val INK_SOFT = 0xFF8C6E5C.toInt()
private const val SPRITE = 96
private const val SPRITE_R = 44f
private const val PIF = PI.toFloat()
/** Where each step sits on the track, and the percentage it starts at. */
private val STEP_AT = floatArrayOf(0f, 60f, 82f, 100f)

private fun argb(a: Float, r: Int, g: Int, b: Int) = ((a.coerceIn(0f, 1f) * 255).roundToInt() shl 24) or (r shl 16) or (g shl 8) or b
private fun c(hex: Long) = hex.toInt()

private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
private fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color
    style = Paint.Style.STROKE
    strokeWidth = width
    strokeCap = Paint.Cap.ROUND
}

private fun weighted(weight: Int): Typeface =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(Typeface.SANS_SERIF, weight, false)
    } else {
        Typeface.create(Typeface.SANS_SERIF, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
    }

/** The panel's faces (dp: the canvas is scaled by the density). */
internal class BobaFonts {
    val pct = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = weighted(700); textSize = 40f }
    val status = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = weighted(600); textSize = 16f }
    val eta = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = weighted(500); textSize = 13f }
    val step = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = weighted(600); textSize = 12f }
}

/** A bitmap of something soft drawn once over `area` (dp), placed back at the same rectangle. */
internal class BobaBaked(area: RectF, density: Float, draw: (Canvas) -> Unit) {
    val dst = RectF(area)
    val bitmap: Bitmap = Bitmap.createBitmap(
        max(1, (area.width() * density).roundToInt()),
        max(1, (area.height() * density).roundToInt()),
        Bitmap.Config.ARGB_8888,
    ).also {
        val cv = Canvas(it)
        cv.scale(it.width / area.width(), it.height / area.height())
        cv.translate(-area.left, -area.top)
        draw(cv)
    }
}

internal fun bobaBackdrop(g: BobaGeo, density: Float): Bitmap {
    val bw = max(1, (g.w * density).roundToInt())
    val bh = max(1, (g.h * density).roundToInt())
    val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
    val cv = Canvas(bmp)
    cv.scale(density, density)
    cv.drawRect(0f, 0f, g.w, g.h, Paint().apply {
        shader = LinearGradient(0f, 0f, 0f, g.h, intArrayOf(c(0xFFF8EBDD), c(0xFFF2DCC6), c(0xFFE9C6AA)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    })
    // A soft light behind the cup.
    cv.drawRect(0f, 0f, g.w, g.h, Paint().apply {
        shader = RadialGradient(
            g.cx, g.top + g.cupH * 0.45f, max(1f, g.cupH * 0.85f),
            intArrayOf(argb(0.75f, 255, 250, 242), argb(0.25f, 255, 246, 236), argb(0f, 255, 246, 236)),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
    })
    // The counter's edge behind the cup's base.
    val cy0 = g.base - 26 * g.u
    cv.drawRect(0f, cy0, g.w, cy0 + 66 * g.u, Paint().apply {
        shader = LinearGradient(
            0f, cy0, 0f, g.base + 40 * g.u,
            intArrayOf(argb(0f, 150, 96, 62), argb(0.10f, 150, 96, 62), argb(0f, 150, 96, 62)),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
    })
    // Fine grain (Skia: fractal noise at 0.9, 4.5 %): one grey cell per dp.
    val gw = ceil(g.w).toInt() + 1
    val gh = ceil(g.h).toInt() + 1
    val rnd = Random(3)
    val px = IntArray(gw * gh) {
        val v = rnd.nextInt(256)
        (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    val grain = Bitmap.createBitmap(px, gw, gh, Bitmap.Config.ARGB_8888)
    cv.drawBitmap(grain, null, RectF(0f, 0f, gw.toFloat(), gh.toFloat()), Paint().apply { alpha = 11 })
    grain.recycle()
    return bmp
}

/** Two glossy tapioca pearls side by side (front, and a darker one for the back layer). */
internal fun bobaPearlSprite(): Bitmap {
    val bmp = Bitmap.createBitmap(SPRITE * 2, SPRITE, Bitmap.Config.ARGB_8888)
    val cv = Canvas(bmp)
    for (k in 0 until 2) {
        val cx = SPRITE * k + SPRITE / 2f
        val cy = SPRITE / 2f
        val stops = if (k == 0) intArrayOf(c(0xFF6B3A22), c(0xFF3E1F11), c(0xFF1F0E07)) else intArrayOf(c(0xFF4E2918), c(0xFF2C150B), c(0xFF150904))
        cv.drawCircle(cx, cy, SPRITE_R, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx - 12, cy - 14, SPRITE_R * 1.35f, stops, floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        })
        // Translucent rim, a warm bounce at the bottom right, and a sharp window highlight.
        cv.drawCircle(cx, cy, SPRITE_R - 1.5f, stroke(argb(0.35f, 150, 84, 48), 3f))
        cv.drawCircle(cx + 14, cy + 16, 12f, fill(argb(0.28f, 214, 140, 90)).apply { maskFilter = BlurMaskFilter(sigmaToRadius(5f), BlurMaskFilter.Blur.NORMAL) })
        cv.drawOval(RectF(cx - 26, cy - 30, cx, cy - 14), fill(argb(0.45f, 255, 240, 225)).apply { maskFilter = BlurMaskFilter(sigmaToRadius(4f), BlurMaskFilter.Blur.NORMAL) })
        cv.drawOval(RectF(cx - 21, cy - 26, cx - 9, cy - 19), fill(if (k == 0) argb(0.9f, 255, 250, 244) else argb(0.6f, 255, 250, 244)))
    }
    return bmp
}

private fun cupPath(g: BobaGeo, inset: Float) = Path().apply {
    val rw = g.rimW - inset
    val bw = g.baseW - inset
    moveTo(-rw, -g.cupH)
    lineTo(-bw, 0f)
    arcTo(RectF(-bw, -g.baseRy, bw, g.baseRy), 180f, -180f, false)
    lineTo(rw, -g.cupH)
    close()
}

private fun surfaceY(x: Float, lvl: Float, tilt: Float, amp: Float, clock: Float, u: Float): Float {
    val w = (2 * PIF * clock) / LOOP
    return -lvl + tan(tilt) * x + amp * (sin((x * 0.09f) / u + w * 6) * 0.6f + sin((x * 0.05f) / u - w * 4 + 1.3f) * 0.4f)
}

/** Everything the cup needs that does not change from frame to frame, made once per layout. */
internal class BobaCupArt(private val g: BobaGeo, density: Float) {
    private val u = g.u
    val sprite = bobaPearlSprite()
    private val srcFront = Rect(0, 0, SPRITE, SPRITE)
    private val srcBack = Rect(SPRITE, 0, SPRITE * 2, SPRITE)
    private val outer = cupPath(g, 0f)
    private val inner = cupPath(g, 3 * u)
    private val backRim = Path().apply { addArc(RectF(-g.rimW, -g.cupH - g.rimRy, g.rimW, -g.cupH + g.rimRy), 180f, 180f) }
    private val frontRim = Path().apply { addArc(RectF(-g.rimW, -g.cupH - g.rimRy, g.rimW, -g.cupH + g.rimRy), 0f, 180f) }
    private val baseRing = Path().apply { addArc(RectF(-g.baseW + 4 * u, -g.baseRy - 6 * u, g.baseW - 4 * u, g.baseRy - 6 * u), 0f, 180f) }
    private val highlight = Path().apply {
        moveTo(-g.rimW + 9 * u, -g.cupH + 8 * u)
        lineTo(-g.baseW + 8 * u, -6 * u)
        lineTo(-g.baseW + 17 * u, -6 * u)
        lineTo(-g.rimW + 21 * u, -g.cupH + 8 * u)
        close()
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, -g.cupH, 0f, 0f, intArrayOf(argb(0.42f, 255, 255, 255), argb(0.12f, 255, 255, 255), argb(0.3f, 255, 255, 255)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
    }
    private val edgeR = Path().apply {
        moveTo(g.rimW - 5 * u, -g.cupH + 10 * u)
        lineTo(g.baseW - 4 * u, -8 * u)
    }

    // The table shadow (wide and soft, plus a contact line), and the tea's caramel light.
    val shadow = BobaBaked(RectF(-g.baseW * 1.25f + 8 * u - 30, -4 * u - 30, g.baseW * 1.25f + 8 * u + 30, 14 * u + 30), density) { cv ->
        cv.drawOval(RectF(-g.baseW * 1.25f + 8 * u, -4 * u, g.baseW * 1.25f + 8 * u, 14 * u), fill(argb(0.20f, 92, 50, 28)).apply { maskFilter = BlurMaskFilter(sigmaToRadius(9f), BlurMaskFilter.Blur.NORMAL) })
        cv.drawOval(RectF(-g.baseW * 1.02f, -3 * u, g.baseW * 1.02f, 4 * u), fill(argb(0.25f, 92, 50, 28)).apply { maskFilter = BlurMaskFilter(sigmaToRadius(2f), BlurMaskFilter.Blur.NORMAL) })
    }
    val glow = BobaBaked(RectF(g.baseW * 0.2f - 32, -32f, g.baseW * 1.6f + 32, 16 * u + 32), density) { cv ->
        cv.drawOval(RectF(g.baseW * 0.2f, 0f, g.baseW * 1.6f, 16 * u), fill(argb(1f, 214, 140, 70)).apply { maskFilter = BlurMaskFilter(sigmaToRadius(10f), BlurMaskFilter.Blur.NORMAL) })
    }

    private val bakedPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val spritePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val backFill = fill(argb(0.13f, 255, 255, 255))
    private val backRimPaint = stroke(argb(0.45f, 255, 255, 255), 1.5f)
    private val body = Path()
    private val teaStops = intArrayOf(c(0xFFD3A273), c(0xFFBC8455), c(0xFF94592F))
    private val teaAt = floatArrayOf(0f, 0.5f, 1f)
    private val teaPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val streak = stroke(argb(0.38f, 104, 52, 20), 9 * u).apply {
        strokeJoin = Paint.Join.ROUND
        maskFilter = BlurMaskFilter(sigmaToRadius(3.2f * u / density), BlurMaskFilter.Blur.NORMAL)
    }
    private val streakPath = Path()
    private val tint = fill(argb(0.30f, 196, 140, 92))
    private val foamPaint = fill(argb(0.85f, 240, 222, 196))
    private val topPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val topShine = fill(argb(0.35f, 255, 248, 236))
    private val stream = Path()
    private val streamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(-21 * u, 0f, -11 * u, 0f, intArrayOf(c(0xFFA86E42), c(0xFFE2B68A), c(0xFFB57B4D)), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
    }
    private val splash = fill(argb(0.9f, 240, 214, 182))
    private val film = fill(0)
    private val filmRing = stroke(0, 1.6f * u)
    private val filmShine = fill(argb(0.35f, 255, 255, 255))
    private val popRing = stroke(0, 1.2f * u)
    private val strawPath = Path().apply {
        val sx0 = 12 * u
        val sx1 = 40 * u
        moveTo(sx0 - 7 * u, -12 * u)
        lineTo(sx1 - 7 * u, -g.cupH - 104 * u)
        lineTo(sx1 + 7 * u, -g.cupH - 104 * u)
        lineTo(sx0 + 7 * u, -12 * u)
        close()
    }
    private val strawPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        val sx1 = 40 * u
        shader = LinearGradient(sx1 - 7 * u, 0f, sx1 + 7 * u, 0f, intArrayOf(c(0xFFE58FA2), c(0xFFFBD3DB), c(0xFFE4899D)), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
    }
    private val edgePaint = stroke(argb(0.35f, 255, 255, 255), 2 * u)
    private val outline = stroke(argb(0.55f, 255, 255, 255), 1.2f)
    private val frontRimPaint = stroke(argb(0.8f, 255, 255, 255), 2.2f)
    private val baseRingPaint = stroke(argb(0.4f, 255, 255, 255), 1.4f)
    private val drop1 = fill(0)
    private val drop2 = fill(0)
    private val pearlXY = FloatArray(2)
    private val rect = RectF()

    fun draw(cv: Canvas, cup: BobaCup, pearls: List<BobaPearl>, clock: Float) {
        val tc = cup.tc
        val lvl = bobaLevel(g, tc)
        val pourIn = bobaPourIn(tc)
        val pourOut = bobaPourOut(tc)
        val pouring = pourIn > 0 && pourOut < 1
        cv.save()
        cv.translate(g.cx + cup.dx, g.base)

        // Shadow on the table (outside the rotation), with a little caramel light through the tea.
        cv.drawBitmap(shadow.bitmap, null, shadow.dst, bakedPaint)
        if (lvl > 1) {
            bakedPaint.alpha = (0.16f * (lvl / g.maxLevel) * 255).roundToInt()
            cv.drawBitmap(glow.bitmap, null, glow.dst, bakedPaint)
            bakedPaint.alpha = 255
        }

        cv.rotate(Math.toDegrees(cup.rot.toDouble()).toFloat())
        // Back of the cup: a faint fill and the far half of the rim.
        cv.drawPath(outer, backFill)
        cv.drawPath(backRim, backRimPaint)

        cv.save()
        cv.clipPath(inner)
        val amp = (1.2f + 3 * (if (pouring) 1 else 0) + 4 * cup.shake) * u
        val hasBody = lvl > 0.5f
        if (hasBody) {
            body.rewind()
            val span = g.rimW + 14 * u
            body.moveTo(-span, surfaceY(-span, lvl, cup.tilt, amp, clock, u))
            for (i in 1..16) {
                val x = -span + (i * span * 2) / 16
                body.lineTo(x, surfaceY(x, lvl, cup.tilt, amp, clock, u))
            }
            body.lineTo(span, 30 * u)
            body.lineTo(-span, 30 * u)
            body.close()
            // A new gradient per cup: the level changes, and two cups can be on screen at once.
            teaPaint.shader = LinearGradient(0f, -lvl, 0f, 0f, teaStops, teaAt, Shader.TileMode.CLAMP)
            cv.drawPath(body, teaPaint)
            // Brown-sugar streaks climbing the walls.
            cv.save()
            cv.clipPath(body)
            var side = -1
            while (side <= 1) {
                for (k in 0 until 2) {
                    streakPath.rewind()
                    for (j in 0..8) {
                        val y = (lvl * (0.95f - k * 0.3f) * j) / 8
                        val x = side * (bobaHalfWidth(g, y) - (4 + k * 16) * u - 9 * u * sin(j * 0.9f + k * 2.1f + side))
                        if (j == 0) streakPath.moveTo(x, -y) else streakPath.lineTo(x, -y)
                    }
                    cv.drawPath(streakPath, streak)
                }
                side += 2
            }
            cv.restore()
        }

        // Pearls: one sprite, one bitmap draw each.
        for (i in pearls.indices) {
            val p = pearls[i]
            bobaPearlAt(g, p, tc, pearlXY)
            val px = pearlXY[0]
            val py = pearlXY[1]
            if (py < 0) continue
            val settled = max(0f, 1 - py / (g.pearlR * 10))
            val jig = cup.shake * g.pearlR * 0.35f
            val jx = sin(cup.phase * 31 + i * 1.7f) * jig
            val jy = abs(sin(cup.phase * 27 + i * 2.3f)) * jig
            val hw = bobaHalfWidth(g, py) - p.r
            val x = max(-hw, min(hw, px + cup.roll * settled * (0.6f + 0.4f * (py / (g.pearlR * 6))) + jx))
            val s = p.r / SPRITE_R
            val half = s * SPRITE / 2
            rect.set(x - half, -py - jy - half, x + half, -py - jy + half)
            cv.drawBitmap(sprite, if (p.back) srcBack else srcFront, rect, spritePaint)
        }

        if (hasBody) {
            cv.drawPath(body, tint)
            // Foam from the shake, then the surface seen from a little above.
            val f = bobaFoam(tc)
            val rx = bobaHalfWidth(g, lvl)
            if (f > 0) cv.drawRect(-g.rimW, -lvl, g.rimW, -lvl + 12 * u * f, foamPaint)
            cv.save()
            cv.translate(0f, surfaceY(0f, lvl, cup.tilt, amp, clock, u))
            cv.rotate(Math.toDegrees(cup.tilt.toDouble()).toFloat())
            topPaint.shader = if (f > 0.5f) {
                LinearGradient(-rx, 0f, rx, 0f, c(0xFFF3E4CE), c(0xFFE6CFAE), Shader.TileMode.CLAMP)
            } else {
                LinearGradient(-rx, 0f, rx, 0f, c(0xFFDDB58C), c(0xFFC99B6E), Shader.TileMode.CLAMP)
            }
            rect.set(-rx, -rx * 0.13f, rx, rx * 0.13f)
            cv.drawOval(rect, topPaint)
            rect.set(-rx * 0.7f, -rx * 0.09f, rx * 0.2f, -rx * 0.02f)
            cv.drawOval(rect, topShine)
            cv.restore()
        }
        cv.restore()

        // The pour, from a spout above the frame.
        if (pouring) {
            val topY = -g.cupH - 170 * u
            val surf = -lvl
            val y0 = topY + (surf - topY) * pourOut
            val y1 = topY + (surf - topY) * pourIn
            if (y1 > y0 + 1) {
                stream.rewind()
                val steps = 14
                val wave = (clock * 2 * PIF * 9) / LOOP
                for (i in 0..steps) {
                    val y = y0 + ((y1 - y0) * i) / steps
                    val x = -16 * u + 1.6f * u * sin(y * 0.06f + wave) - 3.5f * u
                    if (i == 0) stream.moveTo(x, y) else stream.lineTo(x, y)
                }
                for (i in steps downTo 0) {
                    val y = y0 + ((y1 - y0) * i) / steps
                    val x = -16 * u + 1.6f * u * sin(y * 0.06f + wave) + 3.5f * u + (y - topY) * 0.004f
                    stream.lineTo(x, y)
                }
                stream.close()
                cv.drawPath(stream, streamPaint)
                if (pourIn >= 1 && pourOut <= 0) {
                    rect.set(-28 * u, surf - 4 * u, -4 * u, surf + 4 * u)
                    cv.drawOval(rect, splash)
                }
            }
        }

        // Seal film and straw.
        val drop = bobaSealDrop(tc)
        val squash = bobaSealSquash(tc)
        val come = bobaStrawCome(tc)
        val pop = bobaStrawPop(tc)
        if (drop > 0) {
            val fy = -g.cupH - (1 - drop) * 40 * u
            cv.save()
            cv.translate(0f, fy)
            cv.scale(1f, 1 - 0.08f * squash)
            film.color = argb(min(1f, drop * 3) * 0.66f, 252, 240, 226)
            rect.set(-g.rimW - 2 * u, -g.rimRy - 1 * u, g.rimW + 2 * u, g.rimRy + 1 * u)
            cv.drawOval(rect, film)
            filmRing.color = argb(min(1f, drop * 3) * 0.7f, 214, 96, 120)
            rect.set(-g.rimW * 0.5f, -g.rimRy * 0.5f, g.rimW * 0.5f, g.rimRy * 0.5f)
            cv.drawOval(rect, filmRing)
            rect.set(-g.rimW * 0.75f, -g.rimRy * 0.55f, -g.rimW * 0.15f, -g.rimRy * 0.2f)
            cv.drawOval(rect, filmShine)
            if (pop > 0 && pop < 1) {
                popRing.color = argb((1 - pop) * 0.8f, 255, 255, 255)
                val l = 33 * u - (4 + 16 * pop) * u
                val t = -(2 + 5 * pop) * u
                rect.set(l, t, l + (8 + 32 * pop) * u, t + (4 + 10 * pop) * u)
                cv.drawOval(rect, popRing)
            }
            cv.restore()
        }

        if (come > 0) {
            cv.save()
            cv.translate(0f, -(1 - come) * 170 * u)
            // Above the film it is solid; inside the cup it shows dimly through the tea.
            val filmY = -g.cupH + (1 - come) * 170 * u
            cv.save()
            cv.clipRect(-g.w, filmY - g.h, g.w, filmY)
            strawPaint.alpha = 255
            cv.drawPath(strawPath, strawPaint)
            cv.restore()
            cv.save()
            cv.clipRect(-g.w, filmY, g.w, filmY + g.h)
            strawPaint.alpha = (0.38f * 255).roundToInt()
            cv.drawPath(strawPath, strawPaint)
            cv.restore()
            cv.restore()
        }

        // Front of the glass: highlights, rims, condensation where the cold tea is.
        cv.drawPath(highlight, highlightPaint)
        cv.drawPath(edgeR, edgePaint)
        cv.drawPath(outer, outline)
        cv.drawPath(frontRim, frontRimPaint)
        cv.drawPath(baseRing, baseRingPaint)
        for (i in 0 until 26) {
            val hy = (((i * 0.6180339f) % 1) * 0.9f + 0.04f) * g.cupH
            val cold = max(0f, min(1f, (lvl - hy) / (20 * u)))
            if (cold <= 0) continue
            val side = ((i * 0.7548776f) % 1) * 2 - 1
            val hx = side * (bobaHalfWidth(g, hy) - 4 * u)
            val r = (0.9f + ((i * 0.5698403f) % 1) * 1.4f) * u
            drop1.color = argb(0.16f * cold, 90, 50, 26)
            cv.drawCircle(hx + 0.5f, -hy + 0.8f, r, drop1)
            drop2.color = argb(0.7f * cold, 255, 255, 255)
            cv.drawCircle(hx - r * 0.3f, -hy - r * 0.3f, r * 0.45f, drop2)
        }
        cv.restore()
    }
}

/** The percentage, the status line and the four-step tracker. */
internal class BobaPanelArt(private val g: BobaGeo, density: Float) {
    private val fonts = BobaFonts()
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackBack = fill(argb(0.12f, 59, 36, 24))
    private val trackDone = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(32f, 0f, g.w - 32f, 0f, intArrayOf(c(0xFFD29A68), c(0xFFA9653A), c(0xFF7D4423)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    }
    private val check = stroke(c(0xFFFFF6EC), 1.8f).apply { strokeJoin = Paint.Join.ROUND }
    private val checkPath = Path()
    private val nodeShadow = fill(argb(0.18f, 59, 36, 24))
    private val nodeDone = fill(c(0xFF8A4F2C))
    private val nodeOpen = fill(c(0xFFF6E6D6))
    private val nodeRing = stroke(argb(0.22f, 59, 36, 24), 1.5f).apply { strokeCap = Paint.Cap.BUTT }
    private val dot = fill(c(0xFFFFF6EC))
    private val halo = BobaBaked(RectF(-24f, -24f, 24f, 24f), density) { cv ->
        cv.drawCircle(0f, 0f, 13f, fill(argb(0.25f, 169, 101, 58)).apply { maskFilter = BlurMaskFilter(sigmaToRadius(4f / density), BlurMaskFilter.Blur.NORMAL) })
    }
    private val haloDst = RectF()
    private val rect = RectF()

    private fun trackX(pct: Float): Float {
        val x0 = 32f
        val x1 = g.w - 32f
        var k = 0f
        for (i in 0 until 3) {
            if (pct >= STEP_AT[i]) k = i + min(1f, (pct - STEP_AT[i]) / (STEP_AT[i + 1] - STEP_AT[i]))
        }
        return x0 + ((x1 - x0) * k) / 3
    }

    private fun drawText(cv: Canvas, s: String, x: Float, y: Float, paint: Paint, color: Int, alpha: Float) {
        paint.color = color
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255).roundToInt()
        cv.drawText(s, x, y, paint)
    }

    fun draw(cv: Canvas, sc: BobaScene) {
        // Percentage, cross-fading during the cup swap.
        val py = g.percentY + 34
        val fading = sc.pctFade > 0 && sc.pctFade < 1
        if (fading) {
            drawText(cv, "${sc.pctFrom.toInt()}%", 24f, py - 8 * sc.pctFade, fonts.pct, INK, 1 - sc.pctFade)
            drawText(cv, "${sc.pctTo.toInt()}%", 24f, py + 8 * (1 - sc.pctFade), fonts.pct, INK, sc.pctFade)
        } else {
            drawText(cv, "${floor(sc.percent).toInt()}%", 24f, py, fonts.pct, INK, 1f)
        }
        // Status and time, right aligned, cross-fading on a step change.
        val rx = g.w - 24
        for (pass in 0 until 2) {
            val idx = if (pass == 0) sc.prevStep else sc.step
            val alpha = if (pass == 0) 1 - sc.stepFade else sc.stepFade
            val shift = if (pass == 0) -6 * sc.stepFade else 6 * (1 - sc.stepFade)
            if (alpha <= 0.01f) continue
            val status = BOBA_STATUS[idx]
            val eta = BOBA_ETA[idx]
            drawText(cv, status, rx - fonts.status.measureText(status), py - 18 + shift, fonts.status, INK, alpha)
            drawText(cv, eta, rx - fonts.eta.measureText(eta), py + shift, fonts.eta, INK_SOFT, alpha)
        }

        // The tracker.
        val y = g.stepsY
        val x0 = 32f
        val x1 = g.w - 32f
        rect.set(x0, y - 2, x1, y + 2)
        cv.drawRoundRect(rect, 2f, 2f, trackBack)
        val fx = trackX(sc.percent)
        if (fx > x0 + 0.5f) {
            rect.set(x0, y - 2, fx, y + 2)
            cv.drawRoundRect(rect, 2f, 2f, trackDone)
        }
        for (i in 0 until 4) {
            val nx = x0 + ((x1 - x0) * i) / 3
            val reached = sc.percent >= STEP_AT[i] - 0.01f && (i > 0 || sc.percent > 0.5f)
            val active = sc.step == i
            if (active) {
                haloDst.set(nx + halo.dst.left, y + halo.dst.top, nx + halo.dst.right, y + halo.dst.bottom)
                cv.drawBitmap(halo.bitmap, null, haloDst, null)
            }
            cv.drawCircle(nx, y + 1, 9f, nodeShadow)
            cv.drawCircle(nx, y, 9f, if (reached) nodeDone else nodeOpen)
            if (!reached) {
                cv.drawCircle(nx, y, 8.25f, nodeRing)
            } else if (i < sc.step || (i == 3 && sc.step == 3)) {
                checkPath.rewind()
                checkPath.moveTo(nx - 3.6f, y + 0.2f)
                checkPath.lineTo(nx - 1, y + 2.8f)
                checkPath.lineTo(nx + 3.8f, y - 2.6f)
                cv.drawPath(checkPath, check)
            } else {
                cv.drawCircle(nx, y, 3f, dot)
            }
            val label = BOBA_STEPS[i]
            val lw = fonts.step.measureText(label)
            val lx = max(16f, min(g.w - 16 - lw, nx - lw / 2))
            drawText(cv, label, lx, y + 28, fonts.step, if (active) INK else INK_SOFT, 1f)
        }
    }
}
