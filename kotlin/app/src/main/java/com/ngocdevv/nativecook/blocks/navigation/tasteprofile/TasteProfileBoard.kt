package com.ngocdevv.nativecook.blocks.navigation.tasteprofile

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** What a flap face shows: a photo or a digit on a card (ink box in ref px). */
internal sealed interface TasteProfileFace {
    data class Photo(val index: Int) : TasteProfileFace
    data class Digit(val ch: Char, val inkLeft: Float, val inkWidth: Float) : TasteProfileFace
}

private typealias Glyph = Triple<Char, Float, Float>

/** The departure board of Board.tsx: stats, photo strip, dots and count, drawn in screen pixels. */
internal class TasteProfileBoardPainter(private val assets: TasteProfileAssets) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val source = Rect()
    private val matrix = Matrix()
    private val rect = RectF()
    private val onePath = Path()
    private val clipPath = Path()

    private val statGlyphs: List<List<List<Glyph>>> = TasteProfileGlyphRuns.stats.mapIndexed { tile, run ->
        STATS.mapIndexed { city, row ->
            if (city == 0) run.glyphs else layoutTasteProfileNumber(row[tile], run.glyphs.first().second, run.digitHeight)
        }
    }
    private val countRun = TasteProfileGlyphRuns.c1470
    private val countGlyphs: List<List<Glyph>> = run {
        val last = countRun.glyphs[3]
        val right = last.second + last.third
        PHOTO_COUNT.mapIndexed { city, value ->
            if (city == 0) {
                countRun.glyphs
            } else {
                val k = countRun.digitHeight / 36f
                var width = 0f
                for (ch in value) width += kotlin.math.round(digitWidths.getValue(ch) * k) + DIGIT_GAP
                layoutTasteProfileNumber(value, right - (width - DIGIT_GAP), countRun.digitHeight)
            }
        }
    }

    private class Ctx(val u: Float, val y0: Float, val density: Float, val run: TasteProfileGlyphRun, var cardR: Float)

    fun statGlyphsOf(tile: Int, city: Int): List<Glyph> = statGlyphs[tile][city]

    fun draw(canvas: Canvas, geo: TasteProfileGeo, frame: TasteProfileFrame) {
        val c = frame.change
        val cx = Ctx(geo.unit, geo.y0, geo.density, TasteProfileGlyphRuns.stats[0], 0f)
        val changing = c.tau >= 0.0 && c.tau < SETTLE && (c.from != c.to || c.photoStart != 0.0)
        for (t in 0 until 4) {
            if (t == frame.hold.tile) continue
            val run = TasteProfileGlyphRuns.stats[t]
            val ctx = Ctx(geo.unit, geo.y0, geo.density, run, 0f)
            val local = c.tau - TasteProfileBoardMotion.digitDelay - t * TasteProfileBoardMotion.digitTileStagger
            drawNumber(
                canvas, ctx, statGlyphs[t][c.from], statGlyphs[t][c.to], if (c.from == c.to) -1.0 else local,
                TasteProfileBoardMotion.digitSlotStagger, TasteProfileBoardMotion.digitStep, TasteProfileBoardMotion.digitCard,
                c.reduced, c.tau, false,
            )
        }
        val countCtx = Ctx(geo.unit, geo.y0, geo.density, countRun, 0f)
        drawNumber(
            canvas, countCtx, countGlyphs[c.from], countGlyphs[c.to],
            if (c.from == c.to) -1.0 else c.tau - TasteProfileBoardMotion.countDelay,
            TasteProfileBoardMotion.countSlotStagger, TasteProfileBoardMotion.countStep, TasteProfileBoardMotion.countCard,
            c.reduced, c.tau, true,
        )
        fun lenOf(city: Int) = max(1, STRIP_PHOTOS[city].size - 4).toDouble()
        when {
            c.tau < 0.0 -> {
                drawStrip(canvas, cx, c.from, c.photoStart, 0.0)
                drawDots(canvas, cx, c.photoStart / lenOf(c.from) * 3.0)
            }
            changing && !c.reduced -> {
                drawStripFlaps(canvas, cx, c)
                drawDots(canvas, cx, c.photoStart / lenOf(c.from) * 3.0 * (1.0 - easeInOut(clamp01(c.tau / 0.5))))
            }
            c.reduced && c.tau < TasteProfileReduced.fade && (c.from != c.to || c.photoStart != 0.0) -> {
                val half = c.tau < TasteProfileReduced.fade / 2.0
                canvas.saveLayerAlpha(null, (reducedDip(c.tau) * 255.0).roundToInt())
                drawStrip(canvas, cx, if (half) c.from else c.to, if (half) c.photoStart else 0.0, 0.0)
                canvas.restore()
                drawDots(canvas, cx, if (half) c.photoStart / lenOf(c.from) * 3.0 else 0.0)
            }
            else -> {
                drawStrip(canvas, cx, c.to, frame.sx, frame.sk)
                drawDots(canvas, cx, frame.sx / lenOf(c.to) * 3.0)
            }
        }
    }

    // Glyphs.

    private fun argb(color: Color, alpha: Double = 1.0): Int = AndroidColor.argb(
        (color.alpha * alpha.coerceIn(0.0, 1.0) * 255.0).roundToInt(),
        (color.red * 255f).roundToInt(),
        (color.green * 255f).roundToInt(),
        (color.blue * 255f).roundToInt(),
    )

    private fun drawGlyph(canvas: Canvas, cx: Ctx, ch: Char, inkL: Float, inkW: Float, run: TasteProfileGlyphRun, color: Color, alpha: Double) {
        if (ch == ' ' || inkW <= 0f || alpha <= 0.001) return
        val u = cx.u
        if (ch == '1') {
            val left = inkL * u
            val top = (run.baseline - run.digitHeight) * u + cx.y0
            val w = inkW * u
            val h = run.digitHeight * u
            onePath.reset()
            onePath.moveTo(left + w * 16.5f / 17f, top)
            onePath.lineTo(left + w * 8f / 17f, top)
            onePath.lineTo(left, top + h * 4.5f / 36f)
            onePath.lineTo(left, top + h * 13f / 36f)
            onePath.lineTo(left + w * 8.5f / 17f, top + h * 13f / 36f)
            onePath.lineTo(left + w * 8.5f / 17f, top + h)
            onePath.lineTo(left + w * 16.5f / 17f, top + h)
            onePath.close()
            paint.shader = null
            paint.style = Paint.Style.FILL
            paint.color = argb(color, alpha)
            canvas.drawPath(onePath, paint)
            return
        }
        val em = TasteProfileGlyphEm[ch] ?: return
        val fontSize = run.size * 3f * u
        val sx = inkW / (em.second * run.size * 3f)
        text.shader = null
        text.color = argb(color, alpha)
        text.textSize = fontSize
        text.typeface = assets.typeface(run.font)
        text.letterSpacing = 0f
        canvas.save()
        canvas.translate(inkL * u - em.first * fontSize * sx, run.baseline * u + cx.y0)
        canvas.scale(sx, 1f)
        canvas.drawText(ch.toString(), 0f, 0f, text)
        canvas.restore()
    }

    private fun glyphWidth(ch: Char, run: TasteProfileGlyphRun): Float =
        if (ch == ' ') 0f else kotlin.math.round(digitWidths.getValue(ch) * (run.digitHeight / 36f))

    // Matrices (screen pixels), as Board.tsx's flapMatrix and turnMatrix.

    private fun flapMatrix(deg: Double, hx: Float, hy: Float, d: Float, below: Boolean): Matrix {
        val a = deg * PI / 180.0
        val c = cos(a).toFloat()
        val p = ((if (below) -sin(a) else sin(a)) / d).toFloat()
        matrix.setValues(floatArrayOf(1f, hx * p, -hx * p * hy, 0f, c + hy * p, -c * hy + hy - p * hy * hy, 0f, p, 1f - p * hy))
        return matrix
    }

    private fun turnMatrix(deg: Double, cx: Float, cy: Float, d: Float): Matrix {
        val a = deg * PI / 180.0
        val cs = cos(a).toFloat()
        val q = (sin(a) / d).toFloat()
        matrix.setValues(floatArrayOf(cs + cx * q, 0f, -cs * cx + cx - q * cx * cx, cy * q, 1f, -q * cx * cy, q, 0f, 1f - q * cx))
        return matrix
    }

    private fun hingeY(y: Float, h: Float): Float = ((y + h / 2f)).roundToInt().toFloat()

    private fun drawPhoto(canvas: Canvas, index: Int, x: Float, y: Float, w: Float, h: Float, alpha: Int = 255) {
        val image = assets.photos[index]
        val scale = max(w / image.width, h / image.height)
        val sw = w / scale
        val sh = h / scale
        val left = ((image.width - sw) / 2f).roundToInt()
        val top = ((image.height - sh) / 2f).roundToInt()
        source.set(left, top, (left + sw).roundToInt(), (top + sh).roundToInt())
        rect.set(x, y, x + w, y + h)
        paint.shader = null
        paint.alpha = alpha
        canvas.drawBitmap(image, source, rect, paint)
        paint.alpha = 255
    }

    private fun drawHalf(
        canvas: Canvas, cx: Ctx, face: TasteProfileFace, x: Float, y: Float, w: Float, h: Float, top: Boolean, shade: Double, cardAlpha: Double,
    ) {
        val hy = hingeY(y, h)
        canvas.save()
        if (top) canvas.clipRect(x - 2f, y - 2f, x + w + 2f, hy) else canvas.clipRect(x - 2f, hy, x + w + 2f, y + h + 2f)
        paint.shader = null
        paint.style = Paint.Style.FILL
        when (face) {
            is TasteProfileFace.Photo -> drawPhoto(canvas, face.index, x, y, w, h)
            is TasteProfileFace.Digit -> {
                if (cardAlpha > 0.0) {
                    paint.color = argb(if (top) CARD_TOP else CARD_BOTTOM, cardAlpha)
                    canvas.drawRoundRect(x, y, x + w, y + h, cx.cardR, cx.cardR, paint)
                }
                if (cardAlpha < 0.999) drawGlyph(canvas, cx, face.ch, face.inkLeft, face.inkWidth, cx.run, cx.run.color, 1.0)
                if (cardAlpha > 0.001) drawGlyph(canvas, cx, face.ch, face.inkLeft, face.inkWidth, cx.run, Color.White, min(1.0, cardAlpha))
            }
        }
        if (shade != 0.0) {
            paint.shader = null
            paint.style = Paint.Style.FILL
            val photo = face is TasteProfileFace.Photo
            paint.color = argb(if (shade > 0.0) Color.Black else Color.White, abs(shade) * if (photo) 1.0 else cardAlpha)
            if (photo) canvas.drawRect(x, y, x + w, y + h, paint) else canvas.drawRoundRect(x, y, x + w, y + h, cx.cardR, cx.cardR, paint)
        }
        canvas.restore()
    }

    private fun drawFlap(
        canvas: Canvas, cx: Ctx, from: TasteProfileFace, to: TasteProfileFace, x: Float, y: Float, w: Float, h: Float,
        deg: Double, d: Float, alpha: Double, cardAlpha: Double,
    ) {
        val hx = x + w / 2f
        val hy = hingeY(y, h)
        val rad = deg * PI / 180.0
        if (alpha >= 0.999) canvas.save() else canvas.saveLayerAlpha(null, (alpha * 255.0).roundToInt())
        if (deg < 90.0) {
            canvas.save()
            canvas.concat(flapMatrix(deg, hx, hy, d, false))
            val shade = if (from is TasteProfileFace.Photo) TasteProfileBoardMotion.flapDark * sin(rad) else -TasteProfileBoardMotion.glint * sin(2.0 * rad)
            drawHalf(canvas, cx, from, x, y, w, h, true, shade, cardAlpha)
            canvas.restore()
        } else {
            val phi = 180.0 - deg
            canvas.save()
            canvas.concat(flapMatrix(phi, hx, hy, d, true))
            drawHalf(canvas, cx, to, x, y, w, h, false, -TasteProfileBoardMotion.flapLight * sin(phi * PI / 180.0), cardAlpha)
            canvas.restore()
        }
        canvas.restore()
    }

    private fun drawCell(
        canvas: Canvas, cx: Ctx, a: TasteProfileFace, b: TasteProfileFace, x: Float, y: Float, w: Float, h: Float,
        deg: Double, speed: Double, d: Float, cardAlpha: Double, seam: Double,
    ) {
        val bm = TasteProfileBoardMotion
        if (deg < 0.0) {
            drawHalf(canvas, cx, a, x, y, w, h, true, 0.0, cardAlpha)
            if (speed > 0.01) {
                drawHalf(canvas, cx, a, x, y, w, h, false, bm.cover * 0.5 * sin(speed * PI / 180.0), cardAlpha)
                drawFlap(canvas, cx, a, a, x, y, w, h, 180.0 - speed, d, 1.0, cardAlpha)
            } else {
                drawHalf(canvas, cx, a, x, y, w, h, false, 0.0, cardAlpha)
            }
        } else {
            val rad = deg * PI / 180.0
            drawHalf(canvas, cx, b, x, y, w, h, true, if (deg < 90.0) bm.uncover * cos(rad) else 0.0, cardAlpha)
            drawHalf(canvas, cx, a, x, y, w, h, false, if (deg > 90.0) bm.cover * -cos(rad) else 0.0, cardAlpha)
            val step = speed * bm.shutter / bm.ghostAlpha.size
            for (i in bm.ghostAlpha.indices.reversed()) {
                val ghost = deg - (i + 1) * step
                if (ghost > 0.0) drawFlap(canvas, cx, a, b, x, y, w, h, ghost, d, bm.ghostAlpha[i], cardAlpha)
            }
            drawFlap(canvas, cx, a, b, x, y, w, h, deg, d, 1.0, cardAlpha)
        }
        if (seam > 0.0) {
            val photo = a is TasteProfileFace.Photo
            paint.shader = null
            paint.style = Paint.Style.FILL
            paint.color = argb(if (photo) Color.Black else CARD_GAP, if (photo) bm.seam * seam else 0.9 * seam)
            val hy = hingeY(y, h)
            canvas.drawRect(x, hy - 0.75f, x + w, hy + 0.75f, paint)
        }
    }

    // Numbers.

    private fun drawNumber(
        canvas: Canvas, cx: Ctx, from: List<Glyph>, to: List<Glyph>, local: Double, slotStagger: Double, step: Double,
        card: TasteProfileCard, reduced: Boolean, tau: Double, alignRight: Boolean,
    ) {
        val run = cx.run
        if (reduced || local < 0.0) {
            val show = if (reduced) (if (tau >= TasteProfileReduced.fade / 2.0) to else from) else from
            val dip = if (reduced && tau > 0.0 && tau < TasteProfileReduced.fade) reducedDip(tau) else 1.0
            show.forEach { drawGlyph(canvas, cx, it.first, it.second, it.third, run, run.color, dip) }
            return
        }
        val u = cx.u
        val n = max(from.size, to.size)
        val ch = (run.digitHeight + 2 * card.padY).toFloat()
        cx.cardR = (card.r * u).toFloat()
        for (j in 0 until n) {
            val fi = if (alignRight) from.size - n + j else j
            val ti = if (alignRight) to.size - n + j else j
            val a = from.getOrNull(fi)
            val b = to.getOrNull(ti)
            val ca = a?.first ?: ' '
            val cb = b?.first ?: ' '
            val steps = drumSteps(ca, cb)
            val centre = when {
                a != null && b != null -> (a.second + a.third / 2f + b.second + b.third / 2f) / 2f
                a != null -> a.second + a.third / 2f
                b != null -> b.second + b.third / 2f
                else -> 0f
            }
            val slotLocal = local - j * slotStagger
            val f = flapAt(slotLocal, steps, step)
            var cardA = 0.0
            if (steps > 0) {
                val dIn = TasteProfileBoardMotion.digitCardIn
                val end = steps * step
                cardA = if (slotLocal < end) clamp01((slotLocal + dIn) / dIn)
                else 1.0 - clamp01((slotLocal - end - TasteProfileBoardMotion.bounceDur * 0.5) / TasteProfileBoardMotion.digitCardOut)
            }
            val shift = (easeOut(clamp01(cardA)) * card.spread * (if (alignRight) -(n - 1 - j) else j)).toFloat()
            fun face(c: Char, k: Int): TasteProfileFace {
                if (k == 0 && a != null) return TasteProfileFace.Digit(a.first, a.second + shift, a.third)
                if (k == steps && b != null) return TasteProfileFace.Digit(b.first, b.second + shift, b.third)
                val w = glyphWidth(c, run)
                return TasteProfileFace.Digit(c, centre + shift - w / 2f, w)
            }
            val x = (centre + shift - card.w.toFloat() / 2f) * u
            val y = (run.baseline - run.digitHeight - card.padY.toFloat()) * u + cx.y0
            val faceA = face(drumChar(ca, f.k), f.k)
            val faceB = face(drumChar(ca, min(f.k + 1, steps)), min(f.k + 1, steps))
            val settled = f.angle < 0.0 && f.bounce < 0.01 && cardA <= 0.0
            if (steps == 0 || slotLocal < -TasteProfileBoardMotion.digitCardIn || settled) {
                val glyph = if (steps == 0 || settled) b else a
                if (glyph != null) drawGlyph(canvas, cx, glyph.first, glyph.second, glyph.third, run, run.color, 1.0)
                continue
            }
            drawCell(
                canvas, cx, faceA, faceB, x, y, (card.w * u).toFloat(), ch * u, f.angle,
                if (f.angle >= 0.0) f.speed else f.bounce, (TasteProfileBoardMotion.perspDigit * cx.density).toFloat(),
                cardA, if (f.angle >= 0.0) cardA else 0.0,
            )
        }
    }

    // Strip.

    private fun drawDots(canvas: Canvas, cx: Ctx, p: Double) {
        val u = cx.u
        TasteProfileBoxes.dots.forEachIndexed { i, dot ->
            val k = clamp01(1.0 - abs(p - i))
            val centreX = dot.centerX * u
            val centreY = TasteProfileBoxes.dotsY * u + cx.y0
            paint.shader = null
            paint.style = Paint.Style.FILL
            if (k < 0.999) {
                paint.color = argb(if (i == 0) TasteProfileColors.dotOff else dot.color)
                canvas.drawCircle(centreX, centreY, dot.diameter / 2f * u, paint)
            }
            if (k > 0.001) {
                paint.color = argb(TasteProfileColors.dotOn, min(1.0, k))
                canvas.drawCircle(centreX, centreY, dot.diameter / 2f * u, paint)
            }
        }
    }

    private fun drawStripBorder(canvas: Canvas, cx: Ctx, alpha: Double) {
        if (alpha <= 0.0) return
        val u = cx.u
        val st = TasteProfileBoxes.strip
        val bw = max(1f, 2f * u)
        val r = TasteProfileRadius.photo * u
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = bw
        paint.color = argb(Color.White, 0.55 * alpha)
        canvas.drawRoundRect(st.x * u + bw / 2f, st.y * u + cx.y0 + bw / 2f, (st.x + st.w) * u - bw / 2f, (st.y + st.h) * u + cx.y0 - bw / 2f, r - bw / 2f, r - bw / 2f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun clipRounded(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float) {
        clipPath.reset()
        clipPath.addRoundRect(l, t, r, b, radius, radius, Path.Direction.CW)
        canvas.clipPath(clipPath)
    }

    private fun drawStrip(canvas: Canvas, cx: Ctx, city: Int, x: Double, k: Double) {
        val u = cx.u
        val st = TasteProfileBoxes.strip
        val radius = TasteProfileRadius.photo * u
        val sx = st.x * u
        val sy = st.y * u + cx.y0
        val sw = st.w * u
        val sh = st.h * u
        val cw = sw / 4f
        val list = STRIP_PHOTOS[city]
        canvas.save()
        val grow = (TasteProfileCoverMotion.grow * u * k).toFloat()
        clipRounded(canvas, sx - grow * 3f, sy - grow, sx + sw + grow * 3f, sy + sh + grow, radius)
        if (k < 0.0005) {
            val base = floor(x).toInt()
            val frac = (x - base).toFloat()
            for (i in -1..4) {
                val idx = base + i
                if (idx < 0 || idx >= list.size) continue
                drawPhoto(canvas, list[idx], sx + (i - frac) * cw, sy, cw, sh)
            }
        } else {
            val first = max(0, floor(x).toInt() - 2)
            val last = min(list.size - 1, ceil(x).toInt() + 5)
            val order = (first..last).sortedByDescending { abs(it - x - 1.5) }
            for (i in order) {
                val o = i - x - 1.5
                val ao = min(abs(o), TasteProfileCoverMotion.span)
                val ccx = (sx + sw / 2f + o * cw * (1.0 - TasteProfileCoverMotion.squeeze * k * (ao / TasteProfileCoverMotion.span))).toFloat()
                val ccy = sy + sh / 2f
                val sign = if (o > 0.0) 1.0 else if (o < 0.0) -1.0 else 0.0
                val turn = -sign * TasteProfileCoverMotion.rotate * k * ao / TasteProfileCoverMotion.span
                val scale = (1.0 - TasteProfileCoverMotion.shrink * k * (ao / TasteProfileCoverMotion.span)).toFloat()
                val w = (cw * (1.0 - TasteProfileCoverMotion.inset * k)).toFloat()
                val r = (radius * TasteProfileCoverMotion.radius * k).toFloat()
                canvas.save()
                canvas.concat(turnMatrix(turn, ccx, ccy, (TasteProfileBoardMotion.perspPhoto * cx.density * 1.2).toFloat()))
                canvas.translate(ccx, ccy)
                canvas.scale(scale, scale)
                canvas.translate(-ccx, -ccy)
                clipRounded(canvas, ccx - w / 2f, sy, ccx + w / 2f, sy + sh, r)
                drawPhoto(canvas, list[i], ccx - cw / 2f, sy, cw, sh)
                val dim = TasteProfileCoverMotion.dim * k * (ao / TasteProfileCoverMotion.span)
                if (dim > 0.0) {
                    paint.shader = null
                    paint.style = Paint.Style.FILL
                    paint.color = argb(Color.Black, dim)
                    canvas.drawRect(ccx - w / 2f, sy, ccx + w / 2f, sy + sh, paint)
                }
                canvas.restore()
            }
        }
        canvas.restore()
        drawStripBorder(canvas, cx, 1.0 - k)
    }

    private fun drawStripFlaps(canvas: Canvas, cx: Ctx, c: TasteProfileChange) {
        val u = cx.u
        val st = TasteProfileBoxes.strip
        val radius = TasteProfileRadius.photo * u
        val sx = st.x * u
        val sy = st.y * u + cx.y0
        val sw = st.w * u
        val sh = st.h * u
        val cw = sw / 4f
        val bm = TasteProfileBoardMotion
        canvas.save()
        clipRounded(canvas, sx, sy, sx + sw, sy + sh, radius)
        val toList = STRIP_PHOTOS[c.to]
        val fromList = STRIP_PHOTOS[c.from]
        for (i in 0 until 4) {
            val seq0 = fromList[(c.photoStart.toInt() + i).coerceIn(0, fromList.size - 1)]
            val seq3 = toList[i.coerceIn(0, toList.size - 1)]
            val local = c.tau - bm.photoDelay - i * bm.photoCellStagger
            val steps = if (seq0 == seq3) 0 else bm.photoSteps
            val f = flapAt(local, steps, bm.photoStep)
            val mid1 = toList[(i + 4) % toList.size]
            val mid2 = toList[(i + 2) % toList.size]
            fun seqAt(k: Int) = if (k <= 0) seq0 else if (k == 1) mid1 else if (k == 2) mid2 else seq3
            val faceA = TasteProfileFace.Photo(if (steps == 0) seq3 else seqAt(f.k))
            val faceB = TasteProfileFace.Photo(seqAt(min(f.k + 1, steps)))
            val seam = when {
                steps == 0 -> 0.0
                local < 0.0 -> clamp01((local + 0.1) / 0.1)
                local < steps * bm.photoStep -> 1.0
                else -> 1.0 - clamp01((local - steps * bm.photoStep) / 0.25)
            }
            drawCell(
                canvas, cx, faceA, faceB, sx + i * cw, sy, cw, sh, f.angle, if (f.angle >= 0.0) f.speed else f.bounce,
                (bm.perspPhoto * cx.density).toFloat(), 1.0, seam,
            )
        }
        canvas.restore()
        drawStripBorder(canvas, cx, 1.0)
    }

    private companion object {
        val CARD_TOP = Color(0xFF202020)
        val CARD_BOTTOM = Color(0xFF141414)
        val CARD_GAP = Color(0xFFF8F8F8)
    }
}
