package com.ngocdevv.nativecook.blocks.buttons.neonkeycappress

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** The RN canvas artwork, in dp. Soft strokes are rasterized once on software canvases. */
internal class NeonKeycapPressArt(density: Float) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spritePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val add = PorterDuffXfermode(PorterDuff.Mode.ADD)
    private val rect = RectF()
    private val source = Rect()
    private val destination = RectF()
    private val matrix = Matrix()
    private val clip = Path()
    private val icons = Array(9, ::iconPath)
    private val stand = Path().apply {
        addArc(RectF(-0.6f, -0.5f, 0.6f, 0.6f), 0f, 180f)
        moveTo(0f, 0.6f)
        lineTo(0f, 0.95f)
    }
    private val ai = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textSize = 30f
        color = Color.WHITE
    }
    private val aiWidth = ai.measureText("AI")
    private val body = LinearGradient(
        0f, -0.5f, 0f, 0.5f,
        intArrayOf(Color.argb(97, 120, 70, 220), Color.argb(140, 50, 20, 110)),
        null, Shader.TileMode.CLAMP,
    )
    private val hotFill = RadialGradient(
        0f, 0f, 1f,
        intArrayOf(0xFFFF9CF5.toInt(), 0xFFC04BFF.toInt(), 0xFF6A2BD8.toInt()),
        floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
    )
    private var room: RadialGradient? = null
    private var roomWidth = -1f
    private var roomHeight = -1f

    // The wide, low-frequency halo needs no device-resolution surface. Two tracks preserve
    // its exact size during the sink/rise and during the full-size glow's fade.
    private val halo = makeHaloAtlas(minOf(density, 0.75f))
    private val rings = makeRingAtlas(minOf(density, 1f))

    fun draw(canvas: Canvas, width: Float, height: Float, frame: NeonKeycapPressFrame) {
        val cx = width / 2f
        val cy = height * 0.48f
        canvas.drawColor(0xFF07020F.toInt())
        if (room == null || width != roomWidth || height != roomHeight) {
            roomWidth = width
            roomHeight = height
            room = RadialGradient(
                cx, cy, width * 1.1f,
                intArrayOf(0xFF3A1670.toInt(), 0xFF07020F.toInt()),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP,
            )
        }
        paint.reset()
        paint.isAntiAlias = true
        paint.shader = room
        paint.alpha = ((0.35f + 0.65f * frame.glow) * 255f).roundToInt()
        canvas.drawRect(0f, 0f, width, height, paint)

        for (i in 0 until 9) {
            val kx = cx - 108f + (i % 3) * 108f
            val ky = cy - 108f + (i / 3) * 108f
            val selected = i == frame.key
            val glow = if (selected) frame.glow else 0f
            val sink = if (selected) frame.sink else 1f
            val ripple = if (selected) frame.ripple else -1f
            val side = 92f * sink
            val radius = 22f * sink
            rect.set(kx - side / 2f, ky - side / 2f, kx + side / 2f, ky + side / 2f)

            // The small initial sink has its own zero-glow frames, then follows the rise track.
            val haloIndex = when {
                glow <= 0f -> 66f + ((1f - sink) / (1f - 0.9466667f)).coerceIn(0f, 1f) * 8f
                sink < 0.99999f -> glow.coerceIn(0f, 1f) * 32f
                else -> 33f + glow.coerceIn(0f, 1f) * 32f
            }
            drawSprite(canvas, halo, haloIndex, kx, ky, interpolate = selected)

            matrix.setScale(1f, side)
            matrix.postTranslate(kx, ky)
            body.setLocalMatrix(matrix)
            paint.reset()
            paint.isAntiAlias = true
            paint.shader = body
            paint.alpha = 255
            canvas.drawRoundRect(rect, radius, radius, paint)

            if (glow > 0.001f || ripple >= 0f) {
                canvas.save()
                clip.rewind()
                clip.addRoundRect(rect, radius, radius, Path.Direction.CW)
                canvas.clipPath(clip)
                matrix.setScale(side * 0.75f, side * 0.75f)
                matrix.postTranslate(kx, ky + 10f)
                hotFill.setLocalMatrix(matrix)
                paint.reset()
                paint.isAntiAlias = true
                paint.shader = hotFill
                paint.alpha = (glow * 0.85f * 255f).roundToInt()
                canvas.drawRoundRect(rect, radius, radius, paint)
                for (r in 0 until 3) {
                    val age = ripple - r * 0.18f
                    if (age >= 0f && age <= 1f) {
                        drawSprite(canvas, rings, age * 64f, kx, ky + 8f, interpolate = true)
                    }
                }
                canvas.restore()
            }

            paint.reset()
            paint.isAntiAlias = true
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = if (glow > 0.3f) 0xFFFFD6FF.toInt() else 0xFFC59BFF.toInt()
            canvas.drawRoundRect(rect, radius, radius, paint)

            paint.reset()
            paint.isAntiAlias = true
            paint.color = Color.WHITE
            paint.alpha = ((0.85f + 0.15f * glow) * 255f).roundToInt()
            if (i == 1) {
                ai.alpha = paint.alpha
                canvas.drawText("AI", kx - aiWidth / 2f, ky + 11f, ai)
            } else {
                canvas.save()
                canvas.translate(kx, ky)
                canvas.scale(22f * sink, 22f * sink)
                canvas.drawPath(icons[i], paint)
                if (i == 3) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 0.16f
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.alpha = 255
                    canvas.drawPath(stand, paint)
                }
                canvas.restore()
            }
        }
        if (frame.ghost) {
            paint.reset()
            paint.isAntiAlias = true
            paint.color = Color.argb(115, 255, 255, 255)
            canvas.drawCircle(frame.fingerX, frame.fingerY, 18f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.alpha = 217
            canvas.drawCircle(frame.fingerX, frame.fingerY, 18f, paint)
        }
    }

    private data class Atlas(val bitmap: Bitmap, val cell: Int, val columns: Int, val extent: Float)

    private fun drawSprite(canvas: Canvas, atlas: Atlas, index: Float, x: Float, y: Float, interpolate: Boolean) {
        val first = floor(index).toInt()
        val fraction = index - first
        val half = atlas.extent / 2f
        destination.set(x - half, y - half, x + half, y + half)
        spritePaint.alpha = 255
        spritePaint.xfermode = null
        if (!interpolate || fraction < 0.001f) {
            drawTile(canvas, atlas, first)
            return
        }
        // Interpolate premultiplied blur pixels on a small isolated layer. ADD here blends
        // the two atlas frames; the restored layer uses normal source-over on the room.
        val save = canvas.saveLayer(destination, null)
        spritePaint.xfermode = add
        spritePaint.alpha = ((1f - fraction) * 255f).roundToInt()
        drawTile(canvas, atlas, first)
        spritePaint.alpha = (fraction * 255f).roundToInt()
        drawTile(canvas, atlas, first + 1)
        canvas.restoreToCount(save)
        spritePaint.xfermode = null
        spritePaint.alpha = 255
    }

    private fun drawTile(canvas: Canvas, atlas: Atlas, index: Int) {
        val left = (index % atlas.columns) * atlas.cell
        val top = (index / atlas.columns) * atlas.cell
        source.set(left, top, left + atlas.cell, top + atlas.cell)
        canvas.drawBitmap(atlas.bitmap, source, destination, spritePaint)
    }

    private fun makeHaloAtlas(scale: Float): Atlas {
        val extent = 264f
        val cell = ceil(extent * scale).toInt()
        val columns = 8
        val bitmap = Bitmap.createBitmap(cell * columns, cell * 10, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFFB266FF.toInt() }
        val r = RectF()
        for (i in 0 until 75) {
            val glow = if (i < 66) (i % 33) / 32f else 0f
            val sink = when {
                i >= 66 -> 1f - (i - 66) / 8f * (1f - 0.9466667f)
                i >= 33 -> 1f
                glow < 1f / 6f -> 0.9466667f - glow * 0.16f
                else -> 0.904f + glow * 0.096f
            }
            val left = (i % columns) * cell
            val top = (i / columns) * cell
            c.save()
            c.clipRect(left, top, left + cell, top + cell)
            val x = left + cell / 2f
            val y = top + cell / 2f
            val half = 46f * sink * scale
            r.set(x - half, y - half, x + half, y + half)
            p.alpha = ((0.35f + 0.55f * glow) * 255f).roundToInt()
            p.strokeWidth = (6f + 6f * glow) * scale
            p.maskFilter = blurSigma((10f + 16f * glow) * scale)
            c.drawRoundRect(r, 22f * sink * scale, 22f * sink * scale, p)
            c.restore()
        }
        return Atlas(bitmap, cell, columns, extent)
    }

    private fun makeRingAtlas(scale: Float): Atlas {
        val extent = 176f
        val cell = ceil(extent * scale).toInt()
        val columns = 8
        val bitmap = Bitmap.createBitmap(cell * columns, cell * 9, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = 0xFFFFE3FF.toInt()
            maskFilter = blurSigma(2f * scale)
        }
        for (i in 0..64) {
            val age = i / 64f
            val x = (i % columns) * cell + cell / 2f
            val y = (i / columns) * cell + cell / 2f
            p.strokeWidth = (5f * (1f - age) + 1f) * scale
            p.alpha = ((1f - age) * 0.9f * 255f).roundToInt()
            c.drawCircle(x, y, 80f * sqrt(age) * scale, p)
        }
        return Atlas(bitmap, cell, columns, extent)
    }

    // Android's public blur radius is converted to Skia sigma as radius*.57735+.5.
    // RN passes sigma directly, so invert that conversion before creating the mask filter.
    private fun blurSigma(sigma: Float) = BlurMaskFilter((sigma - 0.5f) / 0.57735f, BlurMaskFilter.Blur.NORMAL)

    private fun iconPath(index: Int) = Path().apply {
        when (index) {
            0 -> { moveTo(-1f, -0.1f); lineTo(1f, -0.9f); lineTo(0.35f, 0.95f); lineTo(0.05f, 0.2f); close() }
            2 -> { moveTo(0.2f, -1f); lineTo(-0.55f, 0.12f); lineTo(-0.02f, 0.12f); lineTo(-0.25f, 1f); lineTo(0.55f, -0.15f); lineTo(0.02f, -0.15f); close() }
            3 -> addRoundRect(RectF(-0.32f, -1f, 0.32f, 0.2f), 0.32f, 0.32f, Path.Direction.CW)
            4 -> { moveTo(-0.55f, -0.85f); lineTo(0.85f, 0f); lineTo(-0.55f, 0.85f); close() }
            5 -> { moveTo(0f, 0.85f); cubicTo(-1.1f, 0.1f, -0.9f, -0.95f, 0f, -0.45f); cubicTo(0.9f, -0.95f, 1.1f, 0.1f, 0f, 0.85f); close() }
            6 -> { moveTo(0.2f, -0.95f); cubicTo(-0.75f, -0.85f, -1f, 0.35f, -0.35f, 0.8f); cubicTo(0.15f, 1.1f, 0.75f, 0.85f, 0.95f, 0.35f); cubicTo(0.3f, 0.55f, -0.35f, 0.05f, 0.2f, -0.95f); close() }
            7 -> {
                for (i in 0 until 10) {
                    val angle = -PI / 2 + i * PI / 5
                    val radius = if (i % 2 == 0) 1f else 0.45f
                    val x = cos(angle).toFloat() * radius
                    val y = sin(angle).toFloat() * radius
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            else -> {
                addRect(-0.12f, -0.85f, 0.12f, 0.85f, Path.Direction.CW)
                addRect(-0.85f, -0.12f, 0.85f, 0.12f, Path.Direction.CW)
            }
        }
    }
}
