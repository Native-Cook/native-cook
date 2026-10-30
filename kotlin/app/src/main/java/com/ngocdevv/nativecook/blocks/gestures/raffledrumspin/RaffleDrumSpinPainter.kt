package com.ngocdevv.nativecook.blocks.gestures.raffledrumspin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlendMode
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Picture
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Rect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

internal class RaffleDrumSpinPainter(context: Context, private val reducedMotion: Boolean = false) {
    private val regular = Typeface.createFromAsset(context.assets, "raffledrumspin/raffledrumspin_Inter-Regular.ttf")
    private val medium = Typeface.createFromAsset(context.assets, "raffledrumspin/raffledrumspin_Inter-Medium.ttf")
    private val semibold = Typeface.createFromAsset(context.assets, "raffledrumspin/raffledrumspin_Inter-SemiBold.ttf")
    private val archivo = Typeface.createFromAsset(context.assets, "raffledrumspin/raffledrumspin_Archivo-ExtraBold.ttf")
    private val serif = Typeface.createFromAsset(context.assets, "raffledrumspin/raffledrumspin_SourceSerif4-Semibold.ttf")
    private val ticket = context.assets.open("raffledrumspin/raffledrumspin_golden-ticket.webp").use(BitmapFactory::decodeStream)
    private val ticketBack = context.assets.open("raffledrumspin/raffledrumspin_golden-ticket-back.webp").use(BitmapFactory::decodeStream)
    private val pixelRatio = context.resources.displayMetrics.density
    private val typography = if (Build.VERSION.SDK_INT >= 31) RaffleDrumSpinTypography(context, mapOf(regular to "Inter-Regular", medium to "Inter-Medium", semibold to "Inter-SemiBold", archivo to "Archivo-ExtraBold", serif to "SourceSerif4-Semibold")) else null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val cardPaths = Array(3) { Path() }
    private val cardGeometry = Array(3) { FloatArray(4) { Float.NaN } }
    private val checkPath = Path()
    private val dustBig = FloatArray(240)
    private val dustSmall = FloatArray(240)
    private val backgroundShader = if (Build.VERSION.SDK_INT >= 33) RdsBackgroundShader() else null
    private val ballShader = if (Build.VERSION.SDK_INT >= 33) RdsBallShader() else null
    private val raysShader = if (Build.VERSION.SDK_INT >= 33) RdsRaysShader() else null
    private val lightShader = if (Build.VERSION.SDK_INT >= 33) RdsLightShader() else null
    private val liquidShader = if (Build.VERSION.SDK_INT >= 33) RdsLiquidShader() else null
    private val glassShader = if (Build.VERSION.SDK_INT >= 33) RdsGlassShader() else null
    private val baseLayer = if (Build.VERSION.SDK_INT >= 33) RdsHardwareCardLayer() else null
    private val morphShader = if (Build.VERSION.SDK_INT >= 33) RdsMorphShader(ticket) else null
    private val foilShader = if (Build.VERSION.SDK_INT >= 33) RdsFoilShader(ticket) else null

    private var basePicture: Picture? = null
    private var baseBitmap: Bitmap? = null
    private var baseWidth = 0
    private var baseHeight = 0

    fun drawPage(canvas: Canvas, width: Float, height: Float, frame: RdsFrame) {
        val sx = width / RDS_REF_W
        val sy = height / RDS_REF_H
        if (basePicture == null || baseWidth != width.toInt() || baseHeight != height.toInt()) {
            baseWidth = width.toInt()
            baseHeight = height.toInt()
            val picture = Picture()
            val recording = picture.beginRecording(baseWidth, baseHeight)
            drawBackground(recording, width, height, sx, sy)
            drawDrumShadow(recording, sx, sy)
            drawTitle(recording, sx, sy)
            drawChips(recording, sx, sy)
            drawClose(recording, sx, sy)
            picture.endRecording()
            basePicture = picture
            if (Build.VERSION.SDK_INT >= 33) {
                // Upload an immutable bitmap into the window's own GPU context once. Keeping
                // the ImageReader's external hardware buffer live adds cross-context fences.
                val rendered = baseLayer?.render(baseWidth, baseHeight) { it.drawPicture(picture) }
                baseBitmap = rendered?.copy(Bitmap.Config.ARGB_8888, false)
                baseLayer?.close()
            }
        }
        val staticBitmap = baseBitmap
        if (staticBitmap != null) canvas.drawBitmap(staticBitmap, 0f, 0f, null)
        else basePicture?.let(canvas::drawPicture)
        drawGlow(canvas, frame, width, sx, sy)
        drawAmbientSlips(canvas, frame, sx, sy)
        if (reducedMotion) drawFallbackDrum(canvas, frame, sx)
        drawSparkles(canvas, frame, sx, sy)
        if (frame.lens[4] > .01f && Build.VERSION.SDK_INT >= 33 && glassShader != null && canvas.isHardwareAccelerated) {
            val localX = sx / pixelRatio
            val localY = sy / pixelRatio
            glassShader.drawFiltered(canvas, (width / pixelRatio).toInt(), (height / pixelRatio).toInt(), frame.lens, RdsLayout.cardR, frame.lens[4], localX, localY, pixelRatio) { layerCanvas ->
                repeat(3) { drawCard(layerCanvas, frame, it, localX, localY) }
            }
        } else {
            repeat(3) { drawCard(canvas, frame, it, sx, sy) }
        }
        drawDim(canvas, frame, width, height, sx, sy)
        if (frame.focus <= .001f) drawPlay(canvas, frame, sx, sy)
    }

    fun drawTop(canvas: Canvas, width: Float, height: Float, frame: RdsFrame) {
        val sx = width / RDS_REF_W
        val sy = height / RDS_REF_H
        if (frame.focus > .001f) drawPlay(canvas, frame, sx, sy)
        drawLightEffects(canvas, frame, width, height, sx, sy)
        val morphing = frame.hero != null || (frame.morph > 0f && frame.morph < 1f)
        if (morphing && Build.VERSION.SDK_INT >= 33 && morphShader != null) {
            morphShader.draw(canvas, paint, frame, sx, sy)
            drawThreads(canvas, frame, sx, sy)
            val ink = seg(frame.morph.toDouble(), .78, 1.0).toFloat()
            if (ink > 0f) drawTicketText(canvas, frame, ink, stub = true, body = true, sx = sx, sy = sy)
        } else {
            frame.hero?.let { hero ->
                val ballAlpha = (1f - (frame.morph / .72f).coerceIn(0f, 1f)) * hero.alpha
                if (ballAlpha > .01f) drawBall(canvas, hero.x, hero.y, hero.radius, hero.rx, hero.ry, hero.rz, ballAlpha, hero.squash, sx)
            }
        }
        if (
            !morphing && frame.morph >= 1f &&
            (frame.burn < 1f || frame.tear > 0f || frame.t < (T.burn[1] + T.dust).toFloat())
        ) {
            drawTicket(canvas, frame, sx, sy)
        }
        drawLanding(canvas, frame, sx, sy)
        drawCrankHint(canvas, frame, sx)
    }

    fun release() {
        if (Build.VERSION.SDK_INT >= 33) {
            glassShader?.close()
            baseLayer?.close()
        }
        baseBitmap = null
        basePicture = null
    }

    private fun drawGlow(canvas: Canvas, frame: RdsFrame, width: Float, sx: Float, sy: Float) {
        val k = frame.glow * .5f
        if (k <= .004f) return
        val x = RdsLayout.drumX * sx
        val y = (RdsLayout.drumY - 30f) * sy
        val radius = width * .62f
        paint.resetPaint(Color.WHITE, 1f)
        paint.shader = RadialGradient(
            x,
            y,
            radius,
            intArrayOf(
                Color.argb((k * 255f).toInt(), 0x33, 0x1F, 0x6B),
                Color.argb((k * .55f * 255f).toInt(), 0x33, 0x1F, 0x6B),
                Color.argb((k * .16f * 255f).toInt(), 0x33, 0x1F, 0x6B),
                Color.TRANSPARENT,
            ),
            floatArrayOf(0f, .35f, .7f, 1f),
            Shader.TileMode.CLAMP,
        )
        paint.alpha = 255
        paint.blendMode = BlendMode.PLUS
        canvas.drawRect(max(0f, x - radius), max(0f, y - radius), min(width, x + radius), y + radius, paint)
        paint.blendMode = null
        paint.shader = null
    }

    private fun drawDim(canvas: Canvas, frame: RdsFrame, width: Float, height: Float, sx: Float, sy: Float) {
        if (frame.dim <= .004f) return
        val inner = Color.argb((.2f * frame.dim * 255f).toInt(), 0x0A, 0x04, 0x18)
        val outer = Color.argb((.85f * frame.dim * 255f).toInt(), 0x04, 0x01, 0x0B)
        paint.resetPaint(Color.WHITE, 1f)
        paint.shader = RadialGradient(RdsLayout.revealX * sx, RdsLayout.revealY * sy, height * .75f, inner, outer, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width, height, paint)
        paint.shader = null
    }

    private fun drawBackground(canvas: Canvas, width: Float, height: Float, sx: Float, sy: Float) {
        if (Build.VERSION.SDK_INT >= 33 && backgroundShader != null) {
            backgroundShader.draw(canvas, paint, width, height, RdsLayout.drumX * sx, (RdsLayout.drumY - 30f) * sy, 0f, 0f, sx)
            return
        }
        paint.shader = LinearGradient(
            0f,
            0f,
            0f,
            height,
            intArrayOf(0xFF332448.toInt(), 0xFF22104A.toInt(), 0xFF13023A.toInt(), 0xFF020106.toInt(), 0xFF03041A.toInt()),
            floatArrayOf(0f, .2f, .38f, .56f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width, height, paint)
        paint.shader = RadialGradient(RdsLayout.drumX * sx, (RdsLayout.drumY - 45f) * sy, 120f * sx, 0xAA9D94A8.toInt(), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawCircle(RdsLayout.drumX * sx, (RdsLayout.drumY - 45f) * sy, 120f * sx, paint)
        paint.shader = null
    }

    private fun drawAmbientSlips(canvas: Canvas, frame: RdsFrame, sx: Float, sy: Float) {
        val slips = arrayOf(
            floatArrayOf(112f, 137f, -62f, 24f, 0f), floatArrayOf(219f, 156f, -32f, 18f, 0f),
            floatArrayOf(92f, 240f, 12f, 26f, 0f), floatArrayOf(318f, 222f, 28f, 26f, 0f),
            floatArrayOf(20f, 365f, -58f, 46f, 6f), floatArrayOf(370f, 330f, 35f, 34f, 5f),
            floatArrayOf(87f, 459f, 22f, 30f, 1f), floatArrayOf(298f, 323f, -64f, 14f, 0f),
            floatArrayOf(175f, 283f, -58f, 10f, 0f), floatArrayOf(345f, 448f, 70f, 16f, 0f),
            floatArrayOf(394f, 640f, 10f, 38f, 7f), floatArrayOf(362f, 236f, 22f, 20f, 0f),
        )
        val loop = frame.t / LOOP * (2f * PI.toFloat())
        slips.forEachIndexed { index, slip ->
            val ox = slip[0] - RdsLayout.drumX
            val oy = slip[1] - RdsLayout.drumY
            val distance = max(40f, kotlin.math.hypot(ox, oy))
            val gust = frame.speed * (60f / distance) * 9f
            val dx = 5f * sin(loop * 2f + index) + (ox / distance) * gust + 3f * frame.speed * sin(frame.t * 11f + index * 1.7f)
            val dy = 4f * sin(loop * 3f + index * 2.3f) + (oy / distance) * gust
            val turn = 8f * sin(loop * 2f + index * .7f) + 14f * frame.speed * sin(frame.t * 7f + index)
            val flip = .55f + .45f * cos(loop * (1f + index % 3) + index + frame.speed * sin(frame.t * 13f + index))
            canvas.save()
            canvas.translate((slip[0] + dx) * sx, (slip[1] + dy) * sy)
            canvas.rotate(slip[2] + turn)
            canvas.scale(1f, flip)
            val w = slip[3] * sx
            val h = w * .42f
            paint.resetPaint(Color.WHITE, 1f)
            paint.shader = LinearGradient(0f, -h, 0f, h, intArrayOf(0xF2A58AFF.toInt(), 0xE67B4CF0.toInt()), null, Shader.TileMode.CLAMP)
            if (slip[4] > 0f) paint.maskFilter = rdsSigmaBlur(slip[4] * sx, BlurMaskFilter.Blur.NORMAL)
            canvas.drawRoundRect(RectF(-w / 2f, -h / 2f, w / 2f, h / 2f), 2f * sx, 2f * sx, paint)
            paint.maskFilter = null
            paint.shader = null
            canvas.restore()
        }
    }

    /** The reference's non-WebGPU path, also used for its reduced-motion stills. */
    private fun drawFallbackDrum(canvas: Canvas, frame: RdsFrame, sx: Float) {
        fun stroke(points: List<Projection>, width: Float, color: Int, alpha: Float) {
            path.reset()
            path.moveTo(points[0].x.toFloat(), points[0].y.toFloat())
            for (i in 1 until points.size) path.lineTo(points[i].x.toFloat(), points[i].y.toFloat())
            paint.resetPaint(color, alpha, Paint.Style.STROKE)
            paint.strokeWidth = width
            paint.strokeCap = Paint.Cap.ROUND
            canvas.drawPath(path, paint)
        }
        fun projected(x: Double, y: Double, z: Double) = project(x, y, z, sx.toDouble())
        fun rotated(x: Double, y: Double, z: Double): DoubleArray {
            val sine = sin(frame.theta.toDouble())
            val cosine = cos(frame.theta.toDouble())
            return doubleArrayOf(x, cosine * y - sine * z, sine * y + cosine * z)
        }
        for (side in intArrayOf(-1, 1)) {
            val points = (0..16).map { i ->
                val t = i / 16.0
                val u = 1.0 - t
                val x = side * (1.18 * u * u * u + 1.36 * 3 * u * u * t + 1.34 * 3 * u * t * t + 1.02 * t * t * t)
                val y = -.42 * 3 * u * u * t - 1.02 * 3 * u * t * t - 1.3 * t * t * t
                projected(x, y, 0.0)
            }
            stroke(points, 7f * sx, 0xFF8D8A99.toInt(), 1f)
            stroke(points, 2.5f * sx, 0xFFE9E7F0.toInt(), .9f)
        }
        val corners = listOf(projected(-1.15, -1.3, .72), projected(1.15, -1.3, .72), projected(1.15, -1.3, -.72), projected(-1.15, -1.3, -.72))
        path.reset()
        path.moveTo(corners[0].x.toFloat(), corners[0].y.toFloat())
        for (i in 1..3) path.lineTo(corners[i].x.toFloat(), corners[i].y.toFloat())
        path.close()
        paint.resetPaint(0xFF3B3846.toInt(), 1f)
        canvas.drawPath(path, paint)
        fun halves(front: Boolean) {
            repeat(7) { k ->
                val a = k / 7.0 * PI
                var run = ArrayList<Projection>()
                fun flush() {
                    if (run.size > 1) {
                        stroke(run, 3.2f * sx, if (front) 0xFF9C99AA.toInt() else 0xFF4A4660.toInt(), 1f)
                        stroke(run, 1.1f * sx, if (front) Color.WHITE else 0xFF8D88A6.toInt(), .85f)
                    }
                    run = ArrayList()
                }
                for (i in 0..72) {
                    val u = i / 72.0 * PI * 2
                    val r = rotated(cos(u), sin(u) * cos(a), sin(u) * sin(a))
                    val point = projected(r[0], r[1], r[2])
                    if ((point.z < 6.0) == front) run.add(point) else flush()
                }
                flush()
            }
        }
        halves(false)
        stroke(listOf(projected(-1.24, 0.0, 0.0), projected(1.36, 0.0, 0.0)), 4f * sx, 0xFFB9B6C4.toInt(), 1f)
        for (ball in frame.balls) if (ball.alpha > 0f && ball.depth > 3.5f) {
            drawBall(canvas, ball.x, ball.y, ball.radius, ball.rx, ball.ry, ball.rz, ball.alpha, 1f, sx)
        }
        halves(true)
        val handle = rotated(1.36, .3, 0.0)
        val h0 = projected(handle[0], handle[1], handle[2])
        val h1 = projected(1.66, handle[1], handle[2])
        stroke(listOf(projected(1.36, 0.0, 0.0), h0), 3.5f * sx, 0xFFC9C6D4.toInt(), 1f)
        stroke(listOf(h0, h1), 8f * sx, 0xFF151418.toInt(), 1f)
        for (ball in frame.balls) if (ball.alpha > 0f && ball.depth <= 3.5f) {
            drawBall(canvas, ball.x, ball.y, ball.radius, ball.rx, ball.ry, ball.rz, ball.alpha, 1f, sx)
        }
    }

    private fun drawDrumShadow(canvas: Canvas, sx: Float, sy: Float) {
        paint.resetPaint(0xFF05010F.toInt(), .55f)
        paint.maskFilter = rdsSigmaBlur(14f * sx, BlurMaskFilter.Blur.NORMAL)
        canvas.drawOval(RectF(116f * sx, 288f * sy, 300f * sx, 310f * sy), paint)
        paint.maskFilter = null
        paint.resetPaint(0xFF020008.toInt(), .6f)
        paint.maskFilter = rdsSigmaBlur(4f * sx, BlurMaskFilter.Blur.NORMAL)
        canvas.drawOval(RectF(136f * sx, 295f * sy, 280f * sx, 303f * sy), paint)
        paint.maskFilter = null
    }

    private fun drawTitle(canvas: Canvas, sx: Float, sy: Float) {
        val alpha = 1f
        val size = fitText(RdsCopy.title, 272f * sx, archivo, 44f * sx)
        paint.resetText(archivo, size, Color.WHITE, alpha)
        paint.shader = LinearGradient(
            0f,
            RdsLayout.titleBase * sy - size * .75f,
            0f,
            RdsLayout.titleBase * sy,
            intArrayOf(Color.WHITE, 0xFFE7E3EF.toInt(), 0xFFB9B3C6.toInt()),
            floatArrayOf(0f, .55f, 1f),
            Shader.TileMode.CLAMP,
        )
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = archivo; textSize = size; color = 0xFFB9A2FF.toInt(); this.alpha = (.28f * alpha * 255f).toInt()
            maskFilter = rdsSigmaBlur(10f * sx, BlurMaskFilter.Blur.NORMAL)
        }
        drawCentred(canvas, RdsCopy.title, 201f * sx, RdsLayout.titleBase * sy, glowPaint)
        drawCentred(canvas, RdsCopy.title, 201f * sx, RdsLayout.titleBase * sy, paint)
        paint.shader = null
        paint.resetText(regular, 15.5f * sx, 0xFF9B93AD.toInt(), alpha)
        drawCentred(canvas, RdsCopy.sub1, 201f * sx, RdsLayout.sub1Y * sy, paint)
        drawCentred(canvas, RdsCopy.sub2, 201f * sx, RdsLayout.sub2Y * sy, paint)
    }

    private fun drawSparkles(canvas: Canvas, frame: RdsFrame, sx: Float, sy: Float) {
        val alpha = 1f - frame.dim
        val tw0 = .7f + .3f * sin(frame.t / LOOP * PI.toFloat() * 4f)
        val tw1 = .7f + .3f * sin(frame.t / LOOP * PI.toFloat() * 4f + 2f)
        sparkle(canvas, 50f * sx, 346f * sy, 11f * sx * tw0, .85f * alpha)
        sparkle(canvas, 345f * sx, 388f * sy, 8f * sx * tw1, .7f * alpha)
    }

    private fun drawChips(canvas: Canvas, sx: Float, sy: Float) {
        val alpha = 1f
        val y = RdsLayout.chipsY * sy
        paint.typeface = medium
        paint.textSize = 11f * sx
        val widths = RdsCopy.chips.map { simpleTextWidth(it, paint) + 16f * sx }
        paint.typeface = semibold
        val favouriteWidth = simpleTextWidth(RdsCopy.favourite, paint)
        val gap = 6f * sx
        val star = 30f * sx
        var x = 201f * sx - (widths.sum() + gap + star * 2f + favouriteWidth) / 2f
        x += chip(canvas, RdsCopy.chips[0], x, y, widths[0], sx, alpha) + gap
        x += chip(canvas, RdsCopy.chips[1], x, y, widths[1], sx, alpha)
        val star1 = x + star / 2f
        x += star
        paint.resetText(semibold, 11f * sx, 0xFFD9D3E6.toInt(), alpha)
        drawSimpleText(canvas, RdsCopy.favourite, x, y + 4f * sx, paint)
        x += favouriteWidth
        val star2 = x + star / 2f
        x += star
        chip(canvas, RdsCopy.chips[2], x, y, widths[2], sx, alpha)
        for (starX in floatArrayOf(star1, star2)) {
            paint.resetPaint(0xFF8A83A0.toInt(), .7f * alpha, Paint.Style.STROKE)
            paint.strokeWidth = .9f * sx
            canvas.drawLine(starX - 10f * sx, y, starX + 10f * sx, y, paint)
            sparkle(canvas, starX, y, 5f * sx, .85f * alpha)
        }
    }

    private fun chip(canvas: Canvas, label: String, x: Float, y: Float, width: Float, sx: Float, alpha: Float): Float {
        paint.resetPaint(0xFF6E6880.toInt(), .9f * alpha, Paint.Style.STROKE)
        paint.strokeWidth = sx
        canvas.drawRoundRect(RectF(x + .5f * sx, y - 10f * sx, x + width - .5f * sx, y + 10f * sx), 10f * sx, 10f * sx, paint)
        paint.resetText(medium, 11f * sx, 0xFFB4ADC4.toInt(), alpha)
        drawSimpleText(canvas, label, x + 8f * sx, y + 4f * sx, paint)
        return width
    }

    private fun selectionAmount(frame: RdsFrame, card: Int): Float = when {
        card == frame.to && card == frame.from -> 1f
        card == frame.to -> frame.selection
        card == frame.from -> 1f - frame.selection
        else -> 0f
    }

    private fun drawCard(canvas: Canvas, frame: RdsFrame, card: Int, sx: Float, sy: Float) {
        val selected = selectionAmount(frame, card)
        val alpha = 1f - frame.dim * .85f
        val x = (RdsLayout.cardX0 + card * (RdsLayout.cardW + RdsLayout.cardGap) + frame.strip) * sx
        val y = (RdsLayout.cardY - 2f * selected) * sy
        val width = RdsLayout.cardW * sx
        val height = RdsLayout.cardH * sy
        if (x > RDS_REF_W * sx + 4f || x + width < -4f) return
        val path = cardPaths[card]
        val geometry = cardGeometry[card]
        if (geometry[0] != x || geometry[1] != y || geometry[2] != width || geometry[3] != height) {
            ticketPath(path, x, y, width, height, 18f * sx, 58f * sx, 7f * sx)
            geometry[0] = x; geometry[1] = y; geometry[2] = width; geometry[3] = height
        }
        canvas.save()
        val pressScale = 1f - .04f * frame.cardPress[card]
        canvas.scale(pressScale, pressScale, x + width / 2f, y + height / 2f)
        if (card == frame.to && frame.absorb > .01f) {
            paint.resetPaint(0xFFFFD27A.toInt(), .85f * frame.absorb)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f * sx
            paint.maskFilter = rdsSigmaBlur(6f * sx, BlurMaskFilter.Blur.SOLID)
            canvas.drawPath(path, paint); paint.maskFilter = null
        }
        if (selected > .01f) {
            paint.resetPaint(0xFF6D3FF0.toInt(), .55f * selected * alpha)
            paint.maskFilter = rdsSigmaBlur(12f * sx, BlurMaskFilter.Blur.OUTER)
            canvas.drawPath(path, paint)
            paint.maskFilter = null
        }
        paint.shader = LinearGradient(
            x,
            y,
            x,
            y + height,
            intArrayOf(
                Color.argb((.92f * alpha * 255f).toInt(), 0x2A, 0x1D, 0x4A),
                Color.argb((.92f * alpha * 255f).toInt(), 0x15, 0x0E, 0x26),
                Color.argb((.95f * alpha * 255f).toInt(), 0x0B, 0x08, 0x14),
            ),
            floatArrayOf(0f, .45f, 1f),
            Shader.TileMode.CLAMP,
        )
        paint.alpha = 255
        canvas.drawPath(path, paint)
        val by = y + RdsLayout.ballDy * sy
        canvas.save(); canvas.clipPath(path)
        if (canvas.isHardwareAccelerated && Build.VERSION.SDK_INT >= 33 && raysShader != null) {
            raysShader.draw(canvas, paint, RectF(x, y, x + width, y + height * .62f), x + width / 2f, by, (.25f + .75f * selected) * alpha, frame.t, sx)
        } else {
            paint.shader = RadialGradient(x + width / 2f, by, 80f * sx, 0x886D3FF0.toInt(), Color.TRANSPARENT, Shader.TileMode.CLAMP); canvas.drawPath(path, paint); paint.shader = null
        }
        RDS_ICON_BALLS[card].forEach { icon ->
            paint.resetPaint(0xFF8B62FF.toInt(), .45f * alpha)
            paint.maskFilter = rdsSigmaBlur(12f * sx, BlurMaskFilter.Blur.NORMAL)
            canvas.drawCircle(x + icon[0] * sx, by, icon[1] * sx * 1.05f, paint); paint.maskFilter = null
        }
        paint.shader = LinearGradient(x, y, x + width * .4f, y + height * .5f, intArrayOf(0x12FFFFFF, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        paint.alpha = (alpha * 255f).toInt(); canvas.drawRect(x, y, x + width, y + height, paint); paint.shader = null
        canvas.restore()
        RDS_ICON_BALLS[card].forEachIndexed { index, icon ->
            val alpha = frame.iconAlpha[card][index]
            if (alpha > .01f) {
                val spin = frame.t / LOOP * PI.toFloat() * 2f
                val scale = .92f + .08f * alpha
                drawBall(canvas, x + icon[0] * sx, by, icon[1] * sx * scale, .4f + index * 1.3f, .6f + .15f * sin(spin + index), .3f * index, alpha * (1f - frame.dim * .85f), 1f, sx)
            }
        }
        if (selected > .02f && selected < .98f) {
            paint.resetPaint(0xFF2A2140.toInt(), (1f - selected) * alpha, Paint.Style.STROKE)
            paint.strokeWidth = sx
            canvas.drawPath(path, paint)
        }
        paint.resetPaint(
            if (selected > .5f) 0xFF6D3FF0.toInt() else 0xFF2A2140.toInt(),
            (if (selected > .02f && selected < .98f) selected else (.9f + .1f * selected)) * alpha,
            Paint.Style.STROKE,
        )
        paint.strokeWidth = (1f + 1.2f * selected) * sx
        canvas.drawPath(path, paint)
        if (card == frame.to && frame.sweep > 0f && frame.sweep < 1f) {
            val measure = PathMeasure(path, false)
            val length = measure.length
            val end = (frame.sweep + .62f) * length
            val start = ((end - 90f * sx) % length + length) % length
            val finish = ((end % length) + length) % length
            val glint = Path()
            if (finish > start) measure.getSegment(start, finish, glint, true)
            else {
                measure.getSegment(start, length, glint, true)
                measure.getSegment(0f, finish, glint, true)
            }
            paint.resetPaint(0xFFD9CCFF.toInt(), sin(PI.toFloat() * frame.sweep) * alpha, Paint.Style.STROKE)
            paint.strokeWidth = 2.6f * sx
            paint.strokeCap = Paint.Cap.ROUND
            paint.maskFilter = rdsSigmaBlur(3f * sx, BlurMaskFilter.Blur.SOLID)
            canvas.drawPath(glint, paint)
            paint.maskFilter = null
        }
        val checkX = x + RdsLayout.checkDx * sx
        val checkY = y + RdsLayout.checkDy * sy
        val checkRadius = RdsLayout.checkD * sx / 2f
        val gold = frame.enteredCards[card]
        paint.resetPaint(0xFF1D1730.toInt(), .9f * alpha)
        canvas.drawCircle(checkX, checkY, checkRadius, paint)
        paint.resetPaint(0xFF3A3150.toInt(), (1f - selected) * alpha, Paint.Style.STROKE); paint.strokeWidth = 1.2f * sx; canvas.drawCircle(checkX, checkY, checkRadius, paint)
        val pop = if (card == frame.to) max(0f, frame.check) * selected else selected
        if (pop > .01f || gold > .01f) {
            canvas.save(); canvas.translate(checkX, checkY)
            val amount = max(pop, gold).coerceIn(0f, 1f)
            val popScale = (.6f + .4f * amount) * if (pop > 1f) pop else 1f
            canvas.scale(popScale, popScale)
            paint.resetPaint(0xFF6D3FF0.toInt(), alpha * amount); canvas.drawCircle(0f, 0f, checkRadius, paint)
            if (gold > .01f) {
                paint.resetPaint(0xFFFFD27A.toInt(), .6f * gold * alpha); paint.maskFilter = rdsSigmaBlur(6f * sx, BlurMaskFilter.Blur.NORMAL); canvas.drawCircle(0f, 0f, checkRadius * 1.1f, paint); paint.maskFilter = null
                paint.shader = LinearGradient(0f, -checkRadius, 0f, checkRadius, intArrayOf(0xFFFFF0C2.toInt(), 0xFFE0A83E.toInt()), null, Shader.TileMode.CLAMP); paint.alpha = (gold * alpha * 255f).toInt(); canvas.drawCircle(0f, 0f, checkRadius, paint); paint.shader = null
            }
            paint.resetPaint(if (gold > .5f) 0xFF3A2206.toInt() else Color.WHITE, alpha * amount, Paint.Style.STROKE)
            paint.strokeWidth = 2.2f * sx
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            checkPath.reset()
            checkPath.moveTo(-5f * sx, .5f * sx)
            checkPath.lineTo(-1.5f * sx, 4f * sx)
            checkPath.lineTo(5.5f * sx, -4f * sx)
            canvas.drawPath(checkPath, paint)
            canvas.restore()
        }
        paint.resetText(regular, 15f * sx, if (selected > .5f) 0xFFC7C0D6.toInt() else 0xFFA9A2BA.toInt(), alpha)
        RdsCopy.cardLines[card].forEachIndexed { line, text ->
            drawSimpleText(canvas, text, x + RdsLayout.textDx * sx, y + (RdsLayout.textDy + 16f + line * RdsLayout.textLine) * sy, paint)
        }
        canvas.restore()
    }

    private fun drawPlay(canvas: Canvas, frame: RdsFrame, sx: Float, sy: Float) {
        val pressScale = 1f - .04f * max(frame.playPress, frame.claimPress)
        val x = RdsLayout.playX * sx
        val y = RdsLayout.playY * sy
        val width = RdsLayout.playW * sx
        val height = RdsLayout.playH * sy
        canvas.save()
        canvas.scale(pressScale, pressScale, x + width / 2f, y + height / 2f)
        paint.resetPaint(0xFF6A1CD6.toInt(), .4f + .3f * frame.speed)
        paint.maskFilter = rdsSigmaBlur(16f * sx, BlurMaskFilter.Blur.NORMAL)
        canvas.drawRoundRect(RectF(x + 20f * sx, y + 10f * sx, x + width - 20f * sx, y + height + 4f * sx), height / 2f, height / 2f, paint)
        paint.maskFilter = null
        paint.resetPaint(Color.WHITE, 1f)
        paint.shader = LinearGradient(x, y, x, y + height, intArrayOf(0xFF7426E6.toInt(), 0xFF6A1CD6.toInt(), 0xFF4A0CA8.toInt()), floatArrayOf(0f, .4f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(x, y, x + width, y + height), height / 2f, height / 2f, paint)
        paint.shader = null
        if (frame.fill > .001f && frame.drain < .999f) {
            canvas.save()
            path.reset()
            path.addRoundRect(RectF(x, y, x + width, y + height), height / 2f, height / 2f, Path.Direction.CW)
            canvas.clipPath(path)
            if (Build.VERSION.SDK_INT >= 33 && liquidShader != null) {
                liquidShader.configure(paint, x, y, width, height, frame.fill, frame.drain, x + frame.drainAt * sx, y + height / 2f, frame.t, sx)
                canvas.drawRect(x, y, x + width, y + height, paint)
                paint.shader = null
            } else {
                val level = height * frame.fill * (1f - frame.drain)
                paint.resetPaint(0xFFFFD27A.toInt(), 1f)
                canvas.clipRect(x, y + height - level, x + width, y + height)
                canvas.drawRoundRect(RectF(x, y, x + width, y + height), height / 2f, height / 2f, paint)
            }
            if (frame.claimGlint > 0f && frame.claimGlint < 1f && frame.drain == 0f) {
                val gx = x - 60f * sx + (width + 120f * sx) * frame.claimGlint
                paint.resetPaint(Color.WHITE, 1f)
                paint.blendMode = BlendMode.PLUS
                paint.shader = LinearGradient(gx - 40f * sx, y, gx + 40f * sx, y + height, intArrayOf(0x00FFFFFF, 0x8CFFFAEB.toInt(), 0x00FFFFFF), null, Shader.TileMode.CLAMP)
                canvas.drawRoundRect(RectF(x, y, x + width, y + height), height / 2f, height / 2f, paint)
                paint.shader = null
                paint.blendMode = null
            }
            canvas.restore()
        }
        paint.resetPaint(0xFFB79CFF.toInt(), .55f, Paint.Style.STROKE)
        paint.strokeWidth = sx
        canvas.drawRoundRect(RectF(x + .5f * sx, y + .5f * sx, x + width - .5f * sx, y + height - .5f * sx), height / 2f, height / 2f, paint)
        if (frame.shimmer > 0f && frame.shimmer < 1f) {
            canvas.save()
            path.reset()
            path.addRoundRect(RectF(x, y, x + width, y + height), height / 2f, height / 2f, Path.Direction.CW)
            canvas.clipPath(path)
            val shineX = x - 80f * sx + (width + 160f * sx) * frame.shimmer
            paint.resetPaint(Color.WHITE, 1f)
            paint.blendMode = BlendMode.PLUS
            paint.shader = LinearGradient(shineX - 60f * sx, y, shineX + 60f * sx, y + height, intArrayOf(0x00FFFFFF, 0x80DCCDFF.toInt(), 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            canvas.drawRect(x, y, x + width, y + height, paint)
            paint.shader = null
            paint.blendMode = null
            canvas.restore()
        }
        val out = (frame.claim / .45f).coerceIn(0f, 1f)
        val into = ((frame.claim - .55f) / .45f).coerceIn(0f, 1f)
        drawPlayLabel(canvas, RdsCopy.play, 1f - out, Color.WHITE, -5f * out * sx, x, y, width, height, sx)
        drawPlayLabel(canvas, RdsCopy.claim, into, 0xFF3B2205.toInt(), 5f * (1f - into) * sx, x, y, width, height, sx)
        canvas.restore()
    }

    private fun drawPlayLabel(canvas: Canvas, label: String, alpha: Float, color: Int, lift: Float, x: Float, y: Float, width: Float, height: Float, sx: Float) {
        if (alpha <= .01f) return
        paint.resetText(semibold, 17f * sx, color, alpha)
        val blur = 2f * (1f - alpha) * sx
        if (blur > .2f) paint.maskFilter = rdsSigmaBlur(blur, BlurMaskFilter.Blur.NORMAL)
        drawCentred(canvas, label, x + width / 2f, y + height / 2f + 6.2f * sx + lift, paint)
        paint.maskFilter = null
    }

    private fun drawLightEffects(canvas: Canvas, frame: RdsFrame, width: Float, height: Float, sx: Float, sy: Float) {
        if (frame.streak <= .001f && frame.rays <= .001f && frame.ring <= .001f && frame.aurora <= .001f) return
        if (Build.VERSION.SDK_INT >= 33 && lightShader != null) {
            lightShader.draw(canvas, paint, width, height, frame, sx, sy)
            return
        }
        if (frame.rays > .01f) {
            paint.resetPaint(0xFFFFD27A.toInt(), .18f * frame.rays)
            val cx = 201f * sx
            val cy = 432f * sy
            repeat(16) { index ->
                val angle = index * PI.toFloat() * 2f / 16f
                path.reset()
                path.moveTo(cx, cy)
                path.lineTo(cx + cos(angle) * 220f * sx, cy + sin(angle) * 160f * sy)
                path.lineTo(cx + cos(angle + .045f) * 220f * sx, cy + sin(angle + .045f) * 160f * sy)
                path.close()
                canvas.drawPath(path, paint)
            }
        }
        if (frame.ring > .01f) {
            val age = frame.t - RdsTime.burst
            val radius = (40f + 260f * (age / .5f).coerceIn(0f, 1f)) * sx
            paint.resetPaint(0xFFFFD27A.toInt(), frame.ring, Paint.Style.STROKE)
            paint.strokeWidth = 3f * sx
            canvas.drawCircle(201f * sx, 432f * sy, radius, paint)
        }
    }

    private fun drawTicket(canvas: Canvas, frame: RdsFrame, sx: Float, sy: Float) {
        val width = 300f * sx
        val height = 111f * sx
        val left = 201f * sx - width / 2f
        val top = 432f * sy - height / 2f
        val body = .729f
        val held = frame.t - RdsTime.morph1
        val bob = if (held > 0f) sin(held * 2.1f) * 3f * sy * (1f - frame.burn) else 0f
        val centreX = 201f * sx
        val centreY = 432f * sy + bob
        val textAlpha = seg(frame.morph.toDouble(), .82, 1.0).toFloat()

        if (frame.burn < 1f) {
            paint.resetPaint(0xFFFFD27A.toInt(), .32f * (1f - frame.burn))
            paint.maskFilter = rdsSigmaBlur(28f * sx, BlurMaskFilter.Blur.NORMAL)
            canvas.drawRoundRect(
                RectF(left + 12f * sx, top + bob + 16f * sy, left + width - 12f * sx, top + bob + height + 8f * sy),
                20f * sx,
                20f * sx,
                paint,
            )
            paint.maskFilter = null

            canvas.save()
            canvas.translate(centreX, centreY)
            val perspective = Matrix().apply {
                setValues(floatArrayOf(
                    cos(frame.tiltX), 0f, 0f,
                    0f, cos(frame.tiltY), 0f,
                    sin(frame.tiltX) / (700f * sx), -sin(frame.tiltY) / (700f * sx), 1f,
                ))
            }
            canvas.concat(perspective)
            canvas.translate(-centreX, -(centreY - bob))

            val bodyRight = left + width * if (frame.tear == 0f) 1f else body
            val layer = RectF(left - 2f * sx, top - 2f * sy, left + width + 2f * sx, top + height + 2f * sy)
            if (frame.burn > 0f) canvas.saveLayer(layer, null)
            if (Build.VERSION.SDK_INT >= 33 && foilShader != null) {
                foilShader.configure(paint, left, top, width, height, ticket.width.toFloat(), ticket.height.toFloat(), frame, 0f)
                canvas.drawRect(left, top, bodyRight, top + height, paint)
                paint.shader = null
            } else {
                paint.resetPaint(Color.WHITE, frame.ticketAlpha)
                val sourceRight = (ticket.width * ((bodyRight - left) / width)).toInt()
                canvas.drawBitmap(ticket, Rect(0, 0, sourceRight, ticket.height), RectF(left, top, bodyRight, top + height), paint)
            }
            drawTicketText(canvas, frame, textAlpha, stub = frame.tear == 0f, body = true, sx = sx, sy = sy)
            if (frame.burn > 0f && Build.VERSION.SDK_INT >= 33 && foilShader != null) {
                foilShader.configure(paint, left, top, width, height, ticket.width.toFloat(), ticket.height.toFloat(), frame, 1f, 0f, 0f)
                paint.blendMode = BlendMode.DST_IN
                canvas.drawRect(left, top, left + width * body, top + height, paint)
                paint.blendMode = null
                paint.shader = null
                canvas.restore()

                foilShader.configure(paint, left, top, width, height, ticket.width.toFloat(), ticket.height.toFloat(), frame, 2f, 0f, 0f)
                paint.blendMode = BlendMode.PLUS
                canvas.drawRect(left, top, left + width * body, top + height, paint)
                paint.blendMode = null
                paint.shader = null
            }
            canvas.restore()
        }

        drawDust(canvas, frame, left, top + bob, width, height, sx, sy)

        if (frame.tear > 0f) {
            val stubWidth = width * (1f - body)
            val startX = left + width * body + stubWidth / 2f
            val startY = 432f * sy
            val cardX = (RdsLayout.cardX0 + frame.to * (RdsLayout.cardW + RdsLayout.cardGap) + frame.strip + RdsLayout.checkDx) * sx
            val cardY = (RdsLayout.cardY - 2f + RdsLayout.checkDy) * sy
            val rip = seg(frame.tear.toDouble(), 0.0, .25).toFloat()
            val fly = easeInOut(seg(frame.tear.toDouble(), .25, 1.0)).toFloat()
            val v = 1f - fly
            val px = v * v * (startX + 8f * sx * rip) + 2f * v * fly * ((startX + cardX) / 2f) + fly * fly * cardX
            val py = v * v * startY + 2f * v * fly * (min(startY, cardY) - 120f * sy) + fly * fly * cardY
            val rotation = -9f * sin(PI.toFloat() * min(1f, rip * 1.4f)) * (1f - fly) + 140f * fly * (1f - fly)
            val scale = mix(1.0, (RdsLayout.checkD * sx / height).toDouble(), fly.toDouble()).toFloat()
            val flip = cos(fly * PI.toFloat() * 2f)
            val alpha = 1f - seg(frame.tear.toDouble(), .9, 1.0).toFloat()
            if (alpha > .01f) {
                canvas.save()
                canvas.translate(px, py)
                canvas.rotate(rotation)
                canvas.scale(scale * max(.04f, kotlin.math.abs(flip)), scale)
                val rect = RectF(-stubWidth / 2f, -height / 2f, stubWidth / 2f, height / 2f)
                val layered = alpha < .999f || kotlin.math.abs(flip) < .995f
                if (layered) {
                    paint.resetPaint(Color.WHITE, alpha)
                    canvas.saveLayer(rect, paint)
                }
                if (flip >= 0f && Build.VERSION.SDK_INT >= 33 && foilShader != null) {
                    foilShader.configure(paint, -stubWidth / 2f - width * body, -height / 2f, width, height, ticket.width.toFloat(), ticket.height.toFloat(), frame, 0f, 0f, 1f, fly * PI.toFloat() * 2f, 0f)
                    canvas.drawRect(rect, paint)
                    paint.shader = null
                    canvas.save()
                    canvas.translate(-startX, -startY)
                    drawTicketText(canvas, frame, 1f, stub = true, body = false, sx = sx, sy = sy)
                    canvas.restore()
                } else if (flip < 0f) {
                    paint.resetPaint(Color.WHITE, 1f)
                    canvas.drawBitmap(ticketBack, Rect((ticketBack.width * body).toInt(), 0, ticketBack.width, ticketBack.height), rect, paint)
                }
                if (layered) {
                    paint.resetPaint(Color.BLACK, .45f * (1f - kotlin.math.abs(flip)))
                    paint.blendMode = BlendMode.SRC_ATOP
                    canvas.drawRect(rect, paint)
                    paint.blendMode = null
                    canvas.restore()
                }
                canvas.restore()
            }
        }
        val rip = seg(frame.tear.toDouble(), 0.0, .25).toFloat()
        if (rip > 0f && rip < 1f) {
            val fx = left + width * body
            repeat(10) { i ->
                val life = sin(PI.toFloat() * seg(rip.toDouble(), i * .06, i * .06 + .4).toFloat())
                if (life > 0f) {
                    paint.resetPaint(0xFFFFF1C8.toInt(), life)
                    canvas.drawCircle(fx + (rdsHash(i, 81) - .3f) * 10f * sx * rip, top + (i + .5f) * height / 10f, 1.4f * sx, paint)
                }
            }
        }
    }

    private fun drawThreads(canvas: Canvas, frame: RdsFrame, sx: Float, sy: Float) {
        val ball = frame.hero ?: return
        if (frame.stitchT < 0f) return
        val duration = (T.stitches[1] - T.stitches[0]).toFloat()
        repeat(24) { i ->
            val t = i / 24.0 * PI * 2.0
            val order = ((t / (PI * 2.0) + .25) % 1.0 + 1.0) % 1.0
            val age = frame.stitchT - order * duration
            if (age < 0.0 || age > .6) return@repeat
            val a = .78
            val b = .22
            val ox = a * cos(t) + b * cos(3 * t)
            val oy = a * sin(t) - b * sin(3 * t)
            val oz = 2 * kotlin.math.sqrt(a * b) * sin(2 * t)
            val cy = cos(-ball.ry.toDouble())
            val sy0 = sin(-ball.ry.toDouble())
            val nx = cy * ox + sy0 * oz
            val z1 = -sy0 * ox + cy * oz
            val cx = cos(-ball.rx.toDouble())
            val sx0 = sin(-ball.rx.toDouble())
            val ny = cx * oy - sx0 * z1
            val nz = sx0 * oy + cx * z1
            if (nz < 0.0) return@repeat
            val lenN = kotlin.math.hypot(nx, ny).let { if (it == 0.0) 1.0 else it }
            val dx = nx / lenN
            val dy = -ny / lenN
            val k = age / .6
            val distance = 150 * (1 - exp(-age * 5)) / 5 * sx
            var x = ball.x + nx * ball.radius + dx * distance
            var y = ball.y - ny * ball.radius + dy * distance + 130 * age * age * sy
            val spin = (if (rand(i.toDouble(), 41.0) > .5) 1 else -1) * (2 + 3 * rand(i.toDouble(), 42.0))
            var angle = atan2(dy, dx) + spin * age * 3
            val bend = sin(age * 26 + i) * .9 * (1 - k) + .5
            val length = (3.6 + 1.6 * rand(i.toDouble(), 43.0)) * sx
            paint.resetPaint(0xFFB3242A.toInt(), (1 - k * k).toFloat(), Paint.Style.STROKE)
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = 1.5f * sx
            repeat(3) {
                val x2 = x + cos(angle) * length
                val y2 = y + sin(angle) * length
                canvas.drawLine(x.toFloat(), y.toFloat(), x2.toFloat(), y2.toFloat(), paint)
                x = x2; y = y2; angle += bend
            }
            if (age < .09) {
                paint.resetPaint(0xFFFFE6B0.toInt(), (1 - age / .09).toFloat())
                canvas.drawCircle((ball.x + nx * ball.radius).toFloat(), (ball.y - ny * ball.radius).toFloat(), ((1.2 + 2.4 * age / .09) * sx).toFloat(), paint)
            }
        }
    }

    private fun drawCrankHint(canvas: Canvas, frame: RdsFrame, sx: Float) {
        if (frame.focus > .001f) return
        val u = ((frame.t + .45f) % LOOP) / .9f
        if (u >= 1f) return
        val alpha = sin(PI.toFloat() * u)
        val point = project(1.58, -.2 * cos(frame.theta.toDouble()), -.2 * sin(frame.theta.toDouble()), sx.toDouble())
        paint.resetPaint(0xFFFFF1DC.toInt(), .55f * alpha)
        paint.maskFilter = rdsSigmaBlur(6f * sx, BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), 8f * sx, paint)
        paint.resetPaint(Color.WHITE, .9f * alpha)
        canvas.drawCircle(point.x.toFloat() + (-10f + 20f * u) * sx, point.y.toFloat() - 1.5f * sx, 1.6f * sx, paint)
    }

    private fun drawTicketText(canvas: Canvas, frame: RdsFrame, alpha: Float, stub: Boolean, body: Boolean, sx: Float, sy: Float) {
        if (alpha <= .001f) return
        val width = 300f * sx
        val height = 111f * sx
        val left = 201f * sx - width / 2f
        val top = 432f * sy - height / 2f
        val bodyWidth = width * .729f
        val ink = 0xFF4A2C07.toInt()
        if (body) {
            val centre = left + bodyWidth / 2f
            paint.resetText(serif, 10.5f * sx, 0xFFFFF1C4.toInt(), .5f * alpha)
            drawCentredTracked(canvas, RdsCopy.winner, centre, top + height * .36f + .7f * sx, paint, .12f)
            paint.resetText(serif, 10.5f * sx, ink, .85f * alpha)
            drawCentredTracked(canvas, RdsCopy.winner, centre, top + height * .36f, paint, .12f)

            val size = 30f * sx
            paint.resetText(serif, size, ink, alpha)
            val digitWidth = glyphAdvance(paint, "0")
            val total = digitWidth * (RdsCopy.entry.length + 1)
            val base = top + height * .72f
            canvas.save()
            canvas.clipRect(centre - total / 2f - 4f, base - size * .95f, centre + total / 2f + 4f, base + size * .2f)
            paint.resetText(serif, size, 0xFFFFF1C4.toInt(), .45f * alpha)
            drawGlyphText(canvas, "#", centre - total / 2f, base + .8f * sx, paint)
            paint.color = ink; paint.alpha = (alpha * 255f).toInt()
            drawGlyphText(canvas, "#", centre - total / 2f, base, paint)
            RdsCopy.entry.forEachIndexed { index, character ->
                val k = easeOut(seg(frame.number.toDouble(), index * .12, .55 + index * .12)).toFloat()
                val target = character.code - '0'.code
                val position = (1f - k) * (2 + index) * 10f
                val x = centre - total / 2f + digitWidth * (index + 1)
                for (j in -1..1) {
                    val step = kotlin.math.floor(position).toInt() + j
                    val fraction = position - kotlin.math.floor(position)
                    val digit = ((target - step) % 10 + 10) % 10
                    val y = base + (fraction - j) * size * -1f
                    if (k > .6f) {
                        paint.resetText(serif, size, 0xFFFFF1C4.toInt(), .45f * alpha * k)
                        drawGlyphText(canvas, digit.toString(), x, y + .8f * sx, paint)
                    }
                    paint.resetText(serif, size, ink, alpha)
                    val blur = min(3f, (1f - k) * 6f) * sx
                    if (blur > .2f) paint.maskFilter = rdsSigmaBlur(blur, BlurMaskFilter.Blur.NORMAL)
                    drawGlyphText(canvas, digit.toString(), x, y, paint)
                    paint.maskFilter = null
                }
            }
            canvas.restore()
        }
        if (stub) {
            val centre = left + bodyWidth + (width - bodyWidth) / 2f
            paint.resetText(semibold, 8f * sx, ink, .8f * alpha)
            drawCentredTracked(canvas, RdsCopy.stubTop, centre, top + height * .4f, paint, .08f)
            val count = "×${frame.to + 1}"
            paint.resetText(serif, 22f * sx, 0xFFFFF1C4.toInt(), .45f * alpha)
            drawCentred(canvas, count, centre, top + height * .7f + .8f * sx, paint)
            paint.resetText(serif, 22f * sx, ink, alpha)
            drawCentred(canvas, count, centre, top + height * .7f, paint)
        }
    }

    private fun drawDust(canvas: Canvas, frame: RdsFrame, left: Float, top: Float, width: Float, height: Float, sx: Float, sy: Float) {
        val t = frame.t
        if (t < T.burn[0].toFloat() || t > (T.burn[1] + T.dust).toFloat()) return
        val targetX = (RdsLayout.cardX0 + frame.to * (RdsLayout.cardW + RdsLayout.cardGap) + frame.strip + RdsLayout.cardW / 2f) * sx
        val targetY = (RdsLayout.cardY + 62f) * sy
        val duration = (T.burn[1] - T.burn[0]).toFloat()
        var bigCount = 0
        var smallCount = 0
        repeat(120) { index ->
            val u = rdsHash(index, 61) * .729f
            val v = .12f + .76f * rdsHash(index, 62)
            val at = (.729f + .09f - u + (rdsHash(index, 63) - .5f) * .14f) / (.729f + .27f)
            val age = t - (T.burn[0].toFloat() + at * duration)
            if (age < 0f || age > T.dust.toFloat()) return@repeat
            val k = easeInOut((age / T.dust.toFloat()).toDouble()).toFloat()
            val x0 = left + u * width
            val y0 = top + v * height
            val qx = x0 + (rdsHash(index, 64) - .5f) * 90f * sx
            val qy = y0 - (30f + 50f * rdsHash(index, 65)) * sy
            val x1 = targetX + (rdsHash(index, 66) - .5f) * 70f * sx
            val y1 = targetY + (rdsHash(index, 67) - .5f) * 40f * sy
            val w = 1f - k
            val swirl = sin(age * 14f + index) * 5f * sx * w
            val big = rdsHash(index, 68) > .6f
            val points = if (big) dustBig else dustSmall
            val offset = if (big) bigCount else smallCount
            points[offset] = w * w * x0 + 2f * w * k * qx + k * k * x1 + swirl
            points[offset + 1] = w * w * y0 + 2f * w * k * qy + k * k * y1
            if (big) bigCount += 2 else smallCount += 2
        }
        val fadeIn = min(1f, (t - T.burn[0].toFloat()) / .08f)
        fun drawGroup(coordinates: FloatArray, count: Int, haloWidth: Float, coreWidth: Float) {
            if (count == 0) return
            paint.resetPaint(0xFFFFD27A.toInt(), .55f * fadeIn, Paint.Style.STROKE)
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = haloWidth * sx
            paint.maskFilter = rdsSigmaBlur(3f * sx, BlurMaskFilter.Blur.NORMAL)
            canvas.drawPoints(coordinates, 0, count, paint)
            paint.resetPaint(0xFFFFF6DC.toInt(), fadeIn, Paint.Style.STROKE)
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = coreWidth * sx
            canvas.drawPoints(coordinates, 0, count, paint)
        }
        drawGroup(dustBig, bigCount, 7f, 2.6f)
        drawGroup(dustSmall, smallCount, 4.5f, 1.6f)
    }

    private fun drawLanding(canvas: Canvas, frame: RdsFrame, sx: Float, sy: Float) {
        if (frame.land < 0f) return
        val x = (RdsLayout.cardX0 + frame.to * (RdsLayout.cardW + RdsLayout.cardGap) + frame.strip + RdsLayout.checkDx) * sx
        val y = (RdsLayout.cardY - 2f + RdsLayout.checkDy) * sy
        val k = easeOut(min(1.0, frame.land.toDouble() / .5)).toFloat()
        paint.resetPaint(0xFFFFDF8A.toInt(), 1f - k, Paint.Style.STROKE)
        paint.strokeWidth = mix(2.6, .6, k.toDouble()).toFloat() * sx
        canvas.drawCircle(x, y, mix(12.0, 34.0, k.toDouble()).toFloat() * sx, paint)
        if (frame.land < .3f) {
            val q = frame.land / .3f
            paint.resetPaint(0xFFFFF3C8.toInt(), 1f - q, Paint.Style.STROKE)
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = 1.6f * sx
            repeat(8) { index ->
                val angle = index / 8f * PI.toFloat() * 2f + .3f
                val r0 = mix(14.0, 22.0, easeOut(q.toDouble())).toFloat() * sx
                val r1 = r0 + mix(6.0, 2.0, q.toDouble()).toFloat() * sx
                canvas.drawLine(
                    x + cos(angle) * r0,
                    y + sin(angle) * r0,
                    x + cos(angle) * r1,
                    y + sin(angle) * r1,
                    paint,
                )
            }
        }
    }

    private fun drawBall(canvas: Canvas, x: Float, y: Float, radius: Float, rx: Float, ry: Float, rz: Float, alpha: Float, squash: Float, sx: Float) {
        if (radius < .5f || alpha <= .01f) return
        canvas.save()
        canvas.scale(1f, squash, x, y)
        if (canvas.isHardwareAccelerated && Build.VERSION.SDK_INT >= 33 && ballShader != null) {
            ballShader.draw(canvas, paint, x, y, radius, rx, ry, rz, alpha, sx)
            canvas.restore()
            return
        }
        paint.shader = RadialGradient(x - radius * .35f, y - radius * .4f, radius * 1.35f, intArrayOf(0xFFFDFBF4.toInt(), 0xFFE7E0D6.toInt(), 0xFFBDB2C8.toInt()), floatArrayOf(0f, .65f, 1f), Shader.TileMode.CLAMP)
        paint.alpha = (alpha * 255).toInt()
        canvas.drawCircle(x, y, radius, paint)
        paint.shader = null
        paint.resetPaint(0xFFC72438.toInt(), alpha, Paint.Style.STROKE)
        paint.strokeWidth = max(1f, radius * .055f)
        paint.strokeCap = Paint.Cap.ROUND
        val seam = RectF(x - radius * .62f, y - radius * .92f, x + radius * .62f, y + radius * .92f)
        canvas.save()
        canvas.rotate(rx * 28f, x, y)
        canvas.drawArc(seam, -75f, 150f, false, paint)
        canvas.drawArc(seam, 105f, 150f, false, paint)
        paint.strokeWidth = max(.8f, radius * .035f)
        repeat(8) { index ->
            val a = (-65f + index * 18f) * PI.toFloat() / 180f
            val px = x + cos(a) * radius * .62f
            val py = y + sin(a) * radius * .92f
            canvas.drawLine(px - 3f * sx, py - 2f * sx, px + 3f * sx, py + 2f * sx, paint)
        }
        canvas.restore()
        canvas.restore()
    }

    private fun drawClose(canvas: Canvas, sx: Float, sy: Float) {
        val x = RdsLayout.closeX * sx
        val y = RdsLayout.closeY * sy
        val radius = RdsLayout.closeD * sx / 2f
        paint.resetPaint(0xFF120A22.toInt(), .75f)
        canvas.drawCircle(x, y, radius, paint)
        paint.shader = LinearGradient(x, y - radius, x, y + radius, intArrayOf(0x8CC8B9F0.toInt(), 0x267864AA), null, Shader.TileMode.CLAMP)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.1f * sx
        canvas.drawCircle(x, y, radius - .5f * sx, paint)
        paint.shader = null
        paint.resetPaint(0xFFF4F1FB.toInt(), 1f, Paint.Style.STROKE)
        paint.strokeWidth = 2.4f * sx
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(x - 8f * sx, y - 8f * sx, x + 8f * sx, y + 8f * sx, paint)
        canvas.drawLine(x + 8f * sx, y - 8f * sx, x - 8f * sx, y + 8f * sx, paint)
    }

    private fun sparkle(canvas: Canvas, x: Float, y: Float, radius: Float, alpha: Float) {
        path.reset()
        path.moveTo(x, y - radius)
        path.quadTo(x + radius * .09f, y - radius * .09f, x + radius, y)
        path.quadTo(x + radius * .09f, y + radius * .09f, x, y + radius)
        path.quadTo(x - radius * .09f, y + radius * .09f, x - radius, y)
        path.quadTo(x - radius * .09f, y - radius * .09f, x, y - radius)
        path.close()
        paint.resetPaint(0xFFB9A6FF.toInt(), .5f * alpha)
        paint.maskFilter = rdsSigmaBlur(radius * .4f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawPath(path, paint)
        paint.resetPaint(0xFFF3EFFF.toInt(), alpha)
        canvas.drawPath(path, paint)
    }

    private fun ticketPath(out: Path, x: Float, y: Float, width: Float, height: Float, radius: Float, notchWidth: Float, notchDepth: Float) {
        val centre = x + width / 2f
        out.reset()
        out.moveTo(x + radius, y)
        out.lineTo(centre - notchWidth / 2f - notchDepth, y)
        out.cubicTo(centre - notchWidth / 2f + notchDepth * .6f, y, centre - notchWidth / 2f + notchDepth * .4f, y + notchDepth, centre - notchWidth / 2f + notchDepth * 2f, y + notchDepth)
        out.lineTo(centre + notchWidth / 2f - notchDepth * 2f, y + notchDepth)
        out.cubicTo(centre + notchWidth / 2f - notchDepth * .4f, y + notchDepth, centre + notchWidth / 2f - notchDepth * .6f, y, centre + notchWidth / 2f + notchDepth, y)
        out.lineTo(x + width - radius, y)
        out.quadTo(x + width, y, x + width, y + radius)
        out.lineTo(x + width, y + height - radius)
        out.quadTo(x + width, y + height, x + width - radius, y + height)
        out.lineTo(centre + notchWidth / 2f + notchDepth, y + height)
        out.cubicTo(centre + notchWidth / 2f - notchDepth * .6f, y + height, centre + notchWidth / 2f - notchDepth * .4f, y + height - notchDepth, centre + notchWidth / 2f - notchDepth * 2f, y + height - notchDepth)
        out.lineTo(centre - notchWidth / 2f + notchDepth * 2f, y + height - notchDepth)
        out.cubicTo(centre - notchWidth / 2f + notchDepth * .4f, y + height - notchDepth, centre - notchWidth / 2f + notchDepth * .6f, y + height, centre - notchWidth / 2f - notchDepth, y + height)
        out.lineTo(x + radius, y + height)
        out.quadTo(x, y + height, x, y + height - radius)
        out.lineTo(x, y + radius)
        out.quadTo(x, y, x + radius, y)
        out.close()
    }

    private fun fitText(text: String, targetWidth: Float, typeface: Typeface, maximum: Float): Float {
        paint.typeface = typeface
        paint.textSize = maximum
        val width = simpleTextWidth(text, paint)
        return maximum * targetWidth / width
    }

    private fun drawCentred(canvas: Canvas, text: String, centreX: Float, baseline: Float, paint: Paint) {
        drawSimpleText(canvas, text, centreX - simpleTextWidth(text, paint) / 2f, baseline, paint)
    }

    private fun glyphAdvance(paint: Paint, text: String): Float {
        if (Build.VERSION.SDK_INT >= 31 && typography != null) return typography.advance(paint, text)
        return paint.getRunAdvance(text, 0, text.length, 0, text.length, false, text.length)
    }

    private fun drawGlyphText(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint) {
        if (Build.VERSION.SDK_INT >= 31 && typography != null) typography.draw(canvas, text, x, y, paint)
        else canvas.drawText(text, x, y, paint)
    }

    private fun simpleTextWidth(text: String, paint: Paint): Float = glyphAdvance(paint, text)

    private fun drawSimpleText(canvas: Canvas, text: String, startX: Float, baseline: Float, paint: Paint) {
        drawGlyphText(canvas, text, startX, baseline, paint)
    }

    private fun drawCentredTracked(canvas: Canvas, text: String, centreX: Float, baseline: Float, paint: Paint, em: Float) {
        val gap = em * paint.textSize
        val widths = text.map { glyphAdvance(paint, it.toString()) }
        var x = centreX - (widths.sum() + gap * (text.length - 1)) / 2f
        text.forEachIndexed { index, character ->
            drawGlyphText(canvas, character.toString(), x, baseline, paint)
            x += widths[index] + gap
        }
    }

    private fun Paint.resetPaint(colorValue: Int, alphaValue: Float, paintStyle: Paint.Style = Paint.Style.FILL) {
        reset()
        isAntiAlias = true
        color = colorValue
        alpha = (alphaValue.coerceIn(0f, 1f) * 255f).toInt()
        style = paintStyle
    }

    private fun Paint.resetText(typefaceValue: Typeface, size: Float, colorValue: Int, alphaValue: Float) {
        resetPaint(colorValue, alphaValue)
        typeface = typefaceValue
        textSize = size
    }
}

private fun rdsHash(index: Int, salt: Int): Float = rand(index.toDouble(), salt.toDouble()).toFloat()

@androidx.annotation.RequiresApi(33)
private class RdsBackgroundShader {
    private val shader = RuntimeShader(
        """
        uniform float2 size;
        uniform float2 light;
        uniform float glow;
        uniform float time;
        uniform float scale;
        float3 toP3(float3 c) {
          float3 l = pow(clamp(c, 0.0, 1.0), float3(2.2));
          float3 m = float3(dot(l, float3(0.8225, 0.1774, 0.0)), dot(l, float3(0.0332, 0.9669, 0.0)), dot(l, float3(0.0171, 0.0724, 0.9108)));
          return pow(m, float3(1.0 / 2.2));
        }
        float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
        half4 main(float2 p) {
            float y = p.y / size.y;
            float x = p.x / size.x;
            float3 col = mix(float3(.09,.035,.2), float3(.22,.1,.37), smoothstep(0.,.19,y));
            col = mix(col, float3(.11,.016,.26), smoothstep(.19,.33,y));
            col = mix(col, float3(.065,.008,.17), smoothstep(.33,.4,y));
            col = mix(col, float3(.03,.004,.075), smoothstep(.4,.47,y));
            col = mix(col, float3(.004,.002,.012), smoothstep(.47,.56,y));
            col = mix(col, float3(.012,.016,.1), smoothstep(.75,1.,y));
            float2 h = float2((x-.5)/.2,(y-.105)/.1);
            col += float3(.25,.24,.24)*exp(-dot(h,h));
            float2 v = float2((x-.5)/.25,(y-.22)/.2);
            col += float3(.07,.06,.12)*exp(-dot(v,v));
            float2 d = (p-light)/size.x;
            col += float3(.2,.12,.42)*exp(-dot(d,d)*7.)*glow*.5;
            col += (hash(p/scale)-.5)*.012 + time*0.;
            return half4(half3(toP3(max(col,float3(0.)))),1.);
        }
        """.trimIndent(),
    )

    fun draw(canvas: Canvas, paint: Paint, width: Float, height: Float, lightX: Float, lightY: Float, glow: Float, time: Float, scale: Float) {
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("light", lightX, lightY)
        shader.setFloatUniform("glow", glow)
        shader.setFloatUniform("time", time)
        shader.setFloatUniform("scale", scale)
        paint.reset()
        paint.isAntiAlias = true
        paint.shader = shader
        canvas.drawRect(0f, 0f, width, height, paint)
        paint.shader = null
    }
}

/** Android takes a legacy radius; Skia MakeBlur takes standard deviation. */
private fun rdsSigmaBlur(sigma: Float, style: BlurMaskFilter.Blur): BlurMaskFilter =
    BlurMaskFilter(((sigma - .5f) / .57735f).coerceAtLeast(.01f), style)
