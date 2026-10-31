package com.ngocdevv.nativecook.blocks.media.posterringband

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val PW = 92f
private const val PH = 128f
private const val PAPER = 0xFFEDEBE6.toInt()
private const val INK = 0xFF111111.toInt()
private const val TILT = -16 * PI / 180
private const val ROLL = 8 * PI / 180

private data class PosterRingBandAct(val name: String, val day: String, val bg: Int, val ink: Int, val mark: String)

private val acts = listOf(
    PosterRingBandAct("LUMEN DUO", "FRI", 0xFFE3241B.toInt(), Color.WHITE, "L"),
    PosterRingBandAct("AMA VALE", "FRI", INK, 0xFFF2EFE8.toInt(), "A"),
    PosterRingBandAct("THE ORCHARDS", "SAT", 0xFFF2D13B.toInt(), INK, "O"),
    PosterRingBandAct("KIYO", "SAT", 0xFF1F3BD6.toInt(), Color.WHITE, "K"),
    PosterRingBandAct("RAFA & SOL", "SUN", 0xFFF2EFE8.toInt(), INK, "R"),
    PosterRingBandAct("MONO BLOOM", "SUN", 0xFFFF6A2B.toInt(), INK, "M"),
)

private class PosterRingBandItem(val index: Int) {
    val quad = FloatArray(8)
    var z = 0.0
    var facing = false
}

/** Static poster artwork is rasterized once; only homographies and opacity change per frame. */
internal class PosterRingBandArt(assets: AssetManager) {
    private val anton = Typeface.createFromAsset(assets, "posterringband/posterringband_Anton-Regular.ttf")
    private val bold = Typeface.create("sans-serif", Typeface.BOLD)
    private val semibold = if (Build.VERSION.SDK_INT >= 28) {
        Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), 600, false)
    } else {
        Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val posters = acts.map { poster(it) }
    private val back = poster(null)
    private val items = MutableList(POSTER_RING_BAND_COUNT) { PosterRingBandItem(it) }
    private val matrix = Matrix()
    private val source = floatArrayOf(0f, 0f, PW, 0f, PW, PH, 0f, PH)
    private val posterRect = RectF(0f, 0f, PW, PH)

    private fun poster(act: PosterRingBandAct?): Bitmap {
        val bitmap = Bitmap.createBitmap((PW * 3).toInt(), (PH * 3).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(3f, 3f)
        paint.color = act?.bg ?: 0xFF2A2A2A.toInt()
        canvas.drawRoundRect(0f, 0f, PW, PH, 4f, 4f, paint)
        if (act == null) {
            paint.color = 0xFF3A3A3A.toInt()
            canvas.drawRect(10f, 10f, PW - 10f, PH - 10f, paint)
        } else {
            fun word(value: String, x: Float, y: Float, size: Float) {
                textPaint.typeface = anton
                textPaint.textSize = 100f
                textPaint.color = act.ink
                canvas.save()
                canvas.translate(x, y)
                canvas.scale(size / 100f, size / 100f)
                canvas.drawText(value, 0f, 0f, textPaint)
                canvas.restore()
            }
            word(act.mark, 7f, 92f, 92f)
            paint.color = act.ink
            paint.alpha = 153
            canvas.drawRect(7f, 99f, PW - 7f, 100.5f, paint)
            paint.alpha = 255
            word(act.name, 7f, 113f, 11f)
            word(act.day, PW - 25f, 123f, 9f)
            word("ORBIT 26", 7f, 123f, 8f)
        }
        return bitmap
    }

    private fun ring(canvas: Canvas, turn: Double, tilt: Double, cx: Float, cy: Float) {
        val ct = cos(tilt)
        val st = sin(tilt)
        val cr = cos(ROLL)
        val sr = sin(ROLL)
        for (it in items) {
            val angle = turn + it.index * POSTER_RING_BAND_STEP
            val sx = sin(angle)
            val cz = cos(angle)
            val px = POSTER_RING_BAND_RADIUS * sx
            val pz = POSTER_RING_BAND_RADIUS * cz
            it.z = 0.0
            for (q in 0..3) {
                val edge = if (q == 0 || q == 3) -1 else 1
                val x = px + edge * cz * PW / 2
                val y = (if (q < 2) -1 else 1) * PH / 2.0
                val z = pz - edge * sx * PW / 2
                val y1 = y * ct - z * st
                val z1 = y * st + z * ct
                val scale = 820 / (820 - z1)
                it.quad[q * 2] = (cx + (x * cr - y1 * sr) * scale).toFloat()
                it.quad[q * 2 + 1] = (cy + (x * sr + y1 * cr) * scale).toFloat()
                it.z += z1 / 4
            }
            val p = it.quad
            it.facing = (p[2] - p[0]) * (p[7] - p[1]) - (p[3] - p[1]) * (p[6] - p[0]) > 0
        }
        items.sortBy { it.z }
        for (it in items) {
            if (!matrix.setPolyToPoly(source, 0, it.quad, 0, 4)) continue
            canvas.save()
            canvas.concat(matrix)
            paint.color = Color.WHITE
            canvas.drawBitmap(if (it.facing) posters[it.index % acts.size] else back, null, posterRect, paint)
            val depth = ((it.z + POSTER_RING_BAND_RADIUS) / (2 * POSTER_RING_BAND_RADIUS)).coerceIn(0.0, 1.0)
            paint.color = PAPER
            paint.alpha = (255 * 0.55 * (1 - depth)).toInt()
            canvas.drawRoundRect(posterRect, 4f, 4f, paint)
            canvas.restore()
        }
    }

    private fun text(value: String, face: Typeface, size: Float, color: Int, opacity: Float = 1f) {
        textPaint.typeface = face
        textPaint.textSize = size
        textPaint.color = color
        textPaint.alpha = (opacity * 255).toInt().coerceIn(0, 255)
    }

    private fun trackedWidth(value: String, tracking: Float): Float =
        textPaint.measureText(value) + (value.length - 1).coerceAtLeast(0) * tracking

    private fun tracked(canvas: Canvas, value: String, x: Float, baseline: Float, tracking: Float) {
        if (tracking == 0f) {
            canvas.drawText(value, x, baseline, textPaint)
        } else {
            var cursor = x
            value.forEach { ch ->
                val glyph = ch.toString()
                canvas.drawText(glyph, cursor, baseline, textPaint)
                cursor += textPaint.measureText(glyph) + tracking
            }
        }
    }

    fun draw(
        canvas: Canvas, width: Float, height: Float, safeTop: Float, safeBottom: Float,
        motion: PosterRingBandMotion, title: String, dates: String, action: String,
    ) {
        val headTop = safeTop + 6f
        text(title, anton, 132f, INK)
        canvas.save()
        canvas.clipRect(20f, headTop, width - 20f, headTop + 150f)
        // RN's fixed line box clips Anton's upper ink; use the same descent and baseline.
        tracked(canvas, title, 20f, headTop + 150f - textPaint.descent(), -2f)
        canvas.restore()
        text(dates, bold, 15f, INK)
        tracked(canvas, dates, 20f, safeTop + 146f - textPaint.ascent(), 0.3f)

        val tilt = TILT + 3 * PI / 180 * sin(motion.clock / LOOP * PI * 4)
        ring(canvas, motion.turn, tilt, width / 2, height / 2)

        for (k in 0 until POSTER_RING_BAND_COUNT) {
            val pos = -motion.turn / POSTER_RING_BAND_STEP
            var distance = abs(posterRingBandMod(pos - k + POSTER_RING_BAND_COUNT / 2.0, POSTER_RING_BAND_COUNT.toDouble()) - POSTER_RING_BAND_COUNT / 2.0)
            distance = minOf(distance, POSTER_RING_BAND_COUNT - distance)
            val opacity = (1 - (distance / 0.5).coerceIn(0.0, 1.0)).toFloat()
            if (opacity <= 0f) continue
            val act = acts[k % acts.size]
            val y = height / 2 + 150 + 10 * (1 - opacity)
            text(act.name, anton, 36f, INK, opacity)
            canvas.save()
            canvas.clipRect(20f, y, width - 20, y + 44)
            canvas.drawText(act.name, (width - textPaint.measureText(act.name)) / 2, y + 44 - textPaint.descent(), textPaint)
            canvas.restore()
            val slot = "${act.day} · Main stage · ${arrayOf("20:00", "21:30", "23:00")[k % 3]}"
            text(slot, semibold, 14f, 0xFF6E6B64.toInt(), opacity)
            canvas.drawText(slot, (width - textPaint.measureText(slot)) / 2, y + 44 - textPaint.ascent(), textPaint)
        }

        val buttonTop = height - safeBottom - 72
        paint.color = INK
        canvas.drawRoundRect(20f, buttonTop, width - 20, buttonTop + 58, 4f, 4f, paint)
        text(action, bold, 16f, PAPER)
        tracked(canvas, action, (width - trackedWidth(action, 0.3f)) / 2,
            buttonTop + 29 - (textPaint.ascent() + textPaint.descent()) / 2, 0.3f)
    }
}
