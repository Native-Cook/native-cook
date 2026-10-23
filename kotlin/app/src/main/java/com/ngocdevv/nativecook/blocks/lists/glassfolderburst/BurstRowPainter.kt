package com.ngocdevv.nativecook.blocks.lists.glassfolderburst

import android.content.Context
import android.graphics.*
import kotlin.math.*

internal class BurstRowPainter(context: Context, private val layout: BurstLayout) {
    private val regular = Typeface.createFromAsset(context.assets, "glassfolderburst/glassfolderburst_ArchivoExpanded-Regular.ttf")
    private val black = Typeface.createFromAsset(context.assets, "glassfolderburst/glassfolderburst_ArchivoExpanded-Black.ttf")
    private val covers = context.assets.open("glassfolderburst/glassfolderburst_covers.png").use { BitmapFactory.decodeStream(it) }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply { typeface = regular }
    private val bounds = Rect()
    private val matrix = Matrix()
    private val clip = Path()
    private val width = (layout.width - 48).toFloat()
    private val ink = Color.rgb(242, 255, 247)
    private val green = Color.rgb(40, 224, 124)
    private val glows = Array(5) { makeGlow(18f + it) }
    private val labels = listOf(BurstTracks.subtitle(layout.n)) + BurstTracks.tracks.take(layout.n).map {
        text.textSize = 12f
        val long = "Now playing · ${it.title}"
        if (text.measureText(long) <= layout.width - 100) long else it.title
    }

    private fun makeGlow(sigma: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(ceil((width + 144) * 2).toInt(), 392, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(2f, 2f)
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = green
            maskFilter = BlurMaskFilter((sigma - .5f) / .57735f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawRoundRect(72f, 72f, 72 + width, 124f, 16f, 16f, glow)
        return bitmap
    }
    private fun fill(color: Int, alpha: Double = 1.0) {
        paint.reset()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        paint.color = color
        paint.alpha = (Color.alpha(color) * alpha).roundToInt().coerceIn(0, 255)
    }
    private fun baseline(top: Float, height: Float): Float {
        text.getTextBounds("H", 0, 1, bounds)
        return top + (height - bounds.height()) / 2 - bounds.top
    }
    private fun fitted(value: String, available: Float): String {
        if (text.measureText(value) <= available) return value
        var end = value.length
        while (end > 0 && text.measureText(value.take(end) + "…") > available) end--
        return value.take(end) + "…"
    }
    private fun blend(a: Int, b: Int, t: Double): Int {
        fun channel(x: Int, y: Int) = (x + (y - x) * t).roundToInt().coerceIn(0, 255)
        return Color.argb(channel(Color.alpha(a), Color.alpha(b)), channel(Color.red(a), Color.red(b)),
            channel(Color.green(a), Color.green(b)), channel(Color.blue(a), Color.blue(b)))
    }
    fun rows(canvas: Canvas, s: BurstScene, f: BurstPose.Folder) {
        for (i in layout.n - 1 downTo 0) {
            val pose = BurstPose.row(s.sim.rows[i], i, layout, f, s.reduced,
                if (s.pressed == i) 1 - .03 * s.springs[BurstScene.ROW_PRESS] else 1.0)
            if (pose.opacity <= .001) continue
            val active = s.selectedMix[i]
            canvas.save()
            matrix.setValues(pose.matrix)
            canvas.concat(matrix)
            if (pose.opacity < .999) canvas.saveLayerAlpha(-72f, -72f, width + 72, 124f, (pose.opacity * 255).toInt())
            fill(Color.WHITE, pose.glow * (.48 + .27 * (pose.glow - .12) / .88))
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
            val glow = glows[((pose.glow - .12) / .88 * 4).roundToInt().coerceIn(0, 4)]
            canvas.drawBitmap(glow, null, RectF(-72f, -72f, width + 72, 124f), paint)
            fill(Color.WHITE)
            val dark = blend(Color.rgb(16, 24, 20), Color.rgb(27, 58, 42), active)
            paint.shader = LinearGradient(0f, 26f, width, 26f, intArrayOf(dark, dark, green), floatArrayOf(0f, .28f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRoundRect(0f, 0f, width, 52f, 16f, 16f, paint)
            paint.shader = LinearGradient(0f, 0f, 0f, 20.8f,
                intArrayOf(Color.argb((255 * .14 * (1 + .6 * pose.landing)).toInt(), 255, 255, 255), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(0f, 0f, width, 52f, 16f, 16f, paint)
            fill(blend(Color.argb(26, 255, 255, 255), Color.rgb(140, 255, 192), active))
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = (1 + active * .5).toFloat()
            canvas.drawRoundRect(.75f, .75f, width - .75f, 51.25f, 15.25f, 15.25f, paint)
            if (pose.landing > 0) {
                paint.alpha = (paint.alpha * pose.landing * .6).toInt()
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
                canvas.drawRoundRect(.75f, .75f, width - .75f, 51.25f, 15.25f, 15.25f, paint)
            }
            drawFace(canvas, i, pose.face, active, if (s.reduced) 0.0 else s.idleTime, s.reduced)
            if (pose.opacity < .999) canvas.restore()
            canvas.restore()
        }
    }
    private fun drawFace(canvas: Canvas, i: Int, face: Double, active: Double, time: Double, reduced: Boolean) {
        val track = BurstTracks.tracks[i]
        canvas.save()
        clip.reset()
        clip.addRoundRect(8f, 8f, 44f, 44f, 8f, 8f, Path.Direction.CW)
        canvas.clipPath(clip)
        fill(Color.WHITE, face)
        canvas.drawBitmap(covers, Rect((i % 3) * 256, (i / 3) * 256, (i % 3 + 1) * 256, (i / 3 + 1) * 256), RectF(8f, 8f, 44f, 44f), paint)
        fill(Color.argb(31, 255, 255, 255), face)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(8.5f, 8.5f, 43.5f, 43.5f, 7.5f, 7.5f, paint)
        canvas.restore()
        text.typeface = regular
        text.textSize = 11f
        text.getTextBounds(track.duration, 0, track.duration.length, bounds)
        val timeX = width - 16 - bounds.width() - bounds.left
        val available = timeX - 12 - 56
        text.color = ink
        text.alpha = (face * 255).toInt()
        text.textSize = 14f
        canvas.drawText(fitted(track.title, available), 56f, baseline(9f, 18f), text)
        text.textSize = 11f
        text.alpha = (face * .58 * 255).toInt()
        canvas.drawText(fitted(track.artist, available), 56f, baseline(28f, 14f), text)
        text.color = Color.rgb(2, 24, 13)
        text.alpha = (face * (1 - active) * .72 * 255).toInt()
        canvas.drawText(track.duration, timeX, baseline(0f, 52f), text)
        fill(Color.rgb(2, 24, 13), face * active * .72)
        repeat(4) { phase ->
            val h = if (reduced) 8.0 + phase * 2 else 6 + 10 * (.5 + .5 * sin(time * PI * 2 / 5 + phase * 1.7))
            val x = width - 37 + phase * 6
            canvas.drawRoundRect(x, (26 - h / 2).toFloat(), x + 3, (26 + h / 2).toFloat(), 1.5f, 1.5f, paint)
        }
    }
    fun header(canvas: Canvas, s: BurstScene) {
        line(canvas, BurstTracks.title, true, 1.0, s.dock)
        line(canvas, labels[0], false, max(0.0, 1 - s.selectedMix.sum()), s.dock)
        for (i in 0 until layout.n) if (s.selectedMix[i] > 0) line(canvas, labels[i + 1], false, s.selectedMix[i], s.dock)
    }
    private fun line(canvas: Canvas, value: String, title: Boolean, alpha: Double, dock: Double) {
        text.typeface = if (title) black else regular
        text.textSize = if (title) 20f else 12f
        text.color = ink
        text.alpha = (alpha * (if (title) 1.0 else .58) * 255).roundToInt().coerceIn(0, 255)
        text.getTextBounds(value, 0, value.length, bounds)
        val restX = (layout.width - bounds.width()) / 2 - bounds.left
        val restY = layout.restY - if (title) 154 else 126
        val endY = layout.headerTop + if (title) 0 else 24
        val y = baseline((restY + (endY - restY) * dock).toFloat(), if (title) 24f else 16f)
        canvas.drawText(value, (restX + (76 - restX) * dock).toFloat(), y, text)
    }
    fun close() {
        covers.recycle()
        glows.forEach { it.recycle() }
    }
}
